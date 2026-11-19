/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.data.network

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.conversationlist.data.network.ConversationListUpdater
import com.nextcloud.talk.data.database.dao.ChatMessagesDao
import com.nextcloud.talk.data.database.mappers.asEntity
import com.nextcloud.talk.data.database.model.ChatMessageEntity
import com.nextcloud.talk.data.network.NetworkMonitor
import com.nextcloud.talk.data.source.local.TalkDatabase
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.data.user.model.UserEntity
import com.nextcloud.talk.models.json.chat.ChatMessageDto
import com.nextcloud.talk.models.json.conversations.ConversationDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The server omits the thread data on some copies of a thread's root message: the chat relay sends a new
 * thread's root before the thread exists, and sends the parent of an edit or delete without it. The thread data
 * is taken from the other messages of the thread, without a request.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class ThreadRootDataIntegrationTest {

    private lateinit var db: TalkDatabase
    private lateinit var dao: ChatMessagesDao
    private lateinit var syncer: ChatMessageSyncer
    private val network: ChatNetworkDataSource = mock()
    private val target = ChatMessageSyncer.SyncTarget(
        user = User(id = ACCOUNT_ID, userId = "me", username = "me", baseUrl = BASE_URL),
        roomToken = ROOM_TOKEN,
        threadId = null,
        credentials = CREDENTIALS,
        urlForChatting = "$BASE_URL/chat"
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, TalkDatabase::class.java).allowMainThreadQueries().build()
        dao = db.chatMessagesDao()
        runBlocking {
            db.usersDao().saveUser(UserEntity(id = ACCOUNT_ID, userId = "me", username = "me", baseUrl = BASE_URL))
            db.conversationsDao().insertConversation(ConversationDto(token = ROOM_TOKEN).asEntity(ACCOUNT_ID))
        }
        val networkMonitor: NetworkMonitor = mock()
        whenever(networkMonitor.isOnline).thenReturn(MutableStateFlow(true))
        val updater = ConversationListUpdater(dao, db.chatBlocksDao(), db.conversationsDao())
        syncer = ChatMessageSyncer(dao, db.chatBlocksDao(), network, networkMonitor, updater)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `a root relayed before its thread existed is marked as thread root once the thread is created`() =
        runBlocking {
            persist(root(withThreadData = false))

            persist(threadCreated(replies = REPLIES))

            val root = storedRoot()
            assertTrue(root.isThread)
            assertEquals(ROOT_ID, root.threadId)
            assertEquals(TITLE, root.threadTitle)
            assertEquals(REPLIES, root.threadReplies)
            verifyNoInteractions(network)
        }

    @Test
    fun `a root stored without thread id is marked as root of its own thread`() =
        runBlocking {
            persist(root(withThreadData = false).copy(threadId = null))

            persist(threadCreated())

            val root = storedRoot()
            assertTrue(root.isThread)
            assertEquals(ROOT_ID, root.threadId)
        }

    @Test
    fun `a root and its thread created message in the same batch mark the root`() =
        runBlocking {
            persist(root(withThreadData = false), threadCreated())

            assertTrue(storedRoot().isThread)
        }

    @Test
    fun `an older thread message does not replace the thread data of a marked root`() =
        runBlocking {
            persist(root(withThreadData = true, replies = REPLIES).copy(threadTitle = "Renamed"))

            persist(threadCreated())

            val root = storedRoot()
            assertEquals("Renamed", root.threadTitle)
            assertEquals(REPLIES, root.threadReplies)
        }

    @Test
    fun `a copy of the root without thread data keeps the stored thread data`() =
        runBlocking {
            persist(root(withThreadData = true, replies = REPLIES))

            persist(root(withThreadData = false))

            val root = storedRoot()
            assertTrue(root.isThread)
            assertEquals(TITLE, root.threadTitle)
            assertEquals(REPLIES, root.threadReplies)
        }

    @Test
    fun `the parent of a relayed edit keeps the stored thread data of the root`() =
        runBlocking {
            persist(root(withThreadData = true, replies = REPLIES))

            persist(editOf(root(withThreadData = false).copy(message = "edited")))

            val root = storedRoot()
            assertEquals("edited", root.message)
            assertTrue(root.isThread)
            assertEquals(TITLE, root.threadTitle)
            assertEquals(REPLIES, root.threadReplies)
        }

    private suspend fun persist(vararg messages: ChatMessageDto) {
        syncer.persistChatMessagesAndHandleSystemMessages(target, messages.toList())
    }

    private suspend fun storedRoot(): ChatMessageEntity = dao.getChatMessageEntity("$ACCOUNT_ID@$ROOM_TOKEN", ROOT_ID)!!

    private fun root(withThreadData: Boolean, replies: Int = 0) =
        ChatMessageDto(
            id = ROOT_ID,
            token = ROOM_TOKEN,
            actorType = "users",
            actorId = "me",
            actorDisplayName = "Me",
            message = "first message",
            messageType = "comment",
            systemMessageType = ChatMessage.SystemMessageType.DUMMY,
            threadId = ROOT_ID,
            hasThread = withThreadData,
            threadTitle = if (withThreadData) TITLE else null,
            threadReplies = if (withThreadData) replies else 0
        )

    private fun threadCreated(replies: Int = 0) =
        ChatMessageDto(
            id = ROOT_ID + 1,
            token = ROOM_TOKEN,
            actorType = "users",
            actorId = "me",
            actorDisplayName = "Me",
            message = "You created thread {title}",
            messageType = "system",
            systemMessageType = ChatMessage.SystemMessageType.THREAD_CREATED,
            threadId = ROOT_ID,
            hasThread = true,
            threadTitle = TITLE,
            threadReplies = replies
        )

    private fun editOf(parent: ChatMessageDto) =
        ChatMessageDto(
            id = ROOT_ID + 2,
            token = ROOM_TOKEN,
            actorType = "users",
            actorId = "me",
            actorDisplayName = "Me",
            message = "You edited a message",
            messageType = "system",
            systemMessageType = ChatMessage.SystemMessageType.MESSAGE_EDITED,
            threadId = ROOT_ID,
            parentMessage = parent
        )

    companion object {
        private const val ACCOUNT_ID = 1L
        private const val ROOM_TOKEN = "room1"
        private const val BASE_URL = "https://server.example.com"
        private const val CREDENTIALS = "credentials"
        private const val ROOT_ID = 100L
        private const val TITLE = "Thread title"
        private const val REPLIES = 4
    }
}
