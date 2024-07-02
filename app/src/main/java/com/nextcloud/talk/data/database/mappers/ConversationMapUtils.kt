/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.data.database.mappers

import com.nextcloud.talk.data.database.model.ConversationEntity
import com.nextcloud.talk.models.domain.ConversationModel

fun ConversationModel.asEntity() =
    ConversationEntity(
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
        // lastMessageId = lastMessage?.id?.toLong(),
        objectType = objectType,
        notificationLevel = notificationLevel,
        conversationReadOnlyState = conversationReadOnlyState,
        lobbyState = lobbyState,
        lobbyTimer = lobbyTimer,
        lastReadMessage = lastReadMessage,
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
        remoteToken = remoteToken
    )

fun ConversationEntity.asModel() =
    ConversationModel(
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
        // lastMessageId = lastMessage?.id?.toLong(),
        objectType = objectType,
        notificationLevel = notificationLevel,
        conversationReadOnlyState = conversationReadOnlyState,
        lobbyState = lobbyState,
        lobbyTimer = lobbyTimer,
        lastReadMessage = lastReadMessage,
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
        remoteToken = remoteToken
    )
