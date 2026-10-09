/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nextcloud.talk.R
import com.nextcloud.talk.chat.audio.ChatAudioKey
import com.nextcloud.talk.chat.audio.ChatAudioKind
import com.nextcloud.talk.chat.audio.ChatAudioPlaybackState

/**
 * Compact player shown while an audio message is loaded, also if it belongs to another conversation. Tapping it
 * opens the full player.
 */
@Composable
fun ChatAudioPlayerBar(
    state: ChatAudioPlaybackState,
    callbacks: ChatAudioPlayerCallbacks,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val messageState = state.messageState(state.currentKey, null)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        onClickLabel = stringResource(R.string.nc_audio_player_open),
                        role = Role.Button,
                        onClick = onOpenPlayer
                    )
                    .padding(horizontal = 4.dp)
            ) {
                ChatAudioPlayPauseButton(
                    state = messageState,
                    contentDescription = stringResource(R.string.nc_audio_player_play_pause),
                    onClick = callbacks.onPlayPause
                )
                ChatAudioPlayerBarTitle(
                    title = state.title,
                    subtitle = "${state.subtitle} · ${formatAudioTime(messageState.displayedTimeMs)}",
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                )
                if (state.kind == ChatAudioKind.VOICE_MESSAGE) {
                    TextButton(onClick = callbacks.onSpeedClick) {
                        Text(text = speedOf(state.speed).label)
                    }
                }
                IconButton(onClick = callbacks.onClose) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.nc_audio_player_close)
                    )
                }
            }
            LinearProgressIndicator(
                progress = { messageState.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
            )
        }
    }
}

@Composable
private fun ChatAudioPlayerBarTitle(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** What the controls of [ChatAudioPlayerBar] and [ChatAudioPlayerSheet] do. */
data class ChatAudioPlayerCallbacks(
    val onPlayPause: () -> Unit = {},
    val onSeek: (Float) -> Unit = {},
    val onPrevious: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onSpeedClick: () -> Unit = {},
    val onShuffleClick: () -> Unit = {},
    val onRepeatClick: () -> Unit = {},
    val onQueueItemClick: (Int) -> Unit = {},
    val onOpenMessage: () -> Unit = {},
    val onClose: () -> Unit = {}
)

@Preview
@Composable
private fun ChatAudioPlayerBarPreview() {
    ChatAudioPlayerBar(
        state = ChatAudioPlaybackState(
            currentKey = ChatAudioKey(1L, "preview", 1),
            kind = ChatAudioKind.VOICE_MESSAGE,
            title = "John Doe",
            subtitle = "Voice message",
            isPlaying = true,
            positionMs = 12_000L,
            durationMs = 45_000L,
            speed = 1.5f
        ),
        callbacks = ChatAudioPlayerCallbacks(),
        onOpenPlayer = {}
    )
}
