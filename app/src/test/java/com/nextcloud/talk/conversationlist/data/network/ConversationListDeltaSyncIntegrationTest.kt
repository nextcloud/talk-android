/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationlist.data.network

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nextcloud.talk.arbitrarystorage.ArbitraryStorageManager
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.chat.data.network.ChatMessageSyncer
import com.nextcloud.talk.chat.data.network.ChatNetworkDataSource
import com.nextcloud.talk.data.database.model.ChatBlockEntity
import com.nextcloud.talk.data.database.model.ChatMessageEntity
import com.nextcloud.talk.data.network.NetworkMonitor
import com.nextcloud.talk.data.source.local.TalkDatabase
import com.nextcloud.talk.data.storage.ArbitraryStoragesRepositoryImpl
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.data.user.model.UserEntity
import com.nextcloud.talk.logger.Logger
import com.nextcloud.talk.models.ExternalSignalingServer
import com.nextcloud.talk.models.json.capabilities.CapabilitiesDto
import com.nextcloud.talk.models.json.capabilities.SpreedCapabilityDto
import com.nextcloud.talk.models.json.capabilities.UserStatusCapabilityDto
import com.nextcloud.talk.models.json.chat.ChatOCS
import com.nextcloud.talk.models.json.chat.ChatOverall
import com.nextcloud.talk.models.json.conversations.ConversationDto
import io.reactivex.Observable
import io.reactivex.android.plugins.RxAndroidPlugins
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response

/**
 * Integration tests for the `modifiedSince` delta sync against an in-memory database.
 *
 * Covers that a delta response leaves the conversations it omits, and their cached messages and
 * chat blocks, in place; that a full response still removes them; what a delta request sends; that
 * an internal-signaling account never gets one; and that a failed sync drops the stored timestamp.
 */
