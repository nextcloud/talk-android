/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.upload.chunked

import android.app.Application
import com.nextcloud.talk.api.NcApiCoroutines
import com.nextcloud.talk.data.user.model.User
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * A network failure must reach the caller as an [IOException] so the upload can be retried, while HTTP errors are
 * reported as a plain `false`. PROPFIND is answered with 404, which the uploader treats as "no chunks
 * on the server yet".
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class ChunkedFileUploaderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val server = MockWebServer()
    private val ncApiCoroutines: NcApiCoroutines = mock()
    private val progressListener: OnDataTransferProgressListener = mock()
    private lateinit var client: OkHttpClient
    private lateinit var user: User

    @Before
    fun setUp() {
        server.start()
        client = OkHttpClient.Builder()
            .retryOnConnectionFailure(false)
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
        user = User(
            id = 1,
            userId = USER_ID,
            username = USER_ID,
            token = "token",
            baseUrl = server.url("/").toString().trimEnd('/')
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `uploads the chunk and assembles the file on the happy path`() {
        val file = localFile(SMALL_FILE_SIZE)
        enqueueFolderCreated()
        enqueueNoChunksOnServer()
        server.enqueue(MockResponse().setResponseCode(CREATED))
        server.enqueue(MockResponse().setResponseCode(CREATED))
        server.enqueue(MockResponse().setResponseCode(CREATED))

        val result = uploader().upload(file, MIME_TYPE, TARGET_PATH)

        assertTrue(result)
        assertEquals(listOf("MKCOL", "PROPFIND", "PUT", "MKCOL", "MOVE"), takeRequests().map { it.method })
    }

    @Test
    fun `throws IOException when the connection drops during the chunk upload`() {
        val file = localFile(SMALL_FILE_SIZE)
        enqueueFolderCreated()
        enqueueNoChunksOnServer()
        server.enqueue(MockResponse().apply { socketPolicy = SocketPolicy.DISCONNECT_AFTER_REQUEST })

        assertThrows(IOException::class.java) {
            uploader().upload(file, MIME_TYPE, TARGET_PATH)
        }

        assertEquals(listOf("MKCOL", "PROPFIND", "PUT"), takeRequests().map { it.method })
    }

    @Test
    fun `returns false when the server rejects the chunk with an HTTP error`() {
        val file = localFile(SMALL_FILE_SIZE)
        enqueueFolderCreated()
        enqueueNoChunksOnServer()
        server.enqueue(MockResponse().setResponseCode(FORBIDDEN))

        val result = uploader().upload(file, MIME_TYPE, TARGET_PATH)

        assertFalse(result)
        assertEquals(listOf("MKCOL", "PROPFIND", "PUT"), takeRequests().map { it.method })
    }

    @Test
    fun `returns false when the server rejects assembling the chunks with an HTTP error`() {
        val file = localFile(SMALL_FILE_SIZE)
        enqueueFolderCreated()
        enqueueNoChunksOnServer()
        server.enqueue(MockResponse().setResponseCode(CREATED))
        server.enqueue(MockResponse().setResponseCode(CREATED))
        server.enqueue(MockResponse().setResponseCode(FORBIDDEN))

        val result = uploader().upload(file, MIME_TYPE, TARGET_PATH)

        assertFalse(result)
        assertEquals(listOf("MKCOL", "PROPFIND", "PUT", "MKCOL", "MOVE"), takeRequests().map { it.method })
    }

    private fun uploader() = ChunkedFileUploader(client, user, progressListener, ncApiCoroutines)

    private fun localFile(size: Int): File = tempFolder.newFile("test.txt").apply { writeBytes(ByteArray(size)) }

    private fun enqueueFolderCreated() {
        server.enqueue(MockResponse().setResponseCode(CREATED))
    }

    private fun enqueueNoChunksOnServer() {
        server.enqueue(MockResponse().setResponseCode(NOT_FOUND))
    }

    private fun takeRequests() = (0 until server.requestCount).map { server.takeRequest(1, TimeUnit.SECONDS)!! }

    companion object {
        private const val USER_ID = "alice"
        private const val TARGET_PATH = "/Talk/test.txt"
        private const val SMALL_FILE_SIZE = 5
        private const val TIMEOUT_SECONDS = 10L
        private const val CREATED = 201
        private const val FORBIDDEN = 403
        private const val NOT_FOUND = 404
        private val MIME_TYPE = "text/plain".toMediaType()
    }
}
