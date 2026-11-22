/*
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.chat.data.io

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.nextcloud.talk.utils.singletons.ApplicationWideCurrentRoomHolder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import javax.inject.Inject
import javax.inject.Singleton

object MessageAudioPolicy {
    fun canPlay(context: Context): Boolean {
        val call = ApplicationWideCurrentRoomHolder.getInstance()
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return !call.isInCall &&
            !call.isDialing &&
            !MediaRecorderManager.isRecording &&
            audio.mode != AudioManager.MODE_IN_COMMUNICATION &&
            audio.mode != AudioManager.MODE_IN_CALL
    }
}

@Singleton
class MessageAudioController @Inject constructor(private val context: Context) {
    val playback = MessageAudioPlayback { MessageAudioPolicy.canPlay(context) }
    val state = playback.state
    private val scope = MainScope()
    private var future: ListenableFuture<MediaController>? = null
    private var connection: CompletableDeferred<Boolean>? = null
    private var controller: MediaController? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = playback.refresh()
    }

    fun stop() {
        playback.stop()
        disconnect()
    }

    suspend fun connect(): Boolean {
        connection?.let { return it.await() }
        val ready = CompletableDeferred<Boolean>()
        connection = ready
        val pending = MediaController.Builder(
            context.applicationContext,
            SessionToken(context, ComponentName(context, VoiceMessageMediaService::class.java))
        ).setListener(object : MediaController.Listener {
            override fun onDisconnected(controller: MediaController) {
                disconnect()
            }
        }).buildAsync()
        future = pending
        pending.addListener({
            if (future !== pending) return@addListener
            try {
                val player = pending.get()
                controller = player
                playback.attach(ControllerPort(player))
                player.addListener(listener)
                ready.complete(true)
                scope.launch {
                    while (controller === player) {
                        playback.refresh()
                        delay(PROGRESS_INTERVAL_MS)
                    }
                }
            } catch (_: ExecutionException) {
                disconnect()
            } catch (_: CancellationException) {
                disconnect()
            }
        }, ContextCompat.getMainExecutor(context))
        return ready.await()
    }

    private fun disconnect() {
        controller?.removeListener(listener)
        controller = null
        playback.attach(null)
        val previous = future
        future = null
        connection?.complete(false)
        connection = null
        previous?.let { MediaController.releaseFuture(it) }
    }

    private class ControllerPort(private val player: MediaController) : MessageAudioPort {
        override val mediaId: String? get() = player.currentMediaItem?.mediaId

        override fun snapshot(track: MessageAudioTrack) =
            MessageAudioState(
                track = track,
                isPlaying = player.isPlaying,
                playWhenReady = player.playWhenReady,
                positionMs = player.currentPosition.coerceAtLeast(0),
                durationMs = player.duration.coerceAtLeast(0),
                speed = player.playbackParameters.speed,
                ended = player.playbackState == Player.STATE_ENDED
            )

        override fun load(track: MessageAudioTrack, speed: Float) {
            player.setMediaItem(
                MediaItem.Builder().setMediaId(track.key.mediaId).setUri(track.uri.toUri())
                    .setMediaMetadata(
                        MediaMetadata.Builder().setTitle(track.title).setArtist(track.conversation).build()
                    ).build()
            )
            player.setPlaybackSpeed(speed)
            player.prepare()
        }

        override fun play() = player.play()
        override fun pause() = player.pause()
        override fun clear() {
            player.stop()
            player.clearMediaItems()
        }

        override fun seekTo(positionMs: Long) = player.seekTo(positionMs)
        override fun setSpeed(speed: Float) = player.setPlaybackSpeed(speed)
    }

    companion object {
        private const val PROGRESS_INTERVAL_MS = 250L
    }
}
