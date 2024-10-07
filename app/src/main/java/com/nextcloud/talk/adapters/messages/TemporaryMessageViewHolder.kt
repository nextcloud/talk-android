/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.adapters.messages

import android.content.Context
import android.util.Log
import android.view.View
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import autodagger.AutoInjector
import coil.load
import com.nextcloud.android.common.ui.theme.utils.ColorRole
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.chat.ChatActivity
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.databinding.ItemTemporaryMessageBinding
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.message.MessageUtils
import com.stfalcon.chatkit.messages.MessagesListAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class TemporaryMessageViewHolder(outgoingView: View, payload: Any) :
    MessagesListAdapter.OutcomingMessageViewHolder<ChatMessage>(outgoingView) {

    private val binding: ItemTemporaryMessageBinding = ItemTemporaryMessageBinding.bind(outgoingView)

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    @Inject
    lateinit var context: Context

    @Inject
    lateinit var messageUtils: MessageUtils

    lateinit var temporaryMessageInterface: TemporaryMessageInterface
    var isEditing = false

    override fun onBind(message: ChatMessage) {
        super.onBind(message)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        viewThemeUtils.platform.colorImageView(binding.tempMsgEdit, ColorRole.PRIMARY)
        viewThemeUtils.platform.colorImageView(binding.tempMsgDelete, ColorRole.PRIMARY)

        binding.tempMsgEdit.setOnClickListener {
            isEditing = !isEditing
            if (isEditing) {
                binding.tempMsgEdit.setImageDrawable(
                    ResourcesCompat.getDrawable(
                        context.resources,
                        R.drawable.ic_check,
                        null
                    )
                )
                binding.messageEdit.visibility = View.VISIBLE
                binding.messageEdit.requestFocus()
                ViewCompat.getWindowInsetsController(binding.root)?.show(WindowInsetsCompat.Type.ime())
                binding.messageEdit.setText(binding.messageText.text)
                binding.messageText.visibility = View.GONE
            } else {
                binding.tempMsgEdit.setImageDrawable(
                    ResourcesCompat.getDrawable(
                        context.resources,
                        R.drawable.ic_edit,
                        null
                    )
                )
                binding.messageEdit.visibility = View.GONE
                binding.messageText.visibility = View.VISIBLE
                val newMessage = binding.messageEdit.text.toString()
                message.message = newMessage
                temporaryMessageInterface.editTemporaryMessage(message.tempMessageId, newMessage)
            }
        }

        binding.tempMsgDelete.setOnClickListener {
            temporaryMessageInterface.deleteTemporaryMessage(message.tempMessageId)
        }

        // parent message handling
        if (message.parentMessageId != null && message.parentMessageId!! > 0) {
            processParentMessage(message)
            binding.messageQuote.quotedChatMessageView.visibility = View.VISIBLE
        } else {
            binding.messageQuote.quotedChatMessageView.visibility = View.GONE
        }

        val bgBubbleColor = bubble.resources.getColor(R.color.bg_message_list_incoming_bubble, null)
        val layout = R.drawable.shape_outcoming_message
        val bubbleDrawable = DisplayUtils.getMessageSelector(
            bgBubbleColor,
            ResourcesCompat.getColor(bubble.resources, R.color.transparent, null),
            bgBubbleColor,
            layout
        )
        ViewCompat.setBackground(bubble, bubbleDrawable)

    }

    private fun processParentMessage(message: ChatMessage) {
        if (message.parentMessageId != null && !message.isDeleted) {
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val chatActivity = temporaryMessageInterface as ChatActivity
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
                        val placeholder = context.resources.getDrawable(R.drawable.ic_mimetype_image)
                        binding.messageQuote.quotedMessageImage.setImageDrawable(placeholder)
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
                        val chatActivity = temporaryMessageInterface as ChatActivity
                        chatActivity.jumpToQuotedMessage(parentChatMessage)
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Error when processing parent message in view holder", e)
                }
            }
        }
    }

    fun assignTemporaryMessageInterface(temporaryMessageInterface: TemporaryMessageInterface) {
        this.temporaryMessageInterface = temporaryMessageInterface
    }

    override fun viewDetached() {
        // unused atm
    }

    override fun viewAttached() {
        // unused atm
    }

    override fun viewRecycled() {
        // unused atm
    }

    companion object {
        private val TAG = TemporaryMessageViewHolder::class.java.simpleName
    }
}
