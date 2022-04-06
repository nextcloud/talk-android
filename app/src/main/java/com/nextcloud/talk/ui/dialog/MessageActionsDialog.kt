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

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.annotation.NonNull
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.nextcloud.talk.BuildConfig
import com.nextcloud.talk.R
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.controllers.ChatController
import com.nextcloud.talk.databinding.DialogMessageActionsBinding
import com.nextcloud.talk.models.database.CapabilitiesUtil
import com.nextcloud.talk.models.database.UserEntity
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.utils.ApiUtils
import com.vanniktech.emoji.EmojiPopup
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers

class MessageActionsDialog(
    private val chatController: ChatController,
    private val message: ChatMessage,
    private val user: UserEntity?,
    private val currentConversation: Conversation?,
    private val showMessageDeletionButton: Boolean,
    private val ncApi: NcApi
) : BottomSheetDialog(chatController.activity!!, R.style.BottomSheetDialogThemeNoFloating) {

    private lateinit var dialogMessageActionsBinding: DialogMessageActionsBinding

    private lateinit var popup: EmojiPopup

    @SuppressLint("ClickableViewAccessibility")
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
                user?.userId?.isNotEmpty() == true &&
                user?.userId != "?" &&
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

        dialogMessageActionsBinding.emojiMore.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                if (popup.isShowing) {
                    popup.dismiss()
                } else {
                    popup.show()
                }
            }
            true
        }

        popup = EmojiPopup.Builder
            .fromRootView(dialogMessageActionsBinding.root)
            .setOnEmojiPopupShownListener {
                dialogMessageActionsBinding.emojiMore.clearFocus()
                dialogMessageActionsBinding.messageActions.visibility = View.GONE
            }
            .setOnEmojiClickListener { _, imageView ->
                popup.dismiss()
                sendReaction(message, imageView.unicode)
            }
            .setOnEmojiPopupDismissListener {
                dialogMessageActionsBinding.emojiMore.clearFocus()
                dialogMessageActionsBinding.messageActions.visibility = View.VISIBLE

                val imm: InputMethodManager = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as
                    InputMethodManager
                imm.hideSoftInputFromWindow(dialogMessageActionsBinding.emojiMore.windowToken, 0)
            }
            .build(dialogMessageActionsBinding.emojiMore)
        dialogMessageActionsBinding.emojiMore.disableKeyboardInput(popup)
        dialogMessageActionsBinding.emojiMore.forceSingleEmoji()
    }

    private fun initEmojiBar() {
        if (CapabilitiesUtil.hasSpreedFeatureCapability(user, "reactions")) {
            dialogMessageActionsBinding.emojiThumbsUp.setOnClickListener {
                sendReaction(message, dialogMessageActionsBinding.emojiThumbsUp.text.toString())
            }
            dialogMessageActionsBinding.emojiThumbsDown.setOnClickListener {
                sendReaction(message, dialogMessageActionsBinding.emojiThumbsDown.text.toString())
            }
            dialogMessageActionsBinding.emojiLaugh.setOnClickListener {
                sendReaction(message, dialogMessageActionsBinding.emojiLaugh.text.toString())
            }
            dialogMessageActionsBinding.emojiHeart.setOnClickListener {
                sendReaction(message, dialogMessageActionsBinding.emojiHeart.text.toString())
            }
            dialogMessageActionsBinding.emojiConfused.setOnClickListener {
                sendReaction(message, dialogMessageActionsBinding.emojiConfused.text.toString())
            }
            dialogMessageActionsBinding.emojiSad.setOnClickListener {
                sendReaction(message, dialogMessageActionsBinding.emojiSad.text.toString())
            }
            dialogMessageActionsBinding.emojiMore.setOnClickListener {
                dismiss()
            }
            dialogMessageActionsBinding.emojiBar.visibility = View.VISIBLE
        } else {
            dialogMessageActionsBinding.emojiBar.visibility = View.GONE
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

    private fun sendReaction(message: ChatMessage, emoji: String) {
        val credentials = ApiUtils.getCredentials(user?.username, user?.token)

        ncApi.sendReaction(
            credentials,
            ApiUtils.getUrlForMessageReaction(
                user?.baseUrl,
                currentConversation!!.token,
                message.id
            ),
            emoji
        )
            ?.subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(object : Observer<GenericOverall> {
                override fun onSubscribe(d: Disposable) {
                }

                override fun onNext(@NonNull genericOverall: GenericOverall) {
                    val statusCode = genericOverall.ocs.meta.statusCode
                    if (statusCode == 200 || statusCode == 201) {
                        chatController.updateAdapterAfterSendReaction(message, emoji)
                    }
                }

                override fun onError(e: Throwable) {
                    Log.e(TAG, "error while sending emoji")
                }

                override fun onComplete() {
                    dismiss()
                }
            })
    }

    companion object {
        private const val TAG = "MessageActionsDialog"
        private const val ACTOR_LENGTH = 6
        private const val NO_PREVIOUS_MESSAGE_ID: Int = -1
    }
}
