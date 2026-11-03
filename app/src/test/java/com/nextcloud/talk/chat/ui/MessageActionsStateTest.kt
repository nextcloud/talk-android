/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.chat.ui

import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.capabilities.SpreedCapabilityDto
import com.nextcloud.talk.models.json.conversations.ConversationEnums
import com.nextcloud.talk.models.json.participants.ParticipantDto
import com.nextcloud.talk.utils.DateUtils
import org.junit.Assert
import org.junit.Test
import org.mockito.Mockito

class MessageActionsStateTest {

    private val dateUtils = Mockito.mock(DateUtils::class.java)

    private fun conversation(remoteServer: String? = null) =
        ConversationModel(
            internalId = "1@abc",
            accountId = 1L,
            token = "abc",
            name = "abc",
            displayName = "abc",
            description = "",
            type = ConversationEnums.ConversationType.ROOM_GROUP_CALL,
            participantType = ParticipantDto.ParticipantType.USER,
            sessionId = "0",
            actorId = "alice",
            actorType = "users",
            objectType = ConversationEnums.ObjectType.DEFAULT,
            notificationLevel = ConversationEnums.NotificationLevel.DEFAULT,
            conversationReadOnlyState = ConversationEnums.ConversationReadOnlyState.CONVERSATION_READ_WRITE,
            lobbyState = ConversationEnums.LobbyState.LOBBY_STATE_ALL_PARTICIPANTS,
            lobbyTimer = 0L,
            canLeaveConversation = true,
            canDeleteConversation = false,
            unreadMentionDirect = false,
            notificationCalls = 0,
            avatarVersion = "",
            hasCustomAvatar = false,
            callStartTime = 0L,
            remoteServer = remoteServer
        )

    private fun buildStateFor(
        message: ChatMessage,
        isOnline: Boolean = true,
        hasChatPermission: Boolean = true,
        conversation: ConversationModel? = null
    ) = buildMessageActionsState(
        message = message,
        user = User().apply { userId = "alice" },
        conversation = conversation,
        hasChatPermission = hasChatPermission,
        hasReactPermission = true,
        spreedCapabilities = SpreedCapabilityDto(),
        isOnline = isOnline,
        dateUtils = dateUtils,
        conversationThreadId = null
    )

    @Test
    fun showCopyMessageLink_trueForRegularMessage() {
        val msg = ChatMessage().apply {
            jsonMessageId = 4
            message = "Hello world"
        }
        Assert.assertTrue(buildStateFor(msg).showCopyMessageLink)
    }

    @Test
    fun showCopyMessageLink_falseForDeletedMessage() {
        val msg = ChatMessage().apply {
            jsonMessageId = 4
            message = "Hello world"
            isDeleted = true
        }
        Assert.assertFalse(buildStateFor(msg).showCopyMessageLink)
    }

    @Test
    fun showCopyMessageLink_falseForSystemMessage() {
        // isSystemMessage is computed at construction time, so the type must be set via the constructor
        val msg = ChatMessage(
            jsonMessageId = 4,
            systemMessageType = ChatMessage.SystemMessageType.CONVERSATION_CREATED
        )
        Assert.assertFalse(buildStateFor(msg).showCopyMessageLink)
    }

    private fun fileMessage() =
        ChatMessage(
            jsonMessageId = 7,
            message = "{file}",
            messageParameters = hashMapOf("file" to hashMapOf("id" to "1", "name" to "a.jpg", "path" to "Talk/a.jpg"))
        )

    @Test
    fun showForwardFile_trueForOnlineFileMessage_butTheTextForwardStaysOff() {
        val state = buildStateFor(fileMessage())
        Assert.assertTrue(state.showForwardFile)
        Assert.assertFalse(state.showForward)
    }

    @Test
    fun showForwardFile_falseOfflineDeletedOrForText() {
        Assert.assertFalse(buildStateFor(fileMessage(), isOnline = false).showForwardFile)
        Assert.assertFalse(buildStateFor(fileMessage().apply { isDeleted = true }).showForwardFile)
        Assert.assertFalse(buildStateFor(ChatMessage(jsonMessageId = 8, message = "hi")).showForwardFile)
    }

    @Test
    fun showForwardFile_falseWithoutThePathOfTheFile() {
        val noPath = ChatMessage(
            jsonMessageId = 9,
            message = "{file}",
            messageParameters = hashMapOf("file" to hashMapOf("id" to "1", "name" to "a.jpg"))
        )
        val withPath = ChatMessage(
            jsonMessageId = 9,
            message = "{file}",
            messageParameters = hashMapOf("file" to hashMapOf("id" to "1", "name" to "a.jpg", "path" to "Talk/a.jpg"))
        )
        Assert.assertFalse(buildStateFor(noPath).showForwardFile)
        Assert.assertTrue(buildStateFor(withPath).showForwardFile)
    }

    @Test
    fun showForwardFile_falseInFederatedConversation() {
        val federated = conversation(remoteServer = "https://other.example")
        Assert.assertFalse(buildStateFor(fileMessage(), conversation = federated).showForwardFile)
        Assert.assertTrue(buildStateFor(fileMessage(), conversation = conversation()).showForwardFile)
    }

    @Test
    fun canSendToConversation_followsTheChatPermission() {
        Assert.assertTrue(buildStateFor(fileMessage()).canSendToConversation)
        Assert.assertFalse(buildStateFor(fileMessage(), hasChatPermission = false).canSendToConversation)
    }
}
