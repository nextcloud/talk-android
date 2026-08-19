/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy.Companion.REPLACE
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.nextcloud.talk.data.database.model.ConversationEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

@Dao
interface ConversationsDao {
    @Query("SELECT * FROM Conversations where accountId = :accountId")
    fun getConversationsForUser(accountId: Long): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM Conversations where accountId = :accountId AND token = :token")
    fun getConversationForUser(accountId: Long, token: String): Flow<ConversationEntity?>

    /**
     * Applies a full room list sync atomically: left conversations are deleted and the server
     * items are upserted in one transaction, so observers of the conversations table see a single
     * consistent update per sync instead of intermediate states.
     */
    @Transaction
    suspend fun syncConversationsForUser(
        accountId: Long,
        serverItems: List<ConversationEntity>,
        conversationIdsToDelete: List<String>
    ) {
        if (conversationIdsToDelete.isNotEmpty()) {
            deleteConversations(conversationIdsToDelete)
        }
        upsertConversations(accountId, serverItems)
    }

    @Transaction
    suspend fun upsertConversations(accountId: Long, serverItems: List<ConversationEntity>) {
        serverItems.forEach { serverItem ->
            val existingItem = getConversationForUser(accountId, serverItem.token).first()
            if (existingItem != null) {
                val mergedItem = serverItem.copy()
                mergedItem.messageDraft = existingItem.messageDraft
                mergedItem.hiddenUpcomingEvent = existingItem.hiddenUpcomingEvent
                updateConversation(mergedItem)
            } else {
                insertConversation(serverItem)
            }
        }
    }

    /**
     * Reflects a background message catch-up in the conversation entry. A single guarded UPDATE:
     * it only applies while the derived state is newer than the stored one ([lastActivity] guard),
     * so a concurrently running room list sync — which stays authoritative — can never be
     * overwritten with older data and no read-modify-write window exists. An [unreadMessages]
     * value below zero keeps the stored count (used when the count cannot be derived locally).
     *
     * @return the number of updated rows: 1 when applied, 0 when the stored state was newer.
     */
    @Query(
        """
        UPDATE Conversations
        SET lastMessage = :lastMessageJson,
            lastActivity = :lastActivity,
            unreadMessages = CASE WHEN :unreadMessages >= 0 THEN :unreadMessages ELSE unreadMessages END
        WHERE internalId = :internalId
        AND lastActivity < :lastActivity
        """
    )
    suspend fun updateConversationFromCatchUp(
        internalId: String,
        lastMessageJson: String,
        lastActivity: Long,
        unreadMessages: Int
    ): Int

    /**
     * Optimistically writes the user's read state for a conversation. Deliberately unguarded:
     * marking as unread moves the read marker backwards, so a user action always wins locally —
     * and the next room list sync re-asserts the server state either way.
     */
    @Query(
        """
        UPDATE Conversations
        SET lastReadMessage = :lastReadMessage,
            unreadMessages = :unreadMessages
        WHERE internalId = :internalId
        """
    )
    suspend fun updateReadState(internalId: String, lastReadMessage: Int, unreadMessages: Int)

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

    @Update(onConflict = REPLACE)
    fun updateConversation(conversationEntity: ConversationEntity)

    @Insert(onConflict = REPLACE)
    fun insertConversation(conversation: ConversationEntity)

    @Query(
        """
        DELETE FROM Conversations
        WHERE accountId = :accountId
        """
    )
    fun clearAllConversationsForUser(accountId: Long)
}
