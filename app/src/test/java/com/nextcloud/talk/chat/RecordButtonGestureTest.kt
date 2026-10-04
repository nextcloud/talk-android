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

class RecordButtonGestureTest {

    private val gesture = RecordButtonGesture(cancelX = -300f)

    @Test
    fun releaseBeforeThresholdIsTapThatTogglesMode() {
        gesture.down()
        assertEquals(RecordButtonGesture.Release.TOGGLE_MODE, gesture.up())
        assertEquals(RecordButtonGesture.State.IDLE, gesture.state)
    }

    @Test
    fun releaseAfterThresholdDoesNotToggleMode() {
        gesture.down()
        assertTrue(gesture.holdElapsed())
        assertEquals(RecordButtonGesture.Release.NONE, gesture.up())
    }

    @Test
    fun holdStartsRecordingOnlyOnce() {
        gesture.down()
        assertTrue(gesture.holdElapsed())
        assertFalse(gesture.holdElapsed())
    }

    @Test
    fun holdTimerAfterReleaseDoesNotStartRecording() {
        gesture.down()
        gesture.up()
        assertFalse(gesture.holdElapsed())
    }

    @Test
    fun swipeLeftBeforeStartCancelsWithoutToggle() {
        gesture.down()
        assertFalse(gesture.move(-100f))
        assertTrue(gesture.move(-301f))
        assertFalse(gesture.holdElapsed())
        assertEquals(RecordButtonGesture.Release.NONE, gesture.up())
    }

    @Test
    fun swipeAfterStartIsLeftToRecordingHandler() {
        gesture.down()
        gesture.holdElapsed()
        assertFalse(gesture.move(-500f))
        assertEquals(RecordButtonGesture.State.HELD, gesture.state)
    }

    @Test
    fun systemCancelNeverToggles() {
        gesture.down()
        gesture.cancel()
        assertEquals(RecordButtonGesture.Release.NONE, gesture.up())
        assertFalse(gesture.holdElapsed())
    }

    @Test
    fun releaseWithoutDownDoesNothing() {
        assertEquals(RecordButtonGesture.Release.NONE, gesture.up())
    }

    @Test
    fun nextTouchStartsFresh() {
        gesture.down()
        gesture.holdElapsed()
        gesture.up()
        gesture.down()
        assertEquals(RecordButtonGesture.Release.TOGGLE_MODE, gesture.up())
    }

    @Test
    fun thresholdFollowsSystemLongPressWithinBounds() {
        assertEquals(400L, RecordButtonGesture.holdThresholdMs(400))
        assertEquals(500L, RecordButtonGesture.holdThresholdMs(500))
        assertEquals(400L, RecordButtonGesture.holdThresholdMs(250))
        assertEquals(600L, RecordButtonGesture.holdThresholdMs(1500))
    }
}
