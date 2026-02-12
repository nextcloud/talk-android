/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Sowjanya Kota <sowjanya.kch@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat

import android.text.Spannable
import android.text.method.LinkMovementMethod
import android.text.method.MovementMethod
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.TextView

// Source - https://stackoverflow.com/a/39439031

class NoLongClickMovementMethod : LinkMovementMethod() {
    var longClickDelay: Long = ViewConfiguration.getLongPressTimeout().toLong()
    var startTime: Long = 0

    override fun onTouchEvent(widget: TextView?, buffer: Spannable?, event: MotionEvent): Boolean {
        val action: Int = event.action

        if (action == MotionEvent.ACTION_DOWN) {
            startTime = System.currentTimeMillis()
        }

        if (action == MotionEvent.ACTION_UP) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - startTime >= longClickDelay) return true
        }
        return super.onTouchEvent(widget, buffer, event)
    }

    companion object {
        private val linkMovementMethod = NoLongClickMovementMethod()

        val instance: MovementMethod
            get() = linkMovementMethod
    }
}
