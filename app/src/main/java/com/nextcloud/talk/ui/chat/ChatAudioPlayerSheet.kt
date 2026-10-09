/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nextcloud.talk.R
import com.nextcloud.talk.chat.audio.ChatAudioKey
import com.nextcloud.talk.chat.audio.ChatAudioKind
import com.nextcloud.talk.chat.audio.ChatAudioMessageState
import com.nextcloud.talk.chat.audio.ChatAudioPlaybackState
import com.nextcloud.talk.chat.audio.ChatAudioQueueItem
import com.nextcloud.talk.chat.audio.ChatAudioRepeatMode

private val minHeightForPinnedPlayer = 560.dp

/** The full player with all controls and the queue, shown on top of the current screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatAudioPlayerSheet(state: ChatAudioPlaybackState, callbacks: ChatAudioPlayerCallbacks, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        ChatAudioPlayerSheetContent(
            state = state,
            callbacks = callbacks.copy(
                onOpenMessage = {
                    onDismiss()
                    callbacks.onOpenMessage()
                }
            )
        )
    }
}

/**
 * Keeps the player in place above the queue, which scrolls on its own. In a lower window, e.g. on a phone in
 * landscape, the player scrolls together with the queue instead, so that every control stays reachable.
 */
@Composable
private fun ChatAudioPlayerSheetContent(
    state: ChatAudioPlaybackState,
    callbacks: ChatAudioPlayerCallbacks,
    modifier: Modifier = Modifier
) {
    val hasQueue = state.queue.size > 1
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        if (hasQueue && maxHeight >= minHeightForPinnedPlayer) {
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                ChatAudioSheetPlayer(state = state, callbacks = callbacks)
                ChatAudioQueueHeader()
                ChatAudioQueueList(state = state, callbacks = callbacks, modifier = Modifier.weight(1f, fill = false))
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
                item {
                    ChatAudioSheetPlayer(state = state, callbacks = callbacks)
                }
                if (hasQueue) {
                    item {
                        ChatAudioQueueHeader()
                    }
                    queueItems(state = state, callbacks = callbacks)
                }
            }
        }
    }
}

/** Opens at the current item, so that it is visible together with the items that follow it. */
@Composable
private fun ChatAudioQueueList(
    state: ChatAudioPlaybackState,
    callbacks: ChatAudioPlayerCallbacks,
    modifier: Modifier = Modifier
) {
    val currentIndex = state.queue.indexOfFirst { it.key == state.currentKey }
    LazyColumn(
        state = rememberLazyListState(initialFirstVisibleItemIndex = currentIndex.coerceAtLeast(0)),
        contentPadding = PaddingValues(bottom = 24.dp),
        modifier = modifier
    ) {
        queueItems(state = state, callbacks = callbacks)
    }
}

private fun LazyListScope.queueItems(state: ChatAudioPlaybackState, callbacks: ChatAudioPlayerCallbacks) {
    items(state.queue, key = { it.key.mediaId }) { item ->
        ChatAudioQueueRow(
            item = item,
            kind = state.kind,
            isCurrent = item.key == state.currentKey,
            onClick = { callbacks.onQueueItemClick(item.index) }
        )
    }
}

@Composable
private fun ChatAudioQueueHeader() {
    Column {
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text(
            text = stringResource(R.string.nc_audio_player_queue),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(vertical = 8.dp)
        )
    }
}

@Composable
private fun ChatAudioSheetPlayer(state: ChatAudioPlaybackState, callbacks: ChatAudioPlayerCallbacks) {
    val messageState = state.messageState(state.currentKey, null)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(
                imageVector = kindIcon(state.kind),
                contentDescription = null,
                modifier = Modifier
                    .padding(16.dp)
                    .size(40.dp)
            )
        }
        Text(
            text = state.title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = state.subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ChatAudioSheetSeekBar(messageState = messageState, onSeek = callbacks.onSeek)
        ChatAudioSheetTransport(state = state, messageState = messageState, callbacks = callbacks)
        ChatAudioSheetOptions(state = state, callbacks = callbacks)
        TextButton(onClick = callbacks.onOpenMessage) {
            Text(text = stringResource(R.string.nc_audio_player_show_message))
        }
    }
}

