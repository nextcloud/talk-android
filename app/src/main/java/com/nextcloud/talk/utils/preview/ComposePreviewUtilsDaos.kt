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
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class DummyChatMessagesDaoImpl : ChatMessagesDao {
    override fun getNewestMessageId(internalConversationId: String): Long = 0L

    override fun getMessagesForConversation(internalConversationId: String): Flow<List<ChatMessageEntity>> = flowOf()

    override fun getTempMessagesForConversation(internalConversationId: String): Flow<List<ChatMessageEntity>> =
        flowOf()

    override fun getTempMessageForConversation(
        internalConversationId: String,
        referenceId: String
    ): Flow<ChatMessageEntity> = flowOf()

    override suspend fun upsertChatMessages(chatMessages: List<ChatMessageEntity>) { /* */ }

    override suspend fun upsertChatMessage(chatMessage: ChatMessageEntity) { /* */ }

    override fun getChatMessageForConversation(
        internalConversationId: String,
        messageId: Long
    ): Flow<ChatMessageEntity> = flowOf()

    override fun deleteChatMessages(internalIds: List<String>) { /* */ }

    override fun deleteTempChatMessages(internalConversationId: String, referenceIds: List<String>) { /* */ }

    override fun updateChatMessage(message: ChatMessageEntity) { /* */ }

    override fun getMessagesFromIds(messageIds: List<Long>): Flow<List<ChatMessageEntity>> = flowOf()

    override fun getMessagesForConversationSince(
        internalConversationId: String,
        messageId: Long
    ): Flow<List<ChatMessageEntity>> = flowOf()

    override fun getMessagesForConversationBefore(
        internalConversationId: String,
        messageId: Long,
        limit: Int
    ): Flow<List<ChatMessageEntity>> = flowOf()

    override fun getMessagesForConversationBeforeAndEqual(
        internalConversationId: String,
        messageId: Long,
        limit: Int
    ): Flow<List<ChatMessageEntity>> = flowOf()

    override fun getCountBetweenMessageIds(
        internalConversationId: String,
        oldestMessageId: Long,
        newestMessageId: Long
    ): Int = 0

    override fun clearAllMessagesForUser(pattern: String) { /* */ }

    override fun deleteMessagesOlderThan(internalConversationId: String, messageId: Long) { /* */ }
}

class DummyUserDaoImpl : UsersDao() {
    private val dummyUsers = mutableListOf(
        UserEntity(1L, "user1_id", "user1", "server1", "1"),
        UserEntity(2L, "user2_id", "user2", "server1", "2"),
        UserEntity(0L, "user3_id", "user3", "server2", "3")
    )
    private var activeUserId: Long? = 1L

    override fun getActiveUser(): Maybe<UserEntity> {
        return Maybe.fromCallable { dummyUsers.find { it.id == activeUserId && !it.scheduledForDeletion } }
    }

    override fun getActiveUserObservable(): Observable<UserEntity> {
        return Observable.fromCallable { dummyUsers.find { it.id == activeUserId && !it.scheduledForDeletion } }
    }

    override fun getActiveUserSynchronously(): UserEntity? {
        return dummyUsers.find { it.id == activeUserId && !it.scheduledForDeletion }
    }

    override fun deleteUser(user: UserEntity): Int {
        val initialSize = dummyUsers.size
        dummyUsers.removeIf { it.id == user.id }
        return initialSize - dummyUsers.size
    }

    override fun updateUser(user: UserEntity): Int {
        val index = dummyUsers.indexOfFirst { it.id == user.id }
        return if (index != -1) {
            dummyUsers[index] = user
            1
        } else {
            0
        }
    }

    override fun saveUser(user: UserEntity): Long {
        val newUser = user.copy(id = dummyUsers.size + 1L)
        dummyUsers.add(newUser)
        return newUser.id
    }

    override fun saveUsers(vararg users: UserEntity): List<Long> {
        return users.map { saveUser(it) }
    }

    override fun getUsers(): Single<List<UserEntity>> {
        return Single.just(dummyUsers.filter { !it.scheduledForDeletion })
    }

    override fun getUserWithId(id: Long): Maybe<UserEntity> {
        return Maybe.fromCallable { dummyUsers.find { it.id == id } }
    }

    override fun getUserWithIdNotScheduledForDeletion(id: Long): Maybe<UserEntity> {
        return Maybe.fromCallable { dummyUsers.find { it.id == id && !it.scheduledForDeletion } }
    }

    override fun getUserWithUserId(userId: String): Maybe<UserEntity> {
        return Maybe.fromCallable { dummyUsers.find { it.userId == userId } }
    }

    override fun getUsersScheduledForDeletion(): Single<List<UserEntity>> {
        return Single.just(dummyUsers.filter { it.scheduledForDeletion })
    }

    override fun getUsersNotScheduledForDeletion(): Single<List<UserEntity>> {
        return Single.just(dummyUsers.filter { !it.scheduledForDeletion })
    }

    override fun getUserWithUsernameAndServer(username: String, server: String): Maybe<UserEntity> {
        return Maybe.fromCallable { dummyUsers.find { it.username == username } }
    }

    override fun setUserAsActiveWithId(id: Long): Int {
        activeUserId = id
        return 1
    }

    override fun updatePushState(id: Long, state: PushConfigurationState): Single<Int> {
        val index = dummyUsers.indexOfFirst { it.id == id }
        return if (index != -1) {
            dummyUsers[index] = dummyUsers[index]
            Single.just(1)
        } else {
            Single.just(0)
        }
    }
}

class DummyConversationDaoImpl : ConversationsDao {
    override fun getConversationsForUser(accountId: Long): Flow<List<ConversationEntity>> = flowOf()

    override fun getConversationForUser(accountId: Long, token: String): Flow<ConversationEntity?> = flowOf()

    override fun upsertConversations(conversationEntities: List<ConversationEntity>) { /* */ }

    override fun deleteConversations(conversationIds: List<String>) { /* */ }

    override fun updateConversation(conversationEntity: ConversationEntity) { /* */ }

    override fun clearAllConversationsForUser(accountId: Long) { /* */ }
}

class DummyChatBlocksDaoImpl : ChatBlocksDao {
    override fun deleteChatBlocks(blocks: List<ChatBlockEntity>) { /* */ }

    override fun getChatBlocks(internalConversationId: String): Flow<List<ChatBlockEntity>> = flowOf()

    override fun getChatBlocksContainingMessageId(
        internalConversationId: String,
        messageId: Long
    ): Flow<List<ChatBlockEntity?>> = flowOf()

    override fun getConnectedChatBlocks(
        internalConversationId: String,
        oldestMessageId: Long,
        newestMessageId: Long
    ): Flow<List<ChatBlockEntity>> = flowOf()

    override suspend fun upsertChatBlock(chatBlock: ChatBlockEntity) { /* */ }

    override fun clearChatBlocksForUser(pattern: String) { /* */ }

    override fun deleteChatBlocksOlderThan(internalConversationId: String, messageId: Long) { /* */ }
}
