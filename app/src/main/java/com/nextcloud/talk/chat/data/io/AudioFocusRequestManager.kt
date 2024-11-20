/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.data.io

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

/**
 * Abstraction over the [AudioFocusManager](https://developer.android.com/reference/kotlin/android/media/AudioFocusRequest)
 * class used to manage audio focus requests automatically
 */
class AudioFocusRequestManager(private val context: Context) {
    companion object {
        val TAG: String? = AudioFocusRequestManager::class.java.simpleName
    }

    enum class ManagerState {
        AUDIO_FOCUS_CHANGE_LOSS,
        AUDIO_FOCUS_CHANGE_LOSS_TRANSIENT,
        BROADCAST_RECEIVED
    }

    private val _getManagerState: MutableLiveData<ManagerState> = MutableLiveData()
    val getManagerState: LiveData<ManagerState>
        get() = _getManagerState

    private var isPausedDueToBecomingNoisy = false
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val duration = AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
    private val audioFocusChangeListener: AudioManager.OnAudioFocusChangeListener =
        AudioManager.OnAudioFocusChangeListener { flag ->
            when (flag) {
                AudioManager.AUDIOFOCUS_LOSS -> {
                    isPausedDueToBecomingNoisy = false
                    _getManagerState.value = ManagerState.AUDIO_FOCUS_CHANGE_LOSS
                }

                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    isPausedDueToBecomingNoisy = false
                    _getManagerState.value = ManagerState.AUDIO_FOCUS_CHANGE_LOSS_TRANSIENT
                }
            }
        }
    private val noisyAudioStreamReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            isPausedDueToBecomingNoisy = true
            _getManagerState.value = ManagerState.BROADCAST_RECEIVED
        }
    }

    private val focusRequest = AudioFocusRequest.Builder(duration)
        .setOnAudioFocusChangeListener(audioFocusChangeListener)
        .build()

    /**
     * Requests the OS for audio focus, before executing the callback on success
     */
    fun audioFocusRequest(shouldRequestFocus: Boolean, onGranted: () -> Unit) {
        if (isPausedDueToBecomingNoisy) {
            onGranted()
            return
        }

        val isGranted: Int =
            if (shouldRequestFocus) {
                audioManager.requestAudioFocus(focusRequest)
            } else {
                audioManager.abandonAudioFocusRequest(focusRequest)
            }

        if (isGranted == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            onGranted()
            handleBecomingNoisyBroadcast(shouldRequestFocus)
        }
    }

    private fun handleBecomingNoisyBroadcast(register: Boolean) {
        try {
            if (register) {
                context.registerReceiver(
                    noisyAudioStreamReceiver,
                    IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                )
            } else {
                context.unregisterReceiver(noisyAudioStreamReceiver)
            }
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        }
    }
}
