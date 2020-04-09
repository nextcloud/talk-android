/*
 *
 *  * Nextcloud Talk application
 *  *
 *  * @author Mario Danic
 *  * Copyright (C) 2017-2020 Mario Danic <mario@lovelyhq.com>
 *  *
 *  * This program is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * at your option) any later version.
 *  *
 *  * This program is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *  Inspired by: https://github.com/izjumovfs/SwipeToReply
 */
package com.nextcloud.talk.newarch.utils.swipe

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.nextcloud.talk.R
import com.nextcloud.talk.newarch.features.chat.ChatElement
import com.nextcloud.talk.newarch.features.chat.ChatElementTypes
import com.nextcloud.talk.newarch.utils.dp
import com.nextcloud.talk.newarch.utils.px
import com.otaliastudios.elements.Adapter
import com.otaliastudios.elements.Element
import kotlin.math.abs
import kotlin.math.min

open class ChatMessageSwipeCallback : ItemTouchHelper.Callback {
    private var context: Context
    private var adapter: Adapter
    private var swiped: Boolean = false
    private var chatMessageSwipeInterface: ChatMessageSwipeInterface
    private var replyIcon: Drawable
    private var replyIconBackground: Drawable
    private var currentViewHolder: RecyclerView.ViewHolder? = null
    private var view: View? = null
    private var dx = 0f
    private var replyButtonProgress = 0f
    private var lastReplyButtonAnimationTime: Long = 0
    private var swipeBack = false
    private var isVibrating = false
    private var startTracking = false
    private var mBackgroundColor = 0x20606060
    private val replyBackgroundOffset = 18
    private val replyIconXOffset = 12
    private val replyIconYOffset = 11

    constructor(context: Context, adapter: Adapter, chatMessageSwipeInterface: ChatMessageSwipeInterface) {
        this.context = context
        this.adapter = adapter
        this.chatMessageSwipeInterface = chatMessageSwipeInterface
        replyIcon = context.resources.getDrawable(R.drawable.ic_reply_white_24dp)
        replyIconBackground = context.resources.getDrawable(R.drawable.ic_round_shape)
    }

    constructor(context: Context, adapter: Adapter, chatMessageSwipeInterface: ChatMessageSwipeInterface, replyIcon: Int, replyIconBackground: Int, backgroundColor: Int) {
        this.context = context
        this.adapter = adapter
        this.chatMessageSwipeInterface = chatMessageSwipeInterface
        this.replyIcon = context.resources.getDrawable(replyIcon)
        this.replyIconBackground = context.resources.getDrawable(replyIconBackground)
        mBackgroundColor = backgroundColor
    }


    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        val position: Int = viewHolder.adapterPosition
        val element = adapter.elementAt(position)
        if (element != null) {
            val adapterChatElement = element.element as Element<ChatElement>
            if (adapterChatElement.data is ChatElement) {
                val chatElement = adapterChatElement.data as ChatElement
                if (chatElement.elementType == ChatElementTypes.CHAT_MESSAGE) {
                    view = viewHolder.itemView
                    return makeMovementFlags(ItemTouchHelper.ACTION_STATE_IDLE, ItemTouchHelper.RIGHT)
                }
            }
        }
        return 0
    }

    override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
        return false
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
    }

    override fun convertToAbsoluteDirection(flags: Int, layoutDirection: Int): Int {
        if (swipeBack) {
            swipeBack = false
            return 0
        }
        return super.convertToAbsoluteDirection(flags, layoutDirection)
    }

    override fun onChildDraw(c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            setTouchListener(recyclerView, viewHolder)
        }
        if (view!!.translationX < convertToDp(130) || dX < dx) {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            dx = dX
            startTracking = true
        }
        currentViewHolder = viewHolder
        drawReplyButton(c)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setTouchListener(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        recyclerView.setOnTouchListener(OnTouchListener { v, event ->
            swipeBack = event.action == MotionEvent.ACTION_CANCEL || event.action == MotionEvent.ACTION_UP
            if (swipeBack) {
                if (abs(view!!.translationX) >= convertToDp(100)) {
                    swiped = true
                }
            }
            false
        })
    }

    private fun convertToDp(pixels: Int): Int {
        return pixels.dp
    }

    private fun drawReplyButton(canvas: Canvas) {
        currentViewHolder?.let { currentViewHolder ->
            view?.let { view ->
                val translationX = view.translationX
                val newTime = System.currentTimeMillis()
                val dt = min(17, newTime - lastReplyButtonAnimationTime)
                lastReplyButtonAnimationTime = newTime
                var showing = false
                if (translationX >= convertToDp(30)) {
                    showing = true
                }
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
                    isVibrating = false
                    if (swiped) {
                        chatMessageSwipeInterface.onSwipePerformed(currentViewHolder.adapterPosition)
                        swiped = false
                    }
                } else {
                    if (replyButtonProgress > 0.0f) {
                        replyButtonProgress -= dt / 180.0f
                        if (replyButtonProgress < 0.1f) {
                            replyButtonProgress = 0f
                        }
                    }
                    view.invalidate()
                }
                val alpha: Int
                val scale: Float
                if (showing) {
                    scale = if (replyButtonProgress <= 0.8f) {
                        1.2f * (replyButtonProgress / 0.8f)
                    } else {
                        1.2f - 0.2f * ((replyButtonProgress - 0.8f) / 0.2f)
                    }
                    alpha = min(255, 255 * (replyButtonProgress / 0.8f).toInt())
                } else {
                    scale = replyButtonProgress
                    alpha = min(255, 255 * replyButtonProgress.toInt())
                }
                replyIconBackground.alpha = alpha
                replyIcon.alpha = alpha
                if (startTracking) {
                    if (!isVibrating && view.translationX >= convertToDp(100)) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
                    }
                    isVibrating = true
                }
                val x: Int = if (view.translationX > convertToDp(130)) {
                    convertToDp(130) / 2
                } else {
                    view.translationX.toInt() / 2
                }
                val y: Float = view.top + view.measuredHeight.toFloat() / 2
                replyIconBackground.setColorFilter(mBackgroundColor, PorterDuff.Mode.MULTIPLY)
                replyIconBackground.bounds = Rect(
                        (x - convertToDp(replyBackgroundOffset) * scale).toInt(),
                        (y - convertToDp(replyBackgroundOffset) * scale).toInt(),
                        (x + convertToDp(replyBackgroundOffset) * scale).toInt(),
                        (y + convertToDp(replyBackgroundOffset) * scale).toInt()
                )
                replyIconBackground.draw(canvas)
                replyIcon.bounds = Rect(
                        (x - convertToDp(replyIconXOffset) * scale).toInt(),
                        (y - convertToDp(replyIconYOffset) * scale).toInt(),
                        (x + convertToDp(replyIconXOffset) * scale).toInt(),
                        (y + convertToDp(replyIconYOffset) * scale).toInt()
                )
                replyIcon.draw(canvas)
                replyIconBackground.alpha = 255
                replyIcon.alpha = 255
            }
        }
    }
}