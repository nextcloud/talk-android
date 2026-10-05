/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Julius Linus juliuslinus1@gmail.com
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat

import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import autodagger.AutoInjector
import com.nextcloud.android.common.ui.theme.utils.ColorRole
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.chat.data.io.AudioFocusRequestManager
import com.nextcloud.talk.chat.viewmodels.MessageInputViewModel
import com.nextcloud.talk.databinding.FragmentMessageInputVoiceRecordingBinding
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.ui.theme.hostViewThemeUtils
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class MessageInputVoiceRecordingFragment : Fragment() {
    companion object {
        val TAG: String = MessageInputVoiceRecordingFragment::class.java.simpleName
        private const val SEEK_LIMIT = 98
        private const val PROGRESS_MAX = 1000

        @JvmStatic
        fun newInstance() = MessageInputVoiceRecordingFragment()
    }

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    private val messageInputViewModel: MessageInputViewModel by activityViewModels()

    lateinit var binding: FragmentMessageInputVoiceRecordingBinding
    private lateinit var chatActivity: ChatActivity
    private var pause = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedApplication!!.componentApplication.inject(this)
        viewThemeUtils = hostViewThemeUtils(activity, viewThemeUtils)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentMessageInputVoiceRecordingBinding.inflate(inflater)
        chatActivity = (requireActivity() as ChatActivity)
        themeVoiceRecordingView()
        initVoiceRecordingView()
        initObservers()
        this.lifecycle.addObserver(messageInputViewModel)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        messageInputViewModel.stopMediaPlayer() // if it wasn't stopped already
        this.lifecycle.removeObserver(messageInputViewModel)
    }

    private fun initObservers() {
        if (isVideoRecording()) {
            return
        }
        messageInputViewModel.startMicInput(requireContext())
        messageInputViewModel.micInputAudioObserver.observe(viewLifecycleOwner) {
            binding.micInputCloud.setRotationSpeed(it.first, it.second)
        }

        lifecycleScope.launch {
            messageInputViewModel.mediaPlayerSeekbarObserver.onEach { progress ->
                if (progress >= SEEK_LIMIT) {
                    togglePausePlay()
                    binding.seekbar.progress = 0
                } else if (!pause && messageInputViewModel.isVoicePreviewPlaying.value == true) {
                    binding.seekbar.progress = progress
                }
            }.collect()
        }

        messageInputViewModel.getAudioFocusChange.observe(viewLifecycleOwner) { state ->
            when (state) {
                AudioFocusRequestManager.ManagerState.AUDIO_FOCUS_CHANGE_LOSS -> {
                    if (messageInputViewModel.isVoicePreviewPlaying.value == true) {
                        messageInputViewModel.stopMediaPlayer()
                    }
                }
                AudioFocusRequestManager.ManagerState.AUDIO_FOCUS_CHANGE_LOSS_TRANSIENT -> {
                    if (messageInputViewModel.isVoicePreviewPlaying.value == true) {
                        messageInputViewModel.pauseMediaPlayer()
                    }
                }
                AudioFocusRequestManager.ManagerState.BROADCAST_RECEIVED -> {
                    if (messageInputViewModel.isVoicePreviewPlaying.value == true) {
                        messageInputViewModel.pauseMediaPlayer()
                    }
                }
            }
        }
    }

    private fun initVoiceRecordingView() {
        binding.deleteVoiceRecording.setOnClickListener {
            chatActivity.stopAndDiscardRecording()
            clear()
        }

        binding.sendVoiceRecording.setOnClickListener {
            chatActivity.stopAndSendRecording()
            clear()
        }

        if (isVideoRecording()) {
            showCompactVideoRow()
        } else {
            binding.micInputCloud.setOnClickListener {
                togglePreviewVisibility()
            }
        }

        binding.playPauseBtn.setOnClickListener {
            togglePausePlay()
        }

        binding.audioRecordDuration.base = messageInputViewModel.getRecordingTime.value ?: 0L
        binding.audioRecordDuration.start()

        binding.seekbar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekbar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    messageInputViewModel.seekMediaPlayerTo(progress)
                }
            }

            override fun onStartTrackingTouch(p0: SeekBar) {
                pause = true
            }

            override fun onStopTrackingTouch(p0: SeekBar) {
                pause = false
            }
        })
    }

    /**
     * Video is recorded with a one-line panel (delete, red dot in the progress ring, timer, send) so that the preview
     * gets the height. The views are the ones of the voice panel, only arranged differently; the voice panel stays
     * as it is. The fragment is recreated after a rotation and arranges the row again.
     */
    private fun showCompactVideoRow() {
        val row = binding.recordingControlsRow
        val timer = binding.audioRecordDuration
        val margin = resources.getDimensionPixelSize(R.dimen.standard_half_margin)
        (timer.parent as ViewGroup).removeView(timer)
        row.addView(timer, row.indexOfChild(binding.micInputCloud), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        timer.setPadding(margin, 0, 0, 0)
        row.setPadding(margin, margin, margin, margin)
        row.weightSum = 0f
        for (button in listOf(binding.deleteVoiceRecording, binding.sendVoiceRecording)) {
            button.layoutParams = LinearLayout.LayoutParams(button.layoutParams.width, button.layoutParams.height)
        }
        binding.micInputCloud.visibility = View.GONE
        binding.videoRecordingIndicator.visibility = View.VISIBLE
        timer.setOnChronometerTickListener {
            val elapsed = SystemClock.elapsedRealtime() - it.base
            binding.videoRecordingProgress.progress =
                (elapsed * PROGRESS_MAX / VideoMessageRecorder.MAX_DURATION_MS).toInt().coerceIn(0, PROGRESS_MAX)
        }
    }

    private fun isVideoRecording() = chatActivity.chatViewModel.activeRecordingMode == RecordInputMode.VIDEO

    private fun clear() {
        val isVideo = isVideoRecording()
        val recorderActive = chatActivity.chatViewModel.activeVideoMessageRecorder?.isActive == true
        messageInputViewModel.stopMicInput()
        // A video recording is finished by CameraX later: ChatActivity.onVideoRecordingFinished releases the lock
        // and the in-progress state then, also when the recording failed or was cancelled.
        if (!clearsRecordingStateOnFinalize(isVideo, recorderActive)) {
            chatActivity.chatViewModel.setVoiceRecordingLocked(false)
            if (isVideo) {
                if (chatActivity.chatViewModel.getVoiceRecordingInProgress.value == true) {
                    chatActivity.chatViewModel.onVideoRecordingEnded()
                }
            } else {
                chatActivity.chatViewModel.stopAudioRecording()
            }
        }
        messageInputViewModel.stopMediaPlayer()
        binding.audioRecordDuration.stop()
        binding.audioRecordDuration.clearAnimation()
    }

    private fun togglePreviewVisibility() {
        val visibility = binding.voicePreviewContainer.visibility
        binding.voicePreviewContainer.visibility = if (visibility == View.VISIBLE) {
            messageInputViewModel.stopMediaPlayer()
            binding.playPauseBtn.icon = ContextCompat.getDrawable(
                requireContext(),
                R.drawable.ic_baseline_play_arrow_voice_message_24
            )
            pause = true
            messageInputViewModel.startMicInput(requireContext())
            chatActivity.chatViewModel.startAudioRecording(requireContext(), chatActivity.currentConversation!!)
            binding.audioRecordDuration.visibility = View.VISIBLE
            binding.audioRecordDuration.base = SystemClock.elapsedRealtime()
            binding.audioRecordDuration.start()
            View.GONE
        } else {
            pause = false
            binding.seekbar.progress = 0
            messageInputViewModel.stopMicInput()
            chatActivity.chatViewModel.stopAudioRecording()
            binding.audioRecordDuration.visibility = View.GONE
            binding.audioRecordDuration.stop()
            View.VISIBLE
        }
    }

    private fun togglePausePlay() {
        val path = chatActivity.chatViewModel.getCurrentVoiceRecordFile()
        if (messageInputViewModel.isVoicePreviewPlaying.value == true) {
            binding.playPauseBtn.icon = ContextCompat.getDrawable(
                requireContext(),
                R.drawable.ic_baseline_play_arrow_voice_message_24
            )
            messageInputViewModel.stopMediaPlayer()
        } else {
            binding.playPauseBtn.icon = ContextCompat.getDrawable(
                requireContext(),
                R.drawable.ic_baseline_pause_voice_message_24
            )
            messageInputViewModel.startMediaPlayer(path)
        }
    }

    private fun themeVoiceRecordingView() {
        binding.playPauseBtn.let {
            viewThemeUtils.material.colorMaterialButtonText(it)
        }

        binding.seekbar.let {
            viewThemeUtils.platform.themeHorizontalSeekBar(it)
        }

        binding.deleteVoiceRecording.let {
            viewThemeUtils.platform.colorImageView(it, ColorRole.PRIMARY)
        }
        binding.sendVoiceRecording.let {
            viewThemeUtils.platform.colorImageView(it, ColorRole.PRIMARY)
        }

        binding.voicePreviewContainer.let {
            viewThemeUtils.talk.themeOutgoingMessageBubble(it, true, false)
        }

        binding.micInputCloud.let {
            viewThemeUtils.talk.themeMicInputCloud(it)
        }
    }
}
