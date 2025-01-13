/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.data.io

import android.media.MediaPlayer
import android.util.Log
import androidx.compose.ui.util.fastRoundToInt
import com.nextcloud.talk.chat.ChatActivity
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.ui.PlaybackSpeed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException

/**
 * Abstraction over the [MediaPlayer](https://developer.android.com/reference/android/media/MediaPlayer) class used
 * to manage the MediaPlayer instance.
 */
object MediaPlayerManager : LifecycleAwareManager {
    val TAG: String = MediaPlayerManager::class.java.simpleName
    private const val SEEKBAR_UPDATE_DELAY = 15L
    const val DIVIDER = 100f

    enum class MediaPlayerManagerState {
        DEFAULT,
        SETUP,
        STARTED,
        STOPPED,
        RESUMED,
        PAUSED,
        ERROR
    }

    val backgroundPlayUIFlow: StateFlow<ChatMessage?>
        get() = _backgroundPlayUIFlow
    private val _backgroundPlayUIFlow = MutableStateFlow<ChatMessage?>(null)

    val managerState: Flow<MediaPlayerManagerState>
        get() = _managerState
    private val _managerState = MutableStateFlow(MediaPlayerManagerState.DEFAULT)

    private val playQueue = mutableListOf<Pair<String, ChatMessage>>()

    val mediaPlayerSeekBarPositionPair: Flow<Pair<Int, String>>
        get() = _mediaPlayerSeekBarPositionPair
    private val _mediaPlayerSeekBarPositionPair: MutableSharedFlow<Pair<Int, String>> = MutableSharedFlow()

    val mediaPlayerSeekBarPosition: Flow<Int>
        get() = _mediaPlayerSeekBarPosition
    private val _mediaPlayerSeekBarPosition: MutableSharedFlow<Int> = MutableSharedFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var mediaPlayerPosition: Int = 0
    private var loop = false
    private var scope = MainScope()
    private var currentCycledMessage: ChatMessage? = null
    private var currentDataSource: String = ""
    var mediaPlayerDuration: Int = 0

    /**
     * Starts playing audio from the given path, initializes or resumes if the player is already created.
     */
    fun start(path: String) {
        if (mediaPlayer != null && mediaPlayer!!.isPlaying) {
            stop()
        }

        if (mediaPlayer == null || !scope.isActive) {
            init(path)
        } else { // FIXME need to check if the current chat message is different than the one mediaPlayerManager data
            _managerState.value = MediaPlayerManagerState.RESUMED
            mediaPlayer!!.start()
            loop = true
            scope.launch { seekbarUpdateObserver() }
        }
    }

    /**
     * Starting cycling through the playQueue, playing messages automatically unless stop() is called.
     *
     */
    fun startCycling() {
        if (playQueue.size == 0) {
            error("Attempted to start cycling with empty playList")
        }

        if (mediaPlayer != null && mediaPlayer!!.isPlaying) {
            stop()
        }

        val shouldReset = playQueue.first().first != currentDataSource

        if (mediaPlayer == null || !scope.isActive || shouldReset) {
            initCycling()
        } else {
            _managerState.value = MediaPlayerManagerState.RESUMED
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
            scope.cancel()
            mediaPlayer!!.stop()
            mediaPlayer!!.release()
            mediaPlayer = null
            currentCycledMessage = null
            _managerState.value = MediaPlayerManagerState.STOPPED
        }
    }

