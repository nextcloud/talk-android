/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.nextcloud.talk.data.database.model.ChatBlockEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatBlocksDao {
    @Delete
    fun deleteChatBlocks(blocks: List<ChatBlockEntity>)

    @Query(
        """
        SELECT *
        FROM ChatBlocks
        WHERE internalConversationId in (:internalConversationId)
        AND (threadId = :threadId OR (threadId IS NULL AND :threadId IS NULL))
        AND oldestMessageId <= :messageId
        AND newestMessageId >= :messageId
        ORDER BY newestMessageId ASC
        """
    )
    fun getChatBlocksContainingMessageId(
        internalConversationId: String,
        threadId: Long?,
        messageId: Long
    ): Flow<List<ChatBlockEntity>>

    @Query(
        """
        SELECT *
        FROM ChatBlocks
        WHERE internalConversationId = :internalConversationId
        AND (threadId = :threadId OR (threadId IS NULL AND :threadId IS NULL))
        AND(
            (oldestMessageId <= :oldestMessageId AND newestMessageId >= :oldestMessageId)
            OR
            (oldestMessageId <= :newestMessageId AND newestMessageId >= :newestMessageId)
            OR
            (oldestMessageId >= :oldestMessageId AND newestMessageId <= :newestMessageId)
        )
        ORDER BY newestMessageId ASC
        """
    )
    fun getConnectedChatBlocks(
        internalConversationId: String,
        threadId: Long?,
        oldestMessageId: Long,
        newestMessageId: Long
    ): Flow<List<ChatBlockEntity>>

    @Query(
        """
        SELECT MAX(newestMessageId) as max_items
        FROM ChatBlocks
        WHERE internalConversationId = :internalConversationId
        AND (threadId = :threadId OR (threadId IS NULL AND :threadId IS NULL))
        """
    )
    fun getNewestMessageIdFromChatBlocks(internalConversationId: String, threadId: Long?): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChatBlock(chatBlock: ChatBlockEntity)

    @Transaction
    suspend fun replaceConnectedChatBlocks(connectedBlocks: List<ChatBlockEntity>, mergedBlock: ChatBlockEntity) {
        val newestConnectedBlock = connectedBlocks.maxByOrNull { it.newestMessageId }

        if (newestConnectedBlock == null) {
            upsertChatBlock(mergedBlock)
            return
        }

        val updatedBlock = newestConnectedBlock.copy(
            internalConversationId = mergedBlock.internalConversationId,
            accountId = mergedBlock.accountId,
            token = mergedBlock.token,
            threadId = mergedBlock.threadId,
            oldestMessageId = mergedBlock.oldestMessageId,
            newestMessageId = mergedBlock.newestMessageId,
            hasHistory = mergedBlock.hasHistory
        )

        upsertChatBlock(updatedBlock)

        val blocksToDelete = connectedBlocks.filter { it.id != updatedBlock.id }
        if (blocksToDelete.isNotEmpty()) {
            deleteChatBlocks(blocksToDelete)
        }
    }

    @Query(
        """
        DELETE FROM ChatBlocks
        WHERE internalConversationId = :internalConversationId
        AND oldestMessageId < :messageId
        """
    )
    fun deleteChatBlocksOlderThan(internalConversationId: String, messageId: Long)

    @Query(
        """
        DELETE FROM ChatBlocks
        WHERE internalConversationId = :internalConversationId
        """
    )
    suspend fun deleteAllChatBlocksForConversation(internalConversationId: String)

    @Query(
        """
        DELETE FROM ChatBlocks
        WHERE internalConversationId = :internalConversationId
        AND NOT EXISTS (
            SELECT 1 FROM ChatMessages
            WHERE ChatMessages.internalConversationId = :internalConversationId
            AND ChatMessages.id BETWEEN ChatBlocks.oldestMessageId AND ChatBlocks.newestMessageId
            AND ChatMessages.isTemporary = 0
            AND (ChatBlocks.threadId IS NULL OR ChatMessages.threadId = ChatBlocks.threadId)
        )
        """
    )
    suspend fun deleteEmptyChatBlocks(internalConversationId: String)

    @Query(
        """
        UPDATE ChatBlocks
        SET
            oldestMessageId = COALESCE((
                SELECT MIN(m.id) FROM ChatMessages m
                WHERE m.internalConversationId = ChatBlocks.internalConversationId
                AND m.id BETWEEN ChatBlocks.oldestMessageId AND ChatBlocks.newestMessageId
                AND m.isTemporary = 0
                AND (ChatBlocks.threadId IS NULL OR m.threadId = ChatBlocks.threadId)
            ), ChatBlocks.oldestMessageId),
            newestMessageId = COALESCE((
                SELECT MAX(m.id) FROM ChatMessages m
                WHERE m.internalConversationId = ChatBlocks.internalConversationId
                AND m.id BETWEEN ChatBlocks.oldestMessageId AND ChatBlocks.newestMessageId
                AND m.isTemporary = 0
                AND (ChatBlocks.threadId IS NULL OR m.threadId = ChatBlocks.threadId)
            ), ChatBlocks.newestMessageId)
        WHERE internalConversationId = :internalConversationId
        """
    )
    suspend fun trimChatBlockBoundaries(internalConversationId: String)

    @Transaction
    suspend fun trimAndCleanBlocks(internalConversationId: String) {
        deleteEmptyChatBlocks(internalConversationId)
        trimChatBlockBoundaries(internalConversationId)
    }

    @Query(
        """
    SELECT *
    FROM ChatBlocks
    WHERE internalConversationId = :internalConversationId
      AND (threadId = :threadId OR (threadId IS NULL AND :threadId IS NULL))
    ORDER BY newestMessageId DESC
    LIMIT 1
    """
    )
    fun getLatestChatBlock(internalConversationId: String, threadId: Long?): Flow<ChatBlockEntity?>
}
