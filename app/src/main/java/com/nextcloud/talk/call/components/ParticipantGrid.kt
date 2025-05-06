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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nextcloud.talk.call.ParticipantUiState
import org.webrtc.EglBase

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
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 4.dp)
                        .clickable { onClick() },
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = participants,
                        key = { it.sessionKey }
                    ) { participant ->
                        ParticipantTile(
                            participant = participant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.5f),
                            eglBase = eglBase
                        )
                    }
                }
            } else {
                LazyRow(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp)
                        .clickable { onClick() },
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = participants,
                        key = { it.sessionKey }
                    ) { participant ->
                        ParticipantTile(
                            participant = participant,
                            modifier = Modifier
                                .fillMaxHeight()
                                .aspectRatio(1.5f),
                            eglBase = eglBase
                        )
                    }
                }
            }
        }

        else -> {
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (isPortrait) 2 else 3),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .clickable { onClick() },
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    participants.sortedBy { it.isAudioEnabled }.asReversed(),
                    key = { it.sessionKey }
                ) { participant ->
                    ParticipantTile(
                        participant = participant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.5f),
                        eglBase = eglBase
                    )
                }
            }
        }
    }
}

const val numberOfParticipants = 4

@Preview(
    showBackground = false
)
@Composable
fun ParticipantGridPreview() {
    ParticipantGrid(
        participants = getTestParticipants(numberOfParticipants),
        eglBase = null
    ) {}
}

@Preview(
    showBackground = false,
    heightDp = 902,
    widthDp = 434
)
@Composable
fun ParticipantGridPreviewPortrait2() {
    ParticipantGrid(
        participants = getTestParticipants(numberOfParticipants),
        eglBase = null
    ) {}
}

@Preview(
    showBackground = false,
    heightDp = 360,
    widthDp = 800
)
@Composable
fun ParticipantGridPreviewLandscape1() {
    ParticipantGrid(
        participants = getTestParticipants(numberOfParticipants),
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
