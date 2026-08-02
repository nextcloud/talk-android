/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Andy Scherzinger <andy.scherzinger@nextcloud.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.data.network

import android.database.sqlite.SQLiteConstraintException
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.data.database.dao.ChatBlocksDao
import com.nextcloud.talk.data.database.dao.ChatMessagesDao
import com.nextcloud.talk.data.database.model.ChatBlockEntity
import com.nextcloud.talk.data.network.NetworkMonitor
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.capabilities.Capabilities
import com.nextcloud.talk.models.json.capabilities.SpreedCapability
import com.nextcloud.talk.models.json.chat.ChatMessageJson
import com.nextcloud.talk.models.json.chat.ChatOCS
import com.nextcloud.talk.models.json.chat.ChatOverall
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking
import retrofit2.Response

@Suppress("TooManyFunctions")
class ChatMessageSyncerTest {

    private val chatDao: ChatMessagesDao = mock()
    private val chatBlocksDao: ChatBlocksDao = mock()
    private val network: ChatNetworkDataSource = mock()
    private val networkMonitor: NetworkMonitor = mock()

    private lateinit var syncer: ChatMessageSyncer

    @Before
    fun setUp() {
        syncer = ChatMessageSyncer(chatDao, chatBlocksDao, network, networkMonitor)
        whenever(networkMonitor.isOnline).thenReturn(MutableStateFlow(true))
    }

    @Test
    fun `buildFieldMap keeps push notifications when markNotificationsAsRead is false`() {
        val fieldMap = syncer.buildFieldMap(
            lookIntoFuture = true,
            timeout = 0,
            includeLastKnown = false,
            lastKnown = 42,
            markNotificationsAsRead = false
        )

        assertEquals(0, fieldMap["markNotificationsAsRead"])
        assertEquals(0, fieldMap["setReadMarker"])
        assertEquals(1, fieldMap["lookIntoFuture"])
        assertEquals(42, fieldMap["lastKnownMessageId"])
    }

    @Test
    fun `buildFieldMap omits markNotificationsAsRead by default`() {
        val fieldMap = syncer.buildFieldMap(
            lookIntoFuture = false,
            timeout = 0,
            includeLastKnown = true,
            lastKnown = null
        )

        assertFalse(fieldMap.containsKey("markNotificationsAsRead"))
        assertFalse(fieldMap.containsKey("lastKnownMessageId"))
        assertEquals(0, fieldMap["setReadMarker"])
        assertEquals(1, fieldMap["includeLastKnown"])
    }

    @Test
    fun `catchUpRoom skips when offline`() =
        runTest {
            whenever(networkMonitor.isOnline).thenReturn(MutableStateFlow(false))

            val outcome = syncer.catchUpRoom(target())

            assertFalse(outcome.persistedNewMessages)
            verifyNoInteractions(network)
        }

    @Test
    fun `catchUpRoom skips without chat-keep-notifications capability`() =
        runTest {
            val outcome = syncer.catchUpRoom(target(user(withKeepNotificationsCapability = false)))

            assertFalse(outcome.persistedNewMessages)
            verifyNoInteractions(network)
        }

    @Test
    fun `catchUpRoom delta-fetches from the newest cached message when a chat block exists`() =
        runTest {
            val existingBlock = block(oldest = 10, newest = 42)
            whenever(chatBlocksDao.getNewestMessageIdFromChatBlocks(INTERNAL_CONVERSATION_ID, null))
                .thenReturn(42L)
            whenever(chatBlocksDao.getChatBlocksContainingMessageId(INTERNAL_CONVERSATION_ID, null, 42L))
                .thenReturn(flowOf(listOf(existingBlock)))
            whenever(chatBlocksDao.getConnectedChatBlocks(eq(INTERNAL_CONVERSATION_ID), eq(null), any(), any()))
                .thenReturn(flowOf(listOf(existingBlock)))
            wheneverBlocking { network.pullChatMessages(any(), any(), any()) }
                .thenReturn(Response.success(overall(message(43), message(44))))

            val outcome = syncer.catchUpRoom(target())

            assertTrue(outcome.persistedNewMessages)
            assertEquals(44L, outcome.newestPersistedMessageId)

            val fieldMapCaptor = argumentCaptor<HashMap<String, Int>>()
            verifyBlocking(network) { pullChatMessages(eq(CREDENTIALS), eq(CHAT_URL), fieldMapCaptor.capture()) }
            assertEquals(1, fieldMapCaptor.firstValue["lookIntoFuture"])
            assertEquals(42, fieldMapCaptor.firstValue["lastKnownMessageId"])
            assertEquals(0, fieldMapCaptor.firstValue["markNotificationsAsRead"])

            val blockCaptor = argumentCaptor<ChatBlockEntity>()
            verifyBlocking(chatBlocksDao) { upsertChatBlock(blockCaptor.capture()) }
            assertEquals(10L, blockCaptor.firstValue.oldestMessageId)
            assertEquals(44L, blockCaptor.firstValue.newestMessageId)
        }

