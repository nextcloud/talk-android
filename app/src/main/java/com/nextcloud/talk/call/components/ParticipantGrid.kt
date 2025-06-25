/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:Suppress("MagicNumber", "TooManyFunctions")

package com.nextcloud.talk.call.components

import android.annotation.SuppressLint
import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nextcloud.talk.adapters.ParticipantUiState
import org.webrtc.EglBase
import kotlin.math.ceil

@SuppressLint("UnusedBoxWithConstraintsScope")
@Suppress("LongParameterList")
@Composable
fun ParticipantGrid(
    modifier: Modifier = Modifier,
    eglBase: EglBase?,
    participantUiStates: List<ParticipantUiState>,
    isVoiceOnlyCall: Boolean,
    onClick: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    val minItemHeight = 100.dp

    if (participantUiStates.isEmpty()) return

    val columns = if (isPortrait) {
        when (participantUiStates.size) {
            1, 2, 3 -> 1
            else -> 2
        }
    } else {
        when (participantUiStates.size) {
            1 -> 1
            2, 4 -> 2
            else -> 3
        }
    }.coerceAtLeast(1) // Prevent 0

    val rows = ceil(participantUiStates.size / columns.toFloat()).toInt().coerceAtLeast(1)

    val itemSpacing = 8.dp
    val edgePadding = 8.dp
    val totalVerticalSpacing = itemSpacing * (rows - 1)
    val totalVerticalPadding = edgePadding * 2

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clickable { onClick() }
    ) {
        val availableHeight = maxHeight

        val gridAvailableHeight = availableHeight - totalVerticalSpacing - totalVerticalPadding
        val rawItemHeight = gridAvailableHeight / rows
        val itemHeight = maxOf(rawItemHeight, minItemHeight)

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier
                .fillMaxWidth()
                .height(availableHeight),
            verticalArrangement = Arrangement.spacedBy(itemSpacing),
            horizontalArrangement = Arrangement.spacedBy(itemSpacing),
            contentPadding = PaddingValues(vertical = edgePadding, horizontal = edgePadding)
        ) {
            items(
                participantUiStates,
                key = { it.sessionKey }
            ) { participant ->
                ParticipantTile(
                    participantUiState = participant,
                    modifier = Modifier
                        .height(itemHeight)
                        .fillMaxWidth(),
                    eglBase = eglBase,
                    isVoiceOnlyCall = isVoiceOnlyCall
                )
            }
        }
    }
}

@Preview
@Composable
fun ParticipantGridPreview() {
    ParticipantGrid(
        participantUiStates = getTestParticipants(1),
        eglBase = null,
        isVoiceOnlyCall = false
    ) {}
}

@Preview
@Composable
fun TwoParticipants() {
    ParticipantGrid(
        participantUiStates = getTestParticipants(2),
        eglBase = null,
        isVoiceOnlyCall = false
    ) {}
}

@Preview
@Composable
fun ThreeParticipants() {
    ParticipantGrid(
        participantUiStates = getTestParticipants(3),
        eglBase = null,
        isVoiceOnlyCall = false
    ) {}
}

@Preview
@Composable
fun FourParticipants() {
    ParticipantGrid(
        participantUiStates = getTestParticipants(4),
        eglBase = null,
        isVoiceOnlyCall = false
    ) {}
}

@Preview
@Composable
fun FiveParticipants() {
    ParticipantGrid(
        participantUiStates = getTestParticipants(5),
        eglBase = null,
        isVoiceOnlyCall = false
    ) {}
}

@Preview
@Composable
fun SevenParticipants() {
    ParticipantGrid(
        participantUiStates = getTestParticipants(7),
        eglBase = null,
        isVoiceOnlyCall = false
    ) {}
}

@Preview
@Composable
fun FiftyParticipants() {
    ParticipantGrid(
        participantUiStates = getTestParticipants(50),
        eglBase = null,
        isVoiceOnlyCall = false
    ) {}
}

@Preview(
    showBackground = false,
    heightDp = 360,
    widthDp = 800
)
@Composable
fun OneParticipantLandscape() {
    ParticipantGrid(
        participantUiStates = getTestParticipants(1),
        eglBase = null,
        isVoiceOnlyCall = false
    ) {}
}

@Preview(
    showBackground = false,
    heightDp = 360,
    widthDp = 800
)
@Composable
fun TwoParticipantsLandscape() {
    ParticipantGrid(
        participantUiStates = getTestParticipants(2),
        eglBase = null,
        isVoiceOnlyCall = false
    ) {}
}

@Preview(
    showBackground = false,
    heightDp = 360,
    widthDp = 800
)
@Composable
fun ThreeParticipantsLandscape() {
    ParticipantGrid(
        participantUiStates = getTestParticipants(3),
        eglBase = null,
        isVoiceOnlyCall = false
    ) {}
}

@Preview(
    showBackground = false,
    heightDp = 360,
    widthDp = 800
)
@Composable
fun FourParticipantsLandscape() {
    ParticipantGrid(
        participantUiStates = getTestParticipants(4),
        eglBase = null,
        isVoiceOnlyCall = false
    ) {}
}

@Preview(
    showBackground = false,
    heightDp = 360,
    widthDp = 800
)
@Composable
fun SevenParticipantsLandscape() {
    ParticipantGrid(
        participantUiStates = getTestParticipants(7),
        eglBase = null,
        isVoiceOnlyCall = false
    ) {}
}

@Preview(
    showBackground = false,
    heightDp = 360,
    widthDp = 800
)
@Composable
fun FiftyParticipantsLandscape() {
    ParticipantGrid(
        participantUiStates = getTestParticipants(50),
        eglBase = null,
        isVoiceOnlyCall = false
    ) {}
}

fun getTestParticipants(numberOfParticipants: Int): List<ParticipantUiState> {
    val participantList = mutableListOf<ParticipantUiState>()
    for (i: Int in 1..numberOfParticipants) {
        val participant = ParticipantUiState(
            sessionKey = i.toString(),
            nick = "test$i user",
            isConnected = true,
            isAudioEnabled = false,
            isStreamEnabled = true,
            raisedHand = true,
            avatarUrl = "",
            mediaStream = null
        )
        participantList.add(participant)
    }
    return participantList
}
