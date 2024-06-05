/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.data.io

import android.Manifest
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.nextcloud.talk.ui.MicInputCloud
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.log10

/**
 * Abstraction over the [AudioRecord](https://developer.android.com/reference/android/media/AudioRecord) class used
 * to manage the AudioRecord instance and the asynchronous updating of the MicInputCloud. Allows access to the raw
 * bytes recorded from hardware.
 */
class AudioRecorderManager : LifecycleAwareManager {

    companion object {
        val TAG: String = AudioRecorderManager::class.java.simpleName
        private const val SAMPLE_RATE = 8000
        private const val AUDIO_MAX = 40
        private const val AUDIO_MIN = 20
        private const val AUDIO_INTERVAL = 50L
    }
    private val _getAudioValues: MutableLiveData<Pair<Float, Float>> = MutableLiveData()
    val getAudioValues: LiveData<Pair<Float, Float>>
        get() = _getAudioValues

    private var scope = MainScope()
    private var loop = false
    private var audioRecorder: AudioRecord? = null
    private val bufferSize = AudioRecord.getMinBufferSize(
        SAMPLE_RATE,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    /**
     * Initializes and starts the AudioRecorder. Posts updates to the callback every 50 ms.
     */
    fun start(context: Context) {
        if (audioRecorder == null || audioRecorder!!.state == AudioRecord.STATE_UNINITIALIZED) {
            initAudioRecorder(context)
        }
        Log.d(TAG, "AudioRecorder started")
        audioRecorder!!.startRecording()
        loop = true
        scope = MainScope().apply {
            launch {
                Log.d(TAG, "MicInputObserver started")
                micInputObserver()
            }
        }
    }

    /**
     * Stops and destroys the AudioRecorder. Updates cancelled.
     */
    fun stop() {
        if (audioRecorder == null || audioRecorder!!.state == AudioRecord.STATE_UNINITIALIZED) {
            Log.e(TAG, "Stopped AudioRecord on invalid state ")
            return
        }
        Log.d(TAG, "AudioRecorder stopped")
        loop = false
        audioRecorder!!.stop()
        audioRecorder!!.release()
        audioRecorder = null
    }

    private suspend fun micInputObserver() {
        withContext(Dispatchers.IO) {
            while (true) {
                if (!loop) {
                    return@withContext
                }
                val byteArr = ByteArray(bufferSize / 2)
                audioRecorder!!.read(byteArr, 0, byteArr.size)
                val x = abs(byteArr[0].toFloat())
                val logX = log10(x)
                if (x > AUDIO_MAX) {
                    _getAudioValues.postValue(Pair(logX, MicInputCloud.MAXIMUM_RADIUS))
                } else if (x > AUDIO_MIN) {
                    _getAudioValues.postValue(Pair(logX, MicInputCloud.EXTENDED_RADIUS))
                } else {
                    _getAudioValues.postValue(Pair(1f, MicInputCloud.DEFAULT_RADIUS))
                }

                delay(AUDIO_INTERVAL)
            }
        }
    }

    private fun initAudioRecorder(context: Context) {
        val permissionCheck = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        )

        if (permissionCheck == PermissionChecker.PERMISSION_GRANTED) {
            Log.d(TAG, "AudioRecorder init")
            audioRecorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        }
    }

    override fun handleOnPause() {
        // unused atm
    }

    override fun handleOnResume() {
        // unused atm
    }

    override fun handleOnStop() {
        scope.cancel()
        stop()
    }
}
