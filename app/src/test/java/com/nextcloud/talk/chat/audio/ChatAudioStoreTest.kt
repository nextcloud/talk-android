/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.audio

import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ChatAudioStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val key = ChatAudioKey(1L, "room", 42)
    private val longDuration = ChatAudioMetadata.LONG_AUDIO_DURATION_MS * 2

    private fun newStore() = ChatAudioStore(folder.root, Dispatchers.Unconfined)

    @Test
    fun `remembered values are available immediately`() {
        val store = newStore()

        store.rememberDuration(key, 20_000L)
        store.rememberPosition(key, 5_000L, 0L)

        assertEquals(ChatAudioMetadata(durationMs = 20_000L, positionMs = 5_000L), store.metadataFor(key))
        assertEquals(store.metadataFor(key), store.metadata.value[key])
    }

    @Test
    fun `forgetting the position of an unknown message creates no entry`() {
        val store = newStore()

        store.forgetPosition(key)

        assertNull(store.metadataFor(key))
    }

    @Test
    fun `duration, waveform and position of long recordings are restored after a restart`() {
        newStore().apply {
            rememberDuration(key, longDuration)
            rememberWaveform(key, floatArrayOf(0.5f, 1f))
            rememberPosition(key, 60_000L, longDuration)
        }

        val restored = newStore().apply { load(key) }.metadataFor(key)

        assertEquals(ChatAudioMetadata(longDuration, 60_000L, listOf(0.5f, 1f)), restored)
    }

    @Test
    fun `short recordings start from the beginning after a restart`() {
        newStore().rememberPosition(key, 5_000L, 20_000L)

        val restored = newStore().apply { load(key) }.metadataFor(key)

        assertEquals(ChatAudioMetadata(durationMs = 20_000L), restored)
    }

    @Test
    fun `a waveform that could not be computed is only kept until the app restarts`() {
        newStore().apply {
            rememberDuration(key, 20_000L)
            rememberWaveform(key, FloatArray(0))
            assertEquals(emptyList<Float>(), metadataFor(key)?.waveform)
        }

        val restored = newStore().apply { load(key) }.metadataFor(key)

        assertEquals(ChatAudioMetadata(durationMs = 20_000L), restored)
    }

    @Test
    fun `a position changed before the persisted one is loaded is kept`() {
        newStore().rememberPosition(key, 60_000L, longDuration)
        val store = newStore()

        store.forgetPosition(key)
        store.load(key)

        assertEquals(ChatAudioMetadata(durationMs = longDuration), store.metadataFor(key))
    }

    @Test
    fun `corrupt files are ignored`() {
        newStore().rememberDuration(key, 20_000L)
        folder.root.listFiles().orEmpty().forEach { it.writeText("garbage") }

        val store = newStore().apply { load(key) }

        assertNull(store.metadataFor(key))
    }
}
