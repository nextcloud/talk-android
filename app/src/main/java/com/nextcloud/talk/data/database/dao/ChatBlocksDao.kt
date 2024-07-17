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
        ORDER BY newestMessageId ASC
        """
    )
    fun getChatBlocks(internalConversationId: String): Flow<List<ChatBlockEntity>>

    // @Query(
    //     """
    //     SELECT *
    //     FROM ChatBlocks
    //     WHERE internalConversationId in (:internalConversationId)
    //     AND newestMessageId >= :messageId
    //     ORDER BY newestMessageId ASC
    //     """
    // )
    // fun getChatBlocksThatReachMessageId(
    //     internalConversationId: String,
    //     messageId: Long
    // ):
    //     Flow<List<ChatBlockEntity>>

    @Query(
        """
        SELECT *
        FROM ChatBlocks
        WHERE internalConversationId in (:internalConversationId)
        AND oldestMessageId <= :messageId
        AND newestMessageId >= :messageId
        ORDER BY newestMessageId ASC
        """
    )
    fun getChatBlocksContainingMessageId(internalConversationId: String, messageId: Long): Flow<List<ChatBlockEntity?>>


    @Query(
        """
        SELECT *
        FROM ChatBlocks
        WHERE internalConversationId = :internalConversationId
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
        oldestMessageId: Long,
        newestMessageId: Long
    ): Flow<List<ChatBlockEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChatBlock(chatBlock: ChatBlockEntity)

    @Query(
        """
        DELETE FROM ChatBlocks
        WHERE internalConversationId LIKE :pattern
        """
    )
    fun clearChatBlocksForUser(pattern: String)
}
