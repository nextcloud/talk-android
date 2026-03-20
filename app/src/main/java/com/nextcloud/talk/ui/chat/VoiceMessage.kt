/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chat

import android.text.format.DateUtils
import android.widget.SeekBar
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.nextcloud.talk.chat.ui.model.ChatMessageUi
import com.nextcloud.talk.chat.ui.model.MessageTypeContent
import com.nextcloud.talk.ui.WaveformSeekBar

private const val SEEKBAR_MAX = 100

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
            val inversePrimary = colorScheme.inversePrimary.toArgb()
            val onPrimaryContainer = colorScheme.onPrimaryContainer.toArgb()
            val remainingSeconds = (typeContent.durationSeconds - typeContent.playedSeconds).coerceAtLeast(0)
            val waveformData = typeContent.waveform.toFloatArray()
            val lastWaveformData = remember { mutableListOf<Float>() }

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
                                contentDescription = if (typeContent.isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    AndroidView(
                        factory = { ctx ->
                            WaveformSeekBar(ctx).apply {
                                max = SEEKBAR_MAX
                                setWaveData(waveformData)
                                setColors(
                                    inversePrimary,
                                    onPrimaryContainer
                                )
                                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                                        if (fromUser) {
                                            onSeek(message.id, progress)
                                        }
                                    }

                                    override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

                                    override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
                                })
                            }
                        },
                        update = { seekBar ->
                            seekBar.max = SEEKBAR_MAX
                            val waveformChanged = typeContent.waveform != lastWaveformData
                            if (waveformChanged) {
                                lastWaveformData.clear()
                                lastWaveformData.addAll(typeContent.waveform)
                                seekBar.setWaveData(waveformData)
                                seekBar.requestLayout()
                            }
                            seekBar.setColors(inversePrimary, onPrimaryContainer)
                            seekBar.progress = typeContent.seekbarProgress
                            seekBar.isEnabled = !typeContent.isDownloading
                            seekBar.invalidate()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                    )

                    TextButton(
                        onClick = { onSpeedClick(message.id) },
                        modifier = Modifier.padding(start = 4.dp),
                    ) {
                        Text(
                            text = typeContent.playbackSpeed.label,
                            color = colorScheme.onPrimaryContainer,
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
