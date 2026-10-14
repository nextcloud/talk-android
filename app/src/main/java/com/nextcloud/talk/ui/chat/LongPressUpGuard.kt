/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chat

import android.text.Selection
import android.text.Spannable
import android.view.MotionEvent
import android.view.View
import android.widget.TextView

/**
 * Remembers that a long press already fired during the current touch sequence, so that the closing
 * ACTION_UP is not handed to [android.text.method.LinkMovementMethod] (it would call
 * [android.text.style.ClickableSpan.onClick] and open the link right after the message menu).
 */
internal class LongPressUpGuard {
    private var longPressFired = false

    fun onLongPress() {
        longPressFired = true
    }

    /** @return true if this event must not reach the movement method (UP closing a long press). */
    fun shouldConsume(action: Int): Boolean =
        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL -> {
                longPressFired = false
                false
            }

            MotionEvent.ACTION_UP -> {
                val consume = longPressFired
                longPressFired = false
                consume
            }

            else -> false
        }
}

/**
 * Ends the touch sequence of [event] as cancelled: clears the view's pressed state and the link selection,
 * so no link stays highlighted after the long press.
 */
internal fun cancelTouchSequence(view: View, event: MotionEvent) {
    val cancel = MotionEvent.obtain(event)
    cancel.action = MotionEvent.ACTION_CANCEL
    view.onTouchEvent(cancel)
    cancel.recycle()
    ((view as? TextView)?.text as? Spannable)?.let { Selection.removeSelection(it) }
}
