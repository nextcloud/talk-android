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
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.nextcloud.talk.data.database.dao.ChatMessagesDao
import com.nextcloud.talk.data.database.model.SendStatus
import com.nextcloud.talk.users.UserManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * A failed upload is recorded on its placeholder row and must not take the uploads queued behind it
 * down with it: WorkManager fails every request chained behind a failed one without running it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class UploadAndShareFilesWorkerTest {

    private val userManager: UserManager = mock()
    private val chatDao: ChatMessagesDao = mock()

    @Test
    fun `a failed upload does not strand the upload queued behind it`() {
        val workManager = initWorkManager()
        // the account is unknown, so every upload fails before it reads its file
        val first = upload("ref-a")
        val second = upload("ref-b")

        awaitFinished(workManager, first)
        awaitFinished(workManager, second)

        assertEquals(WorkInfo.State.SUCCEEDED, workManager.getWorkInfoById(first).get()!!.state)
        assertEquals(WorkInfo.State.SUCCEEDED, workManager.getWorkInfoById(second).get()!!.state)
        verify(chatDao).updateTempMessageSendStatus(CONVERSATION_ID, "ref-a", SendStatus.FAILED)
        verify(chatDao).updateTempMessageSendStatus(CONVERSATION_ID, "ref-b", SendStatus.FAILED)
    }

    private fun upload(referenceId: String): UUID =
        UploadAndShareFilesWorker.upload(
            userId = ACCOUNT_ID,
            fileUri = "content://media/$referenceId",
            roomToken = "room",
            conversationName = "Room",
            metaData = null,
            referenceId = referenceId,
            internalConversationId = CONVERSATION_ID
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
            .setExecutor(SynchronousExecutor())
            .setWorkerFactory(factory)
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context(), configuration)
        return WorkManager.getInstance(context())
    }

    private fun awaitFinished(workManager: WorkManager, id: UUID) {
        val deadline = System.currentTimeMillis() + WAIT_MILLIS
        val driver = WorkManagerTestInitHelper.getTestDriver(context())!!
        while (!workManager.getWorkInfoById(id).get()!!.state.isFinished) {
            // A request only becomes ENQUEUED once the one ahead of it is done. The network
            // constraint is granted to every request of the queue that is waiting for it.
            workManager.getWorkInfosForUniqueWork("upload_queue_$CONVERSATION_ID").get()
                .filter { it.state == WorkInfo.State.ENQUEUED }
                .forEach { driver.setAllConstraintsMet(it.id) }
            check(System.currentTimeMillis() < deadline) { "work $id did not finish" }
            Thread.sleep(POLL_MILLIS)
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
