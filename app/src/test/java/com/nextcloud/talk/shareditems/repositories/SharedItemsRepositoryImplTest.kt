/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.shareditems.repositories

import com.nextcloud.talk.api.NcApiCoroutines
import com.nextcloud.talk.models.json.chat.ChatMessageDto
import com.nextcloud.talk.models.json.chat.ChatShareOCS
import com.nextcloud.talk.models.json.chat.ChatShareOverall
import com.nextcloud.talk.shareditems.model.SharedItemType
import com.nextcloud.talk.utils.DateUtils
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking
import retrofit2.HttpException
import retrofit2.Response

class SharedItemsRepositoryImplTest {

    private val ncApiCoroutines: NcApiCoroutines = mock()
    private val dateUtils: DateUtils = mock()
    private val repository = SharedItemsRepositoryImpl(ncApiCoroutines, dateUtils)

    private val parameters = SharedItemsRepository.Parameters("user", "token", "https://example.com", "roomToken")

    @Test
    fun `more items exist when a full page contains a message that cannot be mapped`() =
        runTest {
            whenever(dateUtils.getLocalDateTimeStringFromTimestamp(any())).thenReturn("")
            val messages = (1L..SharedItemsRepositoryImpl.BATCH_SIZE).associate { id ->
                val message = if (id == 1L) unmappableMessage(id) else pollMessage(id)
                id.toString() to message
            }
            wheneverBlocking {
                ncApiCoroutines.getSharedItems(anyOrNull(), any(), any(), anyOrNull(), anyOrNull())
            }.thenReturn(Response.success(ChatShareOverall(ocs = ChatShareOCS(data = HashMap(messages)))))

            val page = repository.media(parameters, SharedItemType.POLL)

            assertEquals(SharedItemsRepositoryImpl.BATCH_SIZE - 1, page.items.size)
            assertTrue(page.moreItemsExisting)
        }

    @Test(expected = HttpException::class)
    fun `failed overview request throws`() =
        runTest {
            wheneverBlocking { ncApiCoroutines.getSharedItemsOverview(anyOrNull(), any(), anyOrNull()) }
                .thenReturn(Response.error(HTTP_SERVICE_UNAVAILABLE, "".toResponseBody()))

            repository.availableTypes(parameters)
        }

    private fun pollMessage(id: Long) =
        ChatMessageDto(
            id = id,
            message = "{object}",
            messageParameters = hashMapOf(
                "object" to hashMapOf<String?, String?>("type" to "talk-poll", "id" to "$id", "name" to "Poll $id"),
                "actor" to hashMapOf<String?, String?>("id" to "actor", "name" to "Actor")
            )
        )

    private fun unmappableMessage(id: Long) = ChatMessageDto(id = id, message = "text")

    companion object {
        private const val HTTP_SERVICE_UNAVAILABLE = 503
    }
}