    @Test
    fun `catchUpRoom fetches the newest messages and creates the first block for a never-opened room`() =
        runTest {
            whenever(chatBlocksDao.getNewestMessageIdFromChatBlocks(INTERNAL_CONVERSATION_ID, null))
                .thenReturn(0L)
            whenever(chatBlocksDao.getConnectedChatBlocks(eq(INTERNAL_CONVERSATION_ID), eq(null), any(), any()))
                .thenReturn(flowOf(emptyList()))
            wheneverBlocking { network.pullChatMessages(any(), any(), any()) }
                .thenReturn(Response.success(overall(message(1), message(2), message(3))))

            val outcome = syncer.catchUpRoom(target())

            assertTrue(outcome.persistedNewMessages)
            assertEquals(3L, outcome.newestPersistedMessageId)

            val fieldMapCaptor = argumentCaptor<HashMap<String, Int>>()
            verifyBlocking(network) { pullChatMessages(eq(CREDENTIALS), eq(CHAT_URL), fieldMapCaptor.capture()) }
            assertEquals(0, fieldMapCaptor.firstValue["lookIntoFuture"])
            assertEquals(1, fieldMapCaptor.firstValue["includeLastKnown"])
            assertFalse(fieldMapCaptor.firstValue.containsKey("lastKnownMessageId"))
            assertEquals(0, fieldMapCaptor.firstValue["markNotificationsAsRead"])

            val blockCaptor = argumentCaptor<ChatBlockEntity>()
            verifyBlocking(chatBlocksDao) { upsertChatBlock(blockCaptor.capture()) }
            assertEquals(1L, blockCaptor.firstValue.oldestMessageId)
            assertEquals(3L, blockCaptor.firstValue.newestMessageId)
        }

    @Test
    fun `pullAndPersistMessages merges connected chat blocks`() =
        runTest {
            val connectedBlocks = listOf(block(oldest = 1, newest = 5), block(oldest = 3, newest = 44))
            whenever(chatBlocksDao.getChatBlocksContainingMessageId(INTERNAL_CONVERSATION_ID, null, 42L))
                .thenReturn(flowOf(listOf(connectedBlocks[1])))
            whenever(chatBlocksDao.getConnectedChatBlocks(eq(INTERNAL_CONVERSATION_ID), eq(null), any(), any()))
                .thenReturn(flowOf(connectedBlocks))
            wheneverBlocking { network.pullChatMessages(any(), any(), any()) }
                .thenReturn(Response.success(overall(message(43), message(44))))

            val fieldMap = syncer.buildFieldMap(
                lookIntoFuture = true,
                timeout = 0,
                includeLastKnown = false,
                lastKnown = 42
            )
            syncer.pullAndPersistMessages(target(), fieldMap)

            val mergedCaptor = argumentCaptor<ChatBlockEntity>()
            verifyBlocking(chatBlocksDao) { replaceConnectedChatBlocks(eq(connectedBlocks), mergedCaptor.capture()) }
            assertEquals(1L, mergedCaptor.firstValue.oldestMessageId)
            assertEquals(44L, mergedCaptor.firstValue.newestMessageId)
        }

    @Test
    fun `pullAndPersistMessages skips the block update when nothing was persisted`() =
        runTest {
            whenever(chatBlocksDao.getChatBlocksContainingMessageId(INTERNAL_CONVERSATION_ID, null, 42L))
                .thenReturn(flowOf(emptyList()))
            wheneverBlocking { network.pullChatMessages(any(), any(), any()) }
                .thenReturn(Response.success(overall(message(43))))
            wheneverBlocking { chatDao.upsertChatMessagesAndDeleteTemp(any(), any()) }
                .thenThrow(SQLiteConstraintException())

            val fieldMap = syncer.buildFieldMap(
                lookIntoFuture = true,
                timeout = 0,
                includeLastKnown = false,
                lastKnown = 42
            )
            val outcome = syncer.pullAndPersistMessages(target(), fieldMap)

            assertFalse(outcome.persistedNewMessages)
            assertNull(outcome.newestPersistedMessageId)
            verifyBlocking(chatBlocksDao, never()) { upsertChatBlock(any()) }
        }

