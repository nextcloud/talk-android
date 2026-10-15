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
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.upload.UploadWorkspace
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_INTERNAL_USER_ID
import io.reactivex.Observable
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

/**
 * What an upload that fails leaves behind: the state of the work, the placeholder message of the chat and the
 * workspace. The worker runs directly (doWork), without a WorkManager, so one run is one call.
 */
@Suppress("TooManyFunctions")
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class UploadAndShareFilesWorkerFailureTest {

    private val rows = ConcurrentHashMap<String, ChatMessageEntity>()
    private val spreed: SpreedCapabilityDto = mock()
    private val capabilities: CapabilitiesDto = mock { on { spreedCapability } doReturn spreed }
    private val user: User = mock {
        on { username } doReturn "alice"
        on { token } doReturn "secret"
        on { baseUrl } doReturn "https://cloud.example"
        on { capabilities } doReturn capabilities
    }
    private val userManager: UserManager = mock {
        on { runBlocking { getUserWithId(ACCOUNT_ID) } } doReturn user
    }
    private val ncApi: NcApi = mock()
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
        // The first "unavailable" answer came 25 hours ago, and the upload did not move on since.
        UploadWorkspace(workspaceDir(worker.id)).unavailableSince(now() - HOURS_25, UPLOADED_PROGRESS)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(SendStatus.FAILED, rows[REFERENCE_ID]?.sendStatus)
        assertFalse("the workspace is removed", workspaceDir(worker.id).exists())
    }

    @Test
    fun `progress of the upload starts the 24 hours again`() {
        val worker = worker()
        uploadedAndRefusedByServer(503)
        // The old answer was one step before the end of the upload; now the file is uploaded.
        UploadWorkspace(workspaceDir(worker.id)).unavailableSince(now() - HOURS_25, UPLOADED_PROGRESS - 1)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
        assertEquals(SendStatus.PENDING, rows[REFERENCE_ID]?.sendStatus)
    }

    /** The file is uploaded already and the server refuses the share with [code]. */
    private fun uploadedAndRefusedByServer(code: Int) {
        whenever(ncApi.createRemoteShare(anyOrNull(), any(), any(), any(), any(), anyOrNull(), any()))
            .thenReturn(Observable.error<GenericOverall>(HttpException(Response.error<Any>(code, "".toResponseBody()))))
    }

    private fun worker(): UploadAndShareFilesWorker {
        rows[REFERENCE_ID] = placeholder()
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
                    it.ncApiCoroutines = mock<NcApiCoroutines>()
                    it.okHttpClient = OkHttpClient()
                }
        }
        val input = Data.Builder()
            .putLong(KEY_INTERNAL_USER_ID, ACCOUNT_ID)
            .putString("DEVICE_SOURCE_FILE", "content://media/photo.jpg")
            .putString("ROOM_TOKEN", "room")
            .putString("CONVERSATION_NAME", "Room")
            .putString(UploadAndShareFilesWorker.KEY_REFERENCE_ID, REFERENCE_ID)
            .putString(UploadAndShareFilesWorker.KEY_INTERNAL_CONVERSATION_ID, CONVERSATION_ID)
            .build()
        val worker = TestListenableWorkerBuilder<UploadAndShareFilesWorker>(contextWithStrings())
            .setInputData(input)
            .setWorkerFactory(factory)
            .build()
        UploadWorkspace(workspaceDir(worker.id)).markUploaded("/Talk/photo.jpg", "photo.jpg")
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
        private const val UPLOADED_PROGRESS = 101
    }
}
