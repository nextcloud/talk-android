/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.adapters.messages

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.databinding.ReactionsInsideMessageBinding
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.DisplayUtils
import com.vanniktech.emoji.EmojiTextView
import java.util.Locale

class Reaction {

    fun showReactions(
        message: ChatMessage,
        clickOnReaction: (message: ChatMessage, emoji: String) -> Unit,
        longClickOnReaction: (message: ChatMessage) -> Unit,
        binding: ReactionsInsideMessageBinding,
        context: Context,
        isOutgoingMessage: Boolean,
        viewThemeUtils: ViewThemeUtils,
        isBubbled: Boolean = true
    ) {
        binding.reactionsEmojiWrapper.removeAllViews()

        if (message.reactions != null && message.reactions!!.isNotEmpty()) {
            binding.reactionsEmojiWrapper.visibility = View.VISIBLE

            binding.reactionsEmojiWrapper.setOnLongClickListener {
                longClickOnReaction(message)
                true
            }

            val paddingSide = DisplayUtils.convertDpToPixel(EMOJI_AND_AMOUNT_PADDING_SIDE, context).toInt()
            val paddingTop = DisplayUtils.convertDpToPixel(WRAPPER_PADDING_TOP, context).toInt()
            val paddingBottom = DisplayUtils.convertDpToPixel(WRAPPER_PADDING_BOTTOM, context).toInt()

            for ((emoji, amount) in message.reactions!!) {
                val isSelfReaction = message.reactionsSelf != null &&
                    message.reactionsSelf!!.isNotEmpty() &&
                    message.reactionsSelf!!.contains(emoji)
                val textColor = viewThemeUtils.talk.getTextColor(isOutgoingMessage, isSelfReaction, binding)
                val emojiWithAmountWrapper = getEmojiWithAmountWrapperLayout(
                    binding.reactionsEmojiWrapper.context,
                    emoji,
                    amount,
                    EmojiWithAmountWrapperLayoutInfo(
                        textColor,
                        paddingSide,
                        paddingTop,
                        paddingBottom,
                        viewThemeUtils,
                        isOutgoingMessage,
                        isSelfReaction
                    ),
                    isBubbled
                )

                emojiWithAmountWrapper.setOnClickListener {
                    clickOnReaction(message, emoji)
                }
                emojiWithAmountWrapper.setOnLongClickListener {
                    longClickOnReaction(message)
                    false
                }

                binding.reactionsEmojiWrapper.addView(emojiWithAmountWrapper)
            }
        } else {
            binding.reactionsEmojiWrapper.visibility = View.GONE
        }
    }

    private fun getEmojiWithAmountWrapperLayout(
        context: Context,
        emoji: String,
        amount: Int,
        layoutInfo: EmojiWithAmountWrapperLayoutInfo,
        isBubbled: Boolean
    ): MaterialCardView {
        val amountParams = getAmountLayoutParams(context)
        val wrapperParams = getWrapperLayoutParams(context)

        val emojiContainer = MaterialCardView(context)
        val emojiWithAmountWrapper = LinearLayout(context)
        emojiWithAmountWrapper.orientation = LinearLayout.HORIZONTAL

        emojiWithAmountWrapper.addView(getEmojiTextView(context, emoji, amountParams))
        emojiWithAmountWrapper.addView(getReactionCount(context, layoutInfo.textColor, amount, amountParams))
        emojiWithAmountWrapper.layoutParams = wrapperParams

        layoutInfo.viewThemeUtils.talk.setReactionsBackground(
            emojiContainer,
            layoutInfo.isOutgoingMessage,
            layoutInfo.isSelfReaction,
            isBubbled
        )

        emojiWithAmountWrapper.setPaddingRelative(
            layoutInfo.paddingSide,
            layoutInfo.paddingTop,
            layoutInfo.paddingSide,
            layoutInfo.paddingBottom
        )

        emojiContainer.addView(emojiWithAmountWrapper)
        val containerParams = getWrapperLayoutParams(context, REACTION_END_MARGIN)
        containerParams.marginStart = 0
        emojiContainer.layoutParams = containerParams
        emojiContainer.setStrokeWidth(DisplayUtils.convertDpToPixel(EMOJI_CONTAINER_STROKE_WIDTH, context).toInt())

        return emojiContainer
    }

    private fun getEmojiTextView(
        context: Context,
        emoji: String,
        layoutParams: LinearLayout.LayoutParams
    ): EmojiTextView {
        val reactionEmoji = EmojiTextView(context)
        reactionEmoji.text = emoji
        reactionEmoji.layoutParams = layoutParams
        return reactionEmoji
    }

    private fun getReactionCount(
        context: Context,
        textColor: Int,
        amount: Int,
        layoutParams: LinearLayout.LayoutParams
    ): TextView {
        val reactionAmount = TextView(context)
        reactionAmount.setTextColor(textColor)
        reactionAmount.text = String.format(Locale.getDefault(), "%d", amount)
        reactionAmount.layoutParams = layoutParams
        return reactionAmount
    }

    private fun getWrapperLayoutParams(
        context: Context,
        endMarginInDp: Float = EMOJI_END_MARGIN
    ): LinearLayout
        .LayoutParams {
        val wrapperParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        wrapperParams.marginEnd = DisplayUtils.convertDpToPixel(endMarginInDp, context).toInt()
        return wrapperParams
    }

    private fun getAmountLayoutParams(context: Context): LinearLayout.LayoutParams {
        val amountParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        amountParams.marginStart = DisplayUtils.convertDpToPixel(AMOUNT_START_MARGIN, context).toInt()
        return amountParams
    }

    private data class EmojiWithAmountWrapperLayoutInfo(
        val textColor: Int,
        val paddingSide: Int,
        val paddingTop: Int,
        val paddingBottom: Int,
        val viewThemeUtils: ViewThemeUtils,
        val isOutgoingMessage: Boolean,
        val isSelfReaction: Boolean
    )

    companion object {
        const val AMOUNT_START_MARGIN: Float = 4F
        const val EMOJI_END_MARGIN: Float = 4F
        const val REACTION_END_MARGIN: Float = 6F
        const val EMOJI_AND_AMOUNT_PADDING_SIDE: Float = 4F
        const val WRAPPER_PADDING_TOP: Float = 2F
        const val WRAPPER_PADDING_BOTTOM: Float = 3F
        const val EMOJI_CONTAINER_STROKE_WIDTH: Float = 1.5F
    }
}
