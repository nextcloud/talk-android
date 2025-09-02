/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Christian Reiner <foss@christian-reiner.info>
 * SPDX-FileCopyrightText: 2023 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2023 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-FileCopyrightText: 2021 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2017-2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.adapters.messages

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.SeekBar
import androidx.appcompat.content.res.AppCompatResources
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
import com.nextcloud.talk.databinding.ItemCustomOutcomingVoiceMessageBinding
import com.nextcloud.talk.models.json.chat.ReadStatus
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.ApiUtils
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
@Suppress("Detekt.TooManyFunctions")
class OutcomingVoiceMessageViewHolder(outcomingView: View) :
    MessageHolders.OutcomingTextMessageViewHolder<ChatMessage>(outcomingView),
    AdjustableMessageHolderInterface {

    override val binding: ItemCustomOutcomingVoiceMessageBinding = ItemCustomOutcomingVoiceMessageBinding.bind(itemView)

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

    lateinit var handler: Handler

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
        viewThemeUtils.platform.colorTextView(binding.messageTime, ColorRole.ON_SURFACE_VARIANT)

        val filename = message.selectedIndividualHashMap!!["name"]
        val retrieved = appPreferences.getWaveFormFromFile(filename)
        if (retrieved.isNotEmpty() &&
            message.voiceMessageFloatArray == null ||
            message.voiceMessageFloatArray?.isEmpty() == true
        ) {
            message.voiceMessageFloatArray = retrieved.toFloatArray()
            binding.seekbar.setWaveData(message.voiceMessageFloatArray!!)
        }

        binding.seekbar.max = MAX
        binding.messageTime.text = dateUtils.getLocalTimeStringFromTimestamp(message.timestamp)

        colorizeMessageBubble(message)

        itemView.isSelected = false

        // parent message handling
        setParentMessageDataOnMessageItem(message)

        updateDownloadState(message)
        viewThemeUtils.talk.themeWaveFormSeekBar(binding.seekbar)
        viewThemeUtils.platform.colorCircularProgressBar(binding.progressBar, ColorRole.ON_SURFACE_VARIANT)

        showVoiceMessageDuration(message)

        handleIsDownloadingVoiceMessageState(message)

        handleResetVoiceMessageState(message)

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

        setReadStatus(message.readStatus)

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
            true,
            viewThemeUtils
        )
        isBound = true
    }

    private fun setReadStatus(readStatus: Enum<ReadStatus>) {
        val readStatusDrawableInt = when (readStatus) {
            ReadStatus.READ -> R.drawable.ic_check_all
            ReadStatus.SENT -> R.drawable.ic_check
            else -> null
        }

        val readStatusContentDescriptionString = when (readStatus) {
            ReadStatus.READ -> context?.resources?.getString(R.string.nc_message_read)
            ReadStatus.SENT -> context?.resources?.getString(R.string.nc_message_sent)
            else -> null
        }

        readStatusDrawableInt?.let { drawableInt ->
            AppCompatResources.getDrawable(context!!, drawableInt)?.let {
                binding.checkMark.setImageDrawable(it)
                viewThemeUtils.talk.themeMessageCheckMark(binding.checkMark)
            }
        }

        binding.checkMark.contentDescription = readStatusContentDescriptionString
    }

    private fun longClickOnReaction(chatMessage: ChatMessage) {
        commonMessageInterface.onLongClickReactions(chatMessage)
    }

    private fun clickOnReaction(chatMessage: ChatMessage, emoji: String) {
        commonMessageInterface.onClickReaction(chatMessage, emoji)
    }

    private fun handleResetVoiceMessageState(message: ChatMessage) {
        if (message.resetVoiceMessage) {
            binding.playPauseBtn.visibility = View.VISIBLE
            binding.playPauseBtn.icon = ContextCompat.getDrawable(
                context!!,
                R.drawable.ic_baseline_play_arrow_voice_message_24
            )
            binding.seekbar.progress = SEEKBAR_START
            message.voiceMessagePlayedSeconds = 0
            showVoiceMessageDuration(message)
            message.resetVoiceMessage = false
        }
    }

    private fun showVoiceMessageDuration(message: ChatMessage) {
        if (message.voiceMessageDuration > 0) {
            binding.voiceMessageDuration.visibility = View.VISIBLE
        } else {
            binding.voiceMessageDuration.visibility = View.INVISIBLE
        }
    }

    private fun handleIsDownloadingVoiceMessageState(message: ChatMessage) {
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
                            updateDownloadState(info)
                        }
                }
            }
        } catch (e: ExecutionException) {
            Log.e(TAG, "Error when checking if worker already exists", e)
        } catch (e: InterruptedException) {
            Log.e(TAG, "Error when checking if worker already exists", e)
        }
    }

    private fun updateDownloadState(info: WorkInfo?) {
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
                    Log.d(TAG, "WorkInfo.State unused in ViewHolder")
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
                            false,
                            viewThemeUtils
                        )
                    viewThemeUtils.talk.colorOutgoingQuoteText(binding.messageQuote.quotedMessage)
                    viewThemeUtils.talk.colorOutgoingQuoteAuthorText(binding.messageQuote.quotedMessageAuthor)
                    viewThemeUtils.talk.colorOutgoingQuoteBackground(binding.messageQuote.quoteColoredView)

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

    private fun colorizeMessageBubble(message: ChatMessage) {
        viewThemeUtils.talk.themeOutgoingMessageBubble(
            bubble,
            message.isGrouped,
            message.isDeleted,
            message.wasPlayedVoiceMessage
        )
    }

    fun assignVoiceMessageInterface(voiceMessageInterface: VoiceMessageInterface) {
        this.voiceMessageInterface = voiceMessageInterface
    }

    fun assignCommonMessageInterface(commonMessageInterface: CommonMessageInterface) {
        this.commonMessageInterface = commonMessageInterface
    }

    companion object {
        private const val TAG = "VoiceOutMessageView"
        private const val SEEKBAR_START: Int = 0
        private const val MAX = 100
    }
}
