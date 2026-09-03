/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Andy Scherzinger <andy.scherzinger@nextcloud.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationlist.data.network

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.chat.data.network.ChatMessageSyncer
import com.nextcloud.talk.chat.data.network.ChatNetworkDataSource
import com.nextcloud.talk.data.network.NetworkMonitor
import com.nextcloud.talk.data.source.local.TalkDatabase
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.data.user.model.UserEntity
import com.nextcloud.talk.models.json.capabilities.Capabilities
import com.nextcloud.talk.models.json.capabilities.SpreedCapability
import com.nextcloud.talk.models.json.chat.ChatMessageJson
import com.nextcloud.talk.models.json.chat.ChatOCS
import com.nextcloud.talk.models.json.chat.ChatOverall
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.utils.ApiUtils
import io.reactivex.Observable
import io.reactivex.android.plugins.RxAndroidPlugins
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response

/**
 * Integration test for the room list message prefetch: a room list sync with unread rooms must
 * leave the chat messages of those rooms in the local database, covered by a contiguous chat
 * block reaching the conversation's last message — so opening the chat needs no network request.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class RoomListMessagePrefetchIntegrationTest {

    private lateinit var db: TalkDatabase
    private lateinit var syncer: ChatMessageSyncer
    private lateinit var repository: OfflineFirstConversationsRepository

    private val conversationsNetwork: ConversationsNetworkDataSource = mock()
    private val chatNetwork: ChatNetworkDataSource = mock()
    private val networkMonitor: NetworkMonitor = mock()

    @Before
    fun setUp() {
        RxAndroidPlugins.setInitMainThreadSchedulerHandler { Schedulers.trampoline() }
        RxAndroidPlugins.setMainThreadSchedulerHandler { Schedulers.trampoline() }

        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, TalkDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        runBlocking {
            db.usersDao().saveUser(UserEntity(id = ACCOUNT_ID, userId = "me", username = "me", baseUrl = BASE_URL))
        }

        whenever(networkMonitor.isOnline).thenReturn(MutableStateFlow(true))

        val conversationListUpdater =
            ConversationListUpdater(db.chatMessagesDao(), db.chatBlocksDao(), db.conversationsDao())
        syncer = ChatMessageSyncer(
            db.chatMessagesDao(),
            db.chatBlocksDao(),
            chatNetwork,
            networkMonitor,
            conversationListUpdater
        )
        repository = OfflineFirstConversationsRepository(
            db.conversationsDao(),
            conversationsNetwork,
            chatNetwork,
            networkMonitor,
            syncer,
            conversationListUpdater,
            context
        )
    }

    @After
    fun tearDown() {
        db.close()
        RxAndroidPlugins.reset()
    }

    @Test
    fun `room list sync prefetches unread messages into contiguous chat blocks`() {
        val rooms = listOf(
            conversation(ROOM_A, unreadMessages = 2, lastMessageId = 12),
            conversation(ROOM_B, unreadMessages = 1, lastMessageId = 7)
        )
        whenever(conversationsNetwork.getRooms(any(), any(), any())).thenReturn(Observable.just(rooms))
        wheneverBlocking { chatNetwork.pullChatMessages(any(), eq(chatUrl(ROOM_A)), any()) }
            .thenReturn(Response.success(overall(message(10, ROOM_A), message(11, ROOM_A), message(12, ROOM_A))))
        wheneverBlocking { chatNetwork.pullChatMessages(any(), eq(chatUrl(ROOM_B)), any()) }
            .thenReturn(Response.success(overall(message(6, ROOM_B), message(7, ROOM_B))))

        runBlocking {
            repository.getRooms(user()).join()

            // the message catch-up runs as a fire-and-forget coroutine after the room list sync
            awaitUntil {
                newestBlockMessageId(ROOM_A) == 12L && newestBlockMessageId(ROOM_B) == 7L
            }

            assertMessagesUpToLastMessage(ROOM_A, oldestFetched = 10L, lastMessageId = 12L)
            assertMessagesUpToLastMessage(ROOM_B, oldestFetched = 6L, lastMessageId = 7L)
        }
    }

    private suspend fun assertMessagesUpToLastMessage(roomToken: String, oldestFetched: Long, lastMessageId: Long) {
        val internalConversationId = "$ACCOUNT_ID@$roomToken"

        val newestCachedMessage = db.chatMessagesDao().getNewestMessageIdInRange(
            internalConversationId = internalConversationId,
            threadId = null,
            oldestMessageId = 0,
            newestMessageId = Long.MAX_VALUE
        )
        assertEquals("newest cached message must reach lastMessage.id", lastMessageId, newestCachedMessage)

        val blocks = db.chatBlocksDao().getChatBlocksForConversation(internalConversationId)
        assertEquals("exactly one contiguous chat block expected", 1, blocks.size)
        assertEquals(oldestFetched, blocks[0].oldestMessageId)
        assertEquals(lastMessageId, blocks[0].newestMessageId)
    }

    private fun newestBlockMessageId(roomToken: String): Long =
        db.chatBlocksDao().getNewestMessageIdFromChatBlocks("$ACCOUNT_ID@$roomToken", null)

    private suspend fun awaitUntil(timeoutMillis: Long = TIMEOUT_MILLIS, condition: () -> Boolean) {
        val start = System.currentTimeMillis()
        while (!condition()) {
            if (System.currentTimeMillis() - start > timeoutMillis) {
                throw AssertionError("Condition not met within $timeoutMillis ms")
            }
            delay(POLL_INTERVAL_MILLIS)
        }
    }

    private fun user(): User =
        User(
            id = ACCOUNT_ID,
            userId = "me",
            username = "me",
            baseUrl = BASE_URL,
            token = "app-password",
            capabilities = Capabilities().apply {
                spreedCapability = SpreedCapability().apply { features = listOf("chat-keep-notifications") }
            }
        )

    private fun conversation(roomToken: String, unreadMessages: Int, lastMessageId: Long): Conversation =
        Conversation(
            token = roomToken,
            lastActivity = lastMessageId,
            unreadMessages = unreadMessages,
            lastMessage = message(lastMessageId, roomToken)
        )

    private fun message(id: Long, roomToken: String): ChatMessageJson =
        ChatMessageJson(
            id = id,
            token = roomToken,
            actorType = "users",
            actorId = "other",
            actorDisplayName = "Other User",
            timestamp = id,
            message = "message $id",
            messageType = "comment",
            systemMessageType = ChatMessage.SystemMessageType.DUMMY
        )

    private fun overall(vararg messages: ChatMessageJson): ChatOverall =
        ChatOverall(ocs = ChatOCS(meta = null, data = messages.toList()))

    private fun chatUrl(roomToken: String): String = ApiUtils.getUrlForChat(1, BASE_URL, roomToken)

    companion object {
        private const val ACCOUNT_ID = 1L
        private const val BASE_URL = "https://server.example.com"
        private const val ROOM_A = "roomA"
        private const val ROOM_B = "roomB"
        private const val TIMEOUT_MILLIS = 10_000L
        private const val POLL_INTERVAL_MILLIS = 50L
    }
}
