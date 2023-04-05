/*
 * Nextcloud Talk application
 *
 * @author Marcel Hibbe
 * Copyright (C) 2022 Marcel Hibbe <dev@mhibbe.de>
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
import android.text.TextUtils
import android.view.View
import androidx.core.content.ContextCompat
import autodagger.AutoInjector
import coil.load
import com.nextcloud.talk.R
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.chat.ChatActivity
import com.nextcloud.talk.databinding.ItemCustomIncomingPollMessageBinding
import com.nextcloud.talk.extensions.loadBotsAvatar
import com.nextcloud.talk.extensions.loadChangelogBotAvatar
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.polls.ui.PollMainDialogFragment
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.DateUtils
import com.nextcloud.talk.utils.preferences.AppPreferences
import com.stfalcon.chatkit.messages.MessageHolders
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class IncomingPollMessageViewHolder(incomingView: View, payload: Any) : MessageHolders
.IncomingTextMessageViewHolder<ChatMessage>(incomingView, payload) {

    private val binding: ItemCustomIncomingPollMessageBinding =
        ItemCustomIncomingPollMessageBinding.bind(itemView)

    @Inject
    lateinit var context: Context

    @Inject
    lateinit var appPreferences: AppPreferences

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    @Inject
    lateinit var dateUtils: DateUtils

    @Inject
    lateinit var ncApi: NcApi

    lateinit var message: ChatMessage

    lateinit var commonMessageInterface: CommonMessageInterface

    @SuppressLint("SetTextI18n")
    override fun onBind(message: ChatMessage) {
        super.onBind(message)
        this.message = message
        sharedApplication!!.componentApplication.inject(this)
        binding.messageTime.text = dateUtils.getLocalTimeStringFromTimestamp(message.timestamp)

        setAvatarAndAuthorOnMessageItem(message)

        colorizeMessageBubble(message)

        itemView.isSelected = false

        // parent message handling
        setParentMessageDataOnMessageItem(message)

        setPollPreview(message)

        Reaction().showReactions(
            message,
            ::clickOnReaction,
            ::longClickOnReaction,
            binding.reactions,
            binding.messageTime.context,
            false,
            viewThemeUtils
        )
    }

    private fun longClickOnReaction(chatMessage: ChatMessage) {
        commonMessageInterface.onLongClickReactions(chatMessage)
    }

    private fun clickOnReaction(chatMessage: ChatMessage, emoji: String) {
        commonMessageInterface.onClickReaction(chatMessage, emoji)
    }

    private fun setPollPreview(message: ChatMessage) {
        var pollId: String? = null
        var pollName: String? = null

        if (message.messageParameters != null && message.messageParameters!!.size > 0) {
            for (key in message.messageParameters!!.keys) {
                val individualHashMap: Map<String?, String?> = message.messageParameters!![key]!!
                if (individualHashMap["type"] == "talk-poll") {
                    pollId = individualHashMap["id"]
                    pollName = individualHashMap["name"].toString()
                }
            }
        }

        if (pollId != null && pollName != null) {
            binding.messagePollTitle.text = pollName

            val roomToken = (payload as? MessagePayload)!!.roomToken
            val isOwnerOrModerator = (payload as? MessagePayload)!!.isOwnerOrModerator ?: false

            binding.bubble.setOnClickListener {
                val pollVoteDialog = PollMainDialogFragment.newInstance(
                    message.activeUser!!,
                    roomToken,
                    isOwnerOrModerator,
                    pollId,
                    pollName
                )
                pollVoteDialog.show(
                    (binding.messagePollIcon.context as ChatActivity).supportFragmentManager,
                    TAG
                )
            }
        }
    }

    private fun setAvatarAndAuthorOnMessageItem(message: ChatMessage) {
        val author: String = message.actorDisplayName!!
        if (!TextUtils.isEmpty(author)) {
            binding.messageAuthor.visibility = View.VISIBLE
            binding.messageAuthor.text = author
            binding.messageUserAvatar.setOnClickListener {
                (payload as? MessagePayload)?.profileBottomSheet?.showFor(message.actorId!!, itemView.context)
            }
        } else {
            binding.messageAuthor.setText(R.string.nc_nick_guest)
        }

        if (!message.isGrouped && !message.isOneToOneConversation && !message.isFormerOneToOneConversation) {
            setAvatarOnMessage(message)
        } else {
            if (message.isOneToOneConversation || message.isFormerOneToOneConversation) {
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
            binding.messageUserAvatar.loadChangelogBotAvatar()
        } else if (message.actorType == "bots") {
            binding.messageUserAvatar.loadBotsAvatar()
        }
    }

    private fun colorizeMessageBubble(message: ChatMessage) {
        viewThemeUtils.talk.themeIncomingMessageBubble(bubble, message.isGrouped, message.isDeleted)
    }

    private fun setParentMessageDataOnMessageItem(message: ChatMessage) {
        if (!message.isDeleted && message.parentMessage != null) {
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
                ?: context.getText(R.string.nc_nick_guest)
            binding.messageQuote.quotedMessage.text = parentChatMessage.text

            binding.messageQuote.quotedMessageAuthor
                .setTextColor(ContextCompat.getColor(context, R.color.textColorMaxContrast))

            if (parentChatMessage.actorId?.equals(message.activeUser!!.userId) == true) {
                viewThemeUtils.platform.colorPrimaryView(binding.messageQuote.quoteColoredView)
            } else {
                binding.messageQuote.quoteColoredView.setBackgroundResource(R.color.textColorMaxContrast)
            }

            binding.messageQuote.quotedChatMessageView.visibility = View.VISIBLE
        } else {
            binding.messageQuote.quotedChatMessageView.visibility = View.GONE
        }
    }

    fun assignCommonMessageInterface(commonMessageInterface: CommonMessageInterface) {
        this.commonMessageInterface = commonMessageInterface
    }

    companion object {
        private val TAG = NextcloudTalkApplication::class.java.simpleName
    }
}
