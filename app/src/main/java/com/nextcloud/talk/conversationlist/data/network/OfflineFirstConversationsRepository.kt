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
import com.nextcloud.talk.utils.CapabilitiesUtil
import com.nextcloud.talk.utils.CapabilitiesUtil.isUserStatusAvailable
import com.nextcloud.talk.utils.SpreedFeatures
import com.nextcloud.talk.utils.withRetry
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.collections.map

@Suppress("LongParameterList")
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
    private val observedAccountId = MutableStateFlow<Long?>(null)

    /**
     * The conversation list as a live view of the local database — the single source of truth.
     * Every write to the conversations table (room list sync, background message catch-up,
     * optimistic read state, drafts) reaches collectors reactively; [getRooms] only selects the
     * account to observe and triggers the background sync, which stays in place as the authority
     * and self-healing safeguard.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    override val roomListFlow: Flow<List<ConversationModel>> =
        observedAccountId
            .filterNotNull()
            .distinctUntilChanged()
            .flatMapLatest { accountId ->
                dao.getConversationsForUser(accountId)
                    .map { entities -> entities.map(ConversationEntity::toDomainModel) }
            }
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
            val accountChanged = observedAccountId.value != user.id
            observedAccountId.value = user.id!!

            if (networkMonitor.isOnline.value) {
                getRoomsFromServer(user, forceFullSync = forceFullSync || accountChanged)
            }
        }

    override suspend fun syncRooms(user: User, forceFullSync: Boolean): Boolean =
        getRoomsFromServer(user, forceFullSync = forceFullSync, awaitCatchUp = true) != null

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

    @Suppress("Detekt.TooGenericExceptionCaught")
    private suspend fun getRoomsFromServer(
        user: User,
        forceFullSync: Boolean = false,
        awaitCatchUp: Boolean = false
    ): List<ConversationEntity>? {
        var conversationsFromSync: List<ConversationEntity>? = null

        if (!networkMonitor.isOnline.value) {
            Log.d(TAG, "Device is offline, can't load conversations from server")
            return null
        }

        val accountId = user.id!!
        val modifiedSince = modifiedSinceFor(user, forceFullSync)

        val includeStatus = modifiedSince == null && isUserStatusAvailable(user)

        try {
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

            conversationsFromSync = roomList.conversations.map {
                it.asEntity(accountId)
            }

            val previousConversations = dao.getConversationsForUser(accountId).first()
                .associateBy { it.internalId }

            dao.syncConversationsForUser(
                accountId = accountId,
                serverItems = conversationListUpdater.preservePendingLocalState(
                    previousConversations,
                    conversationsFromSync
                ),
                conversationIdsToDelete = if (roomList.wasDelta) {
                    emptyList()
                } else {
                    determineLeftConversationIds(previousConversations, conversationsFromSync)
                }
            )

            rememberSyncedState(accountId, roomList)

            val roomsWithNewMessages = getRoomsWithNewMessages(conversationsFromSync, previousConversations)
            if (awaitCatchUp) {
                catchUpRoomsWithNewMessages(user, roomsWithNewMessages)
            } else {
                scope.launch { catchUpRoomsWithNewMessages(user, roomsWithNewMessages) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Something went wrong when fetching conversations", e)
            storeTimestamp(accountId, KEY_MODIFIED_SINCE, null)
            val hasCachedConversations = dao.getConversationsForUser(accountId).first().isNotEmpty()
            if (!hasCachedConversations) {
                _syncErrorFlow.emit(e)
            }
        }
        return conversationsFromSync
    }

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
        val lastFullSyncAt = readTimestamp(accountId, KEY_LAST_FULL_SYNC_AT)
        val fullSyncIsRecent = lastFullSyncAt != null &&
            System.currentTimeMillis() - lastFullSyncAt in 0 until FULL_SYNC_INTERVAL_MILLIS
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
     * the conversation list itself.
     *
     * The catch-up is skipped in battery saver mode and when background data is restricted on a
     * metered network (mirroring the Low Power Mode guard on iOS), and is bounded to the
     * [MAX_ROOMS_TO_CATCH_UP] most recently active rooms with [MAX_CONCURRENT_CATCH_UPS] parallel
     * requests, so a fresh install with many rooms cannot cause an unbounded request burst.
     */
    private suspend fun catchUpRoomsWithNewMessages(user: User, rooms: List<ConversationEntity>) {
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

            !CapabilitiesUtil.isChatPreloadAllowed(user.capabilities?.spreedCapability) -> {
                Log.d(TAG, "Server turned off preloading, skipping message catch-up")
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
