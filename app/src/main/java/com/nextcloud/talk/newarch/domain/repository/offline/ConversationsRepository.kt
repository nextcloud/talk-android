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

package com.nextcloud.talk.newarch.domain.repository.offline

import androidx.lifecycle.LiveData
import com.nextcloud.talk.models.json.conversations.Conversation

interface ConversationsRepository {
    fun getConversationsForUser(userId: Long, filter: CharSequence?): LiveData<List<Conversation>>
    fun getShortcutTargetConversations(userId: Long): LiveData<List<Conversation>>
    suspend fun getConversationForUserWithToken(internalUserId: Long, token: String): Conversation?
    suspend fun clearConversationsForUser(userId: Long)
    suspend fun saveConversationsForUser(
            userId: Long,
            conversations: List<Conversation>,
            deleteOutdated: Boolean
    ): List<Long>

    suspend fun setChangingValueForConversation(
            userId: Long,
            conversationId: String,
            changing: Boolean
    )

    suspend fun setFavoriteValueForConversation(
            userId: Long,
            conversationId: String,
            favorite: Boolean
    )

    suspend fun deleteConversation(
            userId: Long,
            conversationId: String
    )

    suspend fun deleteConversationForUserWithTimestamp(
            userId: Long,
            timestamp: Long
    )
}
