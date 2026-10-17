/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.nextcloud.talk.chat.audio.ChatAudioKey
import com.nextcloud.talk.chat.audio.ChatAudioKind
import com.nextcloud.talk.chat.audio.ChatAudioPlaybackState
import com.nextcloud.talk.chat.audio.ChatAudioPlayer
import com.nextcloud.talk.chat.audio.ChatAudioSpeeds
import com.nextcloud.talk.ui.PlaybackSpeed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** The player bar while an audio message is loaded, and the full player once the bar is tapped. */
@Composable
fun ChatAudioPlayerHost(
    state: ChatAudioPlaybackState,
    callbacks: ChatAudioPlayerCallbacks,
    modifier: Modifier = Modifier
) {
    var isPlayerOpen by rememberSaveable { mutableStateOf(false) }
    val isLoaded = state.currentKey != null
    LaunchedEffect(isLoaded) {
        if (!isLoaded) {
            isPlayerOpen = false
        }
    }
    if (isLoaded) {
        ChatAudioPlayerBar(
            state = state,
            callbacks = callbacks,
            onOpenPlayer = { isPlayerOpen = true },
            modifier = modifier
        )
    }
    if (isLoaded && isPlayerOpen) {
        ChatAudioPlayerSheet(state = state, callbacks = callbacks, onDismiss = { isPlayerOpen = false })
    }
}

/**
 * Controls [player] from [ChatAudioPlayerHost]. A changed speed is saved for the account of the playing message
 * and reported to [onSpeedChanged].
 */
fun chatAudioPlayerCallbacks(
    player: ChatAudioPlayer,
    speeds: ChatAudioSpeeds,
    scope: CoroutineScope,
    onOpenMessage: (ChatAudioKey) -> Unit,
    onSpeedChanged: (ChatAudioKey, ChatAudioKind, PlaybackSpeed) -> Unit = { _, _, _ -> }
): ChatAudioPlayerCallbacks =
    ChatAudioPlayerCallbacks(
        onPlayPause = { player.togglePlayback() },
        onSeek = { fraction -> player.state.value.currentKey?.let { player.seekTo(it, fraction) } },
        onPrevious = { player.skipToPrevious() },
        onNext = { player.skipToNext() },
        onSpeedClick = {
            val state = player.state.value
            val key = state.currentKey
            val kind = state.kind
            if (key != null && kind != null) {
                val speed = speedOf(state.speed).next()
                player.setSpeed(kind, speed.value)
                onSpeedChanged(key, kind, speed)
                scope.launch(Dispatchers.IO) { speeds.save(key, kind, speed) }
            }
        },
        onShuffleClick = {
            val state = player.state.value
            player.setPlaybackModes(!state.shuffleEnabled, state.repeatMode)
        },
        onRepeatClick = {
            val state = player.state.value
            player.setPlaybackModes(state.shuffleEnabled, state.repeatMode.next())
        },
        onQueueItemClick = { player.playQueueItem(it) },
        onOpenMessage = { player.state.value.currentKey?.let(onOpenMessage) },
        onClose = { player.stop() }
    )
