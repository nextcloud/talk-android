/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2019 Mario Danic <mario@lovelyhq.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.newarch.local.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.nextcloud.talk.newarch.local.models.ConversationEntity

@Dao
abstract class ConversationsDao {

  @Query("SELECT * FROM conversations WHERE user = :userId ORDER BY favorite DESC, last_activity DESC")
  abstract fun getConversationsForUser(userId: Long): LiveData<List<ConversationEntity>>

  @Query("DELETE FROM conversations WHERE user = :userId")
  abstract suspend fun clearConversationsForUser(userId: Long)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract suspend fun saveConversationWithInsert(conversation: ConversationEntity): Long

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract suspend fun saveConversationsWithInsert(vararg conversations: ConversationEntity): List<Long>

  @Query(
      "UPDATE conversations SET changing = :changing WHERE user = :userId AND conversation_id = :conversationId"
  )
  abstract suspend fun updateChangingValueForConversation(
    userId: Long,
    conversationId: String,
    changing: Boolean
  )

  @Query(
      "UPDATE conversations SET favorite = :favorite, changing = 0 WHERE user = :userId AND conversation_id = :conversationId"
  )
  abstract suspend fun updateFavoriteValueForConversation(
    userId: Long,
    conversationId: String,
    favorite: Boolean
  )

  @Query("DELETE FROM conversations WHERE user = :userId AND conversation_id = :conversationId")
  abstract suspend fun deleteConversation(
    userId: Long,
    conversationId: String
  )

  @Delete
  abstract suspend fun deleteConversations(vararg conversation: ConversationEntity)

  @Query("DELETE FROM conversations WHERE user = :userId AND modified_at < :timestamp")
  abstract suspend fun deleteConversationsForUserWithTimestamp(
    userId: Long,
    timestamp: Long
  )

  @Transaction
  open suspend fun updateConversationsForUser(
    userId: Long,
    newConversations: Array<ConversationEntity>
  ) {
    val timestamp = System.currentTimeMillis()

    val conversationsWithTimestampApplied = newConversations.map {
      it.modifiedAt = timestamp
      it
    }

    saveConversationsWithInsert(*conversationsWithTimestampApplied.toTypedArray())
    deleteConversationsForUserWithTimestamp(userId, timestamp)
  }
}