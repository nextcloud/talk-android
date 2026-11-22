/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.api

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import java.io.IOException
import com.nextcloud.talk.logger.Level
import com.nextcloud.talk.logger.Logger
import com.nextcloud.talk.logger.LogsRepository
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class FileDownloadStreamingTest {
    @Test
    fun `large download returns before reading the file body into memory`() {
        assertStreaming(advancedLogging = false)
    }

    @Test
    fun `advanced logging does not buffer a large file body`() {
        assertStreaming(advancedLogging = true)
    }

    @Test
    fun `video range response stays streaming with authentication and advanced logging`() {
        assertStreaming(advancedLogging = true, range = true)
    }

    private fun assertStreaming(advancedLogging: Boolean, range: Boolean = false) {
        var bytesRead = 0L
        var closed = false
        val source = object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long {
                if (bytesRead >= READ_LIMIT) throw IOException("File body buffered before the caller could save it")
                val count = minOf(byteCount, READ_LIMIT - bytesRead)
                sink.write(ByteArray(count.toInt()))
                bytesRead += count
                return count
            }

            override fun timeout(): Timeout = Timeout.NONE
            override fun close() {
                closed = true
            }
        }.buffer()
        val body = object : ResponseBody() {
            override fun contentType() = "video/mp4".toMediaType()
            override fun contentLength() = LARGE_FILE_SIZE
            override fun source() = source
        }
        val builder = OkHttpClient.Builder()
        if (advancedLogging) {
            val logs = mock<LogsRepository>()
            whenever(logs.minimumLevel).thenReturn(Level.DEBUG)
            builder.addInterceptor(LoggingHttpInterceptor(mock<Logger>(), logs))
        }
        val client = builder.addInterceptor { chain ->
            if (range) {
                assertEquals("bytes=100-", chain.request().header("Range"))
                assertEquals("version-one", chain.request().header("If-Range"))
                assertEquals("test", chain.request().header("Authorization"))
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(body).build()
        }.build()
        val api = Retrofit.Builder().baseUrl("https://download.test/").client(client).build().create(NcApi::class.java)

        val call = if (range) {
            api.downloadVideoRange("test", "https://download.test/video.mp4", "bytes=100-", "version-one")
        } else {
            api.downloadFile("test", "https://download.test/video.mp4")
        }
        call.execute().body()!!.use { response ->
            assertEquals(LARGE_FILE_SIZE, response.contentLength())
            assertEquals(0L, bytesRead)
            assertEquals(0, response.byteStream().read())
            assertTrue(bytesRead in 1..READ_LIMIT)
        }
        assertTrue(closed)
    }

    companion object {
        private const val READ_LIMIT = 64L * 1024
        private const val LARGE_FILE_SIZE = 750L * 1024 * 1024
    }
}
