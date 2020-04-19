/*
 *
 *  * Nextcloud Talk application
 *  *
 *  * @author Mario Danic
 *  * Copyright (C) 2017-2020 Mario Danic <mario@lovelyhq.com>
 *  *
 *  * This program is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * at your option) any later version.
 *  *
 *  * This program is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.nextcloud.talk.newarch.data.repository.offline

import androidx.lifecycle.LiveData
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.map
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.newarch.domain.repository.offline.MessagesRepository
import com.nextcloud.talk.newarch.local.dao.MessagesDao
import com.nextcloud.talk.newarch.local.models.User
import com.nextcloud.talk.newarch.local.models.hasSpreedFeatureCapability
import com.nextcloud.talk.newarch.local.models.other.ChatMessageStatus
import com.nextcloud.talk.newarch.local.models.toChatMessage
import com.nextcloud.talk.newarch.local.models.toMessageEntity

class MessagesRepositoryImpl(private val messagesDao: MessagesDao) : MessagesRepository {
    override fun getMessagesWithUserForConversation(
            conversationId: String
    ): LiveData<List<ChatMessage>> {
        return messagesDao.getMessagesWithUserForConversation(conversationId).distinctUntilChanged().map {
            it.map { messageEntity ->
                messageEntity.toChatMessage()
            }
        }
    }

    override fun getPendingMessagesForConversation(conversationId: String): LiveData<List<ChatMessage>> {
        return messagesDao.getPendingMessagesLive(conversationId).distinctUntilChanged().map {
            it.map { messageEntity ->
                messageEntity.toChatMessage()
            }
        }
    }

    override suspend fun getMessageForConversation(conversationId: String, messageId: Long): ChatMessage? {
        return messagesDao.getMessageForConversation(conversationId, messageId)?.toChatMessage()
    }

    override fun getMessagesWithUserForConversationSince(conversationId: String, messageId: Long): LiveData<List<ChatMessage>> {
        return messagesDao.getMessagesWithUserForConversationSince(conversationId, messageId).distinctUntilChanged().map {
            it.map { messageEntity ->
                messageEntity.toChatMessage()
            }
        }
    }

    override suspend fun saveMessagesForConversation(user: User, messages: List<ChatMessage>, sendingMessages: Boolean){
        val shouldInsert = !user.hasSpreedFeatureCapability("chat-reference-id") || sendingMessages
        val updatedMessages = messages.map {
            if (!user.hasSpreedFeatureCapability("chat-reference-id")) {
                it.chatMessageStatus = ChatMessageStatus.RECEIVED
            }
            it.toMessageEntity()
        }

        if (shouldInsert) {
            messagesDao.saveMessages(*updatedMessages.toTypedArray())
        } else {
            messagesDao.updateMessages(user, updatedMessages.toTypedArray())
        }
    }

    override suspend fun updateMessageStatus(status: Int, conversationId: String, messageId: Long) {
        messagesDao.updateMessageStatus(status, conversationId, messageId)
    }
}