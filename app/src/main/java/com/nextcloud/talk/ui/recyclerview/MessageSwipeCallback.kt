/*
 * Nextcloud Talk application
 *
 * @author Shain Singh
 * @author Andy Scherzinger
 * Copyright (C) 2021 Shain Singh <shainsingh89@gmail.com>
 * Copyright (C) 2021 Andy Scherzinger <info@andy-scherzinger.de>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
import com.nextcloud.talk.adapters.messages.MagicIncomingTextMessageViewHolder
import com.nextcloud.talk.adapters.messages.MagicOutcomingTextMessageViewHolder
import com.nextcloud.talk.adapters.messages.MagicPreviewMessageViewHolder
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

    companion object {
        const val TAG = "MessageSwipeCallback"
    }

    private var density = 1f

    private lateinit var imageDrawable: Drawable
    private lateinit var shareRound: Drawable

    private var currentItemViewHolder: RecyclerView.ViewHolder? = null
    private lateinit var view: View
    private var dX = 0f

    private var replyButtonProgress: Float = 0.toFloat()
    private var lastReplyButtonAnimationTime: Long = 0
    private var swipeBack = false
    private var isVibrate = false
    private var startTracking = false

    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        if (viewHolder is MagicPreviewMessageViewHolder ||
            viewHolder is MagicIncomingTextMessageViewHolder ||
            viewHolder is MagicOutcomingTextMessageViewHolder
        ) {
            view = viewHolder.itemView
            imageDrawable = AppCompatResources.getDrawable(context, R.drawable.ic_reply)!!
            shareRound = AppCompatResources.getDrawable(context, R.drawable.round_bgnd)!!
            return makeMovementFlags(ACTION_STATE_IDLE, RIGHT)
        }

        // disable swiping any other message type
        return 0
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        return false
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

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

        if (view.translationX < convertToDp(130) || dX < this.dX) {
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
                if (abs(view.translationX) >= this@MessageSwipeCallback.convertToDp(100)) {
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
        val dt = min(17, newTime - lastReplyButtonAnimationTime)
        lastReplyButtonAnimationTime = newTime
        val showing = translationX >= convertToDp(30)
        if (showing) {
            if (replyButtonProgress < 1.0f) {
                replyButtonProgress += dt / 180.0f
                if (replyButtonProgress > 1.0f) {
                    replyButtonProgress = 1.0f
                } else {
                    view.invalidate()
                }
            }
        } else if (translationX <= 0.0f) {
            replyButtonProgress = 0f
            startTracking = false
            isVibrate = false
        } else {
            if (replyButtonProgress > 0.0f) {
                replyButtonProgress -= dt / 180.0f
                if (replyButtonProgress < 0.1f) {
                    replyButtonProgress = 0f
                } else {
                    view.invalidate()
                }
            }
        }

        val alpha: Int
        val scale: Float
        if (showing) {
            scale = if (replyButtonProgress <= 0.8f) {
                1.2f * (replyButtonProgress / 0.8f)
            } else {
                1.2f - 0.2f * ((replyButtonProgress - 0.8f) / 0.2f)
            }
            alpha = min(255f, 255 * (replyButtonProgress / 0.8f)).toInt()
        } else {
            scale = replyButtonProgress
            alpha = min(255f, 255 * replyButtonProgress).toInt()
        }
        shareRound.alpha = alpha
        imageDrawable.alpha = alpha

        if (startTracking) {
            if (!isVibrate && view.translationX >= convertToDp(100)) {
                view.performHapticFeedback(
                    HapticFeedbackConstants.KEYBOARD_TAP,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                )
                isVibrate = true
            }
        }

        val x: Int = if (view.translationX > convertToDp(130)) {
            convertToDp(130) / 2
        } else {
            (view.translationX / 2).toInt()
        }

        val y = (view.top + view.measuredHeight / 2).toFloat()
        shareRound.colorFilter = PorterDuffColorFilter(
            ContextCompat.getColor(context, R.color.bg_message_list_incoming_bubble),
            PorterDuff.Mode.SRC_IN
        )
        imageDrawable.colorFilter = PorterDuffColorFilter(
            ContextCompat.getColor(context, R.color.high_emphasis_text),
            PorterDuff.Mode.SRC_IN
        )

        shareRound.setBounds(
            (x - convertToDp(18) * scale).toInt(),
            (y - convertToDp(18) * scale).toInt(),
            (x + convertToDp(18) * scale).toInt(),
            (y + convertToDp(18) * scale).toInt()
        )
        shareRound.draw(canvas)

        imageDrawable.setBounds(
            (x - convertToDp(12) * scale).toInt(),
            (y - convertToDp(13) * scale).toInt(),
            (x + convertToDp(12) * scale).toInt(),
            (y + convertToDp(11) * scale).toInt()
        )
        imageDrawable.draw(canvas)

        shareRound.alpha = 255
        imageDrawable.alpha = 255
    }

    private fun convertToDp(pixel: Int): Int {
        return dp(pixel.toFloat(), context)
    }

    private fun dp(value: Float, context: Context): Int {
        if (density == 1f) {
            checkDisplaySize(context)
        }
        return if (value == 0f) {
            0
        } else {
            ceil((density * value).toDouble()).toInt()
        }
    }

    private fun checkDisplaySize(context: Context) {
        try {
            density = context.resources.displayMetrics.density
        } catch (e: Exception) {
            Log.w(TAG, "Error calculating density", e)
        }
    }
}
