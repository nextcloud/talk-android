/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017-2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.nextcloud.talk.R
import com.nextcloud.talk.chat.ui.model.ChatMessageUi
import com.nextcloud.talk.chat.ui.model.MessageTypeContent
import com.nextcloud.talk.ui.ComposeWaveformSeekBar
import com.nextcloud.talk.ui.MessageAudioPlayButton
import com.nextcloud.talk.ui.MessageAudioSpeedButton
import com.nextcloud.talk.ui.MessageAudioTime
import com.nextcloud.talk.ui.WAVEFORM_SIZE
import com.nextcloud.talk.utils.AudioUtils

private const val SEEKBAR_MAX = 100f

@Suppress("LongMethod", "LongParameterList")
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
            val waveformData = remember(typeContent.waveform) {
                val floatArr = typeContent.waveform.toFloatArray()
                if (floatArr.size < WAVEFORM_SIZE) {
                    FloatArray(WAVEFORM_SIZE)
                } else {
                    AudioUtils.shrinkFloatArray(floatArr, WAVEFORM_SIZE)
                }
            }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    MessageAudioPlayButton(
                        isPlaying = typeContent.isPlaying,
                        isDownloading = typeContent.isDownloading,
                        description = stringResource(R.string.play_pause_voice_message),
                        onClick = { onPlayPauseClick(message.id) }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        ComposeWaveformSeekBar(
                            value = typeContent.seekbarProgress / SEEKBAR_MAX,
                            onValueChange = { onSeek(message.id, (it * SEEKBAR_MAX).toInt()) },
                            modifier = Modifier.fillMaxWidth(),
                            waveData = waveformData,
                            enabled = true
                        )
                        MessageAudioTime(
                            playedSeconds = typeContent.playedSeconds,
                            durationSeconds = typeContent.durationSeconds,
                            isPlaying = typeContent.isPlaying
                        )
                    }
                    MessageAudioSpeedButton(
                        label = typeContent.playbackSpeed.label,
                        onClick = { onSpeedClick(message.id) }
                    )
                }
            }
        }
    )
}

@ChatMessagePreviews
@Composable
private fun VoiceMessagePreview() {
    PreviewContainer {
        val voice = MessageTypeContent.Voice(
            actorId = null,
            isPlaying = true,
            wasPlayed = false,
            isDownloading = false,
            durationSeconds = 50,
            playedSeconds = 18,
            seekbarProgress = 36,
            waveform = List(WAVEFORM_SIZE) { (it % 5 + 1) / 10f }
        )
        VoiceMessage(typeContent = voice, message = createBaseMessageWithoutCaption(voice))
    }
}
