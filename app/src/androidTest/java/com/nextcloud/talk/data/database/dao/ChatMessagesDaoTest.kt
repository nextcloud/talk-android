/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.data.database.dao

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.runner.AndroidJUnit4
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.data.database.model.ChatMessageEntity
import com.nextcloud.talk.data.database.model.ConversationEntity
import com.nextcloud.talk.data.source.local.TalkDatabase
import com.nextcloud.talk.data.user.UsersDao
import com.nextcloud.talk.data.user.model.UserEntity
import com.nextcloud.talk.models.json.conversations.ConversationEnums
import com.nextcloud.talk.models.json.participants.Participant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatMessagesDaoTest {

    private lateinit var usersDao: UsersDao
    private lateinit var conversationsDao: ConversationsDao
    private lateinit var chatMessagesDao: ChatMessagesDao
    private lateinit var db: TalkDatabase
    private val tag = ChatMessagesDaoTest::class.java.simpleName

    var chatMessageCounter: Long = 1

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context,
            TalkDatabase::class.java
        ).build()
        usersDao = db.usersDao()
        conversationsDao = db.conversationsDao()
        chatMessagesDao = db.chatMessagesDao()
    }

    @After
    fun closeDb() = db.close()

    @Test
    fun test() =
        runTest {
            usersDao.saveUser(createUserEntity("account1", "Account 1"))
            usersDao.saveUser(createUserEntity("account2", "Account 2"))

            val account1 = usersDao.getUserWithUserId("account1").blockingGet()
            val account2 = usersDao.getUserWithUserId("account2").blockingGet()

            // Problem: lets say we want to update the conv list -> We don#t know the primary keys!
            // with account@token that would be easier!
            conversationsDao.upsertConversations(
                account1.id,
                listOf(
                    createConversationEntity(
                        accountId = account1.id,
                        roomName = "Conversation One"
                    ),
                    createConversationEntity(
                        accountId = account1.id,
                        roomName = "Conversation Two"
                    ),
                    createConversationEntity(
                        accountId = account2.id,
                        roomName = "Conversation Three"
                    )
                )
            )

            assertEquals(2, conversationsDao.getConversationsForUser(account1.id).first().size)
            assertEquals(1, conversationsDao.getConversationsForUser(account2.id).first().size)

            // Lets imagine we are on conversations screen...
            conversationsDao.getConversationsForUser(account1.id).first().forEach {
                Log.d(tag, "- next Conversation for account1 -")
                Log.d(tag, "internalId (PK): " + it.internalId)
                Log.d(tag, "accountId: " + it.accountId)
                Log.d(tag, "name: " + it.name)
                Log.d(tag, "token: " + it.token)
            }

            // User sees all conversations and clicks on a item. That's how we get a conversation
            val conversation1 = conversationsDao.getConversationsForUser(account1.id).first()[0]
            val conversation2 = conversationsDao.getConversationsForUser(account1.id).first()[1]

            // Having a conversation token, we can also get a conversation directly
            val conversation1GotByToken = conversationsDao.getConversationForUser(
                account1.id,
                conversation1.token!!
            ).first()

            assertEquals(conversation1, conversation1GotByToken)

            // Lets insert some messages to the conversations
            chatMessagesDao.upsertChatMessages(
                listOf(
                    createChatMessageEntity(conversation1.internalId, "hello"),
                    createChatMessageEntity(conversation1.internalId, "here"),
                    createChatMessageEntity(conversation1.internalId, "are"),
                    createChatMessageEntity(conversation1.internalId, "some"),
                    createChatMessageEntity(conversation1.internalId, "messages")
                )
            )
            chatMessagesDao.upsertChatMessages(
                listOf(
                    createChatMessageEntity(conversation2.internalId, "first message in conversation 2")
                )
            )

            chatMessagesDao.getMessagesForConversation(conversation1.internalId).first().forEach {
                Log.d(tag, "- next Message for conversation1 (account1)-")
                Log.d(tag, "id (PK): " + it.id)
                Log.d(tag, "message: " + it.message)
            }

            val chatMessagesConv1 = chatMessagesDao.getMessagesForConversation(conversation1.internalId)
            assertEquals(5, chatMessagesConv1.first().size)

            val chatMessagesConv2 = chatMessagesDao.getMessagesForConversation(conversation2.internalId)
            assertEquals(1, chatMessagesConv2.first().size)

            assertEquals("some", chatMessagesConv1.first()[1].message)

            val conv1chatMessage3 = chatMessagesDao.getChatMessageForConversation(conversation1.internalId, 3).first()
            assertEquals("are", conv1chatMessage3.message)

            val chatMessagesConv1Since =
                chatMessagesDao.getMessagesForConversationSince(
                    conversation1.internalId,
                    conv1chatMessage3.id,
                    null
                )
            assertEquals(3, chatMessagesConv1Since.first().size)
            assertEquals("are", chatMessagesConv1Since.first()[0].message)
            assertEquals("some", chatMessagesConv1Since.first()[1].message)
            assertEquals("messages", chatMessagesConv1Since.first()[2].message)

            val chatMessagesConv1To =
                chatMessagesDao.getMessagesForConversationBeforeAndEqual(
                    conversation1.internalId,
                    conv1chatMessage3.id,
                    3,
                    null
                )
            assertEquals(3, chatMessagesConv1To.first().size)
            assertEquals("hello", chatMessagesConv1To.first()[2].message)
            assertEquals("here", chatMessagesConv1To.first()[1].message)
            assertEquals("are", chatMessagesConv1To.first()[0].message)
        }

    private fun createUserEntity(userId: String, userName: String) =
        UserEntity(
            userId = userId,
            username = userName,
            baseUrl = null,
            token = null,
            displayName = null,
            pushConfigurationState = null,
            capabilities = null,
            serverVersion = null,
            clientCertificate = null,
            externalSignalingServer = null,
            current = java.lang.Boolean.FALSE,
            scheduledForDeletion = java.lang.Boolean.FALSE
        )

    private fun createConversationEntity(accountId: Long, roomName: String): ConversationEntity {
        val token = (0..10000000).random().toString()

        return ConversationEntity(
            internalId = "$accountId@$token",
            accountId = accountId,
            token = token,
            name = roomName,
            actorId = "",
            actorType = "",
            messageExpiration = 0,
            unreadMessages = 0,
            statusMessage = null,
            lastMessage = null,
            canDeleteConversation = false,
            canLeaveConversation = false,
            lastCommonReadMessage = 0,
            lastReadMessage = 0,
            type = ConversationEnums.ConversationType.DUMMY,
            status = "",
            callFlag = 1,
            favorite = false,
            lastPing = 0,
            hasCall = false,
            sessionId = "",
            canStartCall = false,
            lastActivity = 0,
            remoteServer = "",
            avatarVersion = "",
            unreadMentionDirect = false,
            callRecording = 1,
            callStartTime = 0,
            statusClearAt = 0,
            unreadMention = false,
            lobbyState = ConversationEnums.LobbyState.LOBBY_STATE_MODERATORS_ONLY,
            lobbyTimer = 0,
            objectType = ConversationEnums.ObjectType.FILE,
            objectId = "",
            statusIcon = null,
            description = "",
            displayName = "",
            hasPassword = false,
            permissions = 0,
            notificationCalls = 0,
            remoteToken = "",
            notificationLevel = ConversationEnums.NotificationLevel.ALWAYS,
            conversationReadOnlyState = ConversationEnums.ConversationReadOnlyState.CONVERSATION_READ_ONLY,
            hasCustomAvatar = false,
            participantType = Participant.ParticipantType.DUMMY,
            recordingConsentRequired = 1
        )
    }

    private fun createChatMessageEntity(internalConversationId: String, message: String): ChatMessageEntity {
        val id = chatMessageCounter++

        val emoji1 = "\uD83D\uDE00" // 😀
        val emoji2 = "\uD83D\uDE1C" // 😜
        val reactions = LinkedHashMap<String, Int>()
        reactions[emoji1] = 3
        reactions[emoji2] = 4

        val reactionsSelf = ArrayList<String>()
        reactionsSelf.add(emoji1)

        val entity = ChatMessageEntity(
            internalId = "$internalConversationId@$id",
            internalConversationId = internalConversationId,
            id = id,
            message = message,
            reactions = reactions,
            reactionsSelf = reactionsSelf,
            deleted = false,
            token = "",
            actorId = "",
            actorType = "",
            accountId = 1,
            messageParameters = null,
            messageType = "",
            parentMessageId = null,
            systemMessageType = ChatMessage.SystemMessageType.DUMMY,
            replyable = false,
            timestamp = 0,
            expirationTimestamp = 0,
            actorDisplayName = "",
            lastEditActorType = null,
            lastEditTimestamp = null,
            renderMarkdown = true,
            lastEditActorId = "",
            lastEditActorDisplayName = ""
        )
        return entity
    }
}
