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
import com.nextcloud.talk.data.database.mappers.asEntity
import com.nextcloud.talk.data.database.model.ChatMessageEntity
import com.nextcloud.talk.data.database.model.SendStatus
import com.nextcloud.talk.data.network.NetworkMonitor
import com.nextcloud.talk.data.source.local.TalkDatabase
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.data.user.model.UserEntity
import com.nextcloud.talk.logger.Logger
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.conversations.ConversationDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Upload placeholders against a real in-memory database. The worker is enqueued before the placeholder
 * exists, so a sync can store the server copy of the shared file first; the placeholder must then not
 * be stored, because [com.nextcloud.talk.data.database.dao.ChatMessagesDao.upsertChatMessagesAndDeleteTemp]
 * only drops temporary messages that exist at the time the server copy is written.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class OfflineFirstChatRepositoryUploadPlaceholderTest {

    private lateinit var db: TalkDatabase
    private lateinit var repository: OfflineFirstChatRepository

    private val logger: Logger = mock()
    private val network: ChatNetworkDataSource = mock()
    private val networkMonitor: NetworkMonitor = mock()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, TalkDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        runBlocking {
            db.usersDao().saveUser(UserEntity(id = ACCOUNT_ID, userId = "me", username = "me", baseUrl = BASE_URL))
            db.conversationsDao().upsertConversations(
                ACCOUNT_ID,
                listOf(ConversationDto(token = ROOM_TOKEN).asEntity(ACCOUNT_ID))
            )
        }
        whenever(networkMonitor.isOnline).thenReturn(MutableStateFlow(true))

        val conversationListUpdater =
            ConversationListUpdater(db.chatMessagesDao(), db.chatBlocksDao(), db.conversationsDao())
        repository = OfflineFirstChatRepository(
            logger,
            db.chatMessagesDao(),
            db.chatBlocksDao(),
            network,
            networkMonitor,
            ChatMessageSyncer(
                db.chatMessagesDao(),
                db.chatBlocksDao(),
                network,
                networkMonitor,
                conversationListUpdater
            ),
            conversationListUpdater
        )
        repository.initData(user(), "credentials", CHAT_URL, ROOM_TOKEN, null)
        repository.updateConversation(
            ConversationModel.mapToConversationModel(ConversationDto(token = ROOM_TOKEN), user())
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `placeholder is not stored when the server copy of the file message already exists`() =
        runBlocking {
            // the sync stored the shared file before the placeholder insert ran
            db.chatMessagesDao().upsertChatMessagesAndDeleteTemp(
                INTERNAL_CONVERSATION_ID,
                listOf(serverMessage(SERVER_MESSAGE_ID, REFERENCE_ID))
            )

            val results = addPlaceholder(REFERENCE_ID)

            assertTrue(results.isEmpty())
            assertNull(tempMessage(REFERENCE_ID))
            val stored = db.chatMessagesDao().getChatMessageEntity(INTERNAL_CONVERSATION_ID, SERVER_MESSAGE_ID)
            assertNotNull(stored)
            assertEquals(false, stored!!.isTemporary)
        }

    @Test
    fun `placeholder is stored pending when no server copy exists`() =
        runBlocking {
            val results = addPlaceholder(REFERENCE_ID)

            assertTrue(results.isEmpty())
            val placeholder = tempMessage(REFERENCE_ID)
            assertNotNull(placeholder)
            assertEquals(SendStatus.PENDING, placeholder!!.sendStatus)
        }

    @Test
    fun `server copy of another file does not block the placeholder`() =
        runBlocking {
            db.chatMessagesDao().upsertChatMessagesAndDeleteTemp(
                INTERNAL_CONVERSATION_ID,
                listOf(serverMessage(SERVER_MESSAGE_ID, OTHER_REFERENCE_ID))
            )

            addPlaceholder(REFERENCE_ID)

            assertNotNull(tempMessage(REFERENCE_ID))
        }

    @Test
    fun `server copy arriving after the placeholder still removes it`() =
        runBlocking {
            addPlaceholder(REFERENCE_ID)
            assertNotNull(tempMessage(REFERENCE_ID))

            db.chatMessagesDao().upsertChatMessagesAndDeleteTemp(
                INTERNAL_CONVERSATION_ID,
                listOf(serverMessage(SERVER_MESSAGE_ID, REFERENCE_ID))
            )

            assertNull(tempMessage(REFERENCE_ID))
            assertNotNull(db.chatMessagesDao().getChatMessageEntity(INTERNAL_CONVERSATION_ID, SERVER_MESSAGE_ID))
        }

    @Test
    fun `marking a failed upload does nothing when no placeholder exists`() =
        runBlocking {
            db.chatMessagesDao().upsertChatMessagesAndDeleteTemp(
                INTERNAL_CONVERSATION_ID,
                listOf(serverMessage(SERVER_MESSAGE_ID, REFERENCE_ID))
            )
            addPlaceholder(REFERENCE_ID)

            repository.markUploadPlaceholderFailed(REFERENCE_ID)

            assertNull(tempMessage(REFERENCE_ID))
            val stored = db.chatMessagesDao().getChatMessageEntity(INTERNAL_CONVERSATION_ID, SERVER_MESSAGE_ID)
            assertEquals(false, stored!!.isTemporary)
            assertNull(stored.sendStatus)
        }

    private suspend fun addPlaceholder(referenceId: String) =
        repository.addUploadPlaceholderMessage(
            localFileUri = "content://provider/file",
            fileName = "photo.jpg",
            caption = "",
            mimeType = "image/jpeg",
            fileSize = FILE_SIZE,
            referenceId = referenceId
        ).toList()

    private suspend fun tempMessage(referenceId: String): ChatMessageEntity? =
        db.chatMessagesDao().getTempMessageForConversation(INTERNAL_CONVERSATION_ID, referenceId, null).first()

    private fun serverMessage(id: Long, referenceId: String): ChatMessageEntity =
        ChatMessageEntity(
            internalId = "$INTERNAL_CONVERSATION_ID@$id",
            internalConversationId = INTERNAL_CONVERSATION_ID,
            id = id,
            accountId = ACCOUNT_ID,
            token = ROOM_TOKEN,
            actorDisplayName = "Me",
            actorId = "me",
            actorType = "users",
            message = "{file}",
            messageType = "comment",
            systemMessageType = ChatMessage.SystemMessageType.DUMMY,
            referenceId = referenceId
        )

    private fun user(): User =
        User(
            id = ACCOUNT_ID,
            userId = "me",
            username = "me",
            displayName = "Me",
            baseUrl = BASE_URL
        )

    companion object {
        private const val ACCOUNT_ID = 1L
        private const val ROOM_TOKEN = "room1"
        private const val INTERNAL_CONVERSATION_ID = "$ACCOUNT_ID@$ROOM_TOKEN"
        private const val BASE_URL = "https://server.example.com"
        private const val CHAT_URL = "$BASE_URL/ocs/v2.php/apps/spreed/api/v1/chat/$ROOM_TOKEN"
        private const val REFERENCE_ID = "ref-file-1"
        private const val OTHER_REFERENCE_ID = "ref-file-2"
        private const val SERVER_MESSAGE_ID = 100L
        private const val FILE_SIZE = 1024L
    }
}
