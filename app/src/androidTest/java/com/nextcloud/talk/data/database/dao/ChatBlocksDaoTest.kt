/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.data.database.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.runner.AndroidJUnit4
import com.nextcloud.talk.data.database.model.ChatBlockEntity
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
import java.lang.Boolean
import kotlin.Long
import kotlin.String

@RunWith(AndroidJUnit4::class)
class ChatBlocksDaoTest {
    private lateinit var usersDao: UsersDao
    private lateinit var conversationsDao: ConversationsDao
    private lateinit var chatBlocksDao: ChatBlocksDao
    private lateinit var db: TalkDatabase
    private val tag = ChatBlocksDaoTest::class.java.simpleName

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context,
            TalkDatabase::class.java
        ).build()
        usersDao = db.usersDao()
        conversationsDao = db.conversationsDao()
        chatBlocksDao = db.chatBlocksDao()
    }

    @After
    fun closeDb() = db.close()

    @Test
    fun testGetChatBlocksContainingMessageId() =
        runTest {
            val user = createUserEntity("account1", "Account 1")
            usersDao.saveUser(user)
            val account1 = usersDao.getUserWithUserId("account1").blockingGet()

            conversationsDao.upsertConversations(
                accountId = user.id,
                listOf(
                    createConversationEntity(
                        accountId = account1.id,
                        "abc",
                        roomName = "Conversation One"
                    ),
                    createConversationEntity(
                        accountId = account1.id,
                        "def",
                        roomName = "Conversation Two"
                    )
                )
            )

            val conversation1 = conversationsDao.getConversationsForUser(account1.id).first()[0]

            val chatBlock1 = ChatBlockEntity(
                internalConversationId = conversation1.internalId,
                accountId = conversation1.accountId,
                token = conversation1.token,
                threadId = 123,
                oldestMessageId = 50,
                newestMessageId = 60,
                hasHistory = true
            )

            val chatBlock2 = ChatBlockEntity(
                internalConversationId = conversation1.internalId,
                accountId = conversation1.accountId,
                token = conversation1.token,
                threadId = 123,
                oldestMessageId = 10,
                newestMessageId = 20,
                hasHistory = true
            )

            val chatBlock3 = ChatBlockEntity(
                internalConversationId = conversation1.internalId,
                accountId = conversation1.accountId,
                token = conversation1.token,
                threadId = null,
                oldestMessageId = 50,
                newestMessageId = 60,
                hasHistory = true
            )

            chatBlocksDao.upsertChatBlock(chatBlock1)
            chatBlocksDao.upsertChatBlock(chatBlock2)
            chatBlocksDao.upsertChatBlock(chatBlock3)

            val chatBlocksOfThread = chatBlocksDao.getChatBlocksContainingMessageId(
                internalConversationId = conversation1.internalId,
                threadId = 123,
                messageId = 55
            )

            assertEquals(1, chatBlocksOfThread.first().size)
        }

    @Test
    fun testGetConnectedChatBlocks() =
        runTest {
            val user = createUserEntity("account1", "Account 1")
            usersDao.saveUser(user)
            val account1 = usersDao.getUserWithUserId("account1").blockingGet()

            conversationsDao.upsertConversations(
                account1.id,
                listOf(
                    createConversationEntity(
                        accountId = account1.id,
                        "abc",
                        roomName = "Conversation One"
                    ),
                    createConversationEntity(
                        accountId = account1.id,
                        "def",
                        roomName = "Conversation Two"
                    )
                )
            )

            val conversation1 = conversationsDao.getConversationsForUser(account1.id).first()[0]
            val conversation2 = conversationsDao.getConversationsForUser(account1.id).first()[1]

            val searchedChatBlock = ChatBlockEntity(
                internalConversationId = conversation1.internalId,
                accountId = conversation1.accountId,
                token = conversation1.token,
                threadId = null,
                oldestMessageId = 50,
                newestMessageId = 60,
                hasHistory = true
            )

            val chatBlockTooOld = ChatBlockEntity(
                internalConversationId = conversation1.internalId,
                accountId = conversation1.accountId,
                token = conversation1.token,
                threadId = null,
                oldestMessageId = 10,
                newestMessageId = 20,
                hasHistory = true
            )

            val chatBlockOverlap1 = ChatBlockEntity(
                internalConversationId = conversation1.internalId,
                accountId = conversation1.accountId,
                token = conversation1.token,
                threadId = null,
                oldestMessageId = 45,
                newestMessageId = 55,
                hasHistory = true
            )

            val chatBlockWithin = ChatBlockEntity(
                internalConversationId = conversation1.internalId,
                accountId = conversation1.accountId,
                token = conversation1.token,
                threadId = null,
                oldestMessageId = 52,
                newestMessageId = 58,
                hasHistory = true
            )

            val chatBlockOverall = ChatBlockEntity(
                internalConversationId = conversation1.internalId,
                accountId = conversation1.accountId,
                token = conversation1.token,
                threadId = null,
                oldestMessageId = 1,
                newestMessageId = 99,
                hasHistory = true
            )

            val chatBlockOverlap2 = ChatBlockEntity(
                internalConversationId = conversation1.internalId,
                accountId = conversation1.accountId,
                token = conversation1.token,
                threadId = null,
                oldestMessageId = 59,
                newestMessageId = 70,
                hasHistory = true
            )

            val chatBlockTooNew = ChatBlockEntity(
                internalConversationId = conversation1.internalId,
                accountId = conversation1.accountId,
                token = conversation1.token,
                threadId = null,
                oldestMessageId = 80,
                newestMessageId = 90,
                hasHistory = true
            )

            val chatBlockWithinButOtherConversation = ChatBlockEntity(
                internalConversationId = conversation2.internalId,
                accountId = conversation2.accountId,
                token = conversation2.token,
                threadId = null,
                oldestMessageId = 53,
                newestMessageId = 57,
                hasHistory = true
            )

            chatBlocksDao.upsertChatBlock(searchedChatBlock)

            chatBlocksDao.upsertChatBlock(chatBlockTooOld)
            chatBlocksDao.upsertChatBlock(chatBlockOverlap1)
            chatBlocksDao.upsertChatBlock(chatBlockWithin)
            chatBlocksDao.upsertChatBlock(chatBlockOverall)
            chatBlocksDao.upsertChatBlock(chatBlockOverlap2)
            chatBlocksDao.upsertChatBlock(chatBlockTooNew)
            chatBlocksDao.upsertChatBlock(chatBlockWithinButOtherConversation)

            val results = chatBlocksDao.getConnectedChatBlocks(
                internalConversationId = conversation1.internalId,
                threadId = null,
                oldestMessageId = searchedChatBlock.oldestMessageId,
                newestMessageId = searchedChatBlock.newestMessageId
            )

            assertEquals(5, results.first().size)
        }

    @Test
    fun testGetConnectedChatBlocksWithThreadsScenario() =
        runTest {
            val user = createUserEntity("account1", "Account 1")
            usersDao.saveUser(user)
            val account1 = usersDao.getUserWithUserId("account1").blockingGet()

            conversationsDao.upsertConversations(
                account1.id,
                listOf(
                    createConversationEntity(
                        accountId = account1.id,
                        "abc",
                        roomName = "Conversation One"
                    ),
                    createConversationEntity(
                        accountId = account1.id,
                        "def",
                        roomName = "Conversation Two"
                    )
                )
            )

            val conversation1 = conversationsDao.getConversationsForUser(account1.id).first()[0]

            val searchedChatBlock = ChatBlockEntity(
                internalConversationId = conversation1.internalId,
                accountId = conversation1.accountId,
                token = conversation1.token,
                threadId = 123,
                oldestMessageId = 50,
                newestMessageId = 60,
                hasHistory = true
            )

            val chatBlockOverlap1 = ChatBlockEntity(
                internalConversationId = conversation1.internalId,
                accountId = conversation1.accountId,
                token = conversation1.token,
                threadId = null,
                oldestMessageId = 45,
                newestMessageId = 55,
                hasHistory = true
            )

            val chatBlockOverlap2 = ChatBlockEntity(
                internalConversationId = conversation1.internalId,
                accountId = conversation1.accountId,
                token = conversation1.token,
                threadId = 123,
                oldestMessageId = 59,
                newestMessageId = 70,
                hasHistory = true
            )

            chatBlocksDao.upsertChatBlock(searchedChatBlock)

            chatBlocksDao.upsertChatBlock(chatBlockOverlap1)
            chatBlocksDao.upsertChatBlock(chatBlockOverlap2)

            val resultsForThreadIdNull = chatBlocksDao.getConnectedChatBlocks(
                internalConversationId = conversation1.internalId,
                threadId = null,
                oldestMessageId = searchedChatBlock.oldestMessageId,
                newestMessageId = searchedChatBlock.newestMessageId
            )

            assertEquals(1, resultsForThreadIdNull.first().size)

            val resultsForThreadId123 = chatBlocksDao.getConnectedChatBlocks(
                internalConversationId = conversation1.internalId,
                threadId = 123,
                oldestMessageId = searchedChatBlock.oldestMessageId,
                newestMessageId = searchedChatBlock.newestMessageId
            )

            assertEquals(2, resultsForThreadId123.first().size)
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
            current = Boolean.FALSE,
            scheduledForDeletion = Boolean.FALSE
        )

    private fun createConversationEntity(accountId: Long, token: String, roomName: String) =
        ConversationEntity(
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
