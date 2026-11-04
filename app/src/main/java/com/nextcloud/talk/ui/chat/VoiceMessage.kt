/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017-2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chat

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nextcloud.talk.R
import com.nextcloud.talk.chat.audio.Waveforms
import com.nextcloud.talk.chat.ui.model.ChatMessageUi
import com.nextcloud.talk.ui.ComposeWaveformSeekBar
import com.nextcloud.talk.ui.WAVEFORM_SIZE

private const val START_WAVE_FORM_HEIGHT = 10
private const val END_WAVE_FORM_HEIGHT = 56

@Suppress("Detekt.LongMethod")
@Composable
fun VoiceMessage(
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
            val waveform = chatAudioWaveform(message)
            val speed = chatAudioVoiceSpeed()
            val waveformData = remember(waveform) {
                waveform?.let { Waveforms.reduce(it.toFloatArray(), WAVEFORM_SIZE) } ?: FloatArray(WAVEFORM_SIZE)
            }
            val waveformHeight by animateIntAsState(
                if (waveformData.any { it > 0f }) END_WAVE_FORM_HEIGHT else START_WAVE_FORM_HEIGHT,
                label = "size"
            )
            var draggedProgress by remember { mutableStateOf<Float?>(null) }
            val seekDescription = stringResource(R.string.nc_audio_seek)

            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ChatAudioPlayPauseButton(
                        state = audioState,
                        contentDescription = stringResource(R.string.play_pause_voice_message),
                        onClick = { callbacks.onVoicePlayPauseClick(message.id) }
                    )

                    Box(modifier = Modifier.weight(1f)) {
                        ComposeWaveformSeekBar(
                            value = draggedProgress ?: audioState.progress,
                            onValueChange = { draggedProgress = it },
                            modifier = Modifier
                                .height(waveformHeight.dp)
                                .fillMaxWidth()
                                .padding(8.dp),
                            waveData = waveformData,
                            enabled = audioState.canSeek,
                            onValueChangeFinished = {
                                draggedProgress?.let { callbacks.onVoiceSeek(message.id, it) }
                                draggedProgress = null
                            },
                            sliderModifier = Modifier.semantics { contentDescription = seekDescription }
                        )
                    }

                    TextButton(
                        onClick = { callbacks.onVoiceSpeedClick(message.id) },
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Text(
                            text = speed.label,
                            color = colorScheme.onPrimaryContainer
                        )
                    }
                }

                Text(
                    text = formatAudioTime(audioState.displayedTimeMs),
                    color = colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    )
}
