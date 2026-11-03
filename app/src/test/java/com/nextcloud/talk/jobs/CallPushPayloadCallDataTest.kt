/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.jobs

import android.app.Application
import android.os.Bundle
import com.nextcloud.talk.models.json.participants.ParticipantDto
import com.nextcloud.talk.utils.bundle.BundleKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The kind of the call and the call data that an open call screen follows. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class CallPushPayloadCallDataTest {

    @Test
    fun callTypeIsKnownOnlyWithTheInCallBit() {
        assertFalse(CallPushPayload.isCallTypeKnown(0))
        assertFalse(CallPushPayload.isCallTypeKnown(ParticipantDto.InCallFlags.WITH_VIDEO))
        assertTrue(CallPushPayload.isCallTypeKnown(ParticipantDto.InCallFlags.IN_CALL))
        assertTrue(CallPushPayload.isCallTypeKnown(VIDEO_CALL_FLAG))
    }

    @Test
    fun callTypeComesFromTheCallFlagWhenKnown() {
        assertEquals(CallPushPayload.CallType.VIDEO, CallPushPayload.callTypeOf(VIDEO_CALL_FLAG, true))
        assertEquals(CallPushPayload.CallType.VOICE, CallPushPayload.callTypeOf(VOICE_CALL_FLAG, true))
    }

    @Test
    fun callTypeIsUnknownWithoutInCallBitOrCapability() {
        assertEquals(CallPushPayload.CallType.UNKNOWN, CallPushPayload.callTypeOf(0, true))
        assertEquals(CallPushPayload.CallType.UNKNOWN, CallPushPayload.callTypeOf(VIDEO_CALL_FLAG, false))
    }

    @Test
    fun unknownCallTypeShowsBothAnswerButtons() {
        assertTrue(CallPushPayload.CallType.UNKNOWN.showsVoiceButton)
        assertTrue(CallPushPayload.CallType.UNKNOWN.showsVideoButton)
        assertTrue(CallPushPayload.CallType.VOICE.showsVoiceButton)
        assertFalse(CallPushPayload.CallType.VOICE.showsVideoButton)
        assertFalse(CallPushPayload.CallType.VIDEO.showsVoiceButton)
        assertTrue(CallPushPayload.CallType.VIDEO.showsVideoButton)
    }

    @Test
    fun notificationExtrasCarryTheCallDataButNotTheRoomToken() {
        val call = CallPushPayload.IncomingCall.fromPush(TOKEN, SUBJECT)
        val extras = call.toNotificationExtras(USER_ID, NOTIFICATION_ID)

        assertFalse(extras.containsKey(BundleKeys.KEY_ROOM_TOKEN))
        assertEquals(USER_ID, extras.getLong(BundleKeys.KEY_INTERNAL_USER_ID))
        assertEquals(NOTIFICATION_ID, extras.getInt(BundleKeys.KEY_NOTIFICATION_TIMESTAMP))
        assertEquals(SUBJECT, extras.getString(BundleKeys.KEY_CONVERSATION_DISPLAY_NAME))
        assertEquals(0, extras.getInt(BundleKeys.KEY_CALL_FLAG, -1))
    }

    @Test
    fun changedCallFlagIsCallDataChange() {
        val open = CallPushPayload.IncomingCall.fromPush(TOKEN, SUBJECT).toBundle(USER_ID, NOTIFICATION_ID)
        val update = CallPushPayload.IncomingCall.fromPush(TOKEN, SUBJECT).copy(callFlag = VIDEO_CALL_FLAG)
            .toNotificationExtras(USER_ID, NOTIFICATION_ID)

        assertTrue(CallPushPayload.isCallDataChanged(open, update))
    }

    @Test
    fun changedNameOrOneToOneOrPermissionsOrModeratorIsCallDataChange() {
        val call = CallPushPayload.IncomingCall.fromPush(TOKEN, SUBJECT)
        val open = call.toBundle(USER_ID, NOTIFICATION_ID)

        listOf(
            call.copy(displayName = "Alice"),
            call.copy(name = "alice"),
            call.copy(isOneToOne = true),
            call.copy(canPublishAudio = false),
            call.copy(canPublishVideo = false),
            call.copy(isModerator = true)
        ).forEach {
            assertTrue(CallPushPayload.isCallDataChanged(open, it.toNotificationExtras(USER_ID, NOTIFICATION_ID)))
        }
    }

    @Test
    fun sameCallDataIsNoChange() {
        val call = CallPushPayload.IncomingCall.fromPush(TOKEN, SUBJECT)
        val open = call.toBundle(USER_ID, NOTIFICATION_ID)

        assertFalse(CallPushPayload.isCallDataChanged(open, call.toNotificationExtras(USER_ID, NOTIFICATION_ID)))
    }

    @Test
    fun notificationWithoutCallDataIsNoChange() {
        val open = CallPushPayload.IncomingCall.fromPush(TOKEN, SUBJECT).toBundle(USER_ID, NOTIFICATION_ID)

        assertFalse(CallPushPayload.isCallDataChanged(open, Bundle()))
    }

    @Test
    fun pickedCallDataLeavesOutWhatTheNotificationAlsoCarries() {
        val call = CallPushPayload.IncomingCall.fromPush(TOKEN, SUBJECT)
        val extras = call.toNotificationExtras(USER_ID, NOTIFICATION_ID).apply { putString("android.title", "x") }

        val picked = CallPushPayload.pickCallData(extras)

        assertFalse(picked.containsKey("android.title"))
        assertFalse(picked.containsKey(BundleKeys.KEY_NOTIFICATION_TIMESTAMP))
        assertEquals(SUBJECT, picked.getString(BundleKeys.KEY_CONVERSATION_NAME))
        assertEquals(0, picked.getInt(BundleKeys.KEY_CALL_FLAG, -1))
        assertTrue(picked.getBoolean(BundleKeys.KEY_PARTICIPANT_PERMISSION_CAN_PUBLISH_AUDIO))
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

    private companion object {
        const val TOKEN = "abc123"
        const val SUBJECT = "Alice is calling you"
        const val USER_ID = 7L
        const val NOTIFICATION_ID = 123456
        const val VOICE_CALL_FLAG = ParticipantDto.InCallFlags.IN_CALL or ParticipantDto.InCallFlags.WITH_AUDIO
        const val VIDEO_CALL_FLAG = VOICE_CALL_FLAG or ParticipantDto.InCallFlags.WITH_VIDEO
    }
}
