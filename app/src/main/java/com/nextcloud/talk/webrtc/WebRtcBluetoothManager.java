/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Tim Krüger <t@timkrueger.me>
 * SPDX-FileCopyrightText: 2017 Mario Danic <mario@lovelyhq.com>
 * SPDX-FileCopyrightText: 2016 The WebRTC Project Authors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Original code:
 *
 * Copyright 2016 The WebRTC Project Authors. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license
 * that can be found in the LICENSE file in the root of the source
 * tree. An additional intellectual property rights grant can be found
 * in the file PATENTS.  All contributing project authors may
 * be found in the AUTHORS file in the root of the source tree.
 */
package com.nextcloud.talk.webrtc;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.Log;

import com.nextcloud.talk.utils.ContextExtensionsKt;
import com.nextcloud.talk.utils.ReceiverFlag;

import org.webrtc.ThreadUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

public class WebRtcBluetoothManager {
    private static final String TAG = WebRtcBluetoothManager.class.getSimpleName();

    // Timeout interval for starting or stopping audio to a Bluetooth SCO device.
    private static final int BLUETOOTH_SCO_TIMEOUT_MS = 4000;
    private static final int BLUETOOTH_ROUTE_RETRY_DELAY_MS = 500;
    // Maximum number of SCO connection attempts.
    private static final int MAX_SCO_CONNECTION_ATTEMPTS = 2;
    private final Context apprtcContext;
    private final WebRtcAudioManager webRtcAudioManager;
    private final AudioManager audioManager;
    private final Handler handler;
    private final BluetoothProfile.ServiceListener bluetoothServiceListener;
    private final BroadcastReceiver bluetoothHeadsetReceiver;
    int scoConnectionAttempts;
    private State bluetoothState;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothHeadset bluetoothHeadset;
    private BluetoothDevice bluetoothDevice;
    private ModernBluetoothRoute modernBluetoothRoute;
    private boolean headsetProfileExpected;
    // Runs when the Bluetooth timeout expires. We use that timeout after calling
    // startScoAudio() or stopScoAudio() because we're not guaranteed to get a
    // callback after those calls.
    private final Runnable bluetoothTimeoutRunnable = this::bluetoothTimeout;
    private final Runnable bluetoothRouteRetryRunnable = this::retryBluetoothRoute;
    private boolean bluetoothRouteRetryScheduled;
    private boolean nonBluetoothFallbackPending;
    private boolean started = false;

    protected WebRtcBluetoothManager(Context context, WebRtcAudioManager audioManager) {
        Log.d(TAG, "ctor");
        ThreadUtils.checkIsOnMainThread();
        apprtcContext = context;
        webRtcAudioManager = audioManager;
        this.audioManager = getAudioManager(context);
        bluetoothState = State.UNINITIALIZED;
        bluetoothServiceListener = new BluetoothServiceListener();
        bluetoothHeadsetReceiver = new BluetoothHeadsetBroadcastReceiver();
        handler = new Handler(Looper.getMainLooper());
    }

    /**
     * Construction.
     */
    static WebRtcBluetoothManager create(Context context, WebRtcAudioManager audioManager) {
        return new WebRtcBluetoothManager(context, audioManager);
    }

    static boolean isBluetoothCommunicationDeviceType(int type) {
        return bluetoothCommunicationDevicePriority(type, Build.VERSION.SDK_INT) >= 0;
    }

    @SuppressLint("InlinedApi")
    static int bluetoothCommunicationDevicePriority(int type, int sdkInt) {
        if (sdkInt >= Build.VERSION_CODES.S && type == AudioDeviceInfo.TYPE_BLE_HEADSET) {
            return 4;
        }
        if (type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
            return 3;
        }
        if (sdkInt >= Build.VERSION_CODES.S && type == AudioDeviceInfo.TYPE_HEARING_AID) {
            return 2;
        }
        if (sdkInt >= Build.VERSION_CODES.S && type == AudioDeviceInfo.TYPE_BLE_SPEAKER) {
            return 1;
        }
        // A2DP is a media-only output and cannot be used as a two-way call route.
        return -1;
    }

    static boolean isBluetoothTransitionInProgress(State state) {
        return state == State.SCO_CONNECTING || state == State.SCO_DISCONNECTING;
    }

    static boolean isBluetoothSelectionActive(
            State state,
            boolean retryScheduled,
            boolean legacyHeadsetProfileExpected) {
        return state == State.SCO_CONNECTING
            || state == State.SCO_CONNECTED
            || state == State.SCO_DISCONNECTING
            || retryScheduled
            || (state == State.HEADSET_UNAVAILABLE && legacyHeadsetProfileExpected);
    }

    static boolean shouldKeepModernBluetoothState(
            State state,
            boolean requestedDeviceAvailable,
            boolean confirmedDeviceAvailable,
            boolean anyBluetoothDeviceAvailable) {
        if (state == State.SCO_CONNECTING) {
            return requestedDeviceAvailable;
        }
        if (state == State.SCO_CONNECTED) {
            return confirmedDeviceAvailable;
        }
        return state == State.SCO_DISCONNECTING && anyBluetoothDeviceAvailable;
    }

    static boolean shouldResetModernBluetoothAttempts(
            State state,
            boolean requestedDeviceAvailable,
            boolean anyBluetoothDeviceAvailable) {
        return state == State.SCO_CONNECTING
            && !requestedDeviceAvailable
            && anyBluetoothDeviceAvailable;
    }

    static boolean shouldStartBluetoothRoute(
            State state,
            boolean bluetoothPreferred,
            boolean hasWiredHeadset,
            boolean retryScheduled,
            boolean attemptsAvailable,
            boolean transientFocusLoss) {
        return state == State.HEADSET_AVAILABLE
            && bluetoothPreferred
            && !hasWiredHeadset
            && !retryScheduled
            && attemptsAvailable
            && !transientFocusLoss;
    }

    static boolean shouldAcceptModernBluetoothCallback(
            State state,
            boolean routeSelectionControlled,
            boolean routeClearPending,
            boolean pendingRequestMatches) {
        if (state == State.SCO_DISCONNECTING || routeClearPending) {
            return false;
        }
        if (state == State.SCO_CONNECTING) {
            return pendingRequestMatches;
        }
        if (state == State.SCO_CONNECTED) {
            return true;
        }
        return !routeSelectionControlled;
    }

    static boolean shouldAcceptLegacyScoConnected(State state) {
        return state == State.SCO_CONNECTING || state == State.SCO_CONNECTED;
    }

    static State stateAfterModernRouteClear(boolean bluetoothAvailable) {
        return bluetoothAvailable ? State.HEADSET_AVAILABLE : State.HEADSET_UNAVAILABLE;
    }

    static boolean shouldKeepModernRouteClearPending(
            boolean routeClearPending,
            boolean currentRouteKnown,
            boolean bluetoothSelected) {
        return routeClearPending && (!currentRouteKnown || bluetoothSelected);
    }

