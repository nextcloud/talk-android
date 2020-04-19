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

package com.nextcloud.talk.newarch.local.models

import androidx.room.*
import androidx.room.ForeignKey.CASCADE
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.models.json.chat.ChatMessage.SystemMessageType
import com.nextcloud.talk.newarch.local.models.other.ChatMessageStatus

@Entity(
        tableName = "messages",
        indices = [Index(value = ["conversation_id"])],
        foreignKeys = [ForeignKey(
                entity = ConversationEntity::class,
                deferred = true,
                parentColumns = arrayOf("id"),
                childColumns = arrayOf("conversation_id"),
                onDelete = CASCADE,
                onUpdate = CASCADE
        )]
)
data class MessageEntity(
        @PrimaryKey @ColumnInfo(name = "id") var id: String,
        @ColumnInfo(name = "conversation_id") var conversationId: String,
        @ColumnInfo(name = "message_id") var messageId: Long = 0,
        @ColumnInfo(name = "actor_id") var actorId: String? = null,
        @ColumnInfo(name = "actor_type") var actorType: String? = null,
        @ColumnInfo(name = "actor_display_name") var actorDisplayName: String? = null,
        @ColumnInfo(name = "timestamp") var timestamp: Long = 0,
        @ColumnInfo(name = "message") var message: String? = null,
        @ColumnInfo(name = "messageParameters") var messageParameters: HashMap<String, HashMap<String, String>>? = null,
        @ColumnInfo(name = "parent") var parentMessage: ChatMessage? = null,
        @ColumnInfo(name = "replyable") var replyable: Boolean = false,
        @ColumnInfo(name = "system_message_type") var systemMessageType: SystemMessageType? = null,
        @ColumnInfo(name = "reference_id") var referenceId: String? = null,
        @ColumnInfo(name = "message_status") var chatMessageStatus: ChatMessageStatus = ChatMessageStatus.RECEIVED
)

fun MessageEntity.toChatMessage(): ChatMessage {
    val chatMessage = ChatMessage()
    chatMessage.internalMessageId = this.id
    chatMessage.internalConversationId = this.conversationId
    chatMessage.jsonMessageId = this.messageId
    chatMessage.actorType = this.actorType
    chatMessage.actorId = this.actorId
    chatMessage.actorDisplayName = this.actorDisplayName
    chatMessage.timestamp = this.timestamp
    chatMessage.message = this.message
    chatMessage.messageParameters = this.messageParameters
    chatMessage.systemMessageType = this.systemMessageType
    chatMessage.replyable = this.replyable
    chatMessage.parentMessage = this.parentMessage
    chatMessage.referenceId = this.referenceId
    chatMessage.chatMessageStatus = this.chatMessageStatus
    return chatMessage
}

fun ChatMessage.toMessageEntity(): MessageEntity {
    val messageEntityId = if (this.internalMessageId != null) internalMessageId else this.internalConversationId + "@" + this.jsonMessageId
    val messageEntity = MessageEntity(messageEntityId!!, this.internalConversationId!!)
    messageEntity.messageId = this.jsonMessageId ?: 0
    messageEntity.actorType = this.actorType
    messageEntity.actorId = this.actorId
    messageEntity.actorDisplayName = this.actorDisplayName
    messageEntity.timestamp = this.timestamp
    messageEntity.message = this.message
    messageEntity.systemMessageType = this.systemMessageType
    messageEntity.replyable = this.replyable
    messageEntity.messageParameters = this.messageParameters
    messageEntity.parentMessage = this.parentMessage
    messageEntity.referenceId = this.referenceId
    messageEntity.chatMessageStatus = this.chatMessageStatus
    return messageEntity
}
