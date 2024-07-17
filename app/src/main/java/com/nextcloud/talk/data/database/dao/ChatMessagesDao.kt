/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.nextcloud.talk.data.database.model.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessagesDao {
    @Query(
        """
        SELECT MAX(id) as max_items
        FROM ChatMessages
        WHERE internalConversationId = :internalConversationId
        """
    )
    fun getNewestMessageId(internalConversationId: String): Long

    @Query(
        """
        SELECT *
        FROM ChatMessages
        WHERE internalConversationId = :internalConversationId
        ORDER BY timestamp DESC, id DESC
        """
    )
    fun getMessagesForConversation(internalConversationId: String): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChatMessages(chatMessages: List<ChatMessageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChatMessage(chatMessage: ChatMessageEntity)

    @Query(
        """
        SELECT * 
        FROM ChatMessages
        WHERE internalConversationId = :internalConversationId AND id = :messageId
        """
    )
    fun getChatMessageForConversation(internalConversationId: String, messageId: Long): Flow<ChatMessageEntity>

    @Query(
        value = """
            DELETE FROM ChatMessages
            WHERE id in (:messageIds)
        """
    )
    fun deleteChatMessages(messageIds: List<Int>)

    @Update
    fun updateChatMessage(message: ChatMessageEntity)

    @Query(
        """
        SELECT *
        FROM ChatMessages
        WHERE id in (:messageIds)
        ORDER BY timestamp ASC, id ASC
        """
    )
    fun getMessagesFromIds(messageIds: List<Long>): Flow<List<ChatMessageEntity>>

    @Query(
        """
        SELECT * 
        FROM ChatMessages 
        WHERE internalConversationId = :internalConversationId AND id >= :messageId 
        ORDER BY timestamp ASC, id ASC
        """
    )
    fun getMessagesForConversationSince(internalConversationId: String, messageId: Long): Flow<List<ChatMessageEntity>>

    @Query(
        """
        SELECT *
        FROM ChatMessages
        WHERE internalConversationId = :internalConversationId 
        AND id < :messageId
        ORDER BY timestamp DESC, id DESC
        LIMIT :limit
        """
    )
    fun getMessagesForConversationBefore(
        internalConversationId: String,
        messageId: Long,
        limit: Int
    ): Flow<List<ChatMessageEntity>>

    @Query(
        """
        SELECT *
        FROM ChatMessages
        WHERE internalConversationId = :internalConversationId 
        AND id <= :messageId
        ORDER BY timestamp DESC, id DESC
        LIMIT :limit
        """
    )
    fun getMessagesForConversationBeforeAndEqual(
        internalConversationId: String,
        messageId: Long,
        limit: Int
    ): Flow<List<ChatMessageEntity>>

    @Query(
        """
        SELECT COUNT(*) 
        FROM ChatMessages 
        WHERE internalConversationId = :internalConversationId 
        AND id BETWEEN :newestMessageId AND :oldestMessageId
        """
    )
    fun getCountBetweenMessageIds(internalConversationId: String, oldestMessageId: Long, newestMessageId: Long): Int

    @Query(
        """
        DELETE FROM chatmessages
        WHERE internalId LIKE :pattern
        """
    )
    fun clearAllMessagesForUser(pattern: String)
}
