/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.call.components

// The order of the participants in the speaker view, as a list of session keys: the first one is the promoted
// participant shown fullscreen, the others are shown in the participant bar.

/**
 * Returns the order when switching to the speaker view, starting from the order of the participants in the grid.
 *
 * The promoted participant is the one currently speaking, otherwise the previously promoted one if still in the call,
 * then the most recent speaker and finally the first participant.
 */
fun initialSpeakerViewOrder(
    participantSessionKeys: List<String>,
    activeSpeakerSessionKeys: Set<String>,
    previouslyPromotedSessionKey: String?,
    speakers: List<String>
): List<String> {
    val promoted = participantSessionKeys.firstOrNull { it in activeSpeakerSessionKeys }
        ?: previouslyPromotedSessionKey?.takeIf { it in participantSessionKeys }
        ?: fallbackPromotedSessionKey(participantSessionKeys, speakers)
    return promoteInSpeakerView(participantSessionKeys, promoted)
}

/**
 * Returns the order after the participants or their speaking state changed.
 *
 * Participants that left are dropped and new participants are added to the end of the participant bar. A participant
 * that starts speaking is promoted. When the promoted participant stops speaking, the promotion is handed over to a
 * participant that is still speaking, and when the promoted participant left, a new one is chosen like for
 * [initialSpeakerViewOrder]. When several participants qualify, the one shown first is promoted.
 *
 * @param previousActiveSpeakerSessionKeys the participants that were speaking at the previous update
 */
fun updateSpeakerViewOrder(
    order: List<String>,
    participantSessionKeys: List<String>,
    activeSpeakerSessionKeys: Set<String>,
    previousActiveSpeakerSessionKeys: Set<String>,
    speakers: List<String>
): List<String> {
    val startedSpeaking = activeSpeakerSessionKeys - previousActiveSpeakerSessionKeys
    val stoppedSpeaking = previousActiveSpeakerSessionKeys - activeSpeakerSessionKeys
    val previouslyPromoted = order.firstOrNull()
    val updatedOrder = order.filter { it in participantSessionKeys } +
        participantSessionKeys.filter { it !in order }
    val firstActiveSpeaker = updatedOrder.firstOrNull { it in activeSpeakerSessionKeys }

    val promoted = when {
        startedSpeaking.isNotEmpty() -> updatedOrder.firstOrNull { it in startedSpeaking }
        // The participants that left were already dropped, so the fallback is the first one in the participant bar
        previouslyPromoted !in participantSessionKeys ->
            firstActiveSpeaker ?: fallbackPromotedSessionKey(updatedOrder, speakers)
        previouslyPromoted in stoppedSpeaking -> firstActiveSpeaker
        else -> null
    }
    return promoteInSpeakerView(updatedOrder, promoted)
}

/**
 * Moves the participant to the front. The previously promoted participant becomes the first one in the participant
 * bar, so the participants in the bar are ordered by how recently they were promoted.
 */
fun promoteInSpeakerView(order: List<String>, sessionKey: String?): List<String> {
    val index = order.indexOf(sessionKey)
    if (index <= 0) return order

    return listOf(sessionKey!!) + order.filterIndexed { i, _ -> i != index }
}

private fun fallbackPromotedSessionKey(participantSessionKeys: List<String>, speakers: List<String>): String? =
    speakers.firstOrNull { it in participantSessionKeys } ?: participantSessionKeys.firstOrNull()
