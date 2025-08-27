/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2021-2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2021 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2021 Tim Krüger <t@timkrueger.me>
 * SPDX-FileCopyrightText: 2017-2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.adapters.messages

import android.content.Context
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.CheckBox
import androidx.core.content.ContextCompat
import androidx.emoji2.widget.EmojiTextView
import autodagger.AutoInjector
import coil.load
import com.google.android.flexbox.FlexboxLayout
import com.google.android.material.snackbar.Snackbar
import com.nextcloud.android.common.ui.theme.utils.ColorRole
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.chat.ChatActivity
import com.nextcloud.talk.chat.data.ChatMessageRepository
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.ItemCustomIncomingTextMessageBinding
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.CapabilitiesUtil.hasSpreedFeatureCapability
import com.nextcloud.talk.utils.ChatMessageUtils
import com.nextcloud.talk.utils.DateUtils
import com.nextcloud.talk.utils.SpreedFeatures
import com.nextcloud.talk.utils.TextMatchers
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import com.nextcloud.talk.utils.message.MessageUtils
import com.nextcloud.talk.utils.preferences.AppPreferences
import com.stfalcon.chatkit.messages.MessageHolders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class IncomingTextMessageViewHolder(itemView: View, payload: Any) :
    MessageHolders.IncomingTextMessageViewHolder<ChatMessage>(itemView, payload) {

    private val binding: ItemCustomIncomingTextMessageBinding = ItemCustomIncomingTextMessageBinding.bind(itemView)

    @Inject
    lateinit var context: Context

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    @Inject
    lateinit var messageUtils: MessageUtils

    @Inject
    lateinit var appPreferences: AppPreferences

    @Inject
    lateinit var dateUtils: DateUtils

    @Inject
    lateinit var currentUserProvider: CurrentUserProviderNew

    lateinit var commonMessageInterface: CommonMessageInterface

    @Inject
    lateinit var chatRepository: ChatMessageRepository

    private var job: Job? = null

    override fun onBind(message: ChatMessage) {
        super.onBind(message)
        sharedApplication!!.componentApplication.inject(this)
        setAvatarAndAuthorOnMessageItem(message)
        colorizeMessageBubble(message)
        itemView.isSelected = false
        val user = currentUserProvider.currentUser.blockingGet()
        val hasCheckboxes = processCheckboxes(
            message,
            user
        )
        processMessage(message, hasCheckboxes)
    }

    private fun processMessage(message: ChatMessage, hasCheckboxes: Boolean) {
        var textSize = context.resources!!.getDimension(R.dimen.chat_text_size)
        if (!hasCheckboxes) {
            binding.messageText.visibility = View.VISIBLE
            binding.checkboxContainer.visibility = View.GONE
            var processedMessageText = messageUtils.enrichChatMessageText(
                binding.messageText.context,
                message,
                true,
                viewThemeUtils
            )

            val spansFromString: Array<Any> = processedMessageText!!.getSpans(
                0,
                processedMessageText.length,
                Any::class.java
            )

            if (spansFromString.isNotEmpty()) {
                binding.bubble.layoutParams.apply {
                    width = FlexboxLayout.LayoutParams.MATCH_PARENT
                }
                binding.messageText.layoutParams.apply {
                    width = FlexboxLayout.LayoutParams.MATCH_PARENT
                }
            } else {
                binding.bubble.layoutParams.apply {
                    width = FlexboxLayout.LayoutParams.WRAP_CONTENT
                }
                binding.messageText.layoutParams.apply {
                    width = FlexboxLayout.LayoutParams.WRAP_CONTENT
                }
            }

            processedMessageText = messageUtils.processMessageParameters(
                binding.messageText.context,
                viewThemeUtils,
                processedMessageText,
                message,
                itemView
            )
            val messageParameters = message.messageParameters
            if (
                (messageParameters == null || messageParameters.size <= 0) &&
                TextMatchers.isMessageWithSingleEmoticonOnly(message.text)
            ) {
                textSize = (textSize * TEXT_SIZE_MULTIPLIER).toFloat()
                itemView.isSelected = true
                binding.messageAuthor.visibility = View.GONE
            }
            binding.messageText.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)
            binding.messageText.text = processedMessageText
            // just for debugging:
            // binding.messageText.text =
            //     SpannableStringBuilder(processedMessageText).append(" (" + message.jsonMessageId + ")")
        } else {
            binding.checkboxContainer.visibility = View.VISIBLE
            binding.messageText.visibility = View.GONE
        }

        if (message.lastEditTimestamp != 0L && !message.isDeleted) {
            binding.messageEditIndicator.visibility = View.VISIBLE
            binding.messageTime.text = dateUtils.getLocalTimeStringFromTimestamp(message.lastEditTimestamp!!)
        } else {
            binding.messageEditIndicator.visibility = View.GONE
            binding.messageTime.text = dateUtils.getLocalTimeStringFromTimestamp(message.timestamp)
        }
        viewThemeUtils.platform.colorTextView(binding.messageTime, ColorRole.ON_SURFACE_VARIANT)

        // parent message handling
        val chatActivity = commonMessageInterface as ChatActivity
        binding.messageQuote.quotedChatMessageView.visibility =
            if (!message.isDeleted &&
                message.parentMessageId != null &&
                message.parentMessageId != chatActivity.conversationThreadId
            ) {
                processParentMessage(message)
                View.VISIBLE
            } else {
                View.GONE
            }

        binding.messageQuote.quotedChatMessageView.setOnLongClickListener { l: View? ->
            commonMessageInterface.onOpenMessageActionsDialog(message)
            true
        }

        itemView.setTag(R.string.replyable_message_view_tag, message.replyable)

        Thread().showThreadPreview(
            chatActivity,
            message,
            threadBinding = binding.threadTitleWrapper,
            reactionsBinding = binding.reactions,
            openThread = { openThread(message) }
        )

        Reaction().showReactions(
            message,
            ::clickOnReaction,
            ::longClickOnReaction,
            binding.reactions,
            binding.messageText.context,
            false,
            viewThemeUtils
        )
    }

    @Suppress("LongMethod")
    private fun processCheckboxes(chatMessage: ChatMessage, user: User): Boolean {
        val chatActivity = commonMessageInterface as ChatActivity
        val message = chatMessage.message ?: return false
        val checkBoxContainer = binding.checkboxContainer
        val isOlderThanTwentyFourHours = chatMessage
            .createdAt
            .before(Date(System.currentTimeMillis() - AGE_THRESHOLD_FOR_EDIT_MESSAGE))

        val messageIsEditable = hasSpreedFeatureCapability(
            user.capabilities?.spreedCapability!!,
            SpreedFeatures.EDIT_MESSAGES
        ) &&
            !isOlderThanTwentyFourHours

        checkBoxContainer.removeAllViews()
        val checkboxRegex = """- \[(X|x| )]\s*(.+)""".toRegex()
        val lines = message.lines()

        val checkboxList = mutableListOf<CheckBox>()
        var hasCheckbox = false

        lines.forEach { line ->
            val match = checkboxRegex.matchEntire(line.trim())
            if (match != null) {
                hasCheckbox = true
                val isChecked = match.groupValues[1].equals("X", true)
                val taskText = match.groupValues[2].trim()
                val checkBox = CheckBox(checkBoxContainer.context).apply {
                    val messageText = messageUtils.enrichChatMessageText(
                        context,
                        taskText,
                        true,
                        viewThemeUtils
                    )
                    text = messageUtils.processMessageParameters(
                        context,
                        viewThemeUtils,
                        messageText,
                        chatMessage,
                        null
                    )
                    this.isChecked = isChecked
                    this.isEnabled = (
                        chatMessage.actorType == "bots" ||
                            chatActivity.userAllowedByPrivilages(chatMessage)
                        ) &&
                        messageIsEditable
                    setTextColor(ContextCompat.getColor(context, R.color.no_emphasis_text))
                    setOnCheckedChangeListener { _, _ ->
                        updateCheckboxStates(chatMessage, user, checkboxList)
                    }
                }
                checkBoxContainer.addView(checkBox)
                checkboxList.add(checkBox)
                viewThemeUtils.platform.themeCheckbox(checkBox)
            } else if (line.isNotBlank()) {
                val textView = EmojiTextView(checkBoxContainer.context).apply {
                    val messageText = messageUtils.enrichChatMessageText(
                        context,
                        line,
                        true,
                        viewThemeUtils
                    )
                    text = messageUtils.processMessageParameters(
                        context,
                        viewThemeUtils,
                        messageText,
                        chatMessage,
                        null
                    )
                    viewThemeUtils.platform.colorTextView(this, ColorRole.ON_SURFACE_VARIANT)
                }
                checkBoxContainer.addView(textView)
            }
        }

        return hasCheckbox
    }

    private fun updateCheckboxStates(chatMessage: ChatMessage, user: User, checkboxes: List<CheckBox>) {
        job = CoroutineScope(Dispatchers.Main).launch {
            withContext(Dispatchers.IO) {
                val apiVersion: Int = ApiUtils.getChatApiVersion(
                    user.capabilities?.spreedCapability!!,
                    intArrayOf(1)
                )
                val updatedMessage = updateMessageWithCheckboxStates(chatMessage.message!!, checkboxes)
                chatRepository.editChatMessage(
                    user.getCredentials(),
                    ApiUtils.getUrlForChatMessage(apiVersion, user.baseUrl!!, chatMessage.token!!, chatMessage.id),
                    updatedMessage
                ).collect { result ->
                    withContext(Dispatchers.Main) {
                        if (result.isSuccess) {
                            val editedMessage = result.getOrNull()?.ocs?.data!!.parentMessage!!
                            Log.d(TAG, "EditedMessage: $editedMessage")
                            binding.messageEditIndicator.apply {
                                visibility = View.VISIBLE
                            }
                            binding.messageTime.text =
                                dateUtils.getLocalTimeStringFromTimestamp(editedMessage.lastEditTimestamp!!)
                        } else {
                            Snackbar.make(binding.root, R.string.nc_common_error_sorry, Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun updateMessageWithCheckboxStates(originalMessage: String, checkboxes: List<CheckBox>): String {
        val checkboxRegex = """- \[(X|x| )]\s*(.+)""".toRegex()
        var index = 0
        return originalMessage.lines().joinToString("\n") { line ->
            val match = checkboxRegex.matchEntire(line.trim())
            if (match != null) {
                val taskText = match.groupValues[2].trim()
                val state = if (checkboxes.getOrNull(index++)?.isChecked == true) "X" else " "
                "- [$state] $taskText"
            } else {
                line
            }
        }
    }

    private fun longClickOnReaction(chatMessage: ChatMessage) {
        commonMessageInterface.onLongClickReactions(chatMessage)
    }

    private fun clickOnReaction(chatMessage: ChatMessage, emoji: String) {
        commonMessageInterface.onClickReaction(chatMessage, emoji)
    }

    private fun openThread(chatMessage: ChatMessage) {
        commonMessageInterface.openThread(chatMessage)
    }

    private fun setAvatarAndAuthorOnMessageItem(message: ChatMessage) {
        val actorName = message.actorDisplayName
        if (!actorName.isNullOrBlank()) {
            binding.messageAuthor.visibility = View.VISIBLE
            binding.messageAuthor.text = actorName
            binding.messageUserAvatar.setOnClickListener {
                (payload as? MessagePayload)?.profileBottomSheet?.showFor(message, itemView.context)
            }
        } else {
            binding.messageAuthor.setText(R.string.nc_nick_guest)
        }

        if (!message.isGrouped && !message.isOneToOneConversation && !message.isFormerOneToOneConversation) {
            ChatMessageUtils().setAvatarOnMessage(binding.messageUserAvatar, message, viewThemeUtils)
        } else {
            if (message.isOneToOneConversation || message.isFormerOneToOneConversation) {
                binding.messageUserAvatar.visibility = View.GONE
            } else {
                binding.messageUserAvatar.visibility = View.INVISIBLE
            }
            binding.messageAuthor.visibility = View.GONE
        }
    }

    private fun colorizeMessageBubble(message: ChatMessage) {
        viewThemeUtils.talk.themeIncomingMessageBubble(bubble, message.isGrouped, message.isDeleted)
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

                    parentChatMessage.activeUser = message.activeUser
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
                    binding.messageQuote.quotedMessageAuthor.text =
                        if (parentChatMessage.actorDisplayName.isNullOrEmpty()) {
                            context.getText(R.string.nc_nick_guest)
                        } else {
                            parentChatMessage.actorDisplayName
                        }

                    binding.messageQuote.quotedMessage.text = messageUtils
                        .enrichChatReplyMessageText(
                            binding.messageQuote.quotedMessage.context,
                            parentChatMessage,
                            true,
                            viewThemeUtils
                        )

                    viewThemeUtils.talk.themeParentMessage(
                        parentChatMessage,
                        message,
                        binding.messageQuote.quotedChatMessageView,
                        R.color.high_emphasis_text
                    )

                    binding.messageQuote.quotedChatMessageView.setOnClickListener {
                        chatActivity.jumpToQuotedMessage(parentChatMessage)
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Error when processing parent message in view holder", e)
                }
            }
        }
    }

    fun assignCommonMessageInterface(commonMessageInterface: CommonMessageInterface) {
        this.commonMessageInterface = commonMessageInterface
    }

    override fun viewDetached() {
        super.viewDetached()
        job?.cancel()
    }

    companion object {
        const val TEXT_SIZE_MULTIPLIER = 2.5
        private val TAG = IncomingTextMessageViewHolder::class.java.simpleName
        private const val AGE_THRESHOLD_FOR_EDIT_MESSAGE: Long = 86400000
    }
}
