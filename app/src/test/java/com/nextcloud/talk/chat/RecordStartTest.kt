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
}
