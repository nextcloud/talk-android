/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.webrtc

import android.media.AudioDeviceInfo
import android.os.Build
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothCommunicationDevicePolicyTest {
    @Test
    fun `A2DP is never used for two-way call audio`() {
        assertUnsupported(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, Build.VERSION_CODES.BAKLAVA)
    }

    @Test
    fun `classic SCO is supported on old and current Android`() {
        assertSupported(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, Build.VERSION_CODES.O)
        assertSupported(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, Build.VERSION_CODES.BAKLAVA)
    }

    @Test
    fun `hearing aid requires modern communication-device API`() {
        assertUnsupported(AudioDeviceInfo.TYPE_HEARING_AID, Build.VERSION_CODES.R)
        assertSupported(AudioDeviceInfo.TYPE_HEARING_AID, Build.VERSION_CODES.S)
    }

    @Test
    fun `BLE communication devices require Android twelve`() {
        assertUnsupported(AudioDeviceInfo.TYPE_BLE_HEADSET, Build.VERSION_CODES.R)
        assertUnsupported(AudioDeviceInfo.TYPE_BLE_SPEAKER, Build.VERSION_CODES.R)
        assertSupported(AudioDeviceInfo.TYPE_BLE_HEADSET, Build.VERSION_CODES.S)
        assertSupported(AudioDeviceInfo.TYPE_BLE_SPEAKER, Build.VERSION_CODES.S)
    }

    @Test
    fun `headset endpoints are preferred over output-only BLE speaker`() {
        val headsetPriority = WebRtcBluetoothManager.bluetoothCommunicationDevicePriority(
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            Build.VERSION_CODES.BAKLAVA
        )
        val speakerPriority = WebRtcBluetoothManager.bluetoothCommunicationDevicePriority(
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            Build.VERSION_CODES.BAKLAVA
        )

        assertTrue(headsetPriority > speakerPriority)
    }

    private fun assertSupported(deviceType: Int, sdkInt: Int) {
        assertTrue(WebRtcBluetoothManager.bluetoothCommunicationDevicePriority(deviceType, sdkInt) >= 0)
    }

    private fun assertUnsupported(deviceType: Int, sdkInt: Int) {
        assertTrue(WebRtcBluetoothManager.bluetoothCommunicationDevicePriority(deviceType, sdkInt) < 0)
    }
}
