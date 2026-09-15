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
import com.nextcloud.talk.data.network.NetworkMonitor
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.logger.Logger
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.capabilities.Capabilities
import com.nextcloud.talk.models.json.capabilities.SpreedCapability
import com.nextcloud.talk.models.json.chat.ChatMessageJson
import com.nextcloud.talk.models.json.chat.ChatOCS
import com.nextcloud.talk.models.json.chat.ChatOverall
import com.nextcloud.talk.models.json.chat.ChatOverallSingleMessage
import com.nextcloud.talk.models.json.conversations.Conversation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
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
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

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

    private fun givenLatestBlock(block: ChatBlockEntity?) {
        whenever(chatBlocksDao.getLatestChatBlock(INTERNAL_CONVERSATION_ID, null))
            .thenReturn(flowOf(block))
    }

    private fun singleRequestFieldMap(): HashMap<String, Int> {
        val fieldMapCaptor = argumentCaptor<HashMap<String, Int>>()
        verifyBlocking(network) { pullChatMessages(eq(CREDENTIALS), eq(CHAT_URL), fieldMapCaptor.capture()) }
        return fieldMapCaptor.firstValue
    }

    @Test
    fun `deleteChatMessage marks the message as deleted before the server answers`() =
        runTest {
            val entity = messageEntity(MESSAGE_ID)
            givenCachedMessage(entity)
            val stateWhenAsked = mutableListOf<Pair<String, String>>()
            wheneverBlocking { network.deleteChatMessage(any(), any()) } doSuspendableAnswer {
                stateWhenAsked.add(entity.messageType to entity.message)
                deletedResponse()
            }

            val result = repository.deleteChatMessage(CREDENTIALS, MESSAGE_URL, MESSAGE_ID, DELETED_PLACEHOLDER)

            assertEquals(listOf("comment_deleted" to DELETED_PLACEHOLDER), stateWhenAsked)
            assertTrue(result.isSuccess)
            assertEquals("comment_deleted", entity.messageType)
            assertTrue(entity.deleted)
        }

    @Test
    fun `deleteChatMessage restores the message when the server rejects the deletion`() =
        runTest {
            val entity = messageEntity(MESSAGE_ID)
            givenCachedMessage(entity)
            wheneverBlocking { network.deleteChatMessage(any(), any()) } doSuspendableAnswer {
                throw httpException(HTTP_METHOD_NOT_ALLOWED)
            }

            val result = repository.deleteChatMessage(CREDENTIALS, MESSAGE_URL, MESSAGE_ID, DELETED_PLACEHOLDER)

            assertTrue(result.isFailure)
            assertEquals("comment", entity.messageType)
            assertEquals("message $MESSAGE_ID", entity.message)
            assertFalse(entity.deleted)
        }

    @Test
    fun `deleteChatMessage keeps the deletion when the server no longer knows the message`() =
        runTest {
            val entity = messageEntity(MESSAGE_ID)
            givenCachedMessage(entity)
            wheneverBlocking { network.deleteChatMessage(any(), any()) } doSuspendableAnswer {
                throw httpException(HTTP_NOT_FOUND)
            }

            val result = repository.deleteChatMessage(CREDENTIALS, MESSAGE_URL, MESSAGE_ID, DELETED_PLACEHOLDER)

            assertTrue(result.isSuccess)
            assertNull(result.getOrThrow())
            assertEquals("comment_deleted", entity.messageType)
        }

    @Test
    fun `deleteChatMessage retries a transient failure once before giving up`() =
        runTest {
            val entity = messageEntity(MESSAGE_ID)
            givenCachedMessage(entity)
            var attempts = 0
            wheneverBlocking { network.deleteChatMessage(any(), any()) } doSuspendableAnswer {
                attempts++
                if (attempts == 1) throw IOException("connection reset")
                deletedResponse()
            }

            val result = repository.deleteChatMessage(CREDENTIALS, MESSAGE_URL, MESSAGE_ID, DELETED_PLACEHOLDER)

            assertEquals(2, attempts)
            assertTrue(result.isSuccess)
            assertEquals("comment_deleted", entity.messageType)
        }

    private fun user(): User =
        User(
            id = ACCOUNT_ID,
            userId = "me",
            username = "me",
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

    @Test
    fun `deleting a shared file drops the attachment from the cached message`() =
        runTest {
            // a deleted message carries no file any more, so the bubble must stop rendering one
            val entity = messageEntity(MESSAGE_ID).apply {
                messageParameters = hashMapOf<String?, HashMap<String?, String?>>(
                    "file" to hashMapOf<String?, String?>("id" to "1")
                )
            }
            givenCachedMessage(entity)
            wheneverBlocking { network.deleteChatMessage(any(), any()) } doSuspendableAnswer { deletedResponse() }

            repository.deleteChatMessage(CREDENTIALS, MESSAGE_URL, MESSAGE_ID, DELETED_PLACEHOLDER)

            assertNull(entity.messageParameters)
        }

    @Test
    fun `a rejected deletion puts the attachment back`() =
        runTest {
            val parameters = hashMapOf<String?, HashMap<String?, String?>>(
                "file" to hashMapOf<String?, String?>("id" to "1")
            )
            val entity = messageEntity(MESSAGE_ID).apply { messageParameters = parameters }
            givenCachedMessage(entity)
            wheneverBlocking { network.deleteChatMessage(any(), any()) } doSuspendableAnswer {
                throw httpException(HTTP_METHOD_NOT_ALLOWED)
            }

            repository.deleteChatMessage(CREDENTIALS, MESSAGE_URL, MESSAGE_ID, DELETED_PLACEHOLDER)

            assertEquals(parameters, entity.messageParameters)
        }

    @Test
    fun `a deletion cancelled before it was answered puts the message back`() =
        runTest {
            // leaving the chat cancels the view model scope, which would otherwise skip every catch
            // and leave the message deleted locally although the server may never have heard of it
            val entity = messageEntity(MESSAGE_ID)
            givenCachedMessage(entity)
            val requestStarted = CompletableDeferred<Unit>()
            wheneverBlocking { network.deleteChatMessage(any(), any()) } doSuspendableAnswer {
                requestStarted.complete(Unit)
                awaitCancellation()
            }

            val deletion = launch {
                repository.deleteChatMessage(CREDENTIALS, MESSAGE_URL, MESSAGE_ID, DELETED_PLACEHOLDER)
            }
            requestStarted.await()
            deletion.cancelAndJoin()

            assertEquals("comment", entity.messageType)
            assertEquals("message $MESSAGE_ID", entity.message)
        }

    private fun messageEntity(id: Long): ChatMessageEntity =
        ChatMessageEntity(
            internalId = "$INTERNAL_CONVERSATION_ID@$id",
            internalConversationId = INTERNAL_CONVERSATION_ID,
            id = id,
            accountId = ACCOUNT_ID,
            token = ROOM_TOKEN,
            actorDisplayName = "Me",
            actorId = "me",
            actorType = "users",
            message = "message $id",
            messageType = "comment",
            systemMessageType = ChatMessage.SystemMessageType.DUMMY
        )

    private fun givenCachedMessage(entity: ChatMessageEntity) {
        wheneverBlocking { chatDao.getChatMessageEntity(eq(INTERNAL_CONVERSATION_ID), eq(entity.id)) }
            .thenReturn(entity)
    }

    private fun deletedResponse(): ChatOverallSingleMessage = ChatOverallSingleMessage(ocs = null)

    private fun httpException(code: Int) =
        HttpException(Response.error<Any>(code, "".toResponseBody("text/plain".toMediaType())))

    private fun overall(vararg messages: ChatMessageJson): ChatOverall =
        ChatOverall(ocs = ChatOCS(meta = null, data = messages.toList()))

    companion object {
        private const val ACCOUNT_ID = 1L
        private const val ROOM_TOKEN = "room1"
        private const val INTERNAL_CONVERSATION_ID = "$ACCOUNT_ID@$ROOM_TOKEN"
        private const val CREDENTIALS = "credentials"
        private const val CHAT_URL = "https://server.example.com/ocs/v2.php/apps/spreed/api/v1/chat/$ROOM_TOKEN"
        private const val MESSAGE_ID = 42L
        private const val MESSAGE_URL = "$CHAT_URL/42"
        private const val DELETED_PLACEHOLDER = "Message deleted by you"
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_METHOD_NOT_ALLOWED = 405
    }
}
