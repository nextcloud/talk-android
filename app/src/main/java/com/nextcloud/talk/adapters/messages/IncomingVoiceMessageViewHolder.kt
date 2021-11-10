/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Marcel Hibbe
 * @author Andy Scherzinger
 * @author Tim Krüger
 * Copyright (C) 2021 Tim Krüger <t@timkrueger.me>
 * Copyright (C) 2021 Andy Scherzinger <info@andy-scherzinger.de>
 * Copyright (C) 2021 Marcel Hibbe <dev@mhibbe.de>
 * Copyright (C) 2017-2018 Mario Danic <mario@lovelyhq.com>
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
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.SeekBar
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.work.WorkInfo
import androidx.work.WorkManager
import autodagger.AutoInjector
import coil.load
import com.amulyakhare.textdrawable.TextDrawable
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.databinding.ItemCustomIncomingVoiceMessageBinding
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.ui.bottom.sheet.ProfileBottomSheet
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.preferences.AppPreferences
import com.stfalcon.chatkit.messages.MessageHolders
import java.util.concurrent.ExecutionException
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class IncomingVoiceMessageViewHolder(incomingView: View, payload: Any) : MessageHolders
.IncomingTextMessageViewHolder<ChatMessage>(incomingView, payload) {

    private val binding: ItemCustomIncomingVoiceMessageBinding =
        ItemCustomIncomingVoiceMessageBinding.bind(itemView)

    @JvmField
    @Inject
    var context: Context? = null

    @JvmField
    @Inject
    var appPreferences: AppPreferences? = null

    lateinit var message: ChatMessage

    lateinit var voiceMessageInterface: VoiceMessageInterface

    @SuppressLint("SetTextI18n")
    override fun onBind(message: ChatMessage) {
        super.onBind(message)
        this.message = message
        sharedApplication!!.componentApplication.inject(this)

        setAvatarAndAuthorOnMessageItem(message)

        colorizeMessageBubble(message)

        itemView.isSelected = false
        binding.messageTime.setTextColor(ResourcesCompat.getColor(context?.resources!!, R.color.warm_grey_four, null))

        // parent message handling
        setParentMessageDataOnMessageItem(message)

        updateDownloadState(message)
        binding.seekbar.max = message.voiceMessageDuration

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

        if (message.isDownloadingVoiceMessage) {
            showVoiceMessageLoading()
        } else {
            binding.progressBar.visibility = View.GONE
        }

        if (message.resetVoiceMessage) {
            binding.playPauseBtn.visibility = View.VISIBLE
            binding.playPauseBtn.icon = ContextCompat.getDrawable(
                context!!,
                R.drawable.ic_baseline_play_arrow_voice_message_24
            )
            binding.seekbar.progress = SEEKBAR_START
            message.resetVoiceMessage = false
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
    }

    private fun updateDownloadState(message: ChatMessage) {
        // check if download worker is already running
        val fileId = message.getSelectedIndividualHashMap()["id"]
        val workers = WorkManager.getInstance(context!!).getWorkInfosByTag(fileId!!)

        try {
            for (workInfo in workers.get()) {
                if (workInfo.state == WorkInfo.State.RUNNING || workInfo.state == WorkInfo.State.ENQUEUED) {
                    showVoiceMessageLoading()
                    WorkManager.getInstance(context!!).getWorkInfoByIdLiveData(workInfo.id)
                        .observeForever { info: WorkInfo? ->
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
                }
            }
        } catch (e: ExecutionException) {
            Log.e(TAG, "Error when checking if worker already exists", e)
        } catch (e: InterruptedException) {
            Log.e(TAG, "Error when checking if worker already exists", e)
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
        val author: String = message.actorDisplayName
        if (!TextUtils.isEmpty(author)) {
            binding.messageAuthor.text = author
            binding.messageUserAvatar.setOnClickListener {
                (payload as? ProfileBottomSheet)?.showFor(message.actorId, itemView.context)
            }
        } else {
            binding.messageAuthor.setText(R.string.nc_nick_guest)
        }

        if (!message.isGrouped && !message.isOneToOneConversation) {
            binding.messageUserAvatar.visibility = View.VISIBLE
            if (message.actorType == "guests") {
                // do nothing, avatar is set
            } else if (message.actorType == "bots" && message.actorId == "changelog") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val layers = arrayOfNulls<Drawable>(2)
                    layers[0] = ContextCompat.getDrawable(context!!, R.drawable.ic_launcher_background)
                    layers[1] = ContextCompat.getDrawable(context!!, R.drawable.ic_launcher_foreground)
                    val layerDrawable = LayerDrawable(layers)
                    binding.messageUserAvatar.setImageDrawable(DisplayUtils.getRoundedDrawable(layerDrawable))
                } else {
                    binding.messageUserAvatar.setImageResource(R.mipmap.ic_launcher)
                }
            } else if (message.actorType == "bots") {
                val drawable = TextDrawable.builder()
                    .beginConfig()
                    .bold()
                    .endConfig()
                    .buildRound(
                        ">",
                        ResourcesCompat.getColor(context!!.resources, R.color.black, null)
                    )
                binding.messageUserAvatar.visibility = View.VISIBLE
                binding.messageUserAvatar.setImageDrawable(drawable)
            }
        } else {
            if (message.isOneToOneConversation) {
                binding.messageUserAvatar.visibility = View.GONE
            } else {
                binding.messageUserAvatar.visibility = View.INVISIBLE
            }
            binding.messageAuthor.visibility = View.GONE
        }
    }

    private fun colorizeMessageBubble(message: ChatMessage) {
        val resources = itemView.resources

        var bubbleResource = R.drawable.shape_incoming_message

        if (message.isGrouped) {
            bubbleResource = R.drawable.shape_grouped_incoming_message
        }

        val bgBubbleColor = if (message.isDeleted) {
            ResourcesCompat.getColor(resources, R.color.bg_message_list_incoming_bubble_deleted, null)
        } else {
            ResourcesCompat.getColor(resources, R.color.bg_message_list_incoming_bubble, null)
        }
        val bubbleDrawable = DisplayUtils.getMessageSelector(
            bgBubbleColor,
            ResourcesCompat.getColor(resources, R.color.transparent, null),
            bgBubbleColor, bubbleResource
        )
        ViewCompat.setBackground(bubble, bubbleDrawable)
    }

    private fun setParentMessageDataOnMessageItem(message: ChatMessage) {
        if (!message.isDeleted && message.parentMessage != null) {
            val parentChatMessage = message.parentMessage
            parentChatMessage.activeUser = message.activeUser
            parentChatMessage.imageUrl?.let {
                binding.messageQuote.quotedMessageImage.visibility = View.VISIBLE
                binding.messageQuote.quotedMessageImage.load(it) {
                    addHeader(
                        "Authorization",
                        ApiUtils.getCredentials(message.activeUser.username, message.activeUser.token)
                    )
                }
            } ?: run {
                binding.messageQuote.quotedMessageImage.visibility = View.GONE
            }
            binding.messageQuote.quotedMessageAuthor.text = parentChatMessage.actorDisplayName
                ?: context!!.getText(R.string.nc_nick_guest)
            binding.messageQuote.quotedMessage.text = parentChatMessage.text

            binding.messageQuote.quotedMessageAuthor
                .setTextColor(ContextCompat.getColor(context!!, R.color.textColorMaxContrast))

            if (parentChatMessage.actorId?.equals(message.activeUser.userId) == true) {
                binding.messageQuote.quoteColoredView.setBackgroundResource(R.color.colorPrimary)
            } else {
                binding.messageQuote.quoteColoredView.setBackgroundResource(R.color.textColorMaxContrast)
            }

            binding.messageQuote.quotedChatMessageView.visibility = View.VISIBLE
        } else {
            binding.messageQuote.quotedChatMessageView.visibility = View.GONE
        }
    }

    fun assignAdapter(voiceMessageInterface: VoiceMessageInterface) {
        this.voiceMessageInterface = voiceMessageInterface
    }

    companion object {
        private const val TAG = "VoiceInMessageView"
        private const val SEEKBAR_START: Int = 0
    }
}