private const val ACCOUNT_ID = 1L
private const val BASE_URL = "https://server.example.com"

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class ConversationListDeltaSyncIntegrationTest {

    private lateinit var db: TalkDatabase
    private lateinit var arbitraryStorageManager: ArbitraryStorageManager
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
        wheneverBlocking { chatNetwork.pullChatMessages(any(), any(), any()) }
            .thenReturn(Response.success(ChatOverall(ocs = ChatOCS(meta = null, data = emptyList()))))

        val conversationListUpdater =
            ConversationListUpdater(db.chatMessagesDao(), db.chatBlocksDao(), db.conversationsDao())
        val syncer = ChatMessageSyncer(
            db.chatMessagesDao(),
            db.chatBlocksDao(),
            chatNetwork,
            networkMonitor,
            conversationListUpdater
        )
        arbitraryStorageManager = ArbitraryStorageManager(ArbitraryStoragesRepositoryImpl(db.arbitraryStoragesDao()))
        repository = OfflineFirstConversationsRepository(
            db.conversationsDao(),
            conversationsNetwork,
            chatNetwork,
            networkMonitor,
            syncer,
            conversationListUpdater,
            arbitraryStorageManager,
            context,
            mock<Logger>()
        )
    }

    @After
    fun tearDown() {
        db.close()
        RxAndroidPlugins.reset()
    }

    @Test
    fun `a delta sync keeps the conversations it does not mention, with their messages and blocks`() {
        val allRooms = (1..ROOM_COUNT).map { conversation("room$it", lastActivity = 10) }
        whenever(conversationsNetwork.getRooms(any(), any(), any(), anyOrNull())).thenReturn(
            fullResponse(allRooms, modifiedBefore = 1000),
            deltaResponse(listOf(conversation("room1", lastActivity = 20)), modifiedBefore = 2000)
        )

        runBlocking {
            repository.getRooms(user()).join()
            assertEquals(ROOM_COUNT, storedConversationCount())
            allRooms.forEach { seedCachedChat(it.token!!) }

            repository.getRooms(user()).join()

            assertEquals(
                "a delta response must not reconcile away the conversations it left out",
                ROOM_COUNT,
                storedConversationCount()
            )
            assertTrue("cached messages must survive a delta sync", cachedMessageIds("room7").isNotEmpty())
            assertTrue("chat blocks must survive a delta sync", cachedBlocks("room7").isNotEmpty())
        }
    }

    @Test
    fun `a full sync reconciles away a conversation the response left out`() {
        whenever(conversationsNetwork.getRooms(any(), any(), any(), anyOrNull())).thenReturn(
            fullResponse(listOf(conversation("room1"), conversation("room2")), modifiedBefore = 1000),
            fullResponse(listOf(conversation("room1")), modifiedBefore = 2000)
        )

        runBlocking {
            repository.getRooms(user()).join()
            assertEquals(2, storedConversationCount())

            repository.getRooms(user(), forceFullSync = true).join()

            assertEquals("a full response is the one that can say a conversation is gone", 1, storedConversationCount())
        }
    }

    @Test
    fun `a delta sync sends the stored timestamp and asks without includeStatus`() {
        whenever(conversationsNetwork.getRooms(any(), any(), any(), anyOrNull())).thenReturn(
            fullResponse(listOf(conversation("room1")), modifiedBefore = 1000),
            deltaResponse(listOf(conversation("room1")), modifiedBefore = 2000)
        )

        runBlocking {
            repository.getRooms(user()).join()
            repository.getRooms(user()).join()
        }

        val includeStatus = argumentCaptor<Boolean>()
        val modifiedSince = argumentCaptor<Long>()
        verify(conversationsNetwork, times(2))
            .getRooms(any(), any(), includeStatus.capture(), modifiedSince.capture())

        assertEquals("the first sync has nothing to anchor a delta on", null, modifiedSince.firstValue)
        assertTrue("a full sync keeps user status fresh", includeStatus.firstValue)
        assertEquals("the second sync echoes the header of the first", 1000L, modifiedSince.secondValue)
        assertEquals("includeStatus would return every one-to-one room anyway", false, includeStatus.secondValue)
    }

    @Test
    fun `the internal signaling backend never gets a delta sync`() {
        whenever(conversationsNetwork.getRooms(any(), any(), any(), anyOrNull()))
            .thenReturn(fullResponse(listOf(conversation("room1")), modifiedBefore = 1000))

        runBlocking {
            repository.getRooms(userWithInternalSignaling()).join()
            repository.getRooms(userWithInternalSignaling()).join()
        }

        val modifiedSince = argumentCaptor<Long>()
        verify(conversationsNetwork, times(2))
            .getRooms(any(), any(), any(), modifiedSince.capture())
        assertNull("no signaling server exists to announce a removal out of band", modifiedSince.secondValue)
    }

    @Test
    fun `a failed sync drops the stored timestamp so the next one asks for everything`() {
        whenever(conversationsNetwork.getRooms(any(), any(), any(), anyOrNull())).thenReturn(
            fullResponse(listOf(conversation("room1")), modifiedBefore = 1000),
            Observable.error(IllegalStateException("server said no"))
        )

        runBlocking {
            repository.getRooms(user()).join()
            assertEquals("1000", storedValue(KEY_MODIFIED_SINCE))

            repository.getRooms(user()).join()

            assertNull(
                "a delta anchored on a sync that did not land would skip what it carried",
                storedValue(KEY_MODIFIED_SINCE)
            )
        }
    }

    private fun storedValue(key: String): String? =
        arbitraryStorageManager.getStorageSetting(ACCOUNT_ID, key, "").blockingGet()?.value

    private suspend fun storedConversationCount(): Int =
        db.conversationsDao().getConversationsForUser(ACCOUNT_ID).first().size

    private suspend fun seedCachedChat(roomToken: String) {
        val internalConversationId = "$ACCOUNT_ID@$roomToken"
        db.chatMessagesDao().upsertChatMessages(
            listOf(
                ChatMessageEntity(
                    internalId = "$internalConversationId@1",
                    accountId = ACCOUNT_ID,
                    token = roomToken,
                    id = 1,
                    internalConversationId = internalConversationId,
                    actorDisplayName = "Other User",
                    message = "cached message",
                    actorId = "other",
                    actorType = "users",
                    messageType = "comment",
                    systemMessageType = ChatMessage.SystemMessageType.DUMMY
                )
            )
        )
        db.chatBlocksDao().upsertChatBlock(
            ChatBlockEntity(
                internalConversationId = internalConversationId,
                accountId = ACCOUNT_ID,
                token = roomToken,
                oldestMessageId = 1,
                newestMessageId = 1,
                hasHistory = false
            )
        )
    }

    private suspend fun cachedMessageIds(roomToken: String): List<Long> =
        db.chatMessagesDao().getMessagesForConversation("$ACCOUNT_ID@$roomToken", null).first().map { it.id }

    private suspend fun cachedBlocks(roomToken: String): List<ChatBlockEntity> =
        db.chatBlocksDao().getChatBlocksForConversation("$ACCOUNT_ID@$roomToken")

    companion object {
        private const val ROOM_COUNT = 10
        private const val KEY_MODIFIED_SINCE = "conversation_list_modified_since"
    }
}

private fun conversation(roomToken: String, lastActivity: Long = 10): ConversationDto =
    ConversationDto(token = roomToken, lastActivity = lastActivity, unreadMessages = 0)

private fun fullResponse(rooms: List<ConversationDto>, modifiedBefore: Long): Observable<RoomListResult> =
    Observable.just(RoomListResult(rooms, modifiedBefore = modifiedBefore, wasDelta = false))

private fun deltaResponse(rooms: List<ConversationDto>, modifiedBefore: Long): Observable<RoomListResult> =
    Observable.just(RoomListResult(rooms, modifiedBefore = modifiedBefore, wasDelta = true))

private fun user(): User =
    User(
        id = ACCOUNT_ID,
        userId = "me",
        username = "me",
        baseUrl = BASE_URL,
        token = "app-password",
        externalSignalingServer = ExternalSignalingServer(externalSignalingServer = "https://hpb.example.com"),
        capabilities = CapabilitiesDto().apply {
            spreedCapability = SpreedCapabilityDto().apply { features = listOf("chat-keep-notifications") }
            userStatusCapability = UserStatusCapabilityDto(enabled = true, restore = false, supportsEmoji = true)
        }
    )

private fun userWithInternalSignaling(): User = user().copy(externalSignalingServer = null)
