/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.call.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nextcloud.talk.R
import com.nextcloud.talk.call.ParticipantUiState
import com.nextcloud.talk.utils.ColorGenerator
import org.webrtc.EglBase

@Composable
fun ParticipantTile(
    participant: ParticipantUiState,
    eglBase: EglBase?,
    modifier: Modifier = Modifier,
    isVoiceOnlyCall: Boolean,
) {
    val colorInt = ColorGenerator.shared.usernameToColor(participant.nick)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(colorInt))
    ) {
        if (!isVoiceOnlyCall && participant.isStreamEnabled && participant.mediaStream != null) {
            WebRTCVideoView(participant, eglBase)
        } else {
            AvatarWithFallback(
                participant = participant,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        if (participant.raisedHand) {
            Icon(
                painter = painterResource(id = R.drawable.ic_hand_back_left),
                contentDescription = "Raised Hand",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(24.dp),
                tint = Color.White
            )
        }

        if (!participant.isAudioEnabled) {
            Icon(
                painter = painterResource(id = R.drawable.ic_mic_off_white_24px),
                contentDescription = "Mic Off",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(24.dp),
                tint = Color.White
            )
        }

        Text(
            text = participant.nick,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp),
            style = MaterialTheme.typography.bodyMedium.copy(
                shadow = Shadow(
                    color = Color.Black,
                    offset = Offset(4f, 4f),
                    blurRadius = 4f
                )
            )
        )

        if (!participant.isConnected) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Preview(showBackground = false)
@Composable
fun ParticipantTilePreview() {
    val participant = ParticipantUiState(
        sessionKey = "",
        nick = "testuser one",
        isConnected = true,
        isAudioEnabled = false,
        isStreamEnabled = true,
        raisedHand = true,
        avatarUrl = "",
        mediaStream = null
    )
    ParticipantTile(
        participant = participant,
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        eglBase = null,
        isVoiceOnlyCall = false
    )
}
