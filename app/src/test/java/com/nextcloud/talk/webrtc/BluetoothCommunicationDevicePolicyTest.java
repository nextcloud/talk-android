/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.webrtc;

import android.media.AudioDeviceInfo;
import android.os.Build;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class BluetoothCommunicationDevicePolicyTest {

    @Test
    public void a2dpIsNeverUsedForTwoWayCallAudio() {
        assertUnsupported(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, Build.VERSION_CODES.BAKLAVA);
    }

    @Test
    public void classicScoIsSupportedOnOldAndCurrentAndroid() {
        assertSupported(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, Build.VERSION_CODES.O);
        assertSupported(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, Build.VERSION_CODES.BAKLAVA);
    }

    @Test
    public void hearingAidRequiresModernCommunicationDeviceApi() {
        assertUnsupported(AudioDeviceInfo.TYPE_HEARING_AID, Build.VERSION_CODES.R);
        assertSupported(AudioDeviceInfo.TYPE_HEARING_AID, Build.VERSION_CODES.S);
    }

    @Test
    public void bleCommunicationDevicesRequireAndroidTwelve() {
        assertUnsupported(AudioDeviceInfo.TYPE_BLE_HEADSET, Build.VERSION_CODES.R);
        assertUnsupported(AudioDeviceInfo.TYPE_BLE_SPEAKER, Build.VERSION_CODES.R);
        assertSupported(AudioDeviceInfo.TYPE_BLE_HEADSET, Build.VERSION_CODES.S);
        assertSupported(AudioDeviceInfo.TYPE_BLE_SPEAKER, Build.VERSION_CODES.S);
    }

    @Test
    public void headsetEndpointsArePreferredOverOutputOnlyBleSpeaker() {
        int headsetPriority = WebRtcBluetoothManager.bluetoothCommunicationDevicePriority(
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            Build.VERSION_CODES.BAKLAVA
        );
        int speakerPriority = WebRtcBluetoothManager.bluetoothCommunicationDevicePriority(
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            Build.VERSION_CODES.BAKLAVA
        );

        assertTrue(headsetPriority > speakerPriority);
    }

    private static void assertSupported(int deviceType, int sdkInt) {
        assertTrue(WebRtcBluetoothManager.bluetoothCommunicationDevicePriority(deviceType, sdkInt) >= 0);
    }

    private static void assertUnsupported(int deviceType, int sdkInt) {
        assertTrue(WebRtcBluetoothManager.bluetoothCommunicationDevicePriority(deviceType, sdkInt) < 0);
    }
}
