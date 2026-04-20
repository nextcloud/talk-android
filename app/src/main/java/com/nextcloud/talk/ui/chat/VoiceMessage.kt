/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017-2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chat

import android.text.format.DateUtils
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nextcloud.talk.R
import com.nextcloud.talk.chat.ui.model.ChatMessageUi
import com.nextcloud.talk.chat.ui.model.MessageTypeContent
import com.nextcloud.talk.ui.ComposeWaveformSeekBar
import com.nextcloud.talk.ui.WAVEFORM_SIZE
import com.nextcloud.talk.utils.AudioUtils

private const val SEEKBAR_MAX = 100
private const val START_WAVE_FORM_HEIGHT = 10
private const val END_WAVE_FORM_HEIGHT = 56

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("Detekt.LongMethod", "LongParameterList")
@Composable
fun VoiceMessage(
    typeContent: MessageTypeContent.Voice,
    message: ChatMessageUi,
    isOneToOneConversation: Boolean = false,
    conversationThreadId: Long? = null,
    onPlayPauseClick: (Int) -> Unit = {},
    onSeek: (messageId: Int, progress: Int) -> Unit = { _, _ -> },
    onSpeedClick: (messageId: Int) -> Unit = {}
) {
    MessageScaffold(
        uiMessage = message,
        isOneToOneConversation = isOneToOneConversation,
        conversationThreadId = conversationThreadId,
        forceTimeBelow = true,
        content = {
            val inversePrimaryColor = colorScheme.inversePrimary
            remember(inversePrimaryColor) { inversePrimaryColor.toArgb() }
            val onPrimaryContainerColor = colorScheme.onPrimaryContainer
            remember(onPrimaryContainerColor) { onPrimaryContainerColor.toArgb() }
            val remainingSeconds = (typeContent.durationSeconds - typeContent.playedSeconds).coerceAtLeast(0)
            val waveformData = remember(typeContent.waveform) {
                val floatArr = typeContent.waveform.toFloatArray()
                if (floatArr.size < WAVEFORM_SIZE) {
                    FloatArray(WAVEFORM_SIZE)
                } else {
                    AudioUtils.shrinkFloatArray(floatArr, WAVEFORM_SIZE)
                }
            }

            val animValue by animateIntAsState(
                if (waveformData.average() > 0) END_WAVE_FORM_HEIGHT else START_WAVE_FORM_HEIGHT,
                label = "size"
            )

            Column {
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
                                imageVector = if (typeContent.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = stringResource(R.string.play_pause_voice_message),
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    var sliderValue by remember { mutableFloatStateOf(0f) }
                    sliderValue = typeContent.seekbarProgress * 1f / SEEKBAR_MAX

                    ComposeWaveformSeekBar(
                        sliderValue,
                        {
                            val progressI = (it * SEEKBAR_MAX).toInt()
                            onSeek(message.id, progressI)
                        },
                        modifier = Modifier
                            .height(animValue.dp)
                            .fillMaxWidth()
                            .padding(8.dp), // or weight(1f),
                        waveformData
                    )

                    TextButton(
                        onClick = { onSpeedClick(message.id) },
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Text(
                            text = typeContent.playbackSpeed.label,
                            color = colorScheme.onPrimaryContainer
                        )
                    }
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
