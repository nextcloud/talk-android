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

package com.nextcloud.talk.newarch.data.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import com.nextcloud.talk.models.database.UserEntity
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.newarch.domain.repository.NextcloudTalkOfflineRepository
import com.nextcloud.talk.newarch.local.db.TalkDatabase
import com.nextcloud.talk.newarch.local.models.toConversation
import com.nextcloud.talk.newarch.local.models.toConversationEntity

class NextcloudTalkOfflineRepositoryImpl(val nextcloudTalkDatabase: TalkDatabase) :
    NextcloudTalkOfflineRepository {
  override suspend fun setChangingValueForConversation(
    userId: Long,
    conversationId: String,
    changing: Boolean
  ) {
    nextcloudTalkDatabase.conversationsDao()
        .updateChangingValueForConversation(userId, conversationId, changing)
  }

  override suspend fun setFavoriteValueForConversation(
    userId: Long,
    conversationId: String,
    favorite: Boolean
  ) {
    nextcloudTalkDatabase.conversationsDao()
        .updateFavoriteValueForConversation(userId, conversationId, favorite)
  }

  override suspend fun deleteConversation(
    userId: Long,
    conversationId: String
  ) {
    nextcloudTalkDatabase.conversationsDao()
        .deleteConversation(userId, conversationId)
  }

  override fun getConversationsForUser(user: UserEntity): LiveData<List<Conversation>> {
    return nextcloudTalkDatabase.conversationsDao()
        .getConversationsForUser(user.id)
        .map { data ->
          data.map {
            it.toConversation()
          }
        }
  }

  internal fun getDatabase(): TalkDatabase {
    return nextcloudTalkDatabase
  }

  override suspend fun clearConversationsForUser(user: UserEntity) {
    nextcloudTalkDatabase.conversationsDao()
        .clearConversationsForUser(user.id)
  }

  override suspend fun saveConversationsForUser(
    user: UserEntity,
    conversations: List<Conversation>
  ) {
    nextcloudTalkDatabase.conversationsDao()
        .updateConversationsForUser(
            user.id,
            conversations.map {
              it.toConversationEntity()
            }.toTypedArray()
        )
  }

  override suspend fun deleteConversationForUserWithTimestamp(
    user: UserEntity,
    timestamp: Long
  ) {
    nextcloudTalkDatabase.conversationsDao()
        .deleteConversationsForUserWithTimestamp(user.id, timestamp)
  }
}
