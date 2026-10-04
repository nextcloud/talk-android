/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.chat

import org.junit.Assert
import org.junit.Test

class ShouldVibrateOnRecordingChangeTest {

    @Test
    fun startingARecordingVibrates() {
        Assert.assertTrue(shouldVibrateOnRecordingChange(false, true))
    }

    @Test
    fun startingTheFirstRecordingVibrates() {
        Assert.assertTrue(shouldVibrateOnRecordingChange(null, true))
    }

    @Test
    fun endingARecordingVibrates() {
        Assert.assertTrue(shouldVibrateOnRecordingChange(true, false))
    }

    @Test
    fun runningRecordingShownAgainAfterRotationDoesNotVibrate() {
        Assert.assertFalse(shouldVibrateOnRecordingChange(true, true))
    }

    @Test
    fun idleStateShownAgainAfterRotationDoesNotVibrate() {
        Assert.assertFalse(shouldVibrateOnRecordingChange(false, false))
        Assert.assertFalse(shouldVibrateOnRecordingChange(null, false))
    }
}
