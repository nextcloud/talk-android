/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chat

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.ui.WaveformSeekBar
import kotlin.random.Random

private const val DEFAULT_WAVE_SIZE = 50

@Composable
fun VoiceMessage(message: ChatMessage, conversationThreadId: Long? = null, state: MutableState<Boolean>) {
    CommonMessageBody(
        message = message,
        conversationThreadId = conversationThreadId,
        playAnimation = state.value,
        content = {
            val inversePrimary = colorScheme.inversePrimary.toArgb()
            val onPrimaryContainer = colorScheme.onPrimaryContainer.toArgb()

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "play",
                    modifier = Modifier.size(24.dp)
                )

                AndroidView(
                    factory = { ctx ->
                        WaveformSeekBar(ctx).apply {
                            setWaveData(FloatArray(DEFAULT_WAVE_SIZE) { Random.nextFloat() })
                            setColors(
                                inversePrimary,
                                onPrimaryContainer
                            )
                        }
                    },
                    modifier = Modifier
                        .width(180.dp)
                        .height(80.dp)
                )
            }
        }
    )
}
