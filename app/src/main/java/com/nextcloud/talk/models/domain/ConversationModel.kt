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

class ConversationModel(
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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ConversationModel

        if (hasPassword != other.hasPassword) return false
        if (favorite != other.favorite) return false
        if (lastActivity != other.lastActivity) return false
        if (unreadMessages != other.unreadMessages) return false
        if (unreadMention != other.unreadMention) return false
        if (lobbyTimer != other.lobbyTimer) return false
        if (lastReadMessage != other.lastReadMessage) return false
        if (lastCommonReadMessage != other.lastCommonReadMessage) return false
        if (hasCall != other.hasCall) return false
        if (callFlag != other.callFlag) return false
        if (canStartCall != other.canStartCall) return false
        if (canLeaveConversation != other.canLeaveConversation) return false
        if (canDeleteConversation != other.canDeleteConversation) return false
        if (unreadMentionDirect != other.unreadMentionDirect) return false
        if (notificationCalls != other.notificationCalls) return false
        if (permissions != other.permissions) return false
        if (messageExpiration != other.messageExpiration) return false
        if (statusClearAt != other.statusClearAt) return false
        if (callRecording != other.callRecording) return false
        if (hasCustomAvatar != other.hasCustomAvatar) return false
        if (callStartTime != other.callStartTime) return false
        if (recordingConsentRequired != other.recordingConsentRequired) return false
        if (hasArchived != other.hasArchived) return false
        if (hasSensitive != other.hasSensitive) return false
        if (hasImportant != other.hasImportant) return false
        if (internalId != other.internalId) return false
        if (name != other.name) return false
        if (displayName != other.displayName) return false
        if (description != other.description) return false
        if (type != other.type) return false
        if (participantType != other.participantType) return false
        if (lastMessage != other.lastMessage) return false
        if (objectType != other.objectType) return false
        if (objectId != other.objectId) return false
        if (notificationLevel != other.notificationLevel) return false
        if (conversationReadOnlyState != other.conversationReadOnlyState) return false
        if (lobbyState != other.lobbyState) return false
        if (status != other.status) return false
        if (statusIcon != other.statusIcon) return false
        if (statusMessage != other.statusMessage) return false
        if (avatarVersion != other.avatarVersion) return false
        if (remoteServer != other.remoteServer) return false
        if (remoteToken != other.remoteToken) return false
        if (password != other.password) return false
        if (messageDraft != other.messageDraft) return false

        return true
    }

    override fun hashCode(): Int {
        var result = hasPassword.hashCode()
        result = 31 * result + favorite.hashCode()
        result = 31 * result + lastActivity.hashCode()
        result = 31 * result + unreadMessages
        result = 31 * result + unreadMention.hashCode()
        result = 31 * result + lobbyTimer.hashCode()
        result = 31 * result + lastReadMessage
        result = 31 * result + lastCommonReadMessage
        result = 31 * result + hasCall.hashCode()
        result = 31 * result + callFlag
        result = 31 * result + canStartCall.hashCode()
        result = 31 * result + canLeaveConversation.hashCode()
        result = 31 * result + canDeleteConversation.hashCode()
        result = 31 * result + unreadMentionDirect.hashCode()
        result = 31 * result + notificationCalls
        result = 31 * result + permissions
        result = 31 * result + messageExpiration
        result = 31 * result + (statusClearAt?.hashCode() ?: 0)
        result = 31 * result + callRecording
        result = 31 * result + hasCustomAvatar.hashCode()
        result = 31 * result + callStartTime.hashCode()
        result = 31 * result + recordingConsentRequired
        result = 31 * result + hasArchived.hashCode()
        result = 31 * result + hasSensitive.hashCode()
        result = 31 * result + hasImportant.hashCode()
        result = 31 * result + internalId.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + displayName.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + participantType.hashCode()
        result = 31 * result + (lastMessage?.hashCode() ?: 0)
        result = 31 * result + objectType.hashCode()
        result = 31 * result + objectId.hashCode()
        result = 31 * result + notificationLevel.hashCode()
        result = 31 * result + conversationReadOnlyState.hashCode()
        result = 31 * result + lobbyState.hashCode()
        result = 31 * result + (status?.hashCode() ?: 0)
        result = 31 * result + (statusIcon?.hashCode() ?: 0)
        result = 31 * result + (statusMessage?.hashCode() ?: 0)
        result = 31 * result + avatarVersion.hashCode()
        result = 31 * result + (remoteServer?.hashCode() ?: 0)
        result = 31 * result + (remoteToken?.hashCode() ?: 0)
        result = 31 * result + (password?.hashCode() ?: 0)
        result = 31 * result + (messageDraft?.hashCode() ?: 0)
        return result
    }
}
