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
@Suppress("Detekt.TooManyFunctions")
interface ChatMessagesDao {
    @Query(
        """
        SELECT *
        FROM ChatMessages
        WHERE internalConversationId = :internalConversationId
        AND isTemporary = 0
        ORDER BY timestamp DESC, id DESC
        """
    )
    fun getMessagesForConversation(internalConversationId: String): Flow<List<ChatMessageEntity>>

    @Query(
        """
        SELECT *
        FROM ChatMessages
        WHERE internalConversationId = :internalConversationId
        AND isTemporary = 1
        ORDER BY timestamp DESC, id DESC
        """
    )
    fun getTempMessagesForConversation(internalConversationId: String): Flow<List<ChatMessageEntity>>

    @Query(
        """
        SELECT *
        FROM ChatMessages
        WHERE internalConversationId = :internalConversationId
        AND isTemporary = 1 
        AND sendStatus != 'SENT_PENDING_ACK'
        AND (:threadId IS NULL OR threadId = :threadId)
        ORDER BY timestamp DESC, id DESC
        """
    )
    fun getTempUnsentMessagesForConversation(
        internalConversationId: String,
        threadId: Long?
    ): Flow<List<ChatMessageEntity>>

    @Query(
        """
        SELECT *
        FROM ChatMessages
        WHERE internalConversationId = :internalConversationId
        AND referenceId = :referenceId
        AND isTemporary = 1
        AND (:threadId IS NULL OR threadId = :threadId)
        ORDER BY timestamp DESC, id DESC
        """
    )
    fun getTempMessageForConversation(
        internalConversationId: String,
        referenceId: String,
        threadId: Long?
    ): Flow<ChatMessageEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChatMessages(chatMessages: List<ChatMessageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChatMessage(chatMessage: ChatMessageEntity)

    @Query(
        """
        SELECT * 
        FROM ChatMessages
        WHERE internalConversationId = :internalConversationId 
        AND id = :messageId
        """
    )
    fun getChatMessageForConversation(internalConversationId: String, messageId: Long): Flow<ChatMessageEntity>

    @Query(
        value = """
            DELETE FROM ChatMessages
            WHERE internalId in (:internalIds)
        """
    )
    fun deleteChatMessages(internalIds: List<String>)

    @Query(
        value = """
            DELETE FROM ChatMessages
            WHERE internalConversationId = :internalConversationId
            AND referenceId in (:referenceIds)
            AND isTemporary = 1
        """
    )
    fun deleteTempChatMessages(internalConversationId: String, referenceIds: List<String>)

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
        AND isTemporary = 0
        AND (:threadId IS NULL OR threadId = :threadId)
        ORDER BY timestamp ASC, id ASC
        """
    )
    fun getMessagesForConversationSince(
        internalConversationId: String,
        messageId: Long,
        threadId: Long?
    ): Flow<List<ChatMessageEntity>>

    @Query(
        """
        SELECT *
        FROM ChatMessages
        WHERE internalConversationId = :internalConversationId 
        AND isTemporary = 0
        AND id < :messageId
        AND (:threadId IS NULL OR threadId = :threadId)
        ORDER BY timestamp DESC, id DESC
        LIMIT :limit
        """
    )
    fun getMessagesForConversationBefore(
        internalConversationId: String,
        messageId: Long,
        limit: Int,
        threadId: Long?
    ): Flow<List<ChatMessageEntity>>

    @Query(
        """
        SELECT *
        FROM ChatMessages
        WHERE internalConversationId = :internalConversationId 
        AND isTemporary = 0
        AND id <= :messageId
        AND (:threadId IS NULL OR threadId = :threadId)
        ORDER BY timestamp DESC, id DESC
        LIMIT :limit
        """
    )
    fun getMessagesForConversationBeforeAndEqual(
        internalConversationId: String,
        messageId: Long,
        limit: Int,
        threadId: Long?
    ): Flow<List<ChatMessageEntity>>

    @Query(
        """
        SELECT COUNT(*) 
        FROM ChatMessages 
        WHERE internalConversationId = :internalConversationId 
        AND isTemporary = 0
        AND (:threadId IS NULL OR threadId = :threadId)
        AND id BETWEEN :newestMessageId AND :oldestMessageId
        """
    )
    fun getCountBetweenMessageIds(
        internalConversationId: String,
        oldestMessageId: Long,
        newestMessageId: Long,
        threadId: Long?
    ): Int

    @Query(
        """
        DELETE FROM chatmessages
        WHERE internalId LIKE :pattern
        """
    )
    fun clearAllMessagesForUser(pattern: String)

    @Query(
        """
        DELETE FROM chatmessages
        WHERE internalConversationId = :internalConversationId 
        AND id < :messageId
        """
    )
    fun deleteMessagesOlderThan(internalConversationId: String, messageId: Long)

    @Query(
        """
        SELECT COUNT(*)
        FROM ChatMessages AS child
        INNER JOIN ChatMessages AS parent
        ON child.parent = parent.id
        WHERE child.internalConversationId = :internalConversationId
        AND child.isTemporary = 0
        AND child.messageType = 'comment'
        AND parent.threadId = :threadId
    """
    )
    fun getNumberOfThreadReplies(internalConversationId: String, threadId: Long): Int
}
