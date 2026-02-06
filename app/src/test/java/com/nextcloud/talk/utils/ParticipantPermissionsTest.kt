/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Tim Krüger <t@timkrueger.me>
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.capabilities.SpreedCapability
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.conversations.ConversationEnums
import com.nextcloud.talk.models.json.participants.Participant
import junit.framework.TestCase
import org.junit.Test

class ParticipantPermissionsTest : TestCase() {

    @Test
    fun test_areFlagsSet() {
        val spreedCapability = SpreedCapability()
        val conversation = createConversation()

        conversation.permissions = ParticipantPermissions.PUBLISH_SCREEN or
            ParticipantPermissions.JOIN_CALL or
            ParticipantPermissions.DEFAULT

        val user = User()
        user.id = 1

        val attendeePermissions =
            ParticipantPermissions(
                spreedCapability,
                ConversationModel.mapToConversationModel(conversation, user)
            )

        assert(attendeePermissions.canPublishScreen)
        assert(attendeePermissions.canJoinCall)
        assert(attendeePermissions.isDefault)

        assertFalse(attendeePermissions.isCustom)
        assertFalse(attendeePermissions.canStartCall())
        assertFalse(attendeePermissions.canIgnoreLobby())
        assertTrue(attendeePermissions.canPublishAudio())
        assertTrue(attendeePermissions.canPublishVideo())
    }

    @Test
    fun test_reactPermissionWithReactCapability() {
        val spreedCapability = SpreedCapability()
        // Server with react-permission also supports chat-permission
        spreedCapability.features = listOf("chat-permission", "react-permission")
        val conversation = createConversation()

        // With react-permission capability, only REACT bit matters
        conversation.permissions = ParticipantPermissions.REACT or
            ParticipantPermissions.CUSTOM

        val user = User()
        user.id = 1

        val attendeePermissions =
            ParticipantPermissions(
                spreedCapability,
                ConversationModel.mapToConversationModel(conversation, user)
            )

        assertTrue(attendeePermissions.hasReactPermission())
        assertFalse(attendeePermissions.hasChatPermission())
    }

    @Test
    fun test_reactPermissionDeniedWithReactCapability() {
        val spreedCapability = SpreedCapability()
        // Server with react-permission also supports chat-permission
        spreedCapability.features = listOf("chat-permission", "react-permission")
        val conversation = createConversation()

        // With react-permission capability, only CHAT but no REACT - should NOT allow reactions
        conversation.permissions = ParticipantPermissions.CHAT or
            ParticipantPermissions.CUSTOM

        val user = User()
        user.id = 1

        val attendeePermissions =
            ParticipantPermissions(
                spreedCapability,
                ConversationModel.mapToConversationModel(conversation, user)
            )

        assertFalse(attendeePermissions.hasReactPermission())
        assertTrue(attendeePermissions.hasChatPermission())
    }

    @Test
    fun test_reactPermissionFallbackToChatOnOlderServer() {
        val spreedCapability = SpreedCapability()
        // Older server without react-permission capability but with chat-permission
        spreedCapability.features = listOf("chat-permission")
        val conversation = createConversation()

        // Only CHAT permission set - should allow reactions as fallback for older servers
        conversation.permissions = ParticipantPermissions.CHAT or
            ParticipantPermissions.CUSTOM

        val user = User()
        user.id = 1

        val attendeePermissions =
            ParticipantPermissions(
                spreedCapability,
                ConversationModel.mapToConversationModel(conversation, user)
            )

        assertTrue(attendeePermissions.hasReactPermission())
        assertTrue(attendeePermissions.hasChatPermission())
    }

    @Test
    fun test_reactPermissionDeniedOnOlderServerWithoutChatPermission() {
        val spreedCapability = SpreedCapability()
        // Older server without react-permission capability but with chat-permission
        spreedCapability.features = listOf("chat-permission")
        val conversation = createConversation()

        // No CHAT permission - should deny reactions on older servers
        conversation.permissions = ParticipantPermissions.CUSTOM

        val user = User()
        user.id = 1

        val attendeePermissions =
            ParticipantPermissions(
                spreedCapability,
                ConversationModel.mapToConversationModel(conversation, user)
            )

        assertFalse(attendeePermissions.hasReactPermission())
        assertFalse(attendeePermissions.hasChatPermission())
    }

    private fun createConversation() =
        Conversation(
            token = "test",
            name = "test",
            displayName = "test",
            description = "test",
            type = ConversationEnums.ConversationType.DUMMY,
            lastPing = 1,
            participantType = Participant.ParticipantType.DUMMY,
            hasPassword = true,
            sessionId = "test",
            actorId = "test",
            actorType = "test",
            password = "test",
            favorite = false,
            lastActivity = 1,
            unreadMessages = 1,
            unreadMention = false,
            lastMessage = null,
            objectType = ConversationEnums.ObjectType.DEFAULT,
            notificationLevel = ConversationEnums.NotificationLevel.ALWAYS,
            conversationReadOnlyState = ConversationEnums.ConversationReadOnlyState.CONVERSATION_READ_WRITE,
            lobbyState = ConversationEnums.LobbyState.LOBBY_STATE_ALL_PARTICIPANTS,
            lobbyTimer = 1,
            lastReadMessage = 1,
            lastCommonReadMessage = 1,
            hasCall = true,
            callFlag = 1,
            canStartCall = false,
            canLeaveConversation = true,
            canDeleteConversation = true,
            unreadMentionDirect = true,
            notificationCalls = 1,
            permissions = 1,
            messageExpiration = 1,
            status = "test",
            statusIcon = "test",
            statusMessage = "test",
            statusClearAt = 1,
            callRecording = 1,
            avatarVersion = "test",
            hasCustomAvatar = true,
            callStartTime = 1,
            recordingConsentRequired = 1,
            remoteServer = "",
            remoteToken = ""
        )
}
