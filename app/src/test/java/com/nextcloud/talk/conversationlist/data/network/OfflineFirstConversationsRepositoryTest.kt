/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationlist.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.os.PowerManager
import com.nextcloud.talk.chat.data.network.ChatMessageSyncer
import com.nextcloud.talk.chat.data.network.ChatNetworkDataSource
import com.nextcloud.talk.data.database.dao.ConversationsDao
import com.nextcloud.talk.data.database.mappers.asEntity
import com.nextcloud.talk.data.database.model.ConversationEntity
import com.nextcloud.talk.data.network.NetworkMonitor
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.capabilities.Capabilities
import com.nextcloud.talk.models.json.capabilities.SpreedCapability
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.utils.SpreedFeatures
import io.reactivex.Observable
import io.reactivex.android.plugins.RxAndroidPlugins
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.after
import org.mockito.Mockito.timeout
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.wheneverBlocking
import org.mockito.kotlin.whenever

/**
 * Covers [OfflineFirstConversationsRepository] at the unit level, with all collaborators mocked.
 *
 * The background message catch-up spawned from [OfflineFirstConversationsRepository.getRooms]
 * runs on its own internal [kotlinx.coroutines.CoroutineScope] (fire-and-forget, not a child of the
 * returned [kotlinx.coroutines.Job]), so `getRooms(user).join()` does not wait for it. Assertions
 * about the catch-up therefore poll with a real timeout ([AWAIT_TIMEOUT_MILLIS]) rather than just
 * asserting right after `join()`; skip assertions instead wait a fixed delay
 * ([AFTER_DELAY_MILLIS]) and check nothing arrived. The RxJava chain in [getRoom] behaves the
 * same way, for the same reason.
 */
@Suppress("TooManyFunctions")
class OfflineFirstConversationsRepositoryTest {

    private val dao: ConversationsDao = mock()
    private val network: ConversationsNetworkDataSource = mock()
    private val chatNetworkDataSource: ChatNetworkDataSource = mock()
    private val networkMonitor: NetworkMonitor = mock()
    private val chatMessageSyncer: ChatMessageSyncer = mock()
    private val conversationListUpdater: ConversationListUpdater = mock()
    private val context: Context = mock()
    private val powerManager: PowerManager = mock()
    private val connectivityManager: ConnectivityManager = mock()

    private lateinit var repository: OfflineFirstConversationsRepository

