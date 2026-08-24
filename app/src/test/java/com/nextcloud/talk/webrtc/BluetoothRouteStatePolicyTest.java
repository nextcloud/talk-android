/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.webrtc;

import android.os.Build;

import org.junit.Test;

import static com.nextcloud.talk.webrtc.WebRtcBluetoothManager.State.HEADSET_AVAILABLE;
import static com.nextcloud.talk.webrtc.WebRtcBluetoothManager.State.HEADSET_UNAVAILABLE;
import static com.nextcloud.talk.webrtc.WebRtcBluetoothManager.State.SCO_CONNECTED;
import static com.nextcloud.talk.webrtc.WebRtcBluetoothManager.State.SCO_CONNECTING;
import static com.nextcloud.talk.webrtc.WebRtcBluetoothManager.State.SCO_DISCONNECTING;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BluetoothRouteStatePolicyTest {

    @Test
    public void manualTapKeepsAnAcceptedConnectingRequest() {
        assertTrue(WebRtcBluetoothManager.isBluetoothTransitionInProgress(SCO_CONNECTING));
    }

    @Test
    public void manualTapWaitsForAnInProgressDisconnectBeforeRetrying() {
        assertTrue(WebRtcBluetoothManager.isBluetoothTransitionInProgress(SCO_DISCONNECTING));
    }

    @Test
    public void acceptedAndQueuedSelectionsRemainVisibleToTheUi() {
        assertTrue(WebRtcBluetoothManager.isBluetoothSelectionActive(SCO_CONNECTING, false, false));
        assertTrue(WebRtcBluetoothManager.isBluetoothSelectionActive(HEADSET_AVAILABLE, true, false));
        assertFalse(WebRtcBluetoothManager.isBluetoothSelectionActive(HEADSET_AVAILABLE, false, false));
    }

    @Test
    public void aScheduledRetryPreventsAnImmediateSecondBluetoothAttempt() {
        assertFalse(WebRtcBluetoothManager.shouldStartBluetoothRoute(
            HEADSET_AVAILABLE,
            true,
            false,
            true
        ));
        assertTrue(WebRtcBluetoothManager.shouldStartBluetoothRoute(
            HEADSET_AVAILABLE,
            true,
            false,
            false
        ));
    }

    @Test
    public void legacyProfileConnectionRemainsAnAcceptedPendingSelection() {
        assertTrue(WebRtcBluetoothManager.isBluetoothSelectionActive(HEADSET_UNAVAILABLE, false, true));
    }

    @Test
    public void removingTheRequestedEndpointDoesNotKeepConnectingToAnotherEndpoint() {
        assertFalse(WebRtcBluetoothManager.shouldKeepModernBluetoothState(
            SCO_CONNECTING,
            false,
            false,
            true
        ));
        assertTrue(WebRtcBluetoothManager.shouldResetModernBluetoothAttempts(
            SCO_CONNECTING,
            false,
            true
        ));
    }

    @Test
    public void removingTheConfirmedEndpointDoesNotTreatAnotherEndpointAsConnected() {
        assertFalse(WebRtcBluetoothManager.shouldKeepModernBluetoothState(
            SCO_CONNECTED,
            false,
            false,
            true
        ));
    }

    @Test
    public void removingAnUnrelatedEndpointKeepsTheConfirmedRoute() {
        assertTrue(WebRtcBluetoothManager.shouldKeepModernBluetoothState(
            SCO_CONNECTED,
            false,
            true,
            true
        ));
    }

    @Test
    public void queuedBluetoothCallbackAfterClearIsRejected() {
        assertFalse(WebRtcBluetoothManager.shouldAcceptModernBluetoothCallback(
            HEADSET_AVAILABLE,
            true,
            true,
            false,
            true
        ));
    }

    @Test
    public void lateBluetoothCallbackAfterRejectedRequestIsRejected() {
        assertFalse(WebRtcBluetoothManager.shouldAcceptModernBluetoothCallback(
            HEADSET_AVAILABLE,
            true,
            false,
            false,
            false
        ));
    }

    @Test
    public void onlyTheMatchingPendingRequestCanConfirmModernBluetooth() {
        assertFalse(WebRtcBluetoothManager.shouldAcceptModernBluetoothCallback(
            SCO_CONNECTING,
            true,
            false,
            false,
            false
        ));
        assertTrue(WebRtcBluetoothManager.shouldAcceptModernBluetoothCallback(
            SCO_CONNECTING,
            true,
            false,
            true,
            true
        ));
    }

    @Test
    public void initialSystemBluetoothRouteAndConnectedDuplicateAreAccepted() {
        assertTrue(WebRtcBluetoothManager.shouldAcceptModernBluetoothCallback(
            HEADSET_AVAILABLE,
            false,
            false,
            false,
            false
        ));
        assertTrue(WebRtcBluetoothManager.shouldAcceptModernBluetoothCallback(
            SCO_CONNECTED,
            true,
            false,
            false,
            false
        ));
    }

    @Test
    public void authoritativeSystemPickerCallbackIsAcceptedAfterAnAppControlledRoute() {
        assertTrue(WebRtcBluetoothManager.shouldAcceptModernBluetoothCallback(
            HEADSET_AVAILABLE,
            true,
            false,
            false,
            true
        ));
    }

    @Test
    public void disconnectTimeoutNeverUsesAStaleGetterToResurrectConnectedState() {
        assertEquals(HEADSET_AVAILABLE, WebRtcBluetoothManager.stateAfterModernRouteClear(true));
        assertEquals(HEADSET_UNAVAILABLE, WebRtcBluetoothManager.stateAfterModernRouteClear(false));
    }

    @Test
    public void routeClearFinishesAfterAConfirmedNonBluetoothRoute() {
        assertFalse(WebRtcBluetoothManager.shouldKeepModernRouteClearPending(true, true, false));
        assertFalse(WebRtcBluetoothManager.shouldKeepModernRouteClearPending(false, true, true));
    }

    @Test
    public void routeClearRemainsPendingForBluetoothOrAnUnknownGetterResult() {
        assertTrue(WebRtcBluetoothManager.shouldKeepModernRouteClearPending(true, true, true));
        assertTrue(WebRtcBluetoothManager.shouldKeepModernRouteClearPending(true, false, false));
    }

    @Test
    public void focusGainReassertsOnlyThePreferredModernBluetoothRoute() {
        assertTrue(WebRtcBluetoothManager.shouldReassertModernBluetoothAfterFocusGain(
            SCO_CONNECTED,
            true,
            false,
            Build.VERSION_CODES.S
        ));
        assertFalse(WebRtcBluetoothManager.shouldReassertModernBluetoothAfterFocusGain(
            SCO_CONNECTED,
            true,
            false,
            Build.VERSION_CODES.R
        ));
        assertFalse(WebRtcBluetoothManager.shouldReassertModernBluetoothAfterFocusGain(
            SCO_CONNECTED,
            false,
            false,
            Build.VERSION_CODES.S
        ));
        assertFalse(WebRtcBluetoothManager.shouldReassertModernBluetoothAfterFocusGain(
            SCO_CONNECTED,
            true,
            true,
            Build.VERSION_CODES.S
        ));
        assertFalse(WebRtcBluetoothManager.shouldReassertModernBluetoothAfterFocusGain(
            HEADSET_AVAILABLE,
            true,
            false,
            Build.VERSION_CODES.S
        ));
    }

    @Test
    public void legacyConnectedCallbackIsRejectedDuringDisconnect() {
        assertTrue(WebRtcBluetoothManager.shouldAcceptLegacyScoConnected(SCO_CONNECTING));
        assertFalse(WebRtcBluetoothManager.shouldAcceptLegacyScoConnected(SCO_DISCONNECTING));
    }
}
