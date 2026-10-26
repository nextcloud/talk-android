/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.audio

/**
 * Builds the queue that is played when an audio message is tapped. A voice message is followed by the newer voice
 * messages, so a conversation is listened to in order. An audio file is played within the audio files around it,
 * like in a playlist that can be skipped through, shuffled and repeated.
 */
object ChatAudioQueue {
    const val MAX_SIZE = 50

    /** The items of a queue and the index of the tapped one among them. */
    data class Selection<T>(val items: List<T>, val startIndex: Int)

    /**
     * @param messages the audio messages of the conversation, oldest first
     * @param startId the id of the tapped message
     * @param idOf returns the message id of an element of [messages]
     * @param kindOf returns the audio kind of an element of [messages], or null if it cannot be played
     * @return the queue, or null if the tapped message cannot be played
     */
    fun <T> build(messages: List<T>, startId: Int, idOf: (T) -> Int, kindOf: (T) -> ChatAudioKind?): Selection<T>? {
        val kind = messages.firstOrNull { idOf(it) == startId }?.let(kindOf) ?: return null
        val sameKind = messages.filter { kindOf(it) == kind }
        val index = sameKind.indexOfFirst { idOf(it) == startId }
        return if (kind == ChatAudioKind.VOICE_MESSAGE) {
            Selection(sameKind.subList(index, sameKind.size).take(MAX_SIZE), 0)
        } else {
            val first = (index - MAX_SIZE / 2).coerceIn(0, (sameKind.size - MAX_SIZE).coerceAtLeast(0))
            Selection(sameKind.subList(first, minOf(sameKind.size, first + MAX_SIZE)), index - first)
        }
    }
}
