/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.call.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.ParticipantUiState
import org.webrtc.EglBase

@Composable
fun ScreenShareView(
    participantUiState: ParticipantUiState,
    eglBase: EglBase?,
    modifier: Modifier = Modifier,
    onCloseIconClick: () -> Unit?
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopEnd
    ) {
        if (participantUiState.isScreenStreamEnabled && participantUiState.screenMediaStream != null) {
            WebRTCVideoView(participantUiState.screenMediaStream, eglBase)
        }

        IconButton(
            onClick = { onCloseIconClick() },
            modifier = Modifier
                .padding(12.dp)
                .size(36.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.4f),
                    shape = CircleShape
                )
                .border(1.dp, Color.White.copy(alpha = 0.8f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.close),
                tint = Color.White
            )
        }
    }
}
