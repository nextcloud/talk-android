/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.call.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
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
private val participantBarTilePeekWidth = 16.dp

/**
 * Shows the promoted participant fullscreen, optionally above a horizontally scrollable bar with the other
 * participants.
 *
 * @param speakerViewOrder session keys of the promoted participant followed by the participants in the bar
 */
@Suppress("LongParameterList")
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(speakerViewSpacing),
        verticalArrangement = Arrangement.spacedBy(speakerViewSpacing)
    ) {
        key(promotedParticipant.sessionKey) {
            ParticipantTile(
                participantUiState = promotedParticipant,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                eglBase = eglBase,
                isVoiceOnlyCall = isVoiceOnlyCall,
                onScreenShareIconClick = onScreenShareIconClick
            )
        }

        // When the bar is hidden its participants are not composed, so their videos are not rendered
        if (isBarShown) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val tileWidth = participantBarTileWidth(availableWidth = maxWidth, tileCount = barParticipants.size)

                LazyRow(state = barState, horizontalArrangement = Arrangement.spacedBy(speakerViewSpacing)) {
                    items(items = barParticipants, key = { it.sessionKey!! }) { participant ->
                        ParticipantTile(
                            participantUiState = participant,
                            modifier = Modifier.size(width = tileWidth, height = PARTICIPANT_TILE_MIN_HEIGHT),
                            eglBase = eglBase,
                            isVoiceOnlyCall = isVoiceOnlyCall,
                            onScreenShareIconClick = onScreenShareIconClick
                        )
                    }
                }
            }
        }
    }
}

/**
 * Returns the width of the tiles in the participant bar.
 *
 * When not all tiles fit and the tile at the edge is cut in a clearly visible way, the user can already see that the
 * bar is scrollable and the tiles keep their preferred width. Otherwise the tiles are made a bit narrower, so a few
 * pixels of the next tile are visible, or all tiles fit when only a few pixels were missing.
 */
fun participantBarTileWidth(
    availableWidth: Dp,
    tileCount: Int,
    preferredWidth: Dp = PARTICIPANT_TILE_MIN_HEIGHT * PARTICIPANT_BAR_TILE_ASPECT_RATIO,
    spacing: Dp = speakerViewSpacing,
    peekWidth: Dp = participantBarTilePeekWidth
): Dp {
    val available = availableWidth.value
    val preferred = preferredWidth.value
    val gap = spacing.value
    val peek = peekWidth.value

    val requiredWidth = tileCount * (preferred + gap) - gap
    val fullyFittingTiles = floor((available + gap) / (preferred + gap)).toInt()
    val cutTileVisibleWidth = available - fullyFittingTiles * (preferred + gap)
    val isCutTileClearlyVisible = cutTileVisibleWidth >= peek && cutTileVisibleWidth <= preferred - peek
    if (requiredWidth <= available || isCutTileClearlyVisible) return preferredWidth

    val tilesToFit = max(ceil((available - peek) / (preferred + gap)).toInt(), 1)
    val width = if (tilesToFit >= tileCount) {
        (available - (tileCount - 1) * gap) / tileCount
    } else {
        (available - peek) / tilesToFit - gap
    }
    return floor(width).dp
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
