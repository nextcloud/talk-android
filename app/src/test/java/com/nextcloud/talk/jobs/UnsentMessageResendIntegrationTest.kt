/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.jobs

import android.app.Application
import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.nextcloud.talk.chat.data.network.ChatMessageSyncer
import com.nextcloud.talk.chat.data.network.ChatNetworkDataSource
import com.nextcloud.talk.chat.data.network.OfflineFirstChatRepository
import com.nextcloud.talk.chat.viewmodels.MessageInputViewModel
import com.nextcloud.talk.conversationlist.data.network.ConversationListUpdater
import com.nextcloud.talk.data.database.dao.ChatMessagesDao
import com.nextcloud.talk.data.database.mappers.asEntity
import com.nextcloud.talk.data.database.model.ChatMessageEntity
import com.nextcloud.talk.data.database.model.SendStatus
import com.nextcloud.talk.data.network.NetworkMonitor
import com.nextcloud.talk.data.source.local.TalkDatabase
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.data.user.model.UserEntity
import com.nextcloud.talk.logger.Logger
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.capabilities.CapabilitiesDto
import com.nextcloud.talk.models.json.capabilities.SpreedCapabilityDto
import com.nextcloud.talk.models.json.chat.ChatOverallSingleMessage
import com.nextcloud.talk.models.json.conversations.ConversationDto
import com.nextcloud.talk.users.UserManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mockingDetails
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The reconnect path ([MessageInputViewModel.sendUnsentMessages]) and the send work queued when a
 * message was written ([MessageInputViewModel.sendChatMessage]) both run when the connection comes
 * back. Every unsent message has to reach the server exactly once. Uses a Room database, the real
 * repository and [SendMessageWorker] run by a test WorkManager; only the network is mocked.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions")
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class UnsentMessageResendIntegrationTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val network: ChatNetworkDataSource = mock()
    private val userManager: UserManager = mock()

    private lateinit var db: TalkDatabase
    private lateinit var dao: ChatMessagesDao
    private lateinit var repository: OfflineFirstChatRepository
    private lateinit var viewModel: MessageInputViewModel
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(context(), TalkDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.chatMessagesDao()
        runBlocking {
            db.usersDao().saveUser(UserEntity(id = ACCOUNT_ID, userId = "me", username = "me", baseUrl = BASE_URL))
            db.conversationsDao().insertConversation(ConversationDto(token = ROOM_TOKEN).asEntity(ACCOUNT_ID))
        }

        val networkMonitor: NetworkMonitor = mock()
        whenever(networkMonitor.isOnline).thenReturn(MutableStateFlow(true))
        val updater = ConversationListUpdater(dao, db.chatBlocksDao(), db.conversationsDao())
        repository = OfflineFirstChatRepository(
            mock<Logger>(),
            dao,
            db.chatBlocksDao(),
            mock(),
            networkMonitor,
            ChatMessageSyncer(dao, db.chatBlocksDao(), mock(), networkMonitor, updater),
            updater
        )
        val user = user()
        repository.initData(user, "credentials", "$BASE_URL/chat", ROOM_TOKEN, null)
        repository.updateConversation(
            ConversationModel.mapToConversationModel(ConversationDto(token = ROOM_TOKEN), user)
        )
        viewModel = MessageInputViewModel().apply { setData(repository) }

        wheneverBlocking { userManager.getUserWithId(ACCOUNT_ID) }.thenReturn(user)
        wheneverBlocking { network.sendChatMessage(any(), any(), any(), any(), any(), any(), any(), anyOrNull()) }
            .thenReturn(ChatOverallSingleMessage())
        workManager = initWorkManager()
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `a message written offline is posted once when the connection comes back`() {
        viewModel.sendChatMessage(ACCOUNT_ID, ROOM_TOKEN, "Offline message", "Me", 0, false, null)
        awaitQueued(1)
        val referenceId = singleRow().referenceId!!

        viewModel.sendUnsentMessages(ACCOUNT_ID, ROOM_TOKEN)
        awaitQueued(2)
        runQueue()

        verifyPosted(referenceId, times = 1)
        assertEquals(SendStatus.SENT_PENDING_ACK, singleRow().sendStatus)
    }

    @Test
    fun `a failed message is posted again when the connection comes back`() {
        val referenceId = addRow("Failed message", SendStatus.FAILED)

        viewModel.sendUnsentMessages(ACCOUNT_ID, ROOM_TOKEN)
        awaitQueued(1)
        runQueue()

        verifyPosted(referenceId, times = 1)
        assertEquals(SendStatus.SENT_PENDING_ACK, singleRow().sendStatus)
    }

    @Test
    fun `a pending message whose send work was lost is posted when the connection comes back`() {
        val referenceId = addRow("Orphaned message", SendStatus.PENDING)

        viewModel.sendUnsentMessages(ACCOUNT_ID, ROOM_TOKEN)
        awaitQueued(1)
        runQueue()

        verifyPosted(referenceId, times = 1)
    }

    @Test
    fun `an edited pending message is posted once with its edited text`() {
        viewModel.sendChatMessage(ACCOUNT_ID, ROOM_TOKEN, "before", "Me", 0, false, null)
        awaitQueued(1)
        val row = singleRow()
        dao.updateMessageText(CONVERSATION_ID, row.id, "after")

        viewModel.sendUnsentMessages(ACCOUNT_ID, ROOM_TOKEN)
        awaitQueued(2)
        runQueue()

        runBlocking {
            verify(network, times(1)).sendChatMessage(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                anyOrNull()
            )
            verify(network).sendChatMessage(
                any(),
                any(),
                eq("after"),
                any(),
                any(),
                any(),
                eq(row.referenceId!!),
                anyOrNull()
            )
        }
    }

    @Test
    fun `a sent message waiting for the server copy is not posted again`() {
        val referenceId = addRow("Sent message", SendStatus.SENT_PENDING_ACK)

        viewModel.sendUnsentMessages(ACCOUNT_ID, ROOM_TOKEN)
        runQueueAfterReconnect()

        // only the request of the message sent after the reconnect
        assertEquals(1, queue().size)
        runBlocking {
            verify(network, never()).sendChatMessage(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                eq(referenceId),
                anyOrNull()
            )
        }
    }

    @Test
    fun `unsent messages are posted in the order they were written`() {
        val first = addRow("first", SendStatus.FAILED, timestamp = 1L)
        val second = addRow("second", SendStatus.PENDING, timestamp = 2L)
        val third = addRow("third", SendStatus.FAILED, timestamp = 3L)

        viewModel.sendUnsentMessages(ACCOUNT_ID, ROOM_TOKEN)
        awaitQueued(3)
        runQueue()

        val posted = argumentCaptor<String>()
        runBlocking {
            verify(network, times(3)).sendChatMessage(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                posted.capture(),
                anyOrNull()
            )
        }
        assertEquals(listOf(first, second, third), posted.allValues)
    }

    @Test
    fun `unsent messages written in the same second are posted in the order they were written`() {
        // ids as createChatMessageEntity sets them: a later message gets a larger (still negative) id
        val first = addRow("first", SendStatus.FAILED, timestamp = 5L, id = -1_001L)
        val second = addRow("second", SendStatus.FAILED, timestamp = 5L, id = -1_000L)

        viewModel.sendUnsentMessages(ACCOUNT_ID, ROOM_TOKEN)
        awaitQueued(2)
        runQueue()

        val posted = argumentCaptor<String>()
        runBlocking {
            verify(network, times(2)).sendChatMessage(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                posted.capture(),
                anyOrNull()
            )
        }
        assertEquals(listOf(first, second), posted.allValues)
    }

    /**
     * Runs the send queue until everything queued by a reconnect started before has been handled.
     * sendUnsentMessages and sendChatMessage share a fair lock, so a message sent afterwards is queued
     * behind all of the reconnect's requests; once it is posted, they have all run.
     */
    private fun runQueueAfterReconnect() {
        viewModel.sendChatMessage(ACCOUNT_ID, ROOM_TOKEN, BARRIER_MESSAGE, "Me", 0, false, null)
        val deadline = System.currentTimeMillis() + WAIT_MILLIS
        while (mockingDetails(network).invocations.none { it.arguments.getOrNull(2) == BARRIER_MESSAGE }) {
            check(System.currentTimeMillis() < deadline) { "message sent after the reconnect was not posted" }
            runQueue()
            Thread.sleep(POLL_MILLIS)
        }
    }

    private fun addRow(text: String, status: SendStatus, timestamp: Long? = null, id: Long? = null): String {
        val referenceId = "ref-$text"
        // temporary rows are keyed by the millisecond they were created in
        Thread.sleep(CREATION_GAP_MILLIS)
        runBlocking {
            repository.addTemporaryMessage(text, "Me", 0, false, referenceId).first()
        }
        dao.updateTempMessageSendStatus(CONVERSATION_ID, referenceId, status)
        val row = rowFor(referenceId)
        dao.updateChatMessage(row.copy(timestamp = timestamp ?: row.timestamp, id = id ?: row.id))
        return referenceId
    }

    private fun rowFor(referenceId: String): ChatMessageEntity =
        runBlocking { dao.getTempMessageForConversation(CONVERSATION_ID, referenceId, null).first()!! }

    private fun singleRow(): ChatMessageEntity =
        runBlocking { dao.getTempMessagesForConversation(CONVERSATION_ID).first().single() }

    private fun verifyPosted(referenceId: String, times: Int) {
        runBlocking {
            verify(network, times(times)).sendChatMessage(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                anyOrNull()
            )
            verify(network, times(times)).sendChatMessage(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                eq(referenceId),
                anyOrNull()
            )
        }
    }

    private fun queue(): List<WorkInfo> =
        workManager.getWorkInfosForUniqueWork("send_message_queue_$CONVERSATION_ID").get()

    private fun awaitQueued(count: Int) {
        val deadline = System.currentTimeMillis() + WAIT_MILLIS
        while (queue().size < count) {
            check(System.currentTimeMillis() < deadline) { "expected $count queued requests, got ${queue().size}" }
            Thread.sleep(POLL_MILLIS)
        }
    }

    // The network constraint stands in for the connection coming back: until it is granted, nothing
    // in the queue runs.
    private fun runQueue() {
        val deadline = System.currentTimeMillis() + WAIT_MILLIS
        val driver = WorkManagerTestInitHelper.getTestDriver(context())!!
        while (queue().any { !it.state.isFinished }) {
            queue().filter { it.state == WorkInfo.State.ENQUEUED }.forEach { driver.setAllConstraintsMet(it.id) }
            check(System.currentTimeMillis() < deadline) { "send queue did not finish" }
            Thread.sleep(POLL_MILLIS)
        }
    }

    private fun initWorkManager(): WorkManager {
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker =
                SendMessageWorker(appContext, workerParameters).also {
                    it.userManager = userManager
                    it.chatNetworkDataSource = network
                    it.chatDao = dao
                }
        }
        val configuration = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .setWorkerFactory(factory)
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context(), configuration)
        return WorkManager.getInstance(context())
    }

    private fun user(): User =
        User(
            id = ACCOUNT_ID,
            userId = "me",
            username = "me",
            displayName = "Me",
            token = "token",
            baseUrl = BASE_URL,
            capabilities = CapabilitiesDto(
                null,
                SpreedCapabilityDto(listOf("chat-v2"), null, ""),
                null,
                null,
                null,
                null,
                null,
                null
            )
        )

    private fun context(): Context = ApplicationProvider.getApplicationContext<Application>()

    companion object {
        private const val ACCOUNT_ID = 1L
        private const val BASE_URL = "https://server.example.com"
        private const val ROOM_TOKEN = "room"
        private const val CONVERSATION_ID = "1@room"
        private const val WAIT_MILLIS = 30_000L
        private const val POLL_MILLIS = 20L
        private const val BARRIER_MESSAGE = "sent after the reconnect"
        private const val CREATION_GAP_MILLIS = 2L
    }
}
