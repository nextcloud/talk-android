/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.call.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nextcloud.talk.activities.ParticipantUiState
import kotlinx.coroutines.flow.distinctUntilChanged
import org.webrtc.EglBase
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

private val speakerViewSpacing = 8.dp
private const val PARTICIPANT_BAR_TILE_ASPECT_RATIO = 1.5f
private val participantBarTileWidth = PARTICIPANT_TILE_MIN_HEIGHT * PARTICIPANT_BAR_TILE_ASPECT_RATIO
private val participantBarTilePeekLength = 16.dp

/**
 * Shows the promoted participant fullscreen, optionally next to a scrollable bar with the other participants. The bar
 * is shown below the promoted participant and scrolls horizontally in portrait, and is shown at the side and scrolls
 * vertically in landscape.
 *
 * @param speakerViewOrder session keys of the promoted participant followed by the participants in the bar
 */
@Suppress("LongParameterList", "LongMethod")
@Composable
fun ParticipantSpeakerView(
    modifier: Modifier = Modifier,
    eglBase: EglBase?,
    participantUiStates: List<ParticipantUiState>,
    speakerViewOrder: List<String>,
    isParticipantBarVisible: Boolean,
    isVoiceOnlyCall: Boolean,
    onVisibleSessionKeysChanged: (Set<String>) -> Unit = {},
    onScreenShareIconClick: ((String?) -> Unit?)?
) {
    val participantsBySessionKey = participantUiStates.associateBy { it.sessionKey }
    // Participants that joined since the order was last updated are added to the end of the bar
    val orderedParticipants = speakerViewOrder.mapNotNull { participantsBySessionKey[it] } +
        participantUiStates.filter { it.sessionKey !in speakerViewOrder }
    val promotedParticipant = orderedParticipants.firstOrNull() ?: return
    val barParticipants = orderedParticipants.drop(1)
    val isBarShown = isParticipantBarVisible && barParticipants.isNotEmpty()
    val barState = rememberLazyListState()

    // The previously promoted participant is inserted at the start of the bar. As the bar keeps the position of its
    // first visible tile, scroll back so the most recent speakers are shown
    LaunchedEffect(barState, promotedParticipant.sessionKey) {
        barState.scrollToItem(0)
    }

    LaunchedEffect(barState, promotedParticipant.sessionKey, isBarShown) {
        snapshotFlow {
            val barSessionKeys = if (isBarShown) {
                barState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String }
            } else {
                emptyList()
            }
            setOfNotNull(promotedParticipant.sessionKey) + barSessionKeys
        }
            .distinctUntilChanged()
            .collect { onVisibleSessionKeysChanged(it) }
    }

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val promotedTile: @Composable (Modifier) -> Unit = { tileModifier ->
        key(promotedParticipant.sessionKey) {
            ParticipantTile(
                participantUiState = promotedParticipant,
                modifier = tileModifier,
                eglBase = eglBase,
                isVoiceOnlyCall = isVoiceOnlyCall,
                onScreenShareIconClick = onScreenShareIconClick
            )
        }
    }

    // When the bar is hidden its participants are not composed, so their videos are not rendered
    val participantBar: @Composable () -> Unit = {
        if (isBarShown) {
            ParticipantBar(
                participants = barParticipants,
                state = barState,
                isVertical = isLandscape,
                eglBase = eglBase,
                isVoiceOnlyCall = isVoiceOnlyCall,
                onScreenShareIconClick = onScreenShareIconClick
            )
        }
    }

    val containerModifier = modifier
        .fillMaxSize()
        .padding(speakerViewSpacing)
    if (isLandscape) {
        Row(modifier = containerModifier, horizontalArrangement = Arrangement.spacedBy(speakerViewSpacing)) {
            promotedTile(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
            participantBar()
        }
    } else {
        Column(modifier = containerModifier, verticalArrangement = Arrangement.spacedBy(speakerViewSpacing)) {
            promotedTile(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
            participantBar()
        }
    }
}

/**
 * Scrollable bar with the participants that are not promoted, scrolling vertically at the side of the promoted
 * participant or horizontally below it.
 */
@Suppress("LongParameterList")
@Composable
private fun ParticipantBar(
    participants: List<ParticipantUiState>,
    state: LazyListState,
    isVertical: Boolean,
    eglBase: EglBase?,
    isVoiceOnlyCall: Boolean,
    onScreenShareIconClick: ((String?) -> Unit?)?
) {
    val tile: @Composable (ParticipantUiState, Modifier) -> Unit = { participant, tileModifier ->
        ParticipantTile(
            participantUiState = participant,
            modifier = tileModifier,
            eglBase = eglBase,
            isVoiceOnlyCall = isVoiceOnlyCall,
            onScreenShareIconClick = onScreenShareIconClick
        )
    }

    if (isVertical) {
        BoxWithConstraints(modifier = Modifier.fillMaxHeight()) {
            val tileHeight = participantBarTileLength(
                availableLength = maxHeight,
                tileCount = participants.size,
                preferredLength = PARTICIPANT_TILE_MIN_HEIGHT
            )

            LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(speakerViewSpacing)) {
                items(items = participants, key = { it.sessionKey!! }) { participant ->
                    tile(participant, Modifier.size(width = participantBarTileWidth, height = tileHeight))
                }
            }
        }
    } else {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val tileWidth = participantBarTileLength(availableLength = maxWidth, tileCount = participants.size)

            LazyRow(state = state, horizontalArrangement = Arrangement.spacedBy(speakerViewSpacing)) {
                items(items = participants, key = { it.sessionKey!! }) { participant ->
                    tile(participant, Modifier.size(width = tileWidth, height = PARTICIPANT_TILE_MIN_HEIGHT))
                }
            }
        }
    }
}

