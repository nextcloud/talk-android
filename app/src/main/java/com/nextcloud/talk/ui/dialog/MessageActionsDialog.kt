/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
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
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import autodagger.AutoInjector
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.chat.ChatActivity
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.data.network.NetworkMonitor
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.DialogMessageActionsBinding
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.domain.ReactionAddedModel
import com.nextcloud.talk.models.domain.ReactionDeletedModel
import com.nextcloud.talk.models.json.capabilities.SpreedCapability
import com.nextcloud.talk.models.json.conversations.ConversationEnums
import com.nextcloud.talk.repositories.reactions.ReactionsRepository
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.CapabilitiesUtil
import com.nextcloud.talk.utils.CapabilitiesUtil.hasSpreedFeatureCapability
import com.nextcloud.talk.utils.ConversationUtils
import com.nextcloud.talk.utils.DateConstants
import com.nextcloud.talk.utils.DateUtils
import com.nextcloud.talk.utils.SpreedFeatures
import com.vanniktech.emoji.Emoji
import com.vanniktech.emoji.EmojiPopup
import com.vanniktech.emoji.EmojiTextView
import com.vanniktech.emoji.installDisableKeyboardInput
import com.vanniktech.emoji.installForceSingleEmoji
import com.vanniktech.emoji.recent.RecentEmojiManager
import com.vanniktech.emoji.search.SearchEmojiManager
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class MessageActionsDialog(
    private val chatActivity: ChatActivity,
    private val message: ChatMessage,
    private val user: User?,
    private val currentConversation: ConversationModel?,
    private val showMessageDeletionButton: Boolean,
    private val hasChatPermission: Boolean,
    private val spreedCapabilities: SpreedCapability
) : BottomSheetDialog(chatActivity) {

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    @Inject
    lateinit var reactionsRepository: ReactionsRepository

    @Inject
    lateinit var dateUtils: DateUtils

    @Inject
    lateinit var networkMonitor: NetworkMonitor

    private lateinit var dialogMessageActionsBinding: DialogMessageActionsBinding

    private lateinit var popup: EmojiPopup

    private val messageHasFileAttachment =
        ChatMessage.MessageType.SINGLE_NC_ATTACHMENT_MESSAGE == message.getCalculateMessageType()

    private val messageHasRegularText = ChatMessage.MessageType.REGULAR_TEXT_MESSAGE == message
        .getCalculateMessageType() &&
        !message.isDeleted

    private val isOlderThanTwentyFourHours = message
        .createdAt
        .before(Date(System.currentTimeMillis() - AGE_THRESHOLD_FOR_EDIT_MESSAGE))

    private val isUserAllowedToEdit = chatActivity.userAllowedByPrivilages(message)
    private var isNoTimeLimitOnNoteToSelf = hasSpreedFeatureCapability(
        spreedCapabilities,
        SpreedFeatures
            .EDIT_MESSAGES_NOTE_TO_SELF
    ) &&
        currentConversation?.type == ConversationEnums.ConversationType.NOTE_TO_SELF

    private val isMessageBotOneToOne = (message.actorType == ACTOR_BOTS) &&
        (
            message.isOneToOneConversation ||
                message.isFormerOneToOneConversation
            ) &&
        !isOlderThanTwentyFourHours

    private var messageIsEditable = hasSpreedFeatureCapability(
        spreedCapabilities,
        SpreedFeatures.EDIT_MESSAGES
    ) &&
        messageHasRegularText &&
        !isOlderThanTwentyFourHours &&
        isUserAllowedToEdit

    private val isMessageEditable = isNoTimeLimitOnNoteToSelf || messageIsEditable || isMessageBotOneToOne

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication?.componentApplication?.inject(this)

        dialogMessageActionsBinding = DialogMessageActionsBinding.inflate(layoutInflater)
        setContentView(dialogMessageActionsBinding.root)
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        viewThemeUtils.material.colorBottomSheetBackground(dialogMessageActionsBinding.root)
        viewThemeUtils.material.colorBottomSheetDragHandle(dialogMessageActionsBinding.bottomSheetDragHandle)
        initEmojiBar(hasChatPermission)
        initMenuItemCopy(!message.isDeleted)
        initMenuItems(networkMonitor.isOnline.value)
    }

    private fun initMenuItems(isOnline: Boolean) {
        this.lifecycleScope.launch {
            initMenuItemTranslate(
                !message.isDeleted &&
                    ChatMessage.MessageType.REGULAR_TEXT_MESSAGE == message.getCalculateMessageType() &&
                    CapabilitiesUtil.isTranslationsSupported(spreedCapabilities) &&
                    isOnline
            )
            initMenuEditorDetails(message.lastEditTimestamp!! != 0L && !message.isDeleted)
            initMenuReplyToMessage(message.replyable && hasChatPermission)
            initMenuReplyPrivately(
                message.replyable &&
                    hasUserId(user) &&
                    hasUserActorId(message) &&
                    currentConversation?.type != ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL &&
                    isOnline
            )
            initMenuStartThread(!message.isThread)
            initMenuOpenThread(message.isThread && chatActivity.conversationThreadId == null)
            initMenuEditMessage(isMessageEditable)
            initMenuDeleteMessage(showMessageDeletionButton && isOnline)
            initMenuForwardMessage(
                ChatMessage.MessageType.REGULAR_TEXT_MESSAGE == message.getCalculateMessageType() &&
                    !(message.isDeletedCommentMessage || message.isDeleted) &&
                    isOnline
            )
            initMenuRemindMessage(
                !message.isDeleted &&
                    hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.REMIND_ME_LATER) &&
                    isOnline
            )
            initMenuMarkAsUnread(
                message.previousMessageId > NO_PREVIOUS_MESSAGE_ID &&
                    ChatMessage.MessageType.SYSTEM_MESSAGE != message.getCalculateMessageType() &&
                    isOnline
            )
            initMenuShare(messageHasFileAttachment || messageHasRegularText && isOnline)
            initMenuItemOpenNcApp(
                ChatMessage.MessageType.SINGLE_NC_ATTACHMENT_MESSAGE == message.getCalculateMessageType() &&
                    isOnline
            )
            initMenuItemSave(message.getCalculateMessageType() == ChatMessage.MessageType.SINGLE_NC_ATTACHMENT_MESSAGE)
            initMenuAddToNote(
                !message.isDeleted &&
                    !ConversationUtils.isNoteToSelfConversation(currentConversation) &&
                    networkMonitor.isOnline.value
            )
        }
    }

    override fun onStart() {
        super.onStart()
        val bottomSheet = findViewById<View>(R.id.design_bottom_sheet)
        val behavior = BottomSheetBehavior.from(bottomSheet as View)
        behavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    private fun hasUserId(user: User?): Boolean = user?.userId?.isNotEmpty() == true && user.userId != "?"

    private fun hasUserActorId(message: ChatMessage): Boolean =
        message.user.id.startsWith("users/") &&
            message.user.id.substring(ACTOR_LENGTH) != currentConversation?.actorId

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
        if (hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.REACTIONS) &&
            isPermitted(hasChatPermission) &&
            isReactableMessageType(message)
        ) {
            val recentEmojiManager = RecentEmojiManager(context, MAX_RECENTS)
            val recentEmojis = recentEmojiManager.getRecentEmojis()
            val searchEmojiManager = SearchEmojiManager()

            val initialSearchKeywords = listOf(
                "thumbsup",
                "thumbsdown",
                "heart",
                "joy",
                "confused",
                "cry",
                "pray",
                "fire"
            )
            val initialEmojisFromSearch = mutableSetOf<Emoji>()

            initialSearchKeywords.forEach { keyword ->
                val searchResults = searchEmojiManager.search(keyword)
                if (searchResults.isNotEmpty()) {
                    initialEmojisFromSearch.add(searchResults[ZERO_INDEX].component1())
                    recentEmojiManager.addEmoji(searchResults[ZERO_INDEX].component1())
                }
                if (initialEmojisFromSearch.size >= MAX_RECENTS) {
                    return@forEach
                }
            }
            val combinedEmojis = (recentEmojis + initialEmojisFromSearch).toList().distinct().take(MAX_RECENTS)

            setupEmojiView(combinedEmojis, recentEmojiManager)

            dialogMessageActionsBinding.emojiMore.setOnClickListener {
                dismiss()
            }
            initEmojiMore()
            dialogMessageActionsBinding.emojiBar.visibility = View.VISIBLE
        } else {
            dialogMessageActionsBinding.emojiBar.visibility = View.GONE
        }
    }

    private fun setupEmojiView(combinedEmojis: List<Emoji>, recentEmojiManager: RecentEmojiManager) {
        val emojiSearchKeywords = mapOf(
            "👍" to "thumbsup",
            "👎" to "thumbsdown",
            "❤️" to "heart",
            "😂" to "joy",
            "😕" to "confused",
            "😢" to "cry",
            "🙏" to "pray",
            "🔥" to "fire"
        )

        val emojiTextViews = listOf(
            dialogMessageActionsBinding.emojiThumbsUp,
            dialogMessageActionsBinding.emojiThumbsDown,
            dialogMessageActionsBinding.emojiHeart,
            dialogMessageActionsBinding.emojiLaugh,
            dialogMessageActionsBinding.emojiConfused,
            dialogMessageActionsBinding.emojiCry,
            dialogMessageActionsBinding.emojiPray,
            dialogMessageActionsBinding.emojiFire
        )

        emojiTextViews.forEachIndexed { index, textView ->
            val emoji = combinedEmojis.getOrNull(index)?.unicode
            if (emoji != null) {
                textView.text = emoji
                checkAndSetEmojiSelfReaction(textView)
                textView.setOnClickListener {
                    clickOnEmoji(message, emoji)
                    val keyword = emojiSearchKeywords[emoji] ?: ""
                    val result = SearchEmojiManager().search(keyword)
                    if (result.isNotEmpty()) {
                        recentEmojiManager.addEmoji(result[ZERO_INDEX].component1())
                        recentEmojiManager.persist()
                    }
                }
                textView.visibility = View.VISIBLE
            } else {
                textView.visibility = View.GONE
            }
        }
    }

    private fun isPermitted(hasChatPermission: Boolean): Boolean =
        hasChatPermission &&
            ConversationEnums.ConversationReadOnlyState.CONVERSATION_READ_ONLY !=
            currentConversation?.conversationReadOnlyState

    private fun isReactableMessageType(message: ChatMessage): Boolean =
        !(message.isCommandMessage || message.isDeletedCommentMessage || message.isDeleted)

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

    private fun initMenuRemindMessage(visible: Boolean) {
        if (visible) {
            dialogMessageActionsBinding.menuNotifyMessage.setOnClickListener {
                chatActivity.remindMeLater(message)
                dismiss()
            }
        }

        dialogMessageActionsBinding.menuNotifyMessage.visibility = getVisibility(visible)
    }

    private fun initMenuDeleteMessage(visible: Boolean) {
        if (visible) {
            dialogMessageActionsBinding.menuDeleteMessage.setOnClickListener {
                val areYouSure = context.resources.getString(R.string.message_delete_are_you_sure)
                val deleteMessage = context.resources.getString(R.string.nc_delete_message)
                val delete = context.resources.getString(R.string.nc_delete)
                val cancel = context.resources.getString(R.string.nc_cancel)
                val builder = MaterialAlertDialogBuilder(context)
                builder
                    .setIcon(
                        viewThemeUtils.dialog
                            .colorMaterialAlertDialogIcon(context, R.drawable.ic_delete_black_24dp)
                    )
                    .setMessage(areYouSure)
                    .setTitle(deleteMessage)
                    .setPositiveButton(delete) { dialog, which ->
                        chatActivity.deleteMessage(message)
                        dismiss()
                    }
                    .setNegativeButton(cancel) { dialog, which ->
                        // unused atm
                    }
                    .let { dialogBuilder ->
                        viewThemeUtils.dialog
                            .colorMaterialAlertDialogBackground(context, dialogBuilder)
                    }

                val dialog: AlertDialog = builder.create()
                dialog.setOnShowListener {
                    viewThemeUtils.platform.colorTextButtons(
                        dialog.getButton(BUTTON_POSITIVE),
                        dialog.getButton(BUTTON_NEGATIVE)
                    )
                }
                dialog.show()
            }
        }
        dialogMessageActionsBinding.menuDeleteMessage.visibility = getVisibility(visible)
    }

    private fun initMenuEditMessage(visible: Boolean) {
        dialogMessageActionsBinding.menuEditMessage.setOnClickListener {
            chatActivity.messageInputViewModel.edit(message)
            dismiss()
        }

        dialogMessageActionsBinding.menuEditMessage.visibility = getVisibility(visible)
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

    private fun initMenuStartThread(visible: Boolean) {
        if (visible) {
            dialogMessageActionsBinding.menuStartThread.setOnClickListener {
                chatActivity.createThread(message)
                dismiss()
            }
        }

        dialogMessageActionsBinding.menuStartThread.visibility = getVisibility(visible)
    }

    private fun initMenuOpenThread(visible: Boolean) {
        if (visible) {
            dialogMessageActionsBinding.menuOpenThread.setOnClickListener {
                message.threadId?.let {
                    chatActivity.openThread(it)
                    dismiss()
                }
            }
        }

        dialogMessageActionsBinding.menuOpenThread.visibility = getVisibility(visible)
    }

    private fun initMenuReplyToMessage(visible: Boolean) {
        if (visible) {
            dialogMessageActionsBinding.menuReplyToMessage.setOnClickListener {
                chatActivity.messageInputViewModel.reply(message)
                dismiss()
            }
        }

        dialogMessageActionsBinding.menuReplyToMessage.visibility = getVisibility(visible)
    }

    private fun initMenuEditorDetails(showEditorDetails: Boolean) {
        if (showEditorDetails) {
            val editedTime = dateUtils.getLocalDateTimeStringFromTimestamp(
                message.lastEditTimestamp!! *
                    DateConstants.SECOND_DIVIDER
            )
            val lastEditorName = message.lastEditActorDisplayName ?: ""
            val editorName = String.format(
                context.getString(R.string.message_last_edited_by),
                lastEditorName
            )
            dialogMessageActionsBinding.editorName.text = editorName
            dialogMessageActionsBinding.editedTime.text = editedTime
        }
        dialogMessageActionsBinding.menuMessageEditedInfo.visibility = getVisibility(showEditorDetails)
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

    private fun initMenuShare(visible: Boolean) {
        if (messageHasFileAttachment) {
            dialogMessageActionsBinding.menuShare.setOnClickListener {
                chatActivity.checkIfSharable(message)
                dismiss()
            }
        }
        if (messageHasRegularText) {
            dialogMessageActionsBinding.menuShare.setOnClickListener {
                message.message?.let { messageText -> chatActivity.shareMessageText(messageText) }
                dismiss()
            }
        }
        dialogMessageActionsBinding.menuShare.visibility = getVisibility(visible)
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

    private fun initMenuItemSave(visible: Boolean) {
        if (visible) {
            dialogMessageActionsBinding.menuSaveMessage.setOnClickListener {
                chatActivity.checkIfSaveable(message)
                dismiss()
            }
        }
        dialogMessageActionsBinding.menuSaveMessage.visibility = getVisibility(visible)
    }

    private fun initMenuAddToNote(visible: Boolean) {
        if (visible) {
            dialogMessageActionsBinding.menuShareToNote.setOnClickListener {
                chatActivity.shareToNotes(message)
                dismiss()
            }
        }
        dialogMessageActionsBinding.menuShareToNote.visibility = getVisibility(visible)
    }

    private fun getVisibility(visible: Boolean): Int =
        if (visible) {
            View.VISIBLE
        } else {
            View.GONE
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
        private const val AGE_THRESHOLD_FOR_EDIT_MESSAGE: Long = 86400000
        private const val ACTOR_BOTS = "bots"
        private const val ZERO_INDEX = 0
        private const val MAX_RECENTS = 8
    }
}