    /**
     * Pauses the player.
     */
    fun pause() {
        if (mediaPlayer != null) {
            Log.d(TAG, "media player paused")
            _managerState.value = MediaPlayerManagerState.PAUSED
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
                    val progressI = progress.fastRoundToInt()
                    _mediaPlayerSeekBarPosition.emit(progressI)
                    currentCycledMessage?.let {
                        _mediaPlayerSeekBarPositionPair.emit(Pair(progressI, it.id))
                    }
                }

                delay(SEEKBAR_UPDATE_DELAY)
            }
        }
    }

    /**
     * Adds a audio file to the play queue. for cycling through
     *
     * @throws FileNotFoundException if the file is not downloaded to cache first
     */
    fun addToPlayList(path: String, chatMessage: ChatMessage) {
        val file = File(path)
        if (!file.exists()) {
            throw FileNotFoundException("Cannot add to playlist without downloading to cache first for path\n$path")
        }
        playQueue.add(Pair(path, chatMessage))
    }

    fun clearPlayList() {
        playQueue.clear()
    }

    /**
     * Sets the player speed.
     */
    fun setPlayBackSpeed(speed: PlaybackSpeed) {
        if (mediaPlayer != null && mediaPlayer!!.isPlaying) {
            mediaPlayer!!.playbackParams.setSpeed(speed.value)
        }
    }

    private fun init(path: String) {
        try {
            mediaPlayer = MediaPlayer().apply {
                _managerState.value = MediaPlayerManagerState.SETUP
                setDataSource(path)
                currentDataSource = path
                prepareAsync()
                setOnPreparedListener {
                    onPrepare()
                }
            }
        } catch (e: Exception) {
            Log.e(ChatActivity.TAG, "failed to initialize mediaPlayer", e)
            _managerState.value = MediaPlayerManagerState.ERROR
        }
    }

    private fun initCycling() {
        try {
            mediaPlayer = MediaPlayer().apply {
                _managerState.value = MediaPlayerManagerState.SETUP
                val pair = playQueue.iterator().next()
                setDataSource(pair.first)
                currentDataSource = pair.first
                currentCycledMessage = pair.second
                playQueue.removeAt(0)
                prepareAsync()
                setOnPreparedListener {
                    onPrepare()
                }

                setOnCompletionListener {
                    scope.cancel()
                    // if (playQueue.iterator().hasNext()) { // FIXME illegal state bug here on cycling
                    //     _managerState.value = MediaPlayerManagerState.SETUP
                    //     val nextPair = playQueue.iterator().next()
                    //     setDataSource(nextPair.first)
                    //     _currentCycledMessage = nextPair.second
                    //     prepareAsync()
                    // } else {
                    mediaPlayer!!.release()
                    mediaPlayer = null
                    _backgroundPlayUIFlow.tryEmit(null)
                    currentCycledMessage = null
                    _managerState.value = MediaPlayerManagerState.STOPPED
                    // }
                }
            }
        } catch (e: Exception) {
            Log.e(ChatActivity.TAG, "failed to initialize mediaPlayer", e)
            _managerState.value = MediaPlayerManagerState.ERROR
        }
    }

    private fun MediaPlayer.onPrepare() {
        mediaPlayerDuration = this.duration
        start()
        _managerState.value = MediaPlayerManagerState.STARTED
        currentCycledMessage?.let {
            it.isPlayingVoiceMessage = true
            _backgroundPlayUIFlow.tryEmit(it)
        }
        loop = true
        scope = MainScope()
        scope.launch { seekbarUpdateObserver() }
    }

    override fun handleOnPause() {
        // unused atm
    }

    // FIXME Note: might be some issues with state here, double check
    //  Idea is that on orientation change or resume, if still playing, continue to loop
    override fun handleOnResume() {
        if (mediaPlayer != null && mediaPlayer!!.isPlaying) {
            loop = true
        }
    }

    override fun handleOnStop() {
        loop = false
        if (mediaPlayer != null &&
            _managerState.value != MediaPlayerManagerState.STOPPED && // TODO might be a state error here
            currentCycledMessage != null
        ) {
            CoroutineScope(Dispatchers.Default).launch {
                delay(100)
                _backgroundPlayUIFlow.tryEmit(currentCycledMessage!!)
                Log.d("Julius", "Msg sent")
            }
            // this might be being sent, but before list Activity, therefore not showing up
            // try a different approach. Maybe a stateflow instead should be a better fit.
        }
    }
}
