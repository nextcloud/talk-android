/*
 * Nextcloud Talk application
 *
 * @author Andy Scherzinger
 * @author Marcel Hibbe
 * Copyright (C) 2022 Marcel Hibbe <dev@mhibbe.de>
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
import autodagger.AutoInjector
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.chat.ChatActivity
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.DialogMessageActionsBinding
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.domain.ConversationReadOnlyState
import com.nextcloud.talk.models.domain.ConversationType
import com.nextcloud.talk.models.domain.ReactionAddedModel
import com.nextcloud.talk.models.domain.ReactionDeletedModel
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.repositories.reactions.ReactionsRepository
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.database.user.CapabilitiesUtilNew
import com.vanniktech.emoji.EmojiPopup
import com.vanniktech.emoji.EmojiTextView
import com.vanniktech.emoji.installDisableKeyboardInput
import com.vanniktech.emoji.installForceSingleEmoji
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.json.JSONArray
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class MessageActionsDialog(
    private val chatActivity: ChatActivity,
    private val message: ChatMessage,
    private val user: User?,
    private val currentConversation: ConversationModel?,
    private val showMessageDeletionButton: Boolean,
    private val hasChatPermission: Boolean
) : BottomSheetDialog(chatActivity) {

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    @Inject
    lateinit var reactionsRepository: ReactionsRepository

    private lateinit var dialogMessageActionsBinding: DialogMessageActionsBinding

    private lateinit var popup: EmojiPopup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication?.componentApplication?.inject(this)

        dialogMessageActionsBinding = DialogMessageActionsBinding.inflate(layoutInflater)
        setContentView(dialogMessageActionsBinding.root)
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        viewThemeUtils.platform.themeDialog(dialogMessageActionsBinding.root)
        initEmojiBar(hasChatPermission)
        initMenuItemCopy(!message.isDeleted)
        initMenuItemTranslate(
            !message.isDeleted &&
                ChatMessage.MessageType.REGULAR_TEXT_MESSAGE == message.getCalculateMessageType() &&
                CapabilitiesUtilNew.isTranslationsSupported(user) &&
                JSONArray((CapabilitiesUtilNew.getLanguages(user) as ArrayList<*>).toArray()).length() > 0
        )
        initMenuReplyToMessage(message.replyable && hasChatPermission)
        initMenuReplyPrivately(
            message.replyable &&
                hasUserId(user) &&
                hasUserActorId(message) &&
                currentConversation?.type != ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL
        )
        initMenuDeleteMessage(showMessageDeletionButton)
        initMenuForwardMessage(
            ChatMessage.MessageType.REGULAR_TEXT_MESSAGE == message.getCalculateMessageType() &&
                !(message.isDeletedCommentMessage || message.isDeleted)
        )
        initMenuMarkAsUnread(
            message.previousMessageId > NO_PREVIOUS_MESSAGE_ID &&
                ChatMessage.MessageType.SYSTEM_MESSAGE != message.getCalculateMessageType()
        )
        initMenuItemOpenNcApp(
            ChatMessage.MessageType.SINGLE_NC_ATTACHMENT_MESSAGE == message.getCalculateMessageType()
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
                clickOnEmoji(message, it.unicode)
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
                clickOnEmoji(message, dialogMessageActionsBinding.emojiThumbsUp.text.toString())
            }
            checkAndSetEmojiSelfReaction(dialogMessageActionsBinding.emojiThumbsDown)
            dialogMessageActionsBinding.emojiThumbsDown.setOnClickListener {
                clickOnEmoji(message, dialogMessageActionsBinding.emojiThumbsDown.text.toString())
            }
            checkAndSetEmojiSelfReaction(dialogMessageActionsBinding.emojiLaugh)
            dialogMessageActionsBinding.emojiLaugh.setOnClickListener {
                clickOnEmoji(message, dialogMessageActionsBinding.emojiLaugh.text.toString())
            }
            checkAndSetEmojiSelfReaction(dialogMessageActionsBinding.emojiHeart)
            dialogMessageActionsBinding.emojiHeart.setOnClickListener {
                clickOnEmoji(message, dialogMessageActionsBinding.emojiHeart.text.toString())
            }
            checkAndSetEmojiSelfReaction(dialogMessageActionsBinding.emojiConfused)
            dialogMessageActionsBinding.emojiConfused.setOnClickListener {
                clickOnEmoji(message, dialogMessageActionsBinding.emojiConfused.text.toString())
            }
            checkAndSetEmojiSelfReaction(dialogMessageActionsBinding.emojiSad)
            dialogMessageActionsBinding.emojiSad.setOnClickListener {
                clickOnEmoji(message, dialogMessageActionsBinding.emojiSad.text.toString())
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
        return hasChatPermission && ConversationReadOnlyState.CONVERSATION_READ_ONLY !=
            currentConversation?.conversationReadOnlyState
    }

    private fun isReactableMessageType(message: ChatMessage): Boolean {
        return !(message.isCommandMessage || message.isDeletedCommentMessage || message.isDeleted)
    }

    private fun checkAndSetEmojiSelfReaction(emoji: EmojiTextView) {
        if (emoji.text?.toString() != null && message.reactionsSelf?.contains(emoji.text?.toString()) == true) {
            viewThemeUtils.talk.setCheckedBackground(emoji)
        }
    }

    private fun initMenuMarkAsUnread(visible: Boolean) {
        if (visible) {
            dialogMessageActionsBinding.menuMarkAsUnread.setOnClickListener {
                chatActivity.markAsUnread(message)
                dismiss()
            }
        }

        dialogMessageActionsBinding.menuMarkAsUnread.visibility = getVisibility(visible)
    }

    private fun initMenuForwardMessage(visible: Boolean) {
        if (visible) {
            dialogMessageActionsBinding.menuForwardMessage.setOnClickListener {
                chatActivity.forwardMessage(message)
                dismiss()
            }
        }

        dialogMessageActionsBinding.menuForwardMessage.visibility = getVisibility(visible)
    }

    private fun initMenuDeleteMessage(visible: Boolean) {
        if (visible) {
            dialogMessageActionsBinding.menuDeleteMessage.setOnClickListener {
                chatActivity.deleteMessage(message)
                dismiss()
            }
        }

        dialogMessageActionsBinding.menuDeleteMessage.visibility = getVisibility(visible)
    }

    private fun initMenuReplyPrivately(visible: Boolean) {
        if (visible) {
            dialogMessageActionsBinding.menuReplyPrivately.setOnClickListener {
                chatActivity.replyPrivately(message)
                dismiss()
            }
        }

        dialogMessageActionsBinding.menuReplyPrivately.visibility = getVisibility(visible)
    }

    private fun initMenuReplyToMessage(visible: Boolean) {
        if (visible) {
            dialogMessageActionsBinding.menuReplyToMessage.setOnClickListener {
                chatActivity.replyToMessage(message)
                dismiss()
            }
        }

        dialogMessageActionsBinding.menuReplyToMessage.visibility = getVisibility(visible)
    }

    private fun initMenuItemCopy(visible: Boolean) {
        if (visible) {
            dialogMessageActionsBinding.menuCopyMessage.setOnClickListener {
                chatActivity.copyMessage(message)
                dismiss()
            }
        }

        dialogMessageActionsBinding.menuCopyMessage.visibility = getVisibility(visible)
    }

    private fun initMenuItemTranslate(visible: Boolean) {
        if (visible) {
            dialogMessageActionsBinding.menuTranslateMessage.setOnClickListener {
                chatActivity.translateMessage(message)
                dismiss()
            }
        }

        dialogMessageActionsBinding.menuTranslateMessage.visibility = getVisibility(visible)
    }

    private fun initMenuItemOpenNcApp(visible: Boolean) {
        if (visible) {
            dialogMessageActionsBinding.menuOpenInNcApp.setOnClickListener {
                chatActivity.openInFilesApp(message)
                dismiss()
            }
        }

        dialogMessageActionsBinding.menuOpenInNcApp.visibility = getVisibility(visible)
    }

    private fun getVisibility(visible: Boolean): Int {
        return if (visible) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun clickOnEmoji(message: ChatMessage, emoji: String) {
        if (message.reactionsSelf?.contains(emoji) == true) {
            reactionsRepository.deleteReaction(currentConversation!!.token!!, message, emoji)
                .subscribeOn(Schedulers.io())
                ?.observeOn(AndroidSchedulers.mainThread())
                ?.subscribe(ReactionDeletedObserver())
        } else {
            reactionsRepository.addReaction(currentConversation!!.token!!, message, emoji)
                .subscribeOn(Schedulers.io())
                ?.observeOn(AndroidSchedulers.mainThread())
                ?.subscribe(ReactionAddedObserver())
        }
    }

    inner class ReactionAddedObserver : Observer<ReactionAddedModel> {
        override fun onSubscribe(d: Disposable) {
            // unused atm
        }

        override fun onNext(reactionAddedModel: ReactionAddedModel) {
            if (reactionAddedModel.success) {
                chatActivity.updateUiToAddReaction(
                    reactionAddedModel.chatMessage,
                    reactionAddedModel.emoji
                )
            }
        }

        override fun onError(e: Throwable) {
            Log.e(TAG, "failure in ReactionAddedObserver", e)
        }

        override fun onComplete() {
            dismiss()
        }
    }

    inner class ReactionDeletedObserver : Observer<ReactionDeletedModel> {
        override fun onSubscribe(d: Disposable) {
            // unused atm
        }

        override fun onNext(reactionDeletedModel: ReactionDeletedModel) {
            if (reactionDeletedModel.success) {
                chatActivity.updateUiToDeleteReaction(
                    reactionDeletedModel.chatMessage,
                    reactionDeletedModel.emoji
                )
            }
        }

        override fun onError(e: Throwable) {
            Log.e(TAG, "failure in ReactionDeletedObserver", e)
        }

        override fun onComplete() {
            dismiss()
        }
    }

    companion object {
        private val TAG = MessageActionsDialog::class.java.simpleName
        private const val ACTOR_LENGTH = 6
        private const val NO_PREVIOUS_MESSAGE_ID: Int = -1
        private const val DELAY: Long = 200
    }
}
