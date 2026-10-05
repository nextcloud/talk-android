/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.upload.chunked

import android.app.Application
import at.bitfire.dav4jvm.exception.HttpException
import com.nextcloud.talk.api.NcApiCoroutines
import com.nextcloud.talk.data.user.model.User
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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
import java.util.Collections
import java.util.concurrent.TimeUnit

@Suppress("TooManyFunctions")
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class ChunkedFileUploaderResumeTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val server = MockWebServer()
    private val requests: MutableList<RecordedRequest> = Collections.synchronizedList(mutableListOf())
    private lateinit var client: OkHttpClient
    private lateinit var user: User
    private lateinit var uploader: ChunkedFileUploader
    private lateinit var file: File

    /** Parts the fake server already holds: name -> size. */
    private var partsOnServer: Map<String, Int> = emptyMap()
    private var putResponseCode = CREATED
    private var propfindResponseCode: Int? = null
    private var propfindDisconnects = false
    private var putDisconnects = false

    /** Answers of the next MOVE requests; 201 when empty. */
    private val moveResponseCodes: MutableList<Int> = Collections.synchronizedList(mutableListOf())

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests.add(request)
                return when (request.method) {
                    "PROPFIND" -> when {
                        propfindDisconnects -> MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
                        propfindResponseCode != null -> MockResponse().setResponseCode(propfindResponseCode!!)
                        else -> propfindResponse(request.path!!)
                    }
                    "MOVE" -> MockResponse().setResponseCode(
                        if (moveResponseCodes.isEmpty()) CREATED else moveResponseCodes.removeAt(0)
                    )
                    "PUT" -> if (putDisconnects) {
                        MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
                    } else {
                        MockResponse().setResponseCode(putResponseCode)
                    }
                    "MKCOL" -> MockResponse().setResponseCode(METHOD_NOT_ALLOWED)
                    else -> MockResponse().setResponseCode(CREATED)
                }
            }
        }
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
        uploader = ChunkedFileUploader(client, user, mock<OnDataTransferProgressListener>(), mock<NcApiCoroutines>())
        file = tempFolder.newFile("video.mp4").apply { writeBytes(ByteArray(FILE_SIZE)) }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `uploads only the parts missing on the server`() {
        partsOnServer = mapOf(PART_0 to CHUNK, PART_1 to CHUNK)

        assertTrue(uploader.upload(file, null, "/Talk/video.mp4"))

        assertEquals(listOf("/$PART_2"), putPaths())
    }

    @Test
    fun `uploads everything when the server has no parts`() {
        assertTrue(uploader.upload(file, null, "/Talk/video.mp4"))

        assertEquals(listOf("/$PART_0", "/$PART_1", "/$PART_2"), putPaths())
    }

    @Test
    fun `uploads from scratch when the server removed the upload folder`() {
        propfindResponseCode = NOT_FOUND

        assertTrue(uploader.upload(file, null, "/Talk/video.mp4"))

        assertEquals(listOf("/$PART_0", "/$PART_1", "/$PART_2"), putPaths())
    }

    @Test
    fun `uploads nothing but the assembly when the server has all parts`() {
        partsOnServer = mapOf(PART_0 to CHUNK, PART_1 to CHUNK, PART_2 to LAST_CHUNK)

        assertTrue(uploader.upload(file, null, "/Talk/video.mp4"))

        assertEquals(emptyList<String>(), putPaths())
        assertEquals(1, requests.count { it.method == "MOVE" })
    }

    @Test
    fun `a part with a wrong size on the server is uploaded again`() {
        partsOnServer = mapOf(PART_0 to CHUNK, PART_1 to CHUNK - 1, PART_2 to LAST_CHUNK)

        uploader.upload(file, null, "/Talk/video.mp4")

        assertEquals(listOf("/$PART_1"), putPaths())
    }

    @Test
    fun `foreign names in the upload folder do not break the upload`() {
        partsOnServer = mapOf(PART_0 to CHUNK, "readme.txt" to 3, "1-2" to 2, ".file" to FILE_SIZE)

        assertTrue(uploader.upload(file, null, "/Talk/video.mp4"))

        assertEquals(listOf("/$PART_1", "/$PART_2"), putPaths())
    }

    @Test
    fun `assembly tells the server the total length`() {
        uploader.upload(file, null, "/Talk/video.mp4")

        val move = requests.single { it.method == "MOVE" }
        assertEquals(FILE_SIZE.toString(), move.getHeader("OC-Total-Length"))
        assertTrue(move.path!!.endsWith("/.file"))
    }

    @Test
    fun `a failing request throws and keeps the parts on the server`() {
        putResponseCode = SERVER_ERROR

        try {
            uploader.upload(file, null, "/Talk/video.mp4")
            fail("expected the upload to throw")
        } catch (e: HttpException) {
            assertEquals(SERVER_ERROR, e.code)
        }
        assertEquals(0, requests.count { it.method == "DELETE" })
    }

    @Test
    fun `a lost network while sending a part is an error and keeps the parts on the server`() {
        putDisconnects = true

        try {
            uploader.upload(file, null, "/Talk/video.mp4")
            fail("expected the upload to throw")
        } catch (e: IOException) {
            // retried later by the worker
        }
        assertEquals(0, requests.count { it.method == "DELETE" || it.method == "MOVE" })
    }

    @Test
    fun `stop interrupts the upload without removing the parts`() {
        uploader.stop()

        assertFalse(uploader.upload(file, null, "/Talk/video.mp4"))

        assertEquals(0, requests.count { it.method == "DELETE" || it.method == "MOVE" })
        assertEquals(emptyList<String>(), putPaths())
    }

    @Test
    fun `abort removes the parts from the server`() {
        uploader.upload(file, null, "/Talk/video.mp4")
        requests.clear()

        uploader.abortUpload {}

        assertEquals(1, requests.count { it.method == "DELETE" })
    }

    @Test
    fun `a rejected assembly removes the parts once and uploads the file again`() {
        moveResponseCodes.add(BAD_REQUEST)

        assertTrue(uploader.upload(file, null, "/Talk/video.mp4"))

        assertEquals(1, requests.count { it.method == "DELETE" })
        assertEquals(2, requests.count { it.method == "MOVE" })
        assertEquals(2, requests.count { it.method == "PROPFIND" })
    }

    @Test
    fun `an assembly rejected after an earlier restart is an error at once`() {
        moveResponseCodes.add(BAD_REQUEST)
        var marked = 0
        val restarted = ChunkedFileUploader(
            client,
            user,
            mock<OnDataTransferProgressListener>(),
            mock<NcApiCoroutines>(),
            isRestarted = { true },
            markRestarted = { marked++ }
        )

        try {
            restarted.upload(file, null, "/Talk/video.mp4")
            fail("expected the upload to throw")
        } catch (e: HttpException) {
            assertEquals(BAD_REQUEST, e.code)
        }
        assertEquals(0, requests.count { it.method == "DELETE" })
        assertEquals(0, marked)
    }

    @Test
    fun `a restart after a rejected assembly is reported once`() {
        moveResponseCodes.add(BAD_REQUEST)
        var marked = 0
        val first = ChunkedFileUploader(
            client,
            user,
            mock<OnDataTransferProgressListener>(),
            mock<NcApiCoroutines>(),
            markRestarted = { marked++ }
        )

        assertTrue(first.upload(file, null, "/Talk/video.mp4"))

        assertEquals(1, marked)
    }

    @Test
    fun `a server error while listing the parts is an error`() {
        propfindResponseCode = SERVER_ERROR

        try {
            uploader.upload(file, null, "/Talk/video.mp4")
            fail("expected the upload to throw")
        } catch (e: HttpException) {
            assertEquals(SERVER_ERROR, e.code)
        }
        assertEquals(emptyList<String>(), putPaths())
    }

    @Test
    fun `an assembly rejected twice is an error`() {
        moveResponseCodes.addAll(listOf(BAD_REQUEST, BAD_REQUEST))

        try {
            uploader.upload(file, null, "/Talk/video.mp4")
            fail("expected the upload to throw")
        } catch (e: HttpException) {
            assertEquals(BAD_REQUEST, e.code)
        }
        assertEquals(1, requests.count { it.method == "DELETE" })
    }

    @Test
    fun `a lost network while listing the parts is an error and does not restart the upload`() {
        propfindDisconnects = true

        try {
            uploader.upload(file, null, "/Talk/video.mp4")
            fail("expected the upload to throw")
        } catch (e: IOException) {
            // retried later by the worker
        }
        assertEquals(emptyList<String>(), putPaths())
    }

    @Test
    fun `missing parts are planned around existing ones`() {
        val onServer = listOf(Chunk(0, CHUNK - 1L), Chunk(2L * CHUNK, FILE_SIZE.toLong()))

        val missing = uploader.checkMissingChunks(onServer, FILE_SIZE.toLong())

        assertEquals(listOf(Chunk(CHUNK.toLong(), 2L * CHUNK - 1)), missing)
    }

    @Test
    fun `all parts are planned when nothing is on the server`() {
        val missing = uploader.checkMissingChunks(emptyList(), FILE_SIZE.toLong())

        assertEquals(
            listOf(Chunk(0, CHUNK - 1L), Chunk(CHUNK.toLong(), 2L * CHUNK - 1), Chunk(2L * CHUNK, FILE_SIZE.toLong())),
            missing
        )
    }

    @Test
    fun `only well formed parts of the right size are accepted`() {
        val length = FILE_SIZE.toLong()
        assertEquals(Chunk(0, CHUNK - 1L), uploader.parseUploadedChunk(PART_0, CHUNK.toLong(), length))
        assertEquals(Chunk(2L * CHUNK, length), uploader.parseUploadedChunk(PART_2, LAST_CHUNK.toLong(), length))
        assertNull(uploader.parseUploadedChunk(PART_0, CHUNK - 1L, length))
        assertNull(uploader.parseUploadedChunk(".file", length, length))
        assertNull(uploader.parseUploadedChunk("readme.txt", 3, length))
        assertNull(uploader.parseUploadedChunk("0-1", 2, length))
        assertNull(uploader.parseUploadedChunk(null, 1, length))
    }

    private fun putPaths(): List<String> =
        requests.filter { it.method == "PUT" }.map { "/" + it.path!!.substringAfterLast('/') }

    private fun propfindResponse(path: String): MockResponse {
        val folder = path.trimEnd('/')
        val members = partsOnServer.entries.joinToString("") { (name, size) ->
            """
            <d:response>
              <d:href>$folder/$name</d:href>
              <d:propstat><d:prop><d:resourcetype/><d:getcontentlength>$size</d:getcontentlength></d:prop>
              <d:status>HTTP/1.1 200 OK</d:status></d:propstat>
            </d:response>
            """
        }
        val body = """<?xml version="1.0"?>
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>$folder/</d:href>
                <d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop>
                <d:status>HTTP/1.1 200 OK</d:status></d:propstat>
              </d:response>
              $members
            </d:multistatus>"""
        return MockResponse()
            .setResponseCode(MULTI_STATUS)
            .setHeader("Content-Type", "application/xml; charset=utf-8")
            .setBody(body)
    }

    companion object {
        private const val CHUNK = 1_024_000
        private const val FILE_SIZE = 2 * CHUNK + 952_000
        private const val LAST_CHUNK = 952_000
        private const val PART_0 = "0000000000000000-0000000001023999"
        private const val PART_1 = "0000000001024000-0000000002047999"
        private const val PART_2 = "0000000002048000-0000000003000000"
        private const val TIMEOUT_SECONDS = 10L
        private const val CREATED = 201
        private const val MULTI_STATUS = 207
        private const val METHOD_NOT_ALLOWED = 405
        private const val NOT_FOUND = 404
        private const val BAD_REQUEST = 400
        private const val SERVER_ERROR = 500
    }
}
