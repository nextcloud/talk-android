/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Andy Scherzinger <andy.scherzinger@nextcloud.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.data.network

import android.database.sqlite.SQLiteConstraintException
import android.os.SystemClock
import android.util.Log
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.chat.domain.ChatPullResult
import com.nextcloud.talk.data.database.dao.ChatBlocksDao
import com.nextcloud.talk.data.database.dao.ChatMessagesDao
import com.nextcloud.talk.data.database.mappers.asEntity
import com.nextcloud.talk.data.database.model.ChatBlockEntity
import com.nextcloud.talk.data.database.model.ChatMessageEntity
import com.nextcloud.talk.data.network.NetworkMonitor
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.chat.ChatMessageJson
import com.nextcloud.talk.utils.SpreedFeatures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import retrofit2.HttpException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * The chat message fetch-and-persist core, shared between the chat screen
 * ([OfflineFirstChatRepository]) and background sync callers.
 *
 * Every operation takes a [SyncTarget] describing the account, room and thread to sync, so the
 * syncer can be used for any room at any time — no open chat required. UI-bound side effects are
 * reported through the optional [Events] listener. The only state kept between calls is the
 * per-room coalescing bookkeeping of [catchUpRoom], which collapses bursts of catch-up requests
 * (e.g. one push notification per incoming message) into few actual fetches.
 */
