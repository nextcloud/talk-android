/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.audio

/**
 * Builds the queue that is played when an audio message is tapped: the tapped message followed by the newer
 * messages of the same kind, so consecutive voice messages (or audio files) are played one after another.
 */
object ChatAudioQueue {
    const val MAX_SIZE = 50

    /**
     * @param messages the audio messages of the conversation, oldest first
     * @param startId the id of the tapped message
     * @param idOf returns the message id of an element of [messages]
     * @param kindOf returns the audio kind of an element of [messages], or null if it cannot be played
     */
    fun <T> build(messages: List<T>, startId: Int, idOf: (T) -> Int, kindOf: (T) -> ChatAudioKind?): List<T> {
        val startIndex = messages.indexOfFirst { idOf(it) == startId }
        val startKind = messages.getOrNull(startIndex)?.let(kindOf)
        return if (startKind == null) {
            emptyList()
        } else {
            messages.subList(startIndex, messages.size)
                .filter { kindOf(it) == startKind }
                .take(MAX_SIZE)
        }
    }
}
