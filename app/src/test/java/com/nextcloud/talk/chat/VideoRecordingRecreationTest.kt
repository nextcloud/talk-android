/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.chat

import android.view.OrientationEventListener
import android.view.Surface
import androidx.camera.video.VideoRecordEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoRecordingRecreationTest {

    private fun resume(active: Boolean, pending: Boolean, inProgress: Boolean, locked: Boolean) =
        resolveRecordingResume(active, pending, inProgress, locked)

    @Test
    fun nothingRecordingMeansNothingToResume() {
        assertEquals(RecordingResume.NONE, resume(false, false, false, false))
    }

    @Test
    fun lockedVideoRecordingIsAttachedAgain() {
        assertEquals(RecordingResume.ATTACH, resume(true, false, true, true))
    }

    @Test
    fun heldVideoRecordingIsLockedBecauseTheFingerIsGone() {
        assertEquals(RecordingResume.ATTACH_AND_LOCK, resume(true, false, true, false))
    }

    @Test
    fun heldVoiceRecordingIsLockedToo() {
        assertEquals(RecordingResume.ATTACH_AND_LOCK, resume(false, false, true, false))
    }

    @Test
    fun lockedVoiceRecordingNeedsNoRecorder() {
        assertEquals(RecordingResume.NONE, resume(false, false, true, true))
    }

    @Test
    fun resultWhichArrivedWithoutActivityIsDelivered() {
        assertEquals(RecordingResume.DELIVER_RESULT, resume(false, true, true, true))
    }

    @Test
    fun resultIsDeferredUntilTheChatIsLoaded() {
        assertEquals(
            RecordingResume.DEFER_RESULT,
            resolveRecordingResume(false, true, true, true, chatReady = false)
        )
        assertEquals(
            RecordingResume.DELIVER_RESULT,
            resolveRecordingResume(false, true, true, true, chatReady = true)
        )
    }

    @Test
    fun activeRecordingIsAttachedEvenIfTheChatIsNotLoaded() {
        assertEquals(RecordingResume.ATTACH, resolveRecordingResume(true, false, true, true, chatReady = false))
    }

    @Test
    fun videoRotationFollowsTheSensor() {
        // phone turned clockwise by 90 degrees: the display rotates the other way
        assertEquals(Surface.ROTATION_270, VideoMessageRecorder.videoTargetRotation(90, Surface.ROTATION_0))
        assertEquals(Surface.ROTATION_0, VideoMessageRecorder.videoTargetRotation(10, Surface.ROTATION_90))
        assertEquals(Surface.ROTATION_180, VideoMessageRecorder.videoTargetRotation(180, Surface.ROTATION_0))
        assertEquals(Surface.ROTATION_90, VideoMessageRecorder.videoTargetRotation(270, Surface.ROTATION_0))
    }

    @Test
    fun videoRotationFallsBackToTheDisplayWithoutSensorValue() {
        assertEquals(
            Surface.ROTATION_90,
            VideoMessageRecorder.videoTargetRotation(OrientationEventListener.ORIENTATION_UNKNOWN, Surface.ROTATION_90)
        )
    }

    @Test
    fun cutOffRecordingIsKeptWhenLongEnough() {
        val inactive = VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE
        assertTrue(VideoMessageRecorder.isSalvageable(inactive, VideoMessageRecorder.MIN_DURATION_NANOS, 1024))
        assertFalse(VideoMessageRecorder.isSalvageable(inactive, VideoMessageRecorder.MIN_DURATION_NANOS - 1, 1024))
        assertFalse(VideoMessageRecorder.isSalvageable(inactive, VideoMessageRecorder.MIN_DURATION_NANOS, 0))
        assertFalse(
            VideoMessageRecorder.isSalvageable(
                VideoRecordEvent.Finalize.ERROR_ENCODING_FAILED,
                VideoMessageRecorder.MIN_DURATION_NANOS,
                1024
            )
        )
    }

    @Test
    fun activeVideoRecorderClearsStateOnlyOnFinalize() {
        assertTrue(clearsRecordingStateOnFinalize(isVideo = true, recorderActive = true))
    }

    @Test
    fun idleVideoRecorderAndAudioAreClearedAtOnce() {
        assertFalse(clearsRecordingStateOnFinalize(isVideo = true, recorderActive = false))
        assertFalse(clearsRecordingStateOnFinalize(isVideo = false, recorderActive = false))
        assertFalse(clearsRecordingStateOnFinalize(isVideo = false, recorderActive = true))
    }
}
