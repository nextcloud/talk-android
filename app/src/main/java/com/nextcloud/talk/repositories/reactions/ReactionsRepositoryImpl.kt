/*
 * Nextcloud Talk application
 *
 * @author Marcel Hibbe
 * Copyright (C) 2022 Marcel Hibbe <dev@mhibbe.de>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.repositories.reactions

import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.domain.ReactionAddedModel
import com.nextcloud.talk.models.domain.ReactionDeletedModel
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.models.json.generic.GenericMeta
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import io.reactivex.Observable

class ReactionsRepositoryImpl(private val ncApi: NcApi, currentUserProvider: CurrentUserProviderNew) :
    ReactionsRepository {

    val currentUser: User = currentUserProvider.currentUser.blockingGet()
    val credentials: String = ApiUtils.getCredentials(currentUser.username, currentUser.token)

    override fun addReaction(
        roomToken: String,
        message: ChatMessage,
        emoji: String
    ): Observable<ReactionAddedModel> {
        return ncApi.sendReaction(
            credentials,
            ApiUtils.getUrlForMessageReaction(
                currentUser.baseUrl,
                roomToken,
                message.id
            ),
            emoji
        ).map { mapToReactionAddedModel(message, emoji, it.ocs?.meta!!) }
    }

    override fun deleteReaction(
        roomToken: String,
        message: ChatMessage,
        emoji: String
    ): Observable<ReactionDeletedModel> {
        return ncApi.deleteReaction(
            credentials,
            ApiUtils.getUrlForMessageReaction(
                currentUser.baseUrl,
                roomToken,
                message.id
            ),
            emoji
        ).map { mapToReactionDeletedModel(message, emoji, it.ocs?.meta!!) }
    }

    private fun mapToReactionAddedModel(
        message: ChatMessage,
        emoji: String,
        reactionResponse: GenericMeta
    ): ReactionAddedModel {
        val success = reactionResponse.statusCode == HTTP_CREATED
        return ReactionAddedModel(
            message,
            emoji,
            success
        )
    }

    private fun mapToReactionDeletedModel(
        message: ChatMessage,
        emoji: String,
        reactionResponse: GenericMeta
    ): ReactionDeletedModel {
        val success = reactionResponse.statusCode == HTTP_OK
        return ReactionDeletedModel(
            message,
            emoji,
            success
        )
    }

    companion object {
        private const val HTTP_OK: Int = 200
        private const val HTTP_CREATED: Int = 201
    }
}
