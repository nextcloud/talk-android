/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.webrtc

import android.os.Build
import com.nextcloud.talk.webrtc.WebRtcBluetoothManager.State.HEADSET_AVAILABLE
import com.nextcloud.talk.webrtc.WebRtcBluetoothManager.State.HEADSET_UNAVAILABLE
import com.nextcloud.talk.webrtc.WebRtcBluetoothManager.State.SCO_CONNECTED
import com.nextcloud.talk.webrtc.WebRtcBluetoothManager.State.SCO_CONNECTING
import com.nextcloud.talk.webrtc.WebRtcBluetoothManager.State.SCO_DISCONNECTING
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("TooManyFunctions")
class BluetoothRouteStatePolicyTest {
    @Test
    fun `manual tap keeps an accepted connecting request`() {
        assertTrue(WebRtcBluetoothManager.isBluetoothTransitionInProgress(SCO_CONNECTING))
    }

    @Test
    fun `manual tap waits for an in-progress disconnect before retrying`() {
        assertTrue(WebRtcBluetoothManager.isBluetoothTransitionInProgress(SCO_DISCONNECTING))
    }

    @Test
    fun `accepted and queued selections remain visible to the UI`() {
        assertTrue(WebRtcBluetoothManager.isBluetoothSelectionActive(SCO_CONNECTING, false, false))
        assertTrue(WebRtcBluetoothManager.isBluetoothSelectionActive(HEADSET_AVAILABLE, true, false))
        assertFalse(WebRtcBluetoothManager.isBluetoothSelectionActive(HEADSET_AVAILABLE, false, false))
    }

    @Test
    fun `scheduled retry prevents an immediate second Bluetooth attempt`() {
        assertFalse(WebRtcBluetoothManager.shouldStartBluetoothRoute(HEADSET_AVAILABLE, true, false, true, true))
        assertTrue(WebRtcBluetoothManager.shouldStartBluetoothRoute(HEADSET_AVAILABLE, true, false, false, true))
    }

    @Test
    fun `exhausted attempts prevent automatic Bluetooth restarts`() {
        assertFalse(WebRtcBluetoothManager.shouldStartBluetoothRoute(HEADSET_AVAILABLE, true, false, false, false))
    }

    @Test
    fun `legacy profile connection remains an accepted pending selection`() {
        assertTrue(WebRtcBluetoothManager.isBluetoothSelectionActive(HEADSET_UNAVAILABLE, false, true))
    }

    @Test
    fun `removing requested endpoint does not keep connecting to another endpoint`() {
        assertFalse(WebRtcBluetoothManager.shouldKeepModernBluetoothState(SCO_CONNECTING, false, false, true))
        assertTrue(WebRtcBluetoothManager.shouldResetModernBluetoothAttempts(SCO_CONNECTING, false, true))
    }

    @Test
    fun `removing confirmed endpoint does not treat another endpoint as connected`() {
        assertFalse(WebRtcBluetoothManager.shouldKeepModernBluetoothState(SCO_CONNECTED, false, false, true))
    }

    @Test
    fun `removing unrelated endpoint keeps the confirmed route`() {
        assertTrue(WebRtcBluetoothManager.shouldKeepModernBluetoothState(SCO_CONNECTED, false, true, true))
    }

    @Test
    fun `queued Bluetooth callback after clear is rejected`() {
        assertFalse(WebRtcBluetoothManager.shouldAcceptModernBluetoothCallback(HEADSET_AVAILABLE, true, true, false))
    }

    @Test
    fun `late Bluetooth callback after rejected request is rejected`() {
        assertFalse(WebRtcBluetoothManager.shouldAcceptModernBluetoothCallback(HEADSET_AVAILABLE, true, false, false))
    }

    @Test
    fun `only matching pending request can confirm modern Bluetooth`() {
        assertFalse(WebRtcBluetoothManager.shouldAcceptModernBluetoothCallback(SCO_CONNECTING, true, false, false))
        assertTrue(WebRtcBluetoothManager.shouldAcceptModernBluetoothCallback(SCO_CONNECTING, true, false, true))
    }

    @Test
    fun `initial system Bluetooth route and connected duplicate are accepted`() {
        assertTrue(WebRtcBluetoothManager.shouldAcceptModernBluetoothCallback(HEADSET_AVAILABLE, false, false, false))
        assertTrue(WebRtcBluetoothManager.shouldAcceptModernBluetoothCallback(SCO_CONNECTED, true, false, false))
    }

    @Test
    fun `queued Bluetooth callback cannot restore app-controlled non-Bluetooth route`() {
        assertFalse(WebRtcBluetoothManager.shouldAcceptModernBluetoothCallback(HEADSET_AVAILABLE, true, false, true))
    }

    @Test
    fun `disconnect timeout never uses stale getter to resurrect connected state`() {
        assertEquals(HEADSET_AVAILABLE, WebRtcBluetoothManager.stateAfterModernRouteClear(true))
        assertEquals(HEADSET_UNAVAILABLE, WebRtcBluetoothManager.stateAfterModernRouteClear(false))
    }

    @Test
    fun `route clear finishes after a confirmed non-Bluetooth route`() {
        assertFalse(WebRtcBluetoothManager.shouldKeepModernRouteClearPending(true, true, false))
        assertFalse(WebRtcBluetoothManager.shouldKeepModernRouteClearPending(false, true, true))
    }

    @Test
    fun `route clear remains pending for Bluetooth or unknown getter result`() {
        assertTrue(WebRtcBluetoothManager.shouldKeepModernRouteClearPending(true, true, true))
        assertTrue(WebRtcBluetoothManager.shouldKeepModernRouteClearPending(true, false, false))
    }

    @Test
    fun `focus gain reasserts only preferred modern Bluetooth route with retries available`() {
        assertTrue(
            WebRtcBluetoothManager.shouldReassertModernBluetoothAfterFocusGain(
                SCO_CONNECTED,
                true,
                false,
                Build.VERSION_CODES.S,
                true
            )
        )
        assertFalse(
            WebRtcBluetoothManager.shouldReassertModernBluetoothAfterFocusGain(
                SCO_CONNECTED,
                true,
                false,
                Build.VERSION_CODES.R,
                true
            )
        )
        assertFalse(
            WebRtcBluetoothManager.shouldReassertModernBluetoothAfterFocusGain(
                SCO_CONNECTED,
                false,
                false,
                Build.VERSION_CODES.S,
                true
            )
        )
        assertFalse(
            WebRtcBluetoothManager.shouldReassertModernBluetoothAfterFocusGain(
                SCO_CONNECTED,
                true,
                true,
                Build.VERSION_CODES.S,
                true
            )
        )
        assertFalse(
            WebRtcBluetoothManager.shouldReassertModernBluetoothAfterFocusGain(
                HEADSET_AVAILABLE,
                true,
                false,
                Build.VERSION_CODES.S,
                true
            )
        )
        assertFalse(
            WebRtcBluetoothManager.shouldReassertModernBluetoothAfterFocusGain(
                SCO_CONNECTED,
                true,
                false,
                Build.VERSION_CODES.S,
                false
            )
        )
    }

    @Test
    fun `legacy connected callback is rejected during disconnect`() {
        assertTrue(WebRtcBluetoothManager.shouldAcceptLegacyScoConnected(SCO_CONNECTING))
        assertFalse(WebRtcBluetoothManager.shouldAcceptLegacyScoConnected(SCO_DISCONNECTING))
    }
}
