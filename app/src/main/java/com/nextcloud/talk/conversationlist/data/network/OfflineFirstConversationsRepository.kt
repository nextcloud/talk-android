/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-FileCopyrightText: 2024 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationlist.data.network

import android.util.Log
import com.nextcloud.talk.chat.data.network.OfflineFirstChatRepository
import com.nextcloud.talk.conversationlist.data.OfflineConversationsRepository
import com.nextcloud.talk.data.database.dao.ConversationsDao
import com.nextcloud.talk.data.database.mappers.asEntity
import com.nextcloud.talk.data.database.mappers.asModel
import com.nextcloud.talk.data.database.model.ConversationEntity
import com.nextcloud.talk.data.network.NetworkMonitor
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

class OfflineFirstConversationsRepository @Inject constructor(
    private val dao: ConversationsDao,
    private val network: ConversationsNetworkDataSource,
    private val monitor: NetworkMonitor,
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
            repeat(2) {
                val list = getListOfConversations(user.id!!)
                if (list.isNotEmpty()) {
                    _roomListFlow.emit(list)
                }
                sync()
            }
        }

    override fun getConversationSettings(roomToken: String): Job =
        scope.launch {
            val id = user.id!!
            val model = getConversation(id, roomToken)
            model.let { _conversationFlow.emit(model) }
        }

    private suspend fun sync() {
        if (!monitor.isOnline.first()) {
            Log.d(OfflineFirstChatRepository.TAG, "Device is offline, can't load conversations from server")
            return
        }

        try {
            val conversationsList = network.getRooms(user, user.baseUrl!!, false)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .blockingSingle()

            val list = conversationsList.map {
                it.asEntity(user.id!!)
            }
            dao.upsertConversations(list)
        } catch (e: Exception) {
            Log.e(TAG, "Something went wrong when fetching conversations", e)
        }
    }

    private suspend fun getListOfConversations(accountId: Long): List<ConversationModel> =
        dao.getConversationsForUser(accountId).map {
            it.map(ConversationEntity::asModel)
        }.first()

    private suspend fun getConversation(accountId: Long, token: String): ConversationModel {
        val entity = dao.getConversationForUser(accountId, token).first()
        return entity.asModel()
    }

    companion object {
        val TAG = OfflineFirstConversationsRepository::class.simpleName
    }
}
