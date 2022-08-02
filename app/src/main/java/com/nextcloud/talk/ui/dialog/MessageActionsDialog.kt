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
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.annotation.NonNull
import autodagger.AutoInjector
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.nextcloud.talk.BuildConfig
import com.nextcloud.talk.R
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.controllers.ChatController
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.DialogMessageActionsBinding
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.database.user.CapabilitiesUtilNew
import com.vanniktech.emoji.EmojiPopup
import com.vanniktech.emoji.EmojiTextView
import com.vanniktech.emoji.installDisableKeyboardInput
import com.vanniktech.emoji.installForceSingleEmoji
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class MessageActionsDialog(
    private val chatController: ChatController,
    private val message: ChatMessage,
    private val user: User?,
    private val currentConversation: Conversation?,
    private val showMessageDeletionButton: Boolean,
    private val hasChatPermission: Boolean,
    private val ncApi: NcApi
) : BottomSheetDialog(chatController.activity!!) {

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    private lateinit var dialogMessageActionsBinding: DialogMessageActionsBinding

    private lateinit var popup: EmojiPopup

    init {
        NextcloudTalkApplication.sharedApplication?.componentApplication?.inject(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dialogMessageActionsBinding = DialogMessageActionsBinding.inflate(layoutInflater)
        setContentView(dialogMessageActionsBinding.root)
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        viewThemeUtils.themeDialog(dialogMessageActionsBinding.root)
        initEmojiBar(hasChatPermission)
        initMenuItemCopy(!message.isDeleted)
        initMenuReplyToMessage(message.replyable && hasChatPermission)
        initMenuReplyPrivately(
            message.replyable &&
                hasUserId(user) &&
                hasUserActorId(message) &&
                currentConversation?.type != Conversation.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL
        )
        initMenuDeleteMessage(showMessageDeletionButton)
        initMenuForwardMessage(
            ChatMessage.MessageType.REGULAR_TEXT_MESSAGE == message.getCalculateMessageType() &&
                !(message.isDeletedCommentMessage || message.isDeleted)
        )
        initMenuMarkAsUnread(
            message.previousMessageId > NO_PREVIOUS_MESSAGE_ID &&
                ChatMessage.MessageType.SYSTEM_MESSAGE != message.getCalculateMessageType() &&
                BuildConfig.DEBUG
        )
    }

    override fun onStart() {
        super.onStart()
        val bottomSheet = findViewById<View>(R.id.design_bottom_sheet)
        val behavior = BottomSheetBehavior.from(bottomSheet as View)
        behavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    private fun hasUserId(user: User?): Boolean {
        return user?.userId?.isNotEmpty() == true && user.userId != "?"
    }

    private fun hasUserActorId(message: ChatMessage): Boolean {
        return message.user.id.startsWith("users/") &&
            message.user.id.substring(ACTOR_LENGTH) != currentConversation?.actorId
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initEmojiMore() {
        dialogMessageActionsBinding.emojiMore.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                toggleEmojiPopup()
            }
            true
        }

        popup = EmojiPopup(
            rootView = dialogMessageActionsBinding.root,
            editText = dialogMessageActionsBinding.emojiMore,
            onEmojiPopupShownListener = {
                dialogMessageActionsBinding.emojiMore.clearFocus()
                dialogMessageActionsBinding.messageActions.visibility = View.GONE
            },
            onEmojiClickListener = {
                popup.dismiss()
                sendReaction(message, it.unicode)
            },
            onEmojiPopupDismissListener = {
                dialogMessageActionsBinding.emojiMore.clearFocus()
                dialogMessageActionsBinding.messageActions.visibility = View.VISIBLE

                val imm: InputMethodManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as
                    InputMethodManager
                imm.hideSoftInputFromWindow(dialogMessageActionsBinding.emojiMore.windowToken, 0)
            }
        )
        dialogMessageActionsBinding.emojiMore.installDisableKeyboardInput(popup)
        dialogMessageActionsBinding.emojiMore.installForceSingleEmoji()
    }

    /*
        This method is a hacky workaround to avoid bug #1914
        As the bug happens only for the very first time when the popup is opened,
        it is closed after some milliseconds and opened again.
     */
    private fun toggleEmojiPopup() {
        if (popup.isShowing) {
            popup.dismiss()
        } else {
            popup.show()
            Handler(Looper.getMainLooper()).postDelayed(
                {
                    popup.dismiss()
                    popup.show()
                },
                DELAY
            )
        }
    }

    private fun initEmojiBar(hasChatPermission: Boolean) {
        if (CapabilitiesUtilNew.hasSpreedFeatureCapability(user, "reactions") &&
            isPermitted(hasChatPermission) &&
            isReactableMessageType(message)
        ) {
            checkAndSetEmojiSelfReaction(dialogMessageActionsBinding.emojiThumbsUp)
            dialogMessageActionsBinding.emojiThumbsUp.setOnClickListener {
                sendReaction(message, dialogMessageActionsBinding.emojiThumbsUp.text.toString())
            }
            checkAndSetEmojiSelfReaction(dialogMessageActionsBinding.emojiThumbsDown)
            dialogMessageActionsBinding.emojiThumbsDown.setOnClickListener {
                sendReaction(message, dialogMessageActionsBinding.emojiThumbsDown.text.toString())
            }
            checkAndSetEmojiSelfReaction(dialogMessageActionsBinding.emojiLaugh)
            dialogMessageActionsBinding.emojiLaugh.setOnClickListener {
                sendReaction(message, dialogMessageActionsBinding.emojiLaugh.text.toString())
            }
            checkAndSetEmojiSelfReaction(dialogMessageActionsBinding.emojiHeart)
            dialogMessageActionsBinding.emojiHeart.setOnClickListener {
                sendReaction(message, dialogMessageActionsBinding.emojiHeart.text.toString())
            }
            checkAndSetEmojiSelfReaction(dialogMessageActionsBinding.emojiConfused)
            dialogMessageActionsBinding.emojiConfused.setOnClickListener {
                sendReaction(message, dialogMessageActionsBinding.emojiConfused.text.toString())
            }
            checkAndSetEmojiSelfReaction(dialogMessageActionsBinding.emojiSad)
            dialogMessageActionsBinding.emojiSad.setOnClickListener {
                sendReaction(message, dialogMessageActionsBinding.emojiSad.text.toString())
            }

            dialogMessageActionsBinding.emojiMore.setOnClickListener {
                dismiss()
            }
            initEmojiMore()
            dialogMessageActionsBinding.emojiBar.visibility = View.VISIBLE
        } else {
            dialogMessageActionsBinding.emojiBar.visibility = View.GONE
        }
    }

    private fun isPermitted(hasChatPermission: Boolean): Boolean {
        return hasChatPermission && Conversation.ConversationReadOnlyState.CONVERSATION_READ_ONLY !=
            currentConversation?.conversationReadOnlyState
    }

    private fun isReactableMessageType(message: ChatMessage): Boolean {
        return !(message.isCommandMessage || message.isDeletedCommentMessage || message.isDeleted)
    }

    private fun checkAndSetEmojiSelfReaction(emoji: EmojiTextView) {
        if (emoji.text?.toString() != null && message.reactionsSelf?.contains(emoji.text?.toString()) == true) {
            viewThemeUtils.setCheckedBackground(emoji)
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

    private fun getVisibility(visible: Boolean): Int {
        return if (visible) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun sendReaction(message: ChatMessage, emoji: String) {
        if (message.reactionsSelf?.contains(emoji) == true) {
            deleteReaction(message, emoji)
        } else {
            addReaction(message, emoji)
        }
    }

    private fun addReaction(message: ChatMessage, emoji: String) {
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
                    // unused atm
                }

                override fun onNext(@NonNull genericOverall: GenericOverall) {
                    val statusCode = genericOverall.ocs?.meta?.statusCode
                    if (statusCode == HTTP_CREATED) {
                        chatController.updateAdapterAfterSendReaction(message, emoji)
                    }
                }

                override fun onError(e: Throwable) {
                    Log.e(TAG, "error while sending reaction: $emoji")
                }

                override fun onComplete() {
                    dismiss()
                }
            })
    }

    private fun deleteReaction(message: ChatMessage, emoji: String) {
        val credentials = ApiUtils.getCredentials(user?.username, user?.token)

        ncApi.deleteReaction(
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
                    // unused atm
                }

                override fun onNext(@NonNull genericOverall: GenericOverall) {
                    Log.d(TAG, "deleted reaction: $emoji")
                }

                override fun onError(e: Throwable) {
                    Log.e(TAG, "error while deleting reaction: $emoji")
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
        private const val HTTP_CREATED: Int = 201
        private const val DELAY: Long = 200
    }
}
