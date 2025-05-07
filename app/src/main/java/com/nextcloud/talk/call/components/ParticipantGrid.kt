/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.call.components

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nextcloud.talk.call.ParticipantUiState
import org.webrtc.EglBase
import kotlin.math.ceil

@Composable
fun ParticipantGrid(
    modifier: Modifier = Modifier,
    eglBase: EglBase?,
    participants: List<ParticipantUiState>,
    isVoiceOnlyCall: Boolean,
    onClick: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    val minItemHeight = 100.dp

    val columns =
        if (isPortrait) {
            when (participants.size) {
                1, 2, 3 -> 1
                else -> 2
            }
        } else {
            when (participants.size) {
                1 -> 1
                2, 4 -> 2
                else -> 3
            }
        }

    val rows = ceil(participants.size / columns.toFloat()).toInt()

    val heightForNonGridComponents = if (isVoiceOnlyCall) {
        // this is a workaround for now. It should ~summarize the height of callInfosLinearLayout and callControls
        // Once everything is migrated to jetpack, this workaround should be obsolete or solved in a better way
        240.dp
    } else {
        0.dp
    }

    val gridHeight = LocalConfiguration.current.screenHeightDp.dp - heightForNonGridComponents
    val itemSpacing = 8.dp
    val edgePadding = 8.dp

    val totalVerticalSpacing = itemSpacing * (rows - 1)
    val totalVerticalPadding = edgePadding * 2
    val availableHeight = gridHeight - totalVerticalSpacing - totalVerticalPadding

    val rawItemHeight = availableHeight / rows
    val itemHeight = maxOf(rawItemHeight, minItemHeight)

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = edgePadding)
            .clickable { onClick() },
        verticalArrangement = Arrangement.spacedBy(itemSpacing),
        horizontalArrangement = Arrangement.spacedBy(itemSpacing),
        contentPadding = PaddingValues(vertical = edgePadding)
    ) {
        items(
            participants,
            key = { it.sessionKey }
        ) { participant ->
            ParticipantTile(
                participant = participant,
                modifier = Modifier
                    .height(itemHeight)
                    .fillMaxWidth(),
                eglBase = eglBase
            )
        }
    }
}

@Preview
@Composable
fun ParticipantGridPreview() {
    ParticipantGrid(
        participants = getTestParticipants(1),
        eglBase = null,
        isVoiceOnlyCall = false
    ) {}
}

@Preview
@Composable
fun TwoParticipants() {
    ParticipantGrid(
        participants = getTestParticipants(2),
        eglBase = null,
        isVoiceOnlyCall = false
    ) {}
}

@Preview
@Composable
fun ThreeParticipants() {
    ParticipantGrid(
        participants = getTestParticipants(3),
        eglBase = null,
        isVoiceOnlyCall = false
    ) {}
}

@Preview
@Composable
fun FourParticipants() {
    ParticipantGrid(
        participants = getTestParticipants(4),
        eglBase = null,
        isVoiceOnlyCall = false
    ) {}
}

@Preview
@Composable
fun FiveParticipants() {
    ParticipantGrid(
        participants = getTestParticipants(5),
        eglBase = null,
        isVoiceOnlyCall = false
    ) {}
}

@Preview
@Composable
fun SevenParticipants() {
    ParticipantGrid(
        participants = getTestParticipants(7),
        eglBase = null,
        isVoiceOnlyCall = false
    ) {}
}

@Preview
@Composable
fun FiftyParticipants() {
    ParticipantGrid(
        participants = getTestParticipants(50),
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
        participants = getTestParticipants(1),
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
        participants = getTestParticipants(2),
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
        participants = getTestParticipants(3),
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
        participants = getTestParticipants(4),
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
        participants = getTestParticipants(7),
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
        participants = getTestParticipants(50),
        eglBase = null,
        isVoiceOnlyCall = false
    ) {}
}

fun getTestParticipants(numberOfParticipants: Int): List<ParticipantUiState> {
    val participantList = mutableListOf<ParticipantUiState>()
    for (i: Int in 1..numberOfParticipants) {
        val participant = ParticipantUiState(
            sessionKey = i.toString(),
            nick = "testuser$i Test",
            isConnected = true,
            isAudioEnabled = if (i == 3) true else false,
            isStreamEnabled = true,
            raisedHand = true,
            avatarUrl = "",
            mediaStream = null
        )
        participantList.add(participant)
    }
    return participantList
}
