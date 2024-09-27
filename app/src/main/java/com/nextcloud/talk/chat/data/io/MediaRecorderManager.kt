/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.data.io

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaRecorder
import android.util.Log
import com.nextcloud.talk.R
import com.nextcloud.talk.models.domain.ConversationModel
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Abstraction over the [MediaRecorder](https://developer.android.com/reference/android/media/MediaRecorder) class
 * used to manage the MediaRecorder instance and it's state changes. Google doesn't provide a way of accessing state
 * directly, so this handles the changes without exposing the user to it.
 */
class MediaRecorderManager : LifecycleAwareManager {

    companion object {
        val TAG: String = MediaRecorderManager::class.java.simpleName
        private const val VOICE_MESSAGE_SAMPLING_RATE = 22050
        private const val VOICE_MESSAGE_ENCODING_BIT_RATE = 32000
        private const val VOICE_MESSAGE_CHANNELS = 1
        private const val FILE_DATE_PATTERN = "yyyy-MM-dd HH-mm-ss"
        private const val VOICE_MESSAGE_FILE_SUFFIX = ".mp3"
        private const val VOICE_MESSAGE_PREFIX_MAX_LENGTH = 146
    }

    var currentVoiceRecordFile: String = ""

    enum class MediaRecorderState {
        INITIAL,
        INITIALIZED,
        CONFIGURED,
        PREPARED,
        RECORDING,
        RELEASED,
        ERROR
    }
    private var _mediaRecorderState: MediaRecorderState = MediaRecorderState.INITIAL
    val mediaRecorderState: MediaRecorderState
        get() = _mediaRecorderState
    private var recorder: MediaRecorder? = null

    /**
     * Initializes and starts the MediaRecorder
     */
    fun start(context: Context, currentConversation: ConversationModel) {
        if (_mediaRecorderState == MediaRecorderState.ERROR ||
            _mediaRecorderState == MediaRecorderState.RELEASED
        ) {
            _mediaRecorderState = MediaRecorderState.INITIAL
        }

        if (_mediaRecorderState == MediaRecorderState.INITIAL) {
            setVoiceRecordFileName(context, currentConversation)
            initAndStartRecorder()
        } else {
            Log.e(TAG, "Started MediaRecorder with invalid state ${_mediaRecorderState.name}")
        }
    }

    /**
     * Stops and destroys the MediaRecorder
     */
    fun stop() {
        if (_mediaRecorderState != MediaRecorderState.RELEASED) {
            stopAndDestroyRecorder()
        } else {
            Log.e(TAG, "Stopped MediaRecorder with invalid state ${_mediaRecorderState.name}")
        }
    }

    private fun initAndStartRecorder() {
        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            _mediaRecorderState = MediaRecorderState.INITIALIZED

            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            _mediaRecorderState = MediaRecorderState.CONFIGURED

            setOutputFile(currentVoiceRecordFile)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(VOICE_MESSAGE_SAMPLING_RATE)
            setAudioEncodingBitRate(VOICE_MESSAGE_ENCODING_BIT_RATE)
            setAudioChannels(VOICE_MESSAGE_CHANNELS)

            try {
                prepare()
                _mediaRecorderState = MediaRecorderState.PREPARED
            } catch (e: IOException) {
                _mediaRecorderState = MediaRecorderState.ERROR
                Log.e(TAG, "prepare for audio recording failed")
            }

            try {
                start()
                _mediaRecorderState = MediaRecorderState.RECORDING
                Log.d(TAG, "recording started")
            } catch (e: IllegalStateException) {
                _mediaRecorderState = MediaRecorderState.ERROR
                Log.e(TAG, "start for audio recording failed")
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun stopAndDestroyRecorder() {
        recorder?.apply {
            try {
                if (_mediaRecorderState == MediaRecorderState.RECORDING) {
                    stop()
                    reset()
                    _mediaRecorderState = MediaRecorderState.INITIAL
                    Log.d(TAG, "stopped recorder")
                }
                release()
                _mediaRecorderState = MediaRecorderState.RELEASED
            } catch (e: Exception) {
                when (e) {
                    is java.lang.IllegalStateException,
                    is java.lang.RuntimeException -> {
                        _mediaRecorderState = MediaRecorderState.ERROR
                        Log.e(TAG, "error while stopping recorder! with state $_mediaRecorderState $e")
                    }
                }
            }
        }
        recorder = null
    }

    @SuppressLint("SimpleDateFormat")
    private fun setVoiceRecordFileName(context: Context, currentConversation: ConversationModel) {
        val simpleDateFormat = SimpleDateFormat(FILE_DATE_PATTERN)
        val date: String = simpleDateFormat.format(Date())
        val regex = "[/\\\\:%]".toRegex()
        val displayName = currentConversation.displayName.replace(regex, " ")
        val validDisplayName = displayName.replace("\\s+".toRegex(), " ")

        var fileNameWithoutSuffix = String.format(
            context.resources.getString(R.string.nc_voice_message_filename),
            date,
            validDisplayName
        )
        if (fileNameWithoutSuffix.length > VOICE_MESSAGE_PREFIX_MAX_LENGTH) {
            fileNameWithoutSuffix = fileNameWithoutSuffix.substring(0, VOICE_MESSAGE_PREFIX_MAX_LENGTH)
        }
        val fileName = fileNameWithoutSuffix + VOICE_MESSAGE_FILE_SUFFIX
        currentVoiceRecordFile = "${context.cacheDir.absolutePath}/$fileName"
    }

    override fun handleOnPause() {
        // unused atm
    }

    override fun handleOnResume() {
        // unused atm
    }

    override fun handleOnStop() {
        stop()
    }
}
