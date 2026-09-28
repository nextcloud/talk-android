/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.audio

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.nextcloud.talk.chat.data.io.VoiceMessageMediaService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException

/**
 * Connects a screen to [VoiceMessageMediaService] while the screen is started, exposes the playback as [state]
 * and starts, pauses, seeks and stops audio messages. Playback continues in the service when the screen is gone.
 */
class ChatAudioPlayer(context: Context, private val store: ChatAudioStore) : DefaultLifecycleObserver {

    private val context = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var pendingAction: ((MediaController) -> Unit)? = null
    private var isStarted = false

    private val _state = MutableStateFlow(ChatAudioPlaybackState())
    val state: StateFlow<ChatAudioPlaybackState> = _state.asStateFlow()

    private val progressUpdater = Runnable { updateState() }

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            updateState()
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        isStarted = true
        connect()
    }

    override fun onStop(owner: LifecycleOwner) {
        isStarted = false
        disconnect()
    }

    /**
     * Pauses or resumes [track] if it is loaded; otherwise starts playing [queue], which begins with [track],
     * from the position the message was left at.
     */
    fun playOrPause(track: ChatAudioTrack, queue: List<ChatAudioTrack>, speed: Float) {
        runWithController { controller ->
            if (controller.chatAudioKey() == track.key) {
                controller.pauseOrResume()
            } else {
                startQueue(controller, queue.ifEmpty { listOf(track) }, speed)
            }
        }
    }

    /** Pauses or resumes whatever is loaded. */
    fun togglePlayback() {
        runWithController { it.pauseOrResume() }
    }

    /** Seeks the message to [fraction] of its duration, or remembers it as start position if it is not loaded. */
    fun seekTo(key: ChatAudioKey, fraction: Float) {
        val controller = controller
        if (controller != null && controller.chatAudioKey() == key && controller.duration > 0L) {
            controller.seekTo((controller.duration * fraction).toLong())
        } else {
            val durationMs = store.metadataFor(key)?.durationMs ?: 0L
            if (durationMs > 0L) {
                store.rememberPosition(key, (durationMs * fraction).toLong(), durationMs)
            }
        }
        updateState()
    }

    /** Applies [speed] if a message of [kind] is loaded. */
    fun setSpeed(kind: ChatAudioKind, speed: Float) {
        controller?.takeIf { _state.value.kind == kind }?.setPlaybackSpeed(speed)
    }

    fun pause() {
        controller?.pause()
    }

    /** Stops playback and unloads the queue, which also removes the media notification. */
    fun stop() {
        controller?.let { controller ->
            rememberPosition(controller)
            controller.stop()
            controller.clearMediaItems()
        }
    }

    private fun startQueue(controller: MediaController, queue: List<ChatAudioTrack>, speed: Float) {
        rememberPosition(controller)
        val startPositionMs = store.metadataFor(queue.first().key)?.resumePosition ?: 0L
        controller.setMediaItems(queue.map { it.toMediaItem() }, 0, startPositionMs)
        controller.setPlaybackSpeed(speed)
        controller.prepare()
        controller.play()
    }

    private fun rememberPosition(controller: MediaController) {
        val key = controller.chatAudioKey()
        if (key != null && controller.playbackState != Player.STATE_ENDED) {
            store.rememberPosition(key, controller.currentPosition, controller.duration)
        }
    }

    private fun connect() {
        val sessionToken = SessionToken(context, ComponentName(context, VoiceMessageMediaService::class.java))
        val future = MediaController.Builder(context, sessionToken)
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(controller: MediaController) {
                    if (controller === this@ChatAudioPlayer.controller) {
                        disconnect()
                        _state.value = ChatAudioPlaybackState()
                    }
                }
            })
            .buildAsync()
        controllerFuture = future
        future.addListener(
            {
                if (future === controllerFuture) {
                    val connected = future.controllerOrNull()
                    if (connected == null) {
                        // Allows the next action to connect again.
                        controllerFuture = null
                    } else {
                        controller = connected
                        connected.addListener(playerListener)
                        pendingAction?.invoke(connected)
                        updateState()
                    }
                    pendingAction = null
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    private fun disconnect() {
        handler.removeCallbacks(progressUpdater)
        controller?.removeListener(playerListener)
        controller = null
        pendingAction = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
    }

    private fun runWithController(action: (MediaController) -> Unit) {
        val connected = controller
        if (connected == null) {
            pendingAction = action
            if (controllerFuture == null && isStarted) {
                connect()
            }
        } else {
            action(connected)
            updateState()
        }
    }

    private fun updateState() {
        handler.removeCallbacks(progressUpdater)
        val controller = controller ?: return
        _state.value = controller.toPlaybackState()
        if (controller.isPlaying) {
            handler.postDelayed(progressUpdater, PROGRESS_UPDATE_INTERVAL_MS)
        }
    }

    companion object {
        private const val PROGRESS_UPDATE_INTERVAL_MS = 100L
    }
}

private val TAG = ChatAudioPlayer::class.java.simpleName

private fun ListenableFuture<MediaController>.controllerOrNull(): MediaController? =
    try {
        get()
    } catch (e: ExecutionException) {
        Log.w(TAG, "Failed to connect to the chat audio playback", e)
        null
    } catch (e: CancellationException) {
        Log.d(TAG, "Connection to the chat audio playback was cancelled", e)
        null
    }

private fun Player.chatAudioKey(): ChatAudioKey? = ChatAudioKey.fromMediaId(currentMediaItem?.mediaId)

/** True while playback is requested and not held back by an error, the end of the queue or another app. */
private fun Player.wantsToPlay(): Boolean =
    playWhenReady &&
        playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE &&
        playerError == null &&
        playbackState != Player.STATE_ENDED

private fun Player.pauseOrResume() {
    if (wantsToPlay()) {
        pause()
    } else {
        when (playbackState) {
            Player.STATE_IDLE -> prepare()
            Player.STATE_ENDED -> seekToDefaultPosition()
        }
        play()
    }
}

private fun Player.toPlaybackState(): ChatAudioPlaybackState {
    val mediaItem: MediaItem? = currentMediaItem
    val key = chatAudioKey()
    return if (mediaItem == null || key == null) {
        ChatAudioPlaybackState()
    } else {
        ChatAudioPlaybackState(
            currentKey = key,
            kind = mediaItem.chatAudioKind(),
            title = mediaItem.mediaMetadata.title?.toString().orEmpty(),
            subtitle = mediaItem.mediaMetadata.artist?.toString().orEmpty(),
            isPlaying = wantsToPlay(),
            isBuffering = wantsToPlay() && playbackState == Player.STATE_BUFFERING,
            positionMs = currentPosition.coerceAtLeast(0L),
            durationMs = duration.coerceAtLeast(0L),
            speed = playbackParameters.speed,
            error = playerError?.toChatAudioError()
        )
    }
}
