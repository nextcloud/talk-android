package com.nextcloud.talk.adapters.messages

import android.content.Context
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import com.nextcloud.talk.databinding.ReactionsInsideMessageBinding
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.utils.DisplayUtils
import com.vanniktech.emoji.EmojiTextView

class Reaction {
    fun showReactions(message: ChatMessage, binding: ReactionsInsideMessageBinding, context: Context) {
        binding.reactionsEmojiWrapper.removeAllViews()
        if (message.reactions != null && message.reactions.isNotEmpty()) {

            var remainingEmojisToDisplay = MAX_EMOJIS_TO_DISPLAY
            val showInfoAboutMoreEmojis = message.reactions.size > MAX_EMOJIS_TO_DISPLAY
            for ((emoji, amount) in message.reactions) {
                val reactionEmoji = EmojiTextView(context)
                reactionEmoji.text = emoji

                val reactionAmount = TextView(context)
                reactionAmount.text = amount.toString()

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

                binding.reactionsEmojiWrapper.addView(reactionEmoji)
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
        const val EMOJI_MORE = "..."
    }
}