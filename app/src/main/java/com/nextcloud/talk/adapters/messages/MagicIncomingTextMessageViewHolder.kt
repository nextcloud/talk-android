/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Andy Scherzinger
 * @author Tim Krüger
 * Copyright (C) 2021 Tim Krüger <t@timkrueger.me>
 * Copyright (C) 2021 Andy Scherzinger <info@andy-scherzinger.de>
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
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Build
import android.text.Spannable
import android.text.SpannableString
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import autodagger.AutoInjector
import coil.load
import com.amulyakhare.textdrawable.TextDrawable
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.databinding.ItemCustomIncomingTextMessageBinding
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.ui.bottom.sheet.ProfileBottomSheet
import com.nextcloud.talk.ui.recyclerview.MessageSwipeCallback
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.TextMatchers
import com.nextcloud.talk.utils.preferences.AppPreferences
import com.stfalcon.chatkit.messages.MessageHolders
import java.util.HashMap
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class MagicIncomingTextMessageViewHolder(itemView: View, payload: Any) : MessageHolders
.IncomingTextMessageViewHolder<ChatMessage>(itemView, payload) {

    private val binding: ItemCustomIncomingTextMessageBinding = ItemCustomIncomingTextMessageBinding.bind(itemView)

    @JvmField
    @Inject
    var context: Context? = null

    @JvmField
    @Inject
    var appPreferences: AppPreferences? = null

    override fun onBind(message: ChatMessage) {
        super.onBind(message)
        sharedApplication!!.componentApplication.inject(this)
        processAuthor(message)

        if (!message.isGrouped && !message.isOneToOneConversation) {
            showAvatarOnChatMessage(message)
        } else {
            if (message.isOneToOneConversation) {
                binding.messageUserAvatar.visibility = View.GONE
            } else {
                binding.messageUserAvatar.visibility = View.INVISIBLE
            }
            binding.messageAuthor.visibility = View.GONE
        }

        val resources = itemView.resources

        setBubbleOnChatMessage(message, resources)

        itemView.isSelected = false
        binding.messageTime.setTextColor(ResourcesCompat.getColor(resources, R.color.warm_grey_four, null))

        var messageString: Spannable = SpannableString(message.text)

        var textSize = context?.resources!!.getDimension(R.dimen.chat_text_size)

        val messageParameters = message.messageParameters
        if (messageParameters != null && messageParameters.size > 0) {
            messageString = processMessageParameters(messageParameters, message, messageString)
        } else if (TextMatchers.isMessageWithSingleEmoticonOnly(message.text)) {
            textSize = (textSize * TEXT_SIZE_MULTIPLIER).toFloat()
            itemView.isSelected = true
            binding.messageAuthor.visibility = View.GONE
        }

        binding.messageText.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)
        binding.messageText.text = messageString

        // parent message handling
        if (!message.isDeleted && message.parentMessage != null) {
            processParentMessage(message)
            binding.messageQuote.quotedChatMessageView.visibility = View.VISIBLE
        } else {
            binding.messageQuote.quotedChatMessageView.visibility = View.GONE
        }

        itemView.setTag(MessageSwipeCallback.REPLYABLE_VIEW_TAG, message.isReplyable)
    }

    private fun processAuthor(message: ChatMessage) {
        if (!TextUtils.isEmpty(message.actorDisplayName)) {
            binding.messageAuthor.text = message.actorDisplayName
            binding.messageUserAvatar.setOnClickListener {
                (payload as? ProfileBottomSheet)?.showFor(message.actorId, itemView.context)
            }
        } else {
            binding.messageAuthor.setText(R.string.nc_nick_guest)
        }
    }

    private fun setBubbleOnChatMessage(
        message: ChatMessage,
        resources: Resources
    ) {
        val bgBubbleColor = if (message.isDeleted) {
            ResourcesCompat.getColor(resources, R.color.bg_message_list_incoming_bubble_deleted, null)
        } else {
            ResourcesCompat.getColor(resources, R.color.bg_message_list_incoming_bubble, null)
        }

        var bubbleResource = R.drawable.shape_incoming_message

        if (message.isGrouped) {
            bubbleResource = R.drawable.shape_grouped_incoming_message
        }

        val bubbleDrawable = DisplayUtils.getMessageSelector(
            bgBubbleColor,
            ResourcesCompat.getColor(resources, R.color.transparent, null),
            bgBubbleColor, bubbleResource
        )
        ViewCompat.setBackground(bubble, bubbleDrawable)
    }

    private fun processParentMessage(message: ChatMessage) {
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
        binding.messageQuote.quotedMessageAuthor.text = if (parentChatMessage.actorDisplayName.isNullOrEmpty())
            context!!.getText(R.string.nc_nick_guest) else parentChatMessage.actorDisplayName
        binding.messageQuote.quotedMessage.text = parentChatMessage.text

        binding.messageQuote.quotedMessageAuthor
            .setTextColor(ContextCompat.getColor(context!!, R.color.textColorMaxContrast))

        if (parentChatMessage.actorId?.equals(message.activeUser.userId) == true) {
            binding.messageQuote.quoteColoredView.setBackgroundResource(R.color.colorPrimary)
        } else {
            binding.messageQuote.quoteColoredView.setBackgroundResource(R.color.textColorMaxContrast)
        }
    }

    private fun showAvatarOnChatMessage(message: ChatMessage) {
        binding.messageUserAvatar.visibility = View.VISIBLE
        if (message.actorType == "guests") {
            // do nothing, avatar is set
        } else if (message.actorType == "bots" && message.actorId == "changelog") {
            if (context != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val layers = arrayOfNulls<Drawable>(2)
                    layers[0] = ContextCompat.getDrawable(context!!, R.drawable.ic_launcher_background)
                    layers[1] = ContextCompat.getDrawable(context!!, R.drawable.ic_launcher_foreground)
                    val layerDrawable = LayerDrawable(layers)
                    binding.messageUserAvatar.setImageDrawable(DisplayUtils.getRoundedDrawable(layerDrawable))
                } else {
                    binding.messageUserAvatar.setImageResource(R.mipmap.ic_launcher)
                }
            }
        } else if (message.actorType == "bots") {
            val drawable = TextDrawable.builder()
                .beginConfig()
                .bold()
                .endConfig()
                .buildRound(
                    ">",
                    ResourcesCompat.getColor(context!!.resources, R.color.black, null)
                )
            binding.messageUserAvatar.visibility = View.VISIBLE
            binding.messageUserAvatar.setImageDrawable(drawable)
        }
    }

    private fun processMessageParameters(
        messageParameters: HashMap<String, HashMap<String, String>>,
        message: ChatMessage,
        messageString: Spannable
    ): Spannable {
        var messageStringInternal = messageString
        for (key in messageParameters.keys) {
            val individualHashMap = message.messageParameters[key]
            if (individualHashMap != null) {
                if (
                    individualHashMap["type"] == "user" ||
                    individualHashMap["type"] == "guest" ||
                    individualHashMap["type"] == "call"
                ) {
                    if (individualHashMap["id"] == message.activeUser!!.userId) {
                        messageStringInternal = DisplayUtils.searchAndReplaceWithMentionSpan(
                            binding.messageText.context,
                            messageStringInternal,
                            individualHashMap["id"]!!,
                            individualHashMap["name"]!!,
                            individualHashMap["type"]!!,
                            message.activeUser!!,
                            R.xml.chip_you
                        )
                    } else {
                        messageStringInternal = DisplayUtils.searchAndReplaceWithMentionSpan(
                            binding.messageText.context,
                            messageStringInternal,
                            individualHashMap["id"]!!,
                            individualHashMap["name"]!!,
                            individualHashMap["type"]!!,
                            message.activeUser!!,
                            R.xml.chip_others
                        )
                    }
                } else if (individualHashMap["type"] == "file") {
                    itemView.setOnClickListener { v ->
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(individualHashMap["link"]))
                        context!!.startActivity(browserIntent)
                    }
                }
            }
        }
        return messageStringInternal
    }

    companion object {
        const val TEXT_SIZE_MULTIPLIER = 2.5
    }
}
