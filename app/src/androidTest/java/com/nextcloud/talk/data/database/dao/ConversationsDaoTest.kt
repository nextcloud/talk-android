/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.data.database.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nextcloud.talk.data.database.model.ConversationEntity
import com.nextcloud.talk.data.source.local.TalkDatabase
import com.nextcloud.talk.data.user.UsersDao
import com.nextcloud.talk.data.user.model.UserEntity
import com.nextcloud.talk.models.MessageDraft
import com.nextcloud.talk.models.json.conversations.ConversationEnums
import com.nextcloud.talk.models.json.participants.Participant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConversationsDaoTest {
    private lateinit var usersDao: UsersDao
    private lateinit var conversationsDao: ConversationsDao
    private lateinit var db: TalkDatabase
    private var accountId: Long = 0

    @Before
    fun createDb() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            db = Room.inMemoryDatabaseBuilder(
                context,
                TalkDatabase::class.java
            ).allowMainThreadQueries().build()
            usersDao = db.usersDao()
            conversationsDao = db.conversationsDao()

            val user = UserEntity(userId = "user1", username = "User 1")
            accountId = usersDao.saveUser(user)
        }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertAndGetConversation() =
        runTest {
            val conversation = createConversationEntity(accountId, "token1", "Conversation 1")
            conversationsDao.insertConversation(conversation)

            val retrieved = conversationsDao.getConversationForUser(accountId, "token1").first()
            assertEquals("Conversation 1", retrieved?.name)
        }

    @Test
    fun updateConversation() =
        runTest {
            val conversation = createConversationEntity(accountId, "token1", "Conversation 1")
            conversationsDao.insertConversation(conversation)

            conversation.name = "Updated Name"
            conversationsDao.updateConversation(conversation)

            val retrieved = conversationsDao.getConversationForUser(accountId, "token1").first()
            assertEquals("Updated Name", retrieved?.name)
        }

    @Test
    fun upsertConversations_mergesLocalOnlyFields() =
        runTest {
            val conversation = createConversationEntity(accountId, "token1", "Conversation 1")
            val draft = MessageDraft("Draft message", 5)
            conversation.messageDraft = draft
            conversation.hiddenUpcomingEvent = "event1"
            conversationsDao.insertConversation(conversation)

            val serverConversation = createConversationEntity(accountId, "token1", "Server Name")
            // Server version won't have the local-only fields (they'll be default/null)
            serverConversation.messageDraft = MessageDraft()
            serverConversation.hiddenUpcomingEvent = null

            conversationsDao.upsertConversations(accountId, listOf(serverConversation))

            val retrieved = conversationsDao.getConversationForUser(accountId, "token1").first()
            assertEquals("Server Name", retrieved?.name)
            assertEquals("Draft message", retrieved?.messageDraft?.messageText)
            assertEquals("event1", retrieved?.hiddenUpcomingEvent)
        }

    @Test
    fun deleteConversations() =
        runTest {
            val conv1 = createConversationEntity(accountId, "token1", "Conv 1")
            val conv2 = createConversationEntity(accountId, "token2", "Conv 2")
            conversationsDao.insertConversation(conv1)
            conversationsDao.insertConversation(conv2)

            conversationsDao.deleteConversations(listOf(conv1.internalId))

            val list = conversationsDao.getConversationsForUser(accountId).first()
            assertEquals(1, list.size)
            assertEquals("token2", list[0].token)
        }

    @Test
    fun clearAllConversationsForUser() =
        runTest {
            val conv1 = createConversationEntity(accountId, "token1", "Conv 1")
            conversationsDao.insertConversation(conv1)

            conversationsDao.clearAllConversationsForUser(accountId)

            val list = conversationsDao.getConversationsForUser(accountId).first()
            assertTrue(list.isEmpty())
        }

    private fun createConversationEntity(accountId: Long, token: String, roomName: String) =
        ConversationEntity(
            internalId = "$accountId@$token",
            accountId = accountId,
            token = token,
            name = roomName,
            displayName = roomName,
            actorId = "",
            actorType = "",
            avatarVersion = "",
            canDeleteConversation = false,
            canLeaveConversation = false,
            description = "",
            lobbyState = ConversationEnums.LobbyState.LOBBY_STATE_MODERATORS_ONLY,
            notificationLevel = ConversationEnums.NotificationLevel.ALWAYS,
            objectType = ConversationEnums.ObjectType.FILE,
            objectId = "",
            participantType = Participant.ParticipantType.DUMMY,
            conversationReadOnlyState = ConversationEnums.ConversationReadOnlyState.CONVERSATION_READ_WRITE,
            sessionId = "",
            type = ConversationEnums.ConversationType.DUMMY,
            unreadMentionDirect = false,
            hasCustomAvatar = false
        )
}
