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
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nextcloud.talk.data.user.model.UserEntity
import com.nextcloud.talk.models.MessageDraft
import com.nextcloud.talk.models.json.conversations.ConversationEnums
import com.nextcloud.talk.models.json.participants.Participant

@Entity(
    tableName = "Conversations",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("accountId"),
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["accountId"])
    ]
)
data class ConversationEntity(
    // MOST IMPORTANT ATTRIBUTES

    @PrimaryKey
    @ColumnInfo(name = "internalId")
    var internalId: String,

    // Defines to which talk app account this conversation belongs to
    @ColumnInfo(name = "accountId") var accountId: Long,

    // We don't use token as primary key as we have to manage multiple talk app accounts on
    // the phone, thus multiple accounts can have the same conversation in their list. That's why the servers
    // conversation token is not suitable as primary key on the phone. Also the conversation attributes such as
    // "unread message" etc only match a specific account.
    // If multiple talk app accounts have the same conversation, it is stored as another dataset, which is
    // exactly what we want for this case.
    @ColumnInfo(name = "token") var token: String,

    @ColumnInfo(name = "displayName") var displayName: String,

    // OTHER ATTRIBUTES IN ALPHABETICAL ORDER
    @ColumnInfo(name = "actorId") var actorId: String,
    @ColumnInfo(name = "actorType") var actorType: String,
    @ColumnInfo(name = "avatarVersion") var avatarVersion: String,
    @ColumnInfo(name = "callFlag") var callFlag: Int = 0,
    @ColumnInfo(name = "callRecording") var callRecording: Int = 0,
    @ColumnInfo(name = "callStartTime") var callStartTime: Long = 0,
    @ColumnInfo(name = "canDeleteConversation") var canDeleteConversation: Boolean,
    @ColumnInfo(name = "canLeaveConversation") var canLeaveConversation: Boolean,
    @ColumnInfo(name = "canStartCall") var canStartCall: Boolean = false,
    @ColumnInfo(name = "description") var description: String,
    @ColumnInfo(name = "hasCall") var hasCall: Boolean = false,
    @ColumnInfo(name = "hasPassword") var hasPassword: Boolean = false,
    @ColumnInfo(name = "isCustomAvatar") var hasCustomAvatar: Boolean,
    @ColumnInfo(name = "isFavorite") var favorite: Boolean = false,
    @ColumnInfo(name = "lastActivity") var lastActivity: Long = 0,
    @ColumnInfo(name = "lastCommonReadMessage") var lastCommonReadMessage: Int = 0,
    @ColumnInfo(name = "lastMessage") var lastMessage: String? = null,
    @ColumnInfo(name = "lastPing") var lastPing: Long = 0,
    @ColumnInfo(name = "lastReadMessage") var lastReadMessage: Int = 0,
    @ColumnInfo(name = "lobbyState") var lobbyState: ConversationEnums.LobbyState,
    @ColumnInfo(name = "lobbyTimer") var lobbyTimer: Long = 0,
    @ColumnInfo(name = "messageExpiration") var messageExpiration: Int = 0,
    @ColumnInfo(name = "name") var name: String,
    @ColumnInfo(name = "notificationCalls") var notificationCalls: Int = 0,
    @ColumnInfo(name = "notificationLevel") var notificationLevel: ConversationEnums.NotificationLevel,
    @ColumnInfo(name = "objectType") var objectType: ConversationEnums.ObjectType,
    @ColumnInfo(name = "objectId") var objectId: String,
    @ColumnInfo(name = "participantType") var participantType: Participant.ParticipantType,
    @ColumnInfo(name = "permissions") var permissions: Int = 0,
    @ColumnInfo(name = "readOnly") var conversationReadOnlyState: ConversationEnums.ConversationReadOnlyState,
    @ColumnInfo(name = "recordingConsent") var recordingConsentRequired: Int = 0,
    @ColumnInfo(name = "remoteServer") var remoteServer: String? = null,
    @ColumnInfo(name = "remoteToken") var remoteToken: String? = null,
    @ColumnInfo(name = "sessionId") var sessionId: String,
    @ColumnInfo(name = "status") var status: String? = null,
    @ColumnInfo(name = "statusClearAt") var statusClearAt: Long? = 0,
    @ColumnInfo(name = "statusIcon") var statusIcon: String? = null,
    @ColumnInfo(name = "statusMessage") var statusMessage: String? = null,
    @ColumnInfo(name = "type") var type: ConversationEnums.ConversationType,
    @ColumnInfo(name = "unreadMention") var unreadMention: Boolean = false,
    @ColumnInfo(name = "unreadMentionDirect") var unreadMentionDirect: Boolean,
    @ColumnInfo(name = "unreadMessages") var unreadMessages: Int = 0,
    @ColumnInfo(name = "hasArchived") var hasArchived: Boolean = false,
    @ColumnInfo(name = "hasSensitive") var hasSensitive: Boolean = false,
    @ColumnInfo(name = "hasImportant") var hasImportant: Boolean = false,
    @ColumnInfo(name = "messageDraft") var messageDraft: MessageDraft? = MessageDraft()
    // missing/not needed: attendeeId
    // missing/not needed: attendeePin
    // missing/not needed: attendeePermissions
    // missing/not needed: callPermissions
    // missing/not needed: defaultPermissions
    // missing/not needed: participantInCall
    // missing/not needed: participantFlags
    // missing/not needed: listable
    // missing/not needed: count
    // missing/not needed: numGuests
    // missing/not needed: sipEnabled
    // missing/not needed: canEnableSIP
    // missing/not needed: objectId
    // missing/not needed: breakoutRoomMode
    // missing/not needed: breakoutRoomStatus
)
