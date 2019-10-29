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

package com.nextcloud.talk.newarch.local.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.RoomWarnings
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.models.json.chat.ChatMessage.SystemMessageType

@Entity(
    tableName = "messages",
    indices = [Index(value = ["conversation"]), Index(value = ["user", "conversation"])],
    foreignKeys = [ForeignKey(
        entity = ConversationEntity::class,
        parentColumns = arrayOf("id"),
        childColumns = arrayOf("conversation"),
        onDelete = ForeignKey.CASCADE,
        deferred = true
    )]
)
data class MessageEntity(
  @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") var id: Long? = null,
  @ColumnInfo(name = "user") var user: Long? = 0,
  @ColumnInfo(name = "conversation") var conversation: Long? = null,
  @ColumnInfo(name = "message_id") var messageId: Long = 0,
  @ColumnInfo(name = "actor_id") var actorId: String? = null,
  @ColumnInfo(name = "actor_type") var actorType: String? = null,
  @ColumnInfo(name = "actor_display_name") var actorDisplayName: String? = null,
  @ColumnInfo(name = "timestamp") var timestamp: Long = 0,
  @ColumnInfo(name = "message") var message: String? = null,
    /*@JsonField(name = "messageParameters")
    public HashMap<String, HashMap<String, String>> messageParameters;*/
  @ColumnInfo(name = "system_message_type") var systemMessageType: SystemMessageType? = null
)

@SuppressWarnings(RoomWarnings.CURSOR_MISMATCH)
fun MessageEntity.toChatMessage(): ChatMessage {
  val chatMessage = ChatMessage()
  chatMessage.internalMessageId = this.id
  chatMessage.internalUserId = this.user
  chatMessage.internalConversationId = this.conversation
  chatMessage.jsonMessageId = this.messageId
  chatMessage.actorType = this.actorType
  chatMessage.actorId = this.actorId
  chatMessage.actorDisplayName = this.actorDisplayName
  chatMessage.timestamp = this.timestamp
  chatMessage.message = this.message
  //chatMessage.messageParameters = this.messageParameters
  chatMessage.systemMessageType = this.systemMessageType
  return chatMessage
}

@SuppressWarnings(RoomWarnings.CURSOR_MISMATCH)
fun ChatMessage.toMessageEntity(): MessageEntity {
  val messageEntity = MessageEntity()
  messageEntity.id = this.internalMessageId
  messageEntity.user = this.internalUserId
  messageEntity.conversation = this.internalConversationId
  messageEntity.messageId = this.jsonMessageId
  messageEntity.actorType = this.actorType
  messageEntity.actorId = this.actorId
  messageEntity.actorDisplayName = this.actorDisplayName
  messageEntity.timestamp = this.timestamp
  messageEntity.message = this.message
  messageEntity.systemMessageType = this.systemMessageType
  //messageEntity.messageParameters = this.messageParameters

  return messageEntity
}