@Suppress("TooManyFunctions")
class ChatMessageSyncer @Inject constructor(
    private val chatDao: ChatMessagesDao,
    private val chatBlocksDao: ChatBlocksDao,
    private val network: ChatNetworkDataSource,
    private val networkMonitor: NetworkMonitor
) {

    /**
     * Identifies the conversation (and optionally thread) a sync operation works on.
     */
    data class SyncTarget(
        val user: User,
        val roomToken: String,
        val threadId: Long?,
        val credentials: String,
        val urlForChatting: String
    ) {
        val internalConversationId: String = "${user.id}@$roomToken"
        val accountId: Long
            get() = user.id!!
    }

    /**
     * Side effects that only matter while a chat is on screen. Background callers can pass [NO_EVENTS].
     */
    interface Events {
        suspend fun onLastCommonReadChanged(lastCommonRead: Int?) {
            // no-op by default
        }

        suspend fun onLoadingChanged(isLoading: Boolean) {
            // no-op by default
        }

        suspend fun onRoomRefreshNeeded() {
            // no-op by default
        }

        suspend fun onIncomingMessagesFromOthers() {
            // no-op by default
        }
    }

    data class SyncOutcome(
        val persistedNewMessages: Boolean,
        val newestPersistedMessageId: Long?,
        val oldestPersistedMessageId: Long? = null,
        val persistedMessageCount: Int = 0
    )

    /**
     * Builds the query parameters for a chat pull request. setReadMarker stays 0 so a sync never
     * moves the user's read marker. Background fetches must additionally pass
     * [markNotificationsAsRead] = false so the server keeps push notifications for the fetched
     * messages — only supported when the server has the chat-keep-notifications capability.
     */
    @Suppress("LongParameterList")
    fun buildFieldMap(
        lookIntoFuture: Boolean,
        timeout: Int,
        includeLastKnown: Boolean,
        lastKnown: Int?,
        limit: Int = DEFAULT_MESSAGES_LIMIT,
        threadId: Long? = null,
        lastCommonRead: Int? = null,
        markNotificationsAsRead: Boolean = true
    ): HashMap<String, Int> {
        val fieldMap = HashMap<String, Int>()

        fieldMap["includeLastKnown"] = if (includeLastKnown) 1 else 0

        if (lastKnown != null) {
            fieldMap["lastKnownMessageId"] = lastKnown
        }

        lastCommonRead?.let {
            fieldMap["lastCommonReadId"] = it
        }

        threadId?.let { fieldMap["threadId"] = it.toInt() }

        fieldMap["timeout"] = timeout
        fieldMap["limit"] = limit

        fieldMap["lookIntoFuture"] = if (lookIntoFuture) 1 else 0
        fieldMap["setReadMarker"] = 0

        if (!markNotificationsAsRead) {
            fieldMap["markNotificationsAsRead"] = 0
        }

        return fieldMap
    }

    /**
     * Returns true when messages of the conversation (or thread) are cached locally and covered by
     * a chat block, i.e. a catch-up only needs to fetch the delta.
     */
    fun hasLocalChatBlock(internalConversationId: String, threadId: Long?): Boolean =
        chatBlocksDao.getNewestMessageIdFromChatBlocks(internalConversationId, threadId) > 0

    /**
     * Deletes the expired messages of a conversation and reconciles its chat blocks afterwards:
     * block boundaries are trimmed to the oldest/newest message that still exists and blocks whose
     * messages are all gone are deleted. Without the reconciliation, block boundaries would point
     * to deleted messages, e.g. faking coverage up to a message that is no longer cached.
     */
    suspend fun cleanupExpiredMessages(internalConversationId: String) {
        val deletedMessages = chatDao.deleteExpiredMessages(
            internalConversationId,
            System.currentTimeMillis() / MILLIS_PER_SECOND
        )
        if (deletedMessages == 0) {
            return
        }
        Log.d(TAG, "Deleted $deletedMessages expired messages for $internalConversationId, reconciling chat blocks")

        val blocks = chatBlocksDao.getChatBlocksForConversation(internalConversationId)
        for (block in blocks) {
            val newestExistingId = chatDao.getNewestMessageIdInRange(
                internalConversationId = internalConversationId,
                threadId = block.threadId,
                oldestMessageId = block.oldestMessageId,
                newestMessageId = block.newestMessageId
            )

            if (newestExistingId == null) {
                Log.d(TAG, "Deleting chat block without any remaining messages ($internalConversationId)")
                chatBlocksDao.deleteChatBlocks(listOf(block))
                continue
            }

            val oldestExistingId = chatDao.getOldestMessageIdInRange(
                internalConversationId = internalConversationId,
                threadId = block.threadId,
                oldestMessageId = block.oldestMessageId,
                newestMessageId = block.newestMessageId
            ) ?: continue

            if (block.oldestMessageId != oldestExistingId || block.newestMessageId != newestExistingId) {
                block.oldestMessageId = oldestExistingId
                block.newestMessageId = newestExistingId
                chatBlocksDao.upsertChatBlock(block)
            }
        }
    }

    /**
     * Catches up a room with the server without requiring an open chat.
     *
     * If a chat block exists, only the delta since the newest locally known message is fetched and
     * the block is extended. For rooms without any chat block (never-opened rooms) the newest
     * messages are fetched and the initial chat block is created, so cached messages become
     * visible to [ChatMessagesDao] block queries right away.
     *
     * Requires the chat-keep-notifications capability: without it, a background fetch would
     * dismiss the user's push notifications for the fetched messages, so the catch-up is skipped
     * entirely (same guard as on iOS).
     *
     * Bursts of catch-up requests for the same room (e.g. one push notification per message of an
     * active group chat) are coalesced: while a catch-up runs, further requests only mark a rerun
     * and return, and consecutive fetches are paced by [CATCH_UP_COOLDOWN_MILLIS].
     */
    suspend fun catchUpRoom(target: SyncTarget, limit: Int = DEFAULT_MESSAGES_LIMIT): SyncOutcome =
        when {
            !networkMonitor.isOnline.value -> {
                Log.d(TAG, "Device is offline, skipping catch-up for ${target.internalConversationId}")
                NOTHING_SYNCED
            }

            !target.user.hasSpreedFeatureCapability(SpreedFeatures.CHAT_KEEP_NOTIFICATIONS.value) -> {
                Log.d(
                    TAG,
                    "Server lacks ${SpreedFeatures.CHAT_KEEP_NOTIFICATIONS.value}, " +
                        "skipping catch-up for ${target.internalConversationId}"
                )
                NOTHING_SYNCED
            }

            else -> coalescedRoomCatchUp(target, limit)
        }

    /**
     * Runs at most one catch-up per room at a time. A request arriving while one is running only
     * marks a rerun: the running catch-up re-fetches once more after finishing, so messages that
     * arrived in between are still picked up without a parallel request. Consecutive fetches are
     * paced by [CATCH_UP_COOLDOWN_MILLIS] and a single burst performs at most
     * [MAX_CATCH_UP_RUNS_PER_BURST] fetches — later messages are covered by their own push or the
     * next room list sync.
     */
    private suspend fun coalescedRoomCatchUp(target: SyncTarget, limit: Int): SyncOutcome {
        val stateKey = syncStateKey(target.internalConversationId, target.threadId)
        val state = catchUpStates.getOrPut(stateKey) { RoomCatchUpState() }

        if (!state.mutex.tryLock()) {
            state.rerunRequested.set(true)
            Log.d(TAG, "Catch-up already running for $stateKey, coalescing into it")
            return NOTHING_SYNCED
        }

        try {
            var outcome: SyncOutcome
            var runs = 0
            do {
                awaitCatchUpCooldown(state, stateKey)
                state.rerunRequested.set(false)
                outcome = fetchRoomCatchUp(target, limit)
                state.lastCompletedAtMillis = SystemClock.elapsedRealtime()
                runs++
            } while (state.rerunRequested.get() && runs < MAX_CATCH_UP_RUNS_PER_BURST)
            return outcome
        } finally {
            state.mutex.unlock()
        }
    }

    private suspend fun awaitCatchUpCooldown(state: RoomCatchUpState, stateKey: String) {
        val elapsedSinceLastCatchUp = SystemClock.elapsedRealtime() - state.lastCompletedAtMillis
        val remainingCooldown = CATCH_UP_COOLDOWN_MILLIS - elapsedSinceLastCatchUp
        if (state.lastCompletedAtMillis > 0 && remainingCooldown > 0) {
            Log.d(TAG, "Catch-up cooldown for $stateKey, delaying fetch by $remainingCooldown ms")
            delay(remainingCooldown)
        }
    }

    private class RoomCatchUpState {
        val mutex = Mutex()
        val rerunRequested = AtomicBoolean(false)

        @Volatile
        var lastCompletedAtMillis = 0L
    }

    private val catchUpStates = ConcurrentHashMap<String, RoomCatchUpState>()

    /**
     * Newest message id per conversation/thread that is known from HTTP syncs — deliberately
     * EXCLUDING messages delivered via signaling. Signaling messages extend the latest chat block
     * optimistically (assuming they are contiguous on top of it); the insurance request exists to
     * verify that assumption and must therefore anchor on the last HTTP-synced message, otherwise
     * messages that arrived between the last sync and the signaling delivery would never be
     * fetched. Living in this singleton, the anchor survives reopening a chat.
     */
    private val lastHttpSyncedMessageIds = ConcurrentHashMap<String, Long>()

    /**
     * The newest message id of the conversation/thread confirmed via HTTP sync, or null when no
     * sync happened yet (and [seedHttpSyncedMessageId] was never called) since app start.
     */
    fun lastHttpSyncedMessageId(internalConversationId: String, threadId: Long?): Long? =
        lastHttpSyncedMessageIds[syncStateKey(internalConversationId, threadId)]

    /**
     * Seeds the HTTP-synced anchor without a fetch. Only call with ids whose coverage is proven by
     * HTTP-derived data — e.g. the conversation's lastMessage from the room list sync when the
     * local chat block already reaches it. The anchor only ever moves forward.
     */
    fun seedHttpSyncedMessageId(internalConversationId: String, threadId: Long?, messageId: Long) {
        lastHttpSyncedMessageIds.merge(syncStateKey(internalConversationId, threadId), messageId, ::maxOf)
    }

    private fun recordHttpSyncedMessageId(target: SyncTarget, messageId: Long) {
        lastHttpSyncedMessageIds.merge(syncStateKey(target.internalConversationId, target.threadId), messageId, ::maxOf)
    }

    private fun syncStateKey(internalConversationId: String, threadId: Long?): String =
        "$internalConversationId#$threadId"

    private suspend fun fetchRoomCatchUp(target: SyncTarget, limit: Int): SyncOutcome {
        val newestMessageIdFromDb =
            chatBlocksDao.getNewestMessageIdFromChatBlocks(target.internalConversationId, target.threadId)

        val outcome = if (newestMessageIdFromDb > 0) {
            closeBacklog(
                target = target,
                fromMessageId = newestMessageIdFromDb,
                limit = limit,
                markNotificationsAsRead = false
            )
        } else {
            pullAndPersistMessages(
                target,
                buildFieldMap(
                    lookIntoFuture = false,
                    timeout = 0,
                    includeLastKnown = true,
                    lastKnown = null,
                    limit = limit,
                    threadId = target.threadId,
                    markNotificationsAsRead = false
                )
            )
        }

        if (outcome.persistedNewMessages) {
            Log.d(
                TAG,
                "Background catch-up for room ${target.roomToken}: fetched ${outcome.persistedMessageCount} " +
                    "message(s), ids ${outcome.oldestPersistedMessageId}..${outcome.newestPersistedMessageId}" +
                    if (newestMessageIdFromDb > 0) " (delta from $newestMessageIdFromDb)" else " (initial fetch)"
            )
        } else {
            Log.d(TAG, "Background catch-up for room ${target.roomToken}: no new messages")
        }

        return outcome
    }

    /**
     * Fetches the messages newer than [fromMessageId] until the backlog is fully closed.
     *
     * A single fetch is capped by [limit], so one request only narrows a backlog larger than
     * that — and on chat-relay servers the remaining gap would become permanent as soon as a
     * signaling message extends the latest chat block over it. Fetches are therefore repeated
     * until the server returns fewer messages than the limit. If [MAX_BACKLOG_ROUNDS] full pages
     * were fetched and the backlog is still not closed, the newest messages are fetched with
     * includeLastKnown instead: that creates a separate top chat block, so the remaining gap
     * stays visible in the block structure (closable by scrolling up) rather than a block
     * claiming ranges that were never fetched.
     */
    @Suppress("LongParameterList")
    suspend fun closeBacklog(
        target: SyncTarget,
        fromMessageId: Long,
        limit: Int = DEFAULT_MESSAGES_LIMIT,
        lastCommonRead: Int? = null,
        markNotificationsAsRead: Boolean = true,
        events: Events = NO_EVENTS
    ): SyncOutcome {
        var anchor = fromMessageId
        var totalCount = 0
        var oldestPersisted: Long? = null
        var newestPersisted: Long? = null

        repeat(MAX_BACKLOG_ROUNDS) {
            val fieldMap = buildFieldMap(
                lookIntoFuture = true,
                timeout = 0,
                includeLastKnown = false,
                lastKnown = anchor.toInt(),
                limit = limit,
                threadId = target.threadId,
                lastCommonRead = lastCommonRead,
                markNotificationsAsRead = markNotificationsAsRead
            )
            val roundOutcome = pullAndPersistMessages(target, fieldMap, events)

            if (roundOutcome.persistedNewMessages) {
                totalCount += roundOutcome.persistedMessageCount
                oldestPersisted = oldestPersisted ?: roundOutcome.oldestPersistedMessageId
                newestPersisted = roundOutcome.newestPersistedMessageId ?: newestPersisted
            }

            val caughtUp = !roundOutcome.persistedNewMessages || roundOutcome.persistedMessageCount < limit
            val nextAnchor = roundOutcome.newestPersistedMessageId
            if (caughtUp || nextAnchor == null) {
                return SyncOutcome(
                    persistedNewMessages = totalCount > 0,
                    newestPersistedMessageId = newestPersisted,
                    oldestPersistedMessageId = oldestPersisted,
                    persistedMessageCount = totalCount
                )
            }
            anchor = nextAnchor
        }

        Log.w(
            TAG,
            "Backlog above $fromMessageId in ${target.internalConversationId} still not closed after " +
                "$MAX_BACKLOG_ROUNDS rounds, fetching the newest messages instead"
        )
        val fallbackOutcome = pullAndPersistMessages(
            target,
            buildFieldMap(
                lookIntoFuture = false,
                timeout = 0,
                includeLastKnown = true,
                lastKnown = null,
                limit = limit,
                threadId = target.threadId,
                lastCommonRead = lastCommonRead,
                markNotificationsAsRead = markNotificationsAsRead
            ),
            events
        )
        return SyncOutcome(
            persistedNewMessages = totalCount > 0 || fallbackOutcome.persistedNewMessages,
            newestPersistedMessageId = fallbackOutcome.newestPersistedMessageId ?: newestPersisted,
            oldestPersistedMessageId = oldestPersisted ?: fallbackOutcome.oldestPersistedMessageId,
            persistedMessageCount = totalCount + fallbackOutcome.persistedMessageCount
        )
    }

    fun pullMessagesFlow(target: SyncTarget, fieldMap: HashMap<String, Int>): Flow<ChatPullResult> =
        flow {
            var attempts = 1

            while (attempts < MAX_PULL_ATTEMPTS) {
                runCatching {
                    network.pullChatMessages(target.credentials, target.urlForChatting, fieldMap)
                }.fold(
                    onSuccess = { response ->
                        val result = when (response.code()) {
                            HTTP_CODE_OK -> ChatPullResult.Success(
                                messages = response.body()?.ocs?.data.orEmpty(),
                                lastCommonRead = response.headers()["X-Chat-Last-Common-Read"]?.toInt()
                            )
                            HTTP_CODE_NOT_MODIFIED -> ChatPullResult.NotModified
                            HTTP_CODE_PRECONDITION_FAILED -> ChatPullResult.PreconditionFailed
                            else -> ChatPullResult.Error(HttpException(response))
                        }

                        emit(result)
                        return@flow
                    },
                    onFailure = { e ->
                        Log.e(TAG, "Attempt $attempts failed", e)
                        attempts++
                        fieldMap["limit"] = when (attempts) {
                            2 -> RETRY_LIMIT_SECOND_ATTEMPT
                            3 -> RETRY_LIMIT_THIRD_ATTEMPT
                            else -> RETRY_LIMIT_FALLBACK_ATTEMPT
                        }
                    }
                )
            }

            emit(ChatPullResult.Error(IllegalStateException("All attempts failed")))
        }.flowOn(Dispatchers.IO)

    /**
     * Pulls messages from the server as described by [fieldMap], persists them and updates the
     * chat blocks of [target].
     */
    suspend fun pullAndPersistMessages(
        target: SyncTarget,
        fieldMap: HashMap<String, Int>,
        events: Events = NO_EVENTS
    ): SyncOutcome {
        val isLongPoll = (fieldMap["timeout"] ?: 0) > 0
        if (!isLongPoll) events.onLoadingChanged(true)
        try {
            if (!networkMonitor.isOnline.value) {
                Log.d(TAG, "Device is offline, can't load chat messages from server")
            }

            val queriedMessageId = fieldMap["lastKnownMessageId"]
            val lookIntoFuture = fieldMap["lookIntoFuture"] == 1

            return when (val result = pullMessagesFlow(target, fieldMap).first()) {
                is ChatPullResult.Success ->
                    handleSuccessfulPull(target, result, queriedMessageId, lookIntoFuture, events)

                is ChatPullResult.NotModified -> {
                    Log.d(TAG, "Server returned NOT_MODIFIED, nothing to update")
                    if (lookIntoFuture && queriedMessageId != null) {
                        // the server confirmed there is nothing newer than the queried message, so
                        // the queried message is a valid HTTP-synced anchor
                        recordHttpSyncedMessageId(target, queriedMessageId.toLong())
                    }
                    NOTHING_SYNCED
                }

                is ChatPullResult.PreconditionFailed -> {
                    Log.d(TAG, "Server returned PRECONDITION_FAILED, nothing to update")
                    NOTHING_SYNCED
                }

                is ChatPullResult.Error -> {
                    Log.e(TAG, "Error pulling messages from server", result.throwable)
                    NOTHING_SYNCED
                }
            }
        } finally {
            if (!isLongPoll) events.onLoadingChanged(false)
        }
    }

    private suspend fun handleSuccessfulPull(
        target: SyncTarget,
        result: ChatPullResult.Success,
        queriedMessageId: Int?,
        lookIntoFuture: Boolean,
        events: Events
    ): SyncOutcome {
        events.onLastCommonReadChanged(result.lastCommonRead)

        val hasHistory = getHasHistory(HTTP_CODE_OK, lookIntoFuture)

        Log.d(
            TAG,
            "internalConv=${target.internalConversationId} statusCode=$HTTP_CODE_OK " +
                "lookIntoFuture=$lookIntoFuture hasHistory=$hasHistory " +
                "queriedMessageId=$queriedMessageId"
        )

        val blockContainingQueriedMessage: ChatBlockEntity? = getBlockOfMessage(target, queriedMessageId)

        blockContainingQueriedMessage?.takeIf { !hasHistory }?.apply {
            this.hasHistory = false
            chatBlocksDao.upsertChatBlock(this)
            Log.d(TAG, "End of chat reached, set hasHistory=false")
        }

        return if (result.messages.isNotEmpty()) {
            val persistedMessages = updateMessagesData(
                target,
                result.messages,
                blockContainingQueriedMessage,
                lookIntoFuture,
                hasHistory,
                events
            )
            persistedMessages.maxOfOrNull { it.id }?.let { recordHttpSyncedMessageId(target, it) }
            SyncOutcome(
                persistedNewMessages = persistedMessages.isNotEmpty(),
                newestPersistedMessageId = persistedMessages.maxOfOrNull { it.id },
                oldestPersistedMessageId = persistedMessages.minOfOrNull { it.id },
                persistedMessageCount = persistedMessages.size
            )
        } else {
            Log.d(TAG, "No new messages to update")
            if (lookIntoFuture && queriedMessageId != null) {
                // an empty response to a lookIntoFuture request confirms the queried message as
                // the newest one, so it is a valid HTTP-synced anchor
                recordHttpSyncedMessageId(target, queriedMessageId.toLong())
            }
            NOTHING_SYNCED
        }
    }

    @Suppress("LongParameterList")
    private suspend fun updateMessagesData(
        target: SyncTarget,
        chatMessagesJson: List<ChatMessageJson>,
        blockContainingQueriedMessage: ChatBlockEntity?,
        lookIntoFuture: Boolean,
        hasHistory: Boolean,
        events: Events
    ): List<ChatMessageEntity> {
        val chatMessageEntities = persistChatMessagesAndHandleSystemMessages(
            target,
            chatMessagesJson,
            emitOnIncoming = lookIntoFuture,
            events = events
        )

        if (chatMessageEntities.isEmpty()) {
            // Persisting was skipped because the conversation is not in the DB yet (see
            // persistChatMessagesAndHandleSystemMessages). Without persisted messages there must be
            // no chat block update either, otherwise a block would reference missing messages.
            Log.w(TAG, "No messages were persisted for ${target.internalConversationId}, skipping chat block update")
            return emptyList()
        }

        val oldestIdFromSync = chatMessageEntities.minByOrNull { it.id }!!.id
        val newestIdFromSync = chatMessageEntities.maxByOrNull { it.id }!!.id
        Log.d(TAG, "oldestIdFromSync: $oldestIdFromSync")
        Log.d(TAG, "newestIdFromSync: $newestIdFromSync")

        var oldestMessageIdForNewChatBlock = oldestIdFromSync
        var newestMessageIdForNewChatBlock = newestIdFromSync

        if (blockContainingQueriedMessage != null) {
            if (lookIntoFuture) {
                val oldestMessageIdFromBlockOfQueriedMessage = blockContainingQueriedMessage.oldestMessageId
                Log.d(TAG, "oldestMessageIdFromBlockOfQueriedMessage: $oldestMessageIdFromBlockOfQueriedMessage")
                oldestMessageIdForNewChatBlock = oldestMessageIdFromBlockOfQueriedMessage
            } else {
                val newestMessageIdFromBlockOfQueriedMessage = blockContainingQueriedMessage.newestMessageId
                Log.d(TAG, "newestMessageIdFromBlockOfQueriedMessage: $newestMessageIdFromBlockOfQueriedMessage")
                newestMessageIdForNewChatBlock = newestMessageIdFromBlockOfQueriedMessage
            }
        }

        Log.d(TAG, "oldestMessageIdForNewChatBlock: $oldestMessageIdForNewChatBlock")
        Log.d(TAG, "newestMessageIdForNewChatBlock: $newestMessageIdForNewChatBlock")

        val newChatBlock = ChatBlockEntity(
            internalConversationId = target.internalConversationId,
            accountId = target.accountId,
            token = target.roomToken,
            threadId = target.threadId,
            oldestMessageId = oldestMessageIdForNewChatBlock,
            newestMessageId = newestMessageIdForNewChatBlock,
            hasHistory = hasHistory
        )
        updateBlocks(target, newChatBlock)

        return chatMessageEntities
    }

    suspend fun persistChatMessagesAndHandleSystemMessages(
        target: SyncTarget,
        chatMessages: List<ChatMessageJson>,
        emitOnIncoming: Boolean = false,
        events: Events = NO_EVENTS
    ): List<ChatMessageEntity> {
        handleSystemMessagesThatAffectDatabase(target, chatMessages, events)

        val chatMessageEntities = chatMessages.map {
            it.asEntity(target.accountId)
        }

        try {
            chatDao.upsertChatMessagesAndDeleteTemp(target.internalConversationId, chatMessageEntities)
        } catch (e: SQLiteConstraintException) {
            // Skipped persisting messages: conversation $internalConversationId not in DB yet.
            // This avoids "SQLiteConstraintException: FOREIGN KEY constraint failed".
            // It may happen when a notification for a newly created conversation is opened. The websocket was just
            // faster than the API request so no conversation for the message exists yet. Just swallow the exception
            // and let the insurance request handle it.
            Log.w(TAG, "Skip persisting messages from signaling: conversation not in DB yet. Swallowed exception: $e")
            return emptyList()
        }

        if (emitOnIncoming) {
            val hasIncomingFromOther = chatMessages.any { msg ->
                msg.systemMessageType == ChatMessage.SystemMessageType.DUMMY &&
                    msg.actorId != target.user.userId
            }
            if (hasIncomingFromOther) {
                events.onIncomingMessagesFromOthers()
            }
        }

        return chatMessageEntities
    }

    /**
     * Returns true if all system messages do not require translation.
     * Ignores other message types.
     */
    fun isUntranslatedSystemMessage(messagesJson: List<ChatMessageJson>): Boolean =
        messagesJson.all {
            it.systemMessageType == ChatMessage.SystemMessageType.DUMMY ||
                it.systemMessageType in ChatMessage.SYSTEM_MESSAGE_TYPE_UNTRANSLATED
        }

    private suspend fun handleSystemMessagesThatAffectDatabase(
        target: SyncTarget,
        messagesJson: List<ChatMessageJson>,
        events: Events
    ) {
        var needsRoomRefresh = false
        messagesJson.forEach { messageJson ->
            when (messageJson.systemMessageType) {
                ChatMessage.SystemMessageType.REACTION,
                ChatMessage.SystemMessageType.REACTION_REVOKED,
                ChatMessage.SystemMessageType.REACTION_DELETED ->
                    // Signaling does not include reactionsSelf; derive it so the self-reaction
                    // border stays correct regardless of whether signaling or the API response lands first.
                    upsertParentMessage(target, messageJson, deriveReactions = true)

                ChatMessage.SystemMessageType.MESSAGE_DELETED,
                ChatMessage.SystemMessageType.POLL_VOTED,
                ChatMessage.SystemMessageType.MESSAGE_EDITED ->
                    upsertParentMessage(target, messageJson)

                ChatMessage.SystemMessageType.LOBBY_NONE,
                ChatMessage.SystemMessageType.LOBBY_NON_MODERATORS,
                ChatMessage.SystemMessageType.LOBBY_OPEN_TO_EVERYONE -> needsRoomRefresh = true

                ChatMessage.SystemMessageType.CLEARED_CHAT -> {
                    // for lookIntoFuture just deleting everything would be fine.
                    // But lets say we did not open the chat for a while and in between it was cleared.
                    // We just load the last messages but this don't contain the system message.
                    // We scroll up and load the system message. Deleting everything is not an option as we
                    // would loose the messages that we want to keep. We only want to
                    // delete the messages and chatBlocks older than the system message.
                    chatDao.deleteMessagesOlderThan(target.internalConversationId, messageJson.id)
                    chatBlocksDao.deleteChatBlocksOlderThan(target.internalConversationId, messageJson.id)
                }

                ChatMessage.SystemMessageType.MESSAGE_PINNED,
                ChatMessage.SystemMessageType.MESSAGE_UNPINNED -> needsRoomRefresh = true

                else -> {}
            }
        }
        if (needsRoomRefresh) events.onRoomRefreshNeeded()
    }

    // the parent message is always the newest state, no matter how old the system message is.
    // that's why we can just take the parent, update it in DB and update the UI
    private suspend fun upsertParentMessage(
        target: SyncTarget,
        messageJson: ChatMessageJson,
        deriveReactions: Boolean = false
    ) {
        val parentMessageJson = messageJson.parentMessage ?: return
        parentMessageJson.message ?: return
        val parentMessageEntity = parentMessageJson.asEntity(target.accountId)

        // Preserve parentMessageId if missing in server response but present in local DB
        val existingEntity = chatDao.getChatMessageEntity(target.internalConversationId, parentMessageJson.id)
        if (existingEntity != null && parentMessageEntity.parentMessageId == null) {
            parentMessageEntity.parentMessageId = existingEntity.parentMessageId
        }

        if (deriveReactions) {
            parentMessageEntity.reactionsSelf =
                deriveReactionsSelf(target, messageJson, existingEntity)
        }

        chatDao.upsertChatMessage(parentMessageEntity)
    }

    /**
     * Derives the correct reactionsSelf list for a parent message when a reaction system message arrives via
     * signaling. The signaling payload does not include reactionsSelf, so we preserve the existing DB state and
     * apply a targeted update only if the actor of the system message is the current user.
     *
     * The emoji is always in the message field (messageParameters is empty for all reaction system messages).
     *
     * This handles the race condition where signaling may arrive before or after
     * ReactionsRepositoryImpl has written the optimistic local update.
     */
    @Suppress("NestedBlockDepth")
    private fun deriveReactionsSelf(
        target: SyncTarget,
        systemMessageJson: ChatMessageJson,
        existingEntity: ChatMessageEntity?
    ): ArrayList<String> {
        val reactionsSelf = ArrayList<String>(existingEntity?.reactionsSelf ?: emptyList())
        val isCurrentUserActor = systemMessageJson.actorId == target.user.userId &&
            systemMessageJson.actorType == "users"

        if (isCurrentUserActor) {
            val emoji = systemMessageJson.message
            if (emoji != null) {
                when (systemMessageJson.systemMessageType) {
                    ChatMessage.SystemMessageType.REACTION -> {
                        if (!reactionsSelf.contains(emoji)) reactionsSelf.add(emoji)
                    }

                    ChatMessage.SystemMessageType.REACTION_REVOKED,
                    ChatMessage.SystemMessageType.REACTION_DELETED -> reactionsSelf.remove(emoji)
                    else -> {}
                }
            }
        }

        return reactionsSelf
    }

    /**
     *  304 is returned when oldest message of chat was queried or when long polling request returned with no
     *  modification. hasHistory is only set to false, when 304 was returned for the the oldest message
     */
    private fun getHasHistory(statusCode: Int, lookIntoFuture: Boolean): Boolean =
        if (statusCode == HTTP_CODE_NOT_MODIFIED) {
            lookIntoFuture
        } else {
            true
        }

    suspend fun getBlockOfMessage(target: SyncTarget, queriedMessageId: Int?): ChatBlockEntity? {
        var blockContainingQueriedMessage: ChatBlockEntity? = null
        if (queriedMessageId != null) {
            val blocksContainingQueriedMessage =
                chatBlocksDao.getChatBlocksContainingMessageId(
                    internalConversationId = target.internalConversationId,
                    threadId = target.threadId,
                    messageId = queriedMessageId.toLong()
                )

            val chatBlocks = blocksContainingQueriedMessage.first()
            if (chatBlocks.size > 1) {
                Log.w(TAG, "multiple chat blocks with messageId $queriedMessageId were found")
            }

            blockContainingQueriedMessage = if (chatBlocks.isNotEmpty()) {
                chatBlocks.first()
            } else {
                null
            }
        }
        return blockContainingQueriedMessage
    }

    suspend fun updateBlocks(target: SyncTarget, chatBlock: ChatBlockEntity) {
        chatBlocksDao.upsertChatBlock(chatBlock)

        val connectedChatBlocks =
            chatBlocksDao.getConnectedChatBlocks(
                internalConversationId = target.internalConversationId,
                threadId = target.threadId,
                oldestMessageId = chatBlock.oldestMessageId,
                newestMessageId = chatBlock.newestMessageId
            ).first()

        if (connectedChatBlocks.size == 1) {
            Log.d(TAG, "This chatBlock is not connected to others")
            val chatBlockFromDb = connectedChatBlocks[0]
            Log.d(TAG, "chatBlockFromDb.oldestMessageId: " + chatBlockFromDb.oldestMessageId)
            Log.d(TAG, "chatBlockFromDb.newestMessageId: " + chatBlockFromDb.newestMessageId)
        } else if (connectedChatBlocks.size > 1) {
            Log.d(TAG, "Found " + connectedChatBlocks.size + " chat blocks that are connected")
            val oldestIdFromDbChatBlocks =
                connectedChatBlocks.minByOrNull { it.oldestMessageId }!!.oldestMessageId
            val newestIdFromDbChatBlocks =
                connectedChatBlocks.maxByOrNull { it.newestMessageId }!!.newestMessageId

            val hasNoHistory = connectedChatBlocks.any { !it.hasHistory }
            val hasHistory = !hasNoHistory
            Log.d(TAG, "hasHistory = $hasHistory")

            val newChatBlock = ChatBlockEntity(
                internalConversationId = target.internalConversationId,
                accountId = target.accountId,
                token = target.roomToken,
                threadId = target.threadId,
                oldestMessageId = oldestIdFromDbChatBlocks,
                newestMessageId = newestIdFromDbChatBlocks,
                hasHistory = hasHistory
            )
            chatBlocksDao.replaceConnectedChatBlocks(connectedChatBlocks, newChatBlock)
            Log.d(TAG, "A new chat block was created that covers all the range of the found chatblocks")
            Log.d(TAG, "new chatBlock - oldest MessageId: $oldestIdFromDbChatBlocks")
            Log.d(TAG, "new chatBlock - newest MessageId: $newestIdFromDbChatBlocks")
        } else {
            Log.d(TAG, "No chat block found ....")
        }
    }

    companion object {
        val TAG: String = ChatMessageSyncer::class.java.simpleName

        val NO_EVENTS: Events = object : Events {}

        private val NOTHING_SYNCED = SyncOutcome(persistedNewMessages = false, newestPersistedMessageId = null)

        private const val DEFAULT_MESSAGES_LIMIT = 100
        private const val MILLIS_PER_SECOND = 1000L
        private const val CATCH_UP_COOLDOWN_MILLIS = 5_000L
        private const val MAX_CATCH_UP_RUNS_PER_BURST = 3
        private const val MAX_BACKLOG_ROUNDS = 5
        private const val HTTP_CODE_OK: Int = 200
        private const val HTTP_CODE_NOT_MODIFIED = 304
        private const val HTTP_CODE_PRECONDITION_FAILED = 412
        private const val MAX_PULL_ATTEMPTS = 5
        private const val RETRY_LIMIT_SECOND_ATTEMPT = 50
        private const val RETRY_LIMIT_THIRD_ATTEMPT = 10
        private const val RETRY_LIMIT_FALLBACK_ATTEMPT = 5
    }
}
