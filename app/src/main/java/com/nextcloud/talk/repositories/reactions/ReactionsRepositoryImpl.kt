/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.repositories.reactions

import android.util.Log
import com.nextcloud.talk.api.NcApiCoroutines
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.data.database.dao.ChatMessagesDao
import com.nextcloud.talk.models.domain.ReactionAddedModel
import com.nextcloud.talk.models.domain.ReactionDeletedModel
import com.nextcloud.talk.utils.optimisticAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import javax.inject.Inject

/**
 * Reactions are written to the local database before the server is asked, so the chat renders them
 * without waiting for the round trip. A request that fails for a transient reason is retried once,
 * and only a request that finally fails reverts the local change.
 *
 * A revert is applied only while the local message still carries the optimistic change, so a server
 * payload that arrived meanwhile through signaling or a chat sync stays authoritative. Should the
 * revert still race with a request the server did apply, the next sync of that message corrects it.
 */
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
        val internalConversationId = "$userId@$roomToken"
        val messageId = message.jsonMessageId.toLong()

        val confirmed = optimisticAction(
            apply = {
                val applied = applyLocalAdd(internalConversationId, messageId, emoji)
                revertWith(applied) { applyLocalRemove(internalConversationId, messageId, emoji) }
            },
            isConfirmed = { statusCode -> statusCode in ADD_SUCCESS_CODES },
            request = { ncApiCoroutines.sendReaction(credentials, url, emoji).ocs?.meta?.statusCode }
        ).logFailure().getOrNull() in ADD_SUCCESS_CODES

        return ReactionAddedModel(message, emoji, confirmed)
    }

    override suspend fun deleteReaction(
        credentials: String?,
        userId: Long,
        url: String,
        roomToken: String,
        message: ChatMessage,
        emoji: String
    ): ReactionDeletedModel {
        val internalConversationId = "$userId@$roomToken"
        val messageId = message.jsonMessageId.toLong()

        val confirmed = optimisticAction(
            apply = {
                val applied = applyLocalRemove(internalConversationId, messageId, emoji)
                revertWith(applied) { applyLocalAdd(internalConversationId, messageId, emoji) }
            },
            isConfirmed = { statusCode -> statusCode in DELETE_SUCCESS_CODES },
            request = { deleteReactionStatusCode(credentials, url, emoji) }
        ).logFailure().getOrNull() in DELETE_SUCCESS_CODES

        return ReactionDeletedModel(message, emoji, confirmed)
    }

    /**
     * A reaction the server no longer knows is deleted as far as this call is concerned.
     */
    private suspend fun deleteReactionStatusCode(credentials: String?, url: String, emoji: String): Int? =
        try {
            ncApiCoroutines.deleteReaction(credentials, url, emoji).ocs?.meta?.statusCode
        } catch (e: HttpException) {
            if (e.code() == HTTP_NOT_FOUND) HTTP_OK else throw e
        }

    private fun revertWith(applied: Boolean, revert: suspend () -> Unit): (suspend () -> Unit)? =
        revert.takeIf { applied }

    private fun <T> Result<T>.logFailure(): Result<T> = onFailure { Log.w(TAG, "Reaction request failed: $it") }

    /**
     * Adds the reaction to the cached message and reports whether that changed anything. A reaction the
     * message already carries is left alone - and must not be reverted later, as it was not applied here.
     */
    private suspend fun applyLocalAdd(internalConversationId: String, messageId: Long, emoji: String): Boolean =
        withContext(Dispatchers.IO) {
            val message = dao.getChatMessageEntity(internalConversationId, messageId) ?: return@withContext false

            val reactions = message.reactions ?: LinkedHashMap<String, Int>().also { message.reactions = it }
            val reactionsSelf = message.reactionsSelf ?: ArrayList<String>().also { message.reactionsSelf = it }

            if (reactionsSelf.contains(emoji)) return@withContext false

            reactions[emoji] = reactions.getOrDefault(emoji, 0) + 1
            reactionsSelf.add(emoji)
            dao.updateChatMessage(message)
            true
        }

    /**
     * Removes the reaction from the cached message and reports whether that changed anything.
     */
    private suspend fun applyLocalRemove(internalConversationId: String, messageId: Long, emoji: String): Boolean =
        withContext(Dispatchers.IO) {
            val message = dao.getChatMessageEntity(internalConversationId, messageId) ?: return@withContext false

            val reactions = message.reactions ?: LinkedHashMap<String, Int>().also { message.reactions = it }
            val reactionsSelf = message.reactionsSelf ?: ArrayList<String>().also { message.reactionsSelf = it }

            if (!reactionsSelf.contains(emoji)) return@withContext false

            reactions[emoji] = (reactions.getOrDefault(emoji, 0) - 1).coerceAtLeast(0)
            reactionsSelf.remove(emoji)
            dao.updateChatMessage(message)
            true
        }

    companion object {
        private val TAG = ReactionsRepositoryImpl::class.java.simpleName
        private const val HTTP_OK: Int = 200
        private const val HTTP_CREATED: Int = 201
        private const val HTTP_NOT_FOUND: Int = 404

        private val ADD_SUCCESS_CODES = setOf(HTTP_OK, HTTP_CREATED)
        private val DELETE_SUCCESS_CODES = setOf(HTTP_OK)
    }
}
