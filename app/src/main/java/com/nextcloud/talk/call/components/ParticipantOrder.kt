/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.call.components

import com.nextcloud.talk.activities.ParticipantUiState

/**
 * Returns the speaker list with the participants that started speaking moved to its front, most recent first.
 *
 * Participants that are currently visible are not promoted, so that speakers already on screen do not jump around.
 * Session keys of participants that are no longer in the call are dropped.
 */
fun promoteSpeakers(
    speakers: List<String>,
    startedSpeaking: Set<String>,
    visibleSessionKeys: Set<String>,
    participantSessionKeys: Set<String>
): List<String> {
    val promoted = startedSpeaking.filter { it !in visibleSessionKeys }
    return (promoted + speakers.filter { it !in promoted }).filter { it in participantSessionKeys }
}

/**
 * Sorts participants by screen sharing, then by their position in [speakers], then by their media and call state.
 * The sort is stable, so participants with equal priority keep their order.
 */
fun sortParticipants(participants: List<ParticipantUiState>, speakers: List<String>): List<ParticipantUiState> =
    participants.sortedWith(
        compareBy(
            { !it.isScreenStreamEnabled },
            { speakers.indexOf(it.sessionKey).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE },
            { !it.isStreamEnabled },
            { !it.isAudioEnabled },
            { !it.raisedHand },
            { !it.isConnected }
        )
    )
