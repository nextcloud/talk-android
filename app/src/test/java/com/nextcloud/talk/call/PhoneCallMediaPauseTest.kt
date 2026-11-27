/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.call

import com.nextcloud.talk.call.PhoneCallMediaPause.Media
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneCallMediaPauseTest {

    private val off = Media(microphone = false, camera = false)
    private val on = Media(microphone = true, camera = true)

    @Test
    fun `microphone and camera are turned off and restored`() {
        val pause = PhoneCallMediaPause()

        assertEquals(on, pause.onPhoneCallStarted(on))
        assertTrue(pause.isPhoneCallActive)
        assertEquals(on, pause.onPhoneCallEnded(off))
        assertFalse(pause.isPhoneCallActive)
    }

    @Test
    fun `media that was already off stays off`() {
        val pause = PhoneCallMediaPause()

        assertEquals(Media(microphone = true, camera = false), pause.onPhoneCallStarted(Media(true, false)))
        assertEquals(Media(microphone = true, camera = false), pause.onPhoneCallEnded(off))
    }

    @Test
    fun `nothing is turned off or restored when all media was off`() {
        val pause = PhoneCallMediaPause()

        assertEquals(off, pause.onPhoneCallStarted(off))
        assertEquals(off, pause.onPhoneCallEnded(off))
    }

    @Test
    fun `microphone turned on by the user during the phone call is not toggled again`() {
        val pause = PhoneCallMediaPause()
        pause.onPhoneCallStarted(on)

        pause.onMicrophoneChangedByUser()

        assertEquals(Media(microphone = false, camera = true), pause.onPhoneCallEnded(Media(true, false)))
    }

    @Test
    fun `microphone turned on and off again by the user during the phone call stays off`() {
        val pause = PhoneCallMediaPause()
        pause.onPhoneCallStarted(on)

        pause.onMicrophoneChangedByUser()
        pause.onMicrophoneChangedByUser()

        assertEquals(Media(microphone = false, camera = true), pause.onPhoneCallEnded(off))
    }

    @Test
    fun `camera changed by the user during the phone call is not restored`() {
        val pause = PhoneCallMediaPause()
        pause.onPhoneCallStarted(on)

        pause.onCameraChangedByUser()

        assertEquals(Media(microphone = true, camera = false), pause.onPhoneCallEnded(off))
    }

    @Test
    fun `media that is on again when the phone call ends is not turned on twice`() {
        val pause = PhoneCallMediaPause()
        pause.onPhoneCallStarted(on)

        assertEquals(Media(microphone = false, camera = true), pause.onPhoneCallEnded(Media(true, false)))
    }

    @Test
    fun `repeated start keeps what was turned off by the first one`() {
        val pause = PhoneCallMediaPause()
        pause.onPhoneCallStarted(on)

        assertEquals(off, pause.onPhoneCallStarted(off))
        assertEquals(on, pause.onPhoneCallEnded(off))
    }

    @Test
    fun `end without start restores nothing`() {
        val pause = PhoneCallMediaPause()

        assertEquals(off, pause.onPhoneCallEnded(off))
    }

    @Test
    fun `user change before the phone call does not affect what is restored`() {
        val pause = PhoneCallMediaPause()
        pause.onMicrophoneChangedByUser()
        pause.onCameraChangedByUser()
        pause.onPhoneCallStarted(on)

        assertEquals(on, pause.onPhoneCallEnded(off))
    }

    @Test
    fun `media is not held back without a phone call`() {
        val pause = PhoneCallMediaPause()

        assertFalse(pause.holdBackMicrophone())
        assertFalse(pause.holdBackCamera())
    }

    @Test
    fun `media held back during a phone call is turned on when it ends`() {
        val pause = PhoneCallMediaPause()
        pause.onPhoneCallStarted(off)

        assertTrue(pause.holdBackMicrophone())
        assertTrue(pause.holdBackCamera())
        assertEquals(on, pause.onPhoneCallEnded(off))
    }

    @Test
    fun `held back microphone changed by the user is not turned on when the phone call ends`() {
        val pause = PhoneCallMediaPause()
        pause.onPhoneCallStarted(off)
        pause.holdBackMicrophone()

        pause.onMicrophoneChangedByUser()

        assertEquals(off, pause.onPhoneCallEnded(Media(true, false)))
    }

    @Test
    fun `reset forgets what was turned off`() {
        val pause = PhoneCallMediaPause()
        pause.onPhoneCallStarted(on)

        pause.reset()

        assertFalse(pause.isPhoneCallActive)
        assertEquals(off, pause.onPhoneCallEnded(off))
    }
}
