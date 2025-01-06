/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Christian Reiner <foss@christian-reiner.info>
 * SPDX-FileCopyrightText: 2021 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2021 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2017-2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.adapters.messages

import android.content.Context
import android.util.Log
import android.util.TypedValue
import android.view.View
import androidx.core.content.res.ResourcesCompat
import autodagger.AutoInjector
import coil.load
import com.google.android.flexbox.FlexboxLayout
import com.nextcloud.android.common.ui.theme.utils.ColorRole
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.chat.ChatActivity
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.data.network.NetworkMonitor
import com.nextcloud.talk.databinding.ItemCustomOutcomingTextMessageBinding
import com.nextcloud.talk.models.json.chat.ReadStatus
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.DateUtils
import com.nextcloud.talk.utils.TextMatchers
import com.nextcloud.talk.utils.message.MessageUtils
import com.stfalcon.chatkit.messages.MessageHolders.OutcomingTextMessageViewHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class OutcomingTextMessageViewHolder(itemView: View) :
    OutcomingTextMessageViewHolder<ChatMessage>(itemView),
    AdjustableMessageHolderInterface {

    override val binding: ItemCustomOutcomingTextMessageBinding = ItemCustomOutcomingTextMessageBinding.bind(itemView)
    private val realView: View = itemView

    @Inject
    lateinit var context: Context

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    @Inject
    lateinit var messageUtils: MessageUtils

    @Inject
    lateinit var dateUtils: DateUtils

    @Inject
    lateinit var networkMonitor: NetworkMonitor

    lateinit var commonMessageInterface: CommonMessageInterface

    @Suppress("Detekt.LongMethod")
    override fun onBind(message: ChatMessage) {
        super.onBind(message)
        sharedApplication!!.componentApplication.inject(this)
        realView.isSelected = false
        val layoutParams = binding.messageTime.layoutParams as FlexboxLayout.LayoutParams
        layoutParams.isWrapBefore = false
        var textSize = context.resources.getDimension(R.dimen.chat_text_size)
        viewThemeUtils.platform.colorTextView(binding.messageTime, ColorRole.ON_SURFACE_VARIANT)

        var processedMessageText = messageUtils.enrichChatMessageText(
            binding.messageText.context,
            message,
            false,
            viewThemeUtils
        )
        processedMessageText = messageUtils.processMessageParameters(
            binding.messageText.context,
            viewThemeUtils,
            processedMessageText!!,
            message,
            itemView
        )

        if (
            (message.messageParameters == null || message.messageParameters!!.size <= 0) &&
            TextMatchers.isMessageWithSingleEmoticonOnly(message.text)
        ) {
            textSize = (textSize * TEXT_SIZE_MULTIPLIER).toFloat()
            layoutParams.isWrapBefore = true
            realView.isSelected = true
        }

        setBubbleOnChatMessage(message)

        binding.messageText.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)
        binding.messageTime.layoutParams = layoutParams
        viewThemeUtils.platform.colorTextView(binding.messageText, ColorRole.ON_SURFACE_VARIANT)
        binding.messageText.text = processedMessageText

        if (message.lastEditTimestamp != 0L && !message.isDeleted) {
            binding.messageEditIndicator.visibility = View.VISIBLE
            binding.messageTime.text = dateUtils.getLocalTimeStringFromTimestamp(message.lastEditTimestamp!!)
        } else {
            binding.messageEditIndicator.visibility = View.GONE
            binding.messageTime.text = dateUtils.getLocalTimeStringFromTimestamp(message.timestamp)
        }

        // parent message handling
        if (!message.isDeleted && message.parentMessageId != null) {
            processParentMessage(message)
            binding.messageQuote.quotedChatMessageView.visibility = View.VISIBLE
        } else {
            binding.messageQuote.quotedChatMessageView.visibility = View.GONE
        }

        binding.checkMark.visibility = View.INVISIBLE
        binding.sendingProgress.visibility = View.GONE

        if (message.sendingFailed) {
            updateStatus(
                R.drawable.baseline_report_problem_24,
                "failed"
            )
            binding.bubble.setOnClickListener {
                commonMessageInterface.onOpenMessageActionsDialog(message)
            }
        } else if (message.isTemporary) {
            showSendingSpinner()
        } else if (message.readStatus == ReadStatus.READ) {
            updateStatus(R.drawable.ic_check_all, context.resources?.getString(R.string.nc_message_read))
        } else if (message.readStatus == ReadStatus.SENT) {
            updateStatus(R.drawable.ic_check, context.resources?.getString(R.string.nc_message_sent))
        }

        CoroutineScope(Dispatchers.Main).launch {
            if (message.isTemporary && !networkMonitor.isOnline.first()) {
                updateStatus(
                    R.drawable.ic_signal_wifi_off_white_24dp,
                    "offline"
                )
            }
        }

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

    private fun updateStatus(readStatusDrawableInt: Int, description: String?) {
        binding.sendingProgress.visibility = View.GONE
        binding.checkMark.visibility = View.VISIBLE
        readStatusDrawableInt.let { drawableInt ->
            ResourcesCompat.getDrawable(context.resources, drawableInt, null)?.let {
                binding.checkMark.setImageDrawable(it)
                viewThemeUtils.talk.themeMessageCheckMark(binding.checkMark)
            }
        }
        binding.checkMark.contentDescription = description
    }

    private fun showSendingSpinner() {
        binding.sendingProgress.visibility = View.VISIBLE
        binding.checkMark.visibility = View.GONE

        viewThemeUtils.material.colorProgressBar(binding.sendingProgress)
    }

    private fun longClickOnReaction(chatMessage: ChatMessage) {
        commonMessageInterface.onLongClickReactions(chatMessage)
    }

    private fun clickOnReaction(chatMessage: ChatMessage, emoji: String) {
        commonMessageInterface.onClickReaction(chatMessage, emoji)
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    private fun processParentMessage(message: ChatMessage) {
        if (message.parentMessageId != null && !message.isDeleted) {
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val chatActivity = commonMessageInterface as ChatActivity
                    val urlForChatting = ApiUtils.getUrlForChat(
                        chatActivity.chatApiVersion,
                        chatActivity.conversationUser?.baseUrl,
                        chatActivity.roomToken
                    )

                    val parentChatMessage = withContext(Dispatchers.IO) {
                        chatActivity.chatViewModel.getMessageById(
                            urlForChatting,
                            chatActivity.currentConversation!!,
                            message.parentMessageId!!
                        ).first()
                    }

                    parentChatMessage!!.activeUser = message.activeUser
                    parentChatMessage.imageUrl?.let {
                        binding.messageQuote.quotedMessageImage.visibility = View.VISIBLE
                        binding.messageQuote.quotedMessageImage.load(it) {
                            addHeader(
                                "Authorization",
                                ApiUtils.getCredentials(message.activeUser!!.username, message.activeUser!!.token)!!
                            )
                        }
                    } ?: run {
                        binding.messageQuote.quotedMessageImage.visibility = View.GONE
                    }
                    binding.messageQuote.quotedMessageAuthor.text = parentChatMessage.actorDisplayName
                        ?: context.getText(R.string.nc_nick_guest)
                    binding.messageQuote.quotedMessage.text = messageUtils
                        .enrichChatReplyMessageText(
                            binding.messageQuote.quotedMessage.context,
                            parentChatMessage,
                            false,
                            viewThemeUtils
                        )

                    viewThemeUtils.talk.colorOutgoingQuoteText(binding.messageQuote.quotedMessage)
                    viewThemeUtils.talk.colorOutgoingQuoteAuthorText(binding.messageQuote.quotedMessageAuthor)
                    viewThemeUtils.talk.colorOutgoingQuoteBackground(binding.messageQuote.quoteColoredView)

                    binding.messageQuote.quotedChatMessageView.setOnClickListener {
                        val chatActivity = commonMessageInterface as ChatActivity
                        chatActivity.jumpToQuotedMessage(parentChatMessage)
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Error when processing parent message in view holder", e)
                }
            }
        }
    }

    private fun setBubbleOnChatMessage(message: ChatMessage) {
        viewThemeUtils.talk.themeOutgoingMessageBubble(bubble, message.isGrouped, message.isDeleted)
    }

    fun assignCommonMessageInterface(commonMessageInterface: CommonMessageInterface) {
        this.commonMessageInterface = commonMessageInterface
    }

    companion object {
        const val TEXT_SIZE_MULTIPLIER = 2.5
        private val TAG = OutcomingTextMessageViewHolder::class.java.simpleName
    }
}
