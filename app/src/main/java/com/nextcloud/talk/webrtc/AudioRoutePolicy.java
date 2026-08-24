/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.webrtc;

import java.util.Set;

final class AudioRoutePolicy {
    private AudioRoutePolicy() {
    }

    static WebRtcAudioManager.AudioDevice selectAudioDevice(
            Set<WebRtcAudioManager.AudioDevice> availableDevices,
            WebRtcAudioManager.AudioDevice userSelectedDevice,
            WebRtcAudioManager.AudioDevice defaultDevice,
            boolean hasWiredHeadset,
            boolean bluetoothConnected) {
        if (bluetoothConnected) {
            return WebRtcAudioManager.AudioDevice.BLUETOOTH;
        }

        if (hasWiredHeadset) {
            return WebRtcAudioManager.AudioDevice.WIRED_HEADSET;
        }

        if (userSelectedDevice != WebRtcAudioManager.AudioDevice.NONE
                && userSelectedDevice != WebRtcAudioManager.AudioDevice.BLUETOOTH
                && availableDevices.contains(userSelectedDevice)) {
            return userSelectedDevice;
        }

        if (defaultDevice != WebRtcAudioManager.AudioDevice.NONE && availableDevices.contains(defaultDevice)) {
            return defaultDevice;
        }

        if (availableDevices.contains(WebRtcAudioManager.AudioDevice.EARPIECE)) {
            return WebRtcAudioManager.AudioDevice.EARPIECE;
        }
        if (availableDevices.contains(WebRtcAudioManager.AudioDevice.SPEAKER_PHONE)) {
            return WebRtcAudioManager.AudioDevice.SPEAKER_PHONE;
        }
        return WebRtcAudioManager.AudioDevice.NONE;
    }

    static boolean shouldPreferBluetooth(
            WebRtcAudioManager.AudioDevice userSelectedDevice,
            boolean bluetoothCurrentlyPreferred,
            boolean bluetoothExpected,
            boolean bluetoothUnavailable) {
        if (userSelectedDevice == WebRtcAudioManager.AudioDevice.BLUETOOTH) {
            return true;
        }
        if (userSelectedDevice != WebRtcAudioManager.AudioDevice.NONE) {
            return false;
        }
        if (bluetoothExpected) {
            return true;
        }
        return bluetoothCurrentlyPreferred && !bluetoothUnavailable;
    }
}
