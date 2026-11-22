/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextcloud.talk.R
import com.nextcloud.talk.ui.chat.ChatFontFamily

private const val SPEED_BACKGROUND_ALPHA = 0.12f

@Composable
internal fun MessageAudioPlayButton(
    isPlaying: Boolean,
    isDownloading: Boolean,
    description: String,
    onClick: () -> Unit
) {
    Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        if (isDownloading) {
            CircularProgressIndicator(modifier = Modifier.size(36.dp), strokeWidth = 2.dp)
        } else {
            IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
                Box(
                    modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = description,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun MessageAudioSpeedButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.playback_speed_control)
    Box(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = description
                stateDescription = label
            }
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = ChatFontFamily,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                textDirection = TextDirection.Ltr
            ),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = SPEED_BACKGROUND_ALPHA),
                    RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 6.dp, vertical = 4.dp)
        )
    }
}

@Composable
internal fun MessageAudioTime(
    playedSeconds: Int,
    durationSeconds: Int,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val elapsed = DateUtils.formatElapsedTime(playedSeconds.coerceAtLeast(0).toLong())
    val duration = DateUtils.formatElapsedTime(durationSeconds.coerceAtLeast(0).toLong())
    Text(
        text = if (isPlaying || playedSeconds > 0) "$elapsed / $duration" else duration,
        style = TextStyle(
            fontFamily = ChatFontFamily,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            textDirection = TextDirection.Ltr
        ),
        color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}
