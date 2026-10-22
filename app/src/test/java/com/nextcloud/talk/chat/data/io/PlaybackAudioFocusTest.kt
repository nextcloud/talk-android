/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.chat.data.io

import android.content.Context
import android.media.AudioManager
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAudioManager

/**
 * The players of the chat must take the audio focus while they play, so that the music of another app pauses.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlaybackAudioFocusTest {
    private lateinit var context: Context
    private lateinit var audioManager: AudioManager
    private lateinit var shadow: ShadowAudioManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        shadow = shadowOf(audioManager)
    }

    @Test
    fun `player needs the focus only while it plays`() {
        assertTrue(playerNeedsAudioFocus(true, Player.STATE_READY))
        assertTrue(playerNeedsAudioFocus(true, Player.STATE_BUFFERING))
        assertFalse(playerNeedsAudioFocus(false, Player.STATE_READY))
        assertFalse(playerNeedsAudioFocus(true, Player.STATE_ENDED))
        assertFalse(playerNeedsAudioFocus(true, Player.STATE_IDLE))
    }

    @Test
    fun `playback takes a transient focus and gives it back at the end`() {
        val player = mock<Player>()
        val listener = argumentCaptor<Player.Listener>()
        val focus = PlaybackAudioFocus(context, player)
        verify(player).addListener(listener.capture())

        whenever(player.playWhenReady).thenReturn(true)
        whenever(player.playbackState).thenReturn(Player.STATE_READY)
        listener.firstValue.onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        assertEquals(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT, shadow.lastAudioFocusRequest.audioFocusRequest.focusGain)
        assertEquals(null, shadow.lastAbandonedAudioFocusRequest)

        whenever(player.playbackState).thenReturn(Player.STATE_ENDED)
        listener.firstValue.onPlaybackStateChanged(Player.STATE_ENDED)
        assertNotNull("the music of the other app can come back", shadow.lastAbandonedAudioFocusRequest)
        focus.release()
    }

    @Test
    fun `playback pauses when the focus is lost`() {
        val player = mock<Player>()
        val listener = argumentCaptor<Player.Listener>()
        PlaybackAudioFocus(context, player)
        verify(player).addListener(listener.capture())
        whenever(player.playWhenReady).thenReturn(true)
        whenever(player.playbackState).thenReturn(Player.STATE_READY)
        listener.firstValue.onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)

        shadow.lastAudioFocusRequest.listener
            .onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)

        verify(player).pause()
    }
}
