/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.audio

/** Why the current audio message could not be played. */
enum class ChatAudioError {
    NETWORK,
    UNAVAILABLE,
    UNSUPPORTED,
    UNKNOWN
}

/** Repeat modes of audio files, in the order a repeat button switches through them. */
enum class ChatAudioRepeatMode {
    OFF,
    ALL,
    ONE;

    fun next(): ChatAudioRepeatMode = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromName(name: String?): ChatAudioRepeatMode = entries.firstOrNull { it.name == name } ?: OFF
    }
}

/**
 * State of the chat audio playback as seen by the UI. It only describes the item that is loaded in the playback
 * service; other messages are described by their [ChatAudioMetadata].
 *
 * @param isPlaying true while playback is requested, including while it waits for data
 * @param isBuffering true while playback is requested but waits for data
 * @param hasNext whether the queue has an item after the current one
 */
data class ChatAudioPlaybackState(
    val currentKey: ChatAudioKey? = null,
    val kind: ChatAudioKind? = null,
    val title: String = "",
    val subtitle: String = "",
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1f,
    val error: ChatAudioError? = null,
    val shuffleEnabled: Boolean = false,
    val repeatMode: ChatAudioRepeatMode = ChatAudioRepeatMode.OFF,
    val hasNext: Boolean = false,
    val queue: List<ChatAudioQueueItem> = emptyList()
) {
    val isActive: Boolean
        get() = currentKey != null

    /** The state of the message with the given [key] for its bubble in the chat. */
    fun messageState(key: ChatAudioKey?, metadata: ChatAudioMetadata?): ChatAudioMessageState =
        if (key != null && key == currentKey) {
            ChatAudioMessageState(
                isCurrent = true,
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                positionMs = positionMs,
                durationMs = durationMs.takeIf { it > 0L } ?: metadata?.durationMs ?: 0L,
                error = error
            )
        } else {
            ChatAudioMessageState(
                positionMs = metadata?.resumePosition ?: 0L,
                durationMs = metadata?.durationMs ?: 0L
            )
        }
}

/** An item of the loaded queue; [index] is its position in the queue, not in the shuffled playback order. */
data class ChatAudioQueueItem(val index: Int, val key: ChatAudioKey, val title: String, val subtitle: String)

/** What the bubble of a single audio message shows. */
data class ChatAudioMessageState(
    val isCurrent: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val error: ChatAudioError? = null
) {
    val progress: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    val canSeek: Boolean
        get() = durationMs > 0L

    /** The elapsed time while the message is played or paused in between, otherwise its duration. */
    val displayedTimeMs: Long?
        get() = when {
            isCurrent || positionMs > 0L -> positionMs
            durationMs > 0L -> durationMs
            else -> null
        }
}
