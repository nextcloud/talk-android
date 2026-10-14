/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.jobs

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.WorkManagerTestInitHelper
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.data.database.dao.ChatMessagesDao
import com.nextcloud.talk.data.database.model.ChatMessageEntity
import com.nextcloud.talk.data.database.model.SendStatus
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.upload.UploadWorkspace
import com.nextcloud.talk.users.UserManager
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * The uploads of a conversation are chained behind each other (APPEND_OR_REPLACE). Cancelling one upload must
 * not cancel or fail the uploads behind it: WorkManager gives every request chained behind a cancelled or failed
 * one the same state without running it, and their placeholder rows would stay PENDING for ever.
 *
 * The works run on a real WorkManager. A request chained behind another one is BLOCKED until the one ahead is
 * done, so the network constraint is granted one request after the other, as in [UploadAndShareFilesWorkerTest].
 * The uploads that are not cancelled are marked as shared, so they finish at once without a server.
 */
@Suppress("TooManyFunctions")
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class UploadCancelCascadeTest {

    private val rows = ConcurrentHashMap<String, ChatMessageEntity>()

    // Not a SynchronousExecutor: the worker looks up other works (deleteFinishedWorkspaces) and waits for the
    // answer, which a worker running on the thread of WorkManager's own task executor would never get.
    private val workerExecutor = Executors.newFixedThreadPool(2)
    private val user: User = mock()
    private val userManager: UserManager = mock {
        on { runBlocking { getUserWithId(ACCOUNT_ID) } } doReturn user
    }
    private val chatDao: ChatMessagesDao = mock {
        on { getTempMessageForConversation(any(), any(), anyOrNull()) } doAnswer {
            flowOf(rows[it.getArgument<String>(1)])
        }
        on { deleteTempChatMessageIfPending(any(), any()) } doAnswer {
            val removed = rows[it.getArgument<String>(1)]?.takeIf { row -> row.sendStatus == SendStatus.PENDING }
            removed?.let { row -> rows.remove(row.referenceId) }
            if (removed == null) 0 else 1
        }
    }

    init {
        // The worker writes only the send status of the temporary row.
        doAnswer {
            val referenceId = it.getArgument<String>(1)
            rows[referenceId]?.let { row -> rows[referenceId] = row.copy(sendStatus = it.getArgument(2)) }
            1
        }.whenever(chatDao).updateTempMessageSendStatus(any(), any(), any())
    }

    @After
    fun tearDown() {
        workerExecutor.shutdownNow()
        setSharedApplication(null)
    }

    @Test
    fun `cancelling the first upload does not cancel the uploads behind it`() {
        val workManager = initWorkManager()
        val (a, b, c) = enqueue("ref-a", "ref-b", "ref-c")

        UploadAndShareFilesWorker.cancelUpload("ref-a", a)
        awaitAllFinished(workManager, a, b, c)

        assertAllSucceeded(workManager, a, b, c)
        assertCancelled("ref-a")
        assertDelivered("ref-b", "ref-c")
    }

    @Test
    fun `cancelling a middle upload leaves the ones before and behind it`() {
        val workManager = initWorkManager()
        val (a, b, c) = enqueue("ref-a", "ref-b", "ref-c")

        UploadAndShareFilesWorker.cancelUpload("ref-b", b)
        awaitAllFinished(workManager, a, b, c)

        assertAllSucceeded(workManager, a, b, c)
        assertCancelled("ref-b")
        assertDelivered("ref-a", "ref-c")
    }

    @Test
    fun `cancelling the last upload leaves the ones ahead of it`() {
        val workManager = initWorkManager()
        val (a, b, c) = enqueue("ref-a", "ref-b", "ref-c")

        UploadAndShareFilesWorker.cancelUpload("ref-c", c)
        awaitAllFinished(workManager, a, b, c)

        assertAllSucceeded(workManager, a, b, c)
        assertCancelled("ref-c")
        assertDelivered("ref-a", "ref-b")
    }

    @Test
    fun `cancelling every upload one after the other finishes them all`() {
        val workManager = initWorkManager()
        val (a, b, c) = enqueue("ref-a", "ref-b", "ref-c")

        UploadAndShareFilesWorker.cancelUpload("ref-a", a)
        UploadAndShareFilesWorker.cancelUpload("ref-b", b)
        UploadAndShareFilesWorker.cancelUpload("ref-c", c)
        awaitAllFinished(workManager, a, b, c)

        assertAllSucceeded(workManager, a, b, c)
        assertCancelled("ref-a", "ref-b", "ref-c")
    }

    @Test
    fun `an upload cancelled by its tag after a restart does not cancel the uploads behind it`() {
        val workManager = initWorkManager()
        val (a, b, c) = enqueue("ref-a", "ref-b", "ref-c")

        // After a restart of the app the id of the work is not known, only the reference id.
        UploadAndShareFilesWorker.cancelUpload("ref-a", null)
        awaitAllFinished(workManager, a, b, c)

        assertAllSucceeded(workManager, a, b, c)
        assertCancelled("ref-a")
        assertDelivered("ref-b", "ref-c")
    }

    @Test
    fun `a cancelled upload whose placeholder cannot be removed does not fail the uploads behind it`() {
        val workManager = initWorkManager()
        whenever(chatDao.deleteTempChatMessageIfPending(any(), any())).thenThrow(IllegalStateException("db closed"))
        val (a, b, c) = enqueue("ref-a", "ref-b", "ref-c")

        UploadAndShareFilesWorker.cancelUpload("ref-a", a)
        awaitAllFinished(workManager, a, b, c)

        assertAllSucceeded(workManager, a, b, c)
        assertDelivered("ref-b", "ref-c")
    }

    private fun assertAllSucceeded(workManager: WorkManager, vararg ids: UUID) {
        assertEquals(
            ids.map { WorkInfo.State.SUCCEEDED },
            ids.map { workManager.getWorkInfoById(it).get()!!.state }
        )
    }

    private fun assertCancelled(vararg referenceIds: String) {
        referenceIds.forEach {
            assertNull("the placeholder of $it is removed", rows[it])
        }
    }

    private fun assertDelivered(vararg referenceIds: String) {
        referenceIds.forEach {
            assertEquals("the placeholder of $it", SendStatus.SENT_PENDING_ACK, rows[it]?.sendStatus)
        }
    }

    /**
     * Enqueues one upload per reference id into the queue of the conversation, in this order. The placeholder rows
     * are PENDING. The uploads are marked as shared; only a cancel changes what a work does.
     */
    private fun enqueue(vararg referenceIds: String): List<UUID> =
        referenceIds.map { referenceId ->
            rows[referenceId] = placeholder(referenceId)
            val id = UploadAndShareFilesWorker.upload(
                userId = ACCOUNT_ID,
                fileUri = "content://media/$referenceId",
                roomToken = "room",
                conversationName = "Room",
                metaData = null,
                referenceId = referenceId,
                internalConversationId = CONVERSATION_ID
            )
            UploadWorkspace(File(File(context().noBackupFilesDir, "uploads"), id.toString())).markShared()
            id
        }

    private fun placeholder(referenceId: String) =
        ChatMessageEntity(
            internalId = "$CONVERSATION_ID@_temp_$referenceId",
            accountId = ACCOUNT_ID,
            token = "room",
            internalConversationId = CONVERSATION_ID,
            actorDisplayName = "Me",
            message = "file",
            actorId = "me",
            actorType = "users",
            messageType = "comment",
            systemMessageType = ChatMessage.SystemMessageType.DUMMY,
            referenceId = referenceId,
            isTemporary = true,
            sendStatus = SendStatus.PENDING
        )

    private fun initWorkManager(): WorkManager {
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker =
                UploadAndShareFilesWorker(appContext, workerParameters).also {
                    it.userManager = userManager
                    it.chatDao = chatDao
                }
        }
        val configuration = Configuration.Builder()
            .setExecutor(workerExecutor)
            .setWorkerFactory(factory)
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context(), configuration)
        // cancelUpload finds the context of the application here.
        setSharedApplication(
            mock {
                on { applicationContext } doReturn context()
            }
        )
        return WorkManager.getInstance(context())
    }

    private fun awaitAllFinished(workManager: WorkManager, vararg ids: UUID) {
        val deadline = System.currentTimeMillis() + WAIT_MILLIS
        val driver = WorkManagerTestInitHelper.getTestDriver(context())!!
        while (ids.any { !workManager.getWorkInfoById(it).get()!!.state.isFinished }) {
            // A request only becomes ENQUEUED once the one ahead of it is done. The network constraint is granted
            // to every request of the queue that is waiting for it.
            workManager.getWorkInfosForUniqueWork("upload_queue_$CONVERSATION_ID").get()
                .filter { it.state == WorkInfo.State.ENQUEUED }
                .forEach { driver.setAllConstraintsMet(it.id) }
            check(System.currentTimeMillis() < deadline) {
                "works did not finish: " + ids.map { workManager.getWorkInfoById(it).get()!!.state }
            }
            Thread.sleep(POLL_MILLIS)
        }
        assertFalse(ids.isEmpty())
    }

    // The setter of sharedApplication is protected, and the real application is not started in this test.
    private fun setSharedApplication(application: NextcloudTalkApplication?) {
        NextcloudTalkApplication::class.java.getDeclaredField("sharedApplication").apply {
            isAccessible = true
            set(null, application)
        }
    }

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    companion object {
        private const val ACCOUNT_ID = 1L
        private const val CONVERSATION_ID = "1@room"
        private const val WAIT_MILLIS = 30_000L
        private const val POLL_MILLIS = 20L
    }
}
