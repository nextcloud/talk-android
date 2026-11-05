/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.webrtc

import com.nextcloud.talk.webrtc.WebRtcAudioManager.AudioDevice

/**
 * Decides the audio route while another component (Android Telecom) owns the call's communication route.
 *
 * The owner reports the active and available devices; switching is only possible by asking the owner, which answers
 * asynchronously. This class keeps the user's selection, the request in flight and the proximity override consistent
 * with what the owner reports. It has no Android dependencies so it can be unit tested.
 *
 * Methods returning [AudioDevice] return the device that must be requested from the owner, or null if nothing has to
 * be requested.
 */
class ExternalAudioRouteController {

    var currentDevice: AudioDevice = AudioDevice.NONE
        private set

    var availableDevices: Set<AudioDevice> = emptySet()
        private set

    var userSelectedDevice: AudioDevice = AudioDevice.NONE
        private set

    /** Device requested from the owner whose switch was not reported yet. */
    var pendingDevice: AudioDevice = AudioDevice.NONE
        private set

    /** Shows the requested device right away, so the UI does not lag behind the user's choice. */
    val deviceForUi: AudioDevice
        get() = if (pendingDevice != AudioDevice.NONE) pendingDevice else currentDevice

    /** Remote audio may play once the owner reported a route and no switch is in flight. */
    val isRouteReady: Boolean
        get() = currentDevice != AudioDevice.NONE && pendingDevice == AudioDevice.NONE

    fun reset(userSelectedDevice: AudioDevice) {
        currentDevice = AudioDevice.NONE
        availableDevices = emptySet()
        pendingDevice = AudioDevice.NONE
        this.userSelectedDevice = userSelectedDevice
    }

    /**
     * The owner reported its route. A change the app did not request (headset plugged in, Bluetooth connected,
     * output switched in the system UI or on a car head unit) becomes the user's selection, so the app does not
     * fight the owner by switching back.
     */
    fun onRouteReported(current: AudioDevice, available: Set<AudioDevice>, proximityNear: Boolean): AudioDevice? {
        val previous = currentDevice
        currentDevice = current
        availableDevices = available

        when {
            pendingDevice != AudioDevice.NONE && (current == pendingDevice || pendingDevice !in available) ->
                pendingDevice = AudioDevice.NONE

            pendingDevice == AudioDevice.NONE &&
                previous != AudioDevice.NONE &&
                current != previous &&
                current != desiredDevice(proximityNear) ->
                userSelectedDevice = current
        }
        return deviceToRequest(proximityNear)
    }

    /**
     * The user picked [device]. Returns the device to request, or null when nothing needs to be requested.
     * Returns [AudioDevice.NONE] when [device] can't be selected.
     */
    fun select(device: AudioDevice, proximityNear: Boolean): AudioDevice? =
        if (device !in availableDevices) {
            AudioDevice.NONE
        } else {
            userSelectedDevice = device
            if (desiredDevice(proximityNear) == currentDevice) {
                // a switch still in flight to another device must not be shown or awaited anymore
                pendingDevice = AudioDevice.NONE
            }
            deviceToRequest(proximityNear)
        }

    fun onProximityChanged(proximityNear: Boolean): AudioDevice? = deviceToRequest(proximityNear)

    fun onRequestStarted(device: AudioDevice) {
        pendingDevice = device
    }

    /** The owner answered a request. On rejection the selection falls back to the active route. */
    fun onRequestFinished(device: AudioDevice, accepted: Boolean) {
        if (device != pendingDevice) {
            return
        }
        if (!accepted) {
            pendingDevice = AudioDevice.NONE
            userSelectedDevice = currentDevice
        } else if (currentDevice == device) {
            pendingDevice = AudioDevice.NONE
        }
    }

    private fun desiredDevice(proximityNear: Boolean): AudioDevice =
        if (userSelectedDevice == AudioDevice.NONE) {
            currentDevice
        } else {
            AudioRoutePolicy.applyProximity(userSelectedDevice, proximityNear, availableDevices)
        }

    private fun deviceToRequest(proximityNear: Boolean): AudioDevice? {
        val desired = desiredDevice(proximityNear)
        val needsRequest = desired != AudioDevice.NONE &&
            desired != currentDevice &&
            desired != pendingDevice &&
            desired in availableDevices
        return if (needsRequest) desired else null
    }
}
