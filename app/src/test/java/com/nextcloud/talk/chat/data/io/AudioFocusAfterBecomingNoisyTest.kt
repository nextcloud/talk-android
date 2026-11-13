/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.chat.data.io

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Unplugging the headphones pauses the playback ([AudioManager.ACTION_AUDIO_BECOMING_NOISY]); the audio focus is
 * still held then and must be given back, otherwise the music of another app never comes back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AudioFocusAfterBecomingNoisyTest {
    @Test
    fun `focus is given back after the headphones were unplugged`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val shadow = shadowOf(context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
        val manager = AudioFocusRequestManager(context)
        manager.audioFocusRequest(true) {}

        context.sendBroadcast(Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(
            AudioFocusRequestManager.ManagerState.BROADCAST_RECEIVED,
            manager.getManagerState.value
        )
        var called = false
        manager.audioFocusRequest(false) { called = true }

        assertNotNull("the focus must be abandoned", shadow.lastAbandonedAudioFocusRequest)
        assertEquals(true, called)
    }
}
