/*
 * Nextcloud Talk application
 *
 * @author Andy Scherzinger
 * Copyright (C) 2022 Andy Scherzinger <info@andy-scherzinger.de>
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

package com.nextcloud.talk.ui.dialog

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.nextcloud.talk.BuildConfig
import com.nextcloud.talk.R
import com.nextcloud.talk.controllers.ChatController
import com.nextcloud.talk.databinding.DialogMessageActionsBinding
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.models.json.conversations.Conversation

class MessageActionsDialog(
    val activity: Activity,
    private val chatController: ChatController,
    private val message: ChatMessage,
    private val userId: String?,
    private val currentConversation: Conversation?,
    private val showMessageDeletionButton: Boolean
) : BottomSheetDialog(activity) {

    private lateinit var dialogMessageActionsBinding: DialogMessageActionsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dialogMessageActionsBinding = DialogMessageActionsBinding.inflate(layoutInflater)
        setContentView(dialogMessageActionsBinding.root)
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        initEmojiBar()
        initMenuItemCopy(!message.isDeleted)
        initMenuReplyToMessage(message.replyable)
        initMenuReplyPrivately(
            message.replyable &&
                userId?.isNotEmpty() == true &&
                userId != "?" &&
                message.user.id.startsWith("users/") &&
                message.user.id.substring(ACTOR_LENGTH) != currentConversation?.actorId &&
                currentConversation?.type != Conversation.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL
        )
        initMenuDeleteMessage(showMessageDeletionButton)
        initMenuForwardMessage(ChatMessage.MessageType.REGULAR_TEXT_MESSAGE == message.getMessageType())
        initMenuMarkAsUnread(
            message.previousMessageId > NO_PREVIOUS_MESSAGE_ID &&
                ChatMessage.MessageType.SYSTEM_MESSAGE != message.getMessageType() &&
                BuildConfig.DEBUG
        )
    }

    private fun initEmojiBar() {
        dialogMessageActionsBinding.emojiThumbsUp.setOnClickListener{
            dismiss()
        }
        dialogMessageActionsBinding.emojiThumbsDown.setOnClickListener{
            dismiss()
        }
        dialogMessageActionsBinding.emojiLaugh.setOnClickListener{
            dismiss()
        }
        dialogMessageActionsBinding.emojiHeart.setOnClickListener{
            dismiss()
        }
        dialogMessageActionsBinding.emojiConfused.setOnClickListener{
            dismiss()
        }
        dialogMessageActionsBinding.emojiSad.setOnClickListener{
            dismiss()
        }
        dialogMessageActionsBinding.emojiMore.setOnClickListener{
            dismiss()
        }
    }

    private fun initMenuMarkAsUnread(visible: Boolean) {
        if (visible) {
            dialogMessageActionsBinding.menuMarkAsUnread.setOnClickListener {
                chatController.markAsUnread(message)
                dismiss()
            }
        }

        dialogMessageActionsBinding.menuMarkAsUnread.visibility = getVisibility(visible)
    }

    private fun initMenuForwardMessage(visible: Boolean) {
        if (visible) {
            dialogMessageActionsBinding.menuForwardMessage.setOnClickListener {
                chatController.forwardMessage(message)
                dismiss()
            }
        }

        dialogMessageActionsBinding.menuForwardMessage.visibility = getVisibility(visible)
    }

    private fun initMenuDeleteMessage(visible: Boolean) {
        if (visible) {
            dialogMessageActionsBinding.menuDeleteMessage.setOnClickListener {
                chatController.deleteMessage(message)
                dismiss()
            }
        }

        dialogMessageActionsBinding.menuDeleteMessage.visibility = getVisibility(visible)
    }

    private fun initMenuReplyPrivately(visible: Boolean) {
        if (visible) {
            dialogMessageActionsBinding.menuReplyPrivately.setOnClickListener {
                chatController.replyPrivately(message)
                dismiss()
            }
        }

        dialogMessageActionsBinding.menuReplyPrivately.visibility = getVisibility(visible)
    }

    private fun initMenuReplyToMessage(visible: Boolean) {
        if (visible) {
            dialogMessageActionsBinding.menuReplyToMessage.setOnClickListener {
                chatController.replyToMessage(message)
                dismiss()
            }
        }

        dialogMessageActionsBinding.menuReplyToMessage.visibility = getVisibility(visible)
    }

    private fun initMenuItemCopy(visible: Boolean) {
        if (visible) {
            dialogMessageActionsBinding.menuCopyMessage.setOnClickListener {
                chatController.copyMessage(message)
                dismiss()
            }
        }

        dialogMessageActionsBinding.menuCopyMessage.visibility = getVisibility(visible)
    }

    override fun onStart() {
        super.onStart()
        val bottomSheet = findViewById<View>(R.id.design_bottom_sheet)
        val behavior = BottomSheetBehavior.from(bottomSheet as View)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun getVisibility(visible: Boolean): Int {
        return if (visible) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    companion object {
        private const val ACTOR_LENGTH = 6
        private const val NO_PREVIOUS_MESSAGE_ID: Int = -1
    }
}
