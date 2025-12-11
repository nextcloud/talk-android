/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.fullscreenfile

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.abs

class FullScreenGestureListener(val context: Context, val callback: () -> Unit) :
    GestureDetector.SimpleOnGestureListener() {

    private val viewConfig = ViewConfiguration.get(context)
    private val minSwipeDistance = viewConfig.scaledTouchSlop
    private val minSwipeVelocity = viewConfig.scaledMinimumFlingVelocity

    override fun onDown(event: MotionEvent): Boolean = true

    override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
        // Safety check for null start event
        if (e1 == null) return false

        val deltaX = e2.x - e1.x
        val deltaY = e2.y - e1.y

        if (abs(deltaY) > abs(deltaX)) { // Intended vertical swipe

            if (deltaY > minSwipeDistance && abs(velocityY) > minSwipeVelocity) {
                callback()
                return true
            }
        }

        return false
    }
}
