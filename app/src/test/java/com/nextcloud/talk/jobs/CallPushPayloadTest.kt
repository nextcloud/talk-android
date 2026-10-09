/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.jobs

import android.app.Application
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.capabilities.SpreedCapabilityDto
import com.nextcloud.talk.models.json.conversations.ConversationDto
import com.nextcloud.talk.models.json.conversations.ConversationEnums
import com.nextcloud.talk.models.json.participants.ParticipantDto
import com.nextcloud.talk.utils.ParticipantPermissions
import com.nextcloud.talk.utils.bundle.BundleKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class CallPushPayloadTest {

    @Test
    fun freshPushIsNotStale() {
        assertFalse(CallPushPayload.isStale(NOW - 1_000L, NOW))
        assertFalse(CallPushPayload.isStale(NOW - CallPushPayload.MAX_PUSH_AGE_MS, NOW))
    }

    @Test
    fun oldPushIsStale() {
        assertTrue(CallPushPayload.isStale(NOW - CallPushPayload.MAX_PUSH_AGE_MS - 1L, NOW))
        assertTrue(CallPushPayload.isStale(NOW - 10 * 60_000L, NOW))
    }

    @Test
    fun pushWithoutSentTimeIsFresh() {
        assertFalse(CallPushPayload.isStale(0L, NOW))
        assertFalse(CallPushPayload.isStale(-1L, NOW))
    }

    @Test
    fun serverAnswerDecidesWhatHappensToTheCall() {
        assertEquals(CallPushPayload.CallState.RING, CallPushPayload.stateOf(true))
        assertEquals(CallPushPayload.CallState.MISSED, CallPushPayload.stateOf(false))
        assertEquals(CallPushPayload.CallState.UNKNOWN, CallPushPayload.stateOf(null))
    }

    @Test
    fun callWithoutCacheIsAnAnswerableAudioCall() {
        val call = CallPushPayload.IncomingCall.fromPush(TOKEN, SUBJECT)

        assertEquals(TOKEN, call.roomToken)
        assertEquals(SUBJECT, call.title)
        assertEquals(SUBJECT, call.displayName)
        assertFalse(call.isVideoCall)
        assertFalse(call.isOneToOne)
        assertFalse(call.isModerator)
        assertTrue(call.canPublishAudio)
        assertTrue(call.canPublishVideo)
        assertTrue((call.callFlag and ParticipantDto.InCallFlags.WITH_AUDIO) > 0)
    }

    @Test
    fun callWithoutCacheCarriesWhatTheCallScreensRead() {
        val bundle = CallPushPayload.IncomingCall.fromPush(TOKEN, SUBJECT).toBundle(USER_ID, NOTIFICATION_ID)

        assertEquals(TOKEN, bundle.getString(BundleKeys.KEY_ROOM_TOKEN))
        assertEquals(NOTIFICATION_ID, bundle.getInt(BundleKeys.KEY_NOTIFICATION_TIMESTAMP))
        assertEquals(USER_ID, bundle.getLong(BundleKeys.KEY_INTERNAL_USER_ID))
        assertTrue(bundle.getBoolean(BundleKeys.KEY_FROM_NOTIFICATION_START_CALL))
        assertEquals(SUBJECT, bundle.getString(BundleKeys.KEY_CONVERSATION_DISPLAY_NAME))
        assertEquals(SUBJECT, bundle.getString(BundleKeys.KEY_CONVERSATION_NAME))
        assertTrue(bundle.getBoolean(BundleKeys.KEY_PARTICIPANT_PERMISSION_CAN_PUBLISH_AUDIO))
        assertTrue(bundle.getBoolean(BundleKeys.KEY_PARTICIPANT_PERMISSION_CAN_PUBLISH_VIDEO))
        assertFalse(bundle.getBoolean(BundleKeys.KEY_IS_MODERATOR, true))
        assertFalse(bundle.getBoolean(BundleKeys.KEY_ROOM_ONE_TO_ONE, true))
    }

    @Test
    fun cachedConversationFillsTheCall() {
        val conversation = conversation(
            type = ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL,
            participantType = ParticipantDto.ParticipantType.MODERATOR,
            callFlag = ParticipantDto.InCallFlags.IN_CALL or ParticipantDto.InCallFlags.WITH_VIDEO
        )

        val call = CallPushPayload.IncomingCall.fromConversation(conversation, SUBJECT, SpreedCapabilityDto())

        assertEquals(TOKEN, call.roomToken)
        assertEquals(SUBJECT, call.title)
        assertEquals("Alice", call.displayName)
        assertEquals("alice", call.name)
        assertTrue(call.isOneToOne)
        assertTrue(call.isModerator)
        assertTrue(call.isVideoCall)
        val bundle = call.toBundle(USER_ID, NOTIFICATION_ID)
        assertEquals("alice", bundle.getString(BundleKeys.KEY_CONVERSATION_NAME))
        assertEquals("Alice", bundle.getString(BundleKeys.KEY_CONVERSATION_DISPLAY_NAME))
        assertEquals(call.callFlag, bundle.getInt(BundleKeys.KEY_CALL_FLAG))
    }

    @Test
    fun cachedConversationPermissionsAreKept() {
        val capabilities = SpreedCapabilityDto().apply { features = listOf("conversation-permissions") }
        val conversation = conversation(
            permissions = ParticipantPermissions.JOIN_CALL or ParticipantPermissions.PUBLISH_AUDIO
        )

        val call = CallPushPayload.IncomingCall.fromConversation(conversation, SUBJECT, capabilities)

        assertTrue(call.canPublishAudio)
        assertFalse(call.canPublishVideo)
    }

    private fun conversation(
        type: ConversationEnums.ConversationType = ConversationEnums.ConversationType.ROOM_GROUP_CALL,
        participantType: ParticipantDto.ParticipantType = ParticipantDto.ParticipantType.USER,
        callFlag: Int = 0,
        permissions: Int = 0
    ): ConversationModel =
        ConversationModel.mapToConversationModel(
            ConversationDto(
                token = TOKEN,
                name = "alice",
                displayName = "Alice",
                description = "",
                type = type,
                lastPing = 0,
                participantType = participantType,
                hasPassword = false,
                sessionId = "",
                actorId = "",
                actorType = "",
                password = "",
                favorite = false,
                lastActivity = 0,
                unreadMessages = 0,
                unreadMention = false,
                lastMessage = null,
                objectType = ConversationEnums.ObjectType.DEFAULT,
                notificationLevel = ConversationEnums.NotificationLevel.ALWAYS,
                conversationReadOnlyState = ConversationEnums.ConversationReadOnlyState.CONVERSATION_READ_WRITE,
                lobbyState = ConversationEnums.LobbyState.LOBBY_STATE_ALL_PARTICIPANTS,
                lobbyTimer = 0,
                lastReadMessage = 0,
                lastCommonReadMessage = 0,
                hasCall = false,
                callFlag = callFlag,
                canStartCall = false,
                canLeaveConversation = true,
                canDeleteConversation = true,
                unreadMentionDirect = false,
                notificationCalls = 0,
                permissions = permissions,
                messageExpiration = 0,
                status = "",
                statusIcon = "",
                statusMessage = "",
                statusClearAt = 0,
                callRecording = 0,
                avatarVersion = "",
                hasCustomAvatar = false,
                callStartTime = 0,
                recordingConsentRequired = 0,
                remoteServer = "",
                remoteToken = ""
            ),
            User().apply { id = USER_ID }
        )

    private companion object {
        const val NOW = 1_700_000_100_000L
        const val TOKEN = "abc123"
        const val SUBJECT = "Alice is calling you"
        const val USER_ID = 7L
        const val NOTIFICATION_ID = 123456
    }
}
