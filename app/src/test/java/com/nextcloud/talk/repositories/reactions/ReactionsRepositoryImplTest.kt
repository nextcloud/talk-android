/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.repositories.reactions

import com.nextcloud.talk.api.NcApiCoroutines
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.data.database.dao.ChatMessagesDao
import com.nextcloud.talk.data.database.model.ChatMessageEntity
import com.nextcloud.talk.models.json.generic.GenericMeta
import com.nextcloud.talk.models.json.generic.GenericOCS
import com.nextcloud.talk.models.json.generic.GenericOverall
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class ReactionsRepositoryImplTest {

    private val credentials = "credentials"
    private val url = "https://nextcloud.local/ocs/v2.php/apps/spreed/api/v1/reaction/token/1"
    private val roomToken = "token"
    private val userId = 7L
    private val internalConversationId = "$userId@$roomToken"
    private val emoji = "👍"

    @Test
    fun `an added reaction is persisted before the server answers`() =
        runTest {
            val entity = entity(reactions = linkedMapOf("👍" to 3), reactionsSelf = arrayListOf())
            val dao = dao(entity)
            val persistedBeforeAnswer = mutableListOf<Pair<Int, Boolean>>()
            val api = mock<NcApiCoroutines> {
                on { sendReaction(any(), any(), any()) } doSuspendableAnswer {
                    persistedBeforeAnswer.add(
                        entity.reactions!!.getValue(emoji) to entity.reactionsSelf!!.contains(emoji)
                    )
                    genericOverall(HTTP_CREATED)
                }
            }

            val model = ReactionsRepositoryImpl(
                api,
                dao
            ).addReaction(credentials, userId, url, roomToken, message(), emoji)

            assertEquals(listOf(4 to true), persistedBeforeAnswer)
            assertTrue(model.success)
            assertEquals(4, entity.reactions!!.getValue(emoji))
            assertTrue(entity.reactionsSelf!!.contains(emoji))
        }

    @Test
    fun `a reaction the server reports as already applied stays applied`() =
        runTest {
            val entity = entity(reactions = linkedMapOf(), reactionsSelf = arrayListOf())
            val dao = dao(entity)
            val api = api(addStatusCode = HTTP_OK)

            val model = ReactionsRepositoryImpl(
                api,
                dao
            ).addReaction(credentials, userId, url, roomToken, message(), emoji)

            assertTrue(model.success)
            assertEquals(1, entity.reactions!!.getValue(emoji))
            assertTrue(entity.reactionsSelf!!.contains(emoji))
        }

    @Test
    fun `an added reaction survives a single transient failure`() =
        runTest {
            val entity = entity(reactions = linkedMapOf("👍" to 3), reactionsSelf = arrayListOf())
            val dao = dao(entity)
            var attempts = 0
            val api = mock<NcApiCoroutines> {
                on { sendReaction(any(), any(), any()) } doSuspendableAnswer {
                    attempts++
                    if (attempts == 1) throw IOException("connection reset")
                    genericOverall(HTTP_CREATED)
                }
            }

            val model = ReactionsRepositoryImpl(
                api,
                dao
            ).addReaction(credentials, userId, url, roomToken, message(), emoji)

            assertEquals(2, attempts)
            assertTrue(model.success)
            assertEquals(4, entity.reactions!!.getValue(emoji))
            assertTrue(entity.reactionsSelf!!.contains(emoji))
        }

    @Test
    fun `an added reaction is reverted when the retry fails as well`() =
        runTest {
            val entity = entity(reactions = linkedMapOf("👍" to 3), reactionsSelf = arrayListOf())
            val dao = dao(entity)
            var attempts = 0
            val api = mock<NcApiCoroutines> {
                on { sendReaction(any(), any(), any()) } doSuspendableAnswer {
                    attempts++
                    throw IOException("no connection")
                }
            }

            val model = ReactionsRepositoryImpl(
                api,
                dao
            ).addReaction(credentials, userId, url, roomToken, message(), emoji)

            assertEquals(2, attempts)
            assertFalse(model.success)
            assertEquals(3, entity.reactions!!.getValue(emoji))
            assertFalse(entity.reactionsSelf!!.contains(emoji))
        }

    @Test
    fun `a rejected reaction is reverted without a retry`() =
        runTest {
            val entity = entity(reactions = linkedMapOf("👍" to 3), reactionsSelf = arrayListOf())
            val dao = dao(entity)
            var attempts = 0
            val api = mock<NcApiCoroutines> {
                on { sendReaction(any(), any(), any()) } doSuspendableAnswer {
                    attempts++
                    throw httpException(HTTP_FORBIDDEN)
                }
            }

            val model = ReactionsRepositoryImpl(
                api,
                dao
            ).addReaction(credentials, userId, url, roomToken, message(), emoji)

            assertEquals(1, attempts)
            assertFalse(model.success)
            assertEquals(3, entity.reactions!!.getValue(emoji))
            assertFalse(entity.reactionsSelf!!.contains(emoji))
        }

    @Test
    fun `a deleted reaction is removed before the server answers`() =
        runTest {
            val entity = entity(reactions = linkedMapOf("👍" to 3), reactionsSelf = arrayListOf(emoji))
            val dao = dao(entity)
            val persistedBeforeAnswer = mutableListOf<Pair<Int, Boolean>>()
            val api = mock<NcApiCoroutines> {
                on { deleteReaction(any(), any(), any()) } doSuspendableAnswer {
                    persistedBeforeAnswer.add(
                        entity.reactions!!.getValue(emoji) to entity.reactionsSelf!!.contains(emoji)
                    )
                    genericOverall(HTTP_OK)
                }
            }

            val model =
                ReactionsRepositoryImpl(api, dao).deleteReaction(credentials, userId, url, roomToken, message(), emoji)

            assertEquals(listOf(2 to false), persistedBeforeAnswer)
            assertTrue(model.success)
            assertEquals(2, entity.reactions!!.getValue(emoji))
            assertFalse(entity.reactionsSelf!!.contains(emoji))
        }

    @Test
    fun `a reaction the server no longer knows counts as deleted`() =
        runTest {
            val entity = entity(reactions = linkedMapOf("👍" to 1), reactionsSelf = arrayListOf(emoji))
            val dao = dao(entity)
            val api = mock<NcApiCoroutines> {
                on { deleteReaction(any(), any(), any()) } doSuspendableAnswer {
                    throw httpException(HTTP_NOT_FOUND)
                }
            }

            val model =
                ReactionsRepositoryImpl(api, dao).deleteReaction(credentials, userId, url, roomToken, message(), emoji)

            assertTrue(model.success)
            assertEquals(0, entity.reactions!!.getValue(emoji))
            assertFalse(entity.reactionsSelf!!.contains(emoji))
        }

    @Test
    fun `a deleted reaction is restored when the request fails`() =
        runTest {
            val entity = entity(reactions = linkedMapOf("👍" to 3), reactionsSelf = arrayListOf(emoji))
            val dao = dao(entity)
            val api = mock<NcApiCoroutines> {
                on { deleteReaction(any(), any(), any()) } doSuspendableAnswer {
                    throw httpException(HTTP_FORBIDDEN)
                }
            }

            val model =
                ReactionsRepositoryImpl(api, dao).deleteReaction(credentials, userId, url, roomToken, message(), emoji)

            assertFalse(model.success)
            assertEquals(3, entity.reactions!!.getValue(emoji))
            assertTrue(entity.reactionsSelf!!.contains(emoji))
        }

    @Test
    fun `a revert leaves a reaction alone that the server confirmed meanwhile`() =
        runTest {
            val entity = entity(reactions = linkedMapOf("👍" to 3), reactionsSelf = arrayListOf())
            val dao = dao(entity)
            val api = mock<NcApiCoroutines> {
                on { sendReaction(any(), any(), any()) } doSuspendableAnswer {
                    // the chat sync writes the authoritative server state while the request is running
                    entity.reactions = linkedMapOf(emoji to 4)
                    entity.reactionsSelf = arrayListOf()
                    throw IOException("no connection")
                }
            }

            val model = ReactionsRepositoryImpl(
                api,
                dao
            ).addReaction(credentials, userId, url, roomToken, message(), emoji)

            assertFalse(model.success)
            assertEquals(4, entity.reactions!!.getValue(emoji))
            assertFalse(entity.reactionsSelf!!.contains(emoji))
        }

    @Test
    fun `a reaction the message already carried is not removed when the request fails`() =
        runTest {
            // the reaction was already in the cache, e.g. synced from another device, so this call
            // never applied it and must not take it away either
            val entity = entity(reactions = linkedMapOf("👍" to 1), reactionsSelf = arrayListOf(emoji))
            val dao = dao(entity)
            val api = mock<NcApiCoroutines> {
                on { sendReaction(any(), any(), any()) } doSuspendableAnswer {
                    throw httpException(HTTP_FORBIDDEN)
                }
            }

            val model = ReactionsRepositoryImpl(
                api,
                dao
            ).addReaction(credentials, userId, url, roomToken, message(), emoji)

            assertFalse(model.success)
            assertEquals(1, entity.reactions!!.getValue(emoji))
            assertTrue(entity.reactionsSelf!!.contains(emoji))
        }

    private fun message() = ChatMessage().apply { jsonMessageId = MESSAGE_ID.toInt() }

    private fun entity(reactions: LinkedHashMap<String, Int>, reactionsSelf: ArrayList<String>) =
        ChatMessageEntity(
            internalId = "$internalConversationId@$MESSAGE_ID",
            internalConversationId = internalConversationId,
            id = MESSAGE_ID,
            accountId = userId,
            token = roomToken,
            actorDisplayName = "Alice",
            actorId = "alice",
            actorType = "users",
            message = "a message",
            messageType = "comment",
            systemMessageType = ChatMessage.SystemMessageType.DUMMY,
            reactions = reactions,
            reactionsSelf = reactionsSelf
        )

    private fun dao(entity: ChatMessageEntity): ChatMessagesDao =
        mock<ChatMessagesDao> {
            on { getChatMessageEntity(eq(internalConversationId), eq(MESSAGE_ID)) } doReturn entity
        }

    private fun api(addStatusCode: Int): NcApiCoroutines =
        mock<NcApiCoroutines> {
            on { sendReaction(any(), any(), any()) } doReturn genericOverall(addStatusCode)
        }

    private fun genericOverall(statusCode: Int) =
        GenericOverall(GenericOCS(GenericMeta(status = "ok", statusCode = statusCode, message = null)))

    private fun httpException(code: Int) =
        HttpException(Response.error<Any>(code, "".toResponseBody("text/plain".toMediaType())))

    companion object {
        private const val MESSAGE_ID = 1L
        private const val HTTP_OK = 200
        private const val HTTP_CREATED = 201
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_NOT_FOUND = 404
    }
}
