/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.webrtc

import com.nextcloud.talk.webrtc.WebRtcAudioManager.AudioDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("TooManyFunctions")
class ExternalAudioRouteControllerTest {

    private val phoneDevices = setOf(AudioDevice.EARPIECE, AudioDevice.SPEAKER_PHONE)

    private fun controllerOnEarpiece(userSelected: AudioDevice = AudioDevice.NONE) =
        ExternalAudioRouteController().apply {
            reset(userSelected)
            onRouteReported(AudioDevice.EARPIECE, phoneDevices, proximityNear = false)
        }

    @Test
    fun `route is not ready before the owner reported one`() {
        val controller = ExternalAudioRouteController()
        controller.reset(AudioDevice.NONE)
        assertFalse(controller.isRouteReady)
    }

    @Test
    fun `reported route is ready without a selection`() {
        val controller = controllerOnEarpiece()
        assertTrue(controller.isRouteReady)
        assertEquals(AudioDevice.EARPIECE, controller.deviceForUi)
    }

    @Test
    fun `selecting another device requests it and shows it immediately`() {
        val controller = controllerOnEarpiece()

        val request = controller.select(AudioDevice.SPEAKER_PHONE, proximityNear = false)
        assertEquals(AudioDevice.SPEAKER_PHONE, request)
        controller.onRequestStarted(AudioDevice.SPEAKER_PHONE)

        assertEquals(AudioDevice.SPEAKER_PHONE, controller.deviceForUi)
        assertFalse(controller.isRouteReady)
    }

    @Test
    fun `reported switch to the requested device completes the request`() {
        val controller = controllerOnEarpiece()
        controller.select(AudioDevice.SPEAKER_PHONE, proximityNear = false)
        controller.onRequestStarted(AudioDevice.SPEAKER_PHONE)
        controller.onRequestFinished(AudioDevice.SPEAKER_PHONE, accepted = true)

        assertNull(controller.onRouteReported(AudioDevice.SPEAKER_PHONE, phoneDevices, proximityNear = false))

        assertTrue(controller.isRouteReady)
        assertEquals(AudioDevice.SPEAKER_PHONE, controller.currentDevice)
        assertEquals(AudioDevice.NONE, controller.pendingDevice)
    }

    @Test
    fun `rejected request falls back to the active route`() {
        val controller = controllerOnEarpiece()
        controller.select(AudioDevice.SPEAKER_PHONE, proximityNear = false)
        controller.onRequestStarted(AudioDevice.SPEAKER_PHONE)

        controller.onRequestFinished(AudioDevice.SPEAKER_PHONE, accepted = false)

        assertTrue(controller.isRouteReady)
        assertEquals(AudioDevice.EARPIECE, controller.deviceForUi)
        assertEquals(AudioDevice.EARPIECE, controller.userSelectedDevice)
    }

    @Test
    fun `selecting an unavailable device is refused`() {
        val controller = controllerOnEarpiece()
        assertEquals(AudioDevice.NONE, controller.select(AudioDevice.BLUETOOTH, proximityNear = false))
        assertEquals(AudioDevice.NONE, controller.userSelectedDevice)
    }

    @Test
    fun `selecting the active device needs no request`() {
        val controller = controllerOnEarpiece()
        assertNull(controller.select(AudioDevice.EARPIECE, proximityNear = false))
        assertTrue(controller.isRouteReady)
    }

    @Test
    fun `selection made before the owner took over is requested once the route is reported`() {
        val controller = ExternalAudioRouteController()
        controller.reset(AudioDevice.SPEAKER_PHONE)

        val request = controller.onRouteReported(AudioDevice.EARPIECE, phoneDevices, proximityNear = false)

        assertEquals(AudioDevice.SPEAKER_PHONE, request)
    }

    @Test
    fun `route change made by the owner becomes the selection instead of being reverted`() {
        val controller = controllerOnEarpiece(userSelected = AudioDevice.EARPIECE)
        val withBluetooth = phoneDevices + AudioDevice.BLUETOOTH

        val request = controller.onRouteReported(AudioDevice.BLUETOOTH, withBluetooth, proximityNear = false)

        assertNull(request)
        assertEquals(AudioDevice.BLUETOOTH, controller.userSelectedDevice)
    }

