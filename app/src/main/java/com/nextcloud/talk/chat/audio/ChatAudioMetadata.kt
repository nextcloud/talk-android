/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.audio

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * What is known about an audio message beyond the chat message itself: its duration, the position to resume
 * from and, for voice messages, the waveform.
 */
data class ChatAudioMetadata(val durationMs: Long = 0L, val positionMs: Long = 0L, val waveform: List<Float>? = null) {

    /** Only long recordings keep their position across app restarts, short ones start from the beginning again. */
    val persistablePosition: Long
        get() = if (durationMs >= LONG_AUDIO_DURATION_MS) positionMs else 0L

    /** The position playback should start from, or 0 if the message was (almost) played to the end. */
    val resumePosition: Long
        get() = if (positionMs > 0L && (durationMs <= 0L || positionMs < durationMs - END_TOLERANCE_MS)) {
            positionMs
        } else {
            0L
        }

    companion object {
        const val LONG_AUDIO_DURATION_MS = 5 * 60 * 1000L
        const val END_TOLERANCE_MS = 1000L
    }
}

/** Binary representation of [ChatAudioMetadata] used by the small on-disk metadata cache. */
object ChatAudioMetadataCodec {
    private const val VERSION = 1
    private const val MAX_WAVEFORM_SIZE = 1024

    fun encode(metadata: ChatAudioMetadata): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            val waveform = metadata.waveform.orEmpty().take(MAX_WAVEFORM_SIZE)
            output.writeInt(VERSION)
            output.writeLong(metadata.durationMs)
            output.writeLong(metadata.persistablePosition)
            output.writeInt(waveform.size)
            waveform.forEach { output.writeFloat(it) }
        }
        return bytes.toByteArray()
    }

    fun decode(bytes: ByteArray): ChatAudioMetadata? =
        try {
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                require(input.readInt() == VERSION)
                val durationMs = input.readLong()
                val positionMs = input.readLong()
                val waveformSize = input.readInt()
                require(waveformSize in 0..MAX_WAVEFORM_SIZE)
                val waveform = List(waveformSize) { input.readFloat() }
                ChatAudioMetadata(durationMs, positionMs, waveform.takeIf { it.isNotEmpty() })
            }
        } catch (e: IOException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
}
