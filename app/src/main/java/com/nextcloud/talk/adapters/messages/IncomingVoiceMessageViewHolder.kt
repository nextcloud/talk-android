/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Christian Reiner <foss@christian-reiner.info>
 * SPDX-FileCopyrightText: 2021 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2021 Tim Krüger <t@timkrueger.me>
 * SPDX-FileCopyrightText: 2021 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2017-2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.adapters.messages

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.View
import android.widget.SeekBar
import androidx.core.content.ContextCompat
import androidx.work.WorkInfo
import androidx.work.WorkManager
import autodagger.AutoInjector
import coil.load
import com.nextcloud.android.common.ui.theme.utils.ColorRole
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.chat.ChatActivity
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.databinding.ItemCustomIncomingVoiceMessageBinding
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.ChatMessageUtils
import com.nextcloud.talk.utils.DateUtils
import com.nextcloud.talk.utils.message.MessageUtils
import com.nextcloud.talk.utils.preferences.AppPreferences
import com.stfalcon.chatkit.messages.MessageHolders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutionException
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class IncomingVoiceMessageViewHolder(incomingView: View, payload: Any) :
    MessageHolders.IncomingTextMessageViewHolder<ChatMessage>(incomingView, payload) {

    private val binding: ItemCustomIncomingVoiceMessageBinding = ItemCustomIncomingVoiceMessageBinding.bind(itemView)

    @JvmField
    @Inject
    var context: Context? = null

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    @Inject
    lateinit var messageUtils: MessageUtils

    @Inject
    lateinit var dateUtils: DateUtils

    @Inject
    lateinit var appPreferences: AppPreferences

    lateinit var message: ChatMessage

    lateinit var voiceMessageInterface: VoiceMessageInterface
    lateinit var commonMessageInterface: CommonMessageInterface
    private var isBound = false

    @SuppressLint("SetTextI18n")
    override fun onBind(message: ChatMessage) {
        super.onBind(message)
        if (isBound) {
            handleIsPlayingVoiceMessageState(message)
            return
        }

        this.message = message
        sharedApplication!!.componentApplication.inject(this)

        val filename = message.selectedIndividualHashMap!!["name"]
        val retrieved = appPreferences.getWaveFormFromFile(filename)
        if (retrieved.isNotEmpty() &&
            message.voiceMessageFloatArray == null ||
            message.voiceMessageFloatArray?.isEmpty() == true
        ) {
            message.voiceMessageFloatArray = retrieved.toFloatArray()
            binding.seekbar.setWaveData(message.voiceMessageFloatArray!!)
        }
        binding.messageTime.text = dateUtils.getLocalTimeStringFromTimestamp(message.timestamp)

        setAvatarAndAuthorOnMessageItem(message)

        colorizeMessageBubble(message)

        itemView.isSelected = false

        // parent message handling
        setParentMessageDataOnMessageItem(message)

        updateDownloadState(message)
        binding.seekbar.max = MAX
        viewThemeUtils.talk.themeWaveFormSeekBar(binding.seekbar)
        viewThemeUtils.platform.colorCircularProgressBar(binding.progressBar, ColorRole.ON_SURFACE_VARIANT)

        showVoiceMessageDuration(message)
        if (message.isDownloadingVoiceMessage) {
            showVoiceMessageLoading()
        } else {
            if (message.voiceMessageFloatArray == null || message.voiceMessageFloatArray!!.isEmpty()) {
                binding.seekbar.setWaveData(FloatArray(0))
            } else {
                binding.seekbar.setWaveData(message.voiceMessageFloatArray!!)
            }
            binding.progressBar.visibility = View.GONE
        }

        if (message.resetVoiceMessage) {
            resetVoiceMessage(message)
        }

        binding.seekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                // unused atm
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                // unused atm
            }

            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    voiceMessageInterface.updateMediaPlayerProgressBySlider(message, progress)
                }
            }
        })

        CoroutineScope(Dispatchers.Default).launch {
            (voiceMessageInterface as ChatActivity).chatViewModel.voiceMessagePlayBackUIFlow.onEach { speed ->
                withContext(Dispatchers.Main) {
                    binding.playbackSpeedControlBtn.setSpeed(speed)
                }
            }.collect()
        }

        binding.playbackSpeedControlBtn.setSpeed(appPreferences.getPreferredPlayback(message.actorId))

        Reaction().showReactions(
            message,
            ::clickOnReaction,
            ::longClickOnReaction,
            binding.reactions,
            binding.messageTime.context,
            false,
            viewThemeUtils
        )

        isBound = true
    }

    private fun showVoiceMessageDuration(message: ChatMessage) {
        if (message.voiceMessageDuration > 0) {
            binding.voiceMessageDuration.visibility = View.VISIBLE
        } else {
            binding.voiceMessageDuration.visibility = View.INVISIBLE
        }
    }

    private fun resetVoiceMessage(chatMessage: ChatMessage) {
        binding.playPauseBtn.visibility = View.VISIBLE
        binding.playPauseBtn.icon = ContextCompat.getDrawable(
            context!!,
            R.drawable.ic_baseline_play_arrow_voice_message_24
        )
        binding.seekbar.progress = SEEKBAR_START
        chatMessage.resetVoiceMessage = false
        chatMessage.voiceMessagePlayedSeconds = 0
        showVoiceMessageDuration(message)
    }

    private fun longClickOnReaction(chatMessage: ChatMessage) {
        commonMessageInterface.onLongClickReactions(chatMessage)
    }

    private fun clickOnReaction(chatMessage: ChatMessage, emoji: String) {
        commonMessageInterface.onClickReaction(chatMessage, emoji)
    }

    private fun handleIsPlayingVoiceMessageState(message: ChatMessage) {
        colorizeMessageBubble(message)
        if (message.isPlayingVoiceMessage) {
            showPlayButton()
            binding.playPauseBtn.icon = ContextCompat.getDrawable(
                context!!,
                R.drawable.ic_baseline_pause_voice_message_24
            )

            val d = message.voiceMessageDuration.toLong()
            val t = message.voiceMessagePlayedSeconds.toLong()
            binding.voiceMessageDuration.text = android.text.format.DateUtils.formatElapsedTime(d - t)
            binding.voiceMessageDuration.visibility = View.VISIBLE
            binding.seekbar.progress = message.voiceMessageSeekbarProgress
        } else {
            showVoiceMessageDuration(message)
            binding.playPauseBtn.visibility = View.VISIBLE
            binding.playPauseBtn.icon = ContextCompat.getDrawable(
                context!!,
                R.drawable.ic_baseline_play_arrow_voice_message_24
            )
        }
    }

    private fun updateDownloadState(message: ChatMessage) {
        // check if download worker is already running
        val fileId = message.selectedIndividualHashMap!!["id"]
        val workers = WorkManager.getInstance(context!!).getWorkInfosByTag(fileId!!)

        try {
            for (workInfo in workers.get()) {
                if (workInfo.state == WorkInfo.State.RUNNING || workInfo.state == WorkInfo.State.ENQUEUED) {
                    showVoiceMessageLoading()
                    WorkManager.getInstance(context!!).getWorkInfoByIdLiveData(workInfo.id)
                        .observeForever { info: WorkInfo? ->
                            showStatus(info)
                        }
                }
            }
        } catch (e: ExecutionException) {
            Log.e(TAG, "Error when checking if worker already exists", e)
        } catch (e: InterruptedException) {
            Log.e(TAG, "Error when checking if worker already exists", e)
        }
    }

    private fun showStatus(info: WorkInfo?) {
        if (info != null) {
            when (info.state) {
                WorkInfo.State.RUNNING -> {
                    Log.d(TAG, "WorkInfo.State.RUNNING in ViewHolder")
                    showVoiceMessageLoading()
                }

                WorkInfo.State.SUCCEEDED -> {
                    Log.d(TAG, "WorkInfo.State.SUCCEEDED in ViewHolder")
                    showPlayButton()
                }

                WorkInfo.State.FAILED -> {
                    Log.d(TAG, "WorkInfo.State.FAILED in ViewHolder")
                    showPlayButton()
                }

                else -> {
                }
            }
        }
    }

    private fun showPlayButton() {
        binding.playPauseBtn.visibility = View.VISIBLE
        binding.progressBar.visibility = View.GONE
    }

    private fun showVoiceMessageLoading() {
        binding.playPauseBtn.visibility = View.GONE
        binding.progressBar.visibility = View.VISIBLE
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
        viewThemeUtils.talk.themeIncomingMessageBubble(
            bubble,
            message.isGrouped,
            message.isDeleted,
            message.wasPlayedVoiceMessage
        )
    }

    @Suppress("Detekt.TooGenericExceptionCaught", "Detekt.LongMethod")
    private fun setParentMessageDataOnMessageItem(message: ChatMessage) {
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
                        ?: context!!.getText(R.string.nc_nick_guest)
                    binding.messageQuote.quotedMessage.text = messageUtils
                        .enrichChatReplyMessageText(
                            binding.messageQuote.quotedMessage.context,
                            parentChatMessage,
                            true,
                            viewThemeUtils
                        )

                    binding.messageQuote.quotedMessageAuthor
                        .setTextColor(ContextCompat.getColor(context!!, R.color.textColorMaxContrast))

                    viewThemeUtils.talk.themeParentMessage(
                        parentChatMessage,
                        message,
                        binding.messageQuote.quoteColoredView
                    )

                    binding.messageQuote.quotedChatMessageView.visibility = View.VISIBLE

                    binding.messageQuote.quotedChatMessageView.visibility =
                        if (!message.isDeleted &&
                            message.parentMessageId != null &&
                            message.parentMessageId != chatActivity.conversationThreadId
                        ) {
                            View.VISIBLE
                        } else {
                            View.GONE
                        }
                } catch (e: Exception) {
                    Log.d(TAG, "Error when processing parent message in view holder", e)
                }
            }
        } else {
            binding.messageQuote.quotedChatMessageView.visibility = View.GONE
        }
    }

    fun assignVoiceMessageInterface(voiceMessageInterface: VoiceMessageInterface) {
        this.voiceMessageInterface = voiceMessageInterface
    }

    fun assignCommonMessageInterface(commonMessageInterface: CommonMessageInterface) {
        this.commonMessageInterface = commonMessageInterface
    }

    companion object {
        private const val TAG = "VoiceInMessageView"
        private const val SEEKBAR_START: Int = 0
        private const val MAX: Int = 100
    }
}
