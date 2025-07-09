/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.call

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.nextcloud.talk.R
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.vanniktech.emoji.EmojiTextView

class ReactionAnimator(
    val context: Context,
    private val startPointView: RelativeLayout,
    val viewThemeUtils: ViewThemeUtils?
) {
    private val reactionsList: MutableList<CallReaction> = ArrayList()

    fun addReaction(emoji: String, displayName: String) {
        val callReaction = CallReaction(emoji, displayName)
        reactionsList.add(callReaction)

        if (reactionsList.size == 1) {
            animateReaction(reactionsList[0])
        }
    }

    private fun animateReaction(callReaction: CallReaction) {
        val reactionWrapper = getReactionWrapperView(callReaction)

        val params = RelativeLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            leftMargin = 0
            bottomMargin = 0
        }

        params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, 1)
        startPointView.addView(reactionWrapper, params)

        val moveWithFullAlpha = ObjectAnimator.ofFloat(
            reactionWrapper,
            TRANSLATION_Y_PROPERTY,
            POSITION_Y_WITH_FULL_ALPHA
        )
        moveWithFullAlpha.duration = DURATION_FULL_ALPHA
        moveWithFullAlpha.interpolator = LinearInterpolator()

        val moveWithDecreasingAlpha = ObjectAnimator.ofFloat(
            reactionWrapper,
            TRANSLATION_Y_PROPERTY,
            POSITION_Y_WITH_DECREASING_ALPHA
        )
        moveWithDecreasingAlpha.duration = DURATION_DECREASING_ALPHA
        moveWithDecreasingAlpha.interpolator = LinearInterpolator()

        val decreasingAlpha: ObjectAnimator = ObjectAnimator.ofFloat(
            reactionWrapper,
            ALPHA_PROPERTY,
            ZERO_ALPHA
        )
        decreasingAlpha.duration = DURATION_DECREASING_ALPHA

        val animatorWithFullAlpha = AnimatorSet()
        animatorWithFullAlpha.play(moveWithFullAlpha)

        animatorWithFullAlpha.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                reactionsList.remove(callReaction)
                if (reactionsList.isNotEmpty()) {
                    animateReaction(reactionsList[0])
                }
            }
        })

        val animatorWithDecreasingAlpha = AnimatorSet()
        animatorWithDecreasingAlpha.playTogether(moveWithDecreasingAlpha, decreasingAlpha)

        val finalAnimator = AnimatorSet()
        finalAnimator.play(animatorWithFullAlpha).before(animatorWithDecreasingAlpha)

        finalAnimator.start()
    }

    private fun getReactionWrapperView(callReaction: CallReaction): LinearLayout {
        val reactionWrapper = LinearLayout(context)
        reactionWrapper.orientation = LinearLayout.HORIZONTAL

        val emojiView = EmojiTextView(context)
        emojiView.text = callReaction.emoji
        emojiView.textSize = TEXT_SIZE

        val nameView = getNameView(callReaction)
        reactionWrapper.addView(emojiView)
        reactionWrapper.addView(nameView)
        return reactionWrapper
    }

    @SuppressLint("SetTextI18n")
    private fun getNameView(callReaction: CallReaction): TextView {
        val nameView = TextView(context)

        val nameViewParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        nameViewParams.setMargins(HORIZONTAL_MARGIN, 0, HORIZONTAL_MARGIN, BOTTOM_MARGIN)
        nameView.layoutParams = nameViewParams

        nameView.text = "  " + callReaction.userName + "  "
        nameView.setTextColor(context.resources.getColor(R.color.white, null))

        val backgroundColor = ContextCompat.getColor(
            context,
            R.color.colorPrimary
        )

        val drawable = AppCompatResources
            .getDrawable(context, R.drawable.reaction_self_background)!!
            .mutate()
        DrawableCompat.setTintList(
            drawable,
            ColorStateList.valueOf(backgroundColor)
        )
        nameView.background = drawable
        return nameView
    }

    companion object {
        private const val TRANSLATION_Y_PROPERTY = "translationY"

        // 1333ms to move emoji up 400px with full alpha
        private const val DURATION_FULL_ALPHA = 1333L
        private const val POSITION_Y_WITH_FULL_ALPHA = -400f

        // 666ms to move emoji up 200px while decreasing alpha
        private const val DURATION_DECREASING_ALPHA = 666L
        private const val POSITION_Y_WITH_DECREASING_ALPHA = -600f

        private const val ZERO_ALPHA = 0f
        private const val ALPHA_PROPERTY = "alpha"

        private const val TEXT_SIZE = 20f
        private const val HORIZONTAL_MARGIN: Int = 20
        private const val BOTTOM_MARGIN: Int = 5
    }
}
data class CallReaction(var emoji: String, var userName: String)
