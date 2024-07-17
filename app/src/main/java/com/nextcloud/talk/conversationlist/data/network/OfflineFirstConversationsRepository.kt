/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationlist.data.network

import android.os.Bundle
import androidx.core.os.bundleOf
import com.nextcloud.talk.conversationlist.data.OfflineConversationsRepository
import com.nextcloud.talk.data.database.dao.ConversationsDao
import com.nextcloud.talk.data.database.mappers.asEntity
import com.nextcloud.talk.data.database.mappers.asModel
import com.nextcloud.talk.data.database.model.ConversationEntity
import com.nextcloud.talk.data.sync.Synchronizer
import com.nextcloud.talk.data.sync.changeListSync
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.conversations.Conversation
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
    private val currentUserProviderNew: CurrentUserProviderNew
) : OfflineConversationsRepository, Synchronizer {

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
                this@OfflineFirstConversationsRepository.sync(bundleOf())
            }
        }

    override fun getConversationSettings(roomToken: String): Job =
        scope.launch {
            val id = user.id!!
            val model = getConversation(id, roomToken)
            model?.let { _conversationFlow.emit(model) }
        }

    override suspend fun syncWith(bundle: Bundle, synchronizer: Synchronizer): Boolean =
        synchronizer.changeListSync(
            modelFetcher = {
                return@changeListSync getConversationsFromServer()
            },
            // not needed
            versionUpdater = {},
            modelDeleter = {},
            modelUpdater = { models ->
                val list = models.filterIsInstance<Conversation>().map {
                    it.asEntity(user.id!!)
                }
                dao.upsertConversations(list)
            }
        )

    private fun getConversationsFromServer(): List<Conversation> {
        val list = network.getRooms(user, user.baseUrl!!, false)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .map { list ->
                return@map list.map {
                    it.apply {
                        id = roomId!!.toLong()
                    }
                }
            }
            .blockingSingle()

        return list ?: listOf()
    }

    private suspend fun getListOfConversations(accountId: Long): List<ConversationModel> =
        dao.getConversationsForUser(accountId).map {
            it.map(ConversationEntity::asModel)
        }.first()

    private suspend fun getConversation(accountId: Long, token: String): ConversationModel? {
        val entity = dao.getConversationForUser(accountId, token).first()
        return entity?.asModel()
    }
}
