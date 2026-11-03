/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat

import android.view.MotionEvent
import android.view.View

/**
 * What the record button of the message input records while it is held.
 */
enum class RecordInputMode {
    VOICE,
    VIDEO;

    fun toggled(): RecordInputMode = if (this == VOICE) VIDEO else VOICE

    companion object {
        fun fromVideoFlag(isVideo: Boolean): RecordInputMode = if (isVideo) VIDEO else VOICE
    }
}

/**
 * What the record button does once the hold threshold has passed.
 */
enum class RecordStartStep {
    BUSY,
    REQUEST_VIDEO_PERMISSIONS,
    REQUEST_AUDIO_PERMISSION,
    REQUEST_FILES_PERMISSION,
    START
}

/**
 * Decides how a hold on the record button continues. No recording of either kind starts while a video recorder is
 * still active: a video which was released is not over before CameraX has finalized the file, and its end would
 * clear the state of a recording that started meanwhile. The video is written to the app cache and uploaded through
 * the own file provider, so only the voice recording asks for access to the files.
 */
fun resolveRecordStart(
    isVideo: Boolean,
    videoRecorderActive: Boolean,
    videoPermissionGranted: Boolean,
    audioPermissionGranted: Boolean,
    filesPermissionGranted: Boolean
): RecordStartStep =
    when {
        videoRecorderActive -> RecordStartStep.BUSY
        isVideo && !videoPermissionGranted -> RecordStartStep.REQUEST_VIDEO_PERMISSIONS
        !isVideo && !audioPermissionGranted -> RecordStartStep.REQUEST_AUDIO_PERMISSION
        !isVideo && !filesPermissionGranted -> RecordStartStep.REQUEST_FILES_PERMISSION
        else -> RecordStartStep.START
    }

/**
 * Whether clearing the view model has to give the audio focus of a video recording back. The released recorder
 * reports nothing, so the end of the recording is never signalled otherwise.
 */
fun releasesVideoAudioFocusOnClear(activeMode: RecordInputMode, recordingInProgress: Boolean): Boolean =
    activeMode == RecordInputMode.VIDEO && recordingInProgress

/**
 * Lets the record button process a touch event, e.g. for its pressed state, without the click it would generate. The
 * touch listener switches the mode itself, and the view posts its click only after the touch has been handled, so the
 * release is consumed here and never reaches the view. A click which does not come from a touch (TalkBack, Switch
 * Access, keyboard) is not affected and switches the mode through the click listener.
 */
fun forwardRecordButtonTouch(view: View, event: MotionEvent): Boolean =
    if (event.action == MotionEvent.ACTION_UP) {
        view.isPressed = false
        true
    } else {
        view.onTouchEvent(event)
    }
