/*
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.chat.data.io

import android.content.Context
import android.media.AudioManager
import com.nextcloud.talk.utils.singletons.ApplicationWideCurrentRoomHolder
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class MessageAudioPolicyTest {
    private val context: Context = mock()
    private val audio: AudioManager = mock()
    private val call = ApplicationWideCurrentRoomHolder.getInstance()

    init {
        whenever(context.getSystemService(Context.AUDIO_SERVICE)).thenReturn(audio)
    }

    @After
    fun resetCall() = call.clear()

    @Before
    fun clearCall() = call.clear()

    @Test
    fun `dialing and connected calls forbid message playback`() {
        assertTrue(MessageAudioPolicy.canPlay(context))
        call.isDialing = true
        assertFalse(MessageAudioPolicy.canPlay(context))
        call.isDialing = false
        call.isInCall = true
        assertFalse(MessageAudioPolicy.canPlay(context))
    }

    @Test
    fun `communication audio mode blocks playback before call state is published`() {
        whenever(audio.mode).thenReturn(AudioManager.MODE_IN_COMMUNICATION)
        assertFalse(MessageAudioPolicy.canPlay(context))
        whenever(audio.mode).thenReturn(AudioManager.MODE_IN_CALL)
        assertFalse(MessageAudioPolicy.canPlay(context))
    }
}
