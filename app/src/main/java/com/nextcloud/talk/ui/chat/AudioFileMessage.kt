/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017-2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chat

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nextcloud.talk.R
import com.nextcloud.talk.chat.ui.model.ChatMessageUi
import com.nextcloud.talk.chat.ui.model.MessageTypeContent

private const val SEEKBAR_MAX = 100
private const val INACTIVE_TRACK_ALPHA = 0.4f

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("Detekt.LongMethod")
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
            val remainingSeconds = (typeContent.durationSeconds - typeContent.playedSeconds)
            val icon = if (typeContent.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow

            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_mimetype_audio),
                        contentDescription = null,
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .size(24.dp)
                    )

                    Text(
                        text = typeContent.fileName,
                        color = colorScheme.onPrimaryContainer,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (typeContent.isDownloading) {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(
                            onClick = { onPlayPauseClick(message.id) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = stringResource(R.string.play_pause_audio_file),
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    var sliderValue by remember { mutableFloatStateOf(0f) }
                    sliderValue = typeContent.seekbarProgress * 1f / SEEKBAR_MAX

                    Slider(
                        value = sliderValue,
                        onValueChange = {
                            val progressI = (it * SEEKBAR_MAX).toInt()
                            onSeek(message.id, progressI)
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = colorScheme.primary,
                            activeTrackColor = colorScheme.primary,
                            inactiveTrackColor = colorScheme.onPrimaryContainer.copy(alpha = INACTIVE_TRACK_ALPHA)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    )
                }

                Text(
                    text = DateUtils.formatElapsedTime(remainingSeconds.toLong()),
                    color = colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    )
}

@ChatMessagePreviews
@Composable
private fun AudioFileMessagePreview() {
    PreviewContainer {
        AudioFileMessage(
            typeContent = MessageTypeContent.AudioFile(
                fileName = "podcast-episode-42.mp3",
                isPlaying = false,
                isDownloading = false,
                durationSeconds = 245,
                playedSeconds = 60,
                seekbarProgress = 24
            ),
            message = createBaseMessageWithoutCaption(
                MessageTypeContent.AudioFile(
                    fileName = "podcast-episode-42.mp3",
                    isPlaying = false,
                    isDownloading = false,
                    durationSeconds = 245,
                    playedSeconds = 60,
                    seekbarProgress = 24
                )
            )
        )
    }
}
