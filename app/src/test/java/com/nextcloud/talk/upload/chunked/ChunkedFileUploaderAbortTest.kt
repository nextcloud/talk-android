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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/**
 * WorkManager calls the worker's onStopped from a coroutine cancellation handler. An exception thrown by
 * [ChunkedFileUploader.abortUpload] there crashes the process, so it must never throw, whatever the network does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class ChunkedFileUploaderAbortTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val server = MockWebServer()
    private val ncApiCoroutines: NcApiCoroutines = mock()
    private val progressListener: OnDataTransferProgressListener = mock()
    private lateinit var client: OkHttpClient
    private lateinit var user: User
    private var successCalls = 0

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
            userId = "alice",
            username = "alice",
            token = "token",
            baseUrl = server.url("/").toString().trimEnd('/')
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `does not throw and sends no request when the upload was never started`() {
        uploader().abortUpload { successCalls++ }

        assertEquals(0, server.requestCount)
        assertEquals(0, successCalls)
    }

    @Test
    fun `does not throw and does not report success when the connection drops`() {
        val uploader = startedUploader()
        server.enqueue(MockResponse().apply { socketPolicy = SocketPolicy.DISCONNECT_AFTER_REQUEST })

        uploader.abortUpload { successCalls++ }

        assertEquals(0, successCalls)
    }

    @Test
    fun `does not throw and does not report success on a server error`() {
        val uploader = startedUploader()
        server.enqueue(MockResponse().setResponseCode(SERVER_ERROR))

        uploader.abortUpload { successCalls++ }

        assertEquals(0, successCalls)
    }

    @Test
    fun `reports success when the upload folder is already gone`() {
        val uploader = startedUploader()
        server.enqueue(MockResponse().setResponseCode(NOT_FOUND))

        uploader.abortUpload { successCalls++ }

        assertEquals(1, successCalls)
    }

    @Test
    fun `reports success when the upload folder is deleted`() {
        val uploader = startedUploader()
        server.enqueue(MockResponse().setResponseCode(NO_CONTENT))

        uploader.abortUpload { successCalls++ }

        assertEquals(1, successCalls)
    }

    @Test
    fun `does not throw when the success callback throws`() {
        val uploader = startedUploader()
        server.enqueue(MockResponse().setResponseCode(NO_CONTENT))

        uploader.abortUpload { throw IllegalStateException("callback failed") }
    }

    @Test
    fun `does not throw when the success callback throws for a folder that is already gone`() {
        val uploader = startedUploader()
        server.enqueue(MockResponse().setResponseCode(NOT_FOUND))

        uploader.abortUpload { throw IllegalStateException("callback failed") }
    }

    private fun uploader() = ChunkedFileUploader(client, user, progressListener, ncApiCoroutines)

    /** Runs a rejected upload so the uploader knows its upload folder, then drops the recorded requests. */
    private fun startedUploader(): ChunkedFileUploader {
        val file = tempFolder.newFile("test.txt").apply { writeBytes(ByteArray(FILE_SIZE)) }
        server.enqueue(MockResponse().setResponseCode(CREATED))
        server.enqueue(MockResponse().setResponseCode(NOT_FOUND))
        server.enqueue(MockResponse().setResponseCode(FORBIDDEN))
        val uploader = uploader()
        runCatching { uploader.upload(file, "text/plain".toMediaType(), "/Talk/test.txt") }
        repeat(server.requestCount) { server.takeRequest(1, TimeUnit.SECONDS) }
        return uploader
    }

    companion object {
        private const val FILE_SIZE = 5
        private const val TIMEOUT_SECONDS = 10L
        private const val CREATED = 201
        private const val NO_CONTENT = 204
        private const val FORBIDDEN = 403
        private const val NOT_FOUND = 404
        private const val SERVER_ERROR = 500
    }
}
