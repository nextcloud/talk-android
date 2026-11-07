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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isFinite
import org.webrtc.EglBase
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.ceil
import android.util.Log
import com.nextcloud.talk.activities.ParticipantUiState

/** Minimum height of a participant tile, also used for the tiles in the participant bar of the speaker view. */
val PARTICIPANT_TILE_MIN_HEIGHT = 100.dp

@SuppressLint("UnusedBoxWithConstraintsScope")
@Suppress("LongParameterList", "LongMethod")
@Composable
fun ParticipantGrid(
    modifier: Modifier = Modifier,
    eglBase: EglBase?,
    participantUiStates: List<ParticipantUiState>,
    isVoiceOnlyCall: Boolean,
    speakers: List<String> = emptyList(),
    onVisibleSessionKeysChanged: (Set<String>) -> Unit = {},
    onClick: () -> Unit,
    onScreenShareIconClick: ((String?) -> Unit?)?
) {
    Log.d("ParticipantGrid", "participantUiStates.size in Grid:" + participantUiStates.size)

    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    val gridState = rememberLazyGridState()

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String }.toSet() }
            .distinctUntilChanged()
            .collect { onVisibleSessionKeysChanged(it) }
    }

    // A lazy grid keeps its first visible item in place when items move in front of it, so the grid has to be
    // scrolled explicitly to keep showing the top, where promoted speakers are placed. Whether the grid was at the
    // top is read in the composition with the new speaker order, before the grid is laid out with it.
    val isScrolledToTop by remember {
        derivedStateOf { gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0 }
    }
    val wasScrolledToTop = isScrolledToTop
    LaunchedEffect(speakers) {
        if (wasScrolledToTop) {
            gridState.requestScrollToItem(0)
        }
    }

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
        val hasBoundedHeight = constraints.hasBoundedHeight && maxHeight.isFinite

        val itemHeight = if (hasBoundedHeight) {
            val gridAvailableHeight = maxHeight - totalVerticalSpacing - totalVerticalPadding
            val rawItemHeight = gridAvailableHeight / rows
            maxOf(rawItemHeight, PARTICIPANT_TILE_MIN_HEIGHT)
        } else {
            PARTICIPANT_TILE_MIN_HEIGHT
        }

        val gridHeight = if (hasBoundedHeight) {
            maxHeight
        } else {
            (itemHeight * rows) + totalVerticalSpacing + totalVerticalPadding
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            modifier = Modifier
                .fillMaxWidth()
                .height(gridHeight),
            verticalArrangement = Arrangement.spacedBy(itemSpacing),
            horizontalArrangement = Arrangement.spacedBy(itemSpacing),
            contentPadding = PaddingValues(vertical = edgePadding, horizontal = edgePadding)
        ) {
            items(
                items = sortParticipants(participantUiStates, speakers),
                key = { it.sessionKey!! }
            ) { participant ->
                ParticipantTile(
                    participantUiState = participant,
                    modifier = Modifier
                        .height(itemHeight)
                        .fillMaxWidth(),
                    eglBase = eglBase,
                    isVoiceOnlyCall = isVoiceOnlyCall,
                    onScreenShareIconClick = onScreenShareIconClick
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
        isVoiceOnlyCall = false,
        onClick = {},
        onScreenShareIconClick = {}
    )
}

@Preview
@Composable
fun TwoParticipants() {
    ParticipantGrid(
        participantUiStates = getTestParticipants(2),
        eglBase = null,
        isVoiceOnlyCall = false,
        onClick = {},
        onScreenShareIconClick = {}
    )
}

@Preview
@Composable
fun ThreeParticipants() {
    ParticipantGrid(
        participantUiStates = getTestParticipants(3),
        eglBase = null,
        isVoiceOnlyCall = false,
        onClick = {},
        onScreenShareIconClick = {}
    )
}

@Preview
@Composable
fun FourParticipants() {
    ParticipantGrid(
        participantUiStates = getTestParticipants(4),
        eglBase = null,
        isVoiceOnlyCall = false,
        onClick = {},
        onScreenShareIconClick = {}
    )
}

@Preview
@Composable
fun FiveParticipants() {
    ParticipantGrid(
        participantUiStates = getTestParticipants(5),
        eglBase = null,
        isVoiceOnlyCall = false,
        onClick = {},
        onScreenShareIconClick = {}
    )
}

@Preview
@Composable
fun SevenParticipants() {
    ParticipantGrid(
        participantUiStates = getTestParticipants(7),
        eglBase = null,
        isVoiceOnlyCall = false,
        onClick = {},
        onScreenShareIconClick = {}
    )
}

@Preview
@Composable
fun FiftyParticipants() {
    ParticipantGrid(
        participantUiStates = getTestParticipants(50),
        eglBase = null,
        isVoiceOnlyCall = false,
        onClick = {},
        onScreenShareIconClick = {}
    )
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
        isVoiceOnlyCall = false,
        onClick = {},
        onScreenShareIconClick = {}
    )
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
        isVoiceOnlyCall = false,
        onClick = {},
        onScreenShareIconClick = {}
    )
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
        isVoiceOnlyCall = false,
        onClick = {},
        onScreenShareIconClick = {}
    )
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
        isVoiceOnlyCall = false,
        onClick = {},
        onScreenShareIconClick = {}
    )
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
        isVoiceOnlyCall = false,
        onClick = {},
        onScreenShareIconClick = {}
    )
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
        isVoiceOnlyCall = false,
        onClick = {},
        onScreenShareIconClick = {}
    )
}

fun getTestParticipants(numberOfParticipants: Int): List<ParticipantUiState> {
    val participantList = mutableListOf<ParticipantUiState>()
    for (i: Int in 1..numberOfParticipants) {
        val participant = ParticipantUiState(
            sessionKey = i.toString(),
            baseUrl = "",
            roomToken = "",
            nick = "test$i user",
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
        participantList.add(participant)
    }
    return participantList
}
