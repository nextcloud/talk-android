/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2021 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2019 Shain Singh <shainsingh89@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Based on the MessageSwipeController by Shain Singh at:
 * https://github.com/shainsingh89/SwipeToReply/blob/master/app/src/main/java/com/shain/messenger/MessageSwipeController.kt
 */
package com.nextcloud.talk.ui.recyclerview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_IDLE
import androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_SWIPE
import androidx.recyclerview.widget.ItemTouchHelper.RIGHT
import androidx.recyclerview.widget.RecyclerView
import com.nextcloud.talk.R
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min

/**
 * Callback implementation for swipe-right-gesture on messages.
 *
 * @property context activity's context to load resources like drawables.
 * @property messageSwipeActions the actions to be executed upon swipe-right.
 * @constructor Creates as swipe-right callback for messages
 */
class MessageSwipeCallback(private val context: Context, private val messageSwipeActions: MessageSwipeActions) :
    ItemTouchHelper.Callback() {

    private var density = DENSITY_DEFAULT

    private lateinit var imageDrawable: Drawable
    private lateinit var shareRound: Drawable

    private var currentItemViewHolder: RecyclerView.ViewHolder? = null
    private lateinit var view: View
    private var dX = 0f

    private var replyButtonProgress: Float = NO_PROGRESS
    private var lastReplyButtonAnimationTime: Long = 0
    private var swipeBack = false
    private var isVibrate = false
    private var startTracking = false

    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        view = viewHolder.itemView
        if (viewHolder.itemView.getTag(R.string.replyable_message_view_tag) != null &&
            viewHolder.itemView.getTag(R.string.replyable_message_view_tag) as Boolean
        ) {
            imageDrawable = AppCompatResources.getDrawable(context, R.drawable.ic_reply)!!
            shareRound = AppCompatResources.getDrawable(context, R.drawable.round_bgnd)!!
            return makeMovementFlags(ACTION_STATE_IDLE, RIGHT)
        }

        // disable swiping any other message type
        return NO_SWIPE_FLAG
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        // unused atm
    }

    override fun convertToAbsoluteDirection(flags: Int, layoutDirection: Int): Int {
        if (swipeBack) {
            swipeBack = false
            return 0
        }
        return super.convertToAbsoluteDirection(flags, layoutDirection)
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        if (actionState == ACTION_STATE_SWIPE) {
            setTouchListener(recyclerView, viewHolder)
        }

        if (view.translationX < convertToDp(SWIPE_LIMIT) || dX < this.dX) {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            this.dX = dX
            startTracking = true
        }
        currentItemViewHolder = viewHolder
        drawReplyButton(c)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setTouchListener(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        recyclerView.setOnTouchListener { _, event ->
            swipeBack = event.action == MotionEvent.ACTION_CANCEL || event.action == MotionEvent.ACTION_UP
            if (swipeBack) {
                if (abs(view.translationX) >= this@MessageSwipeCallback.convertToDp(REPLY_POINT)) {
                    messageSwipeActions.showReplyUI(viewHolder.adapterPosition)
                }
            }
            false
        }
    }

    private fun drawReplyButton(canvas: Canvas) {
        if (currentItemViewHolder == null) {
            return
        }
        val translationX = view.translationX
        val newTime = System.currentTimeMillis()
        val dt = min(MIN_ANIMATION_TIME_IN_MILLIS, newTime - lastReplyButtonAnimationTime)
        lastReplyButtonAnimationTime = newTime
        val showing = translationX >= convertToDp(SHOW_REPLY_ICON_POINT)
        if (showing) {
            if (replyButtonProgress < FULL_PROGRESS) {
                replyButtonProgress += dt / PROGRESS_CALCULATION_TIME_BASE
                if (replyButtonProgress > FULL_PROGRESS) {
                    replyButtonProgress = FULL_PROGRESS
                } else {
                    view.invalidate()
                }
            }
        } else if (translationX <= NO_PROGRESS) {
            replyButtonProgress = NO_PROGRESS
            startTracking = false
            isVibrate = false
        } else {
            if (replyButtonProgress > NO_PROGRESS) {
                replyButtonProgress -= dt / PROGRESS_CALCULATION_TIME_BASE
                if (replyButtonProgress < PROGRESS_THRESHOLD) {
                    replyButtonProgress = NO_PROGRESS
                } else {
                    view.invalidate()
                }
            }
        }

        val alpha: Int
        val scale: Float
        if (showing) {
            scale = if (replyButtonProgress <= SCALE_PROGRESS_TOP_THRESHOLD) {
                SCALE_PROGRESS_MULTIPLIER * (replyButtonProgress / SCALE_PROGRESS_TOP_THRESHOLD)
            } else {
                SCALE_PROGRESS_MULTIPLIER -
                    SCALE_PROGRESS_BOTTOM_THRESHOLD *
                    ((replyButtonProgress - SCALE_PROGRESS_TOP_THRESHOLD) / SCALE_PROGRESS_BOTTOM_THRESHOLD)
            }
            alpha = min(FULLY_OPAQUE, FULLY_OPAQUE * (replyButtonProgress / SCALE_PROGRESS_TOP_THRESHOLD)).toInt()
        } else {
            scale = replyButtonProgress
            alpha = min(FULLY_OPAQUE, FULLY_OPAQUE * replyButtonProgress).toInt()
        }

        if (startTracking && !isVibrate && view.translationX >= convertToDp(REPLY_POINT)) {
            view.performHapticFeedback(
                HapticFeedbackConstants.KEYBOARD_TAP,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            )
            isVibrate = true
        }

        drawReplyIcon(alpha, scale, canvas)
    }

    private fun drawReplyIcon(alpha: Int, scale: Float, canvas: Canvas) {
        val x: Int = if (view.translationX > convertToDp(SWIPE_LIMIT)) {
            convertToDp(SWIPE_LIMIT) / AXIS_BASE
        } else {
            (view.translationX / AXIS_BASE).toInt()
        }

        val y = (view.top + view.measuredHeight / AXIS_BASE).toFloat()

        shareRound.alpha = alpha
        imageDrawable.alpha = alpha

        shareRound.colorFilter = PorterDuffColorFilter(
            ContextCompat.getColor(context, R.color.bg_message_list_incoming_bubble),
            PorterDuff.Mode.SRC_IN
        )
        imageDrawable.colorFilter = PorterDuffColorFilter(
            ContextCompat.getColor(context, R.color.high_emphasis_text),
            PorterDuff.Mode.SRC_IN
        )

        shareRound.setBounds(
            (x - convertToDp(BACKGROUND_BOUNDS_PIXEL) * scale).toInt(),
            (y - convertToDp(BACKGROUND_BOUNDS_PIXEL) * scale).toInt(),
            (x + convertToDp(BACKGROUND_BOUNDS_PIXEL) * scale).toInt(),
            (y + convertToDp(BACKGROUND_BOUNDS_PIXEL) * scale).toInt()
        )
        shareRound.draw(canvas)

        imageDrawable.setBounds(
            (x - convertToDp(ICON_BOUNDS_PIXEL_LEFT) * scale).toInt(),
            (y - convertToDp(ICON_BOUNDS_PIXEL_TOP) * scale).toInt(),
            (x + convertToDp(ICON_BOUNDS_PIXEL_RIGHT) * scale).toInt(),
            (y + convertToDp(ICON_BOUNDS_PIXEL_BOTTOM) * scale).toInt()
        )
        imageDrawable.draw(canvas)

        shareRound.alpha = FULLY_OPAQUE_INT
        imageDrawable.alpha = FULLY_OPAQUE_INT
    }

    private fun convertToDp(pixel: Int): Int = dp(pixel.toFloat(), context)

    private fun dp(value: Float, context: Context): Int {
        if (density == DENSITY_DEFAULT) {
            checkDisplaySize(context)
        }
        return if (value == DENSITY_ZERO) {
            DENSITY_ZERO_INT
        } else {
            ceil((density * value).toDouble()).toInt()
        }
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    private fun checkDisplaySize(context: Context) {
        try {
            density = context.resources.displayMetrics.density
        } catch (e: Exception) {
            Log.w(TAG, "Error calculating density", e)
        }
    }

    companion object {
        const val TAG = "MessageSwipeCallback"
        const val NO_SWIPE_FLAG: Int = 0
        const val FULLY_OPAQUE: Float = 255f
        const val FULLY_OPAQUE_INT: Int = 255
        const val DENSITY_DEFAULT: Float = 1f
        const val DENSITY_ZERO: Float = 0f
        const val DENSITY_ZERO_INT: Int = 0
        const val REPLY_POINT: Int = 100
        const val SWIPE_LIMIT: Int = 130
        const val SHOW_REPLY_ICON_POINT: Int = 30
        const val MIN_ANIMATION_TIME_IN_MILLIS: Long = 17
        const val FULL_PROGRESS: Float = 1.0f
        const val NO_PROGRESS: Float = 0.0f
        const val PROGRESS_THRESHOLD: Float = 0.1f
        const val PROGRESS_CALCULATION_TIME_BASE: Float = 180.0f
        const val SCALE_PROGRESS_MULTIPLIER: Float = 1.2f
        const val SCALE_PROGRESS_TOP_THRESHOLD: Float = 0.8f
        const val SCALE_PROGRESS_BOTTOM_THRESHOLD: Float = 0.2f
        const val AXIS_BASE: Int = 2
        const val BACKGROUND_BOUNDS_PIXEL: Int = 18
        const val ICON_BOUNDS_PIXEL_LEFT: Int = 12
        const val ICON_BOUNDS_PIXEL_TOP: Int = 13
        const val ICON_BOUNDS_PIXEL_RIGHT: Int = 12
        const val ICON_BOUNDS_PIXEL_BOTTOM: Int = 11
    }
}
