/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.data.database.dao

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.data.database.mappers.asEntity
import com.nextcloud.talk.data.database.model.ChatMessageEntity
import com.nextcloud.talk.data.source.local.TalkDatabase
import com.nextcloud.talk.data.user.model.UserEntity
import com.nextcloud.talk.models.json.chat.ChatMessageDto
import com.nextcloud.talk.models.json.conversations.ConversationDto
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The reply count shown on a thread root is taken from the cached root message, while the server sends the
 * thread's current count with every message of the thread.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class ChatMessagesDaoThreadRepliesTest {

    private lateinit var db: TalkDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, TalkDatabase::class.java).build()
        runBlocking {
            db.usersDao().saveUser(UserEntity(id = ACCOUNT_ID, userId = "me", username = "me", baseUrl = BASE_URL))
            db.conversationsDao().upsertConversations(
                ACCOUNT_ID,
                listOf(ConversationDto(token = ROOM_TOKEN).asEntity(ACCOUNT_ID))
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `a new reply updates the reply count of the cached thread root`() =
        runBlocking {
            store(threadMessage(id = ROOT_ID, threadReplies = 1))

            store(threadMessage(id = ROOT_ID + 2, threadReplies = 2))

            assertEquals(2, threadRepliesOfRoot())
        }

    @Test
    fun `the newest message of a thread decides the reply count`() =
        runBlocking {
            store(threadMessage(id = ROOT_ID, threadReplies = 1))

            store(
                threadMessage(id = ROOT_ID + 3, threadReplies = 3),
                threadMessage(id = ROOT_ID + 2, threadReplies = 2)
            )

            assertEquals(3, threadRepliesOfRoot())
        }

    @Test
    fun `a message outside of a thread leaves the reply count alone`() =
        runBlocking {
            store(threadMessage(id = ROOT_ID, threadReplies = 1))

            store(threadMessage(id = ROOT_ID + 2, threadReplies = 0).copy(isThread = false, threadId = ROOT_ID + 2))

            assertEquals(1, threadRepliesOfRoot())
        }

    @Test
    fun `the reply counts of several threads are written into their cached roots`() =
        runBlocking {
            store(threadMessage(id = ROOT_ID, threadReplies = 1))

            db.chatMessagesDao().updateThreadRepliesOfThreads(
                INTERNAL_CONVERSATION_ID,
                mapOf(ROOT_ID to 23, ROOT_ID + 1000 to 5)
            )

            assertEquals(23, threadRepliesOfRoot())
        }

    private suspend fun store(vararg messages: ChatMessageEntity) {
        db.chatMessagesDao().upsertChatMessagesAndDeleteTemp(INTERNAL_CONVERSATION_ID, messages.toList())
    }

    private suspend fun threadRepliesOfRoot(): Int? =
        db.chatMessagesDao().getChatMessageEntity(INTERNAL_CONVERSATION_ID, ROOT_ID)?.threadReplies

    private fun threadMessage(id: Long, threadReplies: Int) =
        ChatMessageDto(
            id = id,
            token = ROOM_TOKEN,
            actorType = "users",
            actorId = "other",
            actorDisplayName = "Other",
            message = "message $id",
            messageType = "comment",
            systemMessageType = ChatMessage.SystemMessageType.DUMMY,
            threadId = ROOT_ID,
            hasThread = true,
            threadReplies = threadReplies
        ).asEntity(ACCOUNT_ID)

    companion object {
        private const val ACCOUNT_ID = 1L
        private const val ROOM_TOKEN = "room1"
        private const val INTERNAL_CONVERSATION_ID = "$ACCOUNT_ID@$ROOM_TOKEN"
        private const val BASE_URL = "https://server.example.com"
        private const val ROOT_ID = 100L
    }
}
