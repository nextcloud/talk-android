/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2019 Mario Danic <mario@lovelyhq.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.newarch.data.repository.offline

import androidx.lifecycle.LiveData
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.map
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.newarch.domain.repository.offline.ConversationsRepository
import com.nextcloud.talk.newarch.local.dao.ConversationsDao
import com.nextcloud.talk.newarch.local.models.toConversation
import com.nextcloud.talk.newarch.local.models.toConversationEntity

class ConversationsRepositoryImpl(val conversationsDao: ConversationsDao) :
        ConversationsRepository {
    override suspend fun setChangingValueForConversation(
            userId: Long,
            conversationId: String,
            changing: Boolean
    ) {
        conversationsDao
                .updateChangingValueForConversation(userId, conversationId, changing)
    }

    override suspend fun setFavoriteValueForConversation(
            userId: Long,
            conversationId: String,
            favorite: Boolean
    ) {
        conversationsDao
                .updateFavoriteValueForConversation(userId, conversationId, favorite)
    }

    override suspend fun deleteConversation(
            userId: Long,
            conversationId: String
    ) {
        conversationsDao
                .deleteConversation(userId, conversationId)
    }

    override fun getConversationsForUser(userId: Long, filter: CharSequence?): LiveData<List<Conversation>> {
        filter?.let {
            return conversationsDao.getConversationsForUserWithFilter(userId, it.toString()).distinctUntilChanged().map { data ->
                data.map { conversationEntity ->
                    conversationEntity.toConversation()
                }
            }
        } ?: run {
            return conversationsDao.getConversationsForUser(userId).distinctUntilChanged().map { data ->
                data.map {
                    it.toConversation()
                }
            }
        }
    }

    override fun getShortcutTargetConversations(userId: Long): LiveData<List<Conversation>> {
        return conversationsDao.getShortcutTargetConversations(userId).distinctUntilChanged().map { data ->
            data.map {
                it.toConversation()
            }
        }
    }

    override suspend fun getConversationForUserWithToken(userId: Long, token: String): Conversation? {
        val conversationEntity = conversationsDao.getConversationForUserWithToken(userId, token)
        if (conversationEntity != null) {
            return conversationEntity.toConversation()
        }

        return null
    }

    override suspend fun clearConversationsForUser(userId: Long) {
        conversationsDao
                .clearConversationsForUser(userId)
    }

    override suspend fun saveConversationsForUser(
            userId: Long,
            conversations: List<Conversation>
    ) {
        val map = conversations.map {
            it.toConversationEntity()
        }

        conversationsDao
                .updateConversationsForUser(
                        userId,
                        map.toTypedArray()
                )
    }

    override suspend fun deleteConversationForUserWithTimestamp(
            userId: Long,
            timestamp: Long
    ) {
        conversationsDao
                .deleteConversationsForUserWithTimestamp(userId, timestamp)
    }
}
