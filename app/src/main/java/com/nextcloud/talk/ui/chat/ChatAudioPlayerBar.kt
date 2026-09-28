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
import com.nextcloud.talk.ui.PlaybackSpeed

/**
 * Compact player shown above the chat while an audio message is loaded, also if it belongs to another
 * conversation. Tapping it shows the message.
 */
@Composable
fun ChatAudioPlayerBar(
    state: ChatAudioPlaybackState,
    voiceSpeed: PlaybackSpeed,
    callbacks: ChatAudioPlayerBarCallbacks,
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
                        onClickLabel = stringResource(R.string.nc_audio_player_show_message),
                        role = Role.Button,
                        onClick = callbacks.onClick
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
                        Text(text = voiceSpeed.label)
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

data class ChatAudioPlayerBarCallbacks(
    val onPlayPause: () -> Unit = {},
    val onSpeedClick: () -> Unit = {},
    val onClose: () -> Unit = {},
    val onClick: () -> Unit = {}
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
            durationMs = 45_000L
        ),
        voiceSpeed = PlaybackSpeed.FASTER,
        callbacks = ChatAudioPlayerBarCallbacks()
    )
}
