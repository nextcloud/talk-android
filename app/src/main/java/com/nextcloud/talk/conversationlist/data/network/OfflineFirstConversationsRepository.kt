/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-FileCopyrightText: 2024 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationlist.data.network

import android.util.Log
import com.nextcloud.talk.chat.data.network.ChatNetworkDataSource
import com.nextcloud.talk.conversationlist.data.OfflineConversationsRepository
import com.nextcloud.talk.data.database.dao.ConversationsDao
import com.nextcloud.talk.data.database.mappers.asEntity
import com.nextcloud.talk.data.database.mappers.asModel
import com.nextcloud.talk.data.database.model.ConversationEntity
import com.nextcloud.talk.data.network.NetworkMonitor
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.utils.CapabilitiesUtil.isUserStatusAvailable
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

class OfflineFirstConversationsRepository @Inject constructor(
    private val dao: ConversationsDao,
    private val network: ConversationsNetworkDataSource,
    private val chatNetworkDataSource: ChatNetworkDataSource,
    private val networkMonitor: NetworkMonitor,
    private val currentUserProviderNew: CurrentUserProviderNew
) : OfflineConversationsRepository {
    override val roomListFlow: Flow<List<ConversationModel>>
        get() = _roomListFlow
    private val _roomListFlow: MutableSharedFlow<List<ConversationModel>> = MutableSharedFlow()

    override val conversationFlow: Flow<ConversationModel>
        get() = _conversationFlow
    private val _conversationFlow: MutableSharedFlow<ConversationModel> = MutableSharedFlow()

    private val scope = CoroutineScope(Dispatchers.IO)
    private var user: User = currentUserProviderNew.currentUser.blockingGet()

    override fun getRooms(): Job =
        scope.launch {
            val initialConversationModels = getListOfConversations(user.id!!)
            _roomListFlow.emit(initialConversationModels)

            if (networkMonitor.isOnline.value) {
                val conversationEntitiesFromSync = getRoomsFromServer()
                if (!conversationEntitiesFromSync.isNullOrEmpty()) {
                    val conversationModelsFromSync = conversationEntitiesFromSync.map(ConversationEntity::asModel)
                    _roomListFlow.emit(conversationModelsFromSync)
                }
            }
        }

    override fun getRoom(roomToken: String): Job =
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
                            _conversationFlow.emit(model)
                            val entityList = listOf(model.asEntity())
                            dao.upsertConversations(user.id!!, entityList)
                        }
                    }
                })
        }

    override suspend fun updateConversation(conversationModel: ConversationModel) {
        val entity = conversationModel.asEntity()
        dao.updateConversation(entity)
    }

    override suspend fun getLocallyStoredConversation(roomToken: String): ConversationModel? {
        val id = user.id!!
        return getConversation(id, roomToken)
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    private suspend fun getRoomsFromServer(): List<ConversationEntity>? {
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

            deleteLeftConversations(conversationsFromSync)
            dao.upsertConversations(user.id!!, conversationsFromSync)
        } catch (e: Exception) {
            Log.e(TAG, "Something went wrong when fetching conversations", e)
        }
        return conversationsFromSync
    }

    private suspend fun deleteLeftConversations(conversationsFromSync: List<ConversationEntity>) {
        val conversationsFromSyncIds = conversationsFromSync.map { it.internalId }.toSet()
        val oldConversationsFromDb = dao.getConversationsForUser(user.id!!).first()

        val conversationIdsToDelete = oldConversationsFromDb
            .map { it.internalId }
            .filterNot { it in conversationsFromSyncIds }

        dao.deleteConversations(conversationIdsToDelete)
    }

    private suspend fun getListOfConversations(accountId: Long): List<ConversationModel> =
        dao.getConversationsForUser(accountId).map {
            it.map(ConversationEntity::asModel)
        }.first()

    private suspend fun getConversation(accountId: Long, token: String): ConversationModel? {
        val entity = dao.getConversationForUser(accountId, token).first()
        return entity?.asModel()
    }

    companion object {
        val TAG = OfflineFirstConversationsRepository::class.simpleName
    }
}
