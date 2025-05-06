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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.unit.max
import com.nextcloud.talk.call.ParticipantUiState
import org.webrtc.EglBase
import kotlin.math.ceil

@Composable
fun ParticipantGrid(
    modifier: Modifier = Modifier,
    eglBase: EglBase?,
    participants: List<ParticipantUiState>,
    onClick: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    // Experimental: sort participants by audio/video enabled. Maybe only do this for many participants??
    //
    // val sortedParticipants = remember(participants) {
    //     participants.sortedWith(
    //         compareByDescending<ParticipantUiState> { it.isAudioEnabled && it.isStreamEnabled }
    //             .thenByDescending { it.isAudioEnabled }
    //             .thenByDescending { it.isStreamEnabled }
    //     )
    // }

    when (participants.size) {
        0 -> {}
        1 -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                ParticipantTile(
                    participant = participants[0],
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { onClick() },
                    eglBase = eglBase
                )
            }
        }

        2, 3 -> {
            if (isPortrait) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 4.dp)
                        .clickable { onClick() },
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    participants.forEach {
                        ParticipantTile(
                            participant = it,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            eglBase = eglBase
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp)
                        .clickable { onClick() },
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    participants.forEach {
                        ParticipantTile(
                            participant = it,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            eglBase = eglBase
                        )
                    }
                }
            }
        }

        else -> {
            val columns = if (isPortrait) 2 else 3
            val rows = ceil(participants.size / columns.toFloat()).toInt()
            val screenHeight = LocalConfiguration.current.screenHeightDp.dp
            val rawItemHeight = screenHeight / rows
            val itemHeight = max(rawItemHeight, 120.dp)

            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(0.dp)
                    .clickable { onClick() },
                verticalArrangement = Arrangement.spacedBy(0.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp)
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
    }
}



@Preview
@Composable
fun ParticipantGridPreview() {
    ParticipantGrid(
        participants = getTestParticipants(1),
        eglBase = null
    ) {}
}

@Preview
@Composable
fun TwoParticipants() {
    ParticipantGrid(
        participants = getTestParticipants(2),
        eglBase = null
    ) {}
}

@Preview
@Composable
fun ThreeParticipants() {
    ParticipantGrid(
        participants = getTestParticipants(3),
        eglBase = null
    ) {}
}

@Preview
@Composable
fun FourParticipants() {
    ParticipantGrid(
        participants = getTestParticipants(4),
        eglBase = null
    ) {}
}

@Preview
@Composable
fun FiveParticipants() {
    ParticipantGrid(
        participants = getTestParticipants(5),
        eglBase = null
    ) {}
}

@Preview
@Composable
fun SevenParticipants() {
    ParticipantGrid(
        participants = getTestParticipants(7),
        eglBase = null
    ) {}
}

@Preview
@Composable
fun FiftyParticipants() {
    ParticipantGrid(
        participants = getTestParticipants(50),
        eglBase = null
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
        eglBase = null
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
        eglBase = null
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
        eglBase = null
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
        eglBase = null
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
        eglBase = null
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
        eglBase = null
    ) {}
}

fun getTestParticipants(numberOfParticipants: Int): List<ParticipantUiState> {
    val participantList = mutableListOf<ParticipantUiState>()
    for (i: Int in 1..numberOfParticipants) {
        val participant = ParticipantUiState(
            sessionKey = i.toString(),
            nick = "testuser$i",
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
