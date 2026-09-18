/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.data.network

import android.os.Bundle
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.conversationlist.data.network.ConversationListUpdater
import com.nextcloud.talk.data.database.dao.ChatBlocksDao
import com.nextcloud.talk.data.database.dao.ChatMessagesDao
import com.nextcloud.talk.data.database.dao.ConversationsDao
import com.nextcloud.talk.data.database.model.ChatBlockEntity
import com.nextcloud.talk.data.database.model.ChatMessageEntity
import com.nextcloud.talk.data.database.model.SendStatus
import com.nextcloud.talk.data.network.NetworkMonitor
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.logger.Logger
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.capabilities.Capabilities
import com.nextcloud.talk.models.json.capabilities.SpreedCapability
import com.nextcloud.talk.models.json.chat.ChatMessageJson
import com.nextcloud.talk.models.json.chat.ChatOCS
import com.nextcloud.talk.models.json.chat.ChatOverall
import com.nextcloud.talk.models.json.conversations.Conversation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking
import retrofit2.Response

/**
 * Covers the unread-boundary decisions of [OfflineFirstChatRepository.loadInitialMessages]: the
 * cached window (the latest chat block) is only trusted when it reaches back to the conversation's
 * last read message, otherwise the initial window is re-fetched — anchored at the boundary when
 * the unread backlog calls for it.
 */
@Suppress("TooManyFunctions")
class OfflineFirstChatRepositoryTest {

    private val logger: Logger = mock()
    private val chatDao: ChatMessagesDao = mock()
    private val chatBlocksDao: ChatBlocksDao = mock()
    private val conversationsDao: ConversationsDao = mock()
    private val network: ChatNetworkDataSource = mock()
    private val networkMonitor: NetworkMonitor = mock()
    private val conversationListUpdater = ConversationListUpdater(chatDao, chatBlocksDao, conversationsDao)

    private lateinit var repository: OfflineFirstChatRepository

    @Before
    fun setUp() {
        whenever(networkMonitor.isOnline).thenReturn(MutableStateFlow(true))
        wheneverBlocking { chatDao.deleteExpiredMessages(eq(INTERNAL_CONVERSATION_ID), any()) }.thenReturn(0)
        whenever(chatBlocksDao.getChatBlocksContainingMessageId(eq(INTERNAL_CONVERSATION_ID), eq(null), any()))
            .thenReturn(flowOf(emptyList()))

        repository = OfflineFirstChatRepository(
            logger,
            chatDao,
            chatBlocksDao,
            network,
            networkMonitor,
            ChatMessageSyncer(
                chatDao,
                chatBlocksDao,
                network,
                networkMonitor,
                ConversationListUpdater(chatDao, chatBlocksDao, conversationsDao)
            ),
            conversationListUpdater
        )
        repository.initData(user(), CREDENTIALS, CHAT_URL, ROOM_TOKEN, null)
    }

    @Test
    fun `markPendingReadMarker registers the marker synchronously, without any suspension`() {
        // Leaving the chat can race a room list sync triggered by the conversation list resuming
        // at (almost) the same time - that race is only guarded correctly if the pending marker
        // already exists by the time the sync's response is merged, so registering it must not
        // wait on updateLocalReadState's database reads. Calling it outside of runTest/any
        // coroutine proves no suspension is involved.
        repository.markPendingReadMarker(42)

        assertEquals(42, conversationListUpdater.pendingReadMarker(INTERNAL_CONVERSATION_ID))
    }

    @Test
    fun `loadInitialMessages closes the backlog when the latest block reaches the last read message`() =
        runTest {
            givenLatestBlock(block(oldest = 10, newest = 50))
            repository.updateConversation(conversation(lastReadMessage = 40, unreadMessages = 5))
            wheneverBlocking { network.pullChatMessages(any(), any(), any()) }
                .thenReturn(Response.success(overall(message(51))))

            repository.loadInitialMessages(Bundle())

            val fieldMap = singleRequestFieldMap()
            assertEquals(1, fieldMap["lookIntoFuture"])
            assertEquals(0, fieldMap["includeLastKnown"])
            assertEquals(50, fieldMap["lastKnownMessageId"])
        }

