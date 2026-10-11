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
import com.nextcloud.talk.arbitrarystorage.ArbitraryStorageManager
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.chat.data.network.ChatMessageSyncer
import com.nextcloud.talk.chat.data.network.ChatNetworkDataSource
import com.nextcloud.talk.data.database.mappers.asEntity
import com.nextcloud.talk.data.database.model.ConversationEntity
import com.nextcloud.talk.data.network.NetworkMonitor
import com.nextcloud.talk.data.source.local.TalkDatabase
import com.nextcloud.talk.data.storage.ArbitraryStoragesRepositoryImpl
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.data.user.model.UserEntity
import com.nextcloud.talk.logger.Logger
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.capabilities.CapabilitiesDto
import com.nextcloud.talk.models.json.capabilities.SpreedCapabilityDto
import com.nextcloud.talk.models.json.chat.ChatMessageDto
import com.nextcloud.talk.models.json.chat.ChatOCS
import com.nextcloud.talk.models.json.chat.ChatOverall
import com.nextcloud.talk.models.json.conversations.ConversationDto
import com.nextcloud.talk.utils.ApiUtils
import io.reactivex.Observable
import io.reactivex.android.plugins.RxAndroidPlugins
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
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
@Suppress("TooManyFunctions", "LargeClass")
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
            val lastMessage = LoganSquare.parse(conversation.lastMessage, ChatMessageDto::class.java)
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
            ArbitraryStorageManager(ArbitraryStoragesRepositoryImpl(db.arbitraryStoragesDao())),
            ApplicationProvider.getApplicationContext(),
            mock<Logger>()
        )
        wheneverBlocking { chatNetwork.getRoom(any(), any()) }.thenReturn(
            ConversationModel.mapToConversationModel(
                staleServerRoom(lastReadMessage = 10, unreadMessages = 2),
                user
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
            ArbitraryStorageManager(ArbitraryStoragesRepositoryImpl(db.arbitraryStoragesDao())),
            ApplicationProvider.getApplicationContext(),
            mock<Logger>()
        )
        whenever(conversationsNetwork.getRooms(any(), any(), any(), anyOrNull())).thenReturn(
            roomList(listOf(staleServerRoom(lastReadMessage = 10, unreadMessages = 2))),
            roomList(listOf(staleServerRoom(lastReadMessage = 12, unreadMessages = 0))),
            roomList(listOf(staleServerRoom(lastReadMessage = 8, unreadMessages = 4)))
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
        whenever(conversationsNetwork.getRooms(any(), any(), any(), anyOrNull())).thenReturn(
            roomList(listOf(staleServerRoom(lastReadMessage = 10, unreadMessages = 0))),
            roomList(listOf(staleServerRoom(lastReadMessage = 10, unreadMessages = 0, favorite = true))),
            roomList(listOf(staleServerRoom(lastReadMessage = 10, unreadMessages = 0)))
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
        whenever(conversationsNetwork.getRooms(any(), any(), any(), anyOrNull())).thenReturn(
            roomList(listOf(staleServerRoom(lastReadMessage = 12, unreadMessages = 0))),
            roomList(listOf(staleServerRoom(lastReadMessage = 9, unreadMessages = 3)))
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
        whenever(conversationsNetwork.getRooms(any(), any(), any(), anyOrNull())).thenReturn(
            roomList(listOf(staleServerRoom(lastReadMessage = 10, unreadMessages = 0))),
            roomList(listOf(staleServerRoom(lastReadMessage = 10, unreadMessages = 0, hasArchived = true))),
            roomList(listOf(staleServerRoom(lastReadMessage = 10, unreadMessages = 0)))
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
    fun `a room refresh requested before a sync cannot revert the entry the sync wrote`() {
        val user = user(withKeepNotificationsCapability = false)
        val repository = repository()
        runBlocking {
            seedConversation(lastActivity = 10, lastReadMessage = 10, unreadMessages = 0)
            val refreshRequested = CompletableDeferred<Unit>()
            val refreshAnswer = CompletableDeferred<Unit>()
            wheneverBlocking { chatNetwork.getRoom(any(), any()) }.doSuspendableAnswer {
                refreshRequested.complete(Unit)
                refreshAnswer.await()
                ConversationModel.mapToConversationModel(serverRoom(lastActivity = 12, lastRead = 10, unread = 2), user)
            }
            val refresh = repository.getRoom(user, ROOM_TOKEN)
            refreshRequested.await()

            val roomWithCall = serverRoom(lastActivity = 20, lastRead = 20, unread = 0).apply { hasCall = true }
            whenever(conversationsNetwork.getRooms(any(), any(), any(), anyOrNull()))
                .thenReturn(roomList(listOf(roomWithCall)))
            repository.getRooms(user).join()
            refreshAnswer.complete(Unit)
            refresh.join()

            val conversation = conversationEntity()
            assertEquals("the older answer must not revert the activity", 20L, conversation.lastActivity)
            assertEquals("the older answer must not revert the read marker", 20, conversation.lastReadMessage)
            assertEquals("the older answer must not bring back the unread count", 0, conversation.unreadMessages)
            assertTrue("the older answer must not drop the call", conversation.hasCall)
        }
    }

    @Test
    fun `a sync requested before a room refresh cannot revert the entry the refresh wrote`() {
        val user = user(withKeepNotificationsCapability = false)
        val repository = repository()
        runBlocking {
            seedConversation(lastActivity = 10, lastReadMessage = 10, unreadMessages = 0)
            val syncRequested = CountDownLatch(1)
            val syncAnswer = CountDownLatch(1)
            whenever(conversationsNetwork.getRooms(any(), any(), any(), anyOrNull())).thenReturn(
                Observable.fromCallable {
                    syncRequested.countDown()
                    syncAnswer.await()
                    RoomListResult(
                        listOf(serverRoom(lastActivity = 12, lastRead = 10, unread = 2)),
                        modifiedBefore = null,
                        wasDelta = false
                    )
                }
            )
            val sync = repository.getRooms(user)
            runInterruptible { syncRequested.await() }

            wheneverBlocking { chatNetwork.getRoom(any(), any()) }.thenReturn(
                ConversationModel.mapToConversationModel(
                    serverRoom(lastActivity = 20, lastRead = 20, unread = 0).apply { hasArchived = true },
                    user
                )
            )
            repository.getRoom(user, ROOM_TOKEN).join()
            syncAnswer.countDown()
            sync.join()

            val conversation = conversationEntity()
            assertEquals("the older sync must not revert the activity", 20L, conversation.lastActivity)
            assertEquals("the older sync must not revert the read marker", 20, conversation.lastReadMessage)
            assertEquals("the older sync must not bring back the unread count", 0, conversation.unreadMessages)
            assertTrue("the older sync must not revert the archive flag", conversation.hasArchived)
        }
    }

    @Test
    fun `a sync requested before a catch-up cannot revert the message the catch-up stored`() {
        val user = user(withKeepNotificationsCapability = false)
        val repository = repository()
        runBlocking {
            seedConversation(lastActivity = 10, lastReadMessage = 10, unreadMessages = 0)
            val syncRequested = CountDownLatch(1)
            val syncAnswer = CountDownLatch(1)
            whenever(conversationsNetwork.getRooms(any(), any(), any(), anyOrNull())).thenReturn(
                Observable.fromCallable {
                    syncRequested.countDown()
                    syncAnswer.await()
                    RoomListResult(
                        listOf(serverRoom(lastActivity = 10, lastRead = 10, unread = 0)),
                        modifiedBefore = null,
                        wasDelta = false
                    )
                }
            )
            val sync = repository.getRooms(user)
            runInterruptible { syncRequested.await() }

            wheneverBlocking { chatNetwork.pullChatMessages(any(), any(), any()) }
                .thenReturn(Response.success(overall(message(10), message(11), message(12))))
            syncer.catchUpRoom(target(), lastReadMessage = 10, unreadMessages = 2)
            syncAnswer.countDown()
            sync.join()

            val conversation = conversationEntity()
            assertEquals("the older sync must not revert the activity", 12L, conversation.lastActivity)
            assertEquals("the older sync must not revert the unread count", 2, conversation.unreadMessages)
            assertEquals(12L, LoganSquare.parse(conversation.lastMessage, ChatMessageDto::class.java).id)
        }
    }

    @Test
    fun `a full sync requested before a room was refreshed does not delete it`() {
        val user = user(withKeepNotificationsCapability = false)
        val repository = repository()
        runBlocking {
            seedConversation(lastActivity = 10, lastReadMessage = 10, unreadMessages = 0)
            val syncRequested = CountDownLatch(1)
            val syncAnswer = CountDownLatch(1)
            whenever(conversationsNetwork.getRooms(any(), any(), any(), anyOrNull())).thenReturn(
                Observable.fromCallable {
                    syncRequested.countDown()
                    syncAnswer.await()
                    RoomListResult(
                        listOf(serverRoom(lastActivity = 10, lastRead = 10, unread = 0)),
                        modifiedBefore = null,
                        wasDelta = false
                    )
                }
            )
            val sync = repository.getRooms(user)
            runInterruptible { syncRequested.await() }

            wheneverBlocking { chatNetwork.getRoom(any(), any()) }.thenReturn(
                ConversationModel.mapToConversationModel(
                    serverRoom(token = JOINED_ROOM_TOKEN, lastActivity = 15, lastRead = 15, unread = 0),
                    user
                )
            )
            repository.getRoom(user, JOINED_ROOM_TOKEN).join()
            syncAnswer.countDown()
            sync.join()

            assertEquals(
                "a room the sync could not know about must survive it",
                15L,
                db.conversationsDao().getConversationForUser(ACCOUNT_ID, JOINED_ROOM_TOKEN).first()?.lastActivity
            )
        }
    }

    @Test
    fun `a room refresh requested before a full sync does not bring back a room the sync deleted`() {
        val user = user(withKeepNotificationsCapability = false)
        val repository = repository()
        runBlocking {
            seedConversation(lastActivity = 10, lastReadMessage = 10, unreadMessages = 0)
            val refreshRequested = CompletableDeferred<Unit>()
            val refreshAnswer = CompletableDeferred<Unit>()
            wheneverBlocking { chatNetwork.getRoom(any(), any()) }.doSuspendableAnswer {
                refreshRequested.complete(Unit)
                refreshAnswer.await()
                ConversationModel.mapToConversationModel(serverRoom(lastActivity = 10, lastRead = 10, unread = 0), user)
            }
            val refresh = repository.getRoom(user, ROOM_TOKEN)
            refreshRequested.await()

            whenever(conversationsNetwork.getRooms(any(), any(), any(), anyOrNull())).thenReturn(
                roomList(listOf(serverRoom(token = JOINED_ROOM_TOKEN, lastActivity = 15, lastRead = 15, unread = 0)))
            )
            repository.getRooms(user).join()
            assertNull(db.conversationsDao().getConversationForUser(ACCOUNT_ID, ROOM_TOKEN).first())
            refreshAnswer.complete(Unit)
            refresh.join()

            assertNull(
                "an answer older than the deletion must not bring the room back",
                db.conversationsDao().getConversationForUser(ACCOUNT_ID, ROOM_TOKEN).first()
            )
        }
    }

    @Test
    fun `a room refresh requested after the catch-up request is applied in full`() {
        val user = user(withKeepNotificationsCapability = false)
        val repository = repository()
        runBlocking {
            seedConversation(lastActivity = 10, lastReadMessage = 10, unreadMessages = 0)
            val catchUpRequested = CompletableDeferred<Unit>()
            val catchUpAnswer = CompletableDeferred<Unit>()
            wheneverBlocking { chatNetwork.pullChatMessages(any(), any(), any()) }.doSuspendableAnswer {
                catchUpRequested.complete(Unit)
                catchUpAnswer.await()
                Response.success(overall(message(10), message(11), message(12)))
            }
            val catchUp = launch(Dispatchers.IO) {
                syncer.catchUpRoom(target(), lastReadMessage = 10, unreadMessages = 2)
            }
            catchUpRequested.await()

            val refreshRequested = CompletableDeferred<Unit>()
            val refreshAnswer = CompletableDeferred<Unit>()
            wheneverBlocking { chatNetwork.getRoom(any(), any()) }.doSuspendableAnswer {
                refreshRequested.complete(Unit)
                refreshAnswer.await()
                ConversationModel.mapToConversationModel(
                    serverRoom(lastActivity = 12, lastRead = 10, unread = 2).apply { hasCall = true },
                    user
                )
            }
            val refresh = repository.getRoom(user, ROOM_TOKEN)
            refreshRequested.await()
            catchUpAnswer.complete(Unit)
            catchUp.join()
            refreshAnswer.complete(Unit)
            refresh.join()

            assertTrue("an answer requested after the catch-up request must apply", conversationEntity().hasCall)
        }
    }

    @Test
    fun `a room refresh requested before the catch-up request keeps the fields of the catch-up only`() {
        val user = user(withKeepNotificationsCapability = false)
        val repository = repository()
        runBlocking {
            seedConversation(lastActivity = 10, lastReadMessage = 10, unreadMessages = 0)
            val refreshRequested = CompletableDeferred<Unit>()
            val refreshAnswer = CompletableDeferred<Unit>()
            wheneverBlocking { chatNetwork.getRoom(any(), any()) }.doSuspendableAnswer {
                refreshRequested.complete(Unit)
                refreshAnswer.await()
                ConversationModel.mapToConversationModel(
                    serverRoom(lastActivity = 10, lastRead = 10, unread = 0).apply { hasCall = true },
                    user
                )
            }
            val refresh = repository.getRoom(user, ROOM_TOKEN)
            refreshRequested.await()

            wheneverBlocking { chatNetwork.pullChatMessages(any(), any(), any()) }
                .thenReturn(Response.success(overall(message(10), message(11), message(12))))
            syncer.catchUpRoom(target(), lastReadMessage = 10, unreadMessages = 2)
            refreshAnswer.complete(Unit)
            refresh.join()

            val conversation = conversationEntity()
            assertEquals("the older answer must not revert the activity", 12L, conversation.lastActivity)
            assertEquals("the older answer must not revert the unread count", 2, conversation.unreadMessages)
            assertEquals(12L, LoganSquare.parse(conversation.lastMessage, ChatMessageDto::class.java).id)
            assertTrue("the rest of the answer is newer than the entry and applies", conversation.hasCall)
        }
    }

    @Test
    fun `a delta sync requested before a room refresh drops that room only`() {
        val user = user(withKeepNotificationsCapability = false)
        val repository = repository()
        runBlocking {
            seedConversation(lastActivity = 10, lastReadMessage = 10, unreadMessages = 0)
            val syncRequested = CountDownLatch(1)
            val syncAnswer = CountDownLatch(1)
            whenever(conversationsNetwork.getRooms(any(), any(), any(), anyOrNull())).thenReturn(
                Observable.fromCallable {
                    syncRequested.countDown()
                    syncAnswer.await()
                    RoomListResult(
                        listOf(
                            serverRoom(lastActivity = 12, lastRead = 10, unread = 2),
                            serverRoom(token = JOINED_ROOM_TOKEN, lastActivity = 5, lastRead = 5, unread = 0)
                        ),
                        modifiedBefore = null,
                        wasDelta = true
                    )
                }
            )
            val sync = repository.getRooms(user)
            runInterruptible { syncRequested.await() }

            wheneverBlocking { chatNetwork.getRoom(any(), any()) }.thenReturn(
                ConversationModel.mapToConversationModel(serverRoom(lastActivity = 20, lastRead = 20, unread = 0), user)
            )
            repository.getRoom(user, ROOM_TOKEN).join()
            syncAnswer.countDown()
            sync.join()

            assertEquals("the dropped room keeps the newer entry", 20L, conversationEntity().lastActivity)
            assertEquals(
                "the other rooms of the delta are written",
                5L,
                db.conversationsDao().getConversationForUser(ACCOUNT_ID, JOINED_ROOM_TOKEN).first()?.lastActivity
            )
        }
    }

    @Test
    fun `accounts of one server do not order each other's responses`() {
        val user = user(withKeepNotificationsCapability = false)
        val otherUser = user.copy(id = OTHER_ACCOUNT_ID, userId = "other", username = "other")
        val repository = repository()
        runBlocking {
            db.usersDao().saveUser(
                UserEntity(id = OTHER_ACCOUNT_ID, userId = "other", username = "other", baseUrl = BASE_URL)
            )
            seedConversation(lastActivity = 10, lastReadMessage = 10, unreadMessages = 0)
            val refreshRequested = CompletableDeferred<Unit>()
            val refreshAnswer = CompletableDeferred<Unit>()
            wheneverBlocking { chatNetwork.getRoom(any(), any()) }.doSuspendableAnswer {
                refreshRequested.complete(Unit)
                refreshAnswer.await()
                ConversationModel.mapToConversationModel(
                    serverRoom(lastActivity = 12, lastRead = 10, unread = 2),
                    otherUser
                )
            }
            val refresh = repository.getRoom(otherUser, ROOM_TOKEN)
            refreshRequested.await()

            whenever(conversationsNetwork.getRooms(any(), any(), any(), anyOrNull()))
                .thenReturn(roomList(listOf(serverRoom(lastActivity = 20, lastRead = 20, unread = 0))))
            repository.getRooms(user).join()
            refreshAnswer.complete(Unit)
            refresh.join()

            assertEquals(20L, conversationEntity().lastActivity)
            assertEquals(
                "an answer for the other account is not older than the entry of this one",
                12L,
                db.conversationsDao().getConversationForUser(OTHER_ACCOUNT_ID, ROOM_TOKEN).first()?.lastActivity
            )
        }
    }

    @Test
    fun `a room refresh waits for the write of a sync it is judged against`() {
        val user = user(withKeepNotificationsCapability = false)
        val mergeReached = CountDownLatch(1)
        val refreshDone = CountDownLatch(1)
        val updater = spy(conversationListUpdater)
        val firstMerge = AtomicBoolean(true)
        // the sync is held after it judged its response and before it writes it, the window the lock exists for
        doAnswer { invocation ->
            val superseded = invocation.callRealMethod()
            if (firstMerge.getAndSet(false)) {
                mergeReached.countDown()
                refreshDone.await(LOCK_WAIT_MILLIS, TimeUnit.MILLISECONDS)
            }
            superseded
        }.whenever(updater).isSuperseded(any(), any())
        val repository = OfflineFirstConversationsRepository(
            db.conversationsDao(),
            conversationsNetwork,
            chatNetwork,
            networkMonitor,
            syncer,
            updater,
            ArbitraryStorageManager(ArbitraryStoragesRepositoryImpl(db.arbitraryStoragesDao())),
            ApplicationProvider.getApplicationContext(),
            mock<Logger>()
        )
        runBlocking {
            seedConversation(lastActivity = 10, lastReadMessage = 10, unreadMessages = 0)
            // a delta sync, so that its first judgement is the one of the merge
            whenever(conversationsNetwork.getRooms(any(), any(), any(), anyOrNull())).thenReturn(
                Observable.just(
                    RoomListResult(
                        listOf(serverRoom(lastActivity = 12, lastRead = 10, unread = 2)),
                        modifiedBefore = null,
                        wasDelta = true
                    )
                )
            )
            val sync = repository.getRooms(user)
            runInterruptible { mergeReached.await() }

            wheneverBlocking { chatNetwork.getRoom(any(), any()) }.thenReturn(
                ConversationModel.mapToConversationModel(serverRoom(lastActivity = 20, lastRead = 20, unread = 0), user)
            )
            val refresh = repository.getRoom(user, ROOM_TOKEN)
            launch(Dispatchers.IO) {
                refresh.join()
                refreshDone.countDown()
            }
            sync.join()
            refresh.join()

            assertEquals(
                "the refresh was requested after the sync, so its write must come last",
                20L,
                conversationEntity().lastActivity
            )
        }
    }

    @Test
    fun `a catch-up during a refresh derives unread again from the marker of the answer`() {
        runBlocking {
            // messages 11 and 12 were read on another device before the refresh was requested
            refreshAcrossCatchUp(storedLastRead = 10, answer = serverRoom(lastActivity = 12, lastRead = 12, unread = 0))

            val conversation = conversationEntity()
            assertEquals(12, conversation.lastReadMessage)
            assertEquals("the count follows the marker of the answer", 0, conversation.unreadMessages)
            assertEquals("the preview stays the one of the catch-up", 12L, conversation.lastActivity)
        }
    }

    @Test
    fun `a catch-up during a refresh follows a chat marked as unread elsewhere`() {
        runBlocking {
            refreshAcrossCatchUp(storedLastRead = 12, answer = serverRoom(lastActivity = 12, lastRead = 10, unread = 2))

            val conversation = conversationEntity()
            assertEquals(10, conversation.lastReadMessage)
            assertEquals("the count follows the marker of the answer", 2, conversation.unreadMessages)
        }
    }

    @Test
    fun `a catch-up during a refresh keeps its unread count while the marker is the same`() {
        runBlocking {
            refreshAcrossCatchUp(storedLastRead = 10, answer = serverRoom(lastActivity = 10, lastRead = 10, unread = 0))

            val conversation = conversationEntity()
            assertEquals(10, conversation.lastReadMessage)
            assertEquals("the older answer cannot know the new messages", 2, conversation.unreadMessages)
        }
    }

    @Test
    fun `a lowered marker with an unknown count keeps the higher of the stored and the answered count`() {
        runBlocking {
            // the cached messages start above the marker of the answer, so the count cannot be derived
            refreshAcrossCatchUp(
                storedLastRead = 12,
                answer = serverRoom(lastActivity = 15, lastRead = 10, unread = 2),
                storedUnread = 3,
                fetched = listOf(14, 15)
            )

            val conversation = conversationEntity()
            assertEquals(10, conversation.lastReadMessage)
            assertEquals("both counts are lower bounds", 3, conversation.unreadMessages)
        }
    }

    /**
     * Stores a room, requests a refresh, lets a catch-up that fetches messages 10 to 12 write the room
     * while the refresh is under way, and then lets the refresh answer with [answer].
     */
    private suspend fun refreshAcrossCatchUp(
        storedLastRead: Int,
        answer: ConversationDto,
        storedUnread: Int = 0,
        fetched: List<Long> = listOf(10, 11, 12)
    ) {
        val user = user(withKeepNotificationsCapability = false)
        seedConversation(lastActivity = 10, lastReadMessage = storedLastRead, unreadMessages = storedUnread)
        val refreshRequested = CompletableDeferred<Unit>()
        val refreshAnswer = CompletableDeferred<Unit>()
        wheneverBlocking { chatNetwork.getRoom(any(), any()) }.doSuspendableAnswer {
            refreshRequested.complete(Unit)
            refreshAnswer.await()
            ConversationModel.mapToConversationModel(answer, user)
        }
        val refresh = repository().getRoom(user, ROOM_TOKEN)
        refreshRequested.await()

        wheneverBlocking { chatNetwork.pullChatMessages(any(), any(), any()) }
            .thenReturn(Response.success(ChatOverall(ocs = ChatOCS(meta = null, data = fetched.map { message(it) }))))
        syncer.catchUpRoom(target(), lastReadMessage = storedLastRead, unreadMessages = storedUnread)
        refreshAnswer.complete(Unit)
        refresh.join()
    }

    @Test
    fun `a response requested after the last write applies unchanged`() {
        val user = user(withKeepNotificationsCapability = false)
        val repository = repository()
        runBlocking {
            seedConversation(lastActivity = 10, lastReadMessage = 10, unreadMessages = 0)
            wheneverBlocking { chatNetwork.getRoom(any(), any()) }.thenReturn(
                ConversationModel.mapToConversationModel(serverRoom(lastActivity = 20, lastRead = 20, unread = 0), user)
            )
            repository.getRoom(user, ROOM_TOKEN).join()

            // the server moved on, e.g. the chat was marked as unread on another device
            whenever(conversationsNetwork.getRooms(any(), any(), any(), anyOrNull()))
                .thenReturn(roomList(listOf(serverRoom(lastActivity = 20, lastRead = 15, unread = 5))))
            repository.getRooms(user).join()

            assertEquals(15, conversationEntity().lastReadMessage)
            assertEquals(5, conversationEntity().unreadMessages)
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
            ArbitraryStorageManager(ArbitraryStoragesRepositoryImpl(db.arbitraryStoragesDao())),
            ApplicationProvider.getApplicationContext(),
            mock<Logger>()
        )
        whenever(conversationsNetwork.getRooms(any(), any(), any(), anyOrNull())).thenReturn(
            roomList(listOf(ConversationDto(token = ROOM_TOKEN, lastActivity = 10, unreadMessages = 1)))
        )

        runBlocking {
            val emissions = mutableListOf<List<ConversationModel>>()
            val collector = launch(Dispatchers.IO) {
                repository.roomListFlow(user.id!!).collect { emissions.add(it) }
            }

            repository.getRooms(user).join()
            awaitUntil { emissions.lastOrNull()?.firstOrNull()?.unreadMessages == 1 }

            db.conversationsDao().updateReadState(INTERNAL_CONVERSATION_ID, lastReadMessage = 99, unreadMessages = 7)
            awaitUntil { emissions.lastOrNull()?.firstOrNull()?.unreadMessages == 7 }

            collector.cancel()
        }
    }

    @Test
    fun `room list flow only emits the conversations of the requested account`() {
        val repository = repository()

        runBlocking {
            db.usersDao().saveUser(
                UserEntity(id = OTHER_ACCOUNT_ID, userId = "other", username = "other", baseUrl = BASE_URL)
            )
            seedConversation(lastActivity = 10, lastReadMessage = 1, unreadMessages = 0)

            assertEquals(1, repository.roomListFlow(ACCOUNT_ID).first().size)
            assertTrue(repository.roomListFlow(OTHER_ACCOUNT_ID).first().isEmpty())
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
            ArbitraryStorageManager(ArbitraryStoragesRepositoryImpl(db.arbitraryStoragesDao())),
            ApplicationProvider.getApplicationContext(),
            mock<Logger>()
        )

    private fun serverRoom(
        lastActivity: Long,
        lastRead: Int,
        unread: Int,
        token: String = ROOM_TOKEN
    ): ConversationDto =
        ConversationDto(
            token = token,
            lastActivity = lastActivity,
            lastReadMessage = lastRead,
            unreadMessages = unread
        )

    private fun staleServerRoom(
        lastReadMessage: Int,
        unreadMessages: Int,
        favorite: Boolean = false,
        hasArchived: Boolean = false
    ): ConversationDto =
        ConversationDto(
            token = ROOM_TOKEN,
            lastActivity = 12,
            lastReadMessage = lastReadMessage,
            unreadMessages = unreadMessages,
            favorite = favorite,
            hasArchived = hasArchived
        )

    private suspend fun seedConversation(lastActivity: Long, lastReadMessage: Int, unreadMessages: Int) {
        val entity = ConversationDto(
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
            capabilities = CapabilitiesDto().apply {
                spreedCapability = SpreedCapabilityDto().apply { this.features = features }
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
    ): ChatMessageDto =
        ChatMessageDto(
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

    private fun overall(vararg messages: ChatMessageDto): ChatOverall =
        ChatOverall(ocs = ChatOCS(meta = null, data = messages.toList()))

    companion object {
        private const val ACCOUNT_ID = 1L
        private const val OTHER_ACCOUNT_ID = 2L
        private const val BASE_URL = "https://server.example.com"
        private const val ROOM_TOKEN = "room1"
        private const val JOINED_ROOM_TOKEN = "room2"
        private const val INTERNAL_CONVERSATION_ID = "$ACCOUNT_ID@$ROOM_TOKEN"
        private const val TIMEOUT_MILLIS = 10_000L
        private const val POLL_INTERVAL_MILLIS = 50L
        private const val LOCK_WAIT_MILLIS = 250L
    }
}

private fun roomList(conversations: List<ConversationDto>): Observable<RoomListResult> =
    Observable.just(RoomListResult(conversations, modifiedBefore = null, wasDelta = false))
