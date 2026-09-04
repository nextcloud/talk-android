/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2024 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.data.network

import android.os.Bundle
import android.util.Log
import com.nextcloud.talk.chat.data.ChatMessageRepository
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.conversationlist.data.network.ConversationListUpdater
import com.nextcloud.talk.data.database.dao.ChatBlocksDao
import com.nextcloud.talk.data.database.dao.ChatMessagesDao
import com.nextcloud.talk.data.database.mappers.asEntity
import com.nextcloud.talk.data.database.mappers.toDomainModel
import com.nextcloud.talk.data.database.model.ChatBlockEntity
import com.nextcloud.talk.data.database.model.ChatMessageEntity
import com.nextcloud.talk.data.database.model.SendStatus
import com.nextcloud.talk.data.network.NetworkMonitor
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.extensions.toIntOrZero
import com.nextcloud.talk.logger.Logger
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.chat.ChatMessageJson
import com.nextcloud.talk.models.json.chat.ChatOverallSingleMessage
import com.nextcloud.talk.models.json.converters.EnumActorTypeConverter
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.models.json.participants.Participant
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.message.SendMessageUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import java.io.IOException
import javax.inject.Inject

@Suppress("LargeClass", "TooManyFunctions")
class OfflineFirstChatRepository @Inject constructor(
    private val logger: Logger,
    private val chatDao: ChatMessagesDao,
    private val chatBlocksDao: ChatBlocksDao,
    private val network: ChatNetworkDataSource,
    private val networkMonitor: NetworkMonitor,
    private val syncer: ChatMessageSyncer,
    private val conversationListUpdater: ConversationListUpdater
) : ChatMessageRepository {

    lateinit var currentUser: User

    // Placeholder rows sort by "timestamp ASC, id ASC" (ChatMessagesDao) same as real messages, but
    // id here is a hash of a random referenceId with no relation to call order - so if several
    // files are sent at once and their (second-granularity) timestamps tie, as is near-certain,
    // they'd otherwise sort arbitrarily instead of in the order they were actually sent. Tracking
    // the last-used value and never handing out the same one twice keeps placeholders strictly in
    // call order regardless of how many land within the same wall-clock second.
    private var lastPlaceholderTimestampSeconds = 0L

    private fun nextPlaceholderTimestampSeconds(): Long =
        synchronized(this) {
            val timestamp = maxOf(System.currentTimeMillis() / MILLIES, lastPlaceholderTimestampSeconds + 1)
            lastPlaceholderTimestampSeconds = timestamp
            timestamp
        }

    override val messageFlow:
        Flow<
            Triple<
                Boolean,
                Boolean,
                List<ChatMessage>
                >
            >
        get() = _messageFlow

    private val _messageFlow:
        MutableSharedFlow<
            Triple<
                Boolean,
                Boolean,
                List<ChatMessage>
                >
            > = MutableSharedFlow()

    override val updateMessageFlow: Flow<ChatMessage>
        get() = _updateMessageFlow

    private val _updateMessageFlow:
        MutableSharedFlow<ChatMessage> = MutableSharedFlow()

    override val lastCommonReadFlow: Flow<Int>
        get() = _lastCommonReadFlow

    private val _lastCommonReadFlow:
        MutableSharedFlow<Int> = MutableSharedFlow(replay = 1)

    override val lastReadMessageFlow: Flow<Int>
        get() = _lastReadMessageFlow

    private val _lastReadMessageFlow:
        MutableSharedFlow<Int> = MutableSharedFlow()

    override val roomRefreshFlow: Flow<Unit>
        get() = _roomRefreshFlow

    private val _roomRefreshFlow: MutableSharedFlow<Unit> = MutableSharedFlow()

    override val incomingMessageFlow: Flow<Unit>
        get() = _incomingMessageFlow

    private val _incomingMessageFlow: MutableSharedFlow<Unit> = MutableSharedFlow()

    override val isLoadingFlow: Flow<Boolean>
        get() = _isLoadingFlow

    private val _isLoadingFlow: MutableStateFlow<Boolean> = MutableStateFlow(false)

    private var newXChatLastCommonRead: Int? = null
    private var itIsPaused = false

    lateinit var internalConversationId: String
    private lateinit var roomToken: String
    private lateinit var conversationModel: ConversationModel
    private lateinit var credentials: String
    private lateinit var urlForChatting: String
    private var threadId: Long? = null

    private val requestedParentIds = mutableSetOf<Long>()

    override fun initData(
        currentUser: User,
        credentials: String,
        urlForChatting: String,
        roomToken: String,
        threadId: Long?
    ) {
        this.currentUser = currentUser
        this.roomToken = roomToken

        internalConversationId = currentUser.id.toString() + "@" + roomToken
        this.credentials = credentials
        this.urlForChatting = urlForChatting
        this.threadId = threadId
    }

    override fun updateConversation(conversationModel: ConversationModel) {
        this.conversationModel = conversationModel
    }

    private val syncTarget: ChatMessageSyncer.SyncTarget
        get() = ChatMessageSyncer.SyncTarget(
            user = currentUser,
            roomToken = roomToken,
            threadId = threadId,
            credentials = credentials,
            urlForChatting = urlForChatting
        )

    private val syncEvents = object : ChatMessageSyncer.Events {
        override suspend fun onLastCommonReadChanged(lastCommonRead: Int?) {
            newXChatLastCommonRead = lastCommonRead ?: newXChatLastCommonRead
            updateUiForLastCommonRead()
        }

        override suspend fun onLoadingChanged(isLoading: Boolean) {
            _isLoadingFlow.value = isLoading
        }

        override suspend fun onRoomRefreshNeeded() {
            _roomRefreshFlow.emit(Unit)
        }

        override suspend fun onIncomingMessagesFromOthers() {
            _incomingMessageFlow.emit(Unit)
        }
    }

    override suspend fun loadInitialMessages(withNetworkParams: Bundle) {
        logger.d(TAG, "---- loadInitialMessages ------------")
        cleanupExpiredMessages()
        newXChatLastCommonRead = conversationModel.lastCommonReadMessage
        updateUiForLastCommonRead()

        Log.d(TAG, "conversationModel.internalId: " + conversationModel.internalId)
        Log.d(TAG, "conversationModel.lastReadMessage:" + conversationModel.lastReadMessage)

        val latestBlock = chatBlocksDao.getLatestChatBlock(internalConversationId, threadId).first()
        Log.d(TAG, "latestBlock: ${latestBlock?.oldestMessageId}..${latestBlock?.newestMessageId}")

        if (latestBlock == null) {
            fetchInitialMessages(weAlreadyHaveSomeOfflineMessages = false)
            return
        }

        // The chat shows the messages of the latest chat block, and the unread marker can only be
        // placed correctly when that block reaches back to the last read message. A block floating
        // entirely above the boundary (e.g. created by a capped newest-messages fetch) must be
        // repaired by an anchored fetch — only closing the backlog above it would keep the marker
        // pinned to the block's oldest message, in the middle of the unread messages.
        val lastReadMessage = conversationModel.lastReadMessage.toLong()
        val blockNotFloatingAboveLastReadMessage = lastReadMessage <= 0 ||
            latestBlock.oldestMessageId <= lastReadMessage ||
            !latestBlock.hasHistory

        Log.d(TAG, "blockNotFloatingAboveLastReadMessage:$blockNotFloatingAboveLastReadMessage")

        if (blockNotFloatingAboveLastReadMessage) {
            tryCloseBacklogFromNewestOfflineMessage(latestBlock.newestMessageId)
        } else {
            fetchInitialMessages(weAlreadyHaveSomeOfflineMessages = true)
        }
    }

    /**
     * Tries to close the backlog since the newest offline message.
     */
    private suspend fun tryCloseBacklogFromNewestOfflineMessage(newestMessageIdFromDb: Long) {
        Log.d(TAG, "Try to close the backlog from the newest offline message for initial loading")

        syncer.tryCloseBacklog(
            target = syncTarget,
            fromMessageId = newestMessageIdFromDb,
            lastCommonRead = newXChatLastCommonRead,
            events = syncEvents
        )
    }

    /**
     * Fetches the initial message window via [ChatMessageSyncer.initialCatchUp]: anchored at the
     * last read message when a large unread backlog exists, the newest messages otherwise.
     */
    private suspend fun fetchInitialMessages(weAlreadyHaveSomeOfflineMessages: Boolean) {
        if (!weAlreadyHaveSomeOfflineMessages) {
            Log.d(TAG, "An initial online request is made because offline chat is empty")
            if (networkMonitor.isOnline.value.not()) {
                // _generalUIFlow.emit(ChatActivity.NO_OFFLINE_MESSAGES_FOUND)
            }
        } else {
            Log.d(
                TAG,
                "An initial online request is made because the cached messages don't reach the " +
                    "lastReadMessage (gaps could be closed by scrolling up to merge the chatblocks)"
            )
        }

        Log.d(TAG, "Starting online request for initial loading")
        syncer.initialCatchUp(
            target = syncTarget,
            lastReadMessage = conversationModel.lastReadMessage,
            unreadMessages = conversationModel.unreadMessages,
            lastCommonRead = newXChatLastCommonRead,
            events = syncEvents
        )
    }

    override suspend fun startMessagePolling(hasHighPerformanceBackend: Boolean) {
        if (hasHighPerformanceBackend) {
            initInsuranceRequests()
        } else {
            initLongPolling()
        }
    }

    private suspend fun updateUiForLastCommonRead() {
        newXChatLastCommonRead?.let {
            _lastCommonReadFlow.emit(it)
        }
    }

    suspend fun initLongPolling() {
        Log.d(TAG, "---- initLongPolling ------------")

        val initialMessageId = chatBlocksDao.getNewestMessageIdFromChatBlocks(internalConversationId, threadId)
        Log.d(TAG, "initialMessageId for initLongPolling: $initialMessageId")

        var fieldMap = getFieldMap(
            lookIntoFuture = true,
            // timeout for first longpoll is 0, so "unread message" info is not shown if there were
            // initially no messages but someone writes us in the first 30 seconds.
            timeout = 0,
            includeLastKnown = false,
            lastKnown = initialMessageId.toInt()
        )

        val networkParams = Bundle()

        while (true) {
            if (!networkMonitor.isOnline.value || itIsPaused) {
                delay(HALF_SECOND)
            } else {
                // sync database with server
                // (This is a long blocking call because long polling (lookIntoFuture and timeout) is set)
                networkParams.putSerializable(BundleKeys.KEY_FIELD_MAP, fieldMap)

                Log.d(TAG, "Starting online request for long polling")
                getAndPersistMessages(networkParams)

                val newestMessage = chatBlocksDao.getNewestMessageIdFromChatBlocks(
                    internalConversationId,
                    threadId
                ).toInt()

                // update field map vars for next cycle
                fieldMap = getFieldMap(
                    lookIntoFuture = true,
                    timeout = 30,
                    includeLastKnown = false,
                    lastKnown = newestMessage
                )
            }
        }
    }

    suspend fun initInsuranceRequests() {
        Log.d(TAG, "---- initInsuranceRequests ------------")

        while (true) {
            delay(INSURANCE_REQUEST_DELAY)
            Log.d(TAG, "execute insurance request")

            fetchNewMessages()
        }
    }

    private suspend fun cleanupExpiredMessages() {
        syncer.cleanupExpiredMessages(internalConversationId)
    }

    /**
     * Fetches messages newer than the newest message known from HTTP syncs.
     *
     * The anchor deliberately EXCLUDES messages that were delivered via signaling: those extend
     * the latest chat block optimistically (assuming they are contiguous on top of it), and this
     * request is the insurance that verifies the assumption — anchoring on the chat block's
     * newest message would skip exactly the range it has to check. The anchor lives in the
     * singleton [ChatMessageSyncer], so it survives reopening the chat.
     *
     * No-ops when no HTTP sync has happened yet for this conversation: that means the initial
     * load hasn't completed (or hasn't even started), which is already fetching this room from
     * scratch — anchoring at 0 instead would try to close the backlog from the very beginning of
     * the conversation's history, which for a busy room can't close within
     * [ChatMessageSyncer.tryCloseBacklog]'s round budget and just burns requests before falling
     * back anyway.
     *
     * @return `true` if at least one new message was received and persisted.
     */
    override suspend fun fetchNewMessages(): Boolean {
        cleanupExpiredMessages()

        val lastHttpSyncedMessageId = syncer.lastHttpSyncedMessageId(internalConversationId, threadId) ?: run {
            Log.d(TAG, "No HTTP-synced anchor yet for $internalConversationId, skipping insurance fetch")
            return false
        }

        // tryCloseBacklog loops until the backlog is fully closed, so a backlog larger than the
        // request limit is caught up within one insurance cycle instead of narrowing it by one
        // page every cycle.
        val outcome = syncer.tryCloseBacklog(
            target = syncTarget,
            fromMessageId = lastHttpSyncedMessageId,
            limit = 200,
            lastCommonRead = newXChatLastCommonRead,
            events = syncEvents
        )
        return outcome.persistedNewMessages
    }

    override suspend fun updateLocalReadState(lastReadMessage: Int) {
        conversationListUpdater.updateLocalReadState(syncTarget, lastReadMessage)
    }

    override fun markPendingReadMarker(lastReadMessage: Int) {
        conversationListUpdater.markPendingReadMarker(syncTarget.internalConversationId, lastReadMessage)
    }

    override suspend fun loadMoreMessages(
        anchorMessageId: Long,
        direction: ChatMessageRepository.LoadMoreDirection,
        roomToken: String,
        withMessageLimit: Int,
        withNetworkParams: Bundle
    ): ChatMessageRepository.MessagesRange? {
        Log.d(TAG, "---- loadMoreMessages for $anchorMessageId ($direction) ------------")

        val lookIntoFuture = direction == ChatMessageRepository.LoadMoreDirection.NEWER

        val fieldMap = getFieldMap(
            lookIntoFuture = lookIntoFuture,
            timeout = 0,
            includeLastKnown = true,
            lastKnown = anchorMessageId.toInt(),
            limit = withMessageLimit
        )
        withNetworkParams.putSerializable(BundleKeys.KEY_FIELD_MAP, fieldMap)

        Log.d(TAG, "Starting online request for loadMoreMessages")
        getAndPersistMessages(withNetworkParams)

        return syncer.getBlockOfMessage(syncTarget, anchorMessageId.toInt())?.let {
            ChatMessageRepository.MessagesRange(
                oldestMessageId = it.oldestMessageId,
                newestMessageId = it.newestMessageId
            )
        }
    }

    private fun getFieldMap(
        lookIntoFuture: Boolean,
        timeout: Int,
        includeLastKnown: Boolean,
        lastKnown: Int?,
        limit: Int = DEFAULT_MESSAGES_LIMIT
    ): HashMap<String, Int> =
        syncer.buildFieldMap(
            lookIntoFuture = lookIntoFuture,
            timeout = timeout,
            includeLastKnown = includeLastKnown,
            lastKnown = lastKnown,
            limit = limit,
            threadId = threadId,
            lastCommonRead = newXChatLastCommonRead
        )

    override suspend fun getNumberOfThreadReplies(threadId: Long): Int =
        chatDao.getNumberOfThreadReplies(internalConversationId, threadId)

    override fun getMessage(messageId: Long, bundle: Bundle): Flow<ChatMessage> =
        flow {
            val local = chatDao.getChatMessageEntity(internalConversationId, messageId)

            if (local != null) {
                emit(local.toDomainModel())
                return@flow
            }

            fetchAndPersistSingleMessage(messageId)

            emitAll(
                observeMessageNonNull(internalConversationId, messageId)
                    .map { it.toDomainModel() }
                    .take(1)
            )
        }

    private suspend fun fetchAndPersistSingleMessage(messageId: Long) {
        val messages = network.getContextForChatMessage(
            credentials = credentials,
            baseUrl = currentUser.baseUrl!!,
            token = roomToken,
            messageId = messageId.toString(),
            limit = 1,
            threadId = threadId?.toInt()
        )
        persistChatMessagesAndHandleSystemMessages(messages)
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    override suspend fun loadMessageContext(
        messageId: Long,
        limit: Int,
        threadId: Long?
    ): Result<ChatMessageRepository.MessagesRange> =
        runCatching {
            val messages = network.getContextForChatMessage(
                credentials = credentials,
                baseUrl = currentUser.baseUrl!!,
                token = roomToken,
                messageId = messageId.toString(),
                limit = limit,
                threadId = threadId?.toInt()
            )

            val filteredMessages = if (threadId == null) {
                messages.filter { !it.hasThread || it.threadId == it.id }
            } else {
                messages
            }

            require(filteredMessages.isNotEmpty()) { "No context messages returned" }

            val persisted = persistChatMessagesAndHandleSystemMessages(filteredMessages)
            val oldestId = persisted.minOf { it.id }
            val newestId = persisted.maxOf { it.id }

            val block = ChatBlockEntity(
                internalConversationId = internalConversationId,
                accountId = currentUser.id!!,
                token = roomToken,
                threadId = this.threadId,
                oldestMessageId = oldestId,
                newestMessageId = newestId,
                hasHistory = true
            )
            chatBlocksDao.upsertAndMergeConnectedChatBlocks(block)

            ChatMessageRepository.MessagesRange(
                oldestMessageId = oldestId,
                newestMessageId = newestId
            )
        }

    override fun observeParentMessages(parentIds: List<Long>): Flow<List<ChatMessage>> =
        chatDao.getMessagesFromIds(parentIds)
            .map { entities -> entities.map { it.toDomainModel() } }

    fun observeMessageNonNull(internalConversationId: String, messageId: Long): Flow<ChatMessageEntity> =
        chatDao.observeMessage(internalConversationId, messageId)
            .filterNotNull()

    override suspend fun getParentMessageById(messageId: Long): Flow<ChatMessage> =
        chatDao.getChatMessageForConversation(
            internalConversationId,
            messageId
        ).map(ChatMessageEntity::toDomainModel)

    override suspend fun fetchMissingParents(conversationId: String, parentIds: List<Long>) {
        val newIds = parentIds.filterNot { it in requestedParentIds }

        if (newIds.isEmpty()) return

        requestedParentIds.addAll(newIds)

        for (id in newIds) {
            try {
                val messages = network.getContextForChatMessage(
                    credentials = credentials,
                    baseUrl = currentUser.baseUrl!!,
                    token = roomToken,
                    messageId = id.toString(),
                    limit = 1,
                    threadId = null
                )

                val message = messages.firstOrNull { it.id == id } ?: continue
                chatDao.upsertChatMessage(message.asEntity(currentUser.id!!))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch parent message $id", e)
            }
        }
    }

    // Callers must put a KEY_FIELD_MAP (see getFieldMap/syncer.buildFieldMap) into bundle before
    // calling this.
    private suspend fun getAndPersistMessages(bundle: Bundle): Boolean {
        val fieldMap = requireNotNull(bundle.getSerializable(BundleKeys.KEY_FIELD_MAP) as? HashMap<String, Int>) {
            "getAndPersistMessages requires bundle to carry KEY_FIELD_MAP"
        }
        val outcome = syncer.pullAndPersistMessages(syncTarget, fieldMap, syncEvents)
        return outcome.persistedNewMessages
    }

    private fun isUntranslatedSystemMessage(messagesJson: List<ChatMessageJson>): Boolean =
        syncer.isUntranslatedSystemMessage(messagesJson)

    override fun handleOnPause() {
        itIsPaused = true
    }

    override fun handleOnResume() {
        itIsPaused = false
    }

    override fun handleOnStop() {
        // not used
    }

    @Suppress("LongParameterList")
    override suspend fun sendChatMessage(
        credentials: String,
        url: String,
        message: String,
        displayName: String,
        replyTo: Int,
        sendWithoutNotification: Boolean,
        referenceId: String,
        threadTitle: String?
    ): Flow<Result<ChatMessage?>> {
        if (!networkMonitor.isOnline.value) {
            return flow {
                emit(Result.failure(IOException("Skipped to send message as device is offline")))
            }
        }

        return flow {
            val response = network.sendChatMessage(
                credentials,
                url,
                message,
                displayName,
                replyTo,
                sendWithoutNotification,
                referenceId,
                threadTitle
            )

            val messageJson = response.ocs?.data
            val chatMessageModel = messageJson?.toDomainModel()

            if (messageJson != null && this@OfflineFirstChatRepository::internalConversationId.isInitialized) {
                // Persist the authoritative response immediately (correct isThread/threadId/threadTitle
                // included) instead of leaving the client-side guess in the temp row until the next sync -
                // otherwise a thread indicator only appears after reopening the chat.
                chatDao.upsertChatMessagesAndDeleteTemp(
                    internalConversationId,
                    listOf(messageJson.asEntity(currentUser.id!!))
                )
            }

            Log.d(TAG, "sending chat message succeeded: " + message)
            emit(Result.success(chatMessageModel))
        }
            .catch { e ->
                Log.e(TAG, "Error when sending message", e)

                val failedMessage = if (this@OfflineFirstChatRepository::internalConversationId.isInitialized) {
                    chatDao.getTempMessageForConversation(
                        internalConversationId,
                        referenceId,
                        threadId
                    ).firstOrNull()
                } else {
                    null
                }
                failedMessage?.let {
                    it.sendStatus = SendStatus.FAILED
                    chatDao.updateChatMessage(it)

                    val failedMessageModel = it.toDomainModel()
                    _updateMessageFlow.emit(failedMessageModel)
                }
                emit(Result.failure(e))
            }
    }

    @Suppress("LongParameterList")
    override suspend fun resendChatMessage(
        credentials: String,
        url: String,
        message: String,
        displayName: String,
        replyTo: Int,
        sendWithoutNotification: Boolean,
        referenceId: String
    ): Flow<Result<ChatMessage?>> {
        val messageToResend = chatDao.getTempMessageForConversation(
            internalConversationId,
            referenceId,
            threadId
        ).firstOrNull()
        return if (messageToResend != null) {
            messageToResend.sendStatus = SendStatus.PENDING
            chatDao.updateChatMessage(messageToResend)

            val messageToResendModel = messageToResend.toDomainModel()
            _updateMessageFlow.emit(messageToResendModel)

            sendChatMessage(
                credentials = credentials,
                url = url,
                message = message,
                displayName = displayName,
                replyTo = replyTo,
                sendWithoutNotification = sendWithoutNotification,
                referenceId = referenceId,
                threadTitle = null
            )
        } else {
            flow {
                emit(Result.failure(IllegalStateException("No temporary message found to resend")))
            }
        }
    }

    @Suppress("Detekt.TooGenericExceptionCaught", "LongParameterList")
    override suspend fun addTemporaryMessage(
        message: CharSequence,
        displayName: String,
        replyTo: Int,
        sendWithoutNotification: Boolean,
        referenceId: String,
        threadTitle: String?
    ): Flow<Result<ChatMessage?>> =
        flow {
            try {
                val tempChatMessageEntity = createChatMessageEntity(
                    internalConversationId,
                    message.toString(),
                    replyTo,
                    sendWithoutNotification,
                    referenceId,
                    threadTitle
                )
                chatDao.upsertChatMessage(tempChatMessageEntity)
            } catch (e: Exception) {
                Log.e(TAG, "Something went wrong when adding temporary message", e)
                emit(Result.failure(e))
            }
        }

    @Suppress("Detekt.TooGenericExceptionCaught", "LongMethod")
    override suspend fun addUploadPlaceholderMessage(
        localFileUri: String,
        fileName: String,
        caption: String,
        mimeType: String?,
        fileSize: Long,
        referenceId: String
    ): Flow<Result<ChatMessage?>> =
        flow {
            try {
                // Use referenceId.hashCode() as the placeholder id so that:
                // 1. It is unique per file even when multiple files are selected simultaneously
                // 2. It fits in an Int, so it survives the Long→Int cast in ChatMessageUi.id without
                //    truncation, keeping DB lookups consistent when the message is tapped.
                // 3. It is always negative -> sending the lastReadMessage to server checks "-1 < messageId"
                //    to avoid temporary/placeholder messages (see createChatMessageEntity)
                @Suppress("MagicNumber")
                val placeholderId = -(referenceId.hashCode().toLong() and 0x7FFF_FFFFL)

                Log.d(
                    TAG,
                    "addUploadPlaceholderMessage: referenceId=$referenceId " +
                        "placeholderId=$placeholderId caption=$caption"
                )

                val fileParams = hashMapOf<String?, String?>(
                    "type" to "file",
                    "name" to fileName,
                    "mimetype" to (mimeType ?: ""),
                    "size" to fileSize.toString(),
                    "path" to localFileUri
                )
                val messageParameters = hashMapOf<String?, HashMap<String?, String?>>(
                    "file" to fileParams
                )

                val entity = ChatMessageEntity(
                    internalId = "$internalConversationId@_temp_$referenceId",
                    internalConversationId = internalConversationId,
                    id = placeholderId,
                    threadId = threadId,
                    // "{file}" is the sentinel the server (and rest of this app) uses for "no caption"
                    message = caption.ifEmpty { "{file}" },
                    deleted = false,
                    token = conversationModel.token,
                    actorId = currentUser.userId!!,
                    actorType = EnumActorTypeConverter().convertToString(Participant.ActorType.USERS),
                    accountId = currentUser.id!!,
                    messageParameters = messageParameters,
                    messageType = "comment",
                    parentMessageId = null,
                    systemMessageType = ChatMessage.SystemMessageType.DUMMY,
                    replyable = false,
                    timestamp = nextPlaceholderTimestampSeconds(),
                    expirationTimestamp = 0,
                    actorDisplayName = currentUser.displayName!!,
                    referenceId = referenceId,
                    isTemporary = true,
                    sendStatus = SendStatus.PENDING,
                    silent = false
                )
                chatDao.upsertChatMessage(entity)
            } catch (e: Exception) {
                Log.e(TAG, "addUploadPlaceholderMessage failed for referenceId=$referenceId", e)
                emit(Result.failure(e))
            }
        }

    override suspend fun deleteTempMessageByReferenceId(referenceId: String): Boolean =
        chatDao.deleteTempChatMessageIfPending(internalConversationId, referenceId) > 0

    @Suppress("Detekt.TooGenericExceptionCaught")
    override suspend fun editChatMessage(
        credentials: String,
        url: String,
        text: String
    ): Flow<Result<ChatOverallSingleMessage>> =
        flow {
            try {
                val response = network.editChatMessage(
                    credentials,
                    url,
                    text
                )
                emit(Result.success(response))
            } catch (e: Exception) {
                emit(Result.failure(e))
            }
        }

    @Suppress("Detekt.TooGenericExceptionCaught")
    override suspend fun editTempChatMessage(message: ChatMessage, editedMessageText: String): Flow<Boolean> =
        flow {
            try {
                val messageToEdit = chatDao.getChatMessageForConversation(
                    internalConversationId,
                    message.jsonMessageId.toLong()
                ).first()
                messageToEdit.message = editedMessageText
                chatDao.upsertChatMessage(messageToEdit)

                val editedMessageModel = messageToEdit.toDomainModel()
                _updateMessageFlow.emit(editedMessageModel)
                emit(true)
            } catch (e: Exception) {
                emit(false)
            }
        }

    override suspend fun sendUnsentChatMessages(credentials: String, url: String) {
        val tempMessages = chatDao.getTempUnsentMessagesForConversation(internalConversationId, threadId).first()
        // File-upload placeholders are also temporary messages, but they must never be resent as plain
        // text here: their "message" field is just the "{file}" sentinel, and a failed/interrupted upload
        // needs a real re-upload, not a bogus text message reusing its referenceId.
        val unsentTextMessages = tempMessages.filterNot { it.messageParameters?.containsKey("file") == true }
        unsentTextMessages.sortedBy { it.internalId }.onEach {
            sendChatMessage(
                credentials,
                url,
                it.message,
                it.actorDisplayName,
                it.parentMessageId?.toIntOrZero() ?: 0,
                it.silent,
                it.referenceId.orEmpty(),
                null
            ).collect { result ->
                if (result.isSuccess) {
                    Log.d(TAG, "Sent temp message")
                } else {
                    Log.e(TAG, "Failed to send temp message")
                }
            }
        }
    }

    override suspend fun deleteTempMessage(chatMessage: ChatMessage) {
        chatDao.deleteTempChatMessages(internalConversationId, listOf(chatMessage.referenceId.orEmpty()))
    }

    override suspend fun pinMessage(credentials: String, url: String, pinUntil: Int): Flow<ChatMessage?> =
        flow {
            runCatching {
                val overall = network.pinMessage(credentials, url, pinUntil)
                emit(overall.ocs?.data?.toDomainModel())
            }.getOrElse { throwable ->
                Log.e(TAG, "Error in pinMessage: $throwable")
            }
        }

    override suspend fun unPinMessage(credentials: String, url: String): Flow<ChatMessage?> =
        flow {
            runCatching {
                val overall = network.unPinMessage(credentials, url)
                emit(overall.ocs?.data?.toDomainModel())
            }.getOrElse { throwable ->
                Log.e(TAG, "Error in unPinMessage: $throwable")
            }
        }

    override suspend fun hidePinnedMessage(credentials: String, url: String): Flow<Boolean> =
        flow {
            runCatching {
                network.hidePinnedMessage(credentials, url)
                emit(true)
            }.getOrElse { throwable ->
                Log.e(TAG, "Error in hidePinnedMessage: $throwable")
            }
        }

    override suspend fun onSignalingChatMessageReceived(chatMessages: List<ChatMessageJson>) {
        // check if we need to get user specific data from the backend
        if (!isUntranslatedSystemMessage(chatMessages) ||
            chatMessages.any { it.messageParameters?.containsKey("file") == true }
        ) {
            Log.d(TAG, "onSignalingChatMessageReceived force data refresh")
            fetchNewMessages()
            return
        }

        persistChatMessagesAndHandleSystemMessages(chatMessages, emitOnIncoming = true)

        // we assume that the signaling messages are on top of the latest chatblock and include them inside it.
        // If for whatever reason the assumption was not correct and there would be messages in between, the
        // insurance request should fix this by adding the missing messages and updating the chatblocks.
        val latestChatBlock = chatBlocksDao.getLatestChatBlock(internalConversationId, threadId)
        latestChatBlock.first()?.apply {
            newestMessageId = chatMessages.maxOf { it.id }
            chatBlocksDao.upsertChatBlock(this)
        }
    }

    suspend fun persistChatMessagesAndHandleSystemMessages(
        chatMessages: List<ChatMessageJson>,
        emitOnIncoming: Boolean = false
    ): List<ChatMessageEntity> =
        syncer.persistChatMessagesAndHandleSystemMessages(syncTarget, chatMessages, emitOnIncoming, syncEvents)

    override fun observeLatestMessages(internalConversationId: String): Flow<List<ChatMessageEntity>> =
        chatBlocksDao
            .getLatestChatBlock(internalConversationId, threadId)
            .distinctUntilChanged()
            .flatMapLatest { latestBlock ->

                if (latestBlock == null) {
                    flowOf(emptyList())
                } else {
                    chatDao.getMessagesEqualOrNewerThan(
                        internalConversationId = internalConversationId,
                        threadId = threadId,
                        oldestMessageId = latestBlock.oldestMessageId
                    )
                }
            }

    override fun observeMessagesForAnchor(
        internalConversationId: String,
        anchorMessageId: Long
    ): Flow<List<ChatMessageEntity>> =
        chatBlocksDao
            .getChatBlocksContainingMessageId(
                internalConversationId = internalConversationId,
                threadId = threadId,
                messageId = anchorMessageId
            )
            .distinctUntilChanged()
            .flatMapLatest { blocks ->
                val block = blocks.firstOrNull()
                if (block == null) {
                    flowOf(emptyList())
                } else {
                    chatDao.getMessagesInRange(
                        internalConversationId = internalConversationId,
                        threadId = threadId,
                        oldestMessageId = block.oldestMessageId,
                        newestMessageId = block.newestMessageId
                    )
                }
            }

    @Suppress("LongParameterList")
    override suspend fun sendScheduledChatMessage(
        credentials: String,
        url: String,
        message: String,
        replyTo: Int?,
        sendWithoutNotification: Boolean,
        threadTitle: String?,
        threadId: Long?,
        sendAt: Int?
    ): Flow<Result<ChatOverallSingleMessage>> =
        flow {
            val response = network.sendScheduledChatMessage(
                credentials,
                url,
                message,
                replyTo,
                sendWithoutNotification,
                threadTitle,
                threadId,
                sendAt
            )
            emit(Result.success(response))
        }.catch { e ->
            Log.e(TAG, "Error when scheduling message", e)
            emit(Result.failure(e))
        }

    override suspend fun updateScheduledChatMessage(
        credentials: String,
        url: String,
        message: String,
        sendAt: Int?,
        sendWithoutNotification: Boolean
    ): Flow<Result<ChatMessage>> =
        flow {
            val response = network.updateScheduledMessage(
                credentials,
                url,
                message,
                sendAt,
                sendWithoutNotification
            )

            val messageJson = response.ocs?.data
                ?: error("updateScheduledMessage: response.ocs?.data is null")

            val updatedMessage = messageJson.toDomainModel().copy(
                token = messageJson.id.toString()
            )

            emit(Result.success(updatedMessage))
        }.catch { e ->
            Log.e(TAG, "Error when updating scheduled message", e)
            emit(Result.failure(e))
        }

    override suspend fun deleteScheduledChatMessage(credentials: String, url: String): Flow<Result<GenericOverall>> =
        flow {
            val response = network.deleteScheduledMessage(credentials, url)
            emit(Result.success(response))
        }.catch { e ->
            Log.e(TAG, "Error when deleting scheduled message", e)
            emit(Result.failure(e))
        }

    override suspend fun getScheduledChatMessages(credentials: String, url: String): Flow<Result<List<ChatMessage>>> =
        flow {
            val response = network.getScheduledMessages(credentials, url)
            val messages = response.ocs?.data.orEmpty().map { messageJson ->
                val jsonToModel = messageJson.toDomainModel()
                jsonToModel.copy(
                    token = messageJson.id.toString()
                )
            }
            emit(Result.success(messages))
            Log.d("Get Scheduled", "$messages")
        }.catch { e ->
            Log.e(TAG, "Error when fetching scheduled messages", e)
            emit(Result.failure(e))
        }

    @Suppress("LongParameterList")
    private fun createChatMessageEntity(
        internalConversationId: String,
        message: String,
        replyTo: Int,
        sendWithoutNotification: Boolean,
        referenceId: String,
        threadTitle: String?
    ): ChatMessageEntity {
        val currentTimeMillis = System.currentTimeMillis()

        // only use the time of a day so it can fit into an integer
        val timeOfDayMillis = SendMessageUtils().timeOfDayMillis(currentTimeMillis)

        // make it negative -> sending the lastReadMessage to server checks "-1 < messageId" to avoid temporary messages
        val negativeTimeOfDayMillis = timeOfDayMillis * MINUS_ONE

        val parentMessageId = if (replyTo != 0) {
            replyTo.toLong()
        } else {
            null
        }

        val entity = ChatMessageEntity(
            internalId = "$internalConversationId@_temp_$currentTimeMillis",
            internalConversationId = internalConversationId,
            id = negativeTimeOfDayMillis,
            threadId = threadId,
            isThread = threadTitle != null,
            threadTitle = threadTitle,
            message = message,
            deleted = false,
            token = conversationModel.token,
            actorId = currentUser.userId!!,
            actorType = EnumActorTypeConverter().convertToString(Participant.ActorType.USERS),
            accountId = currentUser.id!!,
            messageParameters = null,
            messageType = "comment",
            parentMessageId = parentMessageId,
            systemMessageType = ChatMessage.SystemMessageType.DUMMY,
            replyable = false,
            timestamp = currentTimeMillis / MILLIES,
            expirationTimestamp = 0,
            actorDisplayName = currentUser.displayName!!,
            referenceId = referenceId,
            isTemporary = true,
            sendStatus = SendStatus.PENDING,
            silent = sendWithoutNotification
        )
        return entity
    }

    companion object {
        val TAG: String = OfflineFirstChatRepository::class.java.simpleName
        private const val HALF_SECOND = 500L
        private const val DEFAULT_MESSAGES_LIMIT = 100
        private const val MILLIES = 1000L
        private const val INSURANCE_REQUEST_DELAY = 2 * 60 * MILLIES

        private const val MINUS_ONE = -1L
    }
}
