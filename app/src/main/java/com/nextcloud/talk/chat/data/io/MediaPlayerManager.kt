/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.data.io

import android.media.MediaPlayer
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.nextcloud.talk.chat.ChatActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Abstraction over the [MediaPlayer](https://developer.android.com/reference/android/media/MediaPlayer) class used
 * to manage the MediaPlayer instance.
 */
class MediaPlayerManager : LifecycleAwareManager {
    companion object {
        val TAG: String = MediaPlayerManager::class.java.simpleName
        private const val SEEKBAR_UPDATE_DELAY = 15L
        const val DIVIDER = 100f
    }

    private var mediaPlayer: MediaPlayer? = null
    private var mediaPlayerPosition: Int = 0
    private var loop = false
    private var scope = MainScope()
    var mediaPlayerDuration: Int = 0
    private val _mediaPlayerSeekBarPosition: MutableLiveData<Int> = MutableLiveData()
    val mediaPlayerSeekBarPosition: LiveData<Int>
        get() = _mediaPlayerSeekBarPosition

    /**
     * Starts playing audio from the given path, initializes or resumes if the player is already created.
     */
    fun start(path: String) {
        if (mediaPlayer == null || !scope.isActive) {
            init(path)
        } else {
            mediaPlayer!!.start()
            loop = true
            scope.launch { seekbarUpdateObserver() }
        }
    }

    /**
     * Stop and destroys the player.
     */
    fun stop() {
        if (mediaPlayer != null) {
            Log.d(TAG, "media player destroyed")
            loop = false
            mediaPlayer!!.stop()
            mediaPlayer!!.release()
            mediaPlayer = null
        }
    }

    /**
     * Pauses the player.
     */
    fun pause() {
        if (mediaPlayer != null) {
            Log.d(TAG, "media player paused")
            mediaPlayer!!.pause()
        }
    }

    /**
     * Seeks the player to the given position, saves position for resynchronization.
     */
    fun seekTo(progress: Int) {
        if (mediaPlayer != null) {
            val pos = mediaPlayer!!.duration * (progress / DIVIDER)
            mediaPlayer!!.seekTo(pos.toInt())
            mediaPlayerPosition = pos.toInt()
        }
    }

    private suspend fun seekbarUpdateObserver() {
        withContext(Dispatchers.IO) {
            while (true) {
                if (!loop) {
                    return@withContext
                }
                if (mediaPlayer != null && mediaPlayer!!.isPlaying) {
                    val pos = mediaPlayer!!.currentPosition
                    val progress = (pos.toFloat() / mediaPlayerDuration) * DIVIDER
                    _mediaPlayerSeekBarPosition.postValue(progress.toInt())
                }

                delay(SEEKBAR_UPDATE_DELAY)
            }
        }
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    private fun init(path: String) {
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(path)
                prepareAsync()
                setOnPreparedListener {
                    mediaPlayerDuration = it.duration
                    start()
                    loop = true
                    scope = MainScope()
                    scope.launch { seekbarUpdateObserver() }
                }
            }
        } catch (e: Exception) {
            Log.e(ChatActivity.TAG, "failed to initialize mediaPlayer", e)
        }
    }

    override fun handleOnPause() {
        // unused atm
    }

    override fun handleOnResume() {
        // unused atm
    }

    override fun handleOnStop() {
        stop()
        scope.cancel()
    }
}
