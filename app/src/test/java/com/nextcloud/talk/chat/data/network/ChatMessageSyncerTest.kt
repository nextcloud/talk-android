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
import com.nextcloud.talk.data.database.dao.ConversationsDao
import com.nextcloud.talk.data.database.model.ChatBlockEntity
import com.nextcloud.talk.data.network.NetworkMonitor
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.capabilities.Capabilities
import com.nextcloud.talk.models.json.capabilities.SpreedCapability
import com.nextcloud.talk.models.json.chat.ChatMessageJson
import com.nextcloud.talk.models.json.chat.ChatOCS
import com.nextcloud.talk.models.json.chat.ChatOverall
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
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
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
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
    private val conversationsDao: ConversationsDao = mock()
    private val network: ChatNetworkDataSource = mock()
    private val networkMonitor: NetworkMonitor = mock()

    private lateinit var syncer: ChatMessageSyncer

    @Before
    fun setUp() {
        syncer = ChatMessageSyncer(chatDao, chatBlocksDao, conversationsDao, network, networkMonitor)
        whenever(networkMonitor.isOnline).thenReturn(MutableStateFlow(true))
        whenever(conversationsDao.getConversationForUser(any(), any())).thenReturn(flowOf(null))
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
    fun `catchUpRoom skips when offline and marks the sync as failed`() =
        runTest {
            whenever(networkMonitor.isOnline).thenReturn(MutableStateFlow(false))

            val outcome = syncer.catchUpRoom(target())

            assertFalse(outcome.persistedNewMessages)
            assertTrue(outcome.syncFailed)
            verifyNoInteractions(network)
        }

    @Test
    fun `catchUpRoom skips without chat-keep-notifications capability`() =
        runTest {
            val outcome = syncer.catchUpRoom(target(user(withKeepNotificationsCapability = false)))

            assertFalse(outcome.persistedNewMessages)
            // a missing capability is not retryable, so the skip must not count as failure
            assertFalse(outcome.syncFailed)
            verifyNoInteractions(network)
        }

    @Test
    fun `catchUpRoom marks the sync as failed when the server request errors`() =
        runTest {
            whenever(chatBlocksDao.getNewestMessageIdFromChatBlocks(INTERNAL_CONVERSATION_ID, null))
                .thenReturn(42L)
            wheneverBlocking { network.pullChatMessages(any(), any(), any()) }
                .thenReturn(Response.error(HTTP_INTERNAL_SERVER_ERROR, "".toResponseBody()))

            val outcome = syncer.catchUpRoom(target())

            assertFalse(outcome.persistedNewMessages)
            assertTrue(outcome.syncFailed)
        }

    @Test
    fun `catchUpRoom delta-fetches from the newest cached message when a chat block exists`() =
        runTest {
            val existingBlock = block(oldest = 10, newest = 42)
            whenever(chatBlocksDao.getNewestMessageIdFromChatBlocks(INTERNAL_CONVERSATION_ID, null))
                .thenReturn(42L)
            whenever(chatBlocksDao.getChatBlocksContainingMessageId(INTERNAL_CONVERSATION_ID, null, 42L))
                .thenReturn(flowOf(listOf(existingBlock)))
            wheneverBlocking { network.pullChatMessages(any(), any(), any()) }
                .thenReturn(Response.success(overall(message(43), message(44))))

            val outcome = syncer.catchUpRoom(target())

            assertTrue(outcome.persistedNewMessages)
            assertEquals(44L, outcome.newestPersistedMessageId)
            assertEquals(43L, outcome.oldestPersistedMessageId)
            assertEquals(2, outcome.persistedMessageCount)

            val fieldMapCaptor = argumentCaptor<HashMap<String, Int>>()
            verifyBlocking(network) { pullChatMessages(eq(CREDENTIALS), eq(CHAT_URL), fieldMapCaptor.capture()) }
            assertEquals(1, fieldMapCaptor.firstValue["lookIntoFuture"])
            assertEquals(42, fieldMapCaptor.firstValue["lastKnownMessageId"])
            assertEquals(0, fieldMapCaptor.firstValue["markNotificationsAsRead"])

            val blockCaptor = argumentCaptor<ChatBlockEntity>()
            verifyBlocking(chatBlocksDao) { upsertAndMergeConnectedChatBlocks(blockCaptor.capture()) }
            assertEquals(10L, blockCaptor.firstValue.oldestMessageId)
            assertEquals(44L, blockCaptor.firstValue.newestMessageId)
        }

    @Test
    fun `catchUpRoom fetches the newest messages and creates the first block for a never-opened room`() =
        runTest {
            whenever(chatBlocksDao.getNewestMessageIdFromChatBlocks(INTERNAL_CONVERSATION_ID, null))
                .thenReturn(0L)
            wheneverBlocking { network.pullChatMessages(any(), any(), any()) }
                .thenReturn(Response.success(overall(message(1), message(2), message(3))))

            val outcome = syncer.catchUpRoom(target())

            assertTrue(outcome.persistedNewMessages)
            assertEquals(3L, outcome.newestPersistedMessageId)
            assertEquals(1L, outcome.oldestPersistedMessageId)
            assertEquals(3, outcome.persistedMessageCount)

            val fieldMapCaptor = argumentCaptor<HashMap<String, Int>>()
            verifyBlocking(network) { pullChatMessages(eq(CREDENTIALS), eq(CHAT_URL), fieldMapCaptor.capture()) }
            assertEquals(0, fieldMapCaptor.firstValue["lookIntoFuture"])
            assertEquals(1, fieldMapCaptor.firstValue["includeLastKnown"])
            assertFalse(fieldMapCaptor.firstValue.containsKey("lastKnownMessageId"))
            assertEquals(0, fieldMapCaptor.firstValue["markNotificationsAsRead"])

            val blockCaptor = argumentCaptor<ChatBlockEntity>()
            verifyBlocking(chatBlocksDao) { upsertAndMergeConnectedChatBlocks(blockCaptor.capture()) }
            assertEquals(1L, blockCaptor.firstValue.oldestMessageId)
            assertEquals(3L, blockCaptor.firstValue.newestMessageId)
        }

    @Test
    fun `catchUpRoom anchors the initial fetch at the last read message for a large unread backlog`() =
        runTest {
            whenever(chatBlocksDao.getNewestMessageIdFromChatBlocks(INTERNAL_CONVERSATION_ID, null))
                .thenReturn(0L)
            whenever(chatBlocksDao.getChatBlocksContainingMessageId(eq(INTERNAL_CONVERSATION_ID), eq(null), any()))
                .thenReturn(flowOf(emptyList()))
            wheneverBlocking { network.pullChatMessages(any(), any(), any()) }
                .thenReturn(
                    // anchored page including the last read message 10 itself
                    Response.success(overall(message(10), message(11))),
                    // backlog rounds until the server returns fewer messages than the limit
                    Response.success(overall(message(12), message(13))),
                    Response.success(overall(message(14)))
                )

            val outcome = syncer.catchUpRoom(target(), limit = 2, lastReadMessage = 10, unreadMessages = 4)

            assertTrue(outcome.persistedNewMessages)
            assertEquals(5, outcome.persistedMessageCount)
            assertEquals(10L, outcome.oldestPersistedMessageId)
            assertEquals(14L, outcome.newestPersistedMessageId)

            val fieldMapCaptor = argumentCaptor<HashMap<String, Int>>()
            verifyBlocking(network, times(3)) { pullChatMessages(any(), any(), fieldMapCaptor.capture()) }
            val anchoredFieldMap = fieldMapCaptor.firstValue
            assertEquals(1, anchoredFieldMap["lookIntoFuture"])
            assertEquals(1, anchoredFieldMap["includeLastKnown"])
            assertEquals(10, anchoredFieldMap["lastKnownMessageId"])
            assertEquals(0, anchoredFieldMap["markNotificationsAsRead"])
        }

    @Test
    fun `catchUpRoom keeps the newest-messages fetch for a backlog smaller than one page`() =
        runTest {
            whenever(chatBlocksDao.getNewestMessageIdFromChatBlocks(INTERNAL_CONVERSATION_ID, null))
                .thenReturn(0L)
            whenever(chatBlocksDao.getChatBlocksContainingMessageId(eq(INTERNAL_CONVERSATION_ID), eq(null), any()))
                .thenReturn(flowOf(emptyList()))
            wheneverBlocking { network.pullChatMessages(any(), any(), any()) }
                .thenReturn(Response.success(overall(message(9), message(10), message(11))))

            syncer.catchUpRoom(target(), limit = 3, lastReadMessage = 10, unreadMessages = 1)

            val fieldMapCaptor = argumentCaptor<HashMap<String, Int>>()
            verifyBlocking(network) { pullChatMessages(any(), any(), fieldMapCaptor.capture()) }
            assertEquals(0, fieldMapCaptor.firstValue["lookIntoFuture"])
            assertEquals(1, fieldMapCaptor.firstValue["includeLastKnown"])
            assertFalse(fieldMapCaptor.firstValue.containsKey("lastKnownMessageId"))
        }

    @Test
    fun `catchUpRoom keeps the newest-messages fetch for a backlog too large to close`() =
        runTest {
            whenever(chatBlocksDao.getNewestMessageIdFromChatBlocks(INTERNAL_CONVERSATION_ID, null))
                .thenReturn(0L)
            whenever(chatBlocksDao.getChatBlocksContainingMessageId(eq(INTERNAL_CONVERSATION_ID), eq(null), any()))
                .thenReturn(flowOf(emptyList()))
            wheneverBlocking { network.pullChatMessages(any(), any(), any()) }
                .thenReturn(Response.success(overall(message(999), message(1000))))

            // MAX_BACKLOG_ROUNDS (5) * limit (2) = 10 closable messages, backlog is 11
            syncer.catchUpRoom(target(), limit = 2, lastReadMessage = 10, unreadMessages = 11)

            val fieldMapCaptor = argumentCaptor<HashMap<String, Int>>()
            verifyBlocking(network) { pullChatMessages(any(), any(), fieldMapCaptor.capture()) }
            assertEquals(0, fieldMapCaptor.firstValue["lookIntoFuture"])
            assertFalse(fieldMapCaptor.firstValue.containsKey("lastKnownMessageId"))
        }

    @Test
    fun `catchUpRoom coalesces a burst of calls for the same room into two fetches`() =
        runTest {
            val existingBlock = block(oldest = 10, newest = 42)
            whenever(chatBlocksDao.getNewestMessageIdFromChatBlocks(INTERNAL_CONVERSATION_ID, null))
                .thenReturn(42L)
            whenever(chatBlocksDao.getChatBlocksContainingMessageId(INTERNAL_CONVERSATION_ID, null, 42L))
                .thenReturn(flowOf(listOf(existingBlock)))

            val firstFetchStarted = CompletableDeferred<Unit>()
            val firstFetchReleased = CompletableDeferred<Unit>()
            var pullCount = 0
            wheneverBlocking { network.pullChatMessages(any(), any(), any()) }.doSuspendableAnswer {
                pullCount++
                if (pullCount == 1) {
                    firstFetchStarted.complete(Unit)
                    firstFetchReleased.await()
                }
                Response.success(overall(message(43), message(44)))
            }

            val firstCall = launch { syncer.catchUpRoom(target()) }
            firstFetchStarted.await()
            assertEquals("first fetch must be in flight", 1, pullCount)

            // a second and third call while the first fetch is in flight must not fetch in
            // parallel — they only mark a rerun for the running catch-up
            val secondOutcome = syncer.catchUpRoom(target())
            val thirdOutcome = syncer.catchUpRoom(target())
            assertFalse(secondOutcome.persistedNewMessages)
            assertFalse(thirdOutcome.persistedNewMessages)
            assertEquals(1, pullCount)

            firstFetchReleased.complete(Unit)
            firstCall.join()

            // the running catch-up re-fetched exactly once for the whole burst
            verifyBlocking(network, times(2)) { pullChatMessages(any(), any(), any()) }
        }

    @Test
    fun `pullAndPersistMessages extends the queried block and delegates the merge to the dao`() =
        runTest {
            val blockOfQueriedMessage = block(oldest = 3, newest = 44)
            whenever(chatBlocksDao.getChatBlocksContainingMessageId(INTERNAL_CONVERSATION_ID, null, 42L))
                .thenReturn(flowOf(listOf(blockOfQueriedMessage)))
            wheneverBlocking { network.pullChatMessages(any(), any(), any()) }
                .thenReturn(Response.success(overall(message(43), message(44))))

            val fieldMap = syncer.buildFieldMap(
                lookIntoFuture = true,
                timeout = 0,
                includeLastKnown = false,
                lastKnown = 42
            )
            syncer.pullAndPersistMessages(target(), fieldMap)

            // merging with other overlapping blocks happens atomically inside the dao, the syncer
            // only hands over the fetched range extended down to the queried block's oldest id
            val blockCaptor = argumentCaptor<ChatBlockEntity>()
            verifyBlocking(chatBlocksDao) { upsertAndMergeConnectedChatBlocks(blockCaptor.capture()) }
            assertEquals(3L, blockCaptor.firstValue.oldestMessageId)
            assertEquals(44L, blockCaptor.firstValue.newestMessageId)
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
            verifyBlocking(chatBlocksDao, never()) { upsertAndMergeConnectedChatBlocks(any()) }
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
    fun `tryCloseBacklog loops until the server returns fewer messages than the limit`() =
        runTest {
            val existingBlock = block(oldest = 10, newest = 42)
            whenever(chatBlocksDao.getChatBlocksContainingMessageId(eq(INTERNAL_CONVERSATION_ID), eq(null), any()))
                .thenReturn(flowOf(listOf(existingBlock)))
            wheneverBlocking { network.pullChatMessages(any(), any(), any()) }
                .thenReturn(
                    Response.success(overall(message(43), message(44))),
                    Response.success(overall(message(45)))
                )

            val outcome = syncer.tryCloseBacklog(target(), fromMessageId = 42, limit = 2)

            assertTrue(outcome.persistedNewMessages)
            assertEquals(3, outcome.persistedMessageCount)
            assertEquals(43L, outcome.oldestPersistedMessageId)
            assertEquals(45L, outcome.newestPersistedMessageId)

            val fieldMapCaptor = argumentCaptor<HashMap<String, Int>>()
            verifyBlocking(network, times(2)) { pullChatMessages(any(), any(), fieldMapCaptor.capture()) }
            assertEquals(42, fieldMapCaptor.firstValue["lastKnownMessageId"])
            assertEquals(44, fieldMapCaptor.secondValue["lastKnownMessageId"])
        }

    @Test
    fun `tryCloseBacklog falls back to the newest messages when the backlog persists`() =
        runTest {
            val existingBlock = block(oldest = 10, newest = 42)
            whenever(chatBlocksDao.getChatBlocksContainingMessageId(eq(INTERNAL_CONVERSATION_ID), eq(null), any()))
                .thenReturn(flowOf(listOf(existingBlock)))
            wheneverBlocking { network.pullChatMessages(any(), any(), any()) }
                .thenReturn(
                    Response.success(overall(message(43))),
                    Response.success(overall(message(44))),
                    Response.success(overall(message(45))),
                    Response.success(overall(message(46))),
                    Response.success(overall(message(47))),
                    Response.success(overall(message(100)))
                )

            val outcome = syncer.tryCloseBacklog(target(), fromMessageId = 42, limit = 1)

            // the reported range is the fallback's own — it must not be merged with the backlog
            // rounds' range, since the fallback lands in a separate, disconnected chat block
            assertTrue(outcome.persistedNewMessages)
            assertEquals(1, outcome.persistedMessageCount)
            assertEquals(100L, outcome.oldestPersistedMessageId)
            assertEquals(100L, outcome.newestPersistedMessageId)

            val fieldMapCaptor = argumentCaptor<HashMap<String, Int>>()
            verifyBlocking(network, times(6)) { pullChatMessages(any(), any(), fieldMapCaptor.capture()) }
            // the last request is the fallback: newest messages with includeLastKnown, no anchor
            val fallbackFieldMap = fieldMapCaptor.allValues.last()
            assertEquals(0, fallbackFieldMap["lookIntoFuture"])
            assertEquals(1, fallbackFieldMap["includeLastKnown"])
            assertFalse(fallbackFieldMap.containsKey("lastKnownMessageId"))
        }

    @Test
    fun `http pulls record the insurance anchor but signaling persists do not`() =
        runTest {
            val existingBlock = block(oldest = 10, newest = 42)
            whenever(chatBlocksDao.getChatBlocksContainingMessageId(INTERNAL_CONVERSATION_ID, null, 42L))
                .thenReturn(flowOf(listOf(existingBlock)))
            wheneverBlocking { network.pullChatMessages(any(), any(), any()) }
                .thenReturn(Response.success(overall(message(43), message(44))))

            val fieldMap = syncer.buildFieldMap(
                lookIntoFuture = true,
                timeout = 0,
                includeLastKnown = false,
                lastKnown = 42
            )
            syncer.pullAndPersistMessages(target(), fieldMap)

            assertEquals(44L, syncer.lastHttpSyncedMessageId(INTERNAL_CONVERSATION_ID, null))

            // a message delivered via signaling must NOT move the anchor: the insurance request
            // has to verify the signaling assumption and needs the last HTTP-synced id for that
            syncer.persistChatMessagesAndHandleSystemMessages(target(), listOf(message(50)))

            assertEquals(44L, syncer.lastHttpSyncedMessageId(INTERNAL_CONVERSATION_ID, null))
        }

    @Test
    fun `an empty lookIntoFuture response confirms the queried anchor`() =
        runTest {
            whenever(chatBlocksDao.getChatBlocksContainingMessageId(INTERNAL_CONVERSATION_ID, null, 42L))
                .thenReturn(flowOf(emptyList()))
            wheneverBlocking { network.pullChatMessages(any(), any(), any()) }
                .thenReturn(Response.success(overall()))

            val fieldMap = syncer.buildFieldMap(
                lookIntoFuture = true,
                timeout = 0,
                includeLastKnown = false,
                lastKnown = 42
            )
            syncer.pullAndPersistMessages(target(), fieldMap)

            assertEquals(42L, syncer.lastHttpSyncedMessageId(INTERNAL_CONVERSATION_ID, null))
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
        private const val HTTP_INTERNAL_SERVER_ERROR = 500
    }
}
