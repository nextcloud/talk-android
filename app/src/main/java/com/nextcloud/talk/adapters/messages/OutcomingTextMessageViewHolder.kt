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
import android.widget.CheckBox
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.text.toSpanned
import androidx.lifecycle.lifecycleScope
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
import com.nextcloud.talk.data.database.model.SendStatus
import com.nextcloud.talk.data.network.NetworkMonitor
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.ItemCustomOutcomingTextMessageBinding
import com.nextcloud.talk.models.json.chat.ReadStatus
import com.nextcloud.talk.models.json.conversations.ConversationEnums
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.CapabilitiesUtil.hasSpreedFeatureCapability
import com.nextcloud.talk.utils.DateUtils
import com.nextcloud.talk.utils.SpreedFeatures
import com.nextcloud.talk.utils.TextMatchers
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import com.nextcloud.talk.utils.message.MessageUtils
import com.stfalcon.chatkit.messages.MessageHolders.OutcomingTextMessageViewHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
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

    @Inject
    lateinit var chatRepository: ChatMessageRepository

    @Inject
    lateinit var currentUserProvider: CurrentUserProviderNew

    private var job: Job? = null

    @Suppress("Detekt.LongMethod")
    override fun onBind(message: ChatMessage) {
        super.onBind(message)
        sharedApplication!!.componentApplication.inject(this)
        val user = currentUserProvider.currentUser.blockingGet()
        val hasCheckboxes = processCheckboxes(
            message,
            user
        )
        processMessage(message, hasCheckboxes)
    }

    @Suppress("Detekt.LongMethod")
    private fun processMessage(message: ChatMessage, hasCheckboxes: Boolean) {
        var isBubbled = true
        val layoutParams = binding.messageTime.layoutParams as FlexboxLayout.LayoutParams
        var textSize = context.resources.getDimension(R.dimen.chat_text_size)
        if (!hasCheckboxes) {
            realView.isSelected = false
            layoutParams.isWrapBefore = false

            binding.messageText.visibility = View.VISIBLE
            binding.checkboxContainer.visibility = View.GONE

            var processedMessageText = messageUtils.enrichChatMessageText(
                binding.messageText.context,
                message,
                false,
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

            if (
                (message.messageParameters == null || message.messageParameters!!.size <= 0) &&
                TextMatchers.isMessageWithSingleEmoticonOnly(message.text)
            ) {
                textSize = (textSize * TEXT_SIZE_MULTIPLIER).toFloat()
                layoutParams.isWrapBefore = true
                realView.isSelected = true
                isBubbled = false
            }

            binding.messageTime.layoutParams = layoutParams
            viewThemeUtils.platform.colorTextView(binding.messageText, ColorRole.ON_SURFACE_VARIANT)
            binding.messageText.text = processedMessageText
            // just for debugging:
            // binding.messageText.text =
            //     SpannableStringBuilder(processedMessageText).append(" (" + message.jsonMessageId + ")")
        } else {
            binding.messageText.visibility = View.GONE
            binding.checkboxContainer.visibility = View.VISIBLE
        }
        binding.messageText.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)
        if (message.lastEditTimestamp != 0L && !message.isDeleted) {
            binding.messageEditIndicator.visibility = View.VISIBLE
            binding.messageTime.text = dateUtils.getLocalTimeStringFromTimestamp(message.lastEditTimestamp!!)
        } else {
            binding.messageEditIndicator.visibility = View.GONE
            binding.messageTime.text = dateUtils.getLocalTimeStringFromTimestamp(message.timestamp)
        }
        viewThemeUtils.platform.colorTextView(binding.messageTime, ColorRole.ON_SURFACE_VARIANT)
        setBubbleOnChatMessage(message)

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

        binding.checkMark.visibility = View.INVISIBLE
        binding.sendingProgress.visibility = View.GONE

        if (message.sendStatus == SendStatus.FAILED) {
            updateStatus(R.drawable.baseline_error_outline_24, context.resources?.getString(R.string.nc_message_failed))
        } else if (message.isTemporary) {
            updateStatus(R.drawable.baseline_schedule_24, context.resources?.getString(R.string.nc_message_sending))
        } else if (message.readStatus == ReadStatus.READ) {
            updateStatus(R.drawable.ic_check_all, context.resources?.getString(R.string.nc_message_read))
        } else if (message.readStatus == ReadStatus.SENT) {
            updateStatus(R.drawable.ic_check, context.resources?.getString(R.string.nc_message_sent))
        }

        chatActivity.lifecycleScope.launch {
            if (message.isTemporary && !networkMonitor.isOnline.value) {
                updateStatus(
                    R.drawable.ic_signal_wifi_off_white_24dp,
                    context.resources?.getString(R.string.nc_message_offline)
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
            viewThemeUtils,
            isBubbled
        )
    }

    private fun processCheckboxes(chatMessage: ChatMessage, user: User): Boolean {
        val chatActivity = commonMessageInterface as ChatActivity
        val message = chatMessage.message!!.toSpanned()
        val messageTextView = binding.messageText
        val checkBoxContainer = binding.checkboxContainer
        val isOlderThanTwentyFourHours = chatMessage
            .createdAt
            .before(Date(System.currentTimeMillis() - AGE_THRESHOLD_FOR_EDIT_MESSAGE))
        val messageIsEditable = hasSpreedFeatureCapability(
            user.capabilities?.spreedCapability!!,
            SpreedFeatures.EDIT_MESSAGES
        ) &&
            !isOlderThanTwentyFourHours

        val isNoTimeLimitOnNoteToSelf = hasSpreedFeatureCapability(
            user.capabilities?.spreedCapability!!,
            SpreedFeatures
                .EDIT_MESSAGES_NOTE_TO_SELF
        ) &&
            chatActivity.currentConversation?.type == ConversationEnums.ConversationType.NOTE_TO_SELF

        checkBoxContainer.removeAllViews()
        val regex = """(- \[(X|x| )])\s*(.+)""".toRegex(RegexOption.MULTILINE)
        val matches = regex.findAll(message)

        if (matches.none()) return false

        val firstPart = message.toString().substringBefore("\n- [")
        messageTextView.text = messageUtils.enrichChatMessageText(
            binding.messageText.context,
            firstPart,
            true,
            viewThemeUtils
        )

        val checkboxList = mutableListOf<CheckBox>()

        matches.forEach { matchResult ->
            val isChecked = matchResult.groupValues[CHECKED_GROUP_INDEX] == "X" ||
                matchResult.groupValues[CHECKED_GROUP_INDEX] == "x"
            val taskText = matchResult.groupValues[TASK_TEXT_GROUP_INDEX].trim()

            val checkBox = CheckBox(checkBoxContainer.context).apply {
                text = taskText
                this.isChecked = isChecked
                this.isEnabled = messageIsEditable || isNoTimeLimitOnNoteToSelf

                setTextColor(ContextCompat.getColor(context, R.color.no_emphasis_text))

                setOnCheckedChangeListener { _, _ ->
                    updateCheckboxStates(chatMessage, user, checkboxList)
                }
            }
            checkBoxContainer.addView(checkBox)
            checkboxList.add(checkBox)
            viewThemeUtils.platform.themeCheckbox(checkBox)
        }
        return true
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
        var updatedMessage = originalMessage
        val regex = """(- \[(X|x| )])\s*(.+)""".toRegex(RegexOption.MULTILINE)

        checkboxes.forEach { _ ->
            updatedMessage = regex.replace(updatedMessage) { matchResult ->
                val taskText = matchResult.groupValues[TASK_TEXT_GROUP_INDEX].trim()
                val checkboxState = if (checkboxes.find { it.text == taskText }?.isChecked == true) "X" else " "
                "- [$checkboxState] $taskText"
            }
        }
        return updatedMessage
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

    override fun viewDetached() {
        super.viewDetached()
        job?.cancel()
    }

    companion object {
        const val TEXT_SIZE_MULTIPLIER = 2.5
        private val TAG = OutcomingTextMessageViewHolder::class.java.simpleName
        private const val CHECKED_GROUP_INDEX = 2
        private const val TASK_TEXT_GROUP_INDEX = 3
        private const val AGE_THRESHOLD_FOR_EDIT_MESSAGE: Long = 86400000
    }
}
