/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.webrtc

import android.media.AudioDeviceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioInputDevicePolicyTest {
    @Test
    fun `USB, wired and built-in microphones are selectable`() {
        listOf(
            AudioDeviceInfo.TYPE_BUILTIN_MIC,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_ACCESSORY
        ).forEach { assertTrue(AudioInputDevicePolicy.isSelectableInputType(it)) }
    }

    @Test
    fun `Bluetooth and system inputs are not selectable`() {
        listOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_TELEPHONY,
            AudioDeviceInfo.TYPE_FM_TUNER,
            AudioDeviceInfo.TYPE_REMOTE_SUBMIX
        ).forEach { assertFalse(AudioInputDevicePolicy.isSelectableInputType(it)) }
    }

    @Test
    fun `device key is stable for the same type and product`() {
        assertEquals(
            AudioInputDevicePolicy.deviceKey(AudioDeviceInfo.TYPE_USB_DEVICE, "DJI MIC MINI"),
            AudioInputDevicePolicy.deviceKey(AudioDeviceInfo.TYPE_USB_DEVICE, "DJI MIC MINI")
        )
    }

    @Test
    fun `device key differs by type and product`() {
        assertNotEquals(
            AudioInputDevicePolicy.deviceKey(AudioDeviceInfo.TYPE_USB_DEVICE, "DJI MIC MINI"),
            AudioInputDevicePolicy.deviceKey(AudioDeviceInfo.TYPE_USB_HEADSET, "DJI MIC MINI")
        )
        assertNotEquals(
            AudioInputDevicePolicy.deviceKey(AudioDeviceInfo.TYPE_USB_DEVICE, "DJI MIC MINI"),
            AudioInputDevicePolicy.deviceKey(AudioDeviceInfo.TYPE_USB_DEVICE, "Other")
        )
    }

    @Test
    fun `missing product name yields a key`() {
        assertEquals(
            "${AudioDeviceInfo.TYPE_USB_DEVICE}:",
            AudioInputDevicePolicy.deviceKey(AudioDeviceInfo.TYPE_USB_DEVICE, null)
        )
    }

    @Test
    fun `Bluetooth route prefers the Bluetooth microphone`() {
        assertEquals(
            listOf(
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_BLE_HEADSET,
                AudioDeviceInfo.TYPE_BUILTIN_MIC
            ),
            AudioInputDevicePolicy.inputTypesForOutputRoute(WebRtcAudioManager.AudioDevice.BLUETOOTH)
        )
    }

    @Test
    fun `speaker route prefers a USB microphone over the built-in one`() {
        assertEquals(
            listOf(
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_USB_DEVICE,
                AudioDeviceInfo.TYPE_USB_ACCESSORY,
                AudioDeviceInfo.TYPE_BUILTIN_MIC
            ),
            AudioInputDevicePolicy.inputTypesForOutputRoute(WebRtcAudioManager.AudioDevice.SPEAKER_PHONE)
        )
    }
}
