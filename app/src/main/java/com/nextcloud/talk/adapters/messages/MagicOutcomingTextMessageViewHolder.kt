/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Marcel Hibbe
 * @author Andy Scherzinger
 * Copyright (C) 2021 Andy Scherzinger <info@andy-scherzinger.de>
 * Copyright (C) 2021 Marcel Hibbe <dev@mhibbe.de>
 * Copyright (C) 2017-2018 Mario Danic <mario@lovelyhq.com>
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
 */
package com.nextcloud.talk.adapters.messages

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.Spannable
import android.text.SpannableString
import android.util.TypedValue
import android.view.View
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import autodagger.AutoInjector
import coil.load
import com.google.android.flexbox.FlexboxLayout
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.databinding.ItemCustomOutcomingTextMessageBinding
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.models.json.chat.ReadStatus
import com.nextcloud.talk.ui.recyclerview.MessageSwipeCallback
import com.nextcloud.talk.ui.theme.ServerTheme
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.DisplayUtils.searchAndReplaceWithMentionSpan
import com.nextcloud.talk.utils.TextMatchers
import com.stfalcon.chatkit.messages.MessageHolders.OutcomingTextMessageViewHolder
import javax.inject.Inject
import kotlin.math.roundToInt

@AutoInjector(NextcloudTalkApplication::class)
class MagicOutcomingTextMessageViewHolder(itemView: View) : OutcomingTextMessageViewHolder<ChatMessage>(itemView) {
    private val binding: ItemCustomOutcomingTextMessageBinding = ItemCustomOutcomingTextMessageBinding.bind(itemView)
    private val realView: View = itemView

    @Inject
    lateinit var context: Context

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    @Inject
    lateinit var serverTheme: ServerTheme

    lateinit var reactionsInterface: ReactionsInterface

    override fun onBind(message: ChatMessage) {
        super.onBind(message)
        sharedApplication!!.componentApplication.inject(this)
        val messageParameters: HashMap<String?, HashMap<String?, String?>>? = message.messageParameters
        var messageString: Spannable = SpannableString(message.text)
        realView.isSelected = false
        val layoutParams = binding.messageTime.layoutParams as FlexboxLayout.LayoutParams
        layoutParams.isWrapBefore = false
        var textSize = context!!.resources.getDimension(R.dimen.chat_text_size)
        if (messageParameters != null && messageParameters.size > 0) {
            messageString = processMessageParameters(messageParameters, message, messageString)
        } else if (TextMatchers.isMessageWithSingleEmoticonOnly(message.text)) {
            textSize = (textSize * TEXT_SIZE_MULTIPLIER).toFloat()
            layoutParams.isWrapBefore = true
            binding.messageTime.setTextColor(
                ResourcesCompat.getColor(context!!.resources, R.color.warm_grey_four, null)
            )
            realView.isSelected = true
        }

        setBubbleOnChatMessage(message)

        binding.messageText.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)
        binding.messageTime.layoutParams = layoutParams
        binding.messageText.text = messageString

        // parent message handling
        if (!message.isDeleted && message.parentMessage != null) {
            processParentMessage(message)
            binding.messageQuote.quotedChatMessageView.visibility = View.VISIBLE
        } else {
            binding.messageQuote.quotedChatMessageView.visibility = View.GONE
        }

        val readStatusDrawableInt = when (message.readStatus) {
            ReadStatus.READ -> R.drawable.ic_check_all
            ReadStatus.SENT -> R.drawable.ic_check
            else -> null
        }

        val readStatusContentDescriptionString = when (message.readStatus) {
            ReadStatus.READ -> context?.resources?.getString(R.string.nc_message_read)
            ReadStatus.SENT -> context?.resources?.getString(R.string.nc_message_sent)
            else -> null
        }

        readStatusDrawableInt?.let { drawableInt ->
            ResourcesCompat.getDrawable(context!!.resources, drawableInt, null)?.let {
                binding.checkMark.setImageDrawable(it)
            }
        }

        binding.checkMark.setContentDescription(readStatusContentDescriptionString)

        itemView.setTag(MessageSwipeCallback.REPLYABLE_VIEW_TAG, message.replyable)

        Reaction().showReactions(message, binding.reactions, context!!, true)
        binding.reactions.reactionsEmojiWrapper.setOnClickListener {
            reactionsInterface.onClickReactions(message)
        }
        binding.reactions.reactionsEmojiWrapper.setOnLongClickListener { l: View? ->
            reactionsInterface.onLongClickReactions(message)
            true
        }
    }

    private fun processParentMessage(message: ChatMessage) {
        val parentChatMessage = message.parentMessage
        parentChatMessage!!.activeUser = message.activeUser
        parentChatMessage.imageUrl?.let {
            binding.messageQuote.quotedMessageImage.visibility = View.VISIBLE
            binding.messageQuote.quotedMessageImage.load(it) {
                addHeader(
                    "Authorization",
                    ApiUtils.getCredentials(message.activeUser!!.username, message.activeUser!!.token)
                )
            }
        } ?: run {
            binding.messageQuote.quotedMessageImage.visibility = View.GONE
        }
        binding.messageQuote.quotedMessageAuthor.text = parentChatMessage.actorDisplayName
            ?: context!!.getText(R.string.nc_nick_guest)
        binding.messageQuote.quotedMessage.text = parentChatMessage.text
        binding.messageQuote.quotedMessage.setTextColor(serverTheme.colorText)

        binding.messageQuote.quotedMessageAuthor.setTextColor(
            ColorUtils.setAlphaComponent(
                serverTheme.colorText,
                ALPHA_80_INT
            )
        )

        binding.messageQuote.quoteColoredView.setBackgroundColor(serverTheme.colorText)
    }

    private fun setBubbleOnChatMessage(message: ChatMessage) {
        viewThemeUtils.themeOutgoingMessageBubble(bubble, message.isGrouped, message.isDeleted)
    }

    private fun processMessageParameters(
        messageParameters: HashMap<String?, HashMap<String?, String?>>,
        message: ChatMessage,
        messageString: Spannable
    ): Spannable {
        var messageString1 = messageString
        for (key in messageParameters.keys) {
            val individualHashMap: HashMap<String?, String?>? = message.messageParameters!![key]
            if (individualHashMap != null) {
                if (individualHashMap["type"] == "user" ||
                    individualHashMap["type"] == "guest" ||
                    individualHashMap["type"] == "call"
                ) {
                    messageString1 = searchAndReplaceWithMentionSpan(
                        binding.messageText.context,
                        messageString1,
                        individualHashMap["id"]!!,
                        individualHashMap["name"]!!,
                        individualHashMap["type"]!!,
                        message.activeUser,
                        R.xml.chip_others
                    )
                } else if (individualHashMap["type"] == "file") {
                    realView.setOnClickListener { v: View? ->
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(individualHashMap["link"]))
                        context!!.startActivity(browserIntent)
                    }
                }
            }
        }
        return messageString1
    }

    fun assignReactionInterface(reactionsInterface: ReactionsInterface) {
        this.reactionsInterface = reactionsInterface
    }

    companion object {
        const val TEXT_SIZE_MULTIPLIER = 2.5
        private const val HALF_ALPHA_INT: Int = 255 / 2
        private val ALPHA_60_INT: Int = (255 * 0.6).roundToInt()
        private val ALPHA_80_INT: Int = (255 * 0.8).roundToInt()
    }
}