@Composable
private fun ChatAudioQueueRow(item: ChatAudioQueueItem, kind: ChatAudioKind?, isCurrent: Boolean, onClick: () -> Unit) {
    val color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClickLabel = stringResource(R.string.nc_audio_player_play_pause),
                role = Role.Button,
                onClick = onClick
            )
            .semantics { selected = isCurrent }
            .padding(vertical = 8.dp)
    ) {
        Icon(
            imageVector = if (isCurrent) Icons.Filled.PlayArrow else kindIcon(kind),
            contentDescription = null,
            tint = if (isCurrent) color else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ChatAudioSheetSeekBar(messageState: ChatAudioMessageState, onSeek: (Float) -> Unit) {
    var draggedProgress by remember { mutableStateOf<Float?>(null) }
    val seekDescription = stringResource(R.string.nc_audio_seek)
    val shownPositionMs = draggedProgress?.let { (it * messageState.durationMs).toLong() } ?: messageState.positionMs
    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = draggedProgress ?: messageState.progress,
            onValueChange = { draggedProgress = it },
            onValueChangeFinished = {
                draggedProgress?.let(onSeek)
                draggedProgress = null
            },
            enabled = messageState.canSeek,
            modifier = Modifier.semantics { contentDescription = seekDescription }
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = formatAudioTime(shownPositionMs), style = MaterialTheme.typography.bodySmall)
            Text(
                text = formatAudioTime(messageState.durationMs.takeIf { it > 0L }),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ChatAudioSheetTransport(
    state: ChatAudioPlaybackState,
    messageState: ChatAudioMessageState,
    callbacks: ChatAudioPlayerCallbacks
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        IconButton(onClick = callbacks.onPrevious) {
            Icon(
                imageVector = Icons.Filled.SkipPrevious,
                contentDescription = stringResource(R.string.nc_audio_player_previous)
            )
        }
        ChatAudioPlayPauseButton(
            state = messageState,
            contentDescription = stringResource(R.string.nc_audio_player_play_pause),
            onClick = callbacks.onPlayPause
        )
        IconButton(onClick = callbacks.onNext, enabled = state.hasNext) {
            Icon(
                imageVector = Icons.Filled.SkipNext,
                contentDescription = stringResource(R.string.nc_audio_player_next)
            )
        }
    }
}

/** Speed for every message; shuffle and repeat only for audio files, as voice messages are listened to in order. */
@Composable
private fun ChatAudioSheetOptions(state: ChatAudioPlaybackState, callbacks: ChatAudioPlayerCallbacks) {
    val isAudioFile = state.kind == ChatAudioKind.AUDIO_FILE
    val speedDescription = stringResource(R.string.playback_speed_control)
    val speedLabel = speedOf(state.speed).label
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (isAudioFile) {
            IconToggleButton(checked = state.shuffleEnabled, onCheckedChange = { callbacks.onShuffleClick() }) {
                Icon(
                    imageVector = Icons.Filled.Shuffle,
                    contentDescription = stringResource(R.string.nc_audio_player_shuffle),
                    tint = activeTint(state.shuffleEnabled)
                )
            }
        }
        TextButton(
            onClick = callbacks.onSpeedClick,
            modifier = Modifier.semantics {
                contentDescription = speedDescription
                stateDescription = speedLabel
            }
        ) {
            Text(text = speedLabel)
        }
        if (isAudioFile) {
            IconButton(onClick = callbacks.onRepeatClick) {
                Icon(
                    imageVector = if (state.repeatMode == ChatAudioRepeatMode.ONE) {
                        Icons.Filled.RepeatOne
                    } else {
                        Icons.Filled.Repeat
                    },
                    contentDescription = stringResource(repeatModeDescription(state.repeatMode)),
                    tint = activeTint(state.repeatMode != ChatAudioRepeatMode.OFF)
                )
            }
        }
    }
}

@Composable
private fun activeTint(isActive: Boolean) =
    if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

private fun kindIcon(kind: ChatAudioKind?) =
    if (kind == ChatAudioKind.AUDIO_FILE) Icons.Filled.MusicNote else Icons.Filled.Mic

private fun repeatModeDescription(repeatMode: ChatAudioRepeatMode): Int =
    when (repeatMode) {
        ChatAudioRepeatMode.OFF -> R.string.nc_audio_player_repeat_off
        ChatAudioRepeatMode.ALL -> R.string.nc_audio_player_repeat_all
        ChatAudioRepeatMode.ONE -> R.string.nc_audio_player_repeat_one
    }

@Preview(heightDp = 720)
@Composable
private fun ChatAudioPlayerSheetContentPreview() {
    val queue = listOf("intro.mp3", "podcast-episode-42.mp3", "outro.mp3").mapIndexed { index, title ->
        ChatAudioQueueItem(index, ChatAudioKey(1L, "preview", index + 1), title, "John Doe")
    }
    Surface {
        ChatAudioPlayerSheetContent(
            state = ChatAudioPlaybackState(
                currentKey = queue[1].key,
                kind = ChatAudioKind.AUDIO_FILE,
                title = queue[1].title,
                subtitle = queue[1].subtitle,
                isPlaying = true,
                positionMs = 754_000L,
                durationMs = 2_710_000L,
                shuffleEnabled = true,
                repeatMode = ChatAudioRepeatMode.ALL,
                hasNext = true,
                queue = queue
            ),
            callbacks = ChatAudioPlayerCallbacks()
        )
    }
}
