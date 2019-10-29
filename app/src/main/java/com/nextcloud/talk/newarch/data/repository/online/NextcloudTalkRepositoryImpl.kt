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

package com.nextcloud.talk.newarch.data.repository.online

import com.nextcloud.talk.models.database.UserEntity
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.newarch.data.source.remote.ApiService
import com.nextcloud.talk.newarch.domain.repository.online.NextcloudTalkRepository
import com.nextcloud.talk.newarch.utils.getCredentials
import com.nextcloud.talk.utils.ApiUtils

class NextcloudTalkRepositoryImpl(private val apiService: ApiService) : NextcloudTalkRepository {
  override suspend fun deleteConversationForUser(
    user: UserEntity,
    conversation: Conversation
  ): GenericOverall {
    return apiService.deleteConversation(
        user.getCredentials(), ApiUtils.getRoom(user.baseUrl, conversation.token)
    )
  }

  override suspend fun leaveConversationForUser(
    user: UserEntity,
    conversation: Conversation
  ): GenericOverall {
    return apiService.leaveConversation(
        user.getCredentials(), ApiUtils.getUrlForRemoveSelfFromRoom(
        user
            .baseUrl, conversation.token
    )
    )
  }

  override suspend fun setFavoriteValueForConversation(
    user: UserEntity,
    conversation: Conversation,
    favorite: Boolean
  ): GenericOverall {
    if (favorite) {
      return apiService.addConversationToFavorites(
          user.getCredentials(),
          ApiUtils.getUrlForConversationFavorites(user.baseUrl, conversation.token)
      )
    } else {
      return apiService.removeConversationFromFavorites(
          user.getCredentials(),
          ApiUtils.getUrlForConversationFavorites(user.baseUrl, conversation.token)
      )
    }
  }

  override suspend fun getConversationsForUser(user: UserEntity): List<Conversation> {
    return apiService.getConversations(
        user.getCredentials(),
        ApiUtils.getUrlForGetRooms(user.baseUrl)
    )
        .ocs.data
  }
}
