/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.data.io

import android.media.MediaPlayer
import android.util.Log
import com.nextcloud.talk.chat.ChatActivity
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.ui.PlaybackSpeed
import com.nextcloud.talk.utils.preferences.AppPreferences
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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import kotlin.math.ceil

/**
 * Abstraction over the [MediaPlayer](https://developer.android.com/reference/android/media/MediaPlayer) class used
 * to manage the MediaPlayer instance.
 */
@Suppress("TooManyFunctions", "TooGenericExceptionCaught")
class MediaPlayerManager : LifecycleAwareManager {
    companion object {
        val TAG: String = MediaPlayerManager::class.java.simpleName
        private const val SEEKBAR_UPDATE_DELAY = 150L
        private const val ONE_SEC = 1000
        private const val DIVIDER = 100f
        private const val IS_PLAYED_CUTOFF = 5

        @JvmStatic
        private val manager: MediaPlayerManager = MediaPlayerManager()

        fun sharedInstance(preferences: AppPreferences): MediaPlayerManager =
            manager.apply {
                appPreferences = preferences
            }
    }

    lateinit var appPreferences: AppPreferences

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

    val mediaPlayerSeekBarPositionMsg: Flow<ChatMessage>
        get() = _mediaPlayerSeekBarPositionMsg
    private val _mediaPlayerSeekBarPositionMsg: MutableSharedFlow<ChatMessage> = MutableSharedFlow()

    val mediaPlayerSeekBarPosition: Flow<Int>
        get() = _mediaPlayerSeekBarPosition
    private val _mediaPlayerSeekBarPosition: MutableSharedFlow<Int> = MutableSharedFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var loop = false
    private var scope = MainScope()
    private var currentCycledMessage: ChatMessage? = null
    private var currentDataSource: String = ""
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
            _backgroundPlayUIFlow.tryEmit(null)
            _managerState.value = MediaPlayerManagerState.STOPPED
        }
    }

    /**
     * Pauses the player.
     */
    fun pause(notifyUI: Boolean) {
        if (mediaPlayer != null) {
            Log.d(TAG, "media player paused")
            _managerState.value = MediaPlayerManagerState.PAUSED
            mediaPlayer!!.pause()
            loop = false
            if (notifyUI) {
                _backgroundPlayUIFlow.tryEmit(null)
            }
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
            currentCycledMessage?.voiceMessageDuration = mediaPlayerDuration / ONE_SEC
            currentCycledMessage?.resetVoiceMessage = false
            while (true) {
                if (!loop) {
                    // NOTE: ok so this doesn't stop the loop, but rather stop the update. Wasteful, but minimal
                    delay(SEEKBAR_UPDATE_DELAY)
                    continue
                }

                mediaPlayer?.let { player ->
                    try {
                        if (!player.isPlaying) return@let
                    } catch (e: IllegalStateException) {
                        Log.e(TAG, "Seekbar updated during an improper state: $e")
                        return@let
                    }

                    val pos = player.currentPosition
                    mediaPlayerPosition = pos
                    val progress = (pos.toFloat() / mediaPlayerDuration) * DIVIDER
                    val progressI = ceil(progress).toInt()
                    val seconds = (pos / ONE_SEC)
                    _mediaPlayerSeekBarPosition.emit(progressI)
                    currentCycledMessage?.let { msg ->
                        msg.isPlayingVoiceMessage = true
                        msg.voiceMessageSeekbarProgress = progressI
                        msg.voiceMessagePlayedSeconds = seconds
                        if (progressI >= IS_PLAYED_CUTOFF) msg.wasPlayedVoiceMessage = true
                        _mediaPlayerSeekBarPositionMsg.emit(msg)
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

        for (pair in playQueue) {
            if (pair.first == path) return
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
            mediaPlayer!!.playbackParams.let { params ->
                params.setSpeed(speed.value)
                mediaPlayer!!.playbackParams = params
            }
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
                    if (playQueue.iterator().hasNext() && playQueue.first().first != currentDataSource) {
                        _managerState.value = MediaPlayerManagerState.SETUP
                        val nextPair = playQueue.iterator().next()
                        playQueue.removeAt(0)
                        mediaPlayer?.reset()
                        mediaPlayer?.setDataSource(nextPair.first)
                        currentCycledMessage = nextPair.second
                        prepare()
                    } else {
                        mediaPlayer?.release()
                        mediaPlayer = null
                        _backgroundPlayUIFlow.tryEmit(null)
                        currentCycledMessage?.let {
                            it.resetVoiceMessage = true
                            it.isPlayingVoiceMessage = false
                        }
                        runBlocking {
                            _mediaPlayerSeekBarPositionMsg.emit(currentCycledMessage!!)
                        }
                        currentCycledMessage = null
                        loop = false
                        _managerState.value = MediaPlayerManagerState.STOPPED
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(ChatActivity.TAG, "failed to initialize mediaPlayer", e)
            _managerState.value = MediaPlayerManagerState.ERROR
        }
    }

    private fun MediaPlayer.onPrepare() {
        mediaPlayerDuration = this.duration

        val playBackSpeed = if (currentCycledMessage?.actorId == null) {
            PlaybackSpeed.NORMAL.value
        } else {
            appPreferences.getPreferredPlayback(currentCycledMessage?.actorId).value
        }
        mediaPlayer!!.playbackParams.setSpeed(playBackSpeed)

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

    override fun handleOnResume() {
        if (mediaPlayer != null && mediaPlayer!!.isPlaying) {
            loop = true
        }
    }

    override fun handleOnStop() {
        loop = false
        if (mediaPlayer != null && currentCycledMessage != null && mediaPlayer!!.isPlaying) {
            CoroutineScope(Dispatchers.Default).launch {
                _backgroundPlayUIFlow.tryEmit(currentCycledMessage!!)
            }
        }
    }
}
