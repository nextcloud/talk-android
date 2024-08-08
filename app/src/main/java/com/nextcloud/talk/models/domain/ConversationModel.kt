/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023-2024 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2023 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models.domain

import com.nextcloud.talk.data.changeListVersion.SyncableModel
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.chat.ChatMessageJson
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.conversations.ConversationEnums
import com.nextcloud.talk.models.json.participants.Participant

class ConversationModel(
    var internalId: String,
    var roomId: String? = null,
    var token: String? = null,
    var name: String? = null,
    var displayName: String? = null,
    var description: String? = null,
    var type: ConversationEnums.ConversationType? = null,
    var lastPing: Long = 0,
    var participantType: Participant.ParticipantType? = null,
    var hasPassword: Boolean = false,
    var sessionId: String? = null,
    var actorId: String? = null,
    var actorType: String? = null,
    var password: String? = null,
    var favorite: Boolean = false,
    var lastActivity: Long = 0,
    var unreadMessages: Int = 0,
    var unreadMention: Boolean = false,
    // var lastMessageViaConversationList: LastMessageJson? = null,
    var lastMessageViaConversationList: ChatMessageJson? = null,
    var objectType: ConversationEnums.ObjectType? = null,
    var notificationLevel: ConversationEnums.NotificationLevel? = null,
    var conversationReadOnlyState: ConversationEnums.ConversationReadOnlyState? = null,
    var lobbyState: ConversationEnums.LobbyState? = null,
    var lobbyTimer: Long? = null,
    var lastReadMessage: Int = 0,
    var lastCommonReadMessage: Int = 0,
    var hasCall: Boolean = false,
    var callFlag: Int = 0,
    var canStartCall: Boolean = false,
    var canLeaveConversation: Boolean? = null,
    var canDeleteConversation: Boolean? = null,
    var unreadMentionDirect: Boolean? = null,
    var notificationCalls: Int? = null,
    var permissions: Int = 0,
    var messageExpiration: Int = 0,
    var status: String? = null,
    var statusIcon: String? = null,
    var statusMessage: String? = null,
    var statusClearAt: Long? = 0,
    var callRecording: Int = 0,
    var avatarVersion: String? = null,
    var hasCustomAvatar: Boolean? = null,
    var callStartTime: Long? = null,
    var recordingConsentRequired: Int = 0,
    var remoteServer: String? = null,
    var remoteToken: String? = null,
    override var id: Long = roomId?.toLong() ?: 0,
    override var markedForDeletion: Boolean = false
) : SyncableModel {

    companion object {
        fun mapToConversationModel(conversation: Conversation, user: User): ConversationModel {
            return ConversationModel(
                internalId = user.id!!.toString() + "@" + conversation.token,
                roomId = conversation.roomId,
                token = conversation.token,
                name = conversation.name,
                displayName = conversation.displayName,
                description = conversation.description,
                type = conversation.type?.let { ConversationEnums.ConversationType.valueOf(it.name) },
                lastPing = conversation.lastPing,
                participantType = conversation.participantType?.let { Participant.ParticipantType.valueOf(it.name) },
                hasPassword = conversation.hasPassword,
                sessionId = conversation.sessionId,
                actorId = conversation.actorId,
                actorType = conversation.actorType,
                password = conversation.password,
                favorite = conversation.favorite,
                lastActivity = conversation.lastActivity,
                unreadMessages = conversation.unreadMessages,
                unreadMention = conversation.unreadMention,
                // lastMessage = conversation.lastMessage,     to do...
                objectType = conversation.objectType?.let { ConversationEnums.ObjectType.valueOf(it.name) },
                notificationLevel = conversation.notificationLevel?.let {
                    ConversationEnums.NotificationLevel.valueOf(
                        it.name
                    )
                },
                conversationReadOnlyState = conversation.conversationReadOnlyState?.let {
                    ConversationEnums.ConversationReadOnlyState.valueOf(
                        it.name
                    )
                },
                lobbyState = conversation.lobbyState?.let { ConversationEnums.LobbyState.valueOf(it.name) },
                lobbyTimer = conversation.lobbyTimer,
                lastReadMessage = conversation.lastReadMessage,
                lastCommonReadMessage = conversation.lastCommonReadMessage,
                hasCall = conversation.hasCall,
                callFlag = conversation.callFlag,
                canStartCall = conversation.canStartCall,
                canLeaveConversation = conversation.canLeaveConversation,
                canDeleteConversation = conversation.canDeleteConversation,
                unreadMentionDirect = conversation.unreadMentionDirect,
                notificationCalls = conversation.notificationCalls,
                permissions = conversation.permissions,
                messageExpiration = conversation.messageExpiration,
                status = conversation.status,
                statusIcon = conversation.statusIcon,
                statusMessage = conversation.statusMessage,
                statusClearAt = conversation.statusClearAt,
                callRecording = conversation.callRecording,
                avatarVersion = conversation.avatarVersion,
                hasCustomAvatar = conversation.hasCustomAvatar,
                callStartTime = conversation.callStartTime,
                recordingConsentRequired = conversation.recordingConsentRequired,
                remoteServer = conversation.remoteServer,
                remoteToken = conversation.remoteToken
            )
        }
    }
}

// enum class ConversationType {
//     DUMMY,
//     ROOM_TYPE_ONE_TO_ONE_CALL,
//     ROOM_GROUP_CALL,
//     ROOM_PUBLIC_CALL,
//     ROOM_SYSTEM,
//     FORMER_ONE_TO_ONE,
//     NOTE_TO_SELF
// }
//
// enum class ParticipantType {
//     DUMMY,
//     OWNER,
//     MODERATOR,
//     USER,
//     GUEST,
//     USER_FOLLOWING_LINK,
//     GUEST_MODERATOR
// }
//
// enum class ObjectType {
//     DEFAULT,
//     SHARE_PASSWORD,
//     FILE,
//     ROOM
// }
//
// enum class NotificationLevel {
//     DEFAULT,
//     ALWAYS,
//     MENTION,
//     NEVER
// }
//
// enum class ConversationReadOnlyState {
//     CONVERSATION_READ_WRITE,
//     CONVERSATION_READ_ONLY
// }
//
// enum class LobbyState {
//     LOBBY_STATE_ALL_PARTICIPANTS,
//     LOBBY_STATE_MODERATORS_ONLY
// }
