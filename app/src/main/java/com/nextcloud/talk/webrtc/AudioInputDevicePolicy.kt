/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.webrtc

import android.annotation.SuppressLint
import android.media.AudioDeviceInfo

internal object AudioInputDevicePolicy {

    /**
     * Input types the user can pick as microphone. Bluetooth inputs are excluded because they are tied to the
     * Bluetooth output route selected in [WebRtcAudioManager].
     */
    @JvmStatic
    fun isSelectableInputType(type: Int): Boolean =
        when (type) {
            AudioDeviceInfo.TYPE_BUILTIN_MIC,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_ACCESSORY -> true
            else -> false
        }

    /**
     * Input types that belong to an output route, in order of preference. Android records from these inputs when no
     * microphone is selected explicitly. TYPE_BLE_HEADSET never occurs below API 31, so using it there is harmless.
     */
    @SuppressLint("InlinedApi")
    @JvmStatic
    fun inputTypesForOutputRoute(outputRoute: WebRtcAudioManager.AudioDevice): List<Int> =
        when (outputRoute) {
            WebRtcAudioManager.AudioDevice.BLUETOOTH -> listOf(
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_BLE_HEADSET
            )
            WebRtcAudioManager.AudioDevice.WIRED_HEADSET -> listOf(AudioDeviceInfo.TYPE_WIRED_HEADSET) + USB_INPUT_TYPES
            else -> USB_INPUT_TYPES
        } + AudioDeviceInfo.TYPE_BUILTIN_MIC

    private val USB_INPUT_TYPES = listOf(
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_ACCESSORY
    )

    /**
     * Stable key of an input device. Device ids change on every reconnect, so the type and product name identify a
     * device across calls. Multiple built-in microphones share one key.
     */
    @JvmStatic
    fun deviceKey(type: Int, productName: CharSequence?): String = "$type:${productName ?: ""}"
}
