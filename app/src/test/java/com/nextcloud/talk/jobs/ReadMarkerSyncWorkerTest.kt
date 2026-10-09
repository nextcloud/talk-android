/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.jobs

import com.nextcloud.talk.conversationlist.data.network.ConversationListUpdater
import com.nextcloud.talk.data.database.dao.ChatBlocksDao
import com.nextcloud.talk.data.database.dao.ChatMessagesDao
import com.nextcloud.talk.data.database.dao.ConversationsDao
import kotlinx.coroutines.test.runTest
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyBlocking
import retrofit2.Response

/**
 * The answer to a read marker carries the last message read by all participants. It has to reach the
 * database, otherwise the check marks of the own messages stay behind until the next sync.
 */
class ReadMarkerSyncWorkerTest {

    @Test
    fun `last common read is taken from the header of the read marker response`() {
        val response = responseWithHeader("2624")

        assertEquals(2624, ReadMarkerSyncWorker.lastCommonReadOf(response))
    }

    @Test
    fun `missing or broken header gives no last common read`() {
        assertNull(ReadMarkerSyncWorker.lastCommonReadOf(responseWithHeader(null)))
        assertNull(ReadMarkerSyncWorker.lastCommonReadOf(responseWithHeader("abc")))
    }

    @Test
    fun `last common read is written to the conversation`() =
        runTest {
            val conversationsDao: ConversationsDao = mock()
            val updater = ConversationListUpdater(mock<ChatMessagesDao>(), mock<ChatBlocksDao>(), conversationsDao)

            updater.updateLastCommonRead("7@token", 2624)

            verifyBlocking(conversationsDao) { updateLastCommonRead("7@token", 2624) }
        }

    private fun responseWithHeader(value: String?): Response<Any> {
        val headers = Headers.Builder().apply { value?.let { add("X-Chat-Last-Common-Read", it) } }.build()
        val raw = okhttp3.Response.Builder()
            .request(okhttp3.Request.Builder().url("https://cloud.example/").build())
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .headers(headers)
            .body("".toResponseBody("application/json".toMediaType()))
            .build()
        return Response.success(null, raw)
    }
}
