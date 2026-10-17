/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.audio

/**
 * Identifies an audio message in a conversation of one account. [mediaId] is the media id of the corresponding
 * item in the playback service, so the key of whatever the service currently plays can always be restored.
 */
data class ChatAudioKey(val internalUserId: Long, val roomToken: String, val messageId: Int) {

    val mediaId: String
        get() = "$MEDIA_ID_PREFIX$internalUserId$SEPARATOR$roomToken$SEPARATOR$messageId"

    companion object {
        private const val MEDIA_ID_PREFIX = "talk-audio:"
        private const val SEPARATOR = "/"
        private const val PART_COUNT = 3

        fun fromMediaId(mediaId: String?): ChatAudioKey? {
            val parts = mediaId
                ?.takeIf { it.startsWith(MEDIA_ID_PREFIX) }
                ?.removePrefix(MEDIA_ID_PREFIX)
                ?.split(SEPARATOR)
                ?.takeIf { it.size == PART_COUNT && it[1].isNotEmpty() }
            val internalUserId = parts?.get(0)?.toLongOrNull()
            val messageId = parts?.get(2)?.toIntOrNull()
            return if (parts != null && internalUserId != null && messageId != null) {
                ChatAudioKey(internalUserId, parts[1], messageId)
            } else {
                null
            }
        }
    }
}

/**
 * The kinds of chat audio. A queue only contains messages of one kind, and each kind is played with its own
 * audio attributes and playback speed.
 */
enum class ChatAudioKind {
    VOICE_MESSAGE,
    AUDIO_FILE
}