    static boolean shouldReassertBluetoothAfterFocusGain(
            State state,
            boolean bluetoothPreferred,
            boolean hasWiredHeadset) {
        return state == State.SCO_CONNECTED
            && bluetoothPreferred
            && !hasWiredHeadset;
    }

    enum ModernFocusRecoveryAction {
        REASSERT,
        FALL_BACK
    }

    static ModernFocusRecoveryAction modernFocusRecoveryAction(boolean attemptsAvailable) {
        return attemptsAvailable ? ModernFocusRecoveryAction.REASSERT : ModernFocusRecoveryAction.FALL_BACK;
    }

    enum LegacyFocusRecoveryAction {
        RECLAIM_CONNECTED,
        RESTART_DISCONNECTED,
        FALL_BACK
    }

    static LegacyFocusRecoveryAction legacyFocusRecoveryAction(
            boolean headsetAvailable,
            boolean headsetAudioConnected,
            boolean attemptsAvailable) {
        if (!headsetAvailable) {
            return LegacyFocusRecoveryAction.FALL_BACK;
        }
        if (headsetAudioConnected) {
            return LegacyFocusRecoveryAction.RECLAIM_CONNECTED;
        }
        return attemptsAvailable
            ? LegacyFocusRecoveryAction.RESTART_DISCONNECTED
            : LegacyFocusRecoveryAction.FALL_BACK;
    }

    /**
     * Returns the internal state.
     */
    public State getState() {
        ThreadUtils.checkIsOnMainThread();
        return bluetoothState;
    }