    @Test
    fun `pending request is dropped when its device disappears`() {
        val controller = ExternalAudioRouteController()
        controller.reset(AudioDevice.NONE)
        val withBluetooth = phoneDevices + AudioDevice.BLUETOOTH
        controller.onRouteReported(AudioDevice.EARPIECE, withBluetooth, proximityNear = false)
        controller.select(AudioDevice.BLUETOOTH, proximityNear = false)
        controller.onRequestStarted(AudioDevice.BLUETOOTH)

        controller.onRouteReported(AudioDevice.EARPIECE, phoneDevices, proximityNear = false)

        assertEquals(AudioDevice.NONE, controller.pendingDevice)
        assertTrue(controller.isRouteReady)
    }

    @Test
    fun `speaker selection switches to earpiece while near and back when far`() {
        val controller = controllerOnEarpiece()
        controller.select(AudioDevice.SPEAKER_PHONE, proximityNear = false)
        controller.onRequestStarted(AudioDevice.SPEAKER_PHONE)
        controller.onRouteReported(AudioDevice.SPEAKER_PHONE, phoneDevices, proximityNear = false)

        assertEquals(AudioDevice.EARPIECE, controller.onProximityChanged(proximityNear = true))
        controller.onRequestStarted(AudioDevice.EARPIECE)
        controller.onRouteReported(AudioDevice.EARPIECE, phoneDevices, proximityNear = true)
        // the proximity override must not replace the user's speaker selection
        assertEquals(AudioDevice.SPEAKER_PHONE, controller.userSelectedDevice)

        assertEquals(AudioDevice.SPEAKER_PHONE, controller.onProximityChanged(proximityNear = false))
    }

    @Test
    fun `request in flight is not repeated`() {
        val controller = controllerOnEarpiece()
        controller.select(AudioDevice.SPEAKER_PHONE, proximityNear = false)
        controller.onRequestStarted(AudioDevice.SPEAKER_PHONE)

        assertNull(controller.onProximityChanged(proximityNear = false))
        assertNull(controller.onRouteReported(AudioDevice.EARPIECE, phoneDevices, proximityNear = false))
    }

    @Test
    fun `accepted request that lands on another device does not stay pending`() {
        val controller = ExternalAudioRouteController()
        controller.reset(AudioDevice.NONE)
        val withBluetooth = phoneDevices + AudioDevice.BLUETOOTH
        controller.onRouteReported(AudioDevice.EARPIECE, withBluetooth, proximityNear = false)
        controller.select(AudioDevice.BLUETOOTH, proximityNear = false)
        controller.onRequestStarted(AudioDevice.BLUETOOTH)

        // Telecom accepts, but the route stays on the earpiece and nothing new is reported
        controller.onRequestFinished(AudioDevice.BLUETOOTH, accepted = true)

        assertEquals(AudioDevice.NONE, controller.pendingDevice)
        assertTrue(controller.isRouteReady)
        assertEquals(AudioDevice.EARPIECE, controller.deviceForUi)
        // selecting Bluetooth again is possible
        assertEquals(AudioDevice.BLUETOOTH, controller.select(AudioDevice.BLUETOOTH, proximityNear = false))
    }

    @Test
    fun `route reported after an accepted request becomes the selection when it differs`() {
        val controller = ExternalAudioRouteController()
        controller.reset(AudioDevice.NONE)
        val withBluetooth = phoneDevices + AudioDevice.BLUETOOTH
        controller.onRouteReported(AudioDevice.EARPIECE, withBluetooth, proximityNear = false)
        controller.select(AudioDevice.BLUETOOTH, proximityNear = false)
        controller.onRequestStarted(AudioDevice.BLUETOOTH)
        controller.onRequestFinished(AudioDevice.BLUETOOTH, accepted = true)

        val request = controller.onRouteReported(AudioDevice.SPEAKER_PHONE, withBluetooth, proximityNear = false)

        assertNull(request)
        assertEquals(AudioDevice.SPEAKER_PHONE, controller.userSelectedDevice)
        assertEquals(AudioDevice.SPEAKER_PHONE, controller.deviceForUi)
    }
}
