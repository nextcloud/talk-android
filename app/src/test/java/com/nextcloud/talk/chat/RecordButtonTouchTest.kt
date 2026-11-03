/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat

import android.app.Application
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.widget.ImageButton
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class RecordButtonTouchTest {
    private var clicks = 0
    private lateinit var button: ImageButton

    @Before
    fun setUp() {
        clicks = 0
        button = ImageButton(ApplicationProvider.getApplicationContext())
        button.setOnClickListener { clicks++ }
        button.setOnTouchListener { v, event -> forwardRecordButtonTouch(v, event) }
    }

    private fun touch(action: Int) {
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now, action, 1f, 1f, 0)
        button.dispatchTouchEvent(event)
        event.recycle()
    }

    @Test
    fun releaseDoesNotGenerateAClickEvenAfterThePostedWorkRuns() {
        touch(MotionEvent.ACTION_DOWN)
        touch(MotionEvent.ACTION_UP)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(0, clicks)
        assertFalse(button.isPressed)
    }

    @Test
    fun cancelDoesNotGenerateAClickAndClearsPressed() {
        touch(MotionEvent.ACTION_DOWN)
        touch(MotionEvent.ACTION_CANCEL)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(0, clicks)
        assertFalse(button.isPressed)
    }

    @Test
    fun accessibilityClickStillReachesTheClickListener() {
        button.performClick()

        assertEquals(1, clicks)
    }
}
