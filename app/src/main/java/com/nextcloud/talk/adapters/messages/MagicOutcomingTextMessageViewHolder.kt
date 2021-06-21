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
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
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
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.DisplayUtils.getMessageSelector
import com.nextcloud.talk.utils.DisplayUtils.searchAndReplaceWithMentionSpan
import com.nextcloud.talk.utils.TextMatchers
import com.stfalcon.chatkit.messages.MessageHolders.OutcomingTextMessageViewHolder
import java.util.HashMap
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class MagicOutcomingTextMessageViewHolder(itemView: View) : OutcomingTextMessageViewHolder<ChatMessage>(itemView) {
    private val binding: ItemCustomOutcomingTextMessageBinding = ItemCustomOutcomingTextMessageBinding.bind(itemView)
    private val realView: View = itemView

    @JvmField
    @Inject
    var context: Context? = null

    override fun onBind(message: ChatMessage) {
        super.onBind(message)
        sharedApplication!!.componentApplication.inject(this)
        val messageParameters: HashMap<String, HashMap<String, String>>? = message.messageParameters
        var messageString: Spannable = SpannableString(message.text)
        realView.isSelected = false
        binding.messageTime.setTextColor(context!!.resources.getColor(R.color.white60))
        val layoutParams = binding.messageTime.layoutParams as FlexboxLayout.LayoutParams
        layoutParams.isWrapBefore = false
        var textSize = context!!.resources.getDimension(R.dimen.chat_text_size)
        if (messageParameters != null && messageParameters.size > 0) {
            for (key in messageParameters.keys) {
                val individualHashMap: HashMap<String, String>? = message.messageParameters[key]
                if (individualHashMap != null) {
                    if (individualHashMap["type"] == "user" ||
                        individualHashMap["type"] == "guest" ||
                        individualHashMap["type"] == "call"
                    ) {
                        messageString = searchAndReplaceWithMentionSpan(
                            binding.messageText.context,
                            messageString,
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
        } else if (TextMatchers.isMessageWithSingleEmoticonOnly(message.text)) {
            textSize = (textSize * 2.5).toFloat()
            layoutParams.isWrapBefore = true
            binding.messageTime.setTextColor(
                ResourcesCompat.getColor(context!!.resources, R.color.warm_grey_four, null)
            )
            realView.isSelected = true
        }
        val resources = sharedApplication!!.resources
        val bgBubbleColor = if (message.isDeleted) {
            ResourcesCompat.getColor(resources, R.color.bg_message_list_outcoming_bubble_deleted, null)
        } else {
            ResourcesCompat.getColor(resources, R.color.bg_message_list_outcoming_bubble, null)
        }
        if (message.isGrouped) {
            val bubbleDrawable = getMessageSelector(
                bgBubbleColor,
                ResourcesCompat.getColor(resources, R.color.transparent, null),
                bgBubbleColor,
                R.drawable.shape_grouped_outcoming_message
            )
            ViewCompat.setBackground(bubble, bubbleDrawable)
        } else {
            val bubbleDrawable = getMessageSelector(
                bgBubbleColor,
                ResourcesCompat.getColor(resources, R.color.transparent, null),
                bgBubbleColor,
                R.drawable.shape_outcoming_message
            )
            ViewCompat.setBackground(bubble, bubbleDrawable)
        }
        binding.messageText.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)
        binding.messageTime.layoutParams = layoutParams
        binding.messageText.text = messageString

        // parent message handling

        if (!message.isDeleted && message.parentMessage != null) {
            val parentChatMessage = message.parentMessage
            parentChatMessage.activeUser = message.activeUser
            parentChatMessage.imageUrl?.let {
                binding.messageQuote.quotedMessageImage.visibility = View.VISIBLE
                binding.messageQuote.quotedMessageImage.load(it) {
                    addHeader(
                        "Authorization",
                        ApiUtils.getCredentials(message.activeUser.username, message.activeUser.token)
                    )
                }
            } ?: run {
                binding.messageQuote.quotedMessageImage.visibility = View.GONE
            }
            binding.messageQuote.quotedMessageAuthor.text = parentChatMessage.actorDisplayName
                ?: context!!.getText(R.string.nc_nick_guest)
            binding.messageQuote.quotedMessage.text = parentChatMessage.text
            binding.messageQuote.quotedMessage.setTextColor(
                ContextCompat.getColor(context!!, R.color.nc_outcoming_text_default)
            )
            binding.messageQuote.quotedMessageAuthor.setTextColor(ContextCompat.getColor(context!!, R.color.nc_grey))

            binding.messageQuote.quoteColoredView.setBackgroundResource(R.color.white)

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
                it.setColorFilter(ContextCompat.getColor(context!!, R.color.white60), PorterDuff.Mode.SRC_ATOP)
                binding.checkMark.setImageDrawable(it)
            }
        }

        binding.checkMark.setContentDescription(readStatusContentDescriptionString)

        itemView.setTag(MessageSwipeCallback.REPLYABLE_VIEW_TAG, message.isReplyable)
    }
}
