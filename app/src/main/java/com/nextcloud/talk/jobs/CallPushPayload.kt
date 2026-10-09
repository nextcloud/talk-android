/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.jobs

import android.os.Bundle
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.capabilities.SpreedCapabilityDto
import com.nextcloud.talk.models.json.conversations.ConversationEnums
import com.nextcloud.talk.models.json.participants.ParticipantDto
import com.nextcloud.talk.utils.ConversationUtils
import com.nextcloud.talk.utils.ParticipantPermissions
import com.nextcloud.talk.utils.bundle.BundleKeys

/**
 * Decisions and data of an incoming-call push that need no network.
 *
 * A call push is shown from its own payload (subject, room token) plus the cached conversation,
 * because the process of a backgrounded app may be frozen and its network blocked right after the
 * push arrives. The server is asked afterwards and only confirms that the call is still going on.
 */
object CallPushPayload {

    /** A push that took longer than this to reach the device is a call nobody waits for any more. */
    const val MAX_PUSH_AGE_MS = 45_000L

    /** What the server answer [ConversationModel.hasCall] means for the call that is shown. */
    enum class CallState {
        /** The call is still going on: keep ringing and refresh the data. */
        RING,

        /** The call is over: replace the ringing by "missed call". */
        MISSED,

        /** The server could not be asked (no network, error, timeout): leave the notification alone. */
        UNKNOWN
    }

    /**
     * Whether the push is too old to ring. [sentTime] is `RemoteMessage.sentTime`; a value `<= 0`
     * (UnifiedPush, generic flavour, no such key) means the age is unknown and the push counts as fresh.
     */
    fun isStale(sentTime: Long, now: Long): Boolean = sentTime > 0L && now - sentTime > MAX_PUSH_AGE_MS

    fun stateOf(hasCall: Boolean?): CallState =
        when (hasCall) {
            true -> CallState.RING
            false -> CallState.MISSED
            null -> CallState.UNKNOWN
        }

    /**
     * The incoming call as the notification and the call screens need it.
     *
     * [title] is the text of the push; [displayName] and [name] come from the conversation.
     */
    data class IncomingCall(
        val roomToken: String,
        val title: String,
        val displayName: String,
        val name: String,
        val isOneToOne: Boolean,
        val callFlag: Int,
        val canPublishAudio: Boolean,
        val canPublishVideo: Boolean,
        val isModerator: Boolean
    ) {
        val isVideoCall: Boolean
            get() = (callFlag and ParticipantDto.InCallFlags.WITH_VIDEO) > 0

        /** The extras of `CallNotificationActivity` and `CallActivity` (answer button). */
        fun toBundle(userId: Long, notificationTimestamp: Int): Bundle =
            Bundle().apply {
                putString(BundleKeys.KEY_ROOM_TOKEN, roomToken)
                putInt(BundleKeys.KEY_NOTIFICATION_TIMESTAMP, notificationTimestamp)
                putLong(BundleKeys.KEY_INTERNAL_USER_ID, userId)
                putBoolean(BundleKeys.KEY_FROM_NOTIFICATION_START_CALL, true)
                putBoolean(BundleKeys.KEY_ROOM_ONE_TO_ONE, isOneToOne)
                putString(BundleKeys.KEY_CONVERSATION_NAME, name)
                putString(BundleKeys.KEY_CONVERSATION_DISPLAY_NAME, displayName)
                putInt(BundleKeys.KEY_CALL_FLAG, callFlag)
                putBoolean(BundleKeys.KEY_PARTICIPANT_PERMISSION_CAN_PUBLISH_AUDIO, canPublishAudio)
                putBoolean(BundleKeys.KEY_PARTICIPANT_PERMISSION_CAN_PUBLISH_VIDEO, canPublishVideo)
                putBoolean(BundleKeys.KEY_IS_MODERATOR, isModerator)
            }

        companion object {
            /** From a conversation of the server or of the local cache. */
            fun fromConversation(
                conversation: ConversationModel,
                title: String,
                capabilities: SpreedCapabilityDto?
            ): IncomingCall {
                val permissions = ParticipantPermissions(capabilities, conversation)
                return IncomingCall(
                    roomToken = conversation.token,
                    title = title,
                    displayName = conversation.displayName,
                    name = conversation.name,
                    isOneToOne = conversation.type == ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL,
                    callFlag = conversation.callFlag,
                    canPublishAudio = permissions.canPublishAudio(),
                    canPublishVideo = permissions.canPublishVideo(),
                    isModerator = ConversationUtils.isParticipantOwnerOrModerator(conversation)
                )
            }

            /**
             * From the push alone, when the conversation is not cached. An audio call of a group
             * that the user may answer: `CallActivity` reads missing publish permissions as "not
             * allowed" and would start without a microphone. The server enforces the real permissions.
             */
            fun fromPush(roomToken: String, subject: String): IncomingCall =
                IncomingCall(
                    roomToken = roomToken,
                    title = subject,
                    displayName = subject,
                    name = subject,
                    isOneToOne = false,
                    callFlag = ParticipantDto.InCallFlags.IN_CALL or ParticipantDto.InCallFlags.WITH_AUDIO,
                    canPublishAudio = true,
                    canPublishVideo = true,
                    isModerator = false
                )
        }
    }
}
