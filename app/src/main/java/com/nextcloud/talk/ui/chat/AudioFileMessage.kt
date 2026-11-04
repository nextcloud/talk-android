/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017-2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nextcloud.talk.R
import com.nextcloud.talk.chat.ui.model.ChatMessageUi
import com.nextcloud.talk.chat.ui.model.MessageTypeContent

private const val INACTIVE_TRACK_ALPHA = 0.4f

@Suppress("Detekt.LongMethod")
@Composable
fun AudioFileMessage(
    typeContent: MessageTypeContent.AudioFile,
    message: ChatMessageUi,
    isOneToOneConversation: Boolean = false,
    conversationThreadId: Long? = null,
    callbacks: ChatMessageCallbacks = ChatMessageCallbacks()
) {
    MessageScaffold(
        uiMessage = message,
        isOneToOneConversation = isOneToOneConversation,
        conversationThreadId = conversationThreadId,
        forceTimeBelow = true,
        content = {
            val audioState = chatAudioMessageState(message)
            var draggedProgress by remember { mutableStateOf<Float?>(null) }
            val seekDescription = stringResource(R.string.nc_audio_seek)

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
                    ChatAudioPlayPauseButton(
                        state = audioState,
                        contentDescription = stringResource(R.string.play_pause_audio_file),
                        onClick = { callbacks.onVoicePlayPauseClick(message.id) }
                    )

                    Slider(
                        value = draggedProgress ?: audioState.progress,
                        onValueChange = { draggedProgress = it },
                        onValueChangeFinished = {
                            draggedProgress?.let { callbacks.onVoiceSeek(message.id, it) }
                            draggedProgress = null
                        },
                        enabled = audioState.canSeek,
                        colors = SliderDefaults.colors(
                            thumbColor = colorScheme.primary,
                            activeTrackColor = colorScheme.primary,
                            inactiveTrackColor = colorScheme.onPrimaryContainer.copy(alpha = INACTIVE_TRACK_ALPHA)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(8.dp)
                            .semantics { contentDescription = seekDescription }
                    )
                }

                Text(
                    text = audioTimeText(audioState.displayedTimeMs, audioState.durationMs),
                    color = colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    )
}

private fun audioTimeText(displayedTimeMs: Long?, durationMs: Long): String =
    if (durationMs > 0L && displayedTimeMs != null && displayedTimeMs != durationMs) {
        "${formatAudioTime(displayedTimeMs)} / ${formatAudioTime(durationMs)}"
    } else {
        formatAudioTime(displayedTimeMs)
    }

@ChatMessagePreviews
@Composable
private fun AudioFileMessagePreview() {
    PreviewContainer {
        val content = MessageTypeContent.AudioFile(fileName = "podcast-episode-42.mp3")
        AudioFileMessage(
            typeContent = content,
            message = createBaseMessageWithoutCaption(content)
        )
    }
}
