/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.webrtc

import android.media.AudioDeviceInfo
import com.nextcloud.talk.webrtc.WebRtcAudioManager.AudioDevice

internal object AudioRoutePolicy {
    @JvmStatic
    fun selectAudioDevice(
        availableDevices: Set<AudioDevice>,
        userSelectedDevice: AudioDevice,
        defaultDevice: AudioDevice,
        hasWiredHeadset: Boolean,
        bluetoothConnected: Boolean
    ): AudioDevice =
        when {
            bluetoothConnected -> AudioDevice.BLUETOOTH
            hasWiredHeadset -> AudioDevice.WIRED_HEADSET
            userSelectedDevice != AudioDevice.NONE &&
                userSelectedDevice != AudioDevice.BLUETOOTH &&
                availableDevices.contains(userSelectedDevice) -> userSelectedDevice
            defaultDevice != AudioDevice.NONE && availableDevices.contains(defaultDevice) -> defaultDevice
            availableDevices.contains(AudioDevice.EARPIECE) -> AudioDevice.EARPIECE
            availableDevices.contains(AudioDevice.SPEAKER_PHONE) -> AudioDevice.SPEAKER_PHONE
            else -> AudioDevice.NONE
        }

    @JvmStatic
    fun shouldPreferBluetooth(
        userSelectedDevice: AudioDevice,
        bluetoothCurrentlyPreferred: Boolean,
        bluetoothExpected: Boolean,
        bluetoothUnavailable: Boolean
    ): Boolean =
        when {
            userSelectedDevice == AudioDevice.BLUETOOTH -> true
            userSelectedDevice != AudioDevice.NONE -> false
            bluetoothExpected -> true
            else -> bluetoothCurrentlyPreferred && !bluetoothUnavailable
        }

    @JvmStatic
    fun shouldSetCommunicationDevice(currentRouteMatches: Boolean, routeSelectionMustBeReasserted: Boolean): Boolean =
        !currentRouteMatches || routeSelectionMustBeReasserted

    @JvmStatic
    fun shouldSelectBeforeBluetoothTeardown(bluetoothStopNeeded: Boolean, targetDevice: AudioDevice): Boolean =
        bluetoothStopNeeded && targetDevice != AudioDevice.NONE && targetDevice != AudioDevice.BLUETOOTH

    @JvmStatic
    fun canFinishBluetoothTeardown(
        bluetoothStopNeeded: Boolean,
        targetMustBeSelectedFirst: Boolean,
        targetSelectionSucceeded: Boolean
    ): Boolean = bluetoothStopNeeded && (!targetMustBeSelectedFirst || targetSelectionSucceeded)

    @JvmStatic
    fun isWiredCommunicationOutput(type: Int, isSink: Boolean): Boolean =
        when (type) {
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_ACCESSORY -> isSink
            else -> false
        }
}
