/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
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
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.text.Spannable
import android.text.SpannableString
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.emoji.widget.EmojiTextView
import autodagger.AutoInjector
import butterknife.BindView
import butterknife.ButterKnife
import coil.api.load
import coil.transform.CircleCropTransformation
import com.amulyakhare.textdrawable.TextDrawable
import com.facebook.drawee.view.SimpleDraweeView
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.TextMatchers
import com.nextcloud.talk.utils.preferences.AppPreferences
import com.stfalcon.chatkit.messages.MessageHolders
import com.stfalcon.chatkit.utils.DateFormatter
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class MagicIncomingTextMessageViewHolder(incomingView: View) : MessageHolders
.IncomingTextMessageViewHolder<ChatMessage>(incomingView) {

    @JvmField
    @BindView(R.id.messageAuthor)
    var messageAuthor: EmojiTextView? = null

    @JvmField
    @BindView(R.id.messageText)
    var messageText: EmojiTextView? = null

    @JvmField
    @BindView(R.id.messageUserAvatar)
    var messageUserAvatarView: SimpleDraweeView? = null

    @JvmField
    @BindView(R.id.messageTime)
    var messageTimeView: TextView? = null

    @JvmField
    @BindView(R.id.quotedChatMessageView)
    var quotedChatMessageView: RelativeLayout? = null

    @JvmField
    @BindView(R.id.quotedUserAvatar)
    var quotedUserAvatar: ImageView? = null

    @JvmField
    @BindView(R.id.quotedMessageAuthor)
    var quotedUserName: EmojiTextView? = null

    @JvmField
    @BindView(R.id.quotedMessageImage)
    var quotedMessagePreview: ImageView? = null

    @JvmField
    @BindView(R.id.quotedMessage)
    var quotedMessage: EmojiTextView? = null

    @JvmField
    @BindView(R.id.quotedMessageTime)
    var quotedMessageTime: TextView? = null

    @JvmField
    @BindView(R.id.quoteColoredView)
    var quoteColoredView: View? = null

    @JvmField
    @Inject
    var context: Context? = null

    @JvmField
    @Inject
    var appPreferences: AppPreferences? = null

    init {
        ButterKnife.bind(
                this,
                itemView
        )
    }

    override fun onBind(message: ChatMessage) {
        super.onBind(message)
        sharedApplication!!.componentApplication.inject(this)
        val author: String = message.actorDisplayName
        if (!TextUtils.isEmpty(author)) {
            messageAuthor!!.text = author
        } else {
            messageAuthor!!.setText(R.string.nc_nick_guest)
        }

        if (!message.isGrouped && !message.isOneToOneConversation) {
            messageUserAvatarView!!.visibility = View.VISIBLE
            if (message.actorType == "guests") {
                // do nothing, avatar is set
            } else if (message.actorType == "bots" && message.actorId == "changelog") {
                val layers = arrayOfNulls<Drawable>(2)
                layers[0] = context?.getDrawable(R.drawable.ic_launcher_background)
                layers[1] = context?.getDrawable(R.drawable.ic_launcher_foreground)
                val layerDrawable = LayerDrawable(layers)
                messageUserAvatarView?.setImageDrawable(DisplayUtils.getRoundedDrawable(layerDrawable))
            } else if (message.actorType == "bots") {
                val drawable = TextDrawable.builder()
                        .beginConfig()
                        .bold()
                        .endConfig()
                        .buildRound(
                                ">",
                                context!!.resources.getColor(R.color.black)
                        )
                messageUserAvatarView!!.visibility = View.VISIBLE
                messageUserAvatarView?.setImageDrawable(drawable)
            }
        } else {
            if (message.isOneToOneConversation) {
                messageUserAvatarView!!.visibility = View.GONE
            } else {
                messageUserAvatarView!!.visibility = View.INVISIBLE
            }
            messageAuthor!!.visibility = View.GONE
        }

        val resources = itemView.resources

        val bg_bubble_color = resources.getColor(R.color.bg_message_list_incoming_bubble)

        var bubbleResource = R.drawable.shape_incoming_message

        if (message.isGrouped) {
            bubbleResource = R.drawable.shape_grouped_incoming_message
        }

        val bubbleDrawable = DisplayUtils.getMessageSelector(
                bg_bubble_color,
                resources.getColor(R.color.transparent),
                bg_bubble_color, bubbleResource
        )
        ViewCompat.setBackground(bubble, bubbleDrawable)

        val messageParameters = message.messageParameters

        itemView.isSelected = false
        messageTimeView!!.setTextColor(context?.resources!!.getColor(R.color.warm_grey_four))

        var messageString: Spannable = SpannableString(message.text)

        var textSize = context?.resources!!.getDimension(R.dimen.chat_text_size)

        if (messageParameters != null && messageParameters.size > 0) {
            for (key in messageParameters.keys) {
                val individualHashMap = message.messageParameters[key]
                if (individualHashMap != null) {
                    if (individualHashMap["type"] == "user" || individualHashMap["type"] == "guest" || individualHashMap["type"] == "call") {
                        if (individualHashMap["id"] == message.activeUser!!.userId) {
                            messageString = DisplayUtils.searchAndReplaceWithMentionSpan(
                                    messageText!!.context,
                                    messageString,
                                    individualHashMap["id"]!!,
                                    individualHashMap["name"]!!,
                                    individualHashMap["type"]!!,
                                    message.activeUser!!,
                                    R.xml.chip_you
                            )
                        } else {
                            messageString = DisplayUtils.searchAndReplaceWithMentionSpan(
                                    messageText!!.context,
                                    messageString,
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
        } else if (TextMatchers.isMessageWithSingleEmoticonOnly(message.text)) {
            textSize = (textSize * 2.5).toFloat()
            itemView.isSelected = true
            messageAuthor!!.visibility = View.GONE
        }

        messageText!!.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)
        messageText!!.text = messageString

        // parent message handling

        message.parentMessage?.let { parentChatMessage ->
            parentChatMessage.activeUser = message.activeUser
            quotedUserAvatar?.load(parentChatMessage.user.avatar) {
                addHeader("Authorization", ApiUtils.getCredentials(message.activeUser.username, message.activeUser.token))
                transformations(CircleCropTransformation())
            }
            parentChatMessage.imageUrl?.let{
                quotedMessagePreview?.visibility = View.VISIBLE
                quotedMessagePreview?.load(it) {
                    addHeader("Authorization", ApiUtils.getCredentials(message.activeUser.username, message.activeUser.token))
                }
            } ?: run {
                quotedMessagePreview?.visibility = View.GONE
            }
            quotedUserName?.text = parentChatMessage.actorDisplayName
                    ?: context!!.getText(R.string.nc_nick_guest)
            quotedMessage?.text = parentChatMessage.text

            quotedUserName?.setTextColor(context!!.resources.getColor(R.color.textColorMaxContrast))

            quotedMessageTime?.text = DateFormatter.format(parentChatMessage.createdAt, DateFormatter.Template.TIME)
            quotedMessageTime?.setTextColor(context!!.resources.getColor(R.color.warm_grey_four))
            quoteColoredView?.setBackgroundResource(R.color.textColorMaxContrast)
            quotedChatMessageView?.visibility = View.VISIBLE
        } ?: run {
            quotedChatMessageView?.visibility = View.GONE
        }
    }
}
