/*
 * Nextcloud Talk application
 *
 * @author Marcel Hibbe
 * Copyright (C) 2021 Marcel Hibbe <dev@mhibbe.de>
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

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.text.TextUtils
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import autodagger.AutoInjector
import coil.load
import com.amulyakhare.textdrawable.TextDrawable
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.MainActivity
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.databinding.ItemCustomIncomingPollMessageBinding
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.polls.ui.PollVoteDialogFragment
import com.nextcloud.talk.ui.bottom.sheet.ProfileBottomSheet
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.preferences.AppPreferences
import com.stfalcon.chatkit.messages.MessageHolders
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class IncomingPollMessageViewHolder(incomingView: View, payload: Any) : MessageHolders
.IncomingTextMessageViewHolder<ChatMessage>(incomingView, payload) {

    private val binding: ItemCustomIncomingPollMessageBinding =
        ItemCustomIncomingPollMessageBinding.bind(itemView)

    @JvmField
    @Inject
    var context: Context? = null

    @JvmField
    @Inject
    var appPreferences: AppPreferences? = null

    @Inject
    @JvmField
    var ncApi: NcApi? = null

    lateinit var message: ChatMessage

    lateinit var reactionsInterface: ReactionsInterface

    @SuppressLint("SetTextI18n")
    override fun onBind(message: ChatMessage) {
        super.onBind(message)
        this.message = message
        sharedApplication!!.componentApplication.inject(this)

        setAvatarAndAuthorOnMessageItem(message)

        colorizeMessageBubble(message)

        itemView.isSelected = false
        binding.messageTime.setTextColor(ResourcesCompat.getColor(context?.resources!!, R.color.warm_grey_four, null))

        // parent message handling
        setParentMessageDataOnMessageItem(message)

        setPollPreview(message)

        Reaction().showReactions(message, binding.reactions, binding.messageTime.context, false)
        binding.reactions.reactionsEmojiWrapper.setOnClickListener {
            reactionsInterface.onClickReactions(message)
        }
        binding.reactions.reactionsEmojiWrapper.setOnLongClickListener { l: View? ->
            reactionsInterface.onLongClickReactions(message)
            true
        }
    }

    private fun setPollPreview(message: ChatMessage) {
        var pollId: Int? = null
        var pollName: String? = null

        if (message.messageParameters != null && message.messageParameters!!.size > 0) {
            for (key in message.messageParameters!!.keys) {
                val individualHashMap: Map<String?, String?> = message.messageParameters!![key]!!
                if (individualHashMap["type"] == "talk-poll") {
                    pollId = Integer.parseInt(individualHashMap["id"])
                    pollName = individualHashMap["name"].toString()
                }
            }
        }

        if (pollId != null && pollName != null) {
            binding.messagePollTitle.text = pollName

            // TODO: how to get room token here?
            val roomToken = "???????????????????????????"

            binding.bubble.setOnClickListener {
                val pollVoteDialog = PollVoteDialogFragment.newInstance(
                    message.activeUser!!, roomToken, pollId,
                    pollName
                )
                pollVoteDialog.show(
                    (binding.messagePollIcon.context as MainActivity).supportFragmentManager,
                    TAG
                )
            }

            //  wait for https://github.com/nextcloud/spreed/pull/7306#issuecomment-1145819317

            // val credentials = ApiUtils.getCredentials(message.activeUser?.username, message.activeUser?.token)
            // ncApi!!.getPoll(
            //     credentials,
            //     ApiUtils.getUrlForPoll(
            //         message.activeUser?.baseUrl,
            //         ???????
            //     )
            // )
        }
    }

    private fun setAvatarAndAuthorOnMessageItem(message: ChatMessage) {
        val author: String = message.actorDisplayName!!
        if (!TextUtils.isEmpty(author)) {
            binding.messageAuthor.text = author
            binding.messageUserAvatar.setOnClickListener {
                (payload as? ProfileBottomSheet)?.showFor(message.actorId!!, itemView.context)
            }
        } else {
            binding.messageAuthor.setText(R.string.nc_nick_guest)
        }

        if (!message.isGrouped && !message.isOneToOneConversation) {
            setAvatarOnMessage(message)
        } else {
            if (message.isOneToOneConversation) {
                binding.messageUserAvatar.visibility = View.GONE
            } else {
                binding.messageUserAvatar.visibility = View.INVISIBLE
            }
            binding.messageAuthor.visibility = View.GONE
        }
    }

    private fun setAvatarOnMessage(message: ChatMessage) {
        binding.messageUserAvatar.visibility = View.VISIBLE
        if (message.actorType == "guests") {
            // do nothing, avatar is set
        } else if (message.actorType == "bots" && message.actorId == "changelog") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val layers = arrayOfNulls<Drawable>(2)
                layers[0] = ContextCompat.getDrawable(context!!, R.drawable.ic_launcher_background)
                layers[1] = ContextCompat.getDrawable(context!!, R.drawable.ic_launcher_foreground)
                val layerDrawable = LayerDrawable(layers)
                binding.messageUserAvatar.setImageDrawable(DisplayUtils.getRoundedDrawable(layerDrawable))
            } else {
                binding.messageUserAvatar.setImageResource(R.mipmap.ic_launcher)
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

    private fun colorizeMessageBubble(message: ChatMessage) {
        val resources = itemView.resources

        var bubbleResource = R.drawable.shape_incoming_message

        if (message.isGrouped) {
            bubbleResource = R.drawable.shape_grouped_incoming_message
        }

        val bgBubbleColor = if (message.isDeleted) {
            ResourcesCompat.getColor(resources, R.color.bg_message_list_incoming_bubble_deleted, null)
        } else {
            ResourcesCompat.getColor(resources, R.color.bg_message_list_incoming_bubble, null)
        }
        val bubbleDrawable = DisplayUtils.getMessageSelector(
            bgBubbleColor,
            ResourcesCompat.getColor(resources, R.color.transparent, null),
            bgBubbleColor, bubbleResource
        )
        ViewCompat.setBackground(bubble, bubbleDrawable)
    }

    private fun setParentMessageDataOnMessageItem(message: ChatMessage) {
        if (!message.isDeleted && message.parentMessage != null) {
            val parentChatMessage = message.parentMessage
            parentChatMessage!!.activeUser = message.activeUser
            parentChatMessage!!.imageUrl?.let {
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

            binding.messageQuote.quotedMessageAuthor
                .setTextColor(ContextCompat.getColor(context!!, R.color.textColorMaxContrast))

            if (parentChatMessage.actorId?.equals(message.activeUser!!.userId) == true) {
                binding.messageQuote.quoteColoredView.setBackgroundResource(R.color.colorPrimary)
            } else {
                binding.messageQuote.quoteColoredView.setBackgroundResource(R.color.textColorMaxContrast)
            }

            binding.messageQuote.quotedChatMessageView.visibility = View.VISIBLE
        } else {
            binding.messageQuote.quotedChatMessageView.visibility = View.GONE
        }
    }

    fun assignReactionInterface(reactionsInterface: ReactionsInterface) {
        this.reactionsInterface = reactionsInterface
    }

    companion object {
        private val TAG = NextcloudTalkApplication::class.java.simpleName
    }
}
