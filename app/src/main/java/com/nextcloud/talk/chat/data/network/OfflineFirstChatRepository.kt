/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.data.network

import android.os.Bundle
import com.nextcloud.talk.chat.data.ChatMessageRepository
import com.nextcloud.talk.chat.data.ChatRepository
import com.nextcloud.talk.chat.data.model.ChatMessageModel
import com.nextcloud.talk.data.database.dao.ChatMessagesDao
import com.nextcloud.talk.data.database.mappers.asEntity
import com.nextcloud.talk.data.database.mappers.asModel
import com.nextcloud.talk.data.database.model.ChatMessageEntity
import com.nextcloud.talk.data.sync.Synchronizer
import com.nextcloud.talk.data.sync.changeListSync
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.models.json.chat.ChatOverall
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.preferences.AppPreferences
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class OfflineFirstChatRepository @Inject constructor(
    private val chatDao: ChatMessagesDao,
    private val network: ChatRepository,
    private val datastore: AppPreferences
) : ChatMessageRepository {

    override fun getMessages(id: Long): Flow<List<ChatMessageModel>> =
        chatDao.getMessagesForConversation(id).map {
            it.map(ChatMessageEntity::asModel)
        }

    override fun getMessage(id: Long): Flow<ChatMessageModel> =
        chatDao.getChatMessage(id).map(ChatMessageEntity::asModel)

    @Suppress("UNCHECKED_CAST")
    private fun getMessagesFromServer(bundle: Bundle): List<ChatMessage> {
        val credentials = bundle.getString(BundleKeys.KEY_CREDENTIALS)
        val url = bundle.getString(BundleKeys.KEY_CHAT_URL)
        val fieldMap = bundle.getSerializable(BundleKeys.KEY_FIELD_MAP) as HashMap<String, Int>

        val list = network.pullChatMessages(credentials!!, url!!, fieldMap)
            .firstElement()
            .subscribeOn(Schedulers.io())
            .map { (it.body() as ChatOverall).ocs!!.data }
            .observeOn(AndroidSchedulers.mainThread())
            .blockingGet()
        return list ?: listOf()
    }

    override suspend fun syncWith(bundle: Bundle, synchronizer: Synchronizer): Boolean =
        synchronizer.changeListSync(
            modelFetcher = {
                return@changeListSync getMessagesFromServer(bundle)
            },
            versionUpdater = { newLastReadId ->
                val conversationId = bundle.getLong(BundleKeys.KEY_CONVERSATION_ID)
                datastore.saveLastReadId(conversationId, newLastReadId)
            },
            modelDeleter = chatDao::deleteChatMessages,
            modelUpdater = { model ->
                chatDao.upsertChatMessages(
                    model.filterIsInstance<ChatMessage>().map { it.asEntity() }
                )
            }
        )
}
