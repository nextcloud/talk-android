/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.chat

import androidx.camera.video.VideoRecordEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoMessageRecordingTest {

    @Test
    fun toggleSwitchesBetweenVoiceAndVideo() {
        assertEquals(RecordInputMode.VIDEO, RecordInputMode.VOICE.toggled())
        assertEquals(RecordInputMode.VOICE, RecordInputMode.VIDEO.toggled())
        assertEquals(RecordInputMode.VOICE, RecordInputMode.VOICE.toggled().toggled())
    }

    @Test
    fun storedFlagMapsToMode() {
        assertEquals(RecordInputMode.VIDEO, RecordInputMode.fromVideoFlag(true))
        assertEquals(RecordInputMode.VOICE, RecordInputMode.fromVideoFlag(false))
    }

    @Test
    fun recordingWithoutErrorIsUsable() {
        assertTrue(VideoMessageRecorder.isUsableFinalize(false, VideoRecordEvent.Finalize.ERROR_NONE))
    }

    @Test
    fun recordingStoppedByLimitIsUsable() {
        assertTrue(
            VideoMessageRecorder.isUsableFinalize(true, VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED)
        )
        assertTrue(
            VideoMessageRecorder.isUsableFinalize(true, VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED)
        )
    }

    @Test
    fun recordingWithOtherErrorIsNotUsable() {
        assertFalse(VideoMessageRecorder.isUsableFinalize(true, VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA))
        assertFalse(VideoMessageRecorder.isUsableFinalize(true, VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE))
        assertFalse(VideoMessageRecorder.isUsableFinalize(true, VideoRecordEvent.Finalize.ERROR_UNKNOWN))
    }

    private fun outcome(discard: Boolean, hasError: Boolean, error: Int, durationNanos: Long) =
        VideoMessageRecorder.resolveOutcome(discard, hasError, error, durationNanos)

    @Test
    fun discardedRecordingIsCancelledEvenWhenLongEnough() {
        assertEquals(
            VideoMessageRecorder.Outcome.CANCELLED,
            outcome(true, false, VideoRecordEvent.Finalize.ERROR_NONE, 5_000_000_000L)
        )
    }

    @Test
    fun releasedBeforeAnyDataIsTooShort() {
        assertEquals(
            VideoMessageRecorder.Outcome.TOO_SHORT,
            outcome(false, true, VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA, 0L)
        )
    }

    @Test
    fun recordingShorterThanMinimumIsTooShort() {
        assertEquals(
            VideoMessageRecorder.Outcome.TOO_SHORT,
            outcome(false, false, VideoRecordEvent.Finalize.ERROR_NONE, VideoMessageRecorder.MIN_DURATION_NANOS - 1)
        )
    }

    @Test
    fun cameraErrorIsFailure() {
        assertEquals(
            VideoMessageRecorder.Outcome.FAILED,
            outcome(false, true, VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE, 5_000_000_000L)
        )
    }

    @Test
    fun longEnoughRecordingAndLimitHitAreSent() {
        assertEquals(
            VideoMessageRecorder.Outcome.SEND,
            outcome(false, false, VideoRecordEvent.Finalize.ERROR_NONE, VideoMessageRecorder.MIN_DURATION_NANOS)
        )
        assertEquals(
            VideoMessageRecorder.Outcome.SEND,
            outcome(false, true, VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED, 120_000_000_000L)
        )
    }
}
