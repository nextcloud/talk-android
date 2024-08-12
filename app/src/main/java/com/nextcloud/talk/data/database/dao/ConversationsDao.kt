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
import com.nextcloud.talk.data.database.model.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationsDao {
    @Query("SELECT * FROM Conversations where accountId = :accountId")
    fun getConversationsForUser(accountId: Long): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM Conversations where accountId = :accountId AND token = :token")
    fun getConversationForUser(accountId: Long, token: String): Flow<ConversationEntity?>

    @Upsert
    fun upsertConversations(conversationEntities: List<ConversationEntity>)

    /**
     * Deletes rows in the db matching the specified [conversationIds]
     */
    @Query(
        value = """
            DELETE FROM conversations
            WHERE internalId in (:conversationIds)
        """
    )
    fun deleteConversations(conversationIds: List<String>)

    @Update
    fun updateConversation(conversationEntity: ConversationEntity)

    @Query(
        """
        DELETE FROM Conversations
        WHERE accountId = :accountId
        """
    )
    fun clearAllConversationsForUser(accountId: Long)
}
