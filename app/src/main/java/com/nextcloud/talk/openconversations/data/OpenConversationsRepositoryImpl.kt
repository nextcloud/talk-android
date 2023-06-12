/*
 * Nextcloud Talk application
 *
 * @author Marcel Hibbe
 * Copyright (C) 2023 Marcel Hibbe <dev@mhibbe.de>
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

package com.nextcloud.talk.openconversations.data

import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import io.reactivex.Observable

class OpenConversationsRepositoryImpl(private val ncApi: NcApi, currentUserProvider: CurrentUserProviderNew) :
    OpenConversationsRepository {

    val currentUser: User = currentUserProvider.currentUser.blockingGet()
    val credentials: String = ApiUtils.getCredentials(currentUser.username, currentUser.token)

    val apiVersion = ApiUtils.getConversationApiVersion(currentUser, intArrayOf(ApiUtils.APIv4, ApiUtils.APIv3, 1))

    override fun fetchConversations(): Observable<OpenConversationsModel> {
        return ncApi.getOpenConversations(
            credentials,
            ApiUtils.getUrlForOpenConversations(apiVersion, currentUser.baseUrl)
        ).map { mapToOpenConversationsModel(it.ocs?.data!!) }
    }

    private fun mapToOpenConversationsModel(
        conversations: List<Conversation>
    ): OpenConversationsModel {
        return OpenConversationsModel(
            conversations.map { conversation ->
                OpenConversation(
                    conversation.roomId!!,
                    conversation.token!!,
                    conversation.name!!
                )
            }
        )
    }
}
