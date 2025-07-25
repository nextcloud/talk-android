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
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import autodagger.AutoInjector
import com.nextcloud.android.common.ui.theme.utils.ColorRole
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.chat.data.io.AudioFocusRequestManager
import com.nextcloud.talk.databinding.FragmentMessageInputVoiceRecordingBinding
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class MessageInputVoiceRecordingFragment : Fragment() {
    companion object {
        val TAG: String = MessageInputVoiceRecordingFragment::class.java.simpleName
        private const val SEEK_LIMIT = 98

        @JvmStatic
        fun newInstance() = MessageInputVoiceRecordingFragment()
    }

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    lateinit var binding: FragmentMessageInputVoiceRecordingBinding
    private lateinit var chatActivity: ChatActivity
    private var pause = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedApplication!!.componentApplication.inject(this)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentMessageInputVoiceRecordingBinding.inflate(inflater)
        chatActivity = (requireActivity() as ChatActivity)
        themeVoiceRecordingView()
        initVoiceRecordingView()
        initObservers()
        this.lifecycle.addObserver(chatActivity.messageInputViewModel)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        chatActivity.messageInputViewModel.stopMediaPlayer() // if it wasn't stopped already
        this.lifecycle.removeObserver(chatActivity.messageInputViewModel)
    }

    private fun initObservers() {
        chatActivity.messageInputViewModel.startMicInput(requireContext())
        chatActivity.messageInputViewModel.micInputAudioObserver.observe(viewLifecycleOwner) {
            binding.micInputCloud.setRotationSpeed(it.first, it.second)
        }

        lifecycleScope.launch {
            chatActivity.messageInputViewModel.mediaPlayerSeekbarObserver.onEach { progress ->
                if (progress >= SEEK_LIMIT) {
                    togglePausePlay()
                    binding.seekbar.progress = 0
                } else if (!pause && chatActivity.messageInputViewModel.isVoicePreviewPlaying.value == true) {
                    binding.seekbar.progress = progress
                }
            }.collect()
        }

        chatActivity.messageInputViewModel.getAudioFocusChange.observe(viewLifecycleOwner) { state ->
            when (state) {
                AudioFocusRequestManager.ManagerState.AUDIO_FOCUS_CHANGE_LOSS -> {
                    if (chatActivity.messageInputViewModel.isVoicePreviewPlaying.value == true) {
                        chatActivity.messageInputViewModel.stopMediaPlayer()
                    }
                }
                AudioFocusRequestManager.ManagerState.AUDIO_FOCUS_CHANGE_LOSS_TRANSIENT -> {
                    if (chatActivity.messageInputViewModel.isVoicePreviewPlaying.value == true) {
                        chatActivity.messageInputViewModel.pauseMediaPlayer()
                    }
                }
                AudioFocusRequestManager.ManagerState.BROADCAST_RECEIVED -> {
                    if (chatActivity.messageInputViewModel.isVoicePreviewPlaying.value == true) {
                        chatActivity.messageInputViewModel.pauseMediaPlayer()
                    }
                }
            }
        }
    }

    private fun initVoiceRecordingView() {
        binding.deleteVoiceRecording.setOnClickListener {
            chatActivity.chatViewModel.stopAndDiscardAudioRecording()
            clear()
        }

        binding.sendVoiceRecording.setOnClickListener {
            chatActivity.chatViewModel.stopAndSendAudioRecording(
                roomToken = chatActivity.roomToken,
                replyToMessageId = chatActivity.getReplyToMessageId(),
                displayName = chatActivity.currentConversation!!.displayName
            )
            clear()
        }

        binding.micInputCloud.setOnClickListener {
            togglePreviewVisibility()
        }

        binding.playPauseBtn.setOnClickListener {
            togglePausePlay()
        }

        binding.audioRecordDuration.base = chatActivity.messageInputViewModel.getRecordingTime.value ?: 0L
        binding.audioRecordDuration.start()

        binding.seekbar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekbar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    chatActivity.messageInputViewModel.seekMediaPlayerTo(progress)
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

    private fun clear() {
        chatActivity.chatViewModel.setVoiceRecordingLocked(false)
        chatActivity.messageInputViewModel.stopMicInput()
        chatActivity.chatViewModel.stopAudioRecording()
        chatActivity.messageInputViewModel.stopMediaPlayer()
        binding.audioRecordDuration.stop()
        binding.audioRecordDuration.clearAnimation()
    }

    private fun togglePreviewVisibility() {
        val visibility = binding.voicePreviewContainer.visibility
        binding.voicePreviewContainer.visibility = if (visibility == View.VISIBLE) {
            chatActivity.messageInputViewModel.stopMediaPlayer()
            binding.playPauseBtn.icon = ContextCompat.getDrawable(
                requireContext(),
                R.drawable.ic_baseline_play_arrow_voice_message_24
            )
            pause = true
            chatActivity.messageInputViewModel.startMicInput(requireContext())
            chatActivity.chatViewModel.startAudioRecording(requireContext(), chatActivity.currentConversation!!)
            binding.audioRecordDuration.visibility = View.VISIBLE
            binding.audioRecordDuration.base = SystemClock.elapsedRealtime()
            binding.audioRecordDuration.start()
            View.GONE
        } else {
            pause = false
            binding.seekbar.progress = 0
            chatActivity.messageInputViewModel.stopMicInput()
            chatActivity.chatViewModel.stopAudioRecording()
            binding.audioRecordDuration.visibility = View.GONE
            binding.audioRecordDuration.stop()
            View.VISIBLE
        }
    }

    private fun togglePausePlay() {
        val path = chatActivity.chatViewModel.getCurrentVoiceRecordFile()
        if (chatActivity.messageInputViewModel.isVoicePreviewPlaying.value == true) {
            binding.playPauseBtn.icon = ContextCompat.getDrawable(
                requireContext(),
                R.drawable.ic_baseline_play_arrow_voice_message_24
            )
            chatActivity.messageInputViewModel.stopMediaPlayer()
        } else {
            binding.playPauseBtn.icon = ContextCompat.getDrawable(
                requireContext(),
                R.drawable.ic_baseline_pause_voice_message_24
            )
            chatActivity.messageInputViewModel.startMediaPlayer(path)
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
