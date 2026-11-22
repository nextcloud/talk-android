/*
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.chat.data.io

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MessageAudioPlaybackTest {
    private val port = FakeMessageAudioPort()
    private var allowed = true
    private val playback = MessageAudioPlayback { allowed }
    private val first = MessageAudioTrack(MessageAudioKey(1, "first", 1), "first.m4a", "Sender", "First", true)
    private val second = first.copy(key = MessageAudioKey(1, "second", 1), uri = "second.m4a")

    @Before
    fun attach() = playback.attach(port)

    private fun play(track: MessageAudioTrack = first, speed: Float = 1f) {
        assertTrue(playback.completeRequest(playback.beginRequest(track.key), track, speed))
    }

    @Test
    fun `playing another room replaces the current audio even when message ids match`() {
        play()
        play(second)
        assertEquals(listOf(first.key.mediaId, second.key.mediaId), port.loads)
        assertEquals(second, playback.state.value.track)
        assertTrue(playback.state.value.isPlaying)
        assertEquals(1, port.pauses)
    }

    @Test
    fun `accounts on the same server never share playback identity`() {
        play()
        val otherAccount = first.copy(key = first.key.copy(accountId = 2))
        assertFalse(playback.toggle(otherAccount.key))
        play(otherAccount)
        assertEquals(otherAccount, playback.state.value.track)
    }

    @Test
    fun `returning to a conversation restores position pause and speed without reloading`() {
        play(speed = 1.5f)
        port.position = 12_000
        playback.refresh()
        assertEquals(12_000L, playback.state.value.positionMs)
        assertEquals(40, playback.state.value.progress)
        playback.toggle(first.key)
        playback.refresh()
        assertFalse(playback.state.value.isPlaying)
        assertEquals(1.5f, playback.state.value.speed)
        playback.toggle(first.key)
        assertTrue(playback.state.value.isPlaying)
        assertEquals(12_000L, playback.state.value.positionMs)
        assertEquals(1, port.loads.size)
    }

    @Test
    fun `a late download cannot replace a newer selection`() {
        val oldRequest = playback.beginRequest(first.key)
        val latestRequest = playback.beginRequest(second.key)
        assertTrue(playback.completeRequest(latestRequest, second, 1f))
        assertFalse(playback.completeRequest(oldRequest, first, 1f))
        assertEquals(second, playback.state.value.track)
        assertEquals(1, port.loads.size)
    }

    @Test
    fun `closing the bar invalidates pending downloads and clears playback`() {
        play()
        val request = playback.beginRequest(second.key)
        playback.stop()
        assertFalse(playback.completeRequest(request, second, 1f))
        assertNull(playback.state.value.track)
        assertNull(port.mediaId)
    }

    @Test
    fun `seeking another room cannot move the active audio`() {
        play()
        playback.seek(second.key, 80)
        assertEquals(0L, port.position)
        playback.seek(first.key, 50)
        assertEquals(15_000L, port.position)
    }

    @Test
    fun `speed changes are scoped to the active voice message`() {
        play()
        playback.setSpeed(second.key, 2f)
        assertEquals(1f, playback.state.value.speed)
        playback.setSpeed(first.key, 1.5f)
        playback.toggle(first.key)
        playback.setSpeed(first.key, 2f)
        assertFalse(playback.state.value.isPlaying)
        assertEquals(2f, playback.state.value.speed)
    }

    @Test
    fun `ordinary audio starts at normal speed after a fast voice message`() {
        play(speed = 2f)
        play(second.copy(isVoice = false), speed = 2f)
        assertEquals(1f, playback.state.value.speed)
    }

    @Test
    fun `calls and recording block starting or resuming message audio`() {
        play()
        allowed = false
        playback.refresh()
        assertFalse(port.playing)
        playback.toggle(first.key)
        assertFalse(port.playing)
        val request = playback.beginRequest(second.key)
        assertFalse(playback.completeRequest(request, second, 1f))
        allowed = true
        playback.refresh()
        assertFalse(port.playing)
    }

    @Test
    fun `a call invalidates a download selected before the call`() {
        val request = playback.beginRequest(first.key)
        allowed = false
        playback.refresh()
        allowed = true
        assertFalse(playback.completeRequest(request, first, 1f))
        assertTrue(port.loads.isEmpty())
    }

    @Test
    fun `external pause and playback completion are reflected by the shared state`() {
        play()
        port.playing = false
        playback.refresh()
        assertFalse(playback.state.value.isPlaying)
        port.ended = true
        port.position = 30_000
        playback.refresh()
        assertTrue(playback.state.value.ended)
        playback.toggle(first.key)
        assertEquals(0L, port.position)
        assertTrue(port.playing)
    }

    @Test
    fun `service disconnection clears stale state`() {
        play()
        playback.attach(null)
        assertNull(playback.state.value.track)
        assertFalse(playback.toggle(first.key))
    }
}

class FakeMessageAudioPort : MessageAudioPort {
    override var mediaId: String? = null
    val loads = mutableListOf<String>()
    var playing = false
    var position = 0L
    var playbackSpeed = 1f
    var ended = false
    var pauses = 0

    override fun snapshot(track: MessageAudioTrack) =
        MessageAudioState(track, playing, playing, position, 30_000, playbackSpeed, ended)

    override fun load(track: MessageAudioTrack, speed: Float) {
        mediaId = track.key.mediaId
        loads.add(track.key.mediaId)
        playbackSpeed = speed
        position = 0
        ended = false
    }

    override fun play() {
        playing = true
        ended = false
    }

    override fun pause() {
        if (playing) pauses++
        playing = false
    }

    override fun clear() {
        playing = false
        mediaId = null
    }

    override fun seekTo(positionMs: Long) {
        position = positionMs
        ended = false
    }

    override fun setSpeed(speed: Float) {
        playbackSpeed = speed
    }
}
