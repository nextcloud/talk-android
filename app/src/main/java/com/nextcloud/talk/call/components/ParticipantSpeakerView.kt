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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.unit.DpSize
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

enum class ParticipantBarPlacement {
    SIDE,
    BOTTOM,
    NONE
}

/**
 * Returns where the participant bar is placed in a speaker view of the given size.
 *
 * The bar is placed at the side when the space is wider than high, and below the promoted participant otherwise. It
 * may take at most half of the space and has to fit at least one tile of its preferred size, so it is placed at the
 * other position or not at all in small windows like picture in picture.
 */
fun participantBarPlacement(width: Dp, height: Dp): ParticipantBarPlacement {
    val sideBarFits = participantBarTileWidth + speakerViewSpacing <= width / 2 &&
        height >= PARTICIPANT_TILE_MIN_HEIGHT
    val bottomBarFits = PARTICIPANT_TILE_MIN_HEIGHT + speakerViewSpacing <= height / 2 &&
        width >= participantBarTileWidth
    return when {
        width > height && sideBarFits -> ParticipantBarPlacement.SIDE
        bottomBarFits -> ParticipantBarPlacement.BOTTOM
        sideBarFits -> ParticipantBarPlacement.SIDE
        else -> ParticipantBarPlacement.NONE
    }
}

/**
 * Shows the promoted participant fullscreen, optionally next to a scrollable bar with the other participants, placed
 * as returned by [participantBarPlacement]. A bar at the side is always shown on the left, also in right-to-left
 * layouts, as the self video is placed on the right.
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
    val barState = rememberLazyListState()

    // The previously promoted participant is inserted at the start of the bar. As the bar keeps the position of its
    // first visible tile, scroll back so the most recent speakers are shown
    LaunchedEffect(barState, promotedParticipant.sessionKey) {
        barState.scrollToItem(0)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(speakerViewSpacing)
    ) {
        val availableWidth = maxWidth
        val availableHeight = maxHeight
        val placement = if (isParticipantBarVisible && barParticipants.isNotEmpty()) {
            participantBarPlacement(width = availableWidth, height = availableHeight)
        } else {
            ParticipantBarPlacement.NONE
        }

        LaunchedEffect(barState, promotedParticipant.sessionKey, placement) {
            snapshotFlow {
                val barSessionKeys = if (placement != ParticipantBarPlacement.NONE) {
                    barState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String }
                } else {
                    emptyList()
                }
                setOfNotNull(promotedParticipant.sessionKey) + barSessionKeys
            }
                .distinctUntilChanged()
                .collect { onVisibleSessionKeysChanged(it) }
        }

        // When the bar is not placed its participants are not composed, so their videos are not rendered
        if (placement == ParticipantBarPlacement.SIDE) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Absolute.spacedBy(speakerViewSpacing)
            ) {
                ParticipantBar(
                    participants = barParticipants,
                    state = barState,
                    isVertical = true,
                    availableLength = availableHeight,
                    eglBase = eglBase,
                    isVoiceOnlyCall = isVoiceOnlyCall,
                    onScreenShareIconClick = onScreenShareIconClick
                )
                key(promotedParticipant.sessionKey) {
                    ParticipantTile(
                        participantUiState = promotedParticipant,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        eglBase = eglBase,
                        isVoiceOnlyCall = isVoiceOnlyCall,
                        onScreenShareIconClick = onScreenShareIconClick
                    )
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(speakerViewSpacing)) {
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
                if (placement == ParticipantBarPlacement.BOTTOM) {
                    ParticipantBar(
                        participants = barParticipants,
                        state = barState,
                        isVertical = false,
                        availableLength = availableWidth,
                        eglBase = eglBase,
                        isVoiceOnlyCall = isVoiceOnlyCall,
                        onScreenShareIconClick = onScreenShareIconClick
                    )
                }
            }
        }
    }
}

/**
 * Scrollable bar with the participants that are not promoted, scrolling vertically next to the promoted participant
 * or horizontally below it.
 *
 * @param availableLength the length of the bar along its scroll direction
 */
@Suppress("LongParameterList")
@Composable
private fun ParticipantBar(
    participants: List<ParticipantUiState>,
    state: LazyListState,
    isVertical: Boolean,
    availableLength: Dp,
    eglBase: EglBase?,
    isVoiceOnlyCall: Boolean,
    onScreenShareIconClick: ((String?) -> Unit?)?
) {
    val tileSize = if (isVertical) {
        val height = participantBarTileLength(availableLength, participants.size, PARTICIPANT_TILE_MIN_HEIGHT)
        DpSize(width = participantBarTileWidth, height = height)
    } else {
        val width = participantBarTileLength(availableLength, participants.size)
        DpSize(width = width, height = PARTICIPANT_TILE_MIN_HEIGHT)
    }

    val content: LazyListScope.() -> Unit = {
        items(items = participants, key = { it.sessionKey!! }) { participant ->
            ParticipantTile(
                participantUiState = participant,
                modifier = Modifier.size(tileSize),
                eglBase = eglBase,
                isVoiceOnlyCall = isVoiceOnlyCall,
                onScreenShareIconClick = onScreenShareIconClick
            )
        }
    }

    if (isVertical) {
        LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(speakerViewSpacing), content = content)
    } else {
        LazyRow(state = state, horizontalArrangement = Arrangement.spacedBy(speakerViewSpacing), content = content)
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
    return floor(length.coerceAtLeast(0f)).dp
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
