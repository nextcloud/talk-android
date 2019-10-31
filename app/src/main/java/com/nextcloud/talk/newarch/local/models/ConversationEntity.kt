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
import androidx.room.ForeignKey.CASCADE
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.conversations.Conversation.ConversationReadOnlyState
import com.nextcloud.talk.models.json.conversations.Conversation.ConversationType
import com.nextcloud.talk.models.json.conversations.Conversation.LobbyState
import com.nextcloud.talk.models.json.conversations.Conversation.NotificationLevel
import com.nextcloud.talk.models.json.participants.Participant.ParticipantType
import java.util.HashMap

@Entity(
    tableName = "conversations",
    indices = [Index(value = ["user", "conversation_id"], unique = true)],
    foreignKeys = [ForeignKey(
        entity = UserNgEntity::class,
        parentColumns = arrayOf("id"),
        childColumns = arrayOf("user"),
        onDelete = CASCADE,
        onUpdate = CASCADE,
        deferred = true
    )]
)
data class ConversationEntity(
  @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") var id: Long? = 0,
  @ColumnInfo(name = "user") var user: Long?,
  @ColumnInfo(name = "conversation_id") var conversationId: String?,
  @ColumnInfo(name = "token") var token: String? = null,
  @ColumnInfo(name = "name") var name: String? = null,
  @ColumnInfo(name = "display_name") var displayName: String? = null,
  @ColumnInfo(name = "type") var type: ConversationType? = null,
  @ColumnInfo(name = "count") var count: Long = 0,
  @ColumnInfo(name = "number_of_guests") var numberOfGuests: Long = 0,
    /*@ColumnInfo(name = "guest_list") var guestList: HashMap<String, HashMap<String, Any>>? = null,
    @ColumnInfo(name = "participants") var participants: HashMap<String, HashMap<String, Any>>? =
    null,
     */
    // hack for participants list
  @ColumnInfo(name = "participants_count") var participantsCount: Int = 0,
  @ColumnInfo(name = "participant_type") var participantType: ParticipantType? = null,
  @ColumnInfo(name = "has_password") var hasPassword: Boolean = false,
  @ColumnInfo(name = "session_id") var sessionId: String? = null,
  @ColumnInfo(name = "favorite") var favorite: Boolean = false,
  @ColumnInfo(name = "last_activity") var lastActivity: Long = 0,
  @ColumnInfo(name = "unread_messages") var unreadMessages: Int = 0,
  @ColumnInfo(name = "unread_mention") var unreadMention: Boolean = false,
  @ColumnInfo(name = "last_message") var lastMessage: ChatMessage? = null,
  @ColumnInfo(name = "object_type") var objectType: String? = null,
  @ColumnInfo(name = "notification_level") var notificationLevel: NotificationLevel? = null,
  @ColumnInfo(
      name = "read_only_state"
  ) var conversationReadOnlyState: ConversationReadOnlyState? = null,
  @ColumnInfo(name = "lobby_state") var lobbyState: LobbyState? = null,
  @ColumnInfo(name = "lobby_timer") var lobbyTimer: Long? = null,
  @ColumnInfo(name = "last_read_message") var lastReadMessageId: Long = 0,
  @ColumnInfo(name = "modified_at") var modifiedAt: Long? = null,
  @ColumnInfo(name = "changing") var changing: Boolean = false
)

fun ConversationEntity.toConversation(): Conversation {
  val conversation = Conversation()
  conversation.internalUserId = this.user
  conversation.conversationId = this.conversationId
  conversation.type = this.type
  conversation.token = this.token
  conversation.name = this.name
  conversation.displayName = this.displayName
  conversation.count = this.count
  conversation.numberOfGuests = this.numberOfGuests
  conversation.participants = HashMap()
  for (i in 0 until participantsCount) {
    conversation.participants?.put(i.toString(), HashMap())
  }
  conversation.participantType = this.participantType
  conversation.hasPassword = this.hasPassword
  conversation.sessionId = this.sessionId
  conversation.favorite = this.favorite
  conversation.lastActivity = this.lastActivity
  conversation.unreadMessages = this.unreadMessages
  conversation.unreadMention = this.unreadMention
  conversation.lastMessage = this.lastMessage
  conversation.objectType = this.objectType
  conversation.notificationLevel = this.notificationLevel
  conversation.conversationReadOnlyState = this.conversationReadOnlyState
  conversation.lobbyState = this.lobbyState
  conversation.lobbyTimer = this.lobbyTimer
  conversation.lastReadMessageId = this.lastReadMessageId
  conversation.changing = this.changing

  return conversation
}

fun Conversation.toConversationEntity(): ConversationEntity {
  val conversationEntity = ConversationEntity(null, this.internalUserId, this.conversationId)
  conversationEntity.token = this.token
  conversationEntity.name = this.name
  conversationEntity.displayName = this.displayName
  conversationEntity.count = this.count
  conversationEntity.numberOfGuests = this.numberOfGuests
  conversationEntity.participantsCount = this.participants?.size ?: 0
  conversationEntity.participantType = this.participantType
  conversationEntity.hasPassword = this.hasPassword
  conversationEntity.sessionId = this.sessionId
  conversationEntity.favorite = this.favorite
  conversationEntity.lastActivity = this.lastActivity
  conversationEntity.unreadMessages = this.unreadMessages
  conversationEntity.unreadMention = this.unreadMention
  conversationEntity.lastMessage = this.lastMessage
  conversationEntity.objectType = this.objectType
  conversationEntity.notificationLevel = this.notificationLevel
  conversationEntity.conversationReadOnlyState = this.conversationReadOnlyState
  conversationEntity.lobbyState = this.lobbyState
  conversationEntity.lobbyTimer = this.lobbyTimer
  conversationEntity.lastReadMessageId = this.lastReadMessageId
  conversationEntity.type = this.type
  conversationEntity.changing = this.changing

  return conversationEntity
}