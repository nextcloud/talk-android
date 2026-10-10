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
     * A stale push is not shown before the server is asked: its age may come from a device clock that runs ahead.
     * It rings only if the server confirms the call; otherwise, also without an answer, it is a missed call.
     */
    fun stateOfStalePush(hasCall: Boolean?): CallState = if (hasCall == true) CallState.RING else CallState.MISSED

    /** What the call screen knows about the kind of the call. */
    enum class CallType(val showsVoiceButton: Boolean, val showsVideoButton: Boolean) {
        /** Both answer buttons, neutral text: the kind of the call is not known (yet). */
        UNKNOWN(showsVoiceButton = true, showsVideoButton = true),
        VOICE(showsVoiceButton = true, showsVideoButton = false),
        VIDEO(showsVoiceButton = false, showsVideoButton = true)
    }

    /**
     * The kind of a call is known only when the call flag says someone is in the call. A flag without
     * [ParticipantDto.InCallFlags.IN_CALL] (0) was read before the call began or never read at all.
     */
    fun isCallTypeKnown(callFlag: Int): Boolean = (callFlag and ParticipantDto.InCallFlags.IN_CALL) != 0

    /** A server without the call-flags capability cannot tell the kind of the call either. */
    fun callTypeOf(callFlag: Int, hasCallFlagsCapability: Boolean): CallType =
        when {
            !hasCallFlagsCapability || !isCallTypeKnown(callFlag) -> CallType.UNKNOWN
            (callFlag and ParticipantDto.InCallFlags.WITH_VIDEO) > 0 -> CallType.VIDEO
            else -> CallType.VOICE
        }

    private val CALL_DATA_KEYS = listOf(
        BundleKeys.KEY_ROOM_ONE_TO_ONE,
        BundleKeys.KEY_CONVERSATION_NAME,
        BundleKeys.KEY_CONVERSATION_DISPLAY_NAME,
        BundleKeys.KEY_CALL_FLAG,
        BundleKeys.KEY_PARTICIPANT_PERMISSION_CAN_PUBLISH_AUDIO,
        BundleKeys.KEY_PARTICIPANT_PERMISSION_CAN_PUBLISH_VIDEO,
        BundleKeys.KEY_IS_MODERATOR
    )

    /** Only the call data of [extras] (notification extras hold more), as `CallActivity` reads it. */
    fun pickCallData(extras: Bundle): Bundle =
        Bundle().apply {
            CALL_DATA_KEYS.filter { extras.containsKey(it) }.forEach { key ->
                @Suppress("DEPRECATION")
                when (val value = extras.get(key)) {
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is String -> putString(key, value)
                }
            }
        }

    /** Whether [update] carries call data that differs from the call data [current] a call screen was opened with. */
    fun isCallDataChanged(current: Bundle?, update: Bundle): Boolean {
        val old = current?.let { pickCallData(it) } ?: Bundle()
        val new = pickCallData(update)
        @Suppress("DEPRECATION")
        return new.keySet().any { old.get(it) != new.get(it) }
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

        /** The call as shown before the server is asked: the kind of the call is not known. */
        fun withUnknownCallType(): IncomingCall = copy(callFlag = 0)

        /**
         * The call data the notification carries for an open `CallNotificationActivity`. The room token
         * is left out: with it `cancelExistingNotificationsForRoom` would take the call down on opening the chat.
         */
        fun toNotificationExtras(userId: Long, notificationTimestamp: Int): Bundle =
            toBundle(userId, notificationTimestamp).apply { remove(BundleKeys.KEY_ROOM_TOKEN) }

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
             * From the push alone, when the conversation is not cached. A call of unknown kind in a group
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
                    callFlag = 0,
                    canPublishAudio = true,
                    canPublishVideo = true,
                    isModerator = false
                )
        }
    }
}
