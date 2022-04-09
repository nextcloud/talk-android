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
import android.widget.RelativeLayout
import android.widget.TextView
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
        useLightColorForText: Boolean
    ) {
        binding.reactionsEmojiWrapper.removeAllViews()
        if (message.reactions != null && message.reactions.isNotEmpty()) {

            var remainingEmojisToDisplay = MAX_EMOJIS_TO_DISPLAY
            val showInfoAboutMoreEmojis = message.reactions.size > MAX_EMOJIS_TO_DISPLAY
            for ((emoji, amount) in message.reactions) {
                val reactionEmoji = EmojiTextView(context)
                reactionEmoji.text = emoji
                binding.reactionsEmojiWrapper.addView(reactionEmoji)

                val reactionAmount = TextView(context)

                if (amount > 1) {
                    if (useLightColorForText) {
                        reactionAmount.setTextColor(ContextCompat.getColor(context, R.color.nc_grey))
                    }
                    reactionAmount.text = amount.toString()
                }

                val params = RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(
                    DisplayUtils.convertDpToPixel(EMOJI_START_MARGIN, context).toInt(),
                    0,
                    DisplayUtils.convertDpToPixel(EMOJI_END_MARGIN, context).toInt(),
                    0
                )
                reactionAmount.layoutParams = params
                binding.reactionsEmojiWrapper.addView(reactionAmount)

                remainingEmojisToDisplay--
                if (remainingEmojisToDisplay == 0 && showInfoAboutMoreEmojis) {
                    val infoAboutMoreEmojis = TextView(context)
                    infoAboutMoreEmojis.text = EMOJI_MORE
                    binding.reactionsEmojiWrapper.addView(infoAboutMoreEmojis)
                    break
                }
            }
        }
    }

    companion object {
        const val MAX_EMOJIS_TO_DISPLAY = 4
        const val EMOJI_START_MARGIN: Float = 2F
        const val EMOJI_END_MARGIN: Float = 8F
        const val EMOJI_MORE = "…"
    }
}
