/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017-2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.nextcloud.talk.R
import com.nextcloud.talk.chat.ui.model.ChatMessageUi
import com.nextcloud.talk.chat.ui.model.MessageTypeContent
import com.nextcloud.talk.ui.MessageAudioPlayButton
import com.nextcloud.talk.ui.MessageAudioTime

private const val SEEKBAR_MAX = 100f
private const val INACTIVE_TRACK_ALPHA = 0.4f

@Suppress("LongMethod")
@Composable
fun AudioFileMessage(
    typeContent: MessageTypeContent.AudioFile,
    message: ChatMessageUi,
    isOneToOneConversation: Boolean = false,
    conversationThreadId: Long? = null,
    onPlayPauseClick: (Int) -> Unit = {},
    onSeek: (messageId: Int, progress: Int) -> Unit = { _, _ -> }
) {
    MessageScaffold(
        uiMessage = message,
        isOneToOneConversation = isOneToOneConversation,
        conversationThreadId = conversationThreadId,
        forceTimeBelow = true,
        content = {
            Column {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        MessageAudioPlayButton(
                            isPlaying = typeContent.isPlaying,
                            isDownloading = typeContent.isDownloading,
                            description = stringResource(R.string.play_pause_audio_file),
                            onClick = { onPlayPauseClick(message.id) }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            AudioFileSeekBar(
                                progress = typeContent.seekbarProgress / SEEKBAR_MAX,
                                onSeek = { onSeek(message.id, (it * SEEKBAR_MAX).toInt()) }
                            )
                            MessageAudioTime(
                                playedSeconds = typeContent.playedSeconds,
                                durationSeconds = typeContent.durationSeconds,
                                isPlaying = typeContent.isPlaying
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_mimetype_audio),
                        contentDescription = null,
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp).size(20.dp)
                    )
                    AttachmentDetails(
                        message,
                        modifier = Modifier.weight(1f),
                        fallbackFileName = typeContent.fileName
                    )
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudioFileSeekBar(progress: Float, onSeek: (Float) -> Unit) {
    val accent = colorScheme.primary
    Slider(
        value = progress,
        onValueChange = onSeek,
        modifier = Modifier.fillMaxWidth(),
        track = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(colorScheme.onSurfaceVariant.copy(alpha = INACTIVE_TRACK_ALPHA), CircleShape)
                    .drawBehind { drawRect(accent, size = size.copy(width = size.width * progress)) }
            )
        },
        thumb = { Box(Modifier.size(8.dp).background(accent, CircleShape)) }
    )
}

@ChatMessagePreviews
@Composable
private fun AudioFileMessagePreview() {
    PreviewContainer {
        val audio = MessageTypeContent.AudioFile(
            fileName = "podcast-episode-42.mp3",
            isPlaying = false,
            isDownloading = false,
            durationSeconds = 245,
            playedSeconds = 60,
            seekbarProgress = 24
        )
        AudioFileMessage(typeContent = audio, message = createBaseMessageWithoutCaption(audio))
    }
}
