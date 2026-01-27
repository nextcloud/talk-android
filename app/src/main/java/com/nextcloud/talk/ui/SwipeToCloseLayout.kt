/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Enrique López-Mañas <eenriquelopez@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.customview.widget.ViewDragHelper
import kotlin.math.abs

class SwipeToCloseLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var dragHelper: ViewDragHelper
    private var swipeListener: OnSwipeToCloseListener? = null

    interface OnSwipeToCloseListener {
        fun onSwipeToClose()
    }

    init {
        dragHelper = ViewDragHelper.create(this, 1.0f, DragCallback())
    }

    fun setOnSwipeToCloseListener(listener: OnSwipeToCloseListener) {
        this.swipeListener = listener
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        val action = ev.actionMasked
        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
            dragHelper.cancel()
            return false
        }
        return dragHelper.shouldInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        dragHelper.processTouchEvent(ev)
        return true
    }

    private inner class DragCallback : ViewDragHelper.Callback() {
        override fun tryCaptureView(child: View, pointerId: Int): Boolean {
            return true // Capture any child view
        }

        override fun getViewVerticalDragRange(child: View): Int {
            return height
        }

        override fun clampViewPositionVertical(child: View, therapeutic: Int, dy: Int): Int {
            return therapeutic
        }

        override fun onViewReleased(releasedChild: View, xvel: Float, yvel: Float) {
            val totalDragDistance = abs(releasedChild.top)
            if (totalDragDistance > height * DRAG_THRESHOLD || abs(yvel) > dragHelper.minVelocity) {
                swipeListener?.onSwipeToClose()
            } else {
                dragHelper.settleCapturedViewAt(0, 0)
                invalidate()
            }
        }

        override fun onViewPositionChanged(changedView: View, left: Int, top: Int, dx: Int, dy: Int) {
            val progress = 1f - (abs(top).toFloat() / height)
            alpha = progress.coerceIn(MIN_ALPHA, MAX_ALPHA)
        }
    }

    override fun computeScroll() {
        if (dragHelper.continueSettling(true)) {
            postInvalidateOnAnimation()
        }
    }

    companion object {
        private const val DRAG_THRESHOLD = 0.3f
        private const val MIN_ALPHA = 0.5f
        private const val MAX_ALPHA = 1.0f
    }
}