    public boolean isHeadsetProfileExpected() {
        ThreadUtils.checkIsOnMainThread();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && modernBluetoothRoute != null) {
            return modernBluetoothRoute.hasBluetoothDevice();
        }
        return headsetProfileExpected;
    }

    public boolean isBluetoothSelectionActive() {
        ThreadUtils.checkIsOnMainThread();
        boolean legacyHeadsetProfilePending = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
            && started
            && headsetProfileExpected;
        return isBluetoothSelectionActive(
            bluetoothState,
            bluetoothRouteRetryScheduled,
            legacyHeadsetProfilePending
        );
    }

    boolean isBluetoothRouteRetryScheduled() {
        ThreadUtils.checkIsOnMainThread();
        return bluetoothRouteRetryScheduled;
    }

    boolean hasRemainingScoConnectionAttempts() {
        ThreadUtils.checkIsOnMainThread();
        return scoConnectionAttempts < MAX_SCO_CONNECTION_ATTEMPTS;
    }

    boolean isNonBluetoothFallbackPending() {
        ThreadUtils.checkIsOnMainThread();
        return nonBluetoothFallbackPending;
    }

    public void resetScoConnectionAttempts() {
        ThreadUtils.checkIsOnMainThread();
        scoConnectionAttempts = 0;
        nonBluetoothFallbackPending = false;
        cancelBluetoothRouteRetry();
    }

    public void reassertBluetoothAudioAfterFocusGain(boolean bluetoothPreferred, boolean hasWiredHeadset) {
        ThreadUtils.checkIsOnMainThread();
        if (!started
            || !shouldReassertBluetoothAfterFocusGain(
                bluetoothState,
                bluetoothPreferred,
                hasWiredHeadset
            )) {
            return;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || modernBluetoothRoute == null) {
            reassertLegacyBluetoothAudioAfterFocusGain();
            return;
        }

        reassertModernBluetoothAudioAfterFocusGain();
    }

    private void reassertModernBluetoothAudioAfterFocusGain() {
        cancelTimer();
        cancelBluetoothRouteRetry();
        if (!modernBluetoothRoute.hasConfirmedBluetoothDevice()) {
            modernBluetoothRoute.clearConfirmedBluetoothDevice();
            bluetoothState = stateAfterModernRouteClear(modernBluetoothRoute.hasBluetoothDevice());
            return;
        }
        if (modernFocusRecoveryAction(hasRemainingScoConnectionAttempts())
                == ModernFocusRecoveryAction.FALL_BACK) {
            nonBluetoothFallbackPending = true;
            return;
        }
        bluetoothState = State.SCO_CONNECTING;
        scoConnectionAttempts++;
        boolean requestAccepted = modernBluetoothRoute.reselectConfirmedBluetoothDevice();
        if (bluetoothState == State.SCO_CONNECTED) {
            return;
        }
        if (!requestAccepted) {
            bluetoothState = stateAfterModernRouteClear(modernBluetoothRoute.hasBluetoothDevice());
            if (bluetoothState == State.HEADSET_AVAILABLE) {
                scheduleBluetoothRouteRetry();
            }
            return;
        }
        startTimer();
        Log.d(TAG, "Reasserting the confirmed Bluetooth route after audio focus returned");
    }

    @SuppressLint("MissingPermission")
    private void reassertLegacyBluetoothAudioAfterFocusGain() {
        boolean headsetAvailable = bluetoothHeadset != null
            && bluetoothDevice != null
            && bluetoothHeadset.getConnectionState(bluetoothDevice) == BluetoothProfile.STATE_CONNECTED;
        boolean headsetAudioConnected = headsetAvailable
            && bluetoothHeadset.isAudioConnected(bluetoothDevice);
        LegacyFocusRecoveryAction action = legacyFocusRecoveryAction(
            headsetAvailable,
            headsetAudioConnected,
            hasRemainingScoConnectionAttempts()
        );
        if (action == LegacyFocusRecoveryAction.RECLAIM_CONNECTED) {
            audioManager.setBluetoothScoOn(true);
            Log.d(TAG, "Reasserted the connected legacy Bluetooth SCO route after audio focus returned");
            return;
        }

        bluetoothState = headsetAvailable ? State.HEADSET_AVAILABLE : State.HEADSET_UNAVAILABLE;
        if (action == LegacyFocusRecoveryAction.RESTART_DISCONNECTED) {
            Log.w(TAG, "Legacy Bluetooth SCO was lost while audio focus was away; restarting it");
            startScoAudio();
        } else {
            Log.w(TAG, "Legacy Bluetooth SCO cannot be recovered after audio focus returned");
        }
    }

    void onNonBluetoothCommunicationDeviceSelected() {
        ThreadUtils.checkIsOnMainThread();
        if (!started || modernBluetoothRoute == null) {
            return;
        }
        cancelTimer();
        cancelBluetoothRouteRetry();
        nonBluetoothFallbackPending = false;
        modernBluetoothRoute.confirmNonBluetoothRouteSelection();
        bluetoothState = stateAfterModernRouteClear(modernBluetoothRoute.hasBluetoothDevice());
        Log.d(TAG, "A non-Bluetooth communication device was selected without clearing the accepted route");
    }

    /**
     * Activates components required to detect Bluetooth devices and to enable
     * BT SCO (audio is routed via BT SCO) for the headset profile. The end
     * state will be HEADSET_UNAVAILABLE but a state machine has started which
     * will start a state change sequence where the final outcome depends on
     * if/when the BT headset is enabled.
     * Example of state change sequence when start() is called while BT device
     * is connected and enabled:
     * UNINITIALIZED --> HEADSET_UNAVAILABLE --> HEADSET_AVAILABLE -->
     * SCO_CONNECTING --> SCO_CONNECTED <==> audio is now routed via BT SCO.
     * Note that the AudioManager is also involved in driving this state
     * change.
     */
    @SuppressLint("MissingPermission")
    public void start() {
        ThreadUtils.checkIsOnMainThread();
        Log.d(TAG, "start");
        if (bluetoothState != State.UNINITIALIZED) {
            Log.w(TAG, "Invalid BT state");
            return;
        }
        bluetoothHeadset = null;
        bluetoothDevice = null;
        scoConnectionAttempts = 0;
        bluetoothRouteRetryScheduled = false;
        nonBluetoothFallbackPending = false;
        headsetProfileExpected = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            bluetoothState = State.HEADSET_UNAVAILABLE;
            modernBluetoothRoute = new ModernBluetoothRoute();
            started = true;
            modernBluetoothRoute.start();
            updateDevice();
            Log.d(TAG, "Modern Bluetooth communication route started: " + bluetoothState);
            return;
        }
        // BluetoothHeadset requires the runtime Bluetooth permission. The Android 12+
        // communication-device API above only requires MODIFY_AUDIO_SETTINGS.
        if (hasNoBluetoothPermission()) {
            return;
        }
        // Get a handle to the default local Bluetooth adapter.
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Log.w(TAG, "Device does not support Bluetooth");
            return;
        }
        int headsetProfileState = bluetoothAdapter.getProfileConnectionState(BluetoothProfile.HEADSET);
        headsetProfileExpected = headsetProfileState == BluetoothProfile.STATE_CONNECTED
            || headsetProfileState == BluetoothProfile.STATE_CONNECTING;
        Log.d(TAG, "HEADSET profile state: " + stateToString(headsetProfileState));
        // Ensure that the device supports use of BT SCO audio for off call use cases.
        if (!audioManager.isBluetoothScoAvailableOffCall()) {
            Log.e(TAG, "Bluetooth SCO audio is not available off call");
            return;
        }
        logBluetoothAdapterInfo(bluetoothAdapter);
        // Establish a connection to the HEADSET profile (includes both Bluetooth Headset and
        // Hands-Free) proxy object and install a listener.
        if (!getBluetoothProfileProxy(
                apprtcContext, bluetoothServiceListener, BluetoothProfile.HEADSET)) {
            Log.e(TAG, "BluetoothAdapter.getProfileProxy(HEADSET) failed");
            return;
        }
        // Register receivers for BluetoothHeadset change notifications.
        IntentFilter bluetoothHeadsetFilter = new IntentFilter();
        // Register receiver for change in connection state of the Headset profile.
        bluetoothHeadsetFilter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        // Register receiver for change in audio connection state of the Headset profile.
        bluetoothHeadsetFilter.addAction(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED);
        registerReceiver(bluetoothHeadsetReceiver, bluetoothHeadsetFilter);
        Log.d(TAG, "Bluetooth proxy for headset profile has started");
        bluetoothState = State.HEADSET_UNAVAILABLE;
        started = true;
        Log.d(TAG, "start done: BT state=" + bluetoothState);
    }

    /**
     * Stops and closes all components related to Bluetooth audio.
     */
    public void stop() {
        ThreadUtils.checkIsOnMainThread();
        Log.d(TAG, "stop: BT state=" + bluetoothState);
        cancelBluetoothRouteRetry();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && modernBluetoothRoute != null) {
            cancelTimer();
            modernBluetoothRoute.stop();
            modernBluetoothRoute = null;
            bluetoothState = State.UNINITIALIZED;
            headsetProfileExpected = false;
            started = false;
            Log.d(TAG, "Modern Bluetooth communication route stopped");
            return;
        }
        if (bluetoothAdapter == null) {
            return;
        }
        // Stop BT SCO connection with remote device if needed.
        stopScoAudio();
        // Close down remaining BT resources.
        if (bluetoothState == State.UNINITIALIZED) {
            return;
        }
        unregisterReceiver(bluetoothHeadsetReceiver);
        cancelTimer();
        if (bluetoothHeadset != null) {
            bluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, bluetoothHeadset);
            bluetoothHeadset = null;
        }
        bluetoothAdapter = null;
        bluetoothDevice = null;
        bluetoothState = State.UNINITIALIZED;
        headsetProfileExpected = false;
        started = false;
        Log.d(TAG, "stop done: BT state=" + bluetoothState);
    }

    /**
     * Starts Bluetooth SCO connection with remote device.
     * Note that the phone application always has the priority on the usage of the SCO connection
     * for telephony. If this method is called while the phone is in call it will be ignored.
     * Similarly, if a call is received or sent while an application is using the SCO connection,
     * the connection will be lost for the application and NOT returned automatically when the call
     * ends. Also note that: up to and including API version JELLY_BEAN_MR1, this method initiates a
     * virtual voice call to the Bluetooth headset. After API version JELLY_BEAN_MR2 only a raw SCO
     * audio connection is established.
     * TODO(henrika): should we add support for virtual voice call to BT headset also for JBMR2 and
     * higher. It might be required to initiates a virtual voice call since many devices do not
     * accept SCO audio without a "call".
     */
    public boolean startScoAudio() {
        ThreadUtils.checkIsOnMainThread();
        Log.d(TAG, "startSco: BT state=" + bluetoothState + ", "
                + "attempts: " + scoConnectionAttempts + ", "
                + "SCO is on: " + isScoOn());
        if (scoConnectionAttempts >= MAX_SCO_CONNECTION_ATTEMPTS) {
            Log.e(TAG, "BT SCO connection fails - no more attempts");
            return false;
        }
        if (bluetoothState != State.HEADSET_AVAILABLE) {
            Log.e(TAG, "BT SCO connection fails - no headset available");
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && modernBluetoothRoute != null) {
            cancelBluetoothRouteRetry();
            bluetoothState = State.SCO_CONNECTING;
            scoConnectionAttempts++;
            boolean requestAccepted = modernBluetoothRoute.selectBluetoothDevice();
            if (bluetoothState == State.SCO_CONNECTED) {
                return true;
            }
            if (!requestAccepted) {
                Log.w(TAG, "Android rejected the Bluetooth communication-device request");
                bluetoothState = State.HEADSET_AVAILABLE;
                scheduleBluetoothRouteRetry();
                return false;
            }
            startTimer();
            Log.d(TAG, "Waiting for Android to select the Bluetooth communication device");
            return true;
        }
        // Start BT SCO channel and wait for ACTION_AUDIO_STATE_CHANGED.
        Log.d(TAG, "Starting Bluetooth SCO and waits for ACTION_AUDIO_STATE_CHANGED...");
        // The SCO connection establishment can take several seconds, hence we cannot rely on the
        // connection to be available when the method returns but instead register to receive the
        // intent ACTION_SCO_AUDIO_STATE_UPDATED and wait for the state to be SCO_AUDIO_STATE_CONNECTED.
        bluetoothState = State.SCO_CONNECTING;
        audioManager.startBluetoothSco();
        audioManager.setBluetoothScoOn(true);
        scoConnectionAttempts++;
        startTimer();
        Log.d(TAG, "startScoAudio done: BT state=" + bluetoothState + ", "
                + "SCO is on: " + isScoOn());
        return true;
    }

    /**
     * Stops Bluetooth SCO connection with remote device.
     */
    public void stopScoAudio() {
        ThreadUtils.checkIsOnMainThread();
        Log.d(TAG, "stopScoAudio: BT state=" + bluetoothState + ", "
                + "SCO is on: " + isScoOn());
        if (bluetoothState != State.SCO_CONNECTING && bluetoothState != State.SCO_CONNECTED) {
            return;
        }
        cancelTimer();
        cancelBluetoothRouteRetry();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && modernBluetoothRoute != null) {
            bluetoothState = State.SCO_DISCONNECTING;
            startTimer();
            modernBluetoothRoute.clearCommunicationDeviceRequest();
            Log.d(TAG, "Bluetooth communication-device request cleared");
            return;
        }
        bluetoothState = State.SCO_DISCONNECTING;
        startTimer();
        audioManager.stopBluetoothSco();
        audioManager.setBluetoothScoOn(false);
        Log.d(TAG, "stopScoAudio done: BT state=" + bluetoothState + ", "
                + "SCO is on: " + isScoOn());
    }

    /**
     * Use the BluetoothHeadset proxy object (controls the Bluetooth Headset
     * Service via IPC) to update the list of connected devices for the HEADSET
     * profile. The internal state will change to HEADSET_UNAVAILABLE or to
     * HEADSET_AVAILABLE and |bluetoothDevice| will be mapped to the connected
     * device if available.
     */
    @SuppressLint("MissingPermission")
    public void updateDevice() {
        if (bluetoothState == State.UNINITIALIZED) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && modernBluetoothRoute != null) {
            boolean bluetoothAvailable = modernBluetoothRoute.hasBluetoothDevice();
            if (bluetoothState == State.SCO_CONNECTING) {
                boolean requestedBluetoothDeviceAvailable = modernBluetoothRoute.hasRequestedBluetoothDevice();
                if (!requestedBluetoothDeviceAvailable) {
                    cancelTimer();
                    modernBluetoothRoute.discardPendingBluetoothRequest();
                    if (shouldResetModernBluetoothAttempts(
                            bluetoothState,
                            requestedBluetoothDeviceAvailable,
                            bluetoothAvailable)) {
                        scoConnectionAttempts = 0;
                    }
                    bluetoothState = stateAfterModernRouteClear(bluetoothAvailable);
                }
            } else if (bluetoothState == State.SCO_DISCONNECTING) {
                if (!bluetoothAvailable) {
                    cancelTimer();
                    modernBluetoothRoute.clearConfirmedBluetoothDevice();
                    bluetoothState = State.HEADSET_UNAVAILABLE;
                }
            } else if (bluetoothState == State.SCO_CONNECTED) {
                if (!modernBluetoothRoute.hasConfirmedBluetoothDevice()) {
                    modernBluetoothRoute.clearConfirmedBluetoothDevice();
                    bluetoothState = stateAfterModernRouteClear(bluetoothAvailable);
                }
            } else if (modernBluetoothRoute.confirmInitialBluetoothRoute()) {
                // Bluetooth may already be the active system route when the call starts.
                bluetoothState = State.SCO_CONNECTED;
                scoConnectionAttempts = 0;
            } else if (bluetoothAvailable) {
                bluetoothState = State.HEADSET_AVAILABLE;
            } else {
                bluetoothState = State.HEADSET_UNAVAILABLE;
            }
            Log.d(TAG, "Modern Bluetooth route state=" + bluetoothState);
            return;
        }
        if (hasNoBluetoothPermission()) {
            return;
        }
        if (bluetoothHeadset == null) {
            return;
        }
        Log.d(TAG, "updateDevice");
        // Get connected devices for the headset profile. Returns the set of
        // devices which are in state STATE_CONNECTED. The BluetoothDevice class
        // is just a thin wrapper for a Bluetooth hardware address.
        List<BluetoothDevice> devices = bluetoothHeadset.getConnectedDevices();
        if (devices.isEmpty()) {
            bluetoothDevice = null;
            bluetoothState = State.HEADSET_UNAVAILABLE;
            headsetProfileExpected = false;
            Log.d(TAG, "No connected bluetooth headset");
        } else {
            // Always use first device in list. Android only supports one device.
            bluetoothDevice = devices.get(0);
            bluetoothState = State.HEADSET_AVAILABLE;
            headsetProfileExpected = true;
            Log.d(TAG, "Connected bluetooth headset: "
                    + "name=" + bluetoothDevice.getName() + ", "
                    + "state=" + stateToString(bluetoothHeadset.getConnectionState(bluetoothDevice))
                    + ", SCO audio=" + bluetoothHeadset.isAudioConnected(bluetoothDevice));
        }
        Log.d(TAG, "updateDevice done: BT state=" + bluetoothState);
    }

    /**
     * Re-arms Bluetooth after an explicit user selection. An already accepted request is kept;
     * clearing it on a second tap can race with an already queued framework callback.
     */
    public boolean requestBluetoothAudioSelection() {
        ThreadUtils.checkIsOnMainThread();
        resetScoConnectionAttempts();
        if (!started) {
            start();
        }
        if (!started) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && modernBluetoothRoute != null) {
            if (!modernBluetoothRoute.hasBluetoothDevice()) {
                cancelTimer();
                bluetoothState = State.HEADSET_UNAVAILABLE;
                return false;
            }
            // Do not cancel an accepted request on a manual tap. Some firmware has already queued
            // its success callback; clearing here creates a stale callback that can falsely report
            // Bluetooth as active after Android has moved back to the earpiece.
            if (isBluetoothTransitionInProgress(bluetoothState)) {
                return true;
            }
            if (bluetoothState == State.SCO_CONNECTED) {
                return true;
            }
            bluetoothState = State.HEADSET_AVAILABLE;
            return true;
        }

        if (bluetoothState == State.SCO_CONNECTED) {
            return true;
        }
        if (isBluetoothTransitionInProgress(bluetoothState)) {
            // Do not restart SCO while its state is settling. A late CONNECTED broadcast could
            // otherwise be mistaken for the new manual request.
            return true;
        }
        updateDevice();
        return bluetoothState == State.HEADSET_AVAILABLE || headsetProfileExpected;
    }

    /**
     * Stubs for test mocks.
     */
    protected final AudioManager getAudioManager(Context context) {
        return (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    protected void registerReceiver(BroadcastReceiver receiver, IntentFilter filter) {
        ContextExtensionsKt.registerBroadcastReceiver(
            apprtcContext,
            receiver,
            filter,
            ReceiverFlag.Exported);
    }

    protected void unregisterReceiver(BroadcastReceiver receiver) {
        apprtcContext.unregisterReceiver(receiver);
    }

    protected boolean getBluetoothProfileProxy(
            Context context, BluetoothProfile.ServiceListener listener, int profile) {
        return bluetoothAdapter.getProfileProxy(context, listener, profile);
    }

    private boolean hasNoBluetoothPermission() {
        String permission;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permission = Manifest.permission.BLUETOOTH_CONNECT;
        } else {
            permission = Manifest.permission.BLUETOOTH;
        }

        boolean hasPermission =
            ActivityCompat.checkSelfPermission(apprtcContext, permission) == PackageManager.PERMISSION_GRANTED;
        if(!hasPermission) {
            Log.w(TAG, "Process (pid=" + Process.myPid() + ") lacks \"" + permission + "\" permission");
        }
        return !hasPermission;
    }

    /**
     * Logs the state of the local Bluetooth adapter.
     */
    @SuppressLint({"HardwareIds", "MissingPermission"})
    private void logBluetoothAdapterInfo(BluetoothAdapter localAdapter) {
        Log.d(TAG, "BluetoothAdapter: "
            + "enabled=" + localAdapter.isEnabled() + ", "
            + "state=" + stateToString(localAdapter.getState()) + ", "
            + "name=" + localAdapter.getName());
        // Log the set of BluetoothDevice objects that are bonded (paired) to the local adapter.
        Set<BluetoothDevice> pairedDevices = localAdapter.getBondedDevices();
        if (!pairedDevices.isEmpty()) {
            Log.d(TAG, "paired devices:");
            for (BluetoothDevice device : pairedDevices) {
                Log.d(TAG, " name=" + device.getName() + ", address=" + device.getAddress());
            }
        }
    }

    /**
     * Ensures that the audio manager updates its list of available audio devices.
     */
    private void updateAudioDeviceState() {
        ThreadUtils.checkIsOnMainThread();
        Log.d(TAG, "updateAudioDeviceState");
        webRtcAudioManager.updateAudioDeviceState();
    }

    /**
     * Starts timer which times out after BLUETOOTH_SCO_TIMEOUT_MS milliseconds.
     */
    private void startTimer() {
        ThreadUtils.checkIsOnMainThread();
        Log.d(TAG, "startTimer");
        handler.removeCallbacks(bluetoothTimeoutRunnable);
        handler.postDelayed(bluetoothTimeoutRunnable, BLUETOOTH_SCO_TIMEOUT_MS);
    }

    /**
     * Cancels any outstanding timer tasks.
     */
    private void cancelTimer() {
        ThreadUtils.checkIsOnMainThread();
        Log.d(TAG, "cancelTimer");
        handler.removeCallbacks(bluetoothTimeoutRunnable);
    }

    private void scheduleBluetoothRouteRetry() {
        ThreadUtils.checkIsOnMainThread();
        cancelBluetoothRouteRetry();
        if (started && scoConnectionAttempts < MAX_SCO_CONNECTION_ATTEMPTS) {
            bluetoothRouteRetryScheduled = true;
            handler.postDelayed(bluetoothRouteRetryRunnable, BLUETOOTH_ROUTE_RETRY_DELAY_MS);
        }
    }

    private void cancelBluetoothRouteRetry() {
        ThreadUtils.checkIsOnMainThread();
        handler.removeCallbacks(bluetoothRouteRetryRunnable);
        bluetoothRouteRetryScheduled = false;
    }

    private void retryBluetoothRoute() {
        ThreadUtils.checkIsOnMainThread();
        bluetoothRouteRetryScheduled = false;
        if (!started || bluetoothState != State.HEADSET_AVAILABLE) {
            return;
        }
        Log.d(TAG, "Retrying Bluetooth communication-device selection");
        updateAudioDeviceState();
    }

    /**
     * Called when start of the BT SCO channel takes too long time. Usually
     * happens when the BT device has been turned on during an ongoing call.
     */
    @SuppressLint("MissingPermission")
    private void bluetoothTimeout() {
        ThreadUtils.checkIsOnMainThread();
        if (bluetoothState == State.UNINITIALIZED ||
            (modernBluetoothRoute == null && bluetoothHeadset == null)) {
            return;
        }
        Log.d(TAG, "bluetoothTimeout: BT state=" + bluetoothState + ", "
                + "attempts: " + scoConnectionAttempts + ", "
                + "SCO is on: " + isScoOn());
        if (bluetoothState == State.SCO_DISCONNECTING) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && modernBluetoothRoute != null) {
                // Never resurrect a request which was explicitly cleared. The getter can still
                // expose the old Bluetooth route while clearCommunicationDevice() is settling.
                modernBluetoothRoute.reconcileRouteClearFromGetter();
                bluetoothState = stateAfterModernRouteClear(modernBluetoothRoute.hasBluetoothDevice());
            } else {
                if (hasNoBluetoothPermission()) {
                    return;
                }
                updateDevice();
            }
            updateAudioDeviceState();
            return;
        }
        if (bluetoothState != State.SCO_CONNECTING) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && modernBluetoothRoute != null) {
            if (modernBluetoothRoute.isRequestedBluetoothSelected()) {
                modernBluetoothRoute.confirmRequestedBluetoothRoute();
                bluetoothState = State.SCO_CONNECTED;
                scoConnectionAttempts = 0;
            } else {
                Log.w(TAG, "Bluetooth communication-device selection timed out");
                bluetoothState = State.SCO_DISCONNECTING;
                startTimer();
                modernBluetoothRoute.clearCommunicationDeviceRequest();
            }
            updateAudioDeviceState();
            return;
        }
        if (hasNoBluetoothPermission()) {
            return;
        }
        // Bluetooth SCO should be connecting; check the latest result.
        boolean scoConnected = false;
        List<BluetoothDevice> devices = bluetoothHeadset.getConnectedDevices();
        if (devices.size() > 0) {
            bluetoothDevice = devices.get(0);
            if (bluetoothHeadset.isAudioConnected(bluetoothDevice)) {
                Log.d(TAG, "SCO connected with " + bluetoothDevice.getName());
                scoConnected = true;
            } else {
                Log.d(TAG, "SCO is not connected with " + bluetoothDevice.getName());
            }
        }
        if (scoConnected) {
            // We thought BT had timed out, but it's actually on; updating state.
            bluetoothState = State.SCO_CONNECTED;
            scoConnectionAttempts = 0;
        } else {
            // Give up and "cancel" our request by calling stopBluetoothSco().
            Log.w(TAG, "BT failed to connect after timeout");
            stopScoAudio();
        }
        updateAudioDeviceState();
        Log.d(TAG, "bluetoothTimeout done: BT state=" + bluetoothState);
    }

    /**
     * Checks whether audio uses Bluetooth SCO.
     */
    private boolean isScoOn() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && modernBluetoothRoute != null) {
            return modernBluetoothRoute.isBluetoothSelected();
        }
        return audioManager.isBluetoothScoOn();
    }

    /**
     * Converts BluetoothAdapter states into local string representations.
     */
    private String stateToString(int state) {
        switch (state) {
            case BluetoothAdapter.STATE_DISCONNECTED:
                return "DISCONNECTED";
            case BluetoothAdapter.STATE_CONNECTED:
                return "CONNECTED";
            case BluetoothAdapter.STATE_CONNECTING:
                return "CONNECTING";
            case BluetoothAdapter.STATE_DISCONNECTING:
                return "DISCONNECTING";
            case BluetoothAdapter.STATE_OFF:
                return "OFF";
            case BluetoothAdapter.STATE_ON:
                return "ON";
            case BluetoothAdapter.STATE_TURNING_OFF:
                // Indicates the local Bluetooth adapter is turning off. Local clients should immediately
                // attempt graceful disconnection of any remote links.
                return "TURNING_OFF";
            case BluetoothAdapter.STATE_TURNING_ON:
                // Indicates the local Bluetooth adapter is turning on. However local clients should wait
                // for STATE_ON before attempting to use the adapter.
                return "TURNING_ON";
            default:
                return "INVALID";
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private class ModernBluetoothRoute {
        private final Set<Integer> knownBluetoothDeviceIds = new HashSet<>();
        private static final int NO_DEVICE_ID = -1;
        private boolean routeSelectionControlled;
        private boolean routeClearPending;
        private boolean routeRequestPending;
        private int requestedBluetoothDeviceId = NO_DEVICE_ID;
        private int confirmedBluetoothDeviceId = NO_DEVICE_ID;
        private final AudioManager.OnCommunicationDeviceChangedListener communicationDeviceChangedListener =
            this::onCommunicationDeviceChanged;
        private final AudioDeviceCallback audioDeviceCallback = new AudioDeviceCallback() {
            @Override
            public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
                for (AudioDeviceInfo device : addedDevices) {
                    if (isBluetoothCommunicationDeviceType(device.getType())
                            && knownBluetoothDeviceIds.add(device.getId())) {
                        scoConnectionAttempts = 0;
                    }
                }
                onDeviceStateChanged();
            }

            @Override
            public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
                for (AudioDeviceInfo device : removedDevices) {
                    knownBluetoothDeviceIds.remove(device.getId());
                }
                onDeviceStateChanged();
            }
        };

        void start() {
            rememberCurrentBluetoothDevices();
            audioManager.addOnCommunicationDeviceChangedListener(
                apprtcContext.getMainExecutor(),
                communicationDeviceChangedListener
            );
            audioManager.registerAudioDeviceCallback(audioDeviceCallback, handler);
        }

        void stop() {
            audioManager.removeOnCommunicationDeviceChangedListener(communicationDeviceChangedListener);
            audioManager.unregisterAudioDeviceCallback(audioDeviceCallback);
            clearCommunicationDeviceRequest();
        }

        boolean selectBluetoothDevice() {
            return selectBluetoothDevice(findBluetoothDevice());
        }

        boolean reselectConfirmedBluetoothDevice() {
            AudioDeviceInfo bluetoothDeviceInfo = confirmedBluetoothDeviceId == NO_DEVICE_ID
                ? null
                : findBluetoothDevice(confirmedBluetoothDeviceId);
            confirmedBluetoothDeviceId = NO_DEVICE_ID;
            return selectBluetoothDevice(bluetoothDeviceInfo);
        }

        private boolean selectBluetoothDevice(AudioDeviceInfo bluetoothDeviceInfo) {
            if (bluetoothDeviceInfo == null) {
                return false;
            }
            routeSelectionControlled = true;
            routeClearPending = false;
            routeRequestPending = true;
            requestedBluetoothDeviceId = bluetoothDeviceInfo.getId();
            try {
                boolean accepted = audioManager.setCommunicationDevice(bluetoothDeviceInfo);
                if (!accepted) {
                    routeRequestPending = false;
                    requestedBluetoothDeviceId = NO_DEVICE_ID;
                }
                return accepted;
            } catch (SecurityException | IllegalArgumentException exception) {
                routeRequestPending = false;
                requestedBluetoothDeviceId = NO_DEVICE_ID;
                Log.e(TAG, "Bluetooth device disappeared while it was being selected", exception);
                return false;
            }
        }

        void clearCommunicationDeviceRequest() {
            routeSelectionControlled = true;
            routeClearPending = true;
            routeRequestPending = false;
            requestedBluetoothDeviceId = NO_DEVICE_ID;
            confirmedBluetoothDeviceId = NO_DEVICE_ID;
            try {
                audioManager.clearCommunicationDevice();
            } catch (SecurityException exception) {
                Log.e(TAG, "Bluetooth permission was revoked while clearing the communication device", exception);
            }
        }

        void confirmNonBluetoothRouteSelection() {
            routeSelectionControlled = true;
            routeClearPending = false;
            routeRequestPending = false;
            requestedBluetoothDeviceId = NO_DEVICE_ID;
            confirmedBluetoothDeviceId = NO_DEVICE_ID;
        }

        boolean hasBluetoothDevice() {
            return findBluetoothDevice() != null;
        }

        boolean isBluetoothSelected() {
            try {
                AudioDeviceInfo device = audioManager.getCommunicationDevice();
                return device != null && isBluetoothCommunicationDeviceType(device.getType());
            } catch (SecurityException exception) {
                Log.e(TAG, "Bluetooth permission was revoked while reading the communication device", exception);
                return false;
            }
        }

        boolean canTrustInitialRouteSnapshot() {
            return !routeSelectionControlled;
        }

        boolean confirmInitialBluetoothRoute() {
            if (!canTrustInitialRouteSnapshot()) {
                return false;
            }
            try {
                AudioDeviceInfo device = audioManager.getCommunicationDevice();
                if (device != null
                        && isBluetoothCommunicationDeviceType(device.getType())
                        && isBluetoothDeviceAvailable(device.getId())) {
                    confirmBluetoothRoute(device);
                    return true;
                }
            } catch (SecurityException exception) {
                Log.e(TAG, "Unable to confirm the initial Bluetooth communication device", exception);
            }
            return false;
        }

        boolean isRequestedBluetoothSelected() {
            if (!routeRequestPending) {
                return false;
            }
            try {
                AudioDeviceInfo device = audioManager.getCommunicationDevice();
                return device != null
                    && device.getId() == requestedBluetoothDeviceId
                    && isBluetoothCommunicationDeviceType(device.getType())
                    && isBluetoothDeviceAvailable(device.getId());
            } catch (SecurityException exception) {
                Log.e(TAG, "Unable to read the requested Bluetooth communication device", exception);
                return false;
            }
        }

        boolean matchesPendingBluetoothRequest(AudioDeviceInfo device) {
            return routeRequestPending
                && device != null
                && device.getId() == requestedBluetoothDeviceId
                && isBluetoothCommunicationDeviceType(device.getType())
                && isBluetoothDeviceAvailable(device.getId());
        }

        void confirmRequestedBluetoothRoute() {
            confirmedBluetoothDeviceId = requestedBluetoothDeviceId;
            routeRequestPending = false;
            requestedBluetoothDeviceId = NO_DEVICE_ID;
        }

        void confirmBluetoothRoute(AudioDeviceInfo device) {
            confirmedBluetoothDeviceId = device.getId();
            routeRequestPending = false;
            requestedBluetoothDeviceId = NO_DEVICE_ID;
        }

        boolean hasRequestedBluetoothDevice() {
            return routeRequestPending && isBluetoothDeviceAvailable(requestedBluetoothDeviceId);
        }

        boolean hasConfirmedBluetoothDevice() {
            return confirmedBluetoothDeviceId != NO_DEVICE_ID
                && isBluetoothDeviceAvailable(confirmedBluetoothDeviceId);
        }

        void discardPendingBluetoothRequest() {
            routeRequestPending = false;
            requestedBluetoothDeviceId = NO_DEVICE_ID;
        }

        void clearConfirmedBluetoothDevice() {
            confirmedBluetoothDeviceId = NO_DEVICE_ID;
        }

        void reconcileRouteClearFromGetter() {
            if (!routeClearPending) {
                return;
            }
            boolean currentRouteKnown = false;
            boolean bluetoothSelected = false;
            try {
                AudioDeviceInfo device = audioManager.getCommunicationDevice();
                currentRouteKnown = true;
                bluetoothSelected = device != null && isBluetoothCommunicationDeviceType(device.getType());
            } catch (SecurityException exception) {
                Log.e(TAG, "Unable to reconcile the cleared Bluetooth communication device", exception);
            }
            routeClearPending = shouldKeepModernRouteClearPending(
                routeClearPending,
                currentRouteKnown,
                bluetoothSelected
            );
        }

        private AudioDeviceInfo findBluetoothDevice() {
            return findBluetoothDevice(NO_DEVICE_ID);
        }

        private AudioDeviceInfo findBluetoothDevice(int exactDeviceId) {
            try {
                AudioDeviceInfo selectedDevice = null;
                int selectedPriority = -1;
                for (AudioDeviceInfo device : audioManager.getAvailableCommunicationDevices()) {
                    int priority = bluetoothCommunicationDevicePriority(device.getType(), Build.VERSION.SDK_INT);
                    if (priority < 0) {
                        continue;
                    }
                    if (exactDeviceId != NO_DEVICE_ID) {
                        if (device.getId() == exactDeviceId) {
                            return device;
                        }
                    } else if (priority > selectedPriority) {
                        selectedDevice = device;
                        selectedPriority = priority;
                    }
                }
                return selectedDevice;
            } catch (SecurityException exception) {
                Log.e(TAG, "Unable to enumerate Bluetooth communication devices", exception);
            }
            return null;
        }

        private boolean isBluetoothDeviceAvailable(int deviceId) {
            try {
                for (AudioDeviceInfo device : audioManager.getAvailableCommunicationDevices()) {
                    if (device.getId() == deviceId
                            && isBluetoothCommunicationDeviceType(device.getType())) {
                        return true;
                    }
                }
            } catch (SecurityException exception) {
                Log.e(TAG, "Unable to verify the Bluetooth communication device", exception);
            }
            return false;
        }

        private void rememberCurrentBluetoothDevices() {
            try {
                for (AudioDeviceInfo device : audioManager.getAvailableCommunicationDevices()) {
                    if (isBluetoothCommunicationDeviceType(device.getType())) {
                        knownBluetoothDeviceIds.add(device.getId());
                    }
                }
            } catch (SecurityException exception) {
                Log.e(TAG, "Bluetooth permission was revoked while remembering communication devices", exception);
            }
        }

        private void onDeviceStateChanged() {
            if (bluetoothState == State.UNINITIALIZED) {
                return;
            }
            State previousState = bluetoothState;
            boolean available = hasBluetoothDevice();
            if (shouldKeepModernBluetoothState(
                    previousState,
                    hasRequestedBluetoothDevice(),
                    hasConfirmedBluetoothDevice(),
                    available)) {
                return;
            }
            updateDevice();
            if (previousState == State.SCO_DISCONNECTING) {
                reconcileRouteClearFromGetter();
            }
            if (bluetoothState == State.SCO_CONNECTED || previousState == State.SCO_DISCONNECTING) {
                cancelTimer();
            }
            if (bluetoothState == State.SCO_CONNECTED) {
                scoConnectionAttempts = 0;
                cancelBluetoothRouteRetry();
            }
            updateAudioDeviceState();
        }

        private void onCommunicationDeviceChanged(AudioDeviceInfo device) {
            if (bluetoothState == State.UNINITIALIZED) {
                return;
            }
            boolean bluetoothSelected = device != null && isBluetoothCommunicationDeviceType(device.getType());
            if (bluetoothSelected) {
                boolean pendingRequestMatches = matchesPendingBluetoothRequest(device);
                if (!shouldAcceptModernBluetoothCallback(
                        bluetoothState,
                        routeSelectionControlled,
                        routeClearPending,
                        pendingRequestMatches)) {
                    Log.d(TAG, "Ignoring a Bluetooth callback for an inactive or cleared route request");
                    return;
                }
                if (!isBluetoothDeviceAvailable(device.getId())) {
                    Log.w(TAG, "Ignoring a Bluetooth callback for an endpoint which is no longer available");
                    return;
                }
                // The callback argument is authoritative. Re-reading getCommunicationDevice()
                // here returns the old earpiece for a short interval on some Samsung devices.
                cancelTimer();
                cancelBluetoothRouteRetry();
                confirmBluetoothRoute(device);
                bluetoothState = State.SCO_CONNECTED;
                scoConnectionAttempts = 0;
                updateAudioDeviceState();
                return;
            }

            routeClearPending = false;

            if (bluetoothState == State.SCO_CONNECTED || bluetoothState == State.SCO_DISCONNECTING) {
                cancelTimer();
                clearConfirmedBluetoothDevice();
                bluetoothState = stateAfterModernRouteClear(hasBluetoothDevice());
                updateAudioDeviceState();
                return;
            }

            // A queued callback for the previous earpiece route can arrive after Android accepted
            // a Bluetooth request. Keep CONNECTING until Bluetooth is confirmed or the request
            // times out; still let the audio manager report its unchanged route state.
            updateAudioDeviceState();
        }
    }

    public boolean started() {
        return started;
    }

    // Bluetooth connection state.
    public enum State {
        // Bluetooth is not available; no adapter or Bluetooth is off.
        UNINITIALIZED,
        // Bluetooth error happened when trying to start Bluetooth.
        ERROR,
        // Bluetooth proxy object for the Headset profile exists, but no connected headset devices,
        // SCO is not started or disconnected.
        HEADSET_UNAVAILABLE,
        // Bluetooth proxy object for the Headset profile connected, connected Bluetooth headset
        // present, but SCO is not started or disconnected.
        HEADSET_AVAILABLE,
        // Bluetooth audio SCO connection with remote device is closing.
        SCO_DISCONNECTING,
        // Bluetooth audio SCO connection with remote device is initiated.
        SCO_CONNECTING,
        // Bluetooth audio SCO connection with remote device is established.
        SCO_CONNECTED
    }

    /**
     * Implementation of an interface that notifies BluetoothProfile IPC clients when they have been
     * connected to or disconnected from the service.
     */
    private class BluetoothServiceListener implements BluetoothProfile.ServiceListener {
        @Override
        // Called to notify the client when the proxy object has been connected to the service.
        // Once we have the profile proxy object, we can use it to monitor the state of the
        // connection and perform other operations that are relevant to the headset profile.
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            if (profile != BluetoothProfile.HEADSET || bluetoothState == State.UNINITIALIZED) {
                return;
            }
            Log.d(TAG, "BluetoothServiceListener.onServiceConnected: BT state=" + bluetoothState);
            // Android only supports one connected Bluetooth Headset at a time.
            bluetoothHeadset = (BluetoothHeadset) proxy;
            updateAudioDeviceState();
            Log.d(TAG, "onServiceConnected done: BT state=" + bluetoothState);
        }

        /**
         * Notifies the client when the proxy object has been disconnected from the service.
         */
        @Override
        public void onServiceDisconnected(int profile) {
            if (profile != BluetoothProfile.HEADSET || bluetoothState == State.UNINITIALIZED) {
                return;
            }
            Log.d(TAG, "BluetoothServiceListener.onServiceDisconnected: BT state=" + bluetoothState);
            stopScoAudio();
            bluetoothHeadset = null;
            bluetoothDevice = null;
            bluetoothState = State.HEADSET_UNAVAILABLE;
            headsetProfileExpected = false;
            updateAudioDeviceState();
            Log.d(TAG, "onServiceDisconnected done: BT state=" + bluetoothState);
        }
    }

    // Intent broadcast receiver which handles changes in Bluetooth device availability.
    // Detects headset changes and Bluetooth SCO state changes.
    private class BluetoothHeadsetBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (bluetoothState == State.UNINITIALIZED) {
                return;
            }
            final String action = intent.getAction();
            // Change in connection state of the Headset profile. Note that the
            // change does not tell us anything about whether we're streaming
            // audio to BT over SCO. Typically received when user turns on a BT
            // headset while audio is active using another audio device.
            if (BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED.equals(action)) {
                final int state =
                        intent.getIntExtra(BluetoothHeadset.EXTRA_STATE, BluetoothHeadset.STATE_DISCONNECTED);
                Log.d(TAG, "BluetoothHeadsetBroadcastReceiver.onReceive: "
                        + "a=ACTION_CONNECTION_STATE_CHANGED, "
                        + "s=" + stateToString(state) + ", "
                        + "sb=" + isInitialStickyBroadcast() + ", "
                        + "BT state: " + bluetoothState);
                if (state == BluetoothHeadset.STATE_CONNECTED) {
                    headsetProfileExpected = true;
                    scoConnectionAttempts = 0;
                    updateAudioDeviceState();
                } else if (state == BluetoothHeadset.STATE_CONNECTING) {
                    headsetProfileExpected = true;
                    Log.d(TAG, "+++ Bluetooth is connecting...");
                    // No action needed.
                } else if (state == BluetoothHeadset.STATE_DISCONNECTING) {
                    Log.d(TAG, "+++ Bluetooth is disconnecting...");
                    // No action needed.
                } else if (state == BluetoothHeadset.STATE_DISCONNECTED) {
                    // Bluetooth is probably powered off during the call.
                    headsetProfileExpected = false;
                    stopScoAudio();
                    updateAudioDeviceState();
                }
                // Change in the audio (SCO) connection state of the Headset profile.
                // Typically received after call to startScoAudio() has finalized.
            } else if (BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED.equals(action)) {
                final int state = intent.getIntExtra(
                        BluetoothHeadset.EXTRA_STATE, BluetoothHeadset.STATE_AUDIO_DISCONNECTED);
                Log.d(TAG, "BluetoothHeadsetBroadcastReceiver.onReceive: "
                        + "a=ACTION_AUDIO_STATE_CHANGED, "
                        + "s=" + stateToString(state) + ", "
                        + "sb=" + isInitialStickyBroadcast() + ", "
                        + "BT state: " + bluetoothState);
                if (state == BluetoothHeadset.STATE_AUDIO_CONNECTED) {
                    if (shouldAcceptLegacyScoConnected(bluetoothState)) {
                        cancelTimer();
                        Log.d(TAG, "+++ Bluetooth audio SCO is now connected");
                        bluetoothState = State.SCO_CONNECTED;
                        scoConnectionAttempts = 0;
                        cancelBluetoothRouteRetry();
                        updateAudioDeviceState();
                    } else {
                        Log.d(TAG, "Ignoring SCO connected callback in state " + bluetoothState);
                    }
                } else if (state == BluetoothHeadset.STATE_AUDIO_CONNECTING) {
                    Log.d(TAG, "+++ Bluetooth audio SCO is now connecting...");
                } else if (state == BluetoothHeadset.STATE_AUDIO_DISCONNECTED) {
                    Log.d(TAG, "+++ Bluetooth audio SCO is now disconnected");
                    if (isInitialStickyBroadcast()) {
                        Log.d(TAG, "Ignore STATE_AUDIO_DISCONNECTED initial sticky broadcast.");
                        return;
                    }
                    cancelTimer();
                    if (bluetoothState == State.SCO_CONNECTED
                            || bluetoothState == State.SCO_CONNECTING
                            || bluetoothState == State.SCO_DISCONNECTING) {
                        bluetoothState = State.HEADSET_AVAILABLE;
                        updateDevice();
                    }
                    updateAudioDeviceState();
                }
            }
            Log.d(TAG, "onReceive done: BT state=" + bluetoothState);
        }
    }
}
