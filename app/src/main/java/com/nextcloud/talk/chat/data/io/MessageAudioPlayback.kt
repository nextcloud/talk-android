/*
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.chat.data.io

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MessageAudioKey(val accountId: Long, val roomToken: String, val messageId: Int) {
    val mediaId: String get() = "$accountId/$roomToken/$messageId"
}

data class MessageAudioTrack(
    val key: MessageAudioKey,
    val uri: String,
    val title: String,
    val conversation: String,
    val isVoice: Boolean,
    val threadId: Long? = null
)

data class MessageAudioState(
    val track: MessageAudioTrack? = null,
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val speed: Float = 1f,
    val ended: Boolean = false
) {
    val progress: Int
        get() = if (durationMs > 0) ((positionMs * PERCENT / durationMs).toInt()).coerceIn(0, PERCENT) else 0

    fun belongsTo(key: MessageAudioKey): Boolean = track?.key == key

    companion object {
        private const val PERCENT = 100
    }
}

interface MessageAudioPort {
    val mediaId: String?
    fun snapshot(track: MessageAudioTrack): MessageAudioState
    fun load(track: MessageAudioTrack, speed: Float)
    fun play()
    fun pause()
    fun clear()
    fun seekTo(positionMs: Long)
    fun setSpeed(speed: Float)
}

/** Main-thread state and commands for the single service-owned message player. */
class MessageAudioPlayback(private val canPlay: () -> Boolean = { true }) {
    private var port: MessageAudioPort? = null
    private var track: MessageAudioTrack? = null
    private var generation = 0L
    private var pendingKey: MessageAudioKey? = null
    private val mutableState = MutableStateFlow(MessageAudioState())
    val state = mutableState.asStateFlow()

    fun attach(player: MessageAudioPort?) {
        port = player
        if (player == null) {
            generation++
            pendingKey = null
            track = null
        }
        refresh()
    }

    fun beginRequest(key: MessageAudioKey): Long {
        generation++
        pendingKey = key
        if (track?.key != key) port?.pause()
        refresh()
        return generation
    }

    fun completeRequest(request: Long, newTrack: MessageAudioTrack, speed: Float): Boolean {
        val player = port
        if (player == null || request != generation || pendingKey != newTrack.key || !canPlay()) return false
        pendingKey = null
        track = newTrack
        player.load(newTrack, if (newTrack.isVoice) speed else 1f)
        player.play()
        refresh()
        return true
    }

    fun toggle(key: MessageAudioKey): Boolean {
        val player = port
        val current = track
        if (player == null || current == null || current.key != key || player.mediaId != key.mediaId) return false
        generation++
        pendingKey = null
        val snapshot = player.snapshot(current)
        if (snapshot.playWhenReady && !snapshot.ended) {
            player.pause()
        } else if (canPlay()) {
            if (snapshot.ended) player.seekTo(0)
            player.play()
        }
        refresh()
        return true
    }

    fun stop() {
        generation++
        pendingKey = null
        track = null
        port?.clear()
        refresh()
    }

    fun seek(key: MessageAudioKey, percent: Int) {
        if (track?.key != key || state.value.durationMs <= 0) return
        port?.seekTo(state.value.durationMs * percent.coerceIn(0, PERCENT) / PERCENT)
        refresh()
    }

    fun setSpeed(key: MessageAudioKey, speed: Float) {
        if (track?.key != key || track?.isVoice != true) return
        port?.setSpeed(speed)
        refresh()
    }

    fun refresh() {
        val current = track
        val player = port
        if (!canPlay()) {
            generation++
            pendingKey = null
            if (current != null && player?.snapshot(current)?.playWhenReady == true) player.pause()
        }
        mutableState.value = if (current != null && player?.mediaId == current.key.mediaId) {
            player.snapshot(current)
        } else {
            MessageAudioState()
        }
    }

    companion object {
        private const val PERCENT = 100
    }
}
