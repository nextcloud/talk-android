/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chat

import android.app.Application
import android.content.Context
import android.os.Looper
import android.os.SystemClock
import android.text.Selection
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * A long press on a link must only show the message menu. The ACTION_UP that closes it must not reach
 * LinkMovementMethod, otherwise ClickableSpan.onClick opens the link as well.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class LinkTouchListenerTest {

    private var spanClicks = 0
    private var taps = 0
    private var longPresses = 0
    private lateinit var textView: TextView

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val text = SpannableString("https://nextcloud.com")
        text.setSpan(
            object : ClickableSpan() {
                override fun onClick(widget: View) {
                    spanClicks++
                }
            },
            0,
            text.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        textView = TextView(context).apply {
            layoutParams = ViewGroup.LayoutParams(WIDTH, HEIGHT)
            setText(text, TextView.BufferType.SPANNABLE)
            movementMethod = LinkMovementMethod.getInstance()
            setOnTouchListener(
                linkTouchListener(context, onTap = { taps++ }, onLongPress = { longPresses++ })
            )
            measure(
                View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY)
            )
            layout(0, 0, WIDTH, HEIGHT)
        }
    }

    private fun touch(action: Int, downTime: Long) {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, TOUCH_X, TOUCH_Y, 0)
        textView.dispatchTouchEvent(event)
        event.recycle()
    }

    private fun idle(millis: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))

    @Test
    fun shortTapOpensTheLink() {
        val downTime = SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN, downTime)
        idle(SHORT_TAP_MS)
        touch(MotionEvent.ACTION_UP, downTime)

        assertEquals(1, spanClicks)
        assertEquals(1, taps)
        assertEquals(0, longPresses)
    }

    @Test
    fun longPressShowsMenuAndDoesNotOpenTheLink() {
        val downTime = SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN, downTime)
        idle(ViewConfiguration.getLongPressTimeout() + 1L)
        touch(MotionEvent.ACTION_UP, downTime)

        assertEquals(1, longPresses)
        assertEquals(0, spanClicks)
        assertEquals(0, taps)
    }

    @Test
    fun linkWorksAgainAfterALongPress() {
        var downTime = SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN, downTime)
        idle(ViewConfiguration.getLongPressTimeout() + 1L)
        touch(MotionEvent.ACTION_UP, downTime)

        downTime = SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN, downTime)
        idle(SHORT_TAP_MS)
        touch(MotionEvent.ACTION_UP, downTime)

        assertEquals(1, longPresses)
        assertEquals(1, spanClicks)
    }

    @Test
    fun longPressLeavesNoPressedStateOrSelection() {
        val downTime = SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN, downTime)
        idle(ViewConfiguration.getLongPressTimeout() + 1L)
        touch(MotionEvent.ACTION_UP, downTime)

        assertEquals(false, textView.isPressed)
        assertEquals(-1, Selection.getSelectionStart(textView.text))
    }

    companion object {
        private const val WIDTH = 1000
        private const val HEIGHT = 100
        private const val TOUCH_X = 20f
        private const val TOUCH_Y = 20f
        private const val SHORT_TAP_MS = 50L
    }
}
