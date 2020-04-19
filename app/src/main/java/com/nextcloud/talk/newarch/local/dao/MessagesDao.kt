/*
 *
 *  * Nextcloud Talk application
 *  *
 *  * @author Mario Danic
 *  * Copyright (C) 2017-2020 Mario Danic <mario@lovelyhq.com>
 *  *
 *  * This program is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * at your option) any later version.
 *  *
 *  * This program is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.nextcloud.talk.newarch.local.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.nextcloud.talk.newarch.local.models.ConversationEntity
import com.nextcloud.talk.newarch.local.models.MessageEntity
import com.nextcloud.talk.newarch.local.models.User

@Dao
abstract class MessagesDao {
    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId ORDER BY timestamp ASC")
    abstract fun getMessagesWithUserForConversation(conversationId: String):
            LiveData<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun saveMessages(vararg messages: MessageEntity): List<Long>

    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId AND id = reference_id")
    abstract suspend fun getPendingMessages(conversationId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId and id = reference_id and message_status != 5 and message_status != 0")
    abstract fun getPendingMessagesLive(conversationId: String): LiveData<List<MessageEntity>>

    @Query(
            "UPDATE messages SET id = :newId WHERE conversation_id = :conversationId AND reference_id = :referenceId"
    )
    abstract suspend fun updateMessageId(newId: String, conversationId: String, referenceId: String)

    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId AND (message_id >= :messageId OR message_id = 0) ORDER BY timestamp ASC")
    abstract fun getMessagesWithUserForConversationSince(conversationId: String, messageId: Long): LiveData<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId AND message_id = :messageId")
    abstract fun getMessageForConversation(conversationId: String, messageId: Long): MessageEntity?

    @Query(
            "UPDATE messages SET message_status = :status WHERE conversation_id = :conversationId AND message_id = :messageId"
    )
    abstract suspend fun updateMessageStatus(
            status: Int,
            conversationId: String,
            messageId: Long
    )

    @Update(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun update(message: MessageEntity): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(message: MessageEntity)

    @Transaction
    open suspend fun updateMessages(user: User, messages: Array<MessageEntity>) {
        val messagesToUpdate = messages.toMutableList()
        if (messagesToUpdate.size > 0) {
            val conversationId = messagesToUpdate[0].conversationId
            val pendingMessages = getPendingMessages(conversationId)
            val pendingMessagesReferenceIds = pendingMessages.map { it.referenceId }
            messagesToUpdate.forEach {
                it.referenceId?.let { referenceId ->
                    if (pendingMessagesReferenceIds.contains(referenceId)) {
                        updateMessageId(it.id, it.conversationId, referenceId)
                    }
                }
            }

            messagesToUpdate.forEach { internalUpsert(it) }
        }
    }

    private suspend fun internalUpsert(message: MessageEntity) {
        val count = update(message)
        if (count == 0) {
            insert(message)
        }
    }

}