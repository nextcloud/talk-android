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

import androidx.room.*
import androidx.room.ForeignKey.CASCADE
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.conversations.Conversation.*
import com.nextcloud.talk.models.json.participants.Participant
import com.nextcloud.talk.models.json.participants.Participant.ParticipantType
import java.util.*

@Entity(
        tableName = "conversations",
        indices = [Index(value = ["user_id", "token"], unique = true), Index(value = ["user_id"])],
        foreignKeys = [ForeignKey(
                entity = UserNgEntity::class,
                parentColumns = arrayOf("id"),
                childColumns = arrayOf("user_id"),
                onDelete = CASCADE,
                onUpdate = CASCADE,
                deferred = true
        )]
)
data class ConversationEntity(
        @PrimaryKey @ColumnInfo(name = "id") var id: String,
        @ColumnInfo(name = "user_id") var userId: Long? = null,
        @ColumnInfo(name = "conversation_id") var conversationId: String? = null,
        @ColumnInfo(name = "token") var token: String? = null,
        @ColumnInfo(name = "name") var name: String? = null,
        @ColumnInfo(name = "display_name") var displayName: String? = null,
        @ColumnInfo(name = "type") var type: ConversationType? = null,
        @ColumnInfo(name = "count") var count: Long = 0,
        @ColumnInfo(name = "number_of_guests") var numberOfGuests: Long = 0,
        @ColumnInfo(name = "participants") var participants: HashMap<String, Participant>? = null,
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
        @ColumnInfo(name = "can_start_call") var canStartCall: Boolean? = true,

        @ColumnInfo(name = "modified_at") var modifiedAt: Long? = null,
        @ColumnInfo(name = "changing") var changing: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ConversationEntity

        if (id != other.id) return false
        if (userId != other.userId) return false
        if (conversationId != other.conversationId) return false
        if (token != other.token) return false
        if (name != other.name) return false
        if (displayName != other.displayName) return false
        if (type != other.type) return false
        if (count != other.count) return false
        if (numberOfGuests != other.numberOfGuests) return false
        if (participants != other.participants) return false
        if (participantType != other.participantType) return false
        if (hasPassword != other.hasPassword) return false
        if (sessionId != other.sessionId) return false
        if (favorite != other.favorite) return false
        if (lastActivity != other.lastActivity) return false
        if (unreadMessages != other.unreadMessages) return false
        if (unreadMention != other.unreadMention) return false
        if (lastMessage != other.lastMessage) return false
        if (objectType != other.objectType) return false
        if (notificationLevel != other.notificationLevel) return false
        if (conversationReadOnlyState != other.conversationReadOnlyState) return false
        if (lobbyState != other.lobbyState) return false
        if (lobbyTimer != other.lobbyTimer) return false
        if (canStartCall != other.canStartCall) return false
        if (lastReadMessageId != other.lastReadMessageId) return false
        if (changing != other.changing) return false

        return true
    }

    override fun hashCode(): Int {
        var result = userId?.hashCode() ?: 0
        result = 31 * result + (token?.hashCode() ?: 0)
        return result
    }
}

fun ConversationEntity.toConversation(): Conversation {
    val conversation = Conversation()
    conversation.databaseId = this.id
    conversation.databaseUserId = this.userId
    conversation.conversationId = this.conversationId
    conversation.type = this.type
    conversation.token = this.token
    conversation.name = this.name
    conversation.displayName = this.displayName
    conversation.count = this.count
    conversation.numberOfGuests = this.numberOfGuests
    conversation.participants = this.participants
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
    conversation.canStartCall = this.canStartCall
    conversation.lastReadMessageId = this.lastReadMessageId
    conversation.changing = this.changing

    return conversation
}

fun Conversation.toConversationEntity(): ConversationEntity {
    val conversationEntity = ConversationEntity(this.databaseUserId.toString() + "@" + this.token)
    conversationEntity.userId = this.databaseUserId
    conversationEntity.conversationId = this.conversationId
    conversationEntity.token = this.token
    conversationEntity.name = this.name
    conversationEntity.displayName = this.displayName
    conversationEntity.count = this.count
    conversationEntity.numberOfGuests = this.numberOfGuests
    conversationEntity.participants = this.participants
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
    conversationEntity.canStartCall = this.canStartCall
    conversationEntity.type = this.type
    conversationEntity.changing = this.changing

    return conversationEntity
}