/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2021 Tim Krüger <t@timkrueger.me>
 * SPDX-FileCopyrightText: 2017 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models.json.conversations

import android.os.Parcelable
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import com.nextcloud.talk.models.json.chat.ChatMessageJson
import com.nextcloud.talk.models.json.converters.ConversationObjectTypeConverter
import com.nextcloud.talk.models.json.converters.EnumLobbyStateConverter
import com.nextcloud.talk.models.json.converters.EnumNotificationLevelConverter
import com.nextcloud.talk.models.json.converters.EnumParticipantTypeConverter
import com.nextcloud.talk.models.json.converters.EnumReadOnlyConversationConverter
import com.nextcloud.talk.models.json.converters.EnumRoomTypeConverter
import com.nextcloud.talk.models.json.participants.Participant.ParticipantType
import kotlinx.parcelize.Parcelize

@Parcelize
@JsonObject
data class Conversation(
    @JsonField(name = ["token"])
    var token: String = "",

    @JsonField(name = ["name"])
    var name: String = "",

    @JsonField(name = ["displayName"])
    var displayName: String = "",

    @JsonField(name = ["description"])
    var description: String = "",

    @JsonField(name = ["type"], typeConverter = EnumRoomTypeConverter::class)
    var type: ConversationEnums.ConversationType = ConversationEnums.ConversationType.DUMMY,

    @JsonField(name = ["lastPing"])
    var lastPing: Long = 0,

    @JsonField(name = ["participantType"], typeConverter = EnumParticipantTypeConverter::class)
    var participantType: ParticipantType = ParticipantType.DUMMY,

    @JsonField(name = ["hasPassword"])
    var hasPassword: Boolean = false,

    @JsonField(name = ["sessionId"])
    var sessionId: String = "0",

    @JsonField(name = ["actorId"])
    var actorId: String = "",

    @JsonField(name = ["actorType"])
    var actorType: String = "",

    // check if this can be removed. Doesn't belong to api-response but is used internally?
    var password: String? = null,

    @JsonField(name = ["isFavorite"])
    var favorite: Boolean = false,

    @JsonField(name = ["lastActivity"])
    var lastActivity: Long = 0,

    @JsonField(name = ["unreadMessages"])
    var unreadMessages: Int = 0,

    @JsonField(name = ["unreadMention"])
    var unreadMention: Boolean = false,

    @JsonField(name = ["lastMessage"])
    var lastMessage: ChatMessageJson? = null,

    @JsonField(name = ["objectType"], typeConverter = ConversationObjectTypeConverter::class)
    var objectType: ConversationEnums.ObjectType = ConversationEnums.ObjectType.DEFAULT,

    @JsonField(name = ["objectId"])
    var objectId: String = "",

    @JsonField(name = ["notificationLevel"], typeConverter = EnumNotificationLevelConverter::class)
    var notificationLevel: ConversationEnums.NotificationLevel = ConversationEnums.NotificationLevel.DEFAULT,

    @JsonField(name = ["readOnly"], typeConverter = EnumReadOnlyConversationConverter::class)
    var conversationReadOnlyState: ConversationEnums.ConversationReadOnlyState =
        ConversationEnums.ConversationReadOnlyState.CONVERSATION_READ_WRITE,

    @JsonField(name = ["lobbyState"], typeConverter = EnumLobbyStateConverter::class)
    var lobbyState: ConversationEnums.LobbyState = ConversationEnums.LobbyState.LOBBY_STATE_ALL_PARTICIPANTS,

    @JsonField(name = ["lobbyTimer"])
    var lobbyTimer: Long = 0,

    @JsonField(name = ["lastReadMessage"])
    var lastReadMessage: Int = 0,

    @JsonField(name = ["lastCommonReadMessage"])
    var lastCommonReadMessage: Int = 0,

    @JsonField(name = ["hasCall"])
    var hasCall: Boolean = false,

    @JsonField(name = ["callFlag"])
    var callFlag: Int = 0,

    @JsonField(name = ["canStartCall"])
    var canStartCall: Boolean = false,

    @JsonField(name = ["canLeaveConversation"])
    var canLeaveConversation: Boolean = true,

    @JsonField(name = ["canDeleteConversation"])
    var canDeleteConversation: Boolean = false,

    @JsonField(name = ["unreadMentionDirect"])
    var unreadMentionDirect: Boolean = false,

    @JsonField(name = ["notificationCalls"])
    var notificationCalls: Int = 0,

    @JsonField(name = ["permissions"])
    var permissions: Int = 0,

    @JsonField(name = ["messageExpiration"])
    var messageExpiration: Int = 0,

    @JsonField(name = ["status"])
    var status: String? = "",

    @JsonField(name = ["statusIcon"])
    var statusIcon: String? = "",

    @JsonField(name = ["statusMessage"])
    var statusMessage: String? = "",

    @JsonField(name = ["statusClearAt"])
    var statusClearAt: Long? = null,

    @JsonField(name = ["callRecording"])
    var callRecording: Int = 0,

    @JsonField(name = ["avatarVersion"])
    var avatarVersion: String = "",

    // Be aware that variables with "is" at the beginning will lead to the error:
    // "@JsonField annotation can only be used on private fields if both getter and setter are present."
    // Instead, name it with "has" at the beginning: isCustomAvatar -> hasCustomAvatar
    @JsonField(name = ["isCustomAvatar"])
    var hasCustomAvatar: Boolean = false,

    @JsonField(name = ["callStartTime"])
    var callStartTime: Long = 0L,

    @JsonField(name = ["recordingConsent"])
    var recordingConsentRequired: Int = 0,

    @JsonField(name = ["remoteServer"])
    var remoteServer: String? = "",

    @JsonField(name = ["remoteToken"])
    var remoteToken: String? = "",

    @JsonField(name = ["isArchived"])
    var hasArchived: Boolean = false,

    @JsonField(name = ["isSensitive"])
    var hasSensitive: Boolean = false,

    @JsonField(name = ["isImportant"])
    var hasImportant: Boolean = false
) : Parcelable
