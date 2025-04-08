/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.data.database.mappers

import com.bluelinelabs.logansquare.LoganSquare
import com.nextcloud.talk.data.database.model.ConversationEntity
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.chat.ChatMessageJson
import com.nextcloud.talk.models.json.conversations.Conversation

fun ConversationModel.asEntity() =
    ConversationEntity(
        internalId = internalId,
        accountId = accountId,
        token = token,
        name = name,
        displayName = displayName,
        description = description,
        type = type,
        lastPing = lastPing,
        participantType = participantType,
        hasPassword = hasPassword,
        sessionId = sessionId,
        actorId = actorId,
        actorType = actorType,
        favorite = favorite,
        lastActivity = lastActivity,
        unreadMessages = unreadMessages,
        unreadMention = unreadMention,
        lastMessage = lastMessage?.let { LoganSquare.serialize(lastMessage) },
        objectType = objectType,
        objectId = objectId,
        notificationLevel = notificationLevel,
        conversationReadOnlyState = conversationReadOnlyState,
        lobbyState = lobbyState,
        lobbyTimer = lobbyTimer,
        lastReadMessage = lastReadMessage,
        lastCommonReadMessage = lastCommonReadMessage,
        hasCall = hasCall,
        callFlag = callFlag,
        canStartCall = canStartCall,
        canLeaveConversation = canLeaveConversation,
        canDeleteConversation = canDeleteConversation,
        unreadMentionDirect = unreadMentionDirect,
        notificationCalls = notificationCalls,
        permissions = permissions,
        messageExpiration = messageExpiration,
        status = status,
        statusIcon = statusIcon,
        statusMessage = statusMessage,
        statusClearAt = statusClearAt,
        callRecording = callRecording,
        avatarVersion = avatarVersion,
        hasCustomAvatar = hasCustomAvatar,
        callStartTime = callStartTime,
        recordingConsentRequired = recordingConsentRequired,
        remoteServer = remoteServer,
        remoteToken = remoteToken,
        hasArchived = hasArchived
    )

fun ConversationEntity.asModel() =
    ConversationModel(
        internalId = internalId,
        accountId = accountId,
        token = token,
        name = name,
        displayName = displayName,
        description = description,
        type = type,
        lastPing = lastPing,
        participantType = participantType,
        hasPassword = hasPassword,
        sessionId = sessionId,
        actorId = actorId,
        actorType = actorType,
        favorite = favorite,
        lastActivity = lastActivity,
        unreadMessages = unreadMessages,
        unreadMention = unreadMention,
        lastMessage = lastMessage?.let
            { LoganSquare.parse(lastMessage, ChatMessageJson::class.java) },
        objectType = objectType,
        objectId = objectId,
        notificationLevel = notificationLevel,
        conversationReadOnlyState = conversationReadOnlyState,
        lobbyState = lobbyState,
        lobbyTimer = lobbyTimer,
        lastReadMessage = lastReadMessage,
        lastCommonReadMessage = lastCommonReadMessage,
        hasCall = hasCall,
        callFlag = callFlag,
        canStartCall = canStartCall,
        canLeaveConversation = canLeaveConversation,
        canDeleteConversation = canDeleteConversation,
        unreadMentionDirect = unreadMentionDirect,
        notificationCalls = notificationCalls,
        permissions = permissions,
        messageExpiration = messageExpiration,
        status = status,
        statusIcon = statusIcon,
        statusMessage = statusMessage,
        statusClearAt = statusClearAt,
        callRecording = callRecording,
        avatarVersion = avatarVersion,
        hasCustomAvatar = hasCustomAvatar,
        callStartTime = callStartTime,
        recordingConsentRequired = recordingConsentRequired,
        remoteServer = remoteServer,
        remoteToken = remoteToken,
        hasArchived = hasArchived
    )

fun Conversation.asEntity(accountId: Long) =
    ConversationEntity(
        internalId = "$accountId@$token",
        accountId = accountId,
        token = token,
        name = name,
        displayName = displayName,
        description = description,
        type = type,
        lastPing = lastPing,
        participantType = participantType,
        hasPassword = hasPassword,
        sessionId = sessionId,
        actorId = actorId,
        actorType = actorType,
        favorite = favorite,
        lastActivity = lastActivity,
        unreadMessages = unreadMessages,
        unreadMention = unreadMention,
        lastMessage = lastMessage?.let { LoganSquare.serialize(lastMessage) },
        objectType = objectType,
        objectId = objectId,
        notificationLevel = notificationLevel,
        conversationReadOnlyState = conversationReadOnlyState,
        lobbyState = lobbyState,
        lobbyTimer = lobbyTimer,
        lastReadMessage = lastReadMessage,
        lastCommonReadMessage = lastCommonReadMessage,
        hasCall = hasCall,
        callFlag = callFlag,
        canStartCall = canStartCall,
        canLeaveConversation = canLeaveConversation,
        canDeleteConversation = canDeleteConversation,
        unreadMentionDirect = unreadMentionDirect,
        notificationCalls = notificationCalls,
        permissions = permissions,
        messageExpiration = messageExpiration,
        status = status,
        statusIcon = statusIcon,
        statusMessage = statusMessage,
        statusClearAt = statusClearAt,
        callRecording = callRecording,
        avatarVersion = avatarVersion,
        hasCustomAvatar = hasCustomAvatar,
        callStartTime = callStartTime,
        recordingConsentRequired = recordingConsentRequired,
        remoteServer = remoteServer,
        remoteToken = remoteToken,
        hasArchived = hasArchived
    )
