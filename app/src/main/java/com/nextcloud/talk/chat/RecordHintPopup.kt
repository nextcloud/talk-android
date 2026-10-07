/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.view.doOnLayout
import com.google.android.material.color.MaterialColors
import com.nextcloud.talk.R

/**
 * Hint bubble above the record button, with an arrow pointing at the button. It does not take touches, so the
 * button stays usable while the hint is visible.
 */
class RecordHintPopup(private val anchor: View) {

    private val handler = Handler(Looper.getMainLooper())
    private val dismissRunnable = Runnable { dismiss() }
    private var popup: PopupWindow? = null
    private var shown = 0
    private var layoutListener: View.OnLayoutChangeListener? = null

    /**
     * Shows the hint above the anchor. The position is taken after the layout of the anchor, because the caller may
     * have changed the layout just before (the recording UI is hidden and the input shown again, which moves the
     * button), and it follows the anchor while the hint is shown.
     */
    fun show(@StringRes messageRes: Int) {
        dismiss()
        if (!anchor.isAttachedToWindow) return
        val token = ++shown
        anchor.doOnLayout {
            if (token == shown && it.isAttachedToWindow) present(messageRes)
        }
    }

    private fun present(@StringRes messageRes: Int) {
        val context = anchor.context
        val content = LayoutInflater.from(context).inflate(R.layout.view_record_hint, null)
        val bubbleColor = MaterialColors.getColor(anchor, com.google.android.material.R.attr.colorSurfaceInverse)
        val textColor = MaterialColors.getColor(anchor, com.google.android.material.R.attr.colorOnSurfaceInverse)
        val text = content.findViewById<TextView>(R.id.recordHintText)
        text.setText(messageRes)
        text.setTextColor(textColor)
        (text.background.mutate() as GradientDrawable).setColor(bubbleColor)
        val arrow = content.findViewById<ImageView>(R.id.recordHintArrow)
        arrow.imageTintList = ColorStateList.valueOf(bubbleColor)

        val metrics = context.resources.displayMetrics
        val margin = (SCREEN_MARGIN_DP * metrics.density).toInt()
        val gap = (GAP_DP * metrics.density).toInt()
        val arrowWidth = (ARROW_WIDTH_DP * metrics.density).toInt()
        content.measure(
            View.MeasureSpec.makeMeasureSpec(anchor.rootView.width - 2 * margin, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val hintWidth = content.measuredWidth

        // showAtLocation positions relative to the window of the anchor, so the anchor is measured in its window
        fun placement(): Placement {
            val location = IntArray(2)
            anchor.getLocationInWindow(location)
            return hintPlacement(
                anchorLeft = location[0],
                anchorTop = location[1],
                anchorWidth = anchor.width,
                windowWidth = anchor.rootView.width,
                windowHeight = anchor.rootView.height,
                hintWidth = hintWidth,
                margin = margin,
                gap = gap,
                arrowWidth = arrowWidth
            )
        }

        fun applyArrow(placement: Placement) {
            val arrowParams = arrow.layoutParams as LinearLayout.LayoutParams
            arrowParams.leftMargin = placement.arrowLeftMargin
            arrow.layoutParams = arrowParams
        }

        val first = placement()
        applyArrow(first)
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        val window = PopupWindow(content, wrap, wrap, false)
            .apply {
                isTouchable = false
                isFocusable = false
                isOutsideTouchable = false
                // anchored by its bottom edge, so that the hint grows upwards, away from the button
                showAtLocation(anchor, Gravity.BOTTOM or Gravity.LEFT, first.x, first.bottomOffset)
            }
        popup = window

        val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val moved = placement()
            applyArrow(moved)
            window.update(moved.x, moved.bottomOffset, -1, -1)
        }
        layoutListener = listener
        anchor.addOnLayoutChangeListener(listener)
        handler.postDelayed(dismissRunnable, SHOW_DURATION_MS)
    }

    fun dismiss() {
        shown++
        handler.removeCallbacks(dismissRunnable)
        layoutListener?.let { anchor.removeOnLayoutChangeListener(it) }
        layoutListener = null
        popup?.dismiss()
        popup = null
    }

    /**
     * Where the hint goes, in the coordinates of the window of the anchor: [x] from the left edge of the window,
     * [bottomOffset] from the bottom edge of the window up to the bottom edge of the hint.
     */
    data class Placement(val x: Int, val bottomOffset: Int, val arrowLeftMargin: Int)

    companion object {
        const val SHOW_DURATION_MS = 1500L
        private const val SCREEN_MARGIN_DP = 8
        private const val GAP_DP = 4
        private const val ARROW_WIDTH_DP = 16

        /**
         * The hint sits above the anchor with [gap] between the bottom edge of the hint and the top edge of the
         * anchor, centred on it and kept [margin] away from the side edges of the window; the arrow points at the
         * centre of the anchor. The height of the hint does not enter: it is anchored by its bottom edge.
         */
        @Suppress("LongParameterList")
        fun hintPlacement(
            anchorLeft: Int,
            anchorTop: Int,
            anchorWidth: Int,
            windowWidth: Int,
            windowHeight: Int,
            hintWidth: Int,
            margin: Int,
            gap: Int,
            arrowWidth: Int
        ): Placement {
            val anchorCenterX = anchorLeft + anchorWidth / 2
            val x = (anchorCenterX - hintWidth / 2).coerceIn(margin, maxOf(margin, windowWidth - margin - hintWidth))
            val arrow = (anchorCenterX - x - arrowWidth / 2).coerceIn(0, maxOf(0, hintWidth - arrowWidth))
            return Placement(x, windowHeight - anchorTop + gap, arrow)
        }
    }
}
