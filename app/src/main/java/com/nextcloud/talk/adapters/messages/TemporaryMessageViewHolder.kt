/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.adapters.messages

import android.content.Context
import android.view.View
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import autodagger.AutoInjector
import com.nextcloud.android.common.ui.theme.utils.ColorRole
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.databinding.ItemTemporaryMessageBinding
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.DisplayUtils
import com.stfalcon.chatkit.messages.MessagesListAdapter
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class TemporaryMessageViewHolder(outgoingView: View, payload: Any) :
    MessagesListAdapter.OutcomingMessageViewHolder<ChatMessage>(outgoingView) {

    private val binding: ItemTemporaryMessageBinding = ItemTemporaryMessageBinding.bind(outgoingView)

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    @Inject
    lateinit var context: Context

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
}
