/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.data.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.nextcloud.talk.data.user.model.UserEntity
import com.nextcloud.talk.models.domain.ConversationReadOnlyState
import com.nextcloud.talk.models.domain.ConversationType
import com.nextcloud.talk.models.domain.LobbyState
import com.nextcloud.talk.models.domain.NotificationLevel
import com.nextcloud.talk.models.domain.ObjectType
import com.nextcloud.talk.models.domain.ParticipantType

// TODO: ConversationEntity.java:5: warning: internal_user_id column references a foreign key but it is not part of an
// index. This may trigger full table scans whenever parent table is modified so you are highly advised to create an index that covers this column.
// public final class ConversationEntity {

@Entity(
    tableName = "Conversations",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("account_id"),
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    var id: Long = 0,

    // Defines to which talk app account this conversation belongs to
    @ColumnInfo(name = "account_id") var accountId: Long? = null,

    // TODO: make sure that "id" from server can really be omitted. Also see iOS app
    // @ColumnInfo(name = "conversation_id") var conversationId: Int? = null,

    // We don't use token as primary key as we have to manage multiple talk app accounts on
    // the phone, thus multiple accounts can have the same conversation in their list. That's why the servers
    // conversation token is not suitable as primary key on the phone. Also the conversation attributes such as
    // "unread message" etc only match a specific account.
    // If multiple talk app accounts have the same conversation, it is stored as another dataset, which is
    // exactly what we want for this case.
    @ColumnInfo(name = "token") var token: String?,

    @ColumnInfo(name = "name") var name: String? = null,
    @ColumnInfo(name = "displayName") var displayName: String? = null,
    @ColumnInfo(name = "description") var description: String? = null,
    @ColumnInfo(name = "type") var type: ConversationType? = null,
    @ColumnInfo(name = "lastPing") var lastPing: Long = 0,
    @ColumnInfo(name = "participantType") var participantType: ParticipantType? = null,
    @ColumnInfo(name = "hasPassword") var hasPassword: Boolean = false,
    @ColumnInfo(name = "sessionId") var sessionId: String? = null,
    @ColumnInfo(name = "actorId") var actorId: String? = null,
    @ColumnInfo(name = "actorType") var actorType: String? = null,
    @ColumnInfo(name = "isFavorite") var favorite: Boolean = false,
    @ColumnInfo(name = "lastActivity") var lastActivity: Long = 0,
    @ColumnInfo(name = "unreadMessages") var unreadMessages: Int = 0,
    @ColumnInfo(name = "unreadMention") var unreadMention: Boolean = false,
    @ColumnInfo(name = "lastMessage") var lastMessageId: Long? = null,
    @ColumnInfo(name = "objectType") var objectType: ObjectType? = null,
    @ColumnInfo(name = "notificationLevel") var notificationLevel: NotificationLevel? = null,
    @ColumnInfo(name = "readOnly") var conversationReadOnlyState: ConversationReadOnlyState? = null,
    @ColumnInfo(name = "lobbyState") var lobbyState: LobbyState? = null,
    @ColumnInfo(name = "lobbyTimer") var lobbyTimer: Long? = null,
    @ColumnInfo(name = "lastReadMessage") var lastReadMessage: Int = 0,
    @ColumnInfo(name = "hasCall") var hasCall: Boolean = false,
    @ColumnInfo(name = "callFlag") var callFlag: Int = 0,
    @ColumnInfo(name = "canStartCall") var canStartCall: Boolean = false,
    @ColumnInfo(name = "canLeaveConversation") var canLeaveConversation: Boolean? = null,
    @ColumnInfo(name = "canDeleteConversation") var canDeleteConversation: Boolean? = null,
    @ColumnInfo(name = "unreadMentionDirect") var unreadMentionDirect: Boolean? = null,
    @ColumnInfo(name = "notificationCalls") var notificationCalls: Int? = null,
    @ColumnInfo(name = "permissions") var permissions: Int = 0,
    @ColumnInfo(name = "messageExpiration") var messageExpiration: Int = 0,
    @ColumnInfo(name = "status") var status: String? = null,
    @ColumnInfo(name = "statusIcon") var statusIcon: String? = null,
    @ColumnInfo(name = "statusMessage") var statusMessage: String? = null,
    @ColumnInfo(name = "statusClearAt") var statusClearAt: Long? = 0,
    @ColumnInfo(name = "callRecording") var callRecording: Int = 0,
    @ColumnInfo(name = "avatarVersion") var avatarVersion: String? = null,
    @ColumnInfo(name = "isCustomAvatar") var hasCustomAvatar: Boolean? = null,
    @ColumnInfo(name = "callStartTime") var callStartTime: Long? = null,
    @ColumnInfo(name = "recordingConsent") var recordingConsentRequired: Int = 0,
    @ColumnInfo(name = "remoteServer") var remoteServer: String? = null,
    @ColumnInfo(name = "remoteToken") var remoteToken: String? = null
)