    @Test
    fun `pullAndPersistMessages treats not modified as no new messages`() =
        runTest {
            val rawResponse = okhttp3.Response.Builder()
                .request(Request.Builder().url(CHAT_URL).build())
                .protocol(Protocol.HTTP_1_1)
                .code(HTTP_NOT_MODIFIED)
                .message("Not Modified")
                .build()
            wheneverBlocking { network.pullChatMessages(any(), any(), any()) }
                .thenReturn(Response.error("".toResponseBody(), rawResponse))
            whenever(chatBlocksDao.getChatBlocksContainingMessageId(INTERNAL_CONVERSATION_ID, null, 42L))
                .thenReturn(flowOf(emptyList()))

            val fieldMap = syncer.buildFieldMap(
                lookIntoFuture = true,
                timeout = 0,
                includeLastKnown = false,
                lastKnown = 42
            )
            val outcome = syncer.pullAndPersistMessages(target(), fieldMap)

            assertFalse(outcome.persistedNewMessages)
            verifyBlocking(chatDao, never()) { upsertChatMessagesAndDeleteTemp(any(), any()) }
        }

    @Test
    fun `cleanupExpiredMessages trims block boundaries and deletes empty blocks`() =
        runTest {
            val partiallyExpiredBlock = block(oldest = 1, newest = 10)
            val fullyExpiredBlock = block(oldest = 20, newest = 30)
            wheneverBlocking { chatDao.deleteExpiredMessages(eq(INTERNAL_CONVERSATION_ID), any()) }
                .thenReturn(2)
            wheneverBlocking { chatBlocksDao.getChatBlocksForConversation(INTERNAL_CONVERSATION_ID) }
                .thenReturn(listOf(partiallyExpiredBlock, fullyExpiredBlock))
            wheneverBlocking { chatDao.getNewestMessageIdInRange(INTERNAL_CONVERSATION_ID, null, 1L, 10L) }
                .thenReturn(8L)
            wheneverBlocking { chatDao.getOldestMessageIdInRange(INTERNAL_CONVERSATION_ID, null, 1L, 10L) }
                .thenReturn(2L)
            wheneverBlocking { chatDao.getNewestMessageIdInRange(INTERNAL_CONVERSATION_ID, null, 20L, 30L) }
                .thenReturn(null)

            syncer.cleanupExpiredMessages(INTERNAL_CONVERSATION_ID)

            val blockCaptor = argumentCaptor<ChatBlockEntity>()
            verifyBlocking(chatBlocksDao) { upsertChatBlock(blockCaptor.capture()) }
            assertEquals(2L, blockCaptor.firstValue.oldestMessageId)
            assertEquals(8L, blockCaptor.firstValue.newestMessageId)

            verify(chatBlocksDao).deleteChatBlocks(listOf(fullyExpiredBlock))
        }

    @Test
    fun `cleanupExpiredMessages leaves blocks untouched when nothing expired`() =
        runTest {
            wheneverBlocking { chatDao.deleteExpiredMessages(eq(INTERNAL_CONVERSATION_ID), any()) }
                .thenReturn(0)

            syncer.cleanupExpiredMessages(INTERNAL_CONVERSATION_ID)

            verifyNoInteractions(chatBlocksDao)
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
            baseUrl = "https://server.example.com",
            capabilities = Capabilities().apply {
                spreedCapability = SpreedCapability().apply { this.features = features }
            }
        )
    }

    private fun target(user: User = user()): ChatMessageSyncer.SyncTarget =
        ChatMessageSyncer.SyncTarget(
            user = user,
            roomToken = ROOM_TOKEN,
            threadId = null,
            credentials = CREDENTIALS,
            urlForChatting = CHAT_URL
        )

    private fun block(oldest: Long, newest: Long): ChatBlockEntity =
        ChatBlockEntity(
            internalConversationId = INTERNAL_CONVERSATION_ID,
            accountId = ACCOUNT_ID,
            token = ROOM_TOKEN,
            threadId = null,
            oldestMessageId = oldest,
            newestMessageId = newest,
            hasHistory = true
        )

    private fun message(id: Long): ChatMessageJson =
        ChatMessageJson(
            id = id,
            token = ROOM_TOKEN,
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

    companion object {
        private const val ACCOUNT_ID = 1L
        private const val ROOM_TOKEN = "room1"
        private const val INTERNAL_CONVERSATION_ID = "$ACCOUNT_ID@$ROOM_TOKEN"
        private const val CREDENTIALS = "credentials"
        private const val CHAT_URL = "https://server.example.com/ocs/v2.php/apps/spreed/api/v1/chat/$ROOM_TOKEN"
        private const val HTTP_NOT_MODIFIED = 304
    }
}
