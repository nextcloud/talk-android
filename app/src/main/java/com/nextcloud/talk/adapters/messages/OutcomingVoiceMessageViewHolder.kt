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
import android.app.Activity
import android.content.Context
import android.graphics.PorterDuff
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.SeekBar
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.ViewCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import autodagger.AutoInjector
import coil.load
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.databinding.ItemCustomOutcomingVoiceMessageBinding
import com.nextcloud.talk.jobs.DownloadFileToCacheWorker
import com.nextcloud.talk.models.database.CapabilitiesUtil
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.models.json.chat.ReadStatus
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.preferences.AppPreferences
import com.stfalcon.chatkit.messages.MessageHolders
import java.io.File
import java.util.concurrent.ExecutionException
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class OutcomingVoiceMessageViewHolder(incomingView: View) : MessageHolders
.OutcomingTextMessageViewHolder<ChatMessage>(incomingView) {

    private val binding: ItemCustomOutcomingVoiceMessageBinding =
        ItemCustomOutcomingVoiceMessageBinding.bind(itemView)
    private val realView: View = itemView

    @JvmField
    @Inject
    var context: Context? = null

    @JvmField
    @Inject
    var appPreferences: AppPreferences? = null

    lateinit var message: ChatMessage

    lateinit var activity: Activity

    var mediaPlayer: MediaPlayer? = null

    lateinit var handler: Handler

    @SuppressLint("SetTextI18n")
    override fun onBind(message: ChatMessage) {
        super.onBind(message)
        this.message = message
        sharedApplication!!.componentApplication.inject(this)

        colorizeMessageBubble(message)

        itemView.isSelected = false
        binding.messageTime.setTextColor(context?.resources!!.getColor(R.color.warm_grey_four))

        // parent message handling
        setParentMessageDataOnMessageItem(message)

        binding.playBtn.setOnClickListener {
            openOrDownloadFile(message)
        }

        binding.pauseBtn.setOnClickListener {
            pausePlayback()
        }

        activity = itemView.context as Activity

        binding.seekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (mediaPlayer != null && fromUser) {
                    mediaPlayer!!.seekTo(progress * 1000)
                }
            }
        })

        // check if download worker is already running
        val fileId = message.getSelectedIndividualHashMap()["id"]
        val workers = WorkManager.getInstance(
            context!!
        ).getWorkInfosByTag(fileId!!)

        try {
            for (workInfo in workers.get()) {
                if (workInfo.state == WorkInfo.State.RUNNING || workInfo.state == WorkInfo.State.ENQUEUED) {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.playBtn.visibility = View.GONE
                    WorkManager.getInstance(context!!).getWorkInfoByIdLiveData(workInfo.id)
                        .observeForever { info: WorkInfo? ->
                            if (info != null) {
                                updateViewsByProgress(
                                    info
                                )
                            }
                        }
                }
            }
        } catch (e: ExecutionException) {
            Log.e(TAG, "Error when checking if worker already exists", e)
        } catch (e: InterruptedException) {
            Log.e(TAG, "Error when checking if worker already exists", e)
        }

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
            binding.messageQuote.quotedMessage.setTextColor(
                context!!.resources.getColor(R.color.nc_outcoming_text_default)
            )
            binding.messageQuote.quotedMessageAuthor.setTextColor(context!!.resources.getColor(R.color.nc_grey))

            binding.messageQuote.quoteColoredView.setBackgroundResource(R.color.white)

            binding.messageQuote.quotedChatMessageView.visibility = View.VISIBLE
        } else {
            binding.messageQuote.quotedChatMessageView.visibility = View.GONE
        }
    }

    private fun colorizeMessageBubble(message: ChatMessage) {
        val resources = sharedApplication!!.resources
        val bgBubbleColor = if (message.isDeleted) {
            resources.getColor(R.color.bg_message_list_outcoming_bubble_deleted)
        } else {
            resources.getColor(R.color.bg_message_list_outcoming_bubble)
        }
        if (message.isGrouped) {
            val bubbleDrawable = DisplayUtils.getMessageSelector(
                bgBubbleColor,
                resources.getColor(R.color.transparent),
                bgBubbleColor,
                R.drawable.shape_grouped_outcoming_message
            )
            ViewCompat.setBackground(bubble, bubbleDrawable)
        } else {
            val bubbleDrawable = DisplayUtils.getMessageSelector(
                bgBubbleColor,
                resources.getColor(R.color.transparent),
                bgBubbleColor,
                R.drawable.shape_outcoming_message
            )
            ViewCompat.setBackground(bubble, bubbleDrawable)
        }
    }

    private fun openOrDownloadFile(message: ChatMessage) {
        val filename = message.getSelectedIndividualHashMap()["name"]
        val file = File(context!!.cacheDir, filename!!)

        if (file.exists()) {
            binding.progressBar.visibility = View.GONE
            startPlayback(message)
        } else {
            binding.playBtn.visibility = View.GONE
            binding.progressBar.visibility = View.VISIBLE
            downloadFileToCache(message)
        }
    }

    private fun startPlayback(message: ChatMessage) {
        initMediaPlayer(message)

        if (!mediaPlayer!!.isPlaying) {
            mediaPlayer!!.start()
        }

        handler = Handler()
        activity.runOnUiThread(object : Runnable {
            override fun run() {
                if (mediaPlayer != null) {
                    val currentPosition: Int = mediaPlayer!!.currentPosition / 1000
                    binding.seekbar.progress = currentPosition
                }
                handler.postDelayed(this, 1000)
            }
        })

        binding.progressBar.visibility = View.GONE
        binding.playBtn.visibility = View.GONE
        binding.pauseBtn.visibility = View.VISIBLE
    }

    private fun pausePlayback() {
        if (mediaPlayer!!.isPlaying) {
            mediaPlayer!!.pause()
        }

        binding.playBtn.visibility = View.VISIBLE
        binding.pauseBtn.visibility = View.GONE
    }

    private fun initMediaPlayer(message: ChatMessage) {
        val fileName = message.getSelectedIndividualHashMap()["name"]
        val absolutePath = context!!.cacheDir.absolutePath + "/" + fileName

        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context!!, Uri.parse(absolutePath))
                prepare()
            }
        }

        binding.seekbar.max = mediaPlayer!!.duration / 1000

        mediaPlayer!!.setOnCompletionListener {
            binding.playBtn.visibility = View.VISIBLE
            binding.pauseBtn.visibility = View.GONE
            binding.seekbar.progress = 0
            handler.removeCallbacksAndMessages(null)
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    @SuppressLint("LongLogTag")
    private fun downloadFileToCache(message: ChatMessage) {
        val baseUrl = message.activeUser.baseUrl
        val userId = message.activeUser.userId
        val attachmentFolder = CapabilitiesUtil.getAttachmentFolder(message.activeUser)
        val fileName = message.getSelectedIndividualHashMap()["name"]
        var size = message.getSelectedIndividualHashMap()["size"]
        if (size == null) {
            size = "-1"
        }
        val fileSize = Integer.valueOf(size)
        val fileId = message.getSelectedIndividualHashMap()["id"]
        val path = message.getSelectedIndividualHashMap()["path"]

        // check if download worker is already running
        val workers = WorkManager.getInstance(
            context!!
        ).getWorkInfosByTag(fileId!!)
        try {
            for (workInfo in workers.get()) {
                if (workInfo.state == WorkInfo.State.RUNNING || workInfo.state == WorkInfo.State.ENQUEUED) {
                    Log.d(
                        TAG, "Download worker for " + fileId + " is already running or " +
                            "scheduled"
                    )
                    return
                }
            }
        } catch (e: ExecutionException) {
            Log.e(TAG, "Error when checking if worker already exsists", e)
        } catch (e: InterruptedException) {
            Log.e(TAG, "Error when checking if worker already exsists", e)
        }
        val data: Data
        val downloadWorker: OneTimeWorkRequest
        data = Data.Builder()
            .putString(DownloadFileToCacheWorker.KEY_BASE_URL, baseUrl)
            .putString(DownloadFileToCacheWorker.KEY_USER_ID, userId)
            .putString(DownloadFileToCacheWorker.KEY_ATTACHMENT_FOLDER, attachmentFolder)
            .putString(DownloadFileToCacheWorker.KEY_FILE_NAME, fileName)
            .putString(DownloadFileToCacheWorker.KEY_FILE_PATH, path)
            .putInt(DownloadFileToCacheWorker.KEY_FILE_SIZE, fileSize)
            .build()
        downloadWorker = OneTimeWorkRequest.Builder(DownloadFileToCacheWorker::class.java)
            .setInputData(data)
            .addTag(fileId)
            .build()
        WorkManager.getInstance().enqueue(downloadWorker)

        WorkManager.getInstance(context!!).getWorkInfoByIdLiveData(downloadWorker.id)
            .observeForever { workInfo: WorkInfo ->
                updateViewsByProgress(
                    workInfo
                )
            }
    }

    private fun updateViewsByProgress(workInfo: WorkInfo) {
        when (workInfo.state) {
            WorkInfo.State.RUNNING -> {
                val progress = workInfo.progress.getInt(DownloadFileToCacheWorker.PROGRESS, -1)
                if (progress > -1) {
                    binding.playBtn.visibility = View.GONE
                    binding.progressBar.visibility = View.VISIBLE
                }
            }
            WorkInfo.State.SUCCEEDED -> {
                startPlayback(message)
            }
            WorkInfo.State.FAILED -> {
                binding.progressBar.visibility = View.GONE
                binding.playBtn.visibility = View.VISIBLE
            }
            else -> {
            }
        }
    }

    companion object {
        private const val TAG = "VoiceOutMessageView"
    }
}
