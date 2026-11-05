/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.call

import androidx.core.telecom.CallEndpointCompat
import com.nextcloud.talk.webrtc.WebRtcAudioManager.AudioDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TelecomAudioDevicesTest {

    private val mappedDevices = listOf(
        CallEndpointCompat.TYPE_EARPIECE to AudioDevice.EARPIECE,
        CallEndpointCompat.TYPE_SPEAKER to AudioDevice.SPEAKER_PHONE,
        CallEndpointCompat.TYPE_BLUETOOTH to AudioDevice.BLUETOOTH,
        CallEndpointCompat.TYPE_WIRED_HEADSET to AudioDevice.WIRED_HEADSET
    )

    @Test
    fun `endpoint types map to audio devices and back`() {
        for ((endpointType, device) in mappedDevices) {
            assertEquals(device, TelecomAudioDevices.toAudioDevice(endpointType))
            assertEquals(endpointType, TelecomAudioDevices.toEndpointType(device))
        }
    }

    @Test
    fun `endpoints without counterpart in the call UI are not mapped`() {
        assertEquals(AudioDevice.NONE, TelecomAudioDevices.toAudioDevice(CallEndpointCompat.TYPE_STREAMING))
        assertEquals(AudioDevice.NONE, TelecomAudioDevices.toAudioDevice(CallEndpointCompat.TYPE_UNKNOWN))
        assertNull(TelecomAudioDevices.toEndpointType(AudioDevice.NONE))
        assertNull(TelecomAudioDevices.toEndpointType(AudioDevice.BLUETOOTH_SCO))
    }
}
