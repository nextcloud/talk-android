/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.data.database.dao

import android.util.Log
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
    suspend fun deleteChatBlocks(blocks: List<ChatBlockEntity>)

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
    suspend fun getConnectedChatBlocks(
        internalConversationId: String,
        threadId: Long?,
        oldestMessageId: Long,
        newestMessageId: Long
    ): List<ChatBlockEntity>

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

    /**
     * Upserts [chatBlock] and merges all chat blocks it overlaps into one covering their combined
     * range, as a single atomic operation.
     *
     * The open-path delta fetch, long polling, the insurance request, signaling and the background
     * catch-up can all update the blocks of the same conversation concurrently. Without one
     * transaction around upsert, connectivity query and merge, two callers could each upsert
     * their block and query connectivity before seeing the other's write, leaving overlapping
     * blocks behind.
     */
    @Transaction
    suspend fun upsertAndMergeConnectedChatBlocks(chatBlock: ChatBlockEntity) {
        upsertChatBlock(chatBlock)

        val connectedChatBlocks = getConnectedChatBlocks(
            internalConversationId = chatBlock.internalConversationId,
            threadId = chatBlock.threadId,
            oldestMessageId = chatBlock.oldestMessageId,
            newestMessageId = chatBlock.newestMessageId
        )
        if (connectedChatBlocks.size > 1) {
            val mergedBlock = chatBlock.copy(
                oldestMessageId = connectedChatBlocks.minOf { it.oldestMessageId },
                newestMessageId = connectedChatBlocks.maxOf { it.newestMessageId },
                hasHistory = connectedChatBlocks.all { it.hasHistory }
            )
            replaceConnectedChatBlocks(connectedChatBlocks, mergedBlock)
            Log.d(
                TAG,
                "Merged ${connectedChatBlocks.size} connected chat blocks into " +
                    "${mergedBlock.oldestMessageId}..${mergedBlock.newestMessageId}"
            )
        }
    }

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
    SELECT *
    FROM ChatBlocks
    WHERE internalConversationId = :internalConversationId
      AND (threadId = :threadId OR (threadId IS NULL AND :threadId IS NULL))
    ORDER BY newestMessageId DESC
    LIMIT 1
    """
    )
    fun getLatestChatBlock(internalConversationId: String, threadId: Long?): Flow<ChatBlockEntity?>

    @Query(
        """
        SELECT *
        FROM ChatBlocks
        WHERE internalConversationId = :internalConversationId
        ORDER BY newestMessageId ASC
        """
    )
    suspend fun getChatBlocksForConversation(internalConversationId: String): List<ChatBlockEntity>

    companion object {
        private const val TAG = "ChatBlocksDao"
    }
}
