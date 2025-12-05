/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.repositories.reactions

import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.data.database.dao.ChatMessagesDao
import com.nextcloud.talk.models.domain.ReactionAddedModel
import com.nextcloud.talk.models.domain.ReactionDeletedModel
import com.nextcloud.talk.models.json.generic.GenericMeta
import io.reactivex.Observable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

class ReactionsRepositoryImpl @Inject constructor(private val ncApi: NcApi, private val dao: ChatMessagesDao) :
    ReactionsRepository {

    override fun addReaction(
        credentials: String?,
        userId: Long,
        url: String,
        roomToken: String,
        message: ChatMessage,
        emoji: String
    ): Observable<ReactionAddedModel> {
        return ncApi.sendReaction(
            credentials,
            url,
            emoji
        ).map {
            val model = mapToReactionAddedModel(message, emoji, it.ocs?.meta!!)
            persistAddedModel(
                userId,
                model,
                roomToken
            )
            return@map model
        }
    }

    override fun deleteReaction(
        credentials: String?,
        userId: Long,
        url: String,
        roomToken: String,
        message: ChatMessage,
        emoji: String
    ): Observable<ReactionDeletedModel> {
        return ncApi.deleteReaction(
            credentials,
            url,
            emoji
        ).map {
            val model = mapToReactionDeletedModel(message, emoji, it.ocs?.meta!!)
            persistDeletedModel(
                userId,
                model,
                roomToken
            )
            return@map model
        }
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

    private fun persistAddedModel(userId: Long, model: ReactionAddedModel, roomToken: String) =
        CoroutineScope(Dispatchers.IO).launch {
            // 1. Call DAO, Get a singular ChatMessageEntity with model.chatMessage.{PARAM}
            val id = model.chatMessage.jsonMessageId.toLong()
            val internalConversationId = "$userId@$roomToken"
            val emoji = model.emoji

            val message = dao.getChatMessageForConversation(
                internalConversationId,
                id
            ).first()

            // 2. Check state of entity, create params as needed
            if (message.reactions == null) {
                message.reactions = LinkedHashMap()
            }

            if (message.reactionsSelf == null) {
                message.reactionsSelf = ArrayList()
            }

            var amount = message.reactions!![emoji]
            if (amount == null) {
                amount = 0
            }
            message.reactions!![emoji] = amount + 1
            message.reactionsSelf!!.add(emoji)

            // 3. Call DAO again, to update the singular ChatMessageEntity with params
            dao.updateChatMessage(message)
        }

    private fun persistDeletedModel(userId: Long, model: ReactionDeletedModel, roomToken: String) =
        CoroutineScope(Dispatchers.IO).launch {
            // 1. Call DAO, Get a singular ChatMessageEntity with model.chatMessage.{PARAM}
            val id = model.chatMessage.jsonMessageId.toLong()
            val internalConversationId = "$userId@$roomToken"
            val emoji = model.emoji

            val message = dao.getChatMessageForConversation(internalConversationId, id).first()

            // 2. Check state of entity, create params as needed
            if (message.reactions == null) {
                message.reactions = LinkedHashMap()
            }

            if (message.reactionsSelf == null) {
                message.reactionsSelf = ArrayList()
            }

            var amount = message.reactions!![emoji]
            if (amount == null) {
                amount = 0
            }
            message.reactions!![emoji] = amount - 1
            message.reactionsSelf!!.remove(emoji)

            // 3. Call DAO again, to update the singular ChatMessageEntity with params
            dao.updateChatMessage(message)
        }

    companion object {
        private const val HTTP_OK: Int = 200
        private const val HTTP_CREATED: Int = 201
    }
}
