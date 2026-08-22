/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.activities

import android.view.MotionEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Push-to-talk release detection ([CallActivity.isPushToTalkRelease]).
 *
 * A long-press that ends with ACTION_CANCEL (system gesture, notification shade) must mute the microphone again
 * just like ACTION_UP, otherwise the mic stays hot while the button shows "muted".
 */
class CallActivityPushToTalkTest {

    @Test
    fun `ACTION_UP ends push to talk`() {
        assertTrue(CallActivity.isPushToTalkRelease(MotionEvent.ACTION_UP))
    }

    @Test
    fun `ACTION_CANCEL ends push to talk`() {
        assertTrue(CallActivity.isPushToTalkRelease(MotionEvent.ACTION_CANCEL))
    }

    @Test
    fun `other actions do not end push to talk`() {
        assertFalse(CallActivity.isPushToTalkRelease(MotionEvent.ACTION_DOWN))
        assertFalse(CallActivity.isPushToTalkRelease(MotionEvent.ACTION_MOVE))
        assertFalse(CallActivity.isPushToTalkRelease(MotionEvent.ACTION_OUTSIDE))
    }
}
