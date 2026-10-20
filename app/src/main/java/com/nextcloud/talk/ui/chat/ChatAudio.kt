/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chat

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nextcloud.talk.R
import com.nextcloud.talk.chat.audio.ChatAudioKey
import com.nextcloud.talk.chat.audio.ChatAudioMessageState
import com.nextcloud.talk.chat.audio.ChatAudioMetadata
import com.nextcloud.talk.chat.audio.ChatAudioPlaybackState
import com.nextcloud.talk.chat.ui.model.ChatMessageUi
import com.nextcloud.talk.ui.PlaybackSpeed
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs

private const val MILLIS_PER_SECOND = 1000L
private const val UNKNOWN_TIME = "--:--"

/**
 * The audio playback as needed by audio message bubbles. Screens that play audio provide it via
 * [LocalChatAudioUi]; without it, audio messages are shown without playback state.
 */
@Stable
class ChatAudioUi(
    val playbackState: StateFlow<ChatAudioPlaybackState>,
    val metadata: StateFlow<Map<ChatAudioKey, ChatAudioMetadata>>,
    val voiceSpeed: StateFlow<PlaybackSpeed>,
    private val internalUserId: () -> Long?,
    private val loadMetadata: (ChatAudioKey) -> Unit
) {
    fun keyFor(message: ChatMessageUi): ChatAudioKey? {
        val userId = internalUserId()
        val roomToken = message.roomToken
        return if (userId == null || roomToken == null) null else ChatAudioKey(userId, roomToken, message.id)
    }

    fun load(key: ChatAudioKey) = loadMetadata(key)
}

val LocalChatAudioUi = staticCompositionLocalOf<ChatAudioUi?> { null }

/** The playback state of [message], only recomposing when it changes for this very message. */
@Composable
fun chatAudioMessageState(message: ChatMessageUi): ChatAudioMessageState {
    val audioUi = LocalChatAudioUi.current
    val key = remember(audioUi, message.roomToken, message.id) { audioUi?.keyFor(message) }
    if (audioUi == null || key == null) {
        return ChatAudioMessageState()
    }
    LaunchedEffect(audioUi, key) { audioUi.load(key) }
    val playbackState = audioUi.playbackState.collectAsState()
    val metadata = audioUi.metadata.collectAsState()
    val messageState by remember(key, playbackState, metadata) {
        derivedStateOf { playbackState.value.messageState(key, metadata.value[key]) }
    }
    return messageState
}

/** The waveform of the voice message [message], or null while it is not known yet. */
@Composable
fun chatAudioWaveform(message: ChatMessageUi): List<Float>? {
    val audioUi = LocalChatAudioUi.current
    val key = remember(audioUi, message.roomToken, message.id) { audioUi?.keyFor(message) }
    if (audioUi == null || key == null) {
        return null
    }
    val metadata = audioUi.metadata.collectAsState()
    val waveform by remember(key, metadata) { derivedStateOf { metadata.value[key]?.waveform } }
    return waveform
}

@Composable
fun chatAudioVoiceSpeed(): PlaybackSpeed {
    val voiceSpeed = LocalChatAudioUi.current?.voiceSpeed ?: return PlaybackSpeed.NORMAL
    val speed by voiceSpeed.collectAsState()
    return speed
}

/** The speed step that is closest to the playback speed [value]. */
fun speedOf(value: Float): PlaybackSpeed = PlaybackSpeed.entries.minBy { abs(it.value - value) }

/** Formats a playback time like the chat does, or a placeholder if the time is not known yet. */
fun formatAudioTime(timeMs: Long?): String =
    timeMs?.let { DateUtils.formatElapsedTime(it / MILLIS_PER_SECOND) } ?: UNKNOWN_TIME

/**
 * Play/pause button of an audio message. It shows a progress ring while the audio is loading and turns into a
 * retry button after an error.
 */
@Composable
fun ChatAudioPlayPauseButton(
    state: ChatAudioMessageState,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(48.dp)) {
        if (state.isBuffering) {
            CircularProgressIndicator(modifier = Modifier.size(44.dp), strokeWidth = 2.dp)
        }
        IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
            when {
                state.error != null -> Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.nc_audio_retry),
                    modifier = Modifier.size(32.dp)
                )

                state.isPlaying -> Icon(
                    imageVector = Icons.Filled.Pause,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(40.dp)
                )

                else -> Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
    }
}
