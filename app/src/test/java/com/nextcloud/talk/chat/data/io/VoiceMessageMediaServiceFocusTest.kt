/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.chat.data.io

import android.content.Context
import android.media.AudioManager
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/**
 * The voice message of the chat plays in the player of [VoiceMessageMediaService], which the chat reaches through a
 * MediaController. That player must take the transient audio focus, so that the music of another app pauses.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VoiceMessageMediaServiceFocusTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val shadow = shadowOf(context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)

    private fun servicePlayer(service: VoiceMessageMediaService): Player {
        val field = VoiceMessageMediaService::class.java.getDeclaredField("mediaSession")
        field.isAccessible = true
        return (field.get(service) as MediaSession).player
    }

    @Test
    fun `voice message played by the chat service holds a transient audio focus`() {
        val service = Robolectric.buildService(VoiceMessageMediaService::class.java).create().get()
        val player = servicePlayer(service)
        val file = File.createTempFile("voice", ".m4a").apply { writeBytes(ByteArray(64)) }

        player.setMediaItem(MediaItem.fromUri(file.toURI().toString()))
        player.prepare()
        player.play()

        assertNotNull("the chat player must request the audio focus", shadow.lastAudioFocusRequest)
        assertEquals(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT, shadow.lastAudioFocusRequest.audioFocusRequest.focusGain)
        assertNull(shadow.lastAbandonedAudioFocusRequest)

        player.pause()
        assertNotNull("the music of the other app can come back", shadow.lastAbandonedAudioFocusRequest)
        service.onDestroy()
    }
}