/**
 * Returns the length of the tiles in the participant bar along its scroll direction, which is the width for a
 * horizontal bar and the height for a vertical bar.
 *
 * When not all tiles fit and the tile at the edge is cut in a clearly visible way, the user can already see that the
 * bar is scrollable and the tiles keep their preferred length. Otherwise the tiles are made a bit shorter, so a few
 * pixels of the next tile are visible, or all tiles fit when only a few pixels were missing.
 */
fun participantBarTileLength(
    availableLength: Dp,
    tileCount: Int,
    preferredLength: Dp = participantBarTileWidth,
    spacing: Dp = speakerViewSpacing,
    peekLength: Dp = participantBarTilePeekLength
): Dp {
    val available = availableLength.value
    val preferred = preferredLength.value
    val gap = spacing.value
    val peek = peekLength.value

    val requiredLength = tileCount * (preferred + gap) - gap
    val fullyFittingTiles = floor((available + gap) / (preferred + gap)).toInt()
    val cutTileVisibleLength = available - fullyFittingTiles * (preferred + gap)
    val isCutTileClearlyVisible = cutTileVisibleLength >= peek && cutTileVisibleLength <= preferred - peek
    if (requiredLength <= available || isCutTileClearlyVisible) return preferredLength

    val tilesToFit = max(ceil((available - peek) / (preferred + gap)).toInt(), 1)
    val length = if (tilesToFit >= tileCount) {
        (available - (tileCount - 1) * gap) / tileCount
    } else {
        (available - peek) / tilesToFit - gap
    }
    return floor(length).dp
}

private const val PREVIEW_PARTICIPANT_COUNT = 5

@Preview
@Composable
fun SpeakerViewPreview() {
    val participants = getTestParticipants(PREVIEW_PARTICIPANT_COUNT)
    ParticipantSpeakerView(
        eglBase = null,
        participantUiStates = participants,
        speakerViewOrder = participants.mapNotNull { it.sessionKey },
        isParticipantBarVisible = true,
        isVoiceOnlyCall = false,
        onScreenShareIconClick = {}
    )
}

@Preview(heightDp = 360, widthDp = 800)
@Composable
fun SpeakerViewLandscapePreview() {
    SpeakerViewPreview()
}
