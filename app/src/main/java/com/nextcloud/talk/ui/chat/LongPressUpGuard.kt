/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chat

import android.text.Selection
import android.text.Spannable
import android.content.Context
import android.view.GestureDetector
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

    /**
     * @param actionMasked [MotionEvent.getActionMasked] of the event
     * @return true if this event must not reach the movement method (UP closing a long press).
     */
    fun shouldConsume(actionMasked: Int): Boolean =
        when (actionMasked) {
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

/**
 * Touch listener for message text with links: reports taps and long presses and, after a long press, keeps
 * the closing ACTION_UP away from LinkMovementMethod so the link is not opened under the message menu.
 */
internal fun linkTouchListener(context: Context, onTap: () -> Unit, onLongPress: () -> Unit): View.OnTouchListener {
    val guard = LongPressUpGuard()
    val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                onTap()
                return false
            }

            override fun onLongPress(e: MotionEvent) {
                guard.onLongPress()
                onLongPress()
            }
        }
    )
    return View.OnTouchListener { view, event ->
        if (guard.shouldConsume(event.actionMasked)) {
            // Long press already showed the menu: end this gesture as cancelled instead of a link tap.
            gestureDetector.onTouchEvent(event)
            cancelTouchSequence(view, event)
            return@OnTouchListener true
        }
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            view.performClick()
        }
        gestureDetector.onTouchEvent(event)
        false
    }
}