    @Test
    fun `loadInitialMessages treats a block without history below as reaching the boundary`() =
        runTest {
            givenLatestBlock(block(oldest = 45, newest = 50, hasHistory = false))
            repository.updateConversation(conversation(lastReadMessage = 40, unreadMessages = 5))
            wheneverBlocking { network.pullChatMessages(any(), any(), any()) }
                .thenReturn(Response.success(overall(message(51))))

            repository.loadInitialMessages(Bundle())

            val fieldMap = singleRequestFieldMap()
            assertEquals(1, fieldMap["lookIntoFuture"])
            assertEquals(50, fieldMap["lastKnownMessageId"])
        }

    @Test
    fun `loadInitialMessages anchors the repair fetch when the latest block floats above the boundary`() =
        runTest {
            givenLatestBlock(block(oldest = 100, newest = 199))
            repository.updateConversation(conversation(lastReadMessage = 40, unreadMessages = 160))
            wheneverBlocking { network.pullChatMessages(any(), any(), any()) }
                .thenReturn(Response.success(overall(message(40), message(41))))

            repository.loadInitialMessages(Bundle())

            val fieldMap = singleRequestFieldMap()
            assertEquals(1, fieldMap["lookIntoFuture"])
            assertEquals(1, fieldMap["includeLastKnown"])
            assertEquals(40, fieldMap["lastKnownMessageId"])
            // opening the chat clears notifications, so the fetch must not keep them
            assertFalse(fieldMap.containsKey("markNotificationsAsRead"))
        }

    @Test
    fun `loadInitialMessages anchors the initial fetch for an empty cache with a large backlog`() =
        runTest {
            givenLatestBlock(null)
            repository.updateConversation(conversation(lastReadMessage = 40, unreadMessages = 160))
            wheneverBlocking { network.pullChatMessages(any(), any(), any()) }
                .thenReturn(Response.success(overall(message(40), message(41))))

            repository.loadInitialMessages(Bundle())

            val fieldMap = singleRequestFieldMap()
            assertEquals(1, fieldMap["lookIntoFuture"])
            assertEquals(1, fieldMap["includeLastKnown"])
            assertEquals(40, fieldMap["lastKnownMessageId"])
        }

    @Test
    fun `loadInitialMessages fetches the newest messages for an empty cache with a small backlog`() =
        runTest {
            givenLatestBlock(null)
            repository.updateConversation(conversation(lastReadMessage = 40, unreadMessages = 5))
            wheneverBlocking { network.pullChatMessages(any(), any(), any()) }
                .thenReturn(Response.success(overall(message(44), message(45))))

            repository.loadInitialMessages(Bundle())

            val fieldMap = singleRequestFieldMap()
            assertEquals(0, fieldMap["lookIntoFuture"])
            assertEquals(1, fieldMap["includeLastKnown"])
            assertFalse(fieldMap.containsKey("lastKnownMessageId"))
        }

    @Test
    fun `fetchNewMessages skips the request when no HTTP-synced anchor exists yet`() =
        runTest {
            // a fresh ChatMessageSyncer has recorded no HTTP sync for any conversation yet, so
            // this is the state of a room whose initial load hasn't fetched anything so far —
            // anchoring at 0 would try to close the backlog from the start of its history instead
            val result = repository.fetchNewMessages()

            assertFalse(result)
            verifyBlocking(network, never()) { pullChatMessages(any(), any(), any()) }
        }

    @Test
    fun `addTemporaryMessage upserts a pending local echo and emits it on success`() =
        runTest {
            repository.updateConversation(conversation(lastReadMessage = 0, unreadMessages = 0))

            val result = repository.addTemporaryMessage(
                message = "hello",
                displayName = "Me",
                replyTo = 0,
                sendWithoutNotification = false,
                referenceId = "ref-1"
            ).toList().single()

            assertTrue(result.isSuccess)
            assertEquals("hello", result.getOrNull()?.message)
            assertEquals(SendStatus.PENDING, result.getOrNull()?.sendStatus)
            assertEquals(true, result.getOrNull()?.isTemporary)

            val entityCaptor = argumentCaptor<ChatMessageEntity>()
            verifyBlocking(chatDao) { upsertChatMessage(entityCaptor.capture()) }
            assertEquals(INTERNAL_CONVERSATION_ID, entityCaptor.firstValue.internalConversationId)
            assertEquals("ref-1", entityCaptor.firstValue.referenceId)
        }

