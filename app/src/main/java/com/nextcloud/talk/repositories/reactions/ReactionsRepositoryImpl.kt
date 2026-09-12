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
import com.nextcloud.talk.utils.revertOnCancellation
import com.nextcloud.talk.utils.withRetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
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

        val applied = applyLocalAdd(internalConversationId, messageId, emoji)
        val revert: suspend () -> Unit = {
            if (applied) applyLocalRemove(internalConversationId, messageId, emoji)
        }

        val confirmed = revertOnCancellation(revert) {
            requestSucceeds(
                successStatusCodes = ADD_SUCCESS_CODES,
                alreadyAppliedHttpCodes = emptySet()
            ) {
                ncApiCoroutines.sendReaction(credentials, url, emoji).ocs?.meta?.statusCode
            }
        }

        if (!confirmed) {
            revert()
        }

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

        val applied = applyLocalRemove(internalConversationId, messageId, emoji)
        val revert: suspend () -> Unit = {
            if (applied) applyLocalAdd(internalConversationId, messageId, emoji)
        }

        val confirmed = revertOnCancellation(revert) {
            requestSucceeds(
                successStatusCodes = DELETE_SUCCESS_CODES,
                alreadyAppliedHttpCodes = DELETE_ALREADY_APPLIED_CODES
            ) {
                ncApiCoroutines.deleteReaction(credentials, url, emoji).ocs?.meta?.statusCode
            }
        }

        if (!confirmed) {
            revert()
        }

        return ReactionDeletedModel(message, emoji, confirmed)
    }

    private suspend fun requestSucceeds(
        successStatusCodes: Set<Int>,
        alreadyAppliedHttpCodes: Set<Int>,
        call: suspend () -> Int?
    ): Boolean =
        try {
            withRetry(retries = 1, initialDelayMillis = RETRY_DELAY_MS, retryOn = ::isRetryable) {
                attemptRequest(successStatusCodes, alreadyAppliedHttpCodes, call)
            }
        } catch (e: IOException) {
            Log.w(TAG, "Reaction request failed, the retry failed as well: $e")
            false
        } catch (e: HttpException) {
            Log.w(TAG, "Reaction request failed with HTTP ${e.code()}, the retry failed as well: $e")
            false
        }

    /**
     * Returns whether the server applied the reaction, and throws for a failure that is worth another
     * attempt: a connection problem, a rate limit or a server error.
     */
    private suspend fun attemptRequest(
        successStatusCodes: Set<Int>,
        alreadyAppliedHttpCodes: Set<Int>,
        call: suspend () -> Int?
    ): Boolean =
        try {
            val statusCode = call()
            if (statusCode in successStatusCodes) {
                true
            } else {
                Log.w(TAG, "Reaction request answered with unexpected status code $statusCode")
                false
            }
        } catch (e: HttpException) {
            when {
                e.code() in alreadyAppliedHttpCodes -> true
                isRetryable(e) -> throw e
                else -> {
                    Log.w(TAG, "Reaction request rejected with HTTP ${e.code()}: $e")
                    false
                }
            }
        }

    private fun isRetryable(error: Exception): Boolean =
        when (error) {
            is HttpException -> error.code() == HTTP_TOO_MANY_REQUESTS || error.code() >= HTTP_INTERNAL_SERVER_ERROR
            else -> error is IOException
        }

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
        private const val HTTP_TOO_MANY_REQUESTS: Int = 429
        private const val HTTP_INTERNAL_SERVER_ERROR: Int = 500
        private const val RETRY_DELAY_MS: Long = 500

        private val ADD_SUCCESS_CODES = setOf(HTTP_OK, HTTP_CREATED)
        private val DELETE_SUCCESS_CODES = setOf(HTTP_OK)
        private val DELETE_ALREADY_APPLIED_CODES = setOf(HTTP_NOT_FOUND)
    }
}
