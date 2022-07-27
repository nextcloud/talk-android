/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Marcel Hibbe
 * Copyright (C) 2017-2018 Mario Danic <mario@lovelyhq.com>
 * Copyright (C) 2021 Marcel Hibbe <dev@mhibbe.de>
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

package com.nextcloud.talk.adapters.messages

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PorterDuff
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.SeekBar
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.work.WorkInfo
import androidx.work.WorkManager
import autodagger.AutoInjector
import coil.load
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.databinding.ItemCustomOutcomingVoiceMessageBinding
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.models.json.chat.ReadStatus
import com.nextcloud.talk.ui.theme.ServerTheme
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.preferences.AppPreferences
import com.stfalcon.chatkit.messages.MessageHolders
import java.util.concurrent.ExecutionException
import javax.inject.Inject
import kotlin.math.roundToInt

@AutoInjector(NextcloudTalkApplication::class)
class OutcomingVoiceMessageViewHolder(outcomingView: View) : MessageHolders
.OutcomingTextMessageViewHolder<ChatMessage>(outcomingView) {

    private val binding: ItemCustomOutcomingVoiceMessageBinding =
        ItemCustomOutcomingVoiceMessageBinding.bind(itemView)

    @JvmField
    @Inject
    var context: Context? = null

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    @Inject
    lateinit var serverTheme: ServerTheme

    @JvmField
    @Inject
    var appPreferences: AppPreferences? = null

    lateinit var message: ChatMessage

    lateinit var handler: Handler

    lateinit var voiceMessageInterface: VoiceMessageInterface
    lateinit var reactionsInterface: ReactionsInterface

    @SuppressLint("SetTextI18n")
    override fun onBind(message: ChatMessage) {
        super.onBind(message)
        this.message = message
        sharedApplication!!.componentApplication.inject(this)

        colorizeMessageBubble(message)

        itemView.isSelected = false
        binding.messageTime.setTextColor(context!!.resources.getColor(R.color.white60))

        // parent message handling
        setParentMessageDataOnMessageItem(message)

        updateDownloadState(message)
        binding.seekbar.max = message.voiceMessageDuration

        handleIsPlayingVoiceMessageState(message)

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

        val readStatusDrawableInt = when (message.readStatus) {
            ReadStatus.READ -> R.drawable.ic_check_all
            ReadStatus.SENT -> R.drawable.ic_check
            else -> null
        }

        val readStatusContentDescriptionString = when (message.readStatus) {
            ReadStatus.READ -> context?.resources?.getString(R.string.nc_message_read)
            ReadStatus.SENT -> context?.resources?.getString(R.string.nc_message_sent)
            else -> null
        }

        readStatusDrawableInt?.let { drawableInt ->
            AppCompatResources.getDrawable(context!!, drawableInt)?.let {
                it.setColorFilter(context?.resources!!.getColor(R.color.white60), PorterDuff.Mode.SRC_ATOP)
                binding.checkMark.setImageDrawable(it)
            }
        }

        binding.checkMark.setContentDescription(readStatusContentDescriptionString)

        Reaction().showReactions(message, binding.reactions, binding.messageTime.context, true)
        binding.reactions.reactionsEmojiWrapper.setOnClickListener {
            reactionsInterface.onClickReactions(message)
        }
        binding.reactions.reactionsEmojiWrapper.setOnLongClickListener { l: View? ->
            reactionsInterface.onLongClickReactions(message)
            true
        }
    }

    private fun handleResetVoiceMessageState(message: ChatMessage) {
        if (message.resetVoiceMessage) {
            binding.playPauseBtn.visibility = View.VISIBLE
            binding.playPauseBtn.icon = ContextCompat.getDrawable(
                context!!,
                R.drawable.ic_baseline_play_arrow_voice_message_24
            )
            binding.seekbar.progress = SEEKBAR_START
            message.resetVoiceMessage = false
        }
    }

    private fun handleIsDownloadingVoiceMessageState(message: ChatMessage) {
        if (message.isDownloadingVoiceMessage) {
            showVoiceMessageLoading()
        } else {
            binding.progressBar.visibility = View.GONE
        }
    }

    private fun handleIsPlayingVoiceMessageState(message: ChatMessage) {
        if (message.isPlayingVoiceMessage) {
            showPlayButton()
            binding.playPauseBtn.icon = ContextCompat.getDrawable(
                context!!,
                R.drawable.ic_baseline_pause_voice_message_24
            )
            binding.seekbar.progress = message.voiceMessagePlayedSeconds
        } else {
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

    private fun setParentMessageDataOnMessageItem(message: ChatMessage) {
        if (!message.isDeleted && message.parentMessage != null) {
            val parentChatMessage = message.parentMessage
            parentChatMessage!!.activeUser = message.activeUser
            parentChatMessage.imageUrl?.let {
                binding.messageQuote.quotedMessageImage.visibility = View.VISIBLE
                binding.messageQuote.quotedMessageImage.load(it) {
                    addHeader(
                        "Authorization",
                        ApiUtils.getCredentials(message.activeUser!!.username, message.activeUser!!.token)
                    )
                }
            } ?: run {
                binding.messageQuote.quotedMessageImage.visibility = View.GONE
            }
            binding.messageQuote.quotedMessageAuthor.text = parentChatMessage.actorDisplayName
                ?: context!!.getText(R.string.nc_nick_guest)
            binding.messageQuote.quotedMessage.text = parentChatMessage.text
            binding.messageQuote.quotedMessage.setTextColor(serverTheme.colorText)
            binding.messageQuote.quotedMessageAuthor.setTextColor(
                ColorUtils.setAlphaComponent(serverTheme.colorText,
                    ALPHA_80_INT
                )
            )

            binding.messageQuote.quoteColoredView.setBackgroundColor(serverTheme.colorText)

            binding.messageQuote.quotedChatMessageView.visibility = View.VISIBLE
        } else {
            binding.messageQuote.quotedChatMessageView.visibility = View.GONE
        }
    }

    private fun colorizeMessageBubble(message: ChatMessage) {
        val resources = sharedApplication!!.resources
        val elementColor = viewThemeUtils.getElementColor(binding.root.context)
        val bgBubbleColor = if (message.isDeleted) {
            ColorUtils.setAlphaComponent(elementColor, HALF_ALPHA_INT)
        } else {
            elementColor
        }
        if (message.isGrouped) {
            val bubbleDrawable = DisplayUtils.getMessageSelector(
                bgBubbleColor,
                ResourcesCompat.getColor(resources, R.color.transparent, null),
                bgBubbleColor,
                R.drawable.shape_grouped_outcoming_message
            )
            ViewCompat.setBackground(bubble, bubbleDrawable)
        } else {
            val bubbleDrawable = DisplayUtils.getMessageSelector(
                bgBubbleColor,
                ResourcesCompat.getColor(resources, R.color.transparent, null),
                bgBubbleColor,
                R.drawable.shape_outcoming_message
            )
            ViewCompat.setBackground(bubble, bubbleDrawable)
        }
    }

    fun assignVoiceMessageInterface(voiceMessageInterface: VoiceMessageInterface) {
        this.voiceMessageInterface = voiceMessageInterface
    }

    fun assignReactionInterface(reactionsInterface: ReactionsInterface) {
        this.reactionsInterface = reactionsInterface
    }

    companion object {
        private const val TAG = "VoiceOutMessageView"
        private const val SEEKBAR_START: Int = 0
        private const val HALF_ALPHA_INT: Int = 255 / 2
        private val ALPHA_80_INT: Int = (255 * 0.8).roundToInt()
    }
}
