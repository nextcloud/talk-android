/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.call

import androidx.core.telecom.CallEndpointCompat
import com.nextcloud.talk.webrtc.WebRtcAudioManager.AudioDevice

/**
 * Maps between Telecom call endpoints and the audio devices the call UI works with.
 */
object TelecomAudioDevices {

    fun toAudioDevice(endpointType: Int): AudioDevice =
        when (endpointType) {
            CallEndpointCompat.TYPE_EARPIECE -> AudioDevice.EARPIECE
            CallEndpointCompat.TYPE_SPEAKER -> AudioDevice.SPEAKER_PHONE
            CallEndpointCompat.TYPE_BLUETOOTH -> AudioDevice.BLUETOOTH
            CallEndpointCompat.TYPE_WIRED_HEADSET -> AudioDevice.WIRED_HEADSET
            // Streaming and unknown endpoints have no counterpart in the call UI
            else -> AudioDevice.NONE
        }

    fun toEndpointType(device: AudioDevice): Int? =
        when (device) {
            AudioDevice.EARPIECE -> CallEndpointCompat.TYPE_EARPIECE
            AudioDevice.SPEAKER_PHONE -> CallEndpointCompat.TYPE_SPEAKER
            AudioDevice.BLUETOOTH -> CallEndpointCompat.TYPE_BLUETOOTH
            AudioDevice.WIRED_HEADSET -> CallEndpointCompat.TYPE_WIRED_HEADSET
            else -> null
        }

    /** The devices the UI can offer for [route], or an empty set if Telecom reported none it can map. */
    fun availableDevices(route: TelecomAudioRoute): Set<AudioDevice> =
        route.availableEndpoints
            .map { toAudioDevice(it.type) }
            .filter { it != AudioDevice.NONE }
            .toSet()
}
