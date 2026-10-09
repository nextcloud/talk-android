/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.data.io
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.chat.ChatActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.time.Duration.Companion.milliseconds

/**
 * Abstraction over an [ExoPlayer] instance, used to play back a voice recording before it is sent. Audio messages
 * of the chat are played by [VoiceMessageMediaService] instead.
 */
@Suppress("TooGenericExceptionCaught")
class MediaPlayerManager : LifecycleAwareManager {
    companion object {
        val TAG: String = MediaPlayerManager::class.java.simpleName
        private const val SEEKBAR_UPDATE_DELAY = 150L
        private const val DIVIDER = 100f
    }

    enum class MediaPlayerManagerState {
        DEFAULT,
        SETUP,
        STARTED,
        STOPPED,
        RESUMED,
        PAUSED,
        ERROR
    }

    private val managerState = MutableStateFlow(MediaPlayerManagerState.DEFAULT)

    val mediaPlayerSeekBarPosition: Flow<Int>
        get() = _mediaPlayerSeekBarPosition
    private val _mediaPlayerSeekBarPosition: MutableSharedFlow<Int> = MutableSharedFlow()

    private var mediaPlayer: ExoPlayer? = null
    private var loop = false
    private var scope = MainScope()

    var mediaPlayerDuration: Int = 0
    var mediaPlayerPosition: Int = 0

    /**
     * Starts playing audio from the given path, initializes or resumes if the player is already created.
     */
    fun start(path: String) {
        if (mediaPlayer != null && mediaPlayer!!.isPlaying) {
            stop()
        }

        if (mediaPlayer == null || !scope.isActive) {
            init(path)
        } else {
            managerState.value = MediaPlayerManagerState.RESUMED
            mediaPlayer!!.play()
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
            scope.cancel()
            mediaPlayer!!.stop()
            mediaPlayer!!.release()
            mediaPlayer = null
            managerState.value = MediaPlayerManagerState.STOPPED
        }
    }

    /**
     * Pauses the player.
     */
    fun pause() {
        if (mediaPlayer != null) {
            Log.d(TAG, "media player paused")
            managerState.value = MediaPlayerManagerState.PAUSED
            mediaPlayer!!.pause()
            loop = false
        }
    }

    /**
     * Seeks the player to the given position, saves position for resynchronization.
     */
    fun seekTo(progress: Int) {
        if (mediaPlayer != null) {
            val pos = mediaPlayer!!.duration * (progress / DIVIDER)
            mediaPlayer!!.seekTo(pos.toLong())
            mediaPlayerPosition = pos.toInt()
        }
    }

    private suspend fun seekbarUpdateObserver() {
        withContext(Dispatchers.IO) {
            while (true) {
                if (!loop) {
                    // NOTE: ok so this doesn't stop the loop, but rather stop the update. Wasteful, but minimal
                    delay(SEEKBAR_UPDATE_DELAY.milliseconds)
                    continue
                }

                withContext(Dispatchers.Main) {
                    mediaPlayer?.let { p ->
                        try {
                            if (!p.isPlaying) return@let
                        } catch (e: IllegalStateException) {
                            Log.e(TAG, "Seekbar updated during an improper state: $e")
                            return@let
                        }

                        val pos = p.currentPosition
                        mediaPlayerPosition = pos.toInt()
                        val progress = (pos.toFloat() / mediaPlayerDuration) * DIVIDER
                        _mediaPlayerSeekBarPosition.emit(ceil(progress).toInt())
                    }
                }

                delay(SEEKBAR_UPDATE_DELAY.milliseconds)
            }
        }
    }

    private fun init(path: String) {
        try {
            val context = NextcloudTalkApplication.sharedApplication!!.applicationContext
            mediaPlayer = ExoPlayer.Builder(context).build().apply {
                managerState.value = MediaPlayerManagerState.SETUP
                setMediaItem(MediaItem.fromUri(path))
                prepare()
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY &&
                            managerState.value == MediaPlayerManagerState.SETUP
                        ) {
                            onPrepare()
                        }
                    }
                })
            }
        } catch (e: Exception) {
            Log.e(ChatActivity.TAG, "failed to initialize mediaPlayer", e)
            managerState.value = MediaPlayerManagerState.ERROR
        }
    }

    private fun ExoPlayer.onPrepare() {
        mediaPlayerDuration = this.duration.toInt()
        play()
        managerState.value = MediaPlayerManagerState.STARTED
        loop = true
        scope = MainScope()
        scope.launch { seekbarUpdateObserver() }
    }

    override fun handleOnPause() {
        // unused atm
    }

    override fun handleOnResume() {
        if (mediaPlayer != null && mediaPlayer!!.isPlaying) {
            loop = true
        }
    }

    override fun handleOnStop() {
        loop = false
    }
}
