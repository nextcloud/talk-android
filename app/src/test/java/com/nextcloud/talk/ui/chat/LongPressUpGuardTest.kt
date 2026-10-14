/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chat

import android.view.MotionEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LongPressUpGuardTest {

    @Test
    fun `up after long press is consumed`() {
        val guard = LongPressUpGuard()
        assertFalse(guard.shouldConsume(MotionEvent.ACTION_DOWN))
        guard.onLongPress()
        assertTrue(guard.shouldConsume(MotionEvent.ACTION_UP))
    }

    @Test
    fun `short tap up is not consumed`() {
        val guard = LongPressUpGuard()
        assertFalse(guard.shouldConsume(MotionEvent.ACTION_DOWN))
        assertFalse(guard.shouldConsume(MotionEvent.ACTION_UP))
    }

    @Test
    fun `flag is reset after the consumed up`() {
        val guard = LongPressUpGuard()
        guard.shouldConsume(MotionEvent.ACTION_DOWN)
        guard.onLongPress()
        guard.shouldConsume(MotionEvent.ACTION_UP)
        // next gesture without a long press: tap must work (also without a DOWN in between)
        assertFalse(guard.shouldConsume(MotionEvent.ACTION_UP))
    }

    @Test
    fun `cancel and new down reset the flag`() {
        val guard = LongPressUpGuard()
        guard.onLongPress()
        assertFalse(guard.shouldConsume(MotionEvent.ACTION_CANCEL))
        assertFalse(guard.shouldConsume(MotionEvent.ACTION_UP))
        guard.onLongPress()
        assertFalse(guard.shouldConsume(MotionEvent.ACTION_DOWN))
        assertFalse(guard.shouldConsume(MotionEvent.ACTION_UP))
    }

    @Test
    fun `move does not change the flag`() {
        val guard = LongPressUpGuard()
        guard.shouldConsume(MotionEvent.ACTION_DOWN)
        guard.onLongPress()
        assertFalse(guard.shouldConsume(MotionEvent.ACTION_MOVE))
        assertTrue(guard.shouldConsume(MotionEvent.ACTION_UP))
    }

    @Test
    fun `second finger up does not consume and does not reset the flag`() {
        val guard = LongPressUpGuard()
        guard.shouldConsume(MotionEvent.ACTION_DOWN)
        guard.onLongPress()
        assertFalse(guard.shouldConsume(MotionEvent.ACTION_POINTER_DOWN))
        assertFalse(guard.shouldConsume(MotionEvent.ACTION_POINTER_UP))
        assertTrue(guard.shouldConsume(MotionEvent.ACTION_UP))
    }

    @Test
    fun `second finger down does not reset the flag`() {
        val guard = LongPressUpGuard()
        guard.shouldConsume(MotionEvent.ACTION_DOWN)
        guard.onLongPress()
        guard.shouldConsume(MotionEvent.ACTION_POINTER_DOWN)
        assertTrue(guard.shouldConsume(MotionEvent.ACTION_UP))
    }
}
