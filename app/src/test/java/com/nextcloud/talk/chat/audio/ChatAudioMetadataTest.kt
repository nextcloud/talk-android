/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatAudioMetadataTest {

    private val longDuration = ChatAudioMetadata.LONG_AUDIO_DURATION_MS + 1000L

    @Test
    fun `codec round trip keeps duration, waveform and the position of long recordings`() {
        val metadata = ChatAudioMetadata(
            durationMs = longDuration,
            positionMs = 42_000L,
            waveform = listOf(0f, 0.5f, 1f)
        )

        assertEquals(metadata, ChatAudioMetadataCodec.decode(ChatAudioMetadataCodec.encode(metadata)))
    }

    @Test
    fun `position of short recordings is not persisted`() {
        val metadata = ChatAudioMetadata(durationMs = 20_000L, positionMs = 5_000L)

        val decoded = ChatAudioMetadataCodec.decode(ChatAudioMetadataCodec.encode(metadata))

        assertEquals(ChatAudioMetadata(durationMs = 20_000L), decoded)
    }

    @Test
    fun `corrupt or foreign data is ignored`() {
        assertNull(ChatAudioMetadataCodec.decode(ByteArray(0)))
        assertNull(ChatAudioMetadataCodec.decode(byteArrayOf(0, 0, 0, 9)))
        val truncated = ChatAudioMetadataCodec.encode(ChatAudioMetadata(1000L, 0L, listOf(1f, 1f))).dropLast(2)
        assertNull(ChatAudioMetadataCodec.decode(truncated.toByteArray()))
    }

    @Test
    fun `resume position starts over at the end of a message`() {
        assertEquals(0L, ChatAudioMetadata(durationMs = 10_000L, positionMs = 9_500L).resumePosition)
        assertEquals(4_000L, ChatAudioMetadata(durationMs = 10_000L, positionMs = 4_000L).resumePosition)
        assertEquals(4_000L, ChatAudioMetadata(durationMs = 0L, positionMs = 4_000L).resumePosition)
        assertEquals(0L, ChatAudioMetadata().resumePosition)
    }
}
