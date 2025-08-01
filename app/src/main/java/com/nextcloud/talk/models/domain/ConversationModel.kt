/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023-2024 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2023 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models.domain

import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.MessageDraft
import com.nextcloud.talk.models.json.chat.ChatMessageJson
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.conversations.ConversationEnums
import com.nextcloud.talk.models.json.participants.Participant

data class ConversationModel(
    var internalId: String,
    var accountId: Long,
    var token: String,
    var name: String,
    var displayName: String,
    var description: String,
    var type: ConversationEnums.ConversationType,
    var lastPing: Long = 0,
    var participantType: Participant.ParticipantType,
    var hasPassword: Boolean = false,
    var sessionId: String,
    var actorId: String,
    var actorType: String,
    var favorite: Boolean = false,
    var lastActivity: Long = 0,
    var unreadMessages: Int = 0,
    var unreadMention: Boolean = false,
    var lastMessage: ChatMessageJson? = null,
    var objectType: ConversationEnums.ObjectType,
    var objectId: String = "",
    var notificationLevel: ConversationEnums.NotificationLevel,
    var conversationReadOnlyState: ConversationEnums.ConversationReadOnlyState,
    var lobbyState: ConversationEnums.LobbyState,
    var lobbyTimer: Long,
    var lastReadMessage: Int = 0,
    var lastCommonReadMessage: Int = 0,
    var hasCall: Boolean = false,
    var callFlag: Int = 0,
    var canStartCall: Boolean = false,
    var canLeaveConversation: Boolean,
    var canDeleteConversation: Boolean,
    var unreadMentionDirect: Boolean,
    var notificationCalls: Int,
    var permissions: Int = 0,
    var messageExpiration: Int = 0,
    var status: String? = null,
    var statusIcon: String? = null,
    var statusMessage: String? = null,
    var statusClearAt: Long? = 0,
    var callRecording: Int = 0,
    var avatarVersion: String,
    var hasCustomAvatar: Boolean,
    var callStartTime: Long,
    var recordingConsentRequired: Int = 0,
    var remoteServer: String? = null,
    var remoteToken: String? = null,
    var hasArchived: Boolean = false,
    var hasSensitive: Boolean = false,
    var hasImportant: Boolean = false,

    // attributes that don't come from API. This should be changed?!
    var password: String? = null,
    var messageDraft: MessageDraft? = MessageDraft()
) {

    companion object {
        @Suppress("LongMethod")
        fun mapToConversationModel(conversation: Conversation, user: User): ConversationModel =
            ConversationModel(
                internalId = user.id!!.toString() + "@" + conversation.token,
                accountId = user.id!!,
                token = conversation.token,
                name = conversation.name,
                displayName = conversation.displayName,
                description = conversation.description,
                type = conversation.type.let { ConversationEnums.ConversationType.valueOf(it.name) },
                lastPing = conversation.lastPing,
                participantType = conversation.participantType.let { Participant.ParticipantType.valueOf(it.name) },
                hasPassword = conversation.hasPassword,
                sessionId = conversation.sessionId,
                actorId = conversation.actorId,
                actorType = conversation.actorType,
                password = conversation.password,
                favorite = conversation.favorite,
                lastActivity = conversation.lastActivity,
                unreadMessages = conversation.unreadMessages,
                unreadMention = conversation.unreadMention,
                lastMessage = conversation.lastMessage,
                objectType = conversation.objectType.let { ConversationEnums.ObjectType.valueOf(it.name) },
                objectId = conversation.objectId,
                notificationLevel = conversation.notificationLevel.let {
                    ConversationEnums.NotificationLevel.valueOf(
                        it.name
                    )
                },
                conversationReadOnlyState = conversation.conversationReadOnlyState.let {
                    ConversationEnums.ConversationReadOnlyState.valueOf(
                        it.name
                    )
                },
                lobbyState = conversation.lobbyState.let { ConversationEnums.LobbyState.valueOf(it.name) },
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
                remoteToken = conversation.remoteToken,
                hasArchived = conversation.hasArchived,
                hasSensitive = conversation.hasSensitive,
                hasImportant = conversation.hasImportant
            )
    }
}
