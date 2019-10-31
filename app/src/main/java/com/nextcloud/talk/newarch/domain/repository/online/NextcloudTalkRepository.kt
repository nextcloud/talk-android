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

package com.nextcloud.talk.newarch.domain.repository.online

import com.nextcloud.talk.models.database.UserEntity
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.newarch.local.models.UserNgEntity

interface NextcloudTalkRepository {
  suspend fun getConversationsForUser(user: UserNgEntity): List<Conversation>
  suspend fun setFavoriteValueForConversation(
    user: UserNgEntity,
    conversation: Conversation,
    favorite: Boolean
  ): GenericOverall

  suspend fun deleteConversationForUser(
    user: UserNgEntity,
    conversation: Conversation
  ): GenericOverall

  suspend fun leaveConversationForUser(
    userEntity: UserNgEntity,
    conversation: Conversation
  ): GenericOverall
}
