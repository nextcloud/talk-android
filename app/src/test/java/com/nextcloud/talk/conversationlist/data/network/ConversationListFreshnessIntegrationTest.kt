/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationlist.data.network

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bluelinelabs.logansquare.LoganSquare
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.chat.data.network.ChatMessageSyncer
import com.nextcloud.talk.chat.data.network.ChatNetworkDataSource
import com.nextcloud.talk.data.database.mappers.asEntity
import com.nextcloud.talk.data.database.model.ConversationEntity
import com.nextcloud.talk.data.network.NetworkMonitor
import com.nextcloud.talk.data.source.local.TalkDatabase
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.data.user.model.UserEntity
import com.nextcloud.talk.models.domain.ConversationModel
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response

/**
 * Integration tests for the conversation list freshness: background catch-ups and local read
 * state changes must be visible in the conversation entries (and thus the reactive room list)
 * without waiting for the next full room list sync — while a newer stored state must never be
 * regressed by a catch-up.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
@Suppress("TooManyFunctions")
class ConversationListFreshnessIntegrationTest {

    private lateinit var db: TalkDatabase
    private lateinit var syncer: ChatMessageSyncer
    private lateinit var conversationListUpdater: ConversationListUpdater

    private val chatNetwork: ChatNetworkDataSource = mock()
    private val conversationsNetwork: ConversationsNetworkDataSource = mock()
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

        conversationListUpdater =
            ConversationListUpdater(db.chatMessagesDao(), db.chatBlocksDao(), db.conversationsDao())
        syncer = ChatMessageSyncer(
            db.chatMessagesDao(),
            db.chatBlocksDao(),
            chatNetwork,
            networkMonitor,
            conversationListUpdater
        )
    }

    @After
    fun tearDown() {
        db.close()
        RxAndroidPlugins.reset()
    }

    @Test
    fun `background catch-up updates the conversation entry from the fetched messages`() {
        runBlocking {
            seedConversation(lastActivity = 10, lastReadMessage = 10, unreadMessages = 0)
            wheneverBlocking { chatNetwork.pullChatMessages(any(), any(), any()) }
                .thenReturn(
                    Response.success(
                        overall(
                            message(10),
                            message(11),
                            message(12),
                            message(
                                id = 13,
                                messageType = "system",
                                systemMessageType = ChatMessage.SystemMessageType.USER_ADDED
                            )
                        )
                    )
                )

            syncer.catchUpRoom(target(), lastReadMessage = 10, unreadMessages = 2)

            val conversation = conversationEntity()
            assertEquals("activity must advance to the newest message", 13L, conversation.lastActivity)
            assertEquals("system messages must not count as unread", 2, conversation.unreadMessages)
            assertEquals("the read marker must not be touched", 10, conversation.lastReadMessage)
            val lastMessage = LoganSquare.parse(conversation.lastMessage, ChatMessageJson::class.java)
            assertEquals("the newest fetched message must become the preview", 13L, lastMessage.id)
        }
    }

    @Test
    fun `background catch-up does not regress unread while a read marker is pending but not yet committed`() {
        runBlocking {
            // Simulates leaving the chat racing a concurrent catch-up: the read marker is
            // registered as pending, but its own updateReadState write hasn't landed on this row
            // yet by the time the catch-up reads lastReadMessage.
            seedConversation(lastActivity = 10, lastReadMessage = 5, unreadMessages = 3)
            conversationListUpdater.markPendingReadMarker(INTERNAL_CONVERSATION_ID, lastReadMessage = 12)

            wheneverBlocking { chatNetwork.pullChatMessages(any(), any(), any()) }
                .thenReturn(Response.success(overall(message(10), message(11), message(12))))

            syncer.catchUpRoom(target(), lastReadMessage = 5, unreadMessages = 3)

            assertEquals(
                "catch-up must not derive unread below the pending read marker",
                0,
                conversationEntity().unreadMessages
            )
        }
    }

    @Test
    fun `background catch-up still reports a message newer than the pending read marker as unread`() {
        runBlocking {
            seedConversation(lastActivity = 10, lastReadMessage = 5, unreadMessages = 3)
            conversationListUpdater.markPendingReadMarker(INTERNAL_CONVERSATION_ID, lastReadMessage = 12)

            wheneverBlocking { chatNetwork.pullChatMessages(any(), any(), any()) }
                .thenReturn(Response.success(overall(message(10), message(11), message(12), message(13))))

            syncer.catchUpRoom(target(), lastReadMessage = 5, unreadMessages = 3)

            assertEquals(
                "a message newer than the pending marker must still count as unread",
                1,
                conversationEntity().unreadMessages
            )
        }
    }

    @Test
    fun `a single-room refresh cannot revert the read state while its marker is pending`() {
        val user = user(withKeepNotificationsCapability = false)
        val repository = OfflineFirstConversationsRepository(
            db.conversationsDao(),
            conversationsNetwork,
            chatNetwork,
            networkMonitor,
            syncer,
            conversationListUpdater,
            ApplicationProvider.getApplicationContext()
        )
        whenever(chatNetwork.getRoom(any(), any())).thenReturn(
            Observable.just(
                ConversationModel.mapToConversationModel(
                    staleServerRoom(lastReadMessage = 10, unreadMessages = 2),
                    user
                )
            )
        )

        runBlocking {
            seedConversation(lastActivity = 12, lastReadMessage = 10, unreadMessages = 2)
            conversationListUpdater.updateLocalReadState(target(), lastReadMessage = 12)

            repository.getRoom(user, ROOM_TOKEN).join()
            awaitUntil { conversationEntity().lastActivity == 12L }

            assertEquals("stale room refresh must not revert the read marker", 12, conversationEntity().lastReadMessage)
            assertEquals("stale room refresh must not revert the unread count", 0, conversationEntity().unreadMessages)
        }
    }

    @Test
    fun `background catch-up never regresses a newer conversation entry`() {
        runBlocking {
            seedConversation(lastActivity = 20, lastReadMessage = 10, unreadMessages = 5)
            wheneverBlocking { chatNetwork.pullChatMessages(any(), any(), any()) }
                .thenReturn(
                    Response.success(overall(message(10), message(11), message(12)))
                )

            syncer.catchUpRoom(target(), lastReadMessage = 10, unreadMessages = 2)

            val conversation = conversationEntity()
            assertEquals("a newer stored activity must win", 20L, conversation.lastActivity)
            assertEquals("the stored unread count must be kept", 5, conversation.unreadMessages)
        }
    }

    @Test
    fun `local read state write-through updates the conversation entry immediately`() {
        runBlocking {
            seedConversation(lastActivity = 12, lastReadMessage = 10, unreadMessages = 2)
            wheneverBlocking { chatNetwork.pullChatMessages(any(), any(), any()) }
                .thenReturn(
                    Response.success(overall(message(10), message(11), message(12, actorId = "me")))
                )
            syncer.catchUpRoom(target(), lastReadMessage = 10, unreadMessages = 2)

            conversationListUpdater.updateLocalReadState(target(), lastReadMessage = 12)
            assertEquals(12, conversationEntity().lastReadMessage)
            assertEquals("everything below the marker is read", 0, conversationEntity().unreadMessages)

            conversationListUpdater.updateLocalReadState(target(), lastReadMessage = 10)
            assertEquals(10, conversationEntity().lastReadMessage)
            assertEquals("own messages must not count as unread", 1, conversationEntity().unreadMessages)
        }
    }

    @Test
    fun `a sync cannot revert the read state while its marker is pending`() {
        val user = user(withKeepNotificationsCapability = false)
        val repository = OfflineFirstConversationsRepository(
            db.conversationsDao(),
            conversationsNetwork,
            chatNetwork,
            networkMonitor,
            syncer,
            conversationListUpdater,
            ApplicationProvider.getApplicationContext()
        )
        whenever(conversationsNetwork.getRooms(any(), any(), any())).thenReturn(
            Observable.just(listOf(staleServerRoom(lastReadMessage = 10, unreadMessages = 2))),
            Observable.just(listOf(staleServerRoom(lastReadMessage = 12, unreadMessages = 0))),
            Observable.just(listOf(staleServerRoom(lastReadMessage = 8, unreadMessages = 4)))
        )

        runBlocking {
            seedConversation(lastActivity = 12, lastReadMessage = 10, unreadMessages = 2)
            conversationListUpdater.updateLocalReadState(target(), lastReadMessage = 12)
            assertEquals(12, conversationEntity().lastReadMessage)
            assertEquals(0, conversationEntity().unreadMessages)

            repository.getRooms(user).join()
            assertEquals("stale sync must not revert the read marker", 12, conversationEntity().lastReadMessage)
            assertEquals("stale sync must not revert the unread count", 0, conversationEntity().unreadMessages)

            repository.getRooms(user).join()
            assertEquals(12, conversationEntity().lastReadMessage)
            assertEquals(0, conversationEntity().unreadMessages)

            repository.getRooms(user).join()
            assertEquals("server authority must be restored", 8, conversationEntity().lastReadMessage)
            assertEquals("marking unread on another device must apply", 4, conversationEntity().unreadMessages)
        }
    }

    @Test
    fun `a sync cannot revert a pending favorite until the server confirms it`() {
        val user = user(withKeepNotificationsCapability = false)
        val repository = repository()
        whenever(conversationsNetwork.getRooms(any(), any(), any())).thenReturn(
            Observable.just(listOf(staleServerRoom(lastReadMessage = 10, unreadMessages = 0))),
            Observable.just(listOf(staleServerRoom(lastReadMessage = 10, unreadMessages = 0, favorite = true))),
            Observable.just(listOf(staleServerRoom(lastReadMessage = 10, unreadMessages = 0)))
        )

        runBlocking {
            seedConversation(lastActivity = 12, lastReadMessage = 10, unreadMessages = 0)
            conversationListUpdater.markPendingFavorite(INTERNAL_CONVERSATION_ID, favorite = true)
            db.conversationsDao().updateConversation(conversationEntity().copy(favorite = true))

            repository.getRooms(user).join()
            assertTrue("stale sync must not revert the favorite", conversationEntity().favorite)

            repository.getRooms(user).join()
            assertTrue("confirming sync must keep the favorite", conversationEntity().favorite)

            repository.getRooms(user).join()
            assertFalse("server authority must be restored", conversationEntity().favorite)
        }
    }

    @Test
    fun `a sync cannot revert a pending mark as unread until the server confirms it`() {
        val user = user(withKeepNotificationsCapability = false)
        val repository = repository()
        whenever(conversationsNetwork.getRooms(any(), any(), any())).thenReturn(
            Observable.just(listOf(staleServerRoom(lastReadMessage = 12, unreadMessages = 0))),
            Observable.just(listOf(staleServerRoom(lastReadMessage = 9, unreadMessages = 3)))
        )

        runBlocking {
            seedConversation(lastActivity = 12, lastReadMessage = 12, unreadMessages = 0)
            conversationListUpdater.markPendingUnread(INTERNAL_CONVERSATION_ID)
            db.conversationsDao().updateConversation(conversationEntity().copy(unreadMessages = 1))

            repository.getRooms(user).join()
            assertEquals("stale sync must not clear the unread state", 1, conversationEntity().unreadMessages)

            repository.getRooms(user).join()
            assertEquals("confirming sync must apply the server state", 3, conversationEntity().unreadMessages)
        }
    }

    @Test
    fun `a sync cannot revert a pending archive until the server confirms it`() {
        val user = user(withKeepNotificationsCapability = false)
        val repository = repository()
        whenever(conversationsNetwork.getRooms(any(), any(), any())).thenReturn(
            Observable.just(listOf(staleServerRoom(lastReadMessage = 10, unreadMessages = 0))),
            Observable.just(listOf(staleServerRoom(lastReadMessage = 10, unreadMessages = 0, hasArchived = true))),
            Observable.just(listOf(staleServerRoom(lastReadMessage = 10, unreadMessages = 0)))
        )

        runBlocking {
            seedConversation(lastActivity = 12, lastReadMessage = 10, unreadMessages = 0)
            conversationListUpdater.markPendingArchived(INTERNAL_CONVERSATION_ID, archived = true)
            db.conversationsDao().updateConversation(conversationEntity().copy(hasArchived = true))

            repository.getRooms(user).join()
            assertTrue("stale sync must not revert the archive", conversationEntity().hasArchived)

            repository.getRooms(user).join()
            assertTrue("confirming sync must keep the archive", conversationEntity().hasArchived)

            repository.getRooms(user).join()
            assertFalse("server authority must be restored", conversationEntity().hasArchived)
        }
    }

    @Test
    fun `room list flow reflects database writes reactively`() {
        val user = user(withKeepNotificationsCapability = false)
        val repository = OfflineFirstConversationsRepository(
            db.conversationsDao(),
            conversationsNetwork,
            chatNetwork,
            networkMonitor,
            syncer,
            conversationListUpdater,
            ApplicationProvider.getApplicationContext()
        )
        whenever(conversationsNetwork.getRooms(any(), any(), any())).thenReturn(
            Observable.just(listOf(Conversation(token = ROOM_TOKEN, lastActivity = 10, unreadMessages = 1)))
        )

        runBlocking {
            val emissions = mutableListOf<List<ConversationModel>>()
            val collector = launch(Dispatchers.IO) {
                repository.roomListFlow.collect { emissions.add(it) }
            }

            repository.getRooms(user).join()
            awaitUntil { emissions.lastOrNull()?.firstOrNull()?.unreadMessages == 1 }

            db.conversationsDao().updateReadState(INTERNAL_CONVERSATION_ID, lastReadMessage = 99, unreadMessages = 7)
            awaitUntil { emissions.lastOrNull()?.firstOrNull()?.unreadMessages == 7 }

            collector.cancel()
        }
    }

    private fun repository(): OfflineFirstConversationsRepository =
        OfflineFirstConversationsRepository(
            db.conversationsDao(),
            conversationsNetwork,
            chatNetwork,
            networkMonitor,
            syncer,
            conversationListUpdater,
            ApplicationProvider.getApplicationContext()
        )

    private fun staleServerRoom(
        lastReadMessage: Int,
        unreadMessages: Int,
        favorite: Boolean = false,
        hasArchived: Boolean = false
    ): Conversation =
        Conversation(
            token = ROOM_TOKEN,
            lastActivity = 12,
            lastReadMessage = lastReadMessage,
            unreadMessages = unreadMessages,
            favorite = favorite,
            hasArchived = hasArchived
        )

    private suspend fun seedConversation(lastActivity: Long, lastReadMessage: Int, unreadMessages: Int) {
        val entity = Conversation(
            token = ROOM_TOKEN,
            lastActivity = lastActivity,
            lastReadMessage = lastReadMessage,
            unreadMessages = unreadMessages,
            lastMessage = message(lastReadMessage.toLong())
        ).asEntity(ACCOUNT_ID)
        db.conversationsDao().upsertConversations(ACCOUNT_ID, listOf(entity))
    }

    private suspend fun conversationEntity(): ConversationEntity =
        db.conversationsDao().getConversationForUser(ACCOUNT_ID, ROOM_TOKEN).first()!!

    private suspend fun awaitUntil(timeoutMillis: Long = TIMEOUT_MILLIS, condition: suspend () -> Boolean) {
        val start = System.currentTimeMillis()
        while (!condition()) {
            if (System.currentTimeMillis() - start > timeoutMillis) {
                throw AssertionError("Condition not met within $timeoutMillis ms")
            }
            delay(POLL_INTERVAL_MILLIS)
        }
    }

    private fun user(withKeepNotificationsCapability: Boolean = true): User {
        val features = if (withKeepNotificationsCapability) {
            listOf("chat-keep-notifications")
        } else {
            emptyList()
        }
        return User(
            id = ACCOUNT_ID,
            userId = "me",
            username = "me",
            baseUrl = BASE_URL,
            token = "app-password",
            capabilities = Capabilities().apply {
                spreedCapability = SpreedCapability().apply { this.features = features }
            }
        )
    }

    private fun target(): ChatMessageSyncer.SyncTarget =
        ChatMessageSyncer.SyncTarget(
            user = user(),
            roomToken = ROOM_TOKEN,
            threadId = null,
            credentials = "credentials",
            urlForChatting = ApiUtils.getUrlForChat(1, BASE_URL, ROOM_TOKEN)
        )

    private fun message(
        id: Long,
        actorId: String = "other",
        messageType: String = "comment",
        systemMessageType: ChatMessage.SystemMessageType = ChatMessage.SystemMessageType.DUMMY
    ): ChatMessageJson =
        ChatMessageJson(
            id = id,
            token = ROOM_TOKEN,
            actorType = "users",
            actorId = actorId,
            actorDisplayName = "Actor $actorId",
            timestamp = id,
            message = "message $id",
            messageType = messageType,
            systemMessageType = systemMessageType
        )

    private fun overall(vararg messages: ChatMessageJson): ChatOverall =
        ChatOverall(ocs = ChatOCS(meta = null, data = messages.toList()))

    companion object {
        private const val ACCOUNT_ID = 1L
        private const val BASE_URL = "https://server.example.com"
        private const val ROOM_TOKEN = "room1"
        private const val INTERNAL_CONVERSATION_ID = "$ACCOUNT_ID@$ROOM_TOKEN"
        private const val TIMEOUT_MILLIS = 10_000L
        private const val POLL_INTERVAL_MILLIS = 50L
    }
}