    @Test
    fun `markMessageForResend resets a failed temp message back to PENDING without sending anything`() =
        runTest {
            val failedMessage = tempMessageEntity(referenceId = "ref-2", sendStatus = SendStatus.FAILED)
            whenever(chatDao.getTempMessageForConversation(INTERNAL_CONVERSATION_ID, "ref-2", null))
                .thenReturn(flowOf(failedMessage))

            val result = repository.markMessageForResend("ref-2").toList().single()

            assertTrue(result.isSuccess)
            assertEquals(SendStatus.PENDING, result.getOrNull()?.sendStatus)
            assertEquals(SendStatus.PENDING, failedMessage.sendStatus)
            verify(chatDao).updateChatMessage(failedMessage)
            // resending is only a local status reset - the actual send is left to SendMessageWorker
            verifyBlocking(network, never()) { sendChatMessage(any(), any(), any(), any(), any(), any(), any(), any()) }
        }

    @Test
    fun `markMessageForResend fails when no temp message exists for the reference id`() =
        runTest {
            whenever(chatDao.getTempMessageForConversation(INTERNAL_CONVERSATION_ID, "missing", null))
                .thenReturn(flowOf(null))

            val result = repository.markMessageForResend("missing").toList().single()

            assertTrue(result.isFailure)
            verify(chatDao, never()).updateChatMessage(any())
        }

    private fun tempMessageEntity(referenceId: String, sendStatus: SendStatus): ChatMessageEntity =
        ChatMessageEntity(
            internalId = "$INTERNAL_CONVERSATION_ID@_temp_$referenceId",
            accountId = ACCOUNT_ID,
            token = ROOM_TOKEN,
            internalConversationId = INTERNAL_CONVERSATION_ID,
            actorDisplayName = "Me",
            message = "hello",
            actorId = "me",
            actorType = "users",
            messageType = "comment",
            systemMessageType = ChatMessage.SystemMessageType.DUMMY,
            referenceId = referenceId,
            isTemporary = true,
            sendStatus = sendStatus
        )

    private fun givenLatestBlock(block: ChatBlockEntity?) {
        whenever(chatBlocksDao.getLatestChatBlock(INTERNAL_CONVERSATION_ID, null))
            .thenReturn(flowOf(block))
    }

    private fun singleRequestFieldMap(): HashMap<String, Int> {
        val fieldMapCaptor = argumentCaptor<HashMap<String, Int>>()
        verifyBlocking(network) { pullChatMessages(eq(CREDENTIALS), eq(CHAT_URL), fieldMapCaptor.capture()) }
        return fieldMapCaptor.firstValue
    }

    private fun user(): User =
        User(
            id = ACCOUNT_ID,
            userId = "me",
            username = "me",
            displayName = "Me",
            baseUrl = "https://server.example.com",
            capabilities = Capabilities().apply {
                spreedCapability = SpreedCapability().apply { features = listOf("chat-keep-notifications") }
            }
        )

    private fun conversation(lastReadMessage: Int, unreadMessages: Int): ConversationModel =
        ConversationModel.mapToConversationModel(
            Conversation(
                token = ROOM_TOKEN,
                lastReadMessage = lastReadMessage,
                unreadMessages = unreadMessages
            ),
            user()
        )

    private fun block(oldest: Long, newest: Long, hasHistory: Boolean = true): ChatBlockEntity =
        ChatBlockEntity(
            internalConversationId = INTERNAL_CONVERSATION_ID,
            accountId = ACCOUNT_ID,
            token = ROOM_TOKEN,
            threadId = null,
            oldestMessageId = oldest,
            newestMessageId = newest,
            hasHistory = hasHistory
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
    }
}
