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
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.chat.data.network.ChatNetworkDataSource
import com.nextcloud.talk.data.database.dao.ChatMessagesDao
import com.nextcloud.talk.data.database.model.ChatMessageEntity
import com.nextcloud.talk.data.database.model.SendStatus
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.capabilities.CapabilitiesDto
import com.nextcloud.talk.models.json.capabilities.SpreedCapabilityDto
import com.nextcloud.talk.models.json.chat.ChatOverallSingleMessage
import com.nextcloud.talk.users.UserManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * Tests for [SendMessageWorker]: a message that cannot be sent must end up as FAILED (so the
 * user can resend or delete it) and must not take the messages queued behind it down with it.
 */
@Suppress("TooManyFunctions")
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class SendMessageWorkerTest {

    private val userManager: UserManager = mock()
    private val network: ChatNetworkDataSource = mock()
    private val chatDao: ChatMessagesDao = mock()
    private val rows = mutableMapOf<String, ChatMessageEntity>()

    @Before
    fun setUp() {
        wheneverBlocking { userManager.getUserWithId(ACCOUNT_ID) }.thenReturn(user())
        whenever(chatDao.getTempMessageForConversation(eq(CONVERSATION_ID), any(), anyOrNull()))
            .thenAnswer { flowOf(rows[it.getArgument<String>(1)]) }
        whenever(chatDao.updateTempMessageSendStatus(any(), any(), any())).thenAnswer {
            rows[it.getArgument<String>(1)]?.sendStatus = it.getArgument<SendStatus>(2)
            1
        }
    }

    @Test
    fun `a message that fails does not strand the message queued behind it`() {
        val workManager = initWorkManager()
        val first = addRow("ref-a", "A")
        val second = addRow("ref-b", "B")
        wheneverBlocking { network.sendChatMessage(any(), any(), eq("A"), any(), any(), any(), any(), anyOrNull()) }
            .thenThrow(IllegalStateException("server rejected the message"))
        wheneverBlocking { network.sendChatMessage(any(), any(), eq("B"), any(), any(), any(), any(), anyOrNull()) }
            .thenReturn(ChatOverallSingleMessage())

        // both are queued while the first one is still waiting for the network, so the second is
        // appended behind a request that has not run yet
        SendMessageWorker.enqueue(CONVERSATION_ID, "ref-a", null)
        SendMessageWorker.enqueue(CONVERSATION_ID, "ref-b", null)
        val (firstId, secondId) = queuedIds(workManager)
        awaitFinished(workManager, firstId)
        awaitFinished(workManager, secondId)

        assertEquals(SendStatus.FAILED, first.sendStatus)
        assertEquals(WorkInfo.State.SUCCEEDED, workManager.getWorkInfoById(secondId).get()!!.state)
        assertEquals(SendStatus.SENT_PENDING_ACK, second.sendStatus)
    }

    @Test
    fun `an unexpected error while preparing the send marks the message failed`() {
        val row = addRow("ref-a", "A")
        wheneverBlocking { userManager.getUserWithId(ACCOUNT_ID) }.thenReturn(user(capabilities = null))

        val result = runBlocking { worker("ref-a").doWork() }

        assertEquals(SendStatus.FAILED, row.sendStatus)
        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun `a failure to record the failure still finishes with success`() {
        addRow("ref-a", "A")
        wheneverBlocking { userManager.getUserWithId(ACCOUNT_ID) }.thenReturn(user(capabilities = null))
        doThrow(IllegalStateException("database is closed")).whenever(chatDao)
            .updateTempMessageSendStatus(any(), any(), any())

        val result = runBlocking { worker("ref-a").doWork() }

        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun `a stopped run leaves the message pending for the rescheduled run`() {
        val row = addRow("ref-a", "A")
        wheneverBlocking { network.sendChatMessage(any(), any(), any(), any(), any(), any(), any(), anyOrNull()) }
            .thenThrow(CancellationException("run stopped"))

        assertThrows(CancellationException::class.java) { runBlocking { worker("ref-a").doWork() } }

        assertEquals(SendStatus.PENDING, row.sendStatus)
    }

    @Test
    fun `missing input data does not fail the messages queued behind`() {
        val result = runBlocking { worker(null).doWork() }

        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun `a conversation has an active send only while its work is waiting or running`() {
        val workManager = initWorkManager()
        addRow("ref-a", "A")
        wheneverBlocking { network.sendChatMessage(any(), any(), any(), any(), any(), any(), any(), anyOrNull()) }
            .thenReturn(ChatOverallSingleMessage())

        assertFalse(hasActiveSend(workManager))

        SendMessageWorker.enqueue(CONVERSATION_ID, "ref-a", null)
        val (id) = queuedIds(workManager)
        assertTrue(hasActiveSend(workManager))

        awaitFinished(workManager, id)
        assertFalse(hasActiveSend(workManager))
    }

    private fun hasActiveSend(workManager: WorkManager): Boolean =
        runBlocking { SendMessageWorker.hasActiveSend(workManager, CONVERSATION_ID) }

    private fun addRow(referenceId: String, text: String): ChatMessageEntity =
        ChatMessageEntity(
            internalId = "$CONVERSATION_ID@_temp_$referenceId",
            accountId = ACCOUNT_ID,
            token = CONVERSATION_TOKEN,
            internalConversationId = CONVERSATION_ID,
            actorDisplayName = "Me",
            message = text,
            actorId = "me",
            actorType = "users",
            messageType = "comment",
            systemMessageType = ChatMessage.SystemMessageType.DUMMY,
            referenceId = referenceId,
            isTemporary = true,
            sendStatus = SendStatus.PENDING
        ).also { rows[referenceId] = it }

    private fun user(capabilities: CapabilitiesDto? = capabilities()): User =
        User(
            id = ACCOUNT_ID,
            userId = "me",
            username = "me",
            token = "token",
            baseUrl = "https://server.example.com",
            capabilities = capabilities
        )

    private fun capabilities() =
        CapabilitiesDto(
            null,
            SpreedCapabilityDto(listOf("chat-v2"), null, ""),
            null,
            null,
            null,
            null,
            null,
            null
        )

    private fun worker(referenceId: String?): SendMessageWorker {
        val input = Data.Builder()
            .putString("INTERNAL_CONVERSATION_ID", CONVERSATION_ID)
            .putString("REFERENCE_ID", referenceId)
            .build()
        return TestListenableWorkerBuilder<SendMessageWorker>(context())
            .setInputData(input)
            .build()
            .also(::inject)
    }

    private fun inject(worker: SendMessageWorker) {
        worker.userManager = userManager
        worker.chatNetworkDataSource = network
        worker.chatDao = chatDao
    }

    private fun initWorkManager(): WorkManager {
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker = SendMessageWorker(appContext, workerParameters).also(::inject)
        }
        val configuration = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .setWorkerFactory(factory)
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context(), configuration)
        return WorkManager.getInstance(context())
    }

    // The unique-work query has no defined order. Right after the enqueues the request at the head of
    // the chain is the only one that is not BLOCKED, so it goes first.
    private fun queuedIds(workManager: WorkManager): List<UUID> =
        workManager.getWorkInfosForUniqueWork("send_message_queue_$CONVERSATION_ID").get()
            .sortedBy { it.state == WorkInfo.State.BLOCKED }
            .map { it.id }

    private fun awaitFinished(workManager: WorkManager, id: UUID) {
        val deadline = System.currentTimeMillis() + WAIT_MILLIS
        val driver = WorkManagerTestInitHelper.getTestDriver(context())!!
        while (!workManager.getWorkInfoById(id).get()!!.state.isFinished) {
            // A request only becomes ENQUEUED once the one ahead of it is done. The network
            // constraint is granted to every request that is waiting for it.
            queuedIds(workManager)
                .filter { workManager.getWorkInfoById(it).get()!!.state == WorkInfo.State.ENQUEUED }
                .forEach(driver::setAllConstraintsMet)
            check(System.currentTimeMillis() < deadline) { "work $id did not finish" }
            Thread.sleep(POLL_MILLIS)
        }
    }

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    companion object {
        private const val ACCOUNT_ID = 1L
        private const val CONVERSATION_TOKEN = "room"
        private const val CONVERSATION_ID = "1@room"
        private const val WAIT_MILLIS = 30_000L
        private const val POLL_MILLIS = 20L
    }
}
