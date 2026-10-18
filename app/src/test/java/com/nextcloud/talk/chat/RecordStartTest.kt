/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordStartTest {

    private fun resolve(
        isVideo: Boolean,
        active: Boolean = false,
        video: Boolean = true,
        audio: Boolean = true,
        files: Boolean = true
    ) = resolveRecordStart(isVideo, active, video, audio, files)

    @Test
    fun noRecordingStartsWhileAVideoIsStillFinalizing() {
        assertEquals(RecordStartStep.BUSY, resolve(isVideo = false, active = true))
        assertEquals(RecordStartStep.BUSY, resolve(isVideo = true, active = true))
    }

    @Test
    fun videoNeedsCameraAndMicrophoneButNoAccessToFiles() {
        assertEquals(RecordStartStep.START, resolve(isVideo = true, files = false))
        assertEquals(RecordStartStep.REQUEST_VIDEO_PERMISSIONS, resolve(isVideo = true, video = false))
    }

    @Test
    fun voiceStillNeedsAccessToFiles() {
        assertEquals(RecordStartStep.REQUEST_FILES_PERMISSION, resolve(isVideo = false, files = false))
        assertEquals(RecordStartStep.REQUEST_AUDIO_PERMISSION, resolve(isVideo = false, audio = false))
        assertEquals(RecordStartStep.START, resolve(isVideo = false))
    }

    @Test
    fun clearingGivesTheAudioFocusOfAVideoRecordingBack() {
        assertTrue(releasesVideoAudioFocusOnClear(RecordInputMode.VIDEO, recordingInProgress = true))
        assertFalse(releasesVideoAudioFocusOnClear(RecordInputMode.VIDEO, recordingInProgress = false))
        assertFalse(releasesVideoAudioFocusOnClear(RecordInputMode.VOICE, recordingInProgress = true))
    }

    @Test
    fun touchDoesNotToggleTwiceButAccessibilityClickDoes() {
        var toggles = 0
        val router = RecordButtonClickRouter { toggles++ }

        router.forwardTouch { router.onClick() }
        assertEquals(0, toggles)

        router.onClick()
        assertEquals(1, toggles)
    }

    @Test
    fun clickAfterAnExceptionInTheForwardedTouchStillToggles() {
        var toggles = 0
        val router = RecordButtonClickRouter { toggles++ }

        runCatching { router.forwardTouch { error("boom") } }
        router.onClick()

        assertEquals(1, toggles)
    }
}
