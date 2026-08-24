/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.webrtc;

import org.junit.Test;

import java.util.EnumSet;
import java.util.Set;

import static com.nextcloud.talk.webrtc.WebRtcAudioManager.AudioDevice.BLUETOOTH;
import static com.nextcloud.talk.webrtc.WebRtcAudioManager.AudioDevice.EARPIECE;
import static com.nextcloud.talk.webrtc.WebRtcAudioManager.AudioDevice.NONE;
import static com.nextcloud.talk.webrtc.WebRtcAudioManager.AudioDevice.SPEAKER_PHONE;
import static com.nextcloud.talk.webrtc.WebRtcAudioManager.AudioDevice.WIRED_HEADSET;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AudioRoutePolicyTest {

    @Test
    public void bluetoothConnectionHasPriority() {
        assertSelected(BLUETOOTH, devices(BLUETOOTH, EARPIECE, SPEAKER_PHONE), SPEAKER_PHONE,
            SPEAKER_PHONE, false, true);
    }

    @Test
    public void wiredHeadsetHasPriorityOverBluetoothPreference() {
        assertSelected(WIRED_HEADSET, devices(BLUETOOTH, WIRED_HEADSET), BLUETOOTH,
            SPEAKER_PHONE, true, false);
    }

    @Test
    public void explicitSpeakerSelectionIsHonoredWhenBluetoothIsNotPreferred() {
        assertSelected(SPEAKER_PHONE, devices(EARPIECE, SPEAKER_PHONE), SPEAKER_PHONE,
            EARPIECE, false, false);
    }

    @Test
    public void configuredDefaultIsUsedWithoutAnExplicitSelection() {
        assertSelected(SPEAKER_PHONE, devices(EARPIECE, SPEAKER_PHONE), NONE,
            SPEAKER_PHONE, false, false);
    }

    @Test
    public void autoBluetoothPreferenceIsReleasedAfterEndpointDisappears() {
        assertFalse(AudioRoutePolicy.shouldPreferBluetooth(NONE, true, false, true));
    }

    @Test
    public void explicitBluetoothPreferenceSurvivesEndpointDisappearance() {
        assertTrue(AudioRoutePolicy.shouldPreferBluetooth(BLUETOOTH, true, false, true));
    }

    @Test
    public void automaticBluetoothPreferenceSurvivesAnUnconfirmedTransition() {
        assertTrue(AudioRoutePolicy.shouldPreferBluetooth(NONE, true, false, false));
    }

    private static Set<WebRtcAudioManager.AudioDevice> devices(WebRtcAudioManager.AudioDevice... devices) {
        return EnumSet.of(devices[0], devices);
    }

    private static void assertSelected(
            WebRtcAudioManager.AudioDevice expected,
            Set<WebRtcAudioManager.AudioDevice> availableDevices,
            WebRtcAudioManager.AudioDevice userSelectedDevice,
            WebRtcAudioManager.AudioDevice defaultDevice,
            boolean hasWiredHeadset,
            boolean bluetoothConnected) {
        assertEquals(expected, AudioRoutePolicy.selectAudioDevice(
            availableDevices,
            userSelectedDevice,
            defaultDevice,
            hasWiredHeadset,
            bluetoothConnected
        ));
    }
}
