/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.webrtc

import android.media.AudioDeviceInfo
import com.nextcloud.talk.webrtc.WebRtcAudioManager.AudioDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("TooManyFunctions")
class AudioRoutePolicyTest {
    @Test
    fun `Bluetooth connection has priority`() {
        assertSelected(
            AudioDevice.BLUETOOTH,
            devices(AudioDevice.BLUETOOTH, AudioDevice.EARPIECE, AudioDevice.SPEAKER_PHONE),
            AudioDevice.SPEAKER_PHONE,
            AudioDevice.SPEAKER_PHONE,
            hasWiredHeadset = false,
            bluetoothConnected = true
        )
    }

    @Test
    fun `wired headset has priority over Bluetooth preference`() {
        assertSelected(
            AudioDevice.WIRED_HEADSET,
            devices(AudioDevice.BLUETOOTH, AudioDevice.WIRED_HEADSET),
            AudioDevice.BLUETOOTH,
            AudioDevice.SPEAKER_PHONE,
            hasWiredHeadset = true,
            bluetoothConnected = false
        )
    }

    @Test
    fun `explicit speaker selection is honored when Bluetooth is not preferred`() {
        assertSelected(
            AudioDevice.SPEAKER_PHONE,
            devices(AudioDevice.EARPIECE, AudioDevice.SPEAKER_PHONE),
            AudioDevice.SPEAKER_PHONE,
            AudioDevice.EARPIECE,
            hasWiredHeadset = false,
            bluetoothConnected = false
        )
    }

    @Test
    fun `configured default is used without an explicit selection`() {
        assertSelected(
            AudioDevice.SPEAKER_PHONE,
            devices(AudioDevice.EARPIECE, AudioDevice.SPEAKER_PHONE),
            AudioDevice.NONE,
            AudioDevice.SPEAKER_PHONE,
            hasWiredHeadset = false,
            bluetoothConnected = false
        )
    }

    @Test
    fun `automatic Bluetooth preference is released after endpoint disappears`() {
        assertFalse(AudioRoutePolicy.shouldPreferBluetooth(AudioDevice.NONE, true, false, true))
    }

    @Test
    fun `explicit Bluetooth preference survives endpoint disappearance`() {
        assertTrue(AudioRoutePolicy.shouldPreferBluetooth(AudioDevice.BLUETOOTH, true, false, true))
    }

    @Test
    fun `automatic Bluetooth preference survives an unconfirmed transition`() {
        assertTrue(AudioRoutePolicy.shouldPreferBluetooth(AudioDevice.NONE, true, false, false))
    }

    @Test
    fun `active route reconciliation forces a request despite a stale matching getter`() {
        assertTrue(
            AudioRoutePolicy.shouldSetCommunicationDevice(
                currentRouteMatches = true,
                routeSelectionMustBeReasserted = true
            )
        )
    }

    @Test
    fun `matching current route is reused without an active Bluetooth selection`() {
        assertFalse(
            AudioRoutePolicy.shouldSetCommunicationDevice(
                currentRouteMatches = true,
                routeSelectionMustBeReasserted = false
            )
        )
    }

    @Test
    fun `different current route always requires a communication-device request`() {
        assertTrue(
            AudioRoutePolicy.shouldSetCommunicationDevice(
                currentRouteMatches = false,
                routeSelectionMustBeReasserted = false
            )
        )
    }

    @Test
    fun `Bluetooth teardown waits for a required target selection`() {
        assertTrue(
            AudioRoutePolicy.shouldSelectBeforeBluetoothTeardown(
                bluetoothStopNeeded = true,
                targetDevice = AudioDevice.WIRED_HEADSET
            )
        )
        assertTrue(
            AudioRoutePolicy.shouldSelectBeforeBluetoothTeardown(
                bluetoothStopNeeded = true,
                targetDevice = AudioDevice.SPEAKER_PHONE
            )
        )
        assertFalse(
            AudioRoutePolicy.shouldSelectBeforeBluetoothTeardown(
                bluetoothStopNeeded = false,
                targetDevice = AudioDevice.WIRED_HEADSET
            )
        )
        assertFalse(
            AudioRoutePolicy.shouldSelectBeforeBluetoothTeardown(
                bluetoothStopNeeded = true,
                targetDevice = AudioDevice.BLUETOOTH
            )
        )
        assertFalse(
            AudioRoutePolicy.shouldSelectBeforeBluetoothTeardown(
                bluetoothStopNeeded = true,
                targetDevice = AudioDevice.NONE
            )
        )
        assertFalse(
            AudioRoutePolicy.canFinishBluetoothTeardown(
                bluetoothStopNeeded = true,
                targetMustBeSelectedFirst = true,
                targetSelectionSucceeded = false
            )
        )
        assertTrue(
            AudioRoutePolicy.canFinishBluetoothTeardown(
                bluetoothStopNeeded = true,
                targetMustBeSelectedFirst = true,
                targetSelectionSucceeded = true
            )
        )
        assertTrue(
            AudioRoutePolicy.canFinishBluetoothTeardown(
                bluetoothStopNeeded = true,
                targetMustBeSelectedFirst = false,
                targetSelectionSucceeded = false
            )
        )
    }

    @Test
    fun `wired communication routes require an output sink`() {
        assertWiredOutput(AudioDeviceInfo.TYPE_WIRED_HEADSET)
        assertWiredOutput(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
        assertWiredOutput(AudioDeviceInfo.TYPE_USB_HEADSET)
        assertWiredOutput(AudioDeviceInfo.TYPE_USB_DEVICE)
        assertWiredOutput(AudioDeviceInfo.TYPE_USB_ACCESSORY)
        assertFalse(AudioRoutePolicy.isWiredCommunicationOutput(AudioDeviceInfo.TYPE_USB_DEVICE, isSink = false))
        assertFalse(AudioRoutePolicy.isWiredCommunicationOutput(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, isSink = true))
    }

    private fun devices(vararg devices: AudioDevice): Set<AudioDevice> = setOf(*devices)

    private fun assertWiredOutput(type: Int) {
        assertTrue(AudioRoutePolicy.isWiredCommunicationOutput(type, isSink = true))
    }

    @Suppress("LongParameterList")
    private fun assertSelected(
        expected: AudioDevice,
        availableDevices: Set<AudioDevice>,
        userSelectedDevice: AudioDevice,
        defaultDevice: AudioDevice,
        hasWiredHeadset: Boolean,
        bluetoothConnected: Boolean
    ) {
        assertEquals(
            expected,
            AudioRoutePolicy.selectAudioDevice(
                availableDevices,
                userSelectedDevice,
                defaultDevice,
                hasWiredHeadset,
                bluetoothConnected
            )
        )
    }
}
