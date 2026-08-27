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
import android.os.PowerManager
import android.util.Log
import com.nextcloud.talk.chat.data.network.ChatMessageSyncer
import com.nextcloud.talk.chat.data.network.ChatNetworkDataSource
import com.nextcloud.talk.conversationlist.data.OfflineConversationsRepository
import com.nextcloud.talk.data.database.dao.ConversationsDao
import com.nextcloud.talk.data.database.mappers.asEntity
import com.nextcloud.talk.data.database.mappers.toDomainModel
import com.nextcloud.talk.data.database.model.ConversationEntity
import com.nextcloud.talk.data.network.NetworkMonitor
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.CapabilitiesUtil.isUserStatusAvailable
import com.nextcloud.talk.utils.SpreedFeatures
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject
import kotlin.collections.map

class OfflineFirstConversationsRepository @Inject constructor(
    private val dao: ConversationsDao,
    private val network: ConversationsNetworkDataSource,
    private val chatNetworkDataSource: ChatNetworkDataSource,
    private val networkMonitor: NetworkMonitor,
    private val chatMessageSyncer: ChatMessageSyncer,
    private val conversationListUpdater: ConversationListUpdater,
    private val context: Context
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

    override fun getRooms(user: User): Job =
        scope.launch {
            observedAccountId.value = user.id!!

            if (networkMonitor.isOnline.value) {
                getRoomsFromServer(user)
            }
        }

    override fun getRoom(user: User, roomToken: String): Job =
        scope.launch {
            chatNetworkDataSource.getRoom(user, roomToken)
                .subscribeOn(Schedulers.io())
                ?.observeOn(AndroidSchedulers.mainThread())
                ?.subscribe(object : Observer<ConversationModel> {
                    override fun onSubscribe(p0: Disposable) {
                        // unused atm
                    }

                    override fun onError(e: Throwable) {
                        runBlocking {
                            // In case network is offline or call fails
                            val id = user.id!!
                            val model = getConversation(id, roomToken)
                            if (model != null) {
                                _conversationFlow.emit(model)
                            } else {
                                Log.e(TAG, "Conversation model not found on device database")
                            }
                        }
                    }

                    override fun onComplete() {
                        // unused atm
                    }

                    override fun onNext(model: ConversationModel) {
                        runBlocking {
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
                        }
                    }
                })
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
    private suspend fun getRoomsFromServer(user: User): List<ConversationEntity>? {
        var conversationsFromSync: List<ConversationEntity>? = null

        if (!networkMonitor.isOnline.value) {
            Log.d(TAG, "Device is offline, can't load conversations from server")
            return null
        }

        val includeStatus = isUserStatusAvailable(user)

        try {
            val conversationsList = network.getRooms(user, user.baseUrl!!, includeStatus)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .blockingSingle()

            conversationsFromSync = conversationsList.map {
                it.asEntity(user.id!!)
            }

            val previousConversations = dao.getConversationsForUser(user.id!!).first()
                .associateBy { it.internalId }

            dao.syncConversationsForUser(
                accountId = user.id!!,
                serverItems = conversationListUpdater.preservePendingLocalState(
                    previousConversations,
                    conversationsFromSync
                ),
                conversationIdsToDelete = determineLeftConversationIds(previousConversations, conversationsFromSync)
            )

            val roomsWithNewMessages = getRoomsWithNewMessages(conversationsFromSync, previousConversations)
            scope.launch { catchUpRoomsWithNewMessages(user, roomsWithNewMessages) }
        } catch (e: Exception) {
            Log.e(TAG, "Something went wrong when fetching conversations", e)
        }
        return conversationsFromSync
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
                            .onFailure { Log.e(TAG, "Message catch-up failed for room ${room.token}", it) }
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

            isPowerSaveMode() -> {
                Log.d(TAG, "Battery saver is active, skipping message catch-up")
                false
            }

            isBackgroundDataRestricted() -> {
                Log.d(TAG, "Background data is restricted on a metered network, skipping message catch-up")
                false
            }

            else -> true
        }

    private fun isPowerSaveMode(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isPowerSaveMode
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
        val TAG = OfflineFirstConversationsRepository::class.simpleName
        private const val CHAT_API_VERSION = 1
        private const val MAX_ROOMS_TO_CATCH_UP = 20
        private const val MAX_CONCURRENT_CATCH_UPS = 3
    }
}
