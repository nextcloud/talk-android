/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.webrtc

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
        assertFalse(
            WebRtcBluetoothManager.shouldStartBluetoothRoute(
                HEADSET_AVAILABLE,
                true,
                false,
                true,
                true,
                false
            )
        )
        assertTrue(
            WebRtcBluetoothManager.shouldStartBluetoothRoute(
                HEADSET_AVAILABLE,
                true,
                false,
                false,
                true,
                false
            )
        )
    }

    @Test
    fun `automatic Bluetooth restarts wait for focus and remaining attempts`() {
        assertFalse(
            WebRtcBluetoothManager.shouldStartBluetoothRoute(
                HEADSET_AVAILABLE,
                true,
                false,
                false,
                false,
                false
            )
        )
        assertFalse(
            WebRtcBluetoothManager.shouldStartBluetoothRoute(
                HEADSET_AVAILABLE,
                true,
                false,
                false,
                true,
                true
            )
        )
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
    fun `late matching Bluetooth callback after rejected request is rejected`() {
        assertFalse(WebRtcBluetoothManager.shouldAcceptModernBluetoothCallback(HEADSET_AVAILABLE, true, false, true))
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
        assertFalse(WebRtcBluetoothManager.shouldAcceptModernBluetoothCallback(HEADSET_AVAILABLE, true, false, false))
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
    fun `focus gain recovery requires connected preferred Bluetooth without wired output`() {
        assertTrue(
            WebRtcBluetoothManager.shouldReassertBluetoothAfterFocusGain(
                SCO_CONNECTED,
                true,
                false
            )
        )
        assertFalse(
            WebRtcBluetoothManager.shouldReassertBluetoothAfterFocusGain(
                SCO_CONNECTED,
                false,
                false
            )
        )
        assertFalse(
            WebRtcBluetoothManager.shouldReassertBluetoothAfterFocusGain(
                SCO_CONNECTED,
                true,
                true
            )
        )
        assertFalse(
            WebRtcBluetoothManager.shouldReassertBluetoothAfterFocusGain(
                HEADSET_AVAILABLE,
                true,
                false
            )
        )
    }

    @Test
    fun `modern focus recovery preserves the retry limit`() {
        assertEquals(
            WebRtcBluetoothManager.ModernFocusRecoveryAction.REASSERT,
            WebRtcBluetoothManager.modernFocusRecoveryAction(true)
        )
        assertEquals(
            WebRtcBluetoothManager.ModernFocusRecoveryAction.FALL_BACK,
            WebRtcBluetoothManager.modernFocusRecoveryAction(false)
        )
    }

    @Test
    fun `legacy focus recovery distinguishes reclaim restart and fallback`() {
        assertEquals(
            WebRtcBluetoothManager.LegacyFocusRecoveryAction.RECLAIM_CONNECTED,
            WebRtcBluetoothManager.legacyFocusRecoveryAction(true, true, false)
        )
        assertEquals(
            WebRtcBluetoothManager.LegacyFocusRecoveryAction.RESTART_DISCONNECTED,
            WebRtcBluetoothManager.legacyFocusRecoveryAction(true, false, true)
        )
        assertEquals(
            WebRtcBluetoothManager.LegacyFocusRecoveryAction.FALL_BACK,
            WebRtcBluetoothManager.legacyFocusRecoveryAction(true, false, false)
        )
        assertEquals(
            WebRtcBluetoothManager.LegacyFocusRecoveryAction.FALL_BACK,
            WebRtcBluetoothManager.legacyFocusRecoveryAction(false, true, true)
        )
    }

    @Test
    fun `legacy connected callback is rejected during disconnect`() {
        assertTrue(WebRtcBluetoothManager.shouldAcceptLegacyScoConnected(SCO_CONNECTING))
        assertFalse(WebRtcBluetoothManager.shouldAcceptLegacyScoConnected(SCO_DISCONNECTING))
    }
}