    @Before
    fun setUp() {
        RxAndroidPlugins.setInitMainThreadSchedulerHandler { Schedulers.trampoline() }
        RxAndroidPlugins.setMainThreadSchedulerHandler { Schedulers.trampoline() }

        whenever(networkMonitor.isOnline).thenReturn(MutableStateFlow(true))
        whenever(context.getSystemService(Context.POWER_SERVICE)).thenReturn(powerManager)
        whenever(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(connectivityManager)
        whenever(powerManager.isPowerSaveMode).thenReturn(false)
        whenever(connectivityManager.isActiveNetworkMetered).thenReturn(false)
        whenever(connectivityManager.restrictBackgroundStatus)
            .thenReturn(ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED)

        whenever(dao.getConversationsForUser(ACCOUNT_ID)).thenReturn(flowOf(emptyList()))
        whenever(conversationListUpdater.preservePendingLocalState(any(), any()))
            .thenAnswer { invocation -> invocation.getArgument<List<ConversationEntity>>(1) }

        repository = OfflineFirstConversationsRepository(
            dao,
            network,
            chatNetworkDataSource,
            networkMonitor,
            chatMessageSyncer,
            conversationListUpdater,
            context
        )
    }

    @After
    fun tearDown() {
        RxAndroidPlugins.reset()
    }

    @Test
    fun `getRooms does not fetch from the server while offline`() =
        runBlocking {
            whenever(networkMonitor.isOnline).thenReturn(MutableStateFlow(false))

            repository.getRooms(user()).join()

            verifyBlocking(network, never()) { getRooms(any(), any(), any()) }
        }

    @Test
    fun `getRooms fetches conversations from the server and syncs them locally when online`() =
        runBlocking {
            val room = conversation(token = ROOM_TOKEN, lastActivity = 5, unreadMessages = 0)
            whenever(network.getRooms(any(), any(), any())).thenReturn(Observable.just(listOf(room)))

            repository.getRooms(user()).join()

            val entityCaptor = argumentCaptor<List<ConversationEntity>>()
            verifyBlocking(dao) { syncConversationsForUser(eq(ACCOUNT_ID), entityCaptor.capture(), eq(emptyList())) }
            assertEquals(1, entityCaptor.firstValue.size)
            assertEquals(ROOM_TOKEN, entityCaptor.firstValue.first().token)
        }

    @Test
    fun `getRooms skips deleting local conversations when the server returns an empty list`() =
        runBlocking {
            val previous = conversation(token = ROOM_TOKEN, lastActivity = 5, unreadMessages = 0).asEntity(ACCOUNT_ID)
            whenever(dao.getConversationsForUser(ACCOUNT_ID)).thenReturn(flowOf(listOf(previous)))
            whenever(network.getRooms(any(), any(), any())).thenReturn(Observable.just(emptyList()))

            repository.getRooms(user()).join()

            // a sync response with no conversations at all is treated as broken/partial, so the
            // previously cached conversation must not be deleted
            verifyBlocking(dao) { syncConversationsForUser(eq(ACCOUNT_ID), eq(emptyList()), eq(emptyList())) }
        }

    @Test
    fun `getRooms deletes conversations that are no longer present on the server`() =
        runBlocking {
            val staying = conversation(token = "roomA", lastActivity = 5, unreadMessages = 0).asEntity(ACCOUNT_ID)
            val leaving = conversation(token = "roomB", lastActivity = 5, unreadMessages = 0).asEntity(ACCOUNT_ID)
            whenever(dao.getConversationsForUser(ACCOUNT_ID)).thenReturn(flowOf(listOf(staying, leaving)))
            val stayingRoom = conversation(token = "roomA", lastActivity = 5, unreadMessages = 0)
            whenever(network.getRooms(any(), any(), any())).thenReturn(Observable.just(listOf(stayingRoom)))

            repository.getRooms(user()).join()

            verifyBlocking(dao) {
                syncConversationsForUser(eq(ACCOUNT_ID), any(), eq(listOf(leaving.internalId)))
            }
        }

    @Test
    fun `getRooms merges the server response through the conversation list updater`() =
        runBlocking {
            val previous = conversation(token = ROOM_TOKEN, lastActivity = 5, unreadMessages = 0).asEntity(ACCOUNT_ID)
            whenever(dao.getConversationsForUser(ACCOUNT_ID)).thenReturn(flowOf(listOf(previous)))
            val serverRoom = conversation(token = ROOM_TOKEN, lastActivity = 6, unreadMessages = 1)
            whenever(network.getRooms(any(), any(), any())).thenReturn(Observable.just(listOf(serverRoom)))

            repository.getRooms(user()).join()

            val previousCaptor = argumentCaptor<Map<String, ConversationEntity>>()
            val serverCaptor = argumentCaptor<List<ConversationEntity>>()
            verify(conversationListUpdater).preservePendingLocalState(previousCaptor.capture(), serverCaptor.capture())
            assertEquals(mapOf(previous.internalId to previous), previousCaptor.firstValue)
            assertEquals(6L, serverCaptor.firstValue.first().lastActivity)
        }

    @Test
    fun `background message catch-up is skipped when the server lacks the chat-keep-notifications capability`() =
        runBlocking {
            val room = conversation(token = ROOM_TOKEN, lastActivity = 5, unreadMessages = 2)
            whenever(network.getRooms(any(), any(), any())).thenReturn(Observable.just(listOf(room)))

            repository.getRooms(user(withKeepNotificationsCapability = false)).join()

            verifyBlocking(chatMessageSyncer, after(AFTER_DELAY_MILLIS).never()) {
                catchUpRoom(any(), any(), anyOrNull(), any())
            }
        }

    @Test
    fun `background message catch-up is skipped in battery saver mode`() =
        runBlocking {
            whenever(powerManager.isPowerSaveMode).thenReturn(true)
            val room = conversation(token = ROOM_TOKEN, lastActivity = 5, unreadMessages = 2)
            whenever(network.getRooms(any(), any(), any())).thenReturn(Observable.just(listOf(room)))

            repository.getRooms(user()).join()

            verifyBlocking(chatMessageSyncer, after(AFTER_DELAY_MILLIS).never()) {
                catchUpRoom(any(), any(), anyOrNull(), any())
            }
        }

    @Test
    fun `background message catch-up is skipped when background data is restricted on a metered network`() =
        runBlocking {
            whenever(connectivityManager.isActiveNetworkMetered).thenReturn(true)
            whenever(connectivityManager.restrictBackgroundStatus)
                .thenReturn(ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED)
            val room = conversation(token = ROOM_TOKEN, lastActivity = 5, unreadMessages = 2)
            whenever(network.getRooms(any(), any(), any())).thenReturn(Observable.just(listOf(room)))

            repository.getRooms(user()).join()

            verifyBlocking(chatMessageSyncer, after(AFTER_DELAY_MILLIS).never()) {
                catchUpRoom(any(), any(), anyOrNull(), any())
            }
        }

    @Test
    fun `background message catch-up runs when the network is metered but not restricted`() =
        runBlocking {
            whenever(connectivityManager.isActiveNetworkMetered).thenReturn(true)
            whenever(connectivityManager.restrictBackgroundStatus)
                .thenReturn(ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED)
            val room = conversation(token = ROOM_TOKEN, lastActivity = 5, unreadMessages = 2)
            whenever(network.getRooms(any(), any(), any())).thenReturn(Observable.just(listOf(room)))
            stubCatchUpRoom()

            repository.getRooms(user()).join()

            verifyBlocking(chatMessageSyncer, timeout(AWAIT_TIMEOUT_MILLIS)) {
                catchUpRoom(any(), any(), anyOrNull(), any())
            }
        }

    @Test
    fun `background catch-up only targets rooms with advanced activity or unread rooms without a cached block`() =
        runBlocking {
            val previousUnchanged =
                conversation(token = "unchanged", lastActivity = 10, unreadMessages = 0).asEntity(ACCOUNT_ID)
            val previousNoBlock =
                conversation(token = "noBlockUnread", lastActivity = 10, unreadMessages = 3).asEntity(ACCOUNT_ID)
            whenever(dao.getConversationsForUser(ACCOUNT_ID))
                .thenReturn(flowOf(listOf(previousUnchanged, previousNoBlock)))

            val unchangedRoom = conversation(token = "unchanged", lastActivity = 10, unreadMessages = 0)
            val noBlockRoom = conversation(token = "noBlockUnread", lastActivity = 10, unreadMessages = 3)
            whenever(network.getRooms(any(), any(), any()))
                .thenReturn(Observable.just(listOf(unchangedRoom, noBlockRoom)))

            whenever(chatMessageSyncer.hasLocalChatBlock("$ACCOUNT_ID@unchanged", null)).thenReturn(true)
            whenever(chatMessageSyncer.hasLocalChatBlock("$ACCOUNT_ID@noBlockUnread", null)).thenReturn(false)
            stubCatchUpRoom()

            repository.getRooms(user()).join()

            val targetCaptor = argumentCaptor<ChatMessageSyncer.SyncTarget>()
            verifyBlocking(chatMessageSyncer, timeout(AWAIT_TIMEOUT_MILLIS).times(1)) {
                catchUpRoom(targetCaptor.capture(), any(), anyOrNull(), any())
            }
            assertEquals(listOf("noBlockUnread"), targetCaptor.allValues.map { it.roomToken })
        }

    @Test
    fun `background catch-up is capped to the 20 most recently active rooms`() =
        runBlocking {
            val rooms = (0 until 25).map { i ->
                conversation(token = "room$i", lastActivity = i.toLong(), unreadMessages = 0)
            }
            whenever(network.getRooms(any(), any(), any())).thenReturn(Observable.just(rooms))
            stubCatchUpRoom()

            repository.getRooms(user()).join()

            val targetCaptor = argumentCaptor<ChatMessageSyncer.SyncTarget>()
            verifyBlocking(chatMessageSyncer, timeout(AWAIT_TIMEOUT_MILLIS).times(20)) {
                catchUpRoom(targetCaptor.capture(), any(), anyOrNull(), any())
            }
            val expectedTokens = (5 until 25).map { "room$it" }.toSet()
            assertEquals(expectedTokens, targetCaptor.allValues.map { it.roomToken }.toSet())
        }

    @Test
    fun `updateConversation persists the model as an entity`() =
        runTest {
            val model = ConversationModel.mapToConversationModel(
                conversation(token = ROOM_TOKEN, lastActivity = 9, unreadMessages = 1),
                user()
            )

            repository.updateConversation(model)

            val captor = argumentCaptor<ConversationEntity>()
            verify(dao).updateConversation(captor.capture())
            assertEquals(ROOM_TOKEN, captor.firstValue.token)
            assertEquals(9L, captor.firstValue.lastActivity)
        }

    @Test
    fun `getLocallyStoredConversation returns the mapped model when present`() =
        runTest {
            val entity = conversation(token = ROOM_TOKEN, lastActivity = 3, unreadMessages = 0).asEntity(ACCOUNT_ID)
            whenever(dao.getConversationForUser(ACCOUNT_ID, ROOM_TOKEN)).thenReturn(flowOf(entity))

            val result = repository.getLocallyStoredConversation(user(), ROOM_TOKEN)

            assertEquals(entity.internalId, result?.internalId)
        }

    @Test
    fun `getLocallyStoredConversation returns null when the conversation is not cached`() =
        runTest {
            whenever(dao.getConversationForUser(ACCOUNT_ID, ROOM_TOKEN)).thenReturn(flowOf(null))

            val result = repository.getLocallyStoredConversation(user(), ROOM_TOKEN)

            assertNull(result)
        }

    @Test
    fun `observeConversation emits Found when the conversation exists`() =
        runTest {
            val entity = conversation(token = ROOM_TOKEN, lastActivity = 1, unreadMessages = 0).asEntity(ACCOUNT_ID)
            whenever(dao.getConversationForUser(ACCOUNT_ID, ROOM_TOKEN)).thenReturn(flowOf(entity))

            val result = repository.observeConversation(ACCOUNT_ID, ROOM_TOKEN).first()

            assertTrue(result is OfflineFirstConversationsRepository.ConversationResult.Found)
            assertEquals(
                entity.internalId,
                (result as OfflineFirstConversationsRepository.ConversationResult.Found).conversation.internalId
            )
        }

    @Test
    fun `observeConversation emits NotFound when the conversation is missing`() =
        runTest {
            whenever(dao.getConversationForUser(ACCOUNT_ID, ROOM_TOKEN)).thenReturn(flowOf(null))

            val result = repository.observeConversation(ACCOUNT_ID, ROOM_TOKEN).first()

            assertEquals(OfflineFirstConversationsRepository.ConversationResult.NotFound, result)
        }

    @Test
    fun `getRoom persists the fetched conversation, preserving the locally hidden upcoming event`() =
        runBlocking {
            val existing = conversation(token = ROOM_TOKEN, lastActivity = 1, unreadMessages = 0)
                .asEntity(ACCOUNT_ID)
                .copy(hiddenUpcomingEvent = "event-1")
            whenever(dao.getConversationForUser(ACCOUNT_ID, ROOM_TOKEN)).thenReturn(flowOf(existing))
            val fetched = ConversationModel.mapToConversationModel(
                conversation(token = ROOM_TOKEN, lastActivity = 5, unreadMessages = 2),
                user()
            )
            whenever(chatNetworkDataSource.getRoom(any(), any())).thenReturn(Observable.just(fetched))

            repository.getRoom(user(), ROOM_TOKEN).join()

            val captor = argumentCaptor<List<ConversationEntity>>()
            verifyBlocking(dao, timeout(AWAIT_TIMEOUT_MILLIS)) { upsertConversations(eq(ACCOUNT_ID), captor.capture()) }
            assertEquals("event-1", captor.firstValue.first().hiddenUpcomingEvent)
            assertEquals(5L, captor.firstValue.first().lastActivity)
        }

    @Test
    fun `getRoom falls back to the locally stored conversation when the network call fails`() =
        runBlocking {
            val existing = conversation(token = ROOM_TOKEN, lastActivity = 4, unreadMessages = 1).asEntity(ACCOUNT_ID)
            whenever(dao.getConversationForUser(ACCOUNT_ID, ROOM_TOKEN)).thenReturn(flowOf(existing))
            whenever(chatNetworkDataSource.getRoom(any(), any()))
                .thenReturn(Observable.error(RuntimeException("network failure")))

            val emissions = mutableListOf<ConversationModel>()
            val collector = launch(Dispatchers.IO) {
                repository.conversationFlow.collect { emissions.add(it) }
            }
            // conversationFlow has no replay, so the collector must already be subscribed before
            // the fallback emits, otherwise the emission is simply dropped
            delay(COLLECTOR_STARTUP_MILLIS)

            repository.getRoom(user(), ROOM_TOKEN).join()

            awaitUntil { emissions.isNotEmpty() }
            assertEquals(existing.internalId, emissions.first().internalId)
            collector.cancel()
        }

    private fun stubCatchUpRoom() {
        wheneverBlocking { chatMessageSyncer.catchUpRoom(any(), any(), anyOrNull(), any()) }
            .thenReturn(ChatMessageSyncer.SyncOutcome(persistedNewMessages = false, newestPersistedMessageId = null))
    }

    private suspend fun awaitUntil(timeoutMillis: Long = AWAIT_TIMEOUT_MILLIS, condition: () -> Boolean) {
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
            listOf(SpreedFeatures.CHAT_KEEP_NOTIFICATIONS.value)
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

    private fun conversation(
        token: String,
        lastActivity: Long,
        unreadMessages: Int,
        lastReadMessage: Int = 0
    ): Conversation =
        Conversation(
            token = token,
            lastActivity = lastActivity,
            unreadMessages = unreadMessages,
            lastReadMessage = lastReadMessage
        )

    companion object {
        private const val ACCOUNT_ID = 1L
        private const val BASE_URL = "https://server.example.com"
        private const val ROOM_TOKEN = "room1"
        private const val AFTER_DELAY_MILLIS = 300L
        private const val AWAIT_TIMEOUT_MILLIS = 2000L
        private const val POLL_INTERVAL_MILLIS = 50L
        private const val COLLECTOR_STARTUP_MILLIS = 100L
    }
}
