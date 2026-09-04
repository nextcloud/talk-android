/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.utils.preview

import com.nextcloud.talk.data.database.dao.ChatBlocksDao
import com.nextcloud.talk.data.database.dao.ChatMessagesDao
import com.nextcloud.talk.data.database.dao.ConversationsDao
import com.nextcloud.talk.data.database.model.ChatBlockEntity
import com.nextcloud.talk.data.database.model.ChatMessageEntity
import com.nextcloud.talk.data.database.model.ConversationEntity
import com.nextcloud.talk.data.user.UsersDao
import com.nextcloud.talk.data.user.model.UserEntity
import com.nextcloud.talk.models.json.push.PushConfigurationState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class DummyChatMessagesDaoImpl : ChatMessagesDao {
    override fun getMessagesEqualOrNewerThan(
        internalConversationId: String,
        threadId: Long?,
        oldestMessageId: Long
    ): Flow<List<ChatMessageEntity>> = flowOf()

    override fun getMessagesForConversation(
        internalConversationId: String,
        threadId: Long?
    ): Flow<List<ChatMessageEntity>> = flowOf()

    override fun getTempMessagesForConversation(internalConversationId: String): Flow<List<ChatMessageEntity>> =
        flowOf()

    override fun getTempUnsentMessagesForConversation(
        internalConversationId: String,
        threadId: Long?
    ): Flow<List<ChatMessageEntity>> {
        // nothing to return here as long this class is only used for the Search window
        return flowOf()
    }

    override fun getTempMessageForConversation(
        internalConversationId: String,
        referenceId: String,
        threadId: Long?
    ): Flow<ChatMessageEntity> = flowOf()

    override suspend fun upsertChatMessages(chatMessages: List<ChatMessageEntity>) {
        /* */
    }

    override suspend fun upsertChatMessage(chatMessage: ChatMessageEntity) {
        /* */
    }

    override fun getChatMessageForConversation(
        internalConversationId: String,
        messageId: Long
    ): Flow<ChatMessageEntity> = flowOf()

    override suspend fun getChatMessageEntity(internalConversationId: String, messageId: Long): ChatMessageEntity? =
        null

    override fun observeMessage(internalConversationId: String, messageId: Long): Flow<ChatMessageEntity?> = flowOf()

    override suspend fun getChatMessageOnce(internalConversationId: String, messageId: Long): ChatMessageEntity? = null

    override fun getChatMessageForConversationNullable(
        internalConversationId: String,
        messageId: Long
    ): Flow<ChatMessageEntity?> = flowOf()

    // override fun deleteChatMessages(internalIds: List<String>) {
    //     /* */
    // }

    override fun deleteTempChatMessages(internalConversationId: String, referenceIds: List<String>) {
        /* */
    }

    override fun deleteTempChatMessageIfPending(internalConversationId: String, referenceId: String): Int = 0

    override fun updateChatMessage(message: ChatMessageEntity) {
        /* */
    }

    override fun getMessagesFromIds(messageIds: List<Long>): Flow<List<ChatMessageEntity>> = flowOf()

    override fun getMessagesForConversationSince(
        internalConversationId: String,
        messageId: Long,
        threadId: Long?
    ): Flow<List<ChatMessageEntity>> = flowOf()

    override fun getMessagesInRange(
        internalConversationId: String,
        threadId: Long?,
        oldestMessageId: Long,
        newestMessageId: Long
    ): Flow<List<ChatMessageEntity>> = flowOf()

    override fun getMessagesForConversationBefore(
        internalConversationId: String,
        messageId: Long,
        limit: Int,
        threadId: Long?
    ): Flow<List<ChatMessageEntity>> = flowOf()

    override fun getMessagesForConversationBeforeAndEqual(
        internalConversationId: String,
        messageId: Long,
        limit: Int,
        threadId: Long?
    ): Flow<List<ChatMessageEntity>> = flowOf()

    override suspend fun countMessagesNewerThan(
        internalConversationId: String,
        messageId: Long,
        excludedActorId: String
    ): Int = 0

    override fun getCountBetweenMessageIds(
        internalConversationId: String,
        oldestMessageId: Long,
        newestMessageId: Long,
        threadId: Long?
    ): Int = 0

    override fun clearAllMessagesForUser(pattern: String) {
        /* */
    }

    override fun deleteMessagesOlderThan(internalConversationId: String, messageId: Long) {
        /* */
    }

    override suspend fun deleteExpiredMessages(internalConversationId: String, currentTimeSecs: Long): Int = 0

    override suspend fun getOldestMessageIdInRange(
        internalConversationId: String,
        threadId: Long?,
        oldestMessageId: Long,
        newestMessageId: Long
    ): Long? = null

    override suspend fun getNewestMessageIdInRange(
        internalConversationId: String,
        threadId: Long?,
        oldestMessageId: Long,
        newestMessageId: Long
    ): Long? = null

    override fun getNumberOfThreadReplies(internalConversationId: String, threadId: Long): Int = 0
}

