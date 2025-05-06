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

@Composable
fun ParticipantGrid(modifier: Modifier = Modifier, participants: List<ParticipantUiState>, onClick: () -> Unit) {
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

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
                        .clickable { onClick() }
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
                                .fillMaxWidth()
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
                                .fillMaxHeight()
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
                items(participants) { participant ->
                    ParticipantTile(
                        participant = participant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.5f)
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
        participants = getTestParticipants(numberOfParticipants)
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
        participants = getTestParticipants(numberOfParticipants)
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
        participants = getTestParticipants(numberOfParticipants)
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
            surfaceViewRenderer = null
        )
        participantList.add(participant)
    }
    return participantList
}
