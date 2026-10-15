/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.jobs

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import android.net.Uri
import androidx.concurrent.futures.ResolvableFuture
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.api.NcApiCoroutines
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.data.database.dao.ChatMessagesDao
import com.nextcloud.talk.data.database.model.ChatMessageEntity
import com.nextcloud.talk.data.database.model.SendStatus
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.capabilities.CapabilitiesDto
import com.nextcloud.talk.models.json.capabilities.SpreedCapabilityDto
import com.nextcloud.talk.models.json.chatprobeattachmentfolder.ChatProbeAttachmentDataDto
import com.nextcloud.talk.models.json.chatprobeattachmentfolder.ChatProbeAttachmentFolderOCS
import com.nextcloud.talk.models.json.chatprobeattachmentfolder.ChatProbeAttachmentFolderOverall
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.upload.PreparedUpload
import com.nextcloud.talk.upload.UploadRetryPolicy
import com.nextcloud.talk.upload.UploadWorkspace
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_INTERNAL_USER_ID
import io.reactivex.Observable
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.HttpException
import retrofit2.Response
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * What an upload that fails leaves behind: the state of the work, the placeholder message of the chat and the
 * workspace. The worker runs directly (doWork), without a WorkManager, so one run is one call.
 */
@Suppress("TooManyFunctions")
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class UploadAndShareFilesWorkerFailureTest {

    private val rows = ConcurrentHashMap<String, ChatMessageEntity>()
    private val foregroundRequests = AtomicInteger()
    private val spreed: SpreedCapabilityDto = mock()
    private val capabilities: CapabilitiesDto = mock { on { spreedCapability } doReturn spreed }
    private val user: User = mock {
        on { username } doReturn "alice"
        on { userId } doReturn "alice"
        on { token } doReturn "secret"
        on { baseUrl } doReturn "https://cloud.example"
        on { capabilities } doReturn capabilities
    }
    private val userManager: UserManager = mock {
        on { runBlocking { getUserWithId(ACCOUNT_ID) } } doReturn user
    }
    private val ncApi: NcApi = mock()
    private val ncApiCoroutines: NcApiCoroutines = mock()
    private val server = MockWebServer()
    private val chatDao: ChatMessagesDao = mock {
        on { getTempMessageForConversation(any(), any(), anyOrNull()) } doAnswer {
            flowOf(rows[it.getArgument<String>(1)])
        }
    }

    init {
        // The worker writes only the send status of the temporary row.
        doAnswer {
            val referenceId = it.getArgument<String>(1)
            val row = rows[referenceId]
            row?.let { found -> rows[referenceId] = found.copy(sendStatus = it.getArgument(2)) }
            if (row == null) 0 else 1
        }.whenever(chatDao).updateTempMessageSendStatus(any(), any(), any())
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    @Test
    fun `an unavailable server is waited for and the placeholder stays pending`() {
        val worker = worker()
        uploadedAndRefusedByServer(500)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
        assertEquals(SendStatus.PENDING, rows[REFERENCE_ID]?.sendStatus)
        assertEquals(true, File(workspaceDir(worker.id), "unavailable").exists())
    }

    @Test
    fun `a server that stays unavailable for 24 hours fails the upload`() {
        val worker = worker()
        uploadedAndRefusedByServer(503)
        // The first "unavailable" answer came 25 hours ago, the server kept answering and the upload did not move on.
        repeat(UploadRetryPolicy.MIN_UNAVAILABLE_ANSWERS) {
            UploadWorkspace(workspaceDir(worker.id)).registerUnavailable(now() - HOURS_25)
        }

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(SendStatus.FAILED, rows[REFERENCE_ID]?.sendStatus)
        assertFalse("the workspace is removed", workspaceDir(worker.id).exists())
    }

    @Test
    fun `time without a network does not fail the upload at the first answer of the server`() {
        val worker = worker()
        uploadedAndRefusedByServer(503)
        // One answer 25 hours ago, then no network until now: this is the second answer.
        UploadWorkspace(workspaceDir(worker.id)).registerUnavailable(now() - HOURS_25)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
        assertEquals(SendStatus.PENDING, rows[REFERENCE_ID]?.sendStatus)
    }

    @Test
    fun `a clock that went back starts the 24 hours again`() {
        val worker = worker()
        uploadedAndRefusedByServer(503)
        repeat(ANSWERS) { UploadWorkspace(workspaceDir(worker.id)).registerUnavailable(now() + HOURS_25) }
        val before = now()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
        val since = File(workspaceDir(worker.id), "unavailable").readLines().first().toLong()
        assertTrue("the clock starts at the time of the answer", since in before..now())
    }

    @Test
    fun `the file that got uploaded since the last answer starts the 24 hours again`() {
        val worker = worker()
        uploadedAndRefusedByServer(503)
        // The last answers came before the file was uploaded (the point -1 is "nothing sent yet").
        seedUnavailable(worker, since = now() - HOURS_25, point = -1, answers = ANSWERS)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
        assertEquals(SendStatus.PENDING, rows[REFERENCE_ID]?.sendStatus)
    }

    @Test
    fun `progress made after the last answer keeps the upload alive in a later run`() {
        // Run 1: unavailable at 10 %. The upload went on to 80 %, then the network was lost and the process killed.
        // Run 2, 26 hours later: the first request of the run, the probe, is answered "unavailable".
        val worker = worker(source = fileUri(SMALL_FILE_SIZE), uploaded = false, draft = true)
        probeRefusedByServer(503)
        seedUnavailable(worker, since = now() - HOURS_26, point = 10, answers = ANSWERS)
        File(workspaceDir(worker.id), "progress").writeText("80")

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
        assertEquals(SendStatus.PENDING, rows[REFERENCE_ID]?.sendStatus)
    }

    @Test
    fun `an upload restarted from the beginning is not failed for the progress it had before`() {
        val worker = worker(source = fileUri(SMALL_FILE_SIZE), uploaded = false, draft = true)
        probeRefusedByServer(503)
        seedUnavailable(worker, since = now() - HOURS_25, point = 100, answers = ANSWERS)
        val workspace = UploadWorkspace(workspaceDir(worker.id))
        workspace.recordProgress(100)
        // The server rejected the assembly: the chunks are sent again from 0 %, now at 30 %.
        workspace.markRestarted()
        workspace.recordProgress(30)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
        assertEquals(SendStatus.PENDING, rows[REFERENCE_ID]?.sendStatus)
    }

    @Test
    fun `progress reported to the worker starts the 24 hours again`() {
        val id = UUID.randomUUID()
        val first = worker(id = id, source = fileUri(SMALL_FILE_SIZE), uploaded = false, draft = true)
        probeRefusedByServer(503)
        assertEquals(ListenableWorker.Result.retry(), first.doWork())
        // The upload sends data, the network is lost, 25 hours pass (the workspace of the retry is kept).
        first.onTransferProgress(PROGRESS_PERCENT)
        seedUnavailable(first, since = now() - HOURS_25, point = -1, answers = ANSWERS)

        val second = worker(id = id, source = fileUri(SMALL_FILE_SIZE), uploaded = false, draft = true)
        probeRefusedByServer(503)
        val result = second.doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
        assertEquals(SendStatus.PENDING, rows[REFERENCE_ID]?.sendStatus)
    }

    @Test
    fun `a workspace that cannot be written does not end the work as failed`() {
        val worker = worker()
        uploadedAndRefusedByServer(503)
        // A directory in the place of the temporary file makes every write of this file fail.
        File(workspaceDir(worker.id), "unavailable.tmp").mkdirs()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
        assertEquals(SendStatus.PENDING, rows[REFERENCE_ID]?.sendStatus)
    }

    @Test
    fun `a server error that cannot be stored does not end the work as failed`() {
        val worker = worker()
        uploadedAndRefusedByServer(403)
        File(workspaceDir(worker.id), "server_errors.tmp").mkdirs()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
        assertEquals(SendStatus.PENDING, rows[REFERENCE_ID]?.sendStatus)
    }

    // The file "unavailable" as the workspace writes it: the time, the progress point and the number of answers.
    private fun seedUnavailable(worker: UploadAndShareFilesWorker, since: Long, point: Int, answers: Int) {
        workspaceDir(worker.id).mkdirs()
        File(workspaceDir(worker.id), "unavailable").writeText("$since\n$point\n$answers")
    }

    private fun probeRefusedByServer(code: Int) {
        val error = HttpException(Response.error<Any>(code, "".toResponseBody()))
        runBlocking {
            whenever(ncApiCoroutines.probeConversationAttachmentFolder(any(), any(), any())).thenThrow(error)
        }
    }

    @Test
    fun `a failed upload reports the failure in the output data`() {
        val worker = worker()
        whenever(runBlocking { userManager.getUserWithId(ACCOUNT_ID) }).thenReturn(null)

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(true, result.outputData.getBoolean(UploadAndShareFilesWorker.KEY_UPLOAD_FAILED, false))
        assertEquals(SendStatus.FAILED, rows[REFERENCE_ID]?.sendStatus)
    }

    @Test
    fun `a cancelled upload does not report a failure`() {
        val worker = worker()
        UploadWorkspace(workspaceDir(worker.id)).markCancelled()

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertFalse(result.outputData.getBoolean(UploadAndShareFilesWorker.KEY_UPLOAD_FAILED, false))
    }

    @Test
    fun `a delivered upload does not report a failure`() {
        val worker = worker()
        UploadWorkspace(workspaceDir(worker.id)).markShared()

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertFalse(result.outputData.getBoolean(UploadAndShareFilesWorker.KEY_UPLOAD_FAILED, false))
        assertEquals(SendStatus.SENT_PENDING_ACK, rows[REFERENCE_ID]?.sendStatus)
    }

    @Test
    fun `a failed upload removes its file from the draft folder`() {
        val server = startServer()
        val worker = worker(draft = true)
        refusePublishing(worker)

        worker.doWork()

        assertEquals(SendStatus.FAILED, rows[REFERENCE_ID]?.sendStatus)
        val request = server.takeRequest(REQUEST_WAIT_SECONDS, TimeUnit.SECONDS)
        assertEquals("DELETE", request?.method)
        assertEquals("/remote.php/dav/files/alice/Talk/draft/id-photo.jpg", request?.path)
    }

    @Test
    fun `a failed upload leaves the file in the attachment folder of Talk`() {
        val server = startServer()
        val worker = worker(draft = false)
        refusePublishing(worker)

        worker.doWork()

        assertEquals(SendStatus.FAILED, rows[REFERENCE_ID]?.sendStatus)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a cancelled upload still removes its draft file`() {
        val server = startServer()
        val worker = worker(draft = true)
        UploadWorkspace(workspaceDir(worker.id)).markCancelled()

        worker.doWork()

        assertEquals("DELETE", server.takeRequest(REQUEST_WAIT_SECONDS, TimeUnit.SECONDS)?.method)
    }

    // Three server errors are counted already, so the next refusal of the server is the last one.
    private fun refusePublishing(worker: UploadAndShareFilesWorker) {
        val workspace = UploadWorkspace(workspaceDir(worker.id))
        repeat(UploadRetryPolicy.MAX_SERVER_ERRORS - 1) { workspace.registerServerError() }
        val forbidden = HttpException(Response.error<Any>(FORBIDDEN, "".toResponseBody()))
        // The draft path publishes with postConversationAttachment, the attachment folder path shares.
        whenever(runBlocking { ncApiCoroutines.postConversationAttachment(any(), any(), any()) }).thenThrow(forbidden)
        whenever(ncApi.createRemoteShare(anyOrNull(), any(), any(), any(), any(), anyOrNull(), any()))
            .thenReturn(Observable.error<GenericOverall>(forbidden))
    }

    private fun startServer(): MockWebServer {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setResponseCode(NO_CONTENT)
        }
        server.start()
        whenever(user.baseUrl).thenReturn(server.url("/").toString().trimEnd('/'))
        return server
    }

    @Test
    fun `a small file is uploaded without a foreground service`() {
        val worker = worker(source = fileUri(SMALL_FILE_SIZE), uploaded = false)

        worker.doWork()

        assertEquals(0, foregroundRequests.get())
    }

    @Test
    fun `a file that is uploaded in chunks runs as a foreground service`() {
        val worker = worker(source = fileUri(BIG_FILE_SIZE), uploaded = false)

        worker.doWork()

        assertEquals(1, foregroundRequests.get())
    }

    @Test
    fun `an upload that only needs the last request runs without a foreground service`() {
        val worker = worker(source = fileUri(BIG_FILE_SIZE), uploaded = true)
        uploadedAndRefusedByServer(500)

        worker.doWork()

        assertEquals(0, foregroundRequests.get())
    }

    @Test
    fun `a later run takes the size from the prepared copy, not from the source`() {
        // The source of unknown size would count as big; its copy is small.
        val worker = worker(source = "content://media/photo.jpg", uploaded = false, preparedSize = SMALL_FILE_SIZE)

        worker.doWork()

        assertEquals(0, foregroundRequests.get())
    }

    @Test
    fun `a later run with a big prepared copy runs as a foreground service`() {
        val worker = worker(source = fileUri(SMALL_FILE_SIZE), uploaded = false, preparedSize = BIG_FILE_SIZE)

        worker.doWork()

        assertEquals(1, foregroundRequests.get())
    }

    @Test
    fun `an empty file is uploaded without a foreground service`() {
        val worker = worker(source = fileUri(0), uploaded = false)

        worker.doWork()

        assertEquals(0, foregroundRequests.get())
    }

    @Test
    fun `an empty prepared copy is taken as it is and needs no foreground service`() {
        // The source of unknown size would count as big; the copy is empty and small.
        val worker = worker(source = "content://media/empty.txt", uploaded = false, preparedSize = 0)

        worker.doWork()

        assertEquals(0, foregroundRequests.get())
    }

    private fun fileUri(size: Int): String =
        Uri.fromFile(File(context().cacheDir, "source-$size.bin").apply { writeBytes(ByteArray(size)) }).toString()

    /** The file is uploaded already and the server refuses the share with [code]. */
    private fun uploadedAndRefusedByServer(code: Int) {
        whenever(ncApi.createRemoteShare(anyOrNull(), any(), any(), any(), any(), anyOrNull(), any()))
            .thenReturn(Observable.error<GenericOverall>(HttpException(Response.error<Any>(code, "".toResponseBody()))))
    }

    private fun worker(
        source: String = "content://media/photo.jpg",
        uploaded: Boolean = true,
        draft: Boolean = false,
        id: UUID? = null,
        preparedSize: Int? = null
    ): UploadAndShareFilesWorker {
        rows[REFERENCE_ID] = placeholder()
        if (draft) {
            // The draft folder path is used when the server has conversation subfolders.
            val attachments = hashMapOf<String, Any>("conversation-subfolders" to true)
            whenever(spreed.config).thenReturn(hashMapOf("attachments" to attachments))
            runBlocking {
                whenever(ncApiCoroutines.probeConversationAttachmentFolder(any(), any(), any())).thenReturn(
                    ChatProbeAttachmentFolderOverall(
                        ChatProbeAttachmentFolderOCS(null, ChatProbeAttachmentDataDto(folder = "Talk/draft"))
                    )
                )
            }
        }
        // Without capabilities a run that has to upload ends at once, after the foreground service was asked for.
        if (!uploaded && !draft) {
            whenever(user.capabilities).thenReturn(null)
        }
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker =
                UploadAndShareFilesWorker(appContext, workerParameters).also {
                    it.userManager = userManager
                    it.chatDao = chatDao
                    it.ncApi = ncApi
                    it.ncApiCoroutines = ncApiCoroutines
                    it.okHttpClient = OkHttpClient()
                }
        }
        val input = Data.Builder()
            .putLong(KEY_INTERNAL_USER_ID, ACCOUNT_ID)
            .putString("DEVICE_SOURCE_FILE", source)
            .putString("ROOM_TOKEN", "room")
            .putString("CONVERSATION_NAME", "Room")
            .putString(UploadAndShareFilesWorker.KEY_REFERENCE_ID, REFERENCE_ID)
            .putString(UploadAndShareFilesWorker.KEY_INTERNAL_CONVERSATION_ID, CONVERSATION_ID)
            .build()
        val worker = TestListenableWorkerBuilder<UploadAndShareFilesWorker>(contextWithStrings())
            .setInputData(input)
            .setWorkerFactory(factory)
            .apply { id?.let { setId(it) } }
            .setForegroundUpdater { _, _, _ ->
                foregroundRequests.incrementAndGet()
                ResolvableFuture.create<Void>().also { it.set(null) }
            }
            .build()
        if (uploaded) {
            val path = if (draft) "/Talk/draft/id-photo.jpg" else "/Talk/photo.jpg"
            UploadWorkspace(workspaceDir(worker.id)).markUploaded(path, "photo.jpg", draft)
        }
        preparedSize?.let { size ->
            UploadWorkspace(workspaceDir(worker.id)).prepareOnce { dir ->
                PreparedUpload(File(dir, "photo.jpg").apply { writeBytes(ByteArray(size)) }, "photo.jpg")
            }
        }
        return worker
    }

    private fun placeholder() =
        ChatMessageEntity(
            internalId = "$CONVERSATION_ID@_temp_$REFERENCE_ID",
            accountId = ACCOUNT_ID,
            token = "room",
            internalConversationId = CONVERSATION_ID,
            actorDisplayName = "Me",
            message = "file",
            actorId = "me",
            actorType = "users",
            messageType = "comment",
            systemMessageType = ChatMessage.SystemMessageType.DUMMY,
            referenceId = REFERENCE_ID,
            isTemporary = true,
            sendStatus = SendStatus.PENDING
        )

    private fun workspaceDir(id: UUID) = File(File(context().noBackupFilesDir, "uploads"), id.toString())

    private fun now() = System.currentTimeMillis()

    // The unit tests have no resources of the app; the failure notification only needs some text.
    private fun contextWithStrings(): Context {
        val base = context().resources

        @Suppress("DEPRECATION")
        val resources = object : Resources(base.assets, base.displayMetrics, base.configuration) {
            override fun getString(id: Int): String = "text %s"
        }
        return object : ContextWrapper(context()) {
            override fun getResources(): Resources = resources
        }
    }

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    companion object {
        private const val ACCOUNT_ID = 1L
        private const val CONVERSATION_ID = "1@room"
        private const val REFERENCE_ID = "ref-a"
        private const val HOURS_25 = 25L * 60 * 60 * 1000
        private const val HOURS_26 = 26L * 60 * 60 * 1000
        private const val ANSWERS = 10
        private const val PROGRESS_PERCENT = 50
        private const val FORBIDDEN = 403
        private const val NO_CONTENT = 204
        private const val REQUEST_WAIT_SECONDS = 5L
        private const val SMALL_FILE_SIZE = 10 * 1024
        private const val BIG_FILE_SIZE = 1024 * 1024 + 1
    }
}
