/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.webrtc

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.nextcloud.talk.R
import com.nextcloud.talk.utils.preferences.AppPreferences

/**
 * Tracks the microphone chosen by the user. The choice is persisted, so a device that is plugged in again is
 * selected automatically. Without a choice, or while the chosen device is absent, Android picks the microphone.
 */
class AudioInputDeviceManager(context: Context, private val appPreferences: AppPreferences) {

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var listener: ((AudioDeviceInfo?) -> Unit)? = null

    var selectedDevice: AudioDeviceInfo? = null
        private set

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            refresh()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            refresh()
        }
    }

    /**
     * Starts tracking input devices. [listener] is called on the main thread with the device to record from, or
     * null for the system default, whenever the selection or the set of available devices changes.
     */
    fun start(listener: (AudioDeviceInfo?) -> Unit) {
        this.listener = listener
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, Handler(Looper.getMainLooper()))
        refresh()
    }

    fun stop() {
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        listener = null
    }

    fun getAvailableDevices(): List<AudioDeviceInfo> = findSelectableInputDevices(audioManager)

    fun getInputDeviceForOutputRoute(outputRoute: WebRtcAudioManager.AudioDevice): AudioDeviceInfo? =
        findInputDeviceForOutputRoute(audioManager, outputRoute)

    /**
     * Selects [device] as microphone, or the system default for null.
     */
    fun select(device: AudioDeviceInfo?) {
        appPreferences.preferredAudioInputDevice = device?.let { deviceKey(it) } ?: ""
        refresh()
    }

    private fun refresh() {
        selectedDevice = findPreferredInputDevice(audioManager, appPreferences.preferredAudioInputDevice)
        Log.d(
            TAG,
            "Input devices: " + getAvailableDevices().joinToString { "${it.type}/${it.productName}" } +
                ", selected: " + (selectedDevice?.let { "${it.type}/${it.productName}" } ?: "system default")
        )
        listener?.invoke(selectedDevice)
    }

    companion object {
        private val TAG = AudioInputDeviceManager::class.java.simpleName

        fun findSelectableInputDevices(audioManager: AudioManager): List<AudioDeviceInfo> =
            audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
                .filter { AudioInputDevicePolicy.isSelectableInputType(it.type) }
                .distinctBy { deviceKey(it) }

        /**
         * Returns the input Android records from for [outputRoute] when no microphone is selected.
         */
        fun findInputDeviceForOutputRoute(
            audioManager: AudioManager,
            outputRoute: WebRtcAudioManager.AudioDevice
        ): AudioDeviceInfo? {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            return AudioInputDevicePolicy.inputTypesForOutputRoute(outputRoute)
                .firstNotNullOfOrNull { type -> devices.firstOrNull { it.type == type } }
        }

        /**
         * Returns the connected device matching the persisted [preferredDeviceKey], or null.
         */
        fun findPreferredInputDevice(audioManager: AudioManager?, preferredDeviceKey: String?): AudioDeviceInfo? {
            if (audioManager == null || preferredDeviceKey.isNullOrEmpty()) {
                return null
            }
            return findSelectableInputDevices(audioManager).firstOrNull { deviceKey(it) == preferredDeviceKey }
        }

        fun deviceKey(device: AudioDeviceInfo): String =
            AudioInputDevicePolicy.deviceKey(device.type, device.productName)

        fun getDisplayName(context: Context, device: AudioDeviceInfo): String =
            when (device.type) {
                AudioDeviceInfo.TYPE_BUILTIN_MIC -> context.getString(R.string.audio_input_phone)
                AudioDeviceInfo.TYPE_WIRED_HEADSET -> context.getString(R.string.audio_output_wired_headset)
                else -> device.productName?.toString()?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.audio_input_usb)
            }
    }
}
