/*
 * Nextcloud Talk application
 *
 * @author Marcel Hibbe
 * Copyright (C) 2022 Marcel Hibbe (dev@mhibbe.de)
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
 * Parts related to account import were either copied from or inspired by the great work done by David Luhmer at:
 * https://github.com/nextcloud/ownCloud-Account-Importer
 */

package com.nextcloud.talk.adapters.messages

import android.content.Context
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.nextcloud.talk.R
import com.nextcloud.talk.databinding.ReactionsInsideMessageBinding
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.DisplayUtils
import com.vanniktech.emoji.EmojiTextView

class Reaction {

    fun showReactions(
        message: ChatMessage,
        binding: ReactionsInsideMessageBinding,
        context: Context,
        isOutgoingMessage: Boolean,
        viewThemeUtils: ViewThemeUtils
    ) {
        binding.reactionsEmojiWrapper.removeAllViews()
        if (message.reactions != null && message.reactions!!.isNotEmpty()) {

            var remainingEmojisToDisplay = MAX_EMOJIS_TO_DISPLAY
            val showInfoAboutMoreEmojis = message.reactions!!.size > MAX_EMOJIS_TO_DISPLAY

            val amountParams = getAmountLayoutParams(context)
            val wrapperParams = getWrapperLayoutParams(context)

            val paddingSide = DisplayUtils.convertDpToPixel(EMOJI_AND_AMOUNT_PADDING_SIDE, context).toInt()
            val paddingTop = DisplayUtils.convertDpToPixel(WRAPPER_PADDING_TOP, context).toInt()
            val paddingBottom = DisplayUtils.convertDpToPixel(WRAPPER_PADDING_BOTTOM, context).toInt()

            for ((emoji, amount) in message.reactions!!) {
                val isSelfReaction = message.reactionsSelf != null &&
                    message.reactionsSelf!!.isNotEmpty() &&
                    message.reactionsSelf!!.contains(emoji)
                val textColor = getTextColor(isOutgoingMessage, isSelfReaction, binding, viewThemeUtils)
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
                )

                binding.reactionsEmojiWrapper.addView(emojiWithAmountWrapper)

                remainingEmojisToDisplay--
                if (remainingEmojisToDisplay == 0 && showInfoAboutMoreEmojis) {
                    binding.reactionsEmojiWrapper.addView(getMoreReactionsTextView(context, textColor))
                    break
                }
            }
        }
    }

    private fun getEmojiWithAmountWrapperLayout(
        context: Context,
        emoji: String,
        amount: Int,
        layoutInfo: EmojiWithAmountWrapperLayoutInfo
    ): LinearLayout {
        val emojiWithAmountWrapper = LinearLayout(context)
        emojiWithAmountWrapper.orientation = LinearLayout.HORIZONTAL

        emojiWithAmountWrapper.addView(getEmojiTextView(context, emoji))
        emojiWithAmountWrapper.addView(getReactionCount(context, layoutInfo.textColor, amount, layoutInfo.amountParams))
        emojiWithAmountWrapper.layoutParams = layoutInfo.wrapperParams

        if (layoutInfo.isSelfReaction) {
            val color = if (layoutInfo.isOutgoingMessage) {
                ContextCompat.getColor(
                    emojiWithAmountWrapper.context,
                    R.color.bg_message_list_incoming_bubble
                )
            } else {
                layoutInfo.viewThemeUtils.getScheme(emojiWithAmountWrapper.context).primaryContainer
            }
            layoutInfo.viewThemeUtils.setCheckedBackground(emojiWithAmountWrapper, color)

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

    private fun getMoreReactionsTextView(context: Context, textColor: Int): TextView {
        val infoAboutMoreEmojis = TextView(context)
        infoAboutMoreEmojis.setTextColor(textColor)
        infoAboutMoreEmojis.text = EMOJI_MORE
        return infoAboutMoreEmojis
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

    private fun getTextColor(
        isOutgoingMessage: Boolean,
        isSelfReaction: Boolean,
        binding: ReactionsInsideMessageBinding,
        viewThemeUtils: ViewThemeUtils
    ): Int {
        var textColor = viewThemeUtils.getScheme(binding.root.context).onSurfaceVariant
        if (!isOutgoingMessage || isSelfReaction) {
            textColor = ContextCompat.getColor(binding.root.context, R.color.high_emphasis_text)
        }
        return textColor
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
        const val MAX_EMOJIS_TO_DISPLAY = 4
        const val AMOUNT_START_MARGIN: Float = 2F
        const val EMOJI_END_MARGIN: Float = 6F
        const val EMOJI_AND_AMOUNT_PADDING_SIDE: Float = 4F
        const val WRAPPER_PADDING_TOP: Float = 2F
        const val WRAPPER_PADDING_BOTTOM: Float = 3F
        const val EMOJI_MORE = "…"
    }
}
