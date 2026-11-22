/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui

import android.os.Bundle
import android.text.format.DateUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.BidiFormatter
import androidx.core.text.TextDirectionHeuristicsCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nextcloud.talk.R
import com.nextcloud.talk.chat.ChatActivity
import com.nextcloud.talk.chat.data.io.MessageAudioController
import com.nextcloud.talk.chat.data.io.MessageAudioState
import com.nextcloud.talk.chat.data.io.MessageAudioTrack
import com.nextcloud.talk.ui.chat.ChatFontFamily
import com.nextcloud.talk.utils.bundle.BundleKeys

private const val MILLIS_PER_SECOND = 1000L
private const val PROGRESS_MAX = 100f

fun AppCompatActivity.openMessageAudioConversation(track: MessageAudioTrack) {
    val extras = Bundle().apply {
        putLong(BundleKeys.KEY_MESSAGE_ID, track.key.messageId.toLong())
        track.threadId?.let { putLong(BundleKeys.KEY_THREAD_ID, it) }
    }
    startActivity(ChatActivity.createIntent(this, track.key.accountId, track.key.roomToken, extras))
}

@Composable
fun MessageAudioBar(controller: MessageAudioController, onOpen: (MessageAudioTrack) -> Unit) {
    val state by controller.state.collectAsStateWithLifecycle()
    val track = state.track ?: return
    if (state.ended) return
    val speed = PlaybackSpeed.entries.firstOrNull { it.value == state.speed } ?: PlaybackSpeed.NORMAL
    MessageAudioBarContent(
        state = state,
        onToggle = { controller.playback.toggle(track.key) },
        onSpeed = { controller.playback.setSpeed(track.key, speed.next().value) },
        onStop = { controller.stop() },
        onOpen = { onOpen(track) }
    )
}

@Suppress("LongMethod")
@Composable
private fun MessageAudioBarContent(
    state: MessageAudioState,
    onToggle: () -> Unit,
    onSpeed: () -> Unit,
    onStop: () -> Unit,
    onOpen: () -> Unit
) {
    val track = state.track ?: return
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val progress = state.progress / PROGRESS_MAX
    val accent = MaterialTheme.colorScheme.primary
    val speed = PlaybackSpeed.entries.firstOrNull { it.value == state.speed } ?: PlaybackSpeed.NORMAL
    Surface(color = MaterialTheme.colorScheme.surface) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(horizontal = 4.dp)
                ) {
                    IconButton(onClick = onToggle, modifier = Modifier.size(48.dp)) {
                        Icon(
                            painterResource(
                                if (state.playWhenReady) {
                                    R.drawable.ic_baseline_pause_voice_message_24
                                } else {
                                    R.drawable.ic_baseline_play_arrow_voice_message_24
                                }
                            ),
                            contentDescription = stringResource(
                                if (track.isVoice) R.string.play_pause_voice_message else R.string.play_pause_audio_file
                            ),
                            tint = accent,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    MessageAudioBarDetails(
                        state = state,
                        isRtl = isRtl,
                        onOpen = onOpen,
                        modifier = Modifier.weight(1f)
                    )
                    if (track.isVoice) {
                        MessageAudioSpeedButton(label = speed.label, onClick = onSpeed)
                    }
                    IconButton(onClick = onStop, modifier = Modifier.size(48.dp)) {
                        Icon(
                            painterResource(R.drawable.ic_baseline_close_24),
                            contentDescription = stringResource(R.string.nc_stop_message_audio),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                        .drawBehind { drawRect(accent, size = size.copy(width = size.width * progress)) }
                        .semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f) }
                )
            }
        }
    }
}

@Composable
private fun MessageAudioBarDetails(
    state: MessageAudioState,
    isRtl: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val track = state.track ?: return
    val elapsed = DateUtils.formatElapsedTime(state.positionMs / MILLIS_PER_SECOND)
    val duration = DateUtils.formatElapsedTime(state.durationMs / MILLIS_PER_SECOND)
    val time = BidiFormatter.getInstance(isRtl).unicodeWrap("$elapsed / $duration", TextDirectionHeuristicsCompat.LTR)
    val textAlign = if (isRtl) TextAlign.Right else TextAlign.Left
    Column(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Text(
            text = track.title,
            style = TextStyle(
                fontFamily = ChatFontFamily,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium,
                textDirection = TextDirection.Content,
                textAlign = textAlign
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "${track.conversation} · $time",
            style = TextStyle(
                fontFamily = ChatFontFamily,
                fontSize = 11.sp,
                lineHeight = 17.sp,
                textDirection = TextDirection.Content,
                textAlign = textAlign
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
