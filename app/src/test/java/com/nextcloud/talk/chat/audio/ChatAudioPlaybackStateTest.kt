/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatAudioPlaybackStateTest {

    private val current = ChatAudioKey(1L, "room", 10)
    private val other = ChatAudioKey(1L, "room", 11)

    private val playing = ChatAudioPlaybackState(
        currentKey = current,
        kind = ChatAudioKind.VOICE_MESSAGE,
        isPlaying = true,
        positionMs = 2_500L,
        durationMs = 10_000L
    )

    @Test
    fun `current message shows the live playback state`() {
        val state = playing.messageState(current, ChatAudioMetadata(durationMs = 9_000L, positionMs = 1_000L))

        assertTrue(state.isCurrent)
        assertTrue(state.isPlaying)
        assertEquals(2_500L, state.positionMs)
        assertEquals(10_000L, state.durationMs)
        assertEquals(0.25f, state.progress, DELTA)
        assertEquals(2_500L, state.displayedTimeMs)
    }

    @Test
    fun `current message falls back to the known duration while the player does not know it yet`() {
        val buffering = playing.copy(isBuffering = true, positionMs = 0L, durationMs = 0L)

        val state = buffering.messageState(current, ChatAudioMetadata(durationMs = 9_000L))

        assertTrue(state.isBuffering)
        assertEquals(9_000L, state.durationMs)
    }

    @Test
    fun `other messages show their remembered position and duration`() {
        val state = playing.messageState(other, ChatAudioMetadata(durationMs = 8_000L, positionMs = 2_000L))

        assertFalse(state.isCurrent)
        assertFalse(state.isPlaying)
        assertEquals(0.25f, state.progress, DELTA)
        assertEquals(2_000L, state.displayedTimeMs)
    }

    @Test
    fun `unplayed messages show their duration or nothing if it is unknown`() {
        assertEquals(8_000L, playing.messageState(other, ChatAudioMetadata(durationMs = 8_000L)).displayedTimeMs)
        assertNull(playing.messageState(other, null).displayedTimeMs)
        assertFalse(playing.messageState(other, null).canSeek)
    }

    @Test
    fun `errors are only reported for the current message`() {
        val failed = playing.copy(isPlaying = false, error = ChatAudioError.NETWORK)

        assertEquals(ChatAudioError.NETWORK, failed.messageState(current, null).error)
        assertNull(failed.messageState(other, null).error)
    }

    companion object {
        private const val DELTA = 0.0001f
    }
}
