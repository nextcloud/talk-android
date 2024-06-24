/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.nextcloud.talk.data.database.model.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessagesDao {
    @Query(
        """
        SELECT * 
        FROM ChatMessages
        WHERE internal_conversation_id = :conversationId
        """
    )
    fun getMessagesForConversation(conversationId: Long): Flow<List<ChatMessageEntity>>

    @Upsert
    fun upsertChatMessages(chatMessages: List<ChatMessageEntity>)

    @Query(
        """
        SELECT * 
        FROM ChatMessages
        WHERE internal_conversation_id = :conversationId AND id = :messageId
        """
    )
    fun getChatMessageForConversation(conversationId: Long, messageId: Long): Flow<ChatMessageEntity>

    @Query(
        value = """
            DELETE FROM chatmessages
            WHERE id in (:messageIds)
        """
    )
    fun deleteChatMessages(messageIds: List<Int>)

    @Update
    fun updateChatMessage(message: ChatMessageEntity)

    @Query(
        """
        SELECT *
        FROM chatmessages
        WHERE id in (:messageIds)
        """
    )
    fun getMessagesFromIds(messageIds: List<Long>): Flow<List<ChatMessageEntity>>

    @Query(
        """
        SELECT * 
        FROM ChatMessages 
        WHERE internal_conversation_id = :conversationId AND id >= :messageId 
        ORDER BY timestamp ASC, id ASC
        """
    )
    fun getMessagesForConversationSince(conversationId: Long, messageId: Long): Flow<List<ChatMessageEntity>>

    @Query(
        """
        SELECT * 
        FROM ChatMessages
        WHERE internal_conversation_id = :conversationId AND id <= :messageId
        ORDER BY timestamp ASC, id ASC
        LIMIT :limit
        """
    )
    fun getMessagesForConversationBefore(conversationId: Long, messageId: Long, limit: Int):
        Flow<List<ChatMessageEntity>>
}
