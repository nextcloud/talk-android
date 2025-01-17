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
import com.nextcloud.talk.databinding.ReactionsInsideMessageBinding
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.DisplayUtils
import com.vanniktech.emoji.EmojiTextView

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

            val amountParams = getAmountLayoutParams(context)
            val wrapperParams = getWrapperLayoutParams(context)

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
                        amountParams,
                        wrapperParams,
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
    ): LinearLayout {
        val emojiWithAmountWrapper = LinearLayout(context)
        emojiWithAmountWrapper.orientation = LinearLayout.HORIZONTAL

        emojiWithAmountWrapper.addView(getEmojiTextView(context, emoji))
        emojiWithAmountWrapper.addView(getReactionCount(context, layoutInfo.textColor, amount, layoutInfo.amountParams))
        emojiWithAmountWrapper.layoutParams = layoutInfo.wrapperParams

        if (layoutInfo.isSelfReaction) {
            layoutInfo.viewThemeUtils.talk.setCheckedBackground(
                emojiWithAmountWrapper,
                layoutInfo.isOutgoingMessage,
                isBubbled
            )

            emojiWithAmountWrapper.setPaddingRelative(
                layoutInfo.paddingSide,
                layoutInfo.paddingTop,
                layoutInfo.paddingSide,
                layoutInfo.paddingBottom
            )
        } else {
            emojiWithAmountWrapper.setPaddingRelative(
                0,
                layoutInfo.paddingTop,
                layoutInfo.paddingSide,
                layoutInfo.paddingBottom
            )
        }
        return emojiWithAmountWrapper
    }

    private fun getEmojiTextView(context: Context, emoji: String): EmojiTextView {
        val reactionEmoji = EmojiTextView(context)
        reactionEmoji.text = emoji
        return reactionEmoji
    }

    private fun getReactionCount(
        context: Context,
        textColor: Int,
        amount: Int,
        amountParams: LinearLayout.LayoutParams
    ): TextView {
        val reactionAmount = TextView(context)
        reactionAmount.setTextColor(textColor)
        reactionAmount.text = amount.toString()
        reactionAmount.layoutParams = amountParams
        return reactionAmount
    }

    private fun getWrapperLayoutParams(context: Context): LinearLayout.LayoutParams {
        val wrapperParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        wrapperParams.marginEnd = DisplayUtils.convertDpToPixel(EMOJI_END_MARGIN, context).toInt()
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
        val amountParams: LinearLayout.LayoutParams,
        val wrapperParams: LinearLayout.LayoutParams,
        val paddingSide: Int,
        val paddingTop: Int,
        val paddingBottom: Int,
        val viewThemeUtils: ViewThemeUtils,
        val isOutgoingMessage: Boolean,
        val isSelfReaction: Boolean
    )

    companion object {
        const val AMOUNT_START_MARGIN: Float = 2F
        const val EMOJI_END_MARGIN: Float = 6F
        const val EMOJI_AND_AMOUNT_PADDING_SIDE: Float = 4F
        const val WRAPPER_PADDING_TOP: Float = 2F
        const val WRAPPER_PADDING_BOTTOM: Float = 3F
    }
}
