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
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import com.nextcloud.talk.R
import com.nextcloud.talk.databinding.ReactionsInsideMessageBinding
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.utils.DisplayUtils
import com.vanniktech.emoji.EmojiTextView

class Reaction {

    fun showReactions(
        message: ChatMessage,
        binding: ReactionsInsideMessageBinding,
        context: Context,
        isOutgoingMessage: Boolean
    ) {
        binding.reactionsEmojiWrapper.removeAllViews()
        if (message.reactions != null && message.reactions.isNotEmpty()) {

            var remainingEmojisToDisplay = MAX_EMOJIS_TO_DISPLAY
            val showInfoAboutMoreEmojis = message.reactions.size > MAX_EMOJIS_TO_DISPLAY

            var textColor = ContextCompat.getColor(context, R.color.white)
            if (!isOutgoingMessage) {
                textColor = ContextCompat.getColor(binding.root.context, R.color.high_emphasis_text)
            }

            val amountParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            amountParams.marginStart = DisplayUtils.convertDpToPixel(AMOUNT_START_MARGIN, context).toInt()

            val wrapperParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            wrapperParams.marginEnd = DisplayUtils.convertDpToPixel(EMOJI_END_MARGIN, context).toInt()

            for ((emoji, amount) in message.reactions) {
                val emojiWithAmountWrapper = LinearLayout(context)
                emojiWithAmountWrapper.orientation = LinearLayout.HORIZONTAL

                val reactionEmoji = EmojiTextView(context)
                reactionEmoji.text = emoji

                emojiWithAmountWrapper.addView(reactionEmoji)

                val reactionAmount = TextView(context)
                reactionAmount.setTextColor(textColor)
                reactionAmount.text = amount.toString()
                reactionAmount.layoutParams = amountParams
                emojiWithAmountWrapper.addView(reactionAmount)

                emojiWithAmountWrapper.layoutParams = wrapperParams

                val paddingSide = DisplayUtils.convertDpToPixel(EMOJI_AND_AMOUNT_PADDING_SIDE, context).toInt()
                val paddingTop = DisplayUtils.convertDpToPixel(WRAPPER_PADDING_TOP, context).toInt()
                val paddingBottom = DisplayUtils.convertDpToPixel(WRAPPER_PADDING_BOTTOM, context).toInt()
                if (message.reactionsSelf != null &&
                    message.reactionsSelf.isNotEmpty() &&
                    message.reactionsSelf.contains(emoji)
                ) {
                    emojiWithAmountWrapper.background =
                        AppCompatResources.getDrawable(context, R.drawable.reaction_self_background)
                    emojiWithAmountWrapper.setPaddingRelative(paddingSide, paddingTop, paddingSide, paddingBottom)
                } else {
                    emojiWithAmountWrapper.setPaddingRelative(0, paddingTop, paddingSide, paddingBottom)
                }

                binding.reactionsEmojiWrapper.addView(emojiWithAmountWrapper)

                remainingEmojisToDisplay--
                if (remainingEmojisToDisplay == 0 && showInfoAboutMoreEmojis) {
                    val infoAboutMoreEmojis = TextView(context)
                    infoAboutMoreEmojis.setTextColor(textColor)
                    infoAboutMoreEmojis.text = EMOJI_MORE
                    binding.reactionsEmojiWrapper.addView(infoAboutMoreEmojis)
                    break
                }
            }
        }
    }

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
