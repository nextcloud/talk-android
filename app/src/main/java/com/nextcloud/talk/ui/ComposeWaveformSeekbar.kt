/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

const val WAVEFORM_SIZE = 30
private const val INACTIVE_WAVEFORM_ALPHA = 0.4f
private const val PREVIEW_PROGRESS = 0.4f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeWaveformSeekBar(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier,
    waveData: FloatArray,
    enabled: Boolean
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = INACTIVE_WAVEFORM_ALPHA)
    Slider(
        value = value,
        enabled = enabled,
        onValueChange = onValueChange,
        modifier = modifier,
        track = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .drawWithCache {
                        val barWidth = 3.dp.toPx()
                        val midpoint = size.height / 2f
                        val halfBar = barWidth / 2f
                        val barGap = (size.width - barWidth) / (waveData.size - 1).coerceAtLeast(1)
                        onDrawBehind {
                            for (i in waveData.indices) {
                                val x = halfBar + i * barGap
                                val amplitude = waveData[i].takeIf { it.isFinite() } ?: 0f
                                val y = (amplitude * size.height).coerceIn(halfBar, midpoint)
                                drawLine(
                                    color = if (i < value * waveData.size) activeColor else inactiveColor,
                                    start = Offset(x, midpoint - y),
                                    end = Offset(x, midpoint + y),
                                    strokeWidth = barWidth,
                                    cap = StrokeCap.Round
                                )
                            }
                        }
                    }
            )
        },
        thumb = {}
    )
}

@Composable
@Preview
fun Preview() {
    val waveData = remember { FloatArray(WAVEFORM_SIZE) { (Math.random() % 1).toFloat() } }
    ComposeWaveformSeekBar(
        value = PREVIEW_PROGRESS,
        onValueChange = {},
        modifier = Modifier.fillMaxWidth(),
        waveData = waveData,
        enabled = true
    )
}
