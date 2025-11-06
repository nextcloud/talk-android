/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.call.components

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.unit.min
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.ParticipantUiState
import com.nextcloud.talk.utils.ColorGenerator
import org.webrtc.EglBase
import kotlin.String

const val NICK_OFFSET = 4f
const val NICK_BLUR_RADIUS = 4f
const val AVATAR_SIZE_FACTOR = 0.6f

@SuppressLint("UnusedBoxWithConstraintsScope")
@Suppress("Detekt.LongMethod")
@Composable
fun ParticipantTile(
    participantUiState: ParticipantUiState,
    eglBase: EglBase?,
    modifier: Modifier = Modifier,
    isVoiceOnlyCall: Boolean,
    onScreenShareIconClick: ((String?) -> Unit?)?
) {
    val colorInt = ColorGenerator.usernameToColor(participantUiState.nick!!)

    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(colorInt))
    ) {
        val avatarSize = min(maxWidth, maxHeight) * AVATAR_SIZE_FACTOR

        if (!isVoiceOnlyCall && participantUiState.isStreamEnabled && participantUiState.mediaStream != null) {
            WebRTCVideoView(participantUiState.mediaStream, eglBase)
        } else {
            AvatarWithFallback(
                participant = participantUiState,
                modifier = Modifier
                    .size(avatarSize)
                    .align(Alignment.Center)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
            ) {
                if (participantUiState.isScreenStreamEnabled) {
                    Icon(
                        painter = painterResource(id = R.drawable.outline_monitor_24),
                        contentDescription = "Mic Off",
                        modifier = Modifier
                            .padding(6.dp)
                            .size(24.dp)
                            .clickable {
                                onScreenShareIconClick?.invoke(participantUiState.sessionKey)
                            },
                        tint = Color.White
                    )
                }

                if (!participantUiState.isAudioEnabled) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_mic_off_white_24px),
                        contentDescription = "Mic Off",
                        modifier = Modifier
                            .padding(6.dp)
                            .size(24.dp),
                        tint = Color.White
                    )
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
            ) {
                if (participantUiState.raisedHand) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_hand_back_left),
                        contentDescription = "Raised Hand",
                        modifier = Modifier
                            .padding(6.dp)
                            .size(24.dp),
                        tint = Color.White
                    )
                }

                Text(
                    text = participantUiState.nick,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        shadow = Shadow(
                            color = Color.Black,
                            offset = Offset(NICK_OFFSET, NICK_OFFSET),
                            blurRadius = NICK_BLUR_RADIUS
                        )
                    )
                )
            }


            if (!participantUiState.isConnected) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

@Preview(showBackground = false)
@Composable
fun ParticipantTilePreview() {
    val participant = ParticipantUiState(
        sessionKey = "",
        baseUrl = "",
        roomToken = "",
        nick = "testuser one",
        isConnected = true,
        isAudioEnabled = false,
        isStreamEnabled = true,
        isScreenStreamEnabled = true,
        raisedHand = true,
        mediaStream = null,
        actorType = null,
        actorId = null,
        isInternal = false
    )
    ParticipantTile(
        participantUiState = participant,
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        eglBase = null,
        isVoiceOnlyCall = false,
        onScreenShareIconClick = null
    )
}
