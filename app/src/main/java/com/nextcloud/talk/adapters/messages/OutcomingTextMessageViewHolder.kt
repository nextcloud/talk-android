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
import android.graphics.PorterDuff
import android.net.Uri
import android.text.Spannable
import android.text.SpannableString
import android.util.TypedValue
import android.view.View
import androidx.core.content.res.ResourcesCompat
import autodagger.AutoInjector
import coil.load
import com.google.android.flexbox.FlexboxLayout
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.chat.ChatActivity
import com.nextcloud.talk.databinding.ItemCustomOutcomingTextMessageBinding
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.models.json.chat.ReadStatus
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.DateUtils
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.TextMatchers
import com.stfalcon.chatkit.messages.MessageHolders.OutcomingTextMessageViewHolder
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class OutcomingTextMessageViewHolder(itemView: View) : OutcomingTextMessageViewHolder<ChatMessage>(itemView) {
    private val binding: ItemCustomOutcomingTextMessageBinding = ItemCustomOutcomingTextMessageBinding.bind(itemView)
    private val realView: View = itemView

    @Inject
    lateinit var context: Context

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    @Inject
    lateinit var dateUtils: DateUtils

    lateinit var commonMessageInterface: CommonMessageInterface

    override fun onBind(message: ChatMessage) {
        super.onBind(message)
        sharedApplication!!.componentApplication.inject(this)
        val messageParameters: HashMap<String?, HashMap<String?, String?>>? = message.messageParameters
        var messageString: Spannable = SpannableString(message.message)
        realView.isSelected = false
        val layoutParams = binding.messageTime.layoutParams as FlexboxLayout.LayoutParams
        layoutParams.isWrapBefore = false
        var textSize = context!!.resources.getDimension(R.dimen.chat_text_size)
        val textColor = viewThemeUtils.getScheme(binding.messageText.context).onSurfaceVariant
        binding.messageTime.setTextColor(textColor)
        if (messageParameters != null && messageParameters.size > 0) {
            messageString = processMessageParameters(messageParameters, message, messageString)
        } else if (TextMatchers.isMessageWithSingleEmoticonOnly(message.text)) {
            textSize = (textSize * TEXT_SIZE_MULTIPLIER).toFloat()
            layoutParams.isWrapBefore = true
            realView.isSelected = true
        }

        setBubbleOnChatMessage(message)

        binding.messageText.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)
        binding.messageTime.layoutParams = layoutParams
        binding.messageText.setTextColor(textColor)
        binding.messageText.text = messageString

        binding.messageTime.text = dateUtils.getLocalTimeStringFromTimestamp(message.timestamp)

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
                binding.checkMark.setColorFilter(
                    viewThemeUtils.getScheme(binding.messageText.context).onSurfaceVariant,
                    PorterDuff.Mode.SRC_ATOP
                )
            }
        }

        binding.checkMark.setContentDescription(readStatusContentDescriptionString)

        itemView.setTag(R.string.replyable_message_view_tag, message.replyable)

        Reaction().showReactions(
            message,
            ::clickOnReaction,
            ::longClickOnReaction,
            binding.reactions,
            context,
            true,
            viewThemeUtils
        )
    }

    private fun longClickOnReaction(chatMessage: ChatMessage) {
        commonMessageInterface.onLongClickReactions(chatMessage)
    }

    private fun clickOnReaction(chatMessage: ChatMessage, emoji: String) {
        commonMessageInterface.onClickReaction(chatMessage, emoji)
    }

    private fun processParentMessage(message: ChatMessage) {
        val parentChatMessage = message.parentMessage
        val textColor = viewThemeUtils.getScheme(binding.messageQuote.quotedMessage.context).onSurfaceVariant
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

        binding.messageQuote.quotedMessageAuthor.setTextColor(textColor)
        binding.messageQuote.quotedMessage.setTextColor(textColor)
        binding.messageQuote.quoteColoredView.setBackgroundColor(textColor)

        binding.messageQuote.quotedChatMessageView.setOnClickListener() {
            val chatActivity = commonMessageInterface as ChatActivity
            chatActivity.jumpToQuotedMessage(parentChatMessage)
        }
    }

    private fun setBubbleOnChatMessage(message: ChatMessage) {
        viewThemeUtils.talk.themeOutgoingMessageBubble(bubble, message.isGrouped, message.isDeleted)
    }

    private fun processMessageParameters(
        messageParameters: HashMap<String?, HashMap<String?, String?>>,
        message: ChatMessage,
        messageString: Spannable
    ): Spannable {
        var messageStringInternal = messageString
        for (key in messageParameters.keys) {
            val individualHashMap: HashMap<String?, String?>? = message.messageParameters!![key]
            if (individualHashMap != null) {
                when (individualHashMap["type"]) {
                    "user", "guest", "call", "user-group" -> {
                        val chip = if (individualHashMap["id"] == message.activeUser!!.userId) {
                            R.xml.chip_you
                        } else {
                            R.xml.chip_others
                        }
                        messageStringInternal = DisplayUtils.searchAndReplaceWithMentionSpan(
                            key,
                            binding.messageText.context,
                            messageStringInternal,
                            individualHashMap["id"]!!,
                            individualHashMap["name"]!!,
                            individualHashMap["type"]!!,
                            message.activeUser!!,
                            chip,
                            viewThemeUtils
                        )
                    }
                    "file" -> {
                        itemView.setOnClickListener { v ->
                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(individualHashMap["link"]))
                            context.startActivity(browserIntent)
                        }
                    }
                }
            }
        }
        return messageStringInternal
    }

    fun assignCommonMessageInterface(commonMessageInterface: CommonMessageInterface) {
        this.commonMessageInterface = commonMessageInterface
    }

    companion object {
        const val TEXT_SIZE_MULTIPLIER = 2.5
    }
}