class DummyUserDaoImpl : UsersDao {
    private val dummyUsers = mutableListOf(
        UserEntity(1L, "user1_id", "user1", "server1", "1"),
        UserEntity(2L, "user2_id", "user2", "server1", "2"),
        UserEntity(0L, "user3_id", "user3", "server2", "3")
    )
    private var activeUserId: Long? = 1L

    override suspend fun getActiveUser(): UserEntity? =
        dummyUsers.find { it.id == activeUserId && !it.scheduledForDeletion }

    override fun getActiveUserFlow(): Flow<UserEntity?> =
        flowOf(dummyUsers.find { it.id == activeUserId && !it.scheduledForDeletion })

    override fun getActiveUserSynchronously(): UserEntity? =
        dummyUsers.find {
            it.id == activeUserId && !it.scheduledForDeletion
        }

    override suspend fun deleteUser(user: UserEntity): Int {
        val initialSize = dummyUsers.size
        dummyUsers.removeIf { it.id == user.id }
        return initialSize - dummyUsers.size
    }

    override suspend fun updateUser(user: UserEntity): Int {
        val index = dummyUsers.indexOfFirst { it.id == user.id }
        return if (index != -1) {
            dummyUsers[index] = user
            1
        } else {
            0
        }
    }

    override suspend fun saveUser(user: UserEntity): Long {
        val newUser = user.copy(id = dummyUsers.size + 1L)
        dummyUsers.add(newUser)
        return newUser.id
    }

    override suspend fun saveUsers(vararg users: UserEntity): List<Long> = users.map { saveUser(it) }

    override suspend fun getUsers(): List<UserEntity> = dummyUsers.filter { !it.scheduledForDeletion }

    override suspend fun getUserWithId(id: Long): UserEntity? = dummyUsers.find { it.id == id }

    override suspend fun getUserWithIdNotScheduledForDeletion(id: Long): UserEntity? =
        dummyUsers.find { it.id == id && !it.scheduledForDeletion }

    override suspend fun getUserWithUserId(userId: String): UserEntity? = dummyUsers.find { it.userId == userId }

    override suspend fun getUsersScheduledForDeletion(): List<UserEntity> =
        dummyUsers.filter {
            it.scheduledForDeletion
        }

    override suspend fun getUsersNotScheduledForDeletion(): List<UserEntity> =
        dummyUsers.filter {
            !it.scheduledForDeletion
        }

    override suspend fun getUserWithUsernameAndServer(username: String, server: String): UserEntity? =
        dummyUsers.find { it.username == username }

    override suspend fun setUserAsActiveWithId(id: Long): Int {
        activeUserId = id
        return 1
    }

    override suspend fun updatePushState(id: Long, state: PushConfigurationState): Int {
        val index = dummyUsers.indexOfFirst { it.id == id }
        return if (index != -1) {
            dummyUsers[index] = dummyUsers[index]
            1
        } else {
            0
        }
    }
}

class DummyConversationDaoImpl : ConversationsDao {
    override fun getConversationsForUser(accountId: Long): Flow<List<ConversationEntity>> = flowOf()

    override fun getConversationForUser(accountId: Long, token: String): Flow<ConversationEntity?> = flowOf()

    override suspend fun upsertConversations(accountId: Long, serverItems: List<ConversationEntity>) {
        /* */
    }

    override fun deleteConversations(conversationIds: List<String>) {
        /* */
    }

    override fun updateConversation(conversationEntity: ConversationEntity) {
        /* */
    }

    override suspend fun updateConversationFromCatchUp(
        internalId: String,
        lastMessageJson: String,
        lastActivity: Long,
        unreadMessages: Int
    ): Int = 0

    override suspend fun updateReadState(internalId: String, lastReadMessage: Int, unreadMessages: Int) {
        /* */
    }

    override fun insertConversation(conversation: ConversationEntity) {
        /* */
    }

    override fun clearAllConversationsForUser(accountId: Long) {
        /* */
    }
}

class DummyChatBlocksDaoImpl : ChatBlocksDao {
    override suspend fun deleteChatBlocks(blocks: List<ChatBlockEntity>) {
        /* */
    }

    override fun getChatBlocksContainingMessageId(
        internalConversationId: String,
        threadId: Long?,
        messageId: Long
    ): Flow<List<ChatBlockEntity>> = flowOf()

    override suspend fun getConnectedChatBlocks(
        internalConversationId: String,
        threadId: Long?,
        oldestMessageId: Long,
        newestMessageId: Long
    ): List<ChatBlockEntity> = emptyList()

    override fun getNewestMessageIdFromChatBlocks(internalConversationId: String, threadId: Long?): Long = 0L

    override suspend fun upsertChatBlock(chatBlock: ChatBlockEntity) {
        /* */
    }

    override fun deleteChatBlocksOlderThan(internalConversationId: String, messageId: Long) {
        /* */
    }

    override fun getLatestChatBlock(internalConversationId: String, threadId: Long?): Flow<ChatBlockEntity?> = flowOf()

    override suspend fun getChatBlocksForConversation(internalConversationId: String): List<ChatBlockEntity> =
        emptyList()
}
