/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.mediaviewer.stream

import android.net.Uri
import androidx.media3.datasource.DataSpec
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import retrofit2.Call
import retrofit2.Response
import java.io.IOException

class ProgressiveVideoDataSourceTest {
    @get:Rule val folder = TemporaryFolder()
    private val uri = mock<Uri>()

    @Test
    fun `player reads the downloaded prefix without another network request`() {
        val file = folder.newFile().apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val download = ProgressiveVideoFile(100, "etag")
        download.attach(file)
        download.advance(3)
        val source = ProgressiveVideoDataSource(download) { _, _ -> error("Unexpected network read") }
        try {
            assertEquals(100L, source.open(DataSpec.Builder().setUri(uri).build()))
            val bytes = ByteArray(10)
            assertEquals(3, source.read(bytes, 0, bytes.size))
            assertArrayEquals(byteArrayOf(1, 2, 3), bytes.copyOf(3))
        } finally {
            source.close()
        }
    }

    @Test
    fun `player can fetch tail metadata before the sequential download completes`() {
        val download = ProgressiveVideoFile(100, "etag")
        val call = response(206, "bytes 97-99/100")
        val source = ProgressiveVideoDataSource(download) { start, validator ->
            assertEquals(97L, start)
            assertEquals("etag", validator)
            call
        }
        try {
            assertEquals(3L, source.open(DataSpec.Builder().setUri(uri).setPosition(97).build()))
            val bytes = ByteArray(3)
            assertEquals(3, source.read(bytes, 0, 3))
            assertArrayEquals(byteArrayOf(7, 8, 9), bytes)
            assertEquals(-1, source.read(bytes, 0, 3))
        } finally {
            source.close()
        }
        verify(call).cancel()
    }

    @Test
    fun `server ignoring range falls back to the original download`() {
        val file = folder.newFile().apply { writeBytes(ByteArray(100) { 4 }) }
        val download = ProgressiveVideoFile(100, "etag")
        download.attach(file)
        val call = response(200, null)
        val source = ProgressiveVideoDataSource(download) { _, _ -> call }
        try {
            source.open(DataSpec.Builder().setUri(uri).setPosition(97).build())
            download.advance(100)
            val bytes = ByteArray(3)
            assertEquals(3, source.read(bytes, 0, 3))
            assertArrayEquals(byteArrayOf(4, 4, 4), bytes)
        } finally {
            source.close()
        }
    }

    @Test
    fun `invalid range fails and still releases the request`() {
        val call = response(206, "bytes 0-2/100")
        val source = ProgressiveVideoDataSource(ProgressiveVideoFile(100, "etag")) { _, _ -> call }
        try {
            assertThrows(IOException::class.java) {
                source.open(DataSpec.Builder().setUri(uri).setPosition(97).build())
            }
        } finally {
            source.close()
        }
        verify(call).cancel()
    }

    private fun response(code: Int, range: String?): Call<ResponseBody> {
        val raw = okhttp3.Response.Builder()
            .request(Request.Builder().url("https://video.test/file").build())
            .protocol(Protocol.HTTP_1_1).code(code).message("test")
        if (range != null) raw.header("Content-Range", range)
        val call = mock<Call<ResponseBody>>()
        whenever(call.execute()).thenReturn(Response.success(byteArrayOf(7, 8, 9).toResponseBody(), raw.build()))
        return call
    }
}
