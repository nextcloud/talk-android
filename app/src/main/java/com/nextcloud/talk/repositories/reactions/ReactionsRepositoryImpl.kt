/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.repositories.reactions

import com.nextcloud.talk.api.NcApiCoroutines
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.data.database.dao.ChatMessagesDao
import com.nextcloud.talk.models.domain.ReactionAddedModel
import com.nextcloud.talk.models.domain.ReactionDeletedModel
import javax.inject.Inject

class ReactionsRepositoryImpl @Inject constructor(
    private val ncApiCoroutines: NcApiCoroutines,
    private val dao: ChatMessagesDao
) : ReactionsRepository {

    override suspend fun addReaction(
        credentials: String?,
        userId: Long,
        url: String,
        roomToken: String,
        message: ChatMessage,
        emoji: String
    ): ReactionAddedModel {
        val response = ncApiCoroutines.sendReaction(credentials, url, emoji)
        val model = ReactionAddedModel(
            message,
            emoji,
            response.ocs?.meta?.statusCode == HTTP_CREATED
        )
        persistAddedModel(userId, model, roomToken)
        return model
    }

    override suspend fun deleteReaction(
        credentials: String?,
        userId: Long,
        url: String,
        roomToken: String,
        message: ChatMessage,
        emoji: String
    ): ReactionDeletedModel {
        val response = ncApiCoroutines.deleteReaction(credentials, url, emoji)
        val model = ReactionDeletedModel(
            message,
            emoji,
            response.ocs?.meta?.statusCode == HTTP_OK
        )
        persistDeletedModel(userId, model, roomToken)
        return model
    }

    private suspend fun persistAddedModel(userId: Long, model: ReactionAddedModel, roomToken: String) {
        val id = model.chatMessage.jsonMessageId.toLong()
        val internalConversationId = "$userId@$roomToken"
        val emoji = model.emoji

        val message = dao.getChatMessageEntity(internalConversationId, id) ?: return

        val reactions = message.reactions ?: LinkedHashMap<String, Int>().also { message.reactions = it }
        val reactionsSelf = message.reactionsSelf ?: ArrayList<String>().also { message.reactionsSelf = it }

        if (!reactionsSelf.contains(emoji)) {
            reactions[emoji] = reactions.getOrDefault(emoji, 0) + 1
            reactionsSelf.add(emoji)
            dao.updateChatMessage(message)
        }
    }

    private suspend fun persistDeletedModel(userId: Long, model: ReactionDeletedModel, roomToken: String) {
        val id = model.chatMessage.jsonMessageId.toLong()
        val internalConversationId = "$userId@$roomToken"
        val emoji = model.emoji

        val message = dao.getChatMessageEntity(internalConversationId, id) ?: return

        val reactions = message.reactions ?: LinkedHashMap<String, Int>().also { message.reactions = it }
        val reactionsSelf = message.reactionsSelf ?: ArrayList<String>().also { message.reactionsSelf = it }

        if (reactionsSelf.contains(emoji)) {
            reactions[emoji] = (reactions.getOrDefault(emoji, 0) - 1).coerceAtLeast(0)
            reactionsSelf.remove(emoji)
            dao.updateChatMessage(message)
        }
    }

    companion object {
        private const val HTTP_OK: Int = 200
        private const val HTTP_CREATED: Int = 201
    }
}
