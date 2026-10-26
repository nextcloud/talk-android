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
import com.nextcloud.talk.conversationlist.data.network.ConversationListUpdater
import com.nextcloud.talk.data.database.dao.ChatMessagesDao
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A PENDING message without send work is repaired to FAILED. The rows are written by the real
 * insert path of [OfflineFirstChatRepository] into a Room database, because the repair filter
 * depends on how Room stores the (absent) message parameters.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class PendingMessageRepairIntegrationTest {

    private lateinit var db: TalkDatabase
    private lateinit var dao: ChatMessagesDao
    private lateinit var repository: OfflineFirstChatRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, TalkDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.chatMessagesDao()
        runBlocking {
            db.usersDao().saveUser(UserEntity(id = ACCOUNT_ID, userId = "me", username = "me", baseUrl = BASE_URL))
            // the messages of a conversation reference its row
            db.conversationsDao().insertConversation(ConversationDto(token = ROOM_TOKEN).asEntity(ACCOUNT_ID))
        }

        val networkMonitor: NetworkMonitor = mock()
        whenever(networkMonitor.isOnline).thenReturn(MutableStateFlow(true))
        val updater = ConversationListUpdater(dao, db.chatBlocksDao(), db.conversationsDao())
        repository = OfflineFirstChatRepository(
            mock<Logger>(),
            dao,
            db.chatBlocksDao(),
            mock(),
            networkMonitor,
            ChatMessageSyncer(dao, db.chatBlocksDao(), mock(), networkMonitor, updater),
            updater
        )
        val user = User(id = ACCOUNT_ID, userId = "me", username = "me", displayName = "Me", baseUrl = BASE_URL)
        repository.initData(user, "credentials", "$BASE_URL/chat", ROOM_TOKEN, null)
        repository.updateConversation(
            ConversationModel.mapToConversationModel(ConversationDto(token = ROOM_TOKEN), user)
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `a pending text message is failed and an upload placeholder is not`() {
        runBlocking {
            repository.addTemporaryMessage("hello", "Me", 0, false, TEXT_REFERENCE).first()
            repository.addUploadPlaceholderMessage("content://file", "a.jpg", "", "image/jpeg", 1L, UPLOAD_REFERENCE)
                .toList()

            val changed = repository.failPendingMessagesWithoutSendWork(createdBefore = Long.MAX_VALUE)

            assertEquals(1, changed)
            assertEquals(SendStatus.FAILED, row(TEXT_REFERENCE).sendStatus)
            assertEquals(SendStatus.PENDING, row(UPLOAD_REFERENCE).sendStatus)
        }
    }

    @Test
    fun `a message created after the cut-off is not touched`() {
        runBlocking {
            repository.addTemporaryMessage("hello", "Me", 0, false, TEXT_REFERENCE).first()
            val createdAt = row(TEXT_REFERENCE).timestamp

            assertEquals(0, repository.failPendingMessagesWithoutSendWork(createdBefore = createdAt))
            assertEquals(SendStatus.PENDING, row(TEXT_REFERENCE).sendStatus)

            assertEquals(1, repository.failPendingMessagesWithoutSendWork(createdBefore = createdAt + 1))
            assertEquals(SendStatus.FAILED, row(TEXT_REFERENCE).sendStatus)
        }
    }

    @Test
    fun `messages that were sent or already failed keep their status`() {
        runBlocking {
            repository.addTemporaryMessage("sent", "Me", 0, false, TEXT_REFERENCE).first()
            dao.updateTempMessageSendStatus(CONVERSATION_ID, TEXT_REFERENCE, SendStatus.SENT_PENDING_ACK)

            assertEquals(0, repository.failPendingMessagesWithoutSendWork(createdBefore = Long.MAX_VALUE))
            assertEquals(SendStatus.SENT_PENDING_ACK, row(TEXT_REFERENCE).sendStatus)
        }
    }

    @Test
    fun `writing the send status keeps an edited text and writing the text keeps the send status`() {
        runBlocking {
            repository.addTemporaryMessage("before", "Me", 0, false, TEXT_REFERENCE).first()
            val id = row(TEXT_REFERENCE).id

            dao.updateMessageText(CONVERSATION_ID, id, "edited")
            dao.updateTempMessageSendStatus(CONVERSATION_ID, TEXT_REFERENCE, SendStatus.FAILED)
            dao.updateMessageText(CONVERSATION_ID, id, "edited again")

            val row = row(TEXT_REFERENCE)
            assertEquals("edited again", row.message)
            assertEquals(SendStatus.FAILED, row.sendStatus)
        }
    }

    private suspend fun row(referenceId: String): ChatMessageEntity =
        dao.getTempMessageForConversation(CONVERSATION_ID, referenceId, null).first()!!

    companion object {
        private const val ACCOUNT_ID = 1L
        private const val BASE_URL = "https://server.example.com"
        private const val ROOM_TOKEN = "room"
        private const val CONVERSATION_ID = "1@room"
        private const val TEXT_REFERENCE = "ref-text"
        private const val UPLOAD_REFERENCE = "ref-upload"
    }
}
