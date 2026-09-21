/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-FileCopyrightText: 2024 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationlist.data.network

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.net.ConnectivityManager
import android.util.Log
import com.nextcloud.talk.arbitrarystorage.ArbitraryStorageManager
import com.nextcloud.talk.chat.data.network.ChatMessageSyncer
import com.nextcloud.talk.chat.data.network.ChatNetworkDataSource
import com.nextcloud.talk.conversationlist.data.OfflineConversationsRepository
import com.nextcloud.talk.data.database.dao.ConversationsDao
import com.nextcloud.talk.data.database.mappers.asEntity
import com.nextcloud.talk.data.database.mappers.toDomainModel
import com.nextcloud.talk.data.database.model.ConversationEntity
import com.nextcloud.talk.data.network.NetworkMonitor
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.extensions.isPowerSaveMode
import com.nextcloud.talk.logger.Logger
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.CapabilitiesUtil.isUserStatusAvailable
import com.nextcloud.talk.utils.SpreedFeatures
import com.nextcloud.talk.utils.withRetry
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import kotlin.collections.map
import kotlin.time.Duration.Companion.milliseconds

@Suppress("LongParameterList", "TooManyFunctions")
class OfflineFirstConversationsRepository @Inject constructor(
    private val dao: ConversationsDao,
    private val network: ConversationsNetworkDataSource,
    private val chatNetworkDataSource: ChatNetworkDataSource,
    private val networkMonitor: NetworkMonitor,
    private val chatMessageSyncer: ChatMessageSyncer,
    private val conversationListUpdater: ConversationListUpdater,
    private val arbitraryStorageManager: ArbitraryStorageManager,
    private val context: Context,
    private val logger: Logger
) : OfflineConversationsRepository {
    /**
     * The conversation list as a live view of the local database — the single source of truth.
     * Every write to the conversations table (room list sync, background message catch-up,
     * optimistic read state, drafts) reaches collectors reactively; [getRooms] only triggers the
     * background sync, which stays in place as the authority and self-healing safeguard.
     */
    override fun roomListFlow(accountId: Long): Flow<List<ConversationModel>> =
        dao.getConversationsForUser(accountId)
            .map { entities -> entities.map(ConversationEntity::toDomainModel) }
            .distinctUntilChanged()

    override val conversationFlow: Flow<ConversationModel>
        get() = _conversationFlow
    private val _conversationFlow: MutableSharedFlow<ConversationModel> = MutableSharedFlow()

    override val syncErrorFlow: Flow<Throwable>
        get() = _syncErrorFlow
    private val _syncErrorFlow: MutableSharedFlow<Throwable> = MutableSharedFlow()

    private val scope = CoroutineScope(Dispatchers.IO)

    sealed interface ConversationResult {
        data class Found(val conversation: ConversationModel) : ConversationResult
        object NotFound : ConversationResult
    }

    override fun observeConversation(accountId: Long, roomToken: String): Flow<ConversationResult> =
        dao.getConversationForUser(
            accountId,
            roomToken
        )
            .map { entity ->
                if (entity == null) {
                    ConversationResult.NotFound
                } else {
                    ConversationResult.Found(entity.toDomainModel())
                }
            }

    override fun getRooms(user: User, forceFullSync: Boolean): Job =
        scope.launch {
            if (networkMonitor.isOnline.value) {
                val roomsWithNewMessages = getRoomsFromServer(
                    user,
                    forceFullSync = forceFullSync,
                    reportSyncError = true
                ) ?: return@launch
                // a launch of its own, so the returned job completes without waiting for the catch-up
                scope.launch { catchUpRooms(user, roomsWithNewMessages) }
            }
        }

    override suspend fun syncRooms(
        user: User,
        forceFullSync: Boolean,
        roomListTimeoutMillis: Long?
    ): List<ConversationEntity>? =
        // the sync blocks its thread on the request and on stored state, so it must not run on the caller's
        withContext(Dispatchers.IO) {
            getRoomsFromServer(
                user,
                forceFullSync = forceFullSync,
                roomListTimeoutMillis = roomListTimeoutMillis,
                reportSyncError = false
            )
        }

    @Suppress("Detekt.TooGenericExceptionCaught")
    override fun getRoom(user: User, roomToken: String): Job =
        scope.launch {
            try {
                val model = chatNetworkDataSource.getRoom(user, roomToken)
                val existingEntity = dao.getConversationForUser(user.id!!, model.token).first()
                model.hiddenUpcomingEvent = existingEntity?.hiddenUpcomingEvent
                _conversationFlow.emit(model)
                val previous = existingEntity?.let { mapOf(it.internalId to it) }.orEmpty()
                val entityList = conversationListUpdater.preservePendingLocalState(
                    previous,
                    listOf(model.asEntity())
                )
                try {
                    dao.upsertConversations(user.id!!, entityList)
                } catch (e: SQLiteConstraintException) {
                    Log.w(TAG, "Skipping conversation upsert for removed account ${user.id}", e)
                }
            } catch (e: Exception) {
                // In case network is offline, the call fails, or getRoom can't resolve a supported
                // conversation API version (e.g. capabilities not loaded yet)
                fallBackToLocalConversation(user, roomToken, e)
            }
        }

    private suspend fun fallBackToLocalConversation(user: User, roomToken: String, e: Throwable) {
        logger.e(TAG, "Failed to fetch room $roomToken from server", e)
        val id = user.id!!
        val model = getConversation(id, roomToken)
        if (model != null) {
            _conversationFlow.emit(model)
        } else {
            Log.e(TAG, "Conversation model not found on device database")
        }
    }

    override suspend fun updateConversation(conversationModel: ConversationModel) {
        val entity = conversationModel.asEntity()
        dao.updateConversation(entity)
    }

    override suspend fun getLocallyStoredConversation(user: User, roomToken: String): ConversationModel? {
        val id = user.id!!
        return getConversation(id, roomToken)
    }

    /**
     * Syncs [user]'s room list, returning the rooms whose messages should be caught up, or null
     * when the sync failed.
     *
     * [reportSyncError] lets a failure reach [syncErrorFlow]. That flow does not say which account
     * failed and the conversation list shows whatever arrives there, so only a sync of the account
     * the list shows may report to it.
     */
    @Suppress("Detekt.TooGenericExceptionCaught")
    private suspend fun getRoomsFromServer(
        user: User,
        forceFullSync: Boolean = false,
        roomListTimeoutMillis: Long? = null,
        reportSyncError: Boolean
    ): List<ConversationEntity>? {
        val accountId = user.id!!
        val modifiedSince = modifiedSinceFor(user, forceFullSync)

        return try {
            if (roomListTimeoutMillis == null) {
                syncRoomList(user, modifiedSince)
            } else {
                withTimeout(roomListTimeoutMillis.milliseconds) { syncRoomList(user, modifiedSince) }
            }
        } catch (e: TimeoutCancellationException) {
            // the caller's run goes on, so this is a failed sync rather than a cancellation
            Log.w(TAG, "Room list sync for account $accountId ran out of its time budget")
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Something went wrong when fetching conversations", e)
            storeTimestamp(accountId, KEY_MODIFIED_SINCE, null)
            if (reportSyncError && dao.getConversationsForUser(accountId).first().isEmpty()) {
                _syncErrorFlow.emit(e)
            }
            null
        }
    }

    /**
     * Fetches [user]'s room list, filtered by [modifiedSince] when set, and stores it, returning the
     * rooms whose messages should be caught up.
     */
    private suspend fun syncRoomList(user: User, modifiedSince: Long?): List<ConversationEntity> {
        val accountId = user.id!!
        val includeStatus = modifiedSince == null && isUserStatusAvailable(user)

        val roomList = withRetry(
            retries = NETWORK_FETCH_RETRIES,
            initialDelayMillis = NETWORK_FETCH_RETRY_INITIAL_DELAY_MS,
            maxDelayMillis = NETWORK_FETCH_RETRY_MAX_DELAY_MS
        ) {
            network.getRooms(user, user.baseUrl!!, includeStatus, modifiedSince)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .blockingSingle()
        }

        val conversationsFromSync = roomList.conversations.map {
            it.asEntity(accountId)
        }

        val previousConversations = dao.getConversationsForUser(accountId).first()
            .associateBy { it.internalId }

        val serverItems = if (includeStatus) {
            conversationsFromSync
        } else {
            keepCachedStatus(conversationsFromSync, previousConversations)
        }

        dao.syncConversationsForUser(
            accountId = accountId,
            serverItems = conversationListUpdater.preservePendingLocalState(
                previousConversations,
                serverItems
            ),
            conversationIdsToDelete = if (roomList.wasDelta) {
                emptyList()
            } else {
                determineLeftConversationIds(previousConversations, conversationsFromSync)
            }
        )

        rememberSyncedState(accountId, roomList)

        return getRoomsWithNewMessages(conversationsFromSync, previousConversations)
    }

    override fun requireFullSync(accountId: Long) {
        storeTimestamp(accountId, KEY_LAST_FULL_SYNC_AT, null)
    }

    override suspend fun lastFullSyncAt(accountId: Long): Long? =
        withContext(Dispatchers.IO) { readTimestamp(accountId, KEY_LAST_FULL_SYNC_AT) }

    /**
     * The stored timestamp to send as `modifiedSince`, or null when the sync has to fetch the whole
     * list.
     *
     * Null is returned when [forceFullSync] is set, when the account uses the internal signaling
     * backend, when the last full sync is older than [FULL_SYNC_INTERVAL_MILLIS], and when no
     * timestamp is stored.
     */
    private fun modifiedSinceFor(user: User, forceFullSync: Boolean): Long? {
        val accountId = user.id!!
        val usesExternalSignaling = !user.externalSignalingServer?.externalSignalingServer.isNullOrEmpty()

        return if (!forceFullSync && usesExternalSignaling && fullSyncIsRecent(accountId)) {
            readTimestamp(accountId, KEY_MODIFIED_SINCE)
        } else {
            null
        }
    }

    private fun fullSyncIsRecent(accountId: Long): Boolean {
        val lastFullSyncAt = readTimestamp(accountId, KEY_LAST_FULL_SYNC_AT) ?: return false
        return System.currentTimeMillis() - lastFullSyncAt in 0 until FULL_SYNC_INTERVAL_MILLIS
    }

    override suspend fun isPeriodicSyncDue(user: User): Boolean =
        withContext(Dispatchers.IO) {
            modifiedSinceFor(user, forceFullSync = false) != null || !fullSyncIsRecent(user.id!!)
        }

    /**
     * Stores the timestamp [roomList] reported for the next request, and, when it was a full
     * response, the time of this full sync. Called after the response has been written to the
     * database.
     */
    private fun rememberSyncedState(accountId: Long, roomList: RoomListResult) {
        storeTimestamp(accountId, KEY_MODIFIED_SINCE, roomList.modifiedBefore)
        if (!roomList.wasDelta) {
            storeTimestamp(accountId, KEY_LAST_FULL_SYNC_AT, System.currentTimeMillis())
        }
    }

    /** The timestamp stored under [key] for [accountId], or null when none is stored or it is not a positive number. */
    private fun readTimestamp(accountId: Long, key: String): Long? =
        arbitraryStorageManager.getStorageSetting(accountId, key, "")
            .blockingGet()
            ?.value
            ?.toLongOrNull()
            ?.takeIf { it > 0 }

    private fun storeTimestamp(accountId: Long, key: String, value: Long?) {
        arbitraryStorageManager.storeStorageSetting(accountId, key, value?.toString(), "")
    }

    /**
     * Determines the rooms whose messages should be caught up in the background: rooms with
     * activity newer than the last synced state (matching the iOS behavior) plus unread rooms that
     * have no cached messages yet (never-opened rooms).
     */
    private fun getRoomsWithNewMessages(
        conversationsFromSync: List<ConversationEntity>,
        previousConversations: Map<String, ConversationEntity>
    ): List<ConversationEntity> =
        conversationsFromSync.filter { room ->
            val previous = previousConversations[room.internalId]
            val activityAdvanced = previous == null || room.lastActivity > previous.lastActivity
            activityAdvanced ||
                (room.unreadMessages > 0 && !chatMessageSyncer.hasLocalChatBlock(room.internalId, null))
        }

    /**
     * Prefetches the messages of [rooms] into the local database so they are instantly visible
     * when a chat is opened. Runs after the room list sync; failures are logged and never affect
     * the conversation list itself, and cancellation propagates.
     *
     * The catch-up is skipped in battery saver mode and when background data is restricted on a
     * metered network (mirroring the Low Power Mode guard on iOS), and is bounded to the
     * [MAX_ROOMS_TO_CATCH_UP] most recently active rooms with [MAX_CONCURRENT_CATCH_UPS] parallel
     * requests, so a fresh install with many rooms cannot cause an unbounded request burst.
     */
    override suspend fun catchUpRooms(user: User, rooms: List<ConversationEntity>) {
        if (rooms.isEmpty() || !isCatchUpAllowed(user)) {
            return
        }

        val credentials = ApiUtils.getCredentials(user.username, user.token) ?: return

        val cappedRooms = rooms
            .sortedByDescending { it.lastActivity }
            .take(MAX_ROOMS_TO_CATCH_UP)
        if (cappedRooms.size < rooms.size) {
            Log.w(TAG, "Capping message catch-up to ${cappedRooms.size} of ${rooms.size} rooms")
        }

        Log.d(TAG, "Catching up messages for ${cappedRooms.size} rooms")
        coroutineScope {
            val semaphore = Semaphore(MAX_CONCURRENT_CATCH_UPS)
            cappedRooms.forEach { room ->
                launch {
                    semaphore.withPermit {
                        val target = ChatMessageSyncer.SyncTarget(
                            user = user,
                            roomToken = room.token,
                            threadId = null,
                            credentials = credentials,
                            urlForChatting = ApiUtils.getUrlForChat(CHAT_API_VERSION, user.baseUrl!!, room.token)
                        )
                        runCatching {
                            chatMessageSyncer.catchUpRoom(
                                target = target,
                                lastReadMessage = room.lastReadMessage,
                                unreadMessages = room.unreadMessages
                            )
                        }
                            .onFailure {
                                if (it is CancellationException) throw it
                                Log.e(TAG, "Message catch-up failed for room ${room.token}", it)
                            }
                    }
                }
            }
        }
    }

    private fun isCatchUpAllowed(user: User): Boolean =
        when {
            !user.hasSpreedFeatureCapability(SpreedFeatures.CHAT_KEEP_NOTIFICATIONS.value) -> {
                Log.d(TAG, "Server lacks ${SpreedFeatures.CHAT_KEEP_NOTIFICATIONS.value}, skipping message catch-up")
                false
            }

            context.isPowerSaveMode() -> {
                Log.d(TAG, "Battery saver is active, skipping message catch-up")
                false
            }

            isBackgroundDataRestricted() -> {
                Log.d(TAG, "Background data is restricted on a metered network, skipping message catch-up")
                false
            }

            else -> true
        }

    private fun isBackgroundDataRestricted(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return connectivityManager.isActiveNetworkMetered &&
            connectivityManager.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
    }

    /**
     * Returns [conversations] with the stored user status of each, for a response that did not
     * carry one. The upsert replaces the row it writes, so a response fetched without
     * `includeStatus` would otherwise clear the status of every conversation it covers.
     */
    private fun keepCachedStatus(
        conversations: List<ConversationEntity>,
        previousConversations: Map<String, ConversationEntity>
    ): List<ConversationEntity> =
        conversations.map { item ->
            val previous = previousConversations[item.internalId] ?: return@map item
            item.copy(
                status = previous.status,
                statusClearAt = previous.statusClearAt,
                statusIcon = previous.statusIcon,
                statusMessage = previous.statusMessage
            )
        }

    private fun determineLeftConversationIds(
        previousConversations: Map<String, ConversationEntity>,
        conversationsFromSync: List<ConversationEntity>
    ): List<String> {
        if (conversationsFromSync.isEmpty() && previousConversations.isNotEmpty()) {
            // A sync that suddenly contains no conversations at all is most likely a broken or
            // partial server response. Deleting the local conversations in that case would also
            // wipe their cached chat messages and chat blocks via foreign key cascade, destroying
            // the offline cache. Skip and let a later successful sync reconcile.
            Log.w(
                TAG,
                "Sync returned no conversations while ${previousConversations.size} exist locally, " +
                    "skipping deletion of left conversations"
            )
            return emptyList()
        }

        val conversationsFromSyncIds = conversationsFromSync.map { it.internalId }.toSet()

        return previousConversations.keys.filterNot { it in conversationsFromSyncIds }
    }

    private suspend fun getConversation(accountId: Long, token: String): ConversationModel? {
        val entity = dao.getConversationForUser(accountId, token).first()
        return entity?.toDomainModel()
    }

    companion object {
        val TAG = OfflineFirstConversationsRepository::class.java.simpleName
        private const val CHAT_API_VERSION = 1
        private const val MAX_ROOMS_TO_CATCH_UP = 20
        private const val MAX_CONCURRENT_CATCH_UPS = 3
        private const val NETWORK_FETCH_RETRIES = 3
        private const val NETWORK_FETCH_RETRY_INITIAL_DELAY_MS = 1000L
        private const val NETWORK_FETCH_RETRY_MAX_DELAY_MS = 8000L
        private const val FULL_SYNC_INTERVAL_MILLIS = 5 * 60 * 1000L
        private const val KEY_MODIFIED_SINCE = "conversation_list_modified_since"
        private const val KEY_LAST_FULL_SYNC_AT = "conversation_list_last_full_sync_at"
    }
}
