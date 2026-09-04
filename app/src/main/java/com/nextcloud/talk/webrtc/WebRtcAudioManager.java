/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Tim Krüger <t@timkrueger.me>
 * SPDX-FileCopyrightText: 2017 Mario Danic <mario@lovelyhq.com>
 * SPDX-FileCopyrightText: 2014 The WebRTC Project Authors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Original code:
 *
 * Copyright 2014 The WebRTC Project Authors. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license
 * that can be found in the LICENSE file in the root of the source
 * tree. An additional intellectual property rights grant can be found
 * in the file PATENTS.  All contributing project authors may
 * be found in the AUTHORS file in the root of the source tree.
 */
package com.nextcloud.talk.webrtc;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.util.Log;

import com.nextcloud.talk.events.ProximitySensorEvent;
import com.nextcloud.talk.utils.ContextExtensionsKt;
import com.nextcloud.talk.utils.ReceiverFlag;
import com.nextcloud.talk.utils.power.PowerManagerUtils;

import org.greenrobot.eventbus.EventBus;
import org.webrtc.ThreadUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

public class WebRtcAudioManager {
    private static final String TAG = WebRtcAudioManager.class.getSimpleName();
    private final Context context;
    private final WebRtcBluetoothManager bluetoothManager;
    private final boolean useProximitySensor;
    private final AudioManager audioManager;
    private AudioManagerListener audioManagerListener;
    private AudioManagerState amState;
    private int savedAudioMode = AudioManager.MODE_INVALID;
    private boolean savedIsSpeakerPhoneOn = false;
    private boolean savedIsMicrophoneMute = false;
    private boolean hasWiredHeadset = false;
    private boolean bluetoothPreferredForCall = false;

    private AudioDevice userSelectedAudioDevice = AudioDevice.NONE;
    private AudioDevice currentAudioDevice = AudioDevice.NONE;
    private AudioDevice defaultAudioDevice = AudioDevice.NONE;
    private AudioDevice lastReportedAudioDeviceForUi = AudioDevice.NONE;

    private ProximitySensor proximitySensor = null;

    private Set<AudioDevice> audioDevices = new HashSet<>();

    private Set<AudioDevice> internalAudioDevices = new HashSet<>();

    private final BroadcastReceiver wiredHeadsetReceiver;
    private final AudioDeviceCallback wiredAudioDeviceCallback;
    private boolean wiredRouteRefreshPending;
    private AudioManager.OnAudioFocusChangeListener audioFocusChangeListener;
    private AudioFocusRequest audioFocusRequest;
    private final AudioFocusState audioFocusState = new AudioFocusState();

    private final PowerManagerUtils powerManagerUtils;

    private WebRtcAudioManager(Context context, boolean useProximitySensor) {
        Log.d(TAG, "ctor");
        ThreadUtils.checkIsOnMainThread();
        this.context = context;
        audioManager = ((AudioManager) context.getSystemService(Context.AUDIO_SERVICE));
        bluetoothManager = WebRtcBluetoothManager.create(context, this);
        wiredHeadsetReceiver = new WiredHeadsetReceiver();
        wiredAudioDeviceCallback = new AudioDeviceCallback() {
            @Override
            public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
                onWiredAudioDevicesChanged(addedDevices);
            }

            @Override
            public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
                onWiredAudioDevicesChanged(removedDevices);
            }
        };
        amState = AudioManagerState.UNINITIALIZED;

        powerManagerUtils = new PowerManagerUtils();
        powerManagerUtils.updatePhoneState(PowerManagerUtils.PhoneState.WITH_PROXIMITY_SENSOR_LOCK);

        this.useProximitySensor = useProximitySensor;

        // Create and initialize the proximity sensor.
        // Tablet devices (e.g. Nexus 7) does not support proximity sensors.
        // Note that, the sensor will not be active until start() has been called.
        proximitySensor = ProximitySensor.create(context, new Runnable() {
            // This method will be called each time a state change is detected.
            // Example: user holds his hand over the device (closer than ~5 cm),
            // or removes his hand from the device.
            public void run() {
                onProximitySensorChangedState();
            }
        });
    }

    /**
     * Construction.
     */
    public static WebRtcAudioManager create(Context context, boolean useProximitySensor) {
       return new WebRtcAudioManager(context, useProximitySensor);
    }

    public void startBluetoothManager() {
        // Initialize and start Bluetooth if a BT device is available or initiate
        // detection of new (enabled) BT devices.
        bluetoothManager.start();
    }

    /**
     * This method is called when the proximity sensor reports a state change, e.g. from "NEAR to FAR" or from "FAR to
     * NEAR".
     */
    private void onProximitySensorChangedState() {
        if (!useProximitySensor) {
            return;
        }

        if (userSelectedAudioDevice == AudioDevice.SPEAKER_PHONE
            && audioDevices.contains(AudioDevice.EARPIECE)
            && audioDevices.contains(AudioDevice.SPEAKER_PHONE)) {

            if (proximitySensor.sensorReportsNearState()) {
                setAudioDeviceInternal(AudioDevice.EARPIECE);
                Log.d(TAG, "switched to EARPIECE because userSelectedAudioDevice was SPEAKER_PHONE and proximity=near");

                EventBus.getDefault().post(new ProximitySensorEvent(ProximitySensorEvent.ProximitySensorEventType.SENSOR_NEAR));

            } else {
                setAudioDeviceInternal(WebRtcAudioManager.AudioDevice.SPEAKER_PHONE);
                Log.d(TAG, "switched to SPEAKER_PHONE because userSelectedAudioDevice was SPEAKER_PHONE and proximity=far");

                EventBus.getDefault().post(new ProximitySensorEvent(ProximitySensorEvent.ProximitySensorEventType.SENSOR_FAR));
            }
        }
    }

    @SuppressLint("WrongConstant")
    public void start(AudioManagerListener audioManagerListener) {
        Log.d(TAG, "start");
        ThreadUtils.checkIsOnMainThread();
        if (amState == AudioManagerState.RUNNING) {
            Log.e(TAG, "AudioManager is already active");
            return;
        }
        // TODO(henrika): perhaps call new method called preInitAudio() here if UNINITIALIZED.

        Log.d(TAG, "AudioManager starts...");
        this.audioManagerListener = audioManagerListener;
        amState = AudioManagerState.RUNNING;

        // Store current audio state so we can restore it when stop() is called.
        savedAudioMode = audioManager.getMode();
        savedIsSpeakerPhoneOn = audioManager.isSpeakerphoneOn();
        savedIsMicrophoneMute = audioManager.isMicrophoneMute();
        hasWiredHeadset = hasWiredHeadset();

        audioFocusChangeListener = this::onAudioFocusChange;

        // Request audio focus for a long-running call (delivered on the main thread).
        audioFocusRequest = buildCallAudioFocusRequest(audioFocusChangeListener);
        int result = audioManager.requestAudioFocus(audioFocusRequest);
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.d(TAG, "Audio focus request granted for VOICE_CALL streams");
        } else {
            Log.e(TAG, "Audio focus request failed");
        }

        // Start by setting MODE_IN_COMMUNICATION as default audio mode. It is
        // required to be in this mode when playout and/or recording starts for
        // best possible VoIP performance.
        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);

        // Always disable microphone mute during a WebRTC call.
        setMicrophoneMute(false);

        // Set initial device states.
        userSelectedAudioDevice = AudioDevice.NONE;
        currentAudioDevice = AudioDevice.NONE;
        defaultAudioDevice = AudioDevice.NONE;
        bluetoothPreferredForCall = false;
        audioFocusState.reset();
        lastReportedAudioDeviceForUi = AudioDevice.NONE;
        audioDevices.clear();
        internalAudioDevices.clear();
        wiredRouteRefreshPending = false;

        audioManager.registerAudioDeviceCallback(wiredAudioDeviceCallback, null);
        hasWiredHeadset = hasWiredHeadset();
        startBluetoothManager();

        // Do initial selection of audio device. This setting can later be changed
        // either by adding/removing a BT or wired headset or by covering/uncovering
        // the proximity sensor.
        updateAudioDeviceState();

        proximitySensor.start();
        // Register receiver for broadcast intents related to adding/removing a
        // wired headset.
        registerReceiver(wiredHeadsetReceiver, new IntentFilter(Intent.ACTION_HEADSET_PLUG));
        Log.d(TAG, "AudioManager started");
    }

    /**
     * Handles audio focus changes (called on the main thread). Re-asserts the communication mode and audio route
     * when focus returns after a transient loss, see {@link AudioFocusState}.
     */
    void onAudioFocusChange(int focusChange) {
        if (audioFocusState.handle(focusChange) && amState == AudioManagerState.RUNNING) {
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            bluetoothManager.reassertBluetoothAudioAfterFocusGain(bluetoothPreferredForCall, hasWiredHeadset);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                && currentAudioDevice != AudioDevice.NONE
                && currentAudioDevice != AudioDevice.BLUETOOTH) {
                setAudioDeviceInternal(currentAudioDevice);
            }
            updateAudioDeviceState();
        }
        Log.d(TAG, "onAudioFocusChange: " + focusChange);
    }

    static AudioFocusRequest buildCallAudioFocusRequest(AudioManager.OnAudioFocusChangeListener listener) {
        return new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build())
            .setAcceptsDelayedFocusGain(true)
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener(listener)
            .build();
    }

    /**
     * Tracks audio focus losses during a call.
     *
     * A transient focus holder such as the telephony stack also switches the global audio mode and restores its own
     * saved mode on release, clobbering MODE_IN_COMMUNICATION. "handle" reports whether the communication mode must
     * be re-asserted for a focus change, so the call does not continue without hardware echo cancellation and proper
     * VoIP routing after an interruption.
     */
    static class AudioFocusState {
        private boolean transientLoss = false;

        boolean handle(int focusChange) {
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    transientLoss = true;
                    return false;
                case AudioManager.AUDIOFOCUS_GAIN:
                    boolean restore = transientLoss;
                    transientLoss = false;
                    return restore;
                default:
                    transientLoss = false;
                    return false;
            }
        }

        boolean hasTransientLoss() {
            return transientLoss;
        }

        void reset() {
            transientLoss = false;
        }
    }

    @SuppressLint("WrongConstant")
    public void stop() {
        Log.d(TAG, "stop");
        ThreadUtils.checkIsOnMainThread();
        if (amState != AudioManagerState.RUNNING) {
            Log.e(TAG, "Trying to stop AudioManager in incorrect state: " + amState);
            return;
        }
        amState = AudioManagerState.UNINITIALIZED;

        unregisterReceiver(wiredHeadsetReceiver);
        audioManager.unregisterAudioDeviceCallback(wiredAudioDeviceCallback);

        if(bluetoothManager.started()) {
            bluetoothManager.stop();
        }

        // Restore previously stored audio states.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            setSpeakerphoneOn(savedIsSpeakerPhoneOn);
        }
        setMicrophoneMute(savedIsMicrophoneMute);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            clearCommunicationDevice();
        }
        audioManager.setMode(savedAudioMode);

        // Abandon audio focus. Gives the previous focus owner, if any, focus.
        if (audioFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest);
            audioFocusRequest = null;
        }
        audioFocusChangeListener = null;
        Log.d(TAG, "Abandoned audio focus for VOICE_CALL streams");

        if (proximitySensor != null) {
            proximitySensor.stop();
            proximitySensor = null;
        }

        powerManagerUtils.updatePhoneState(PowerManagerUtils.PhoneState.IDLE);

        audioManagerListener = null;
        Log.d(TAG, "AudioManager stopped");
    }

    ;

    /**
     * Changes selection of the currently active audio device.
     */
    private boolean setAudioDeviceInternal(AudioDevice audioDevice) {
        Log.d(TAG, "setAudioDeviceInternal(device=" + audioDevice + ")");

        if (audioDevice == AudioDevice.NONE) {
            currentAudioDevice = AudioDevice.NONE;
            return true;
        }

        if (!audioDevices.contains(audioDevice)) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!setCommunicationDevice(audioDevice)) {
                Log.e(TAG, "Unable to select communication device " + audioDevice);
                return false;
            }
        } else {
            switch (audioDevice) {
                case SPEAKER_PHONE:
                    setSpeakerphoneOn(true);
                    break;
                case EARPIECE:
                case WIRED_HEADSET:
                case BLUETOOTH:
                    setSpeakerphoneOn(false);
                    break;
                default:
                    Log.e(TAG, "Invalid audio device selection");
                    return false;
            }
        }

        currentAudioDevice = audioDevice;
        return true;
    }

    /**
     * Sets the default audio device to use if selection algo has no other option
     */
    public void setDefaultAudioDevice(AudioDevice device) {
        ThreadUtils.checkIsOnMainThread();
        if (!audioDevices.contains(device)) {
            Log.e(TAG, "Can not select default " + device + " from available " + audioDevices);
        }
        defaultAudioDevice = device;
        updateAudioDeviceState();
    }

    /**
     * Changes selection of the currently active audio device.
     *
     * @return {@code true} when the route is active or Android accepted/queued the request; {@code false} when no
     *         selection state was retained
     */
    public boolean selectAudioDevice(AudioDevice device) {
        ThreadUtils.checkIsOnMainThread();
        if (device == AudioDevice.BLUETOOTH) {
            AudioDevice previousUserSelectedAudioDevice = userSelectedAudioDevice;
            boolean wasBluetoothPreferredForCall = bluetoothPreferredForCall;
            if (!bluetoothManager.requestBluetoothAudioSelection()) {
                Log.e(TAG, "Bluetooth is not available for communication audio");
                updateAudioDeviceState();
                return false;
            }
            userSelectedAudioDevice = AudioDevice.BLUETOOTH;
            bluetoothPreferredForCall = true;
            updateAudioDeviceState();
            if (bluetoothManager.isBluetoothSelectionActive()) {
                return true;
            }
            userSelectedAudioDevice = previousUserSelectedAudioDevice;
            bluetoothPreferredForCall = wasBluetoothPreferredForCall;
            updateAudioDeviceState();
            return false;
        }
        if (!audioDevices.contains(device)) {
            Log.e(TAG, "Can not select " + device + " from available " + audioDevices);
            return false;
        }
        AudioDevice previousUserSelectedAudioDevice = userSelectedAudioDevice;
        boolean wasBluetoothPreferredForCall = bluetoothPreferredForCall;
        userSelectedAudioDevice = device;
        bluetoothPreferredForCall = false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Ask Android to switch first. If the request is rejected, Bluetooth remains the active route and the
            // previous preference can be restored without waiting for the asynchronous Bluetooth teardown timeout.
            if (!setAudioDeviceInternal(device)) {
                userSelectedAudioDevice = previousUserSelectedAudioDevice;
                bluetoothPreferredForCall = wasBluetoothPreferredForCall;
                return false;
            }
            bluetoothManager.onNonBluetoothCommunicationDeviceSelected();
            updateAudioDeviceState();
            return currentAudioDevice == device;
        }

        updateAudioDeviceState();
        if (currentAudioDevice == device) {
            return true;
        }
        userSelectedAudioDevice = previousUserSelectedAudioDevice;
        bluetoothPreferredForCall = wasBluetoothPreferredForCall;
        updateAudioDeviceState();
        return false;
    }

    /**
     * Returns current set of available/selectable audio devices.
     */
    public Set<AudioDevice> getAudioDevices() {
        ThreadUtils.checkIsOnMainThread();
        return Collections.unmodifiableSet(new HashSet<AudioDevice>(audioDevices));
    }

    /**
     * Returns the currently selected audio device.
     */
    public AudioDevice getCurrentAudioDevice() {
        ThreadUtils.checkIsOnMainThread();
        return currentAudioDevice;
    }

    /**
     * Returns the active route, or Bluetooth while Android is processing an accepted Bluetooth request.
     */
    public AudioDevice getAudioDeviceForUi() {
        ThreadUtils.checkIsOnMainThread();
        if (bluetoothPreferredForCall
                && !hasWiredHeadset
                && audioDevices.contains(AudioDevice.BLUETOOTH)
                && bluetoothManager.isBluetoothSelectionActive()) {
            return AudioDevice.BLUETOOTH;
        }
        return currentAudioDevice;
    }

    /**
     * Helper method for receiver registration.
     */
    private void registerReceiver(BroadcastReceiver receiver, IntentFilter filter) {
        ContextExtensionsKt.registerBroadcastReceiver(context, receiver, filter, ReceiverFlag.NotExported);
    }

    /**
     * Helper method for unregistration of an existing receiver.
     */
    private void unregisterReceiver(BroadcastReceiver receiver) {
        context.unregisterReceiver(receiver);
    }

    /**
     * Sets the speaker phone mode.
     */
    private void setSpeakerphoneOn(boolean on) {
        boolean wasOn = audioManager.isSpeakerphoneOn();
        if (wasOn == on) {
            return;
        }
        audioManager.setSpeakerphoneOn(on);
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private boolean setCommunicationDevice(AudioDevice audioDevice) {
        if (audioDevice == AudioDevice.BLUETOOTH
                && bluetoothManager.getState() == WebRtcBluetoothManager.State.SCO_CONNECTED) {
            return true;
        }
        try {
            AudioDeviceInfo currentDevice = getCommunicationDevice();
            boolean currentRouteMatches = currentDevice != null && matchesAudioDevice(currentDevice, audioDevice);
            if (!AudioRoutePolicy.shouldSetCommunicationDevice(
                    currentRouteMatches,
                    bluetoothManager.isBluetoothSelectionActive() || wiredRouteRefreshPending)) {
                return true;
            }

            AudioDeviceInfo selectedDevice = null;
            int selectedPriority = -1;
            for (AudioDeviceInfo device : audioManager.getAvailableCommunicationDevices()) {
                if (matchesAudioDevice(device, audioDevice)) {
                    int priority = audioDevice == AudioDevice.BLUETOOTH
                        ? WebRtcBluetoothManager.bluetoothCommunicationDevicePriority(
                            device.getType(),
                            Build.VERSION.SDK_INT
                        )
                        : 0;
                    if (priority > selectedPriority) {
                        selectedDevice = device;
                        selectedPriority = priority;
                    }
                }
            }
            if (selectedDevice != null) {
                boolean selected = audioManager.setCommunicationDevice(selectedDevice);
                if (!selected) {
                    Log.w(TAG, "Failed to select communication device " + selectedDevice.getType());
                }
                return selected;
            }
        } catch (SecurityException | IllegalArgumentException exception) {
            Log.e(TAG, "Communication device disappeared while it was being selected", exception);
        }
        return false;
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private boolean isCommunicationDeviceSelected(AudioDevice audioDevice) {
        AudioDeviceInfo communicationDevice = getCommunicationDevice();
        return communicationDevice != null && matchesAudioDevice(communicationDevice, audioDevice);
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private boolean matchesAudioDevice(AudioDeviceInfo device, AudioDevice audioDevice) {
        int type = device.getType();
        switch (audioDevice) {
            case BLUETOOTH:
                return WebRtcBluetoothManager.isBluetoothCommunicationDeviceType(type);
            case WIRED_HEADSET:
                return AudioRoutePolicy.isWiredCommunicationOutput(type, device.isSink());
            case EARPIECE:
                return type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE;
            case SPEAKER_PHONE:
                return type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    || type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE;
            default:
                return false;
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    @Nullable
    private AudioDeviceInfo getCommunicationDevice() {
        try {
            return audioManager.getCommunicationDevice();
        } catch (SecurityException exception) {
            Log.e(TAG, "Permission was revoked while reading the communication device", exception);
            return null;
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private void clearCommunicationDevice() {
        try {
            audioManager.clearCommunicationDevice();
        } catch (SecurityException exception) {
            Log.e(TAG, "Permission was revoked while clearing the communication device", exception);
        }
    }

    /**
     * Sets the microphone mute state.
     */
    private void setMicrophoneMute(boolean on) {
        boolean wasMuted = audioManager.isMicrophoneMute();
        if (wasMuted == on) {
            return;
        }
        audioManager.setMicrophoneMute(on);
    }

    /**
     * Gets the current earpiece state.
     */
    private boolean hasEarpiece() {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
    }

    /**
     * Checks whether a wired or USB output sink is currently available. Input-only USB devices are not routes.
     */
    private boolean hasWiredHeadset() {
        Iterable<AudioDeviceInfo> devices = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            ? audioManager.getAvailableCommunicationDevices()
            : Arrays.asList(audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS));
        for (AudioDeviceInfo device : devices) {
            if (AudioRoutePolicy.isWiredCommunicationOutput(device.getType(), device.isSink())) {
                Log.d(TAG, "hasWiredHeadset: found wired or USB audio device");
                return true;
            }
        }
        return false;
    }

    private void onWiredAudioDevicesChanged(AudioDeviceInfo[] changedDevices) {
        ThreadUtils.checkIsOnMainThread();
        for (AudioDeviceInfo device : changedDevices) {
            if (AudioRoutePolicy.isWiredCommunicationOutput(device.getType(), device.isSink())) {
                wiredRouteRefreshPending = true;
                refreshWiredHeadsetState();
                return;
            }
        }
    }

    private void refreshWiredHeadsetState() {
        ThreadUtils.checkIsOnMainThread();
        if (amState != AudioManagerState.RUNNING) {
            return;
        }
        boolean wiredHeadsetAvailable = hasWiredHeadset();
        if (!wiredHeadsetAvailable && hasWiredHeadset == wiredHeadsetAvailable) {
            wiredRouteRefreshPending = false;
        }
        if (hasWiredHeadset == wiredHeadsetAvailable && !wiredRouteRefreshPending) {
            return;
        }
        hasWiredHeadset = wiredHeadsetAvailable;
        updateAudioDeviceState();
    }

    private boolean hasBluetoothCommunicationOutput() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                for (AudioDeviceInfo device : audioManager.getAvailableCommunicationDevices()) {
                    if (WebRtcBluetoothManager.isBluetoothCommunicationDeviceType(device.getType())) {
                        return true;
                    }
                }
            } catch (SecurityException exception) {
                Log.e(TAG, "Bluetooth permission was revoked while enumerating communication devices", exception);
            }
            return false;
        }

        for (AudioDeviceInfo device : audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            if (device.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                return true;
            }
        }
        return false;
    }

    private boolean isBluetoothSelectionPending() {
        WebRtcBluetoothManager.State state = bluetoothManager.getState();
        return bluetoothPreferredForCall
            && !hasWiredHeadset
            && (state == WebRtcBluetoothManager.State.SCO_CONNECTING
                || state == WebRtcBluetoothManager.State.SCO_DISCONNECTING
                || bluetoothManager.isBluetoothRouteRetryScheduled());
    }

    public final void updateAudioDeviceState() {
        ThreadUtils.checkIsOnMainThread();
        Log.d(TAG, "--- updateAudioDeviceState: "
            + "wired headset=" + hasWiredHeadset + ", "
            + "BT state=" + bluetoothManager.getState());
        Log.d(TAG, "Device status: "
            + "internally available=" + internalAudioDevices + ", "
            + "externally available=" + audioDevices + ", "
            + "default=" + defaultAudioDevice + ", "
            + "current=" + currentAudioDevice + ", "
            + "user selected=" + userSelectedAudioDevice);

        if (bluetoothManager.getState() == WebRtcBluetoothManager.State.HEADSET_AVAILABLE
            || bluetoothManager.getState() == WebRtcBluetoothManager.State.HEADSET_UNAVAILABLE) {
            bluetoothManager.updateDevice();
        }

        boolean bluetoothCommunicationOutputAvailable = hasBluetoothCommunicationOutput();
        boolean bluetoothExpected = bluetoothManager.started()
            && (bluetoothCommunicationOutputAvailable || bluetoothManager.isHeadsetProfileExpected());
        bluetoothPreferredForCall = AudioRoutePolicy.shouldPreferBluetooth(
            userSelectedAudioDevice,
            bluetoothPreferredForCall,
            bluetoothExpected,
            bluetoothManager.getState() == WebRtcBluetoothManager.State.HEADSET_UNAVAILABLE
        );

        Set<AudioDevice> newInternalAudioDevices = new HashSet<>();

        if (bluetoothManager.getState() == WebRtcBluetoothManager.State.SCO_CONNECTED
            || bluetoothManager.getState() == WebRtcBluetoothManager.State.SCO_CONNECTING
            || bluetoothManager.getState() == WebRtcBluetoothManager.State.HEADSET_AVAILABLE
            || bluetoothExpected) {
            newInternalAudioDevices.add(AudioDevice.BLUETOOTH);
        }

        if (bluetoothManager.getState() == WebRtcBluetoothManager.State.SCO_CONNECTED) {
            newInternalAudioDevices.add(AudioDevice.BLUETOOTH_SCO);
        }

        if (hasWiredHeadset) {
            // If a wired headset is connected, then it is the only possible option.
            newInternalAudioDevices.add(AudioDevice.WIRED_HEADSET);
        } else {
            newInternalAudioDevices.add(AudioDevice.SPEAKER_PHONE);
            if (hasEarpiece()) {
                newInternalAudioDevices.add(AudioDevice.EARPIECE);
            }
        }

        // Correct user selected wired audio devices if needed. An explicit Bluetooth selection remains sticky so it
        // can resume after the endpoint reconnects.
        if (userSelectedAudioDevice == AudioDevice.SPEAKER_PHONE && hasWiredHeadset) {
            userSelectedAudioDevice = AudioDevice.WIRED_HEADSET;
        }
        if (userSelectedAudioDevice == AudioDevice.WIRED_HEADSET && !hasWiredHeadset) {
            userSelectedAudioDevice = AudioDevice.SPEAKER_PHONE;
        }


        // Need to start Bluetooth if it is available and user either selected it explicitly or
        // user did not select any output device.
        boolean needBluetoothScoStart = WebRtcBluetoothManager.shouldStartBluetoothRoute(
            bluetoothManager.getState(),
            bluetoothPreferredForCall,
            hasWiredHeadset,
            bluetoothManager.isBluetoothRouteRetryScheduled(),
            bluetoothManager.hasRemainingScoConnectionAttempts(),
            audioFocusState.hasTransientLoss()
        );
        boolean nonBluetoothFallbackPending = bluetoothManager.isNonBluetoothFallbackPending();

        // Need to stop Bluetooth audio if user selected different device and
        // Bluetooth SCO connection is established or in the process.
        boolean needBluetoothScoStop =
            (bluetoothManager.getState() == WebRtcBluetoothManager.State.SCO_CONNECTED
                || bluetoothManager.getState() == WebRtcBluetoothManager.State.SCO_CONNECTING)
                && (!bluetoothPreferredForCall || hasWiredHeadset || nonBluetoothFallbackPending);

        if (bluetoothManager.getState() == WebRtcBluetoothManager.State.HEADSET_AVAILABLE
            || bluetoothManager.getState() == WebRtcBluetoothManager.State.SCO_CONNECTING
            || bluetoothManager.getState() == WebRtcBluetoothManager.State.SCO_CONNECTED) {
            Log.d(TAG, "Need BT audio: start=" + needBluetoothScoStart + ", "
                + "stop=" + needBluetoothScoStop + ", "
                + "BT state=" + bluetoothManager.getState());
        }

        // Start Bluetooth SCO when no transition away from it is required.
        if (!needBluetoothScoStop && needBluetoothScoStart && !bluetoothManager.startScoAudio()) {
            // Keep Bluetooth visible so an explicit user selection can reset the bounded retry counter.
            newInternalAudioDevices.remove(AudioDevice.BLUETOOTH_SCO);
        }

        boolean audioDeviceSetUpdated = !internalAudioDevices.equals(newInternalAudioDevices);
        internalAudioDevices = newInternalAudioDevices;
        // BLUETOOTH_SCO isn't allowed to be in the externally accessible list of devices
        audioDevices = new HashSet<>(internalAudioDevices);
        audioDevices.remove(AudioDevice.BLUETOOTH_SCO);


        boolean bluetoothConnected = bluetoothPreferredForCall
            && !hasWiredHeadset
            && !nonBluetoothFallbackPending
            && bluetoothManager.getState() == WebRtcBluetoothManager.State.SCO_CONNECTED
            && newInternalAudioDevices.contains(AudioDevice.BLUETOOTH_SCO);
        AudioDevice selectableDefaultAudioDevice = nonBluetoothFallbackPending
            && defaultAudioDevice == AudioDevice.BLUETOOTH ? AudioDevice.NONE : defaultAudioDevice;
        AudioDevice newCurrentAudioDevice = AudioRoutePolicy.selectAudioDevice(
            audioDevices,
            userSelectedAudioDevice,
            selectableDefaultAudioDevice,
            hasWiredHeadset,
            bluetoothConnected
        );
        boolean selectBeforeBluetoothTeardown = AudioRoutePolicy.shouldSelectBeforeBluetoothTeardown(
            needBluetoothScoStop,
            newCurrentAudioDevice
        );
        boolean communicationRouteNeedsSelection = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            && newCurrentAudioDevice != AudioDevice.NONE
            && !(newCurrentAudioDevice == AudioDevice.BLUETOOTH
                && bluetoothManager.getState() == WebRtcBluetoothManager.State.SCO_CONNECTED)
            && !isCommunicationDeviceSelected(newCurrentAudioDevice);
        boolean audioDeviceUpdateNeeded = newCurrentAudioDevice != currentAudioDevice
            || audioDeviceSetUpdated
            || communicationRouteNeedsSelection
            || selectBeforeBluetoothTeardown
            || wiredRouteRefreshPending;
        AudioDevice previousCurrentAudioDevice = currentAudioDevice;
        boolean routeSelectionSucceeded = false;
        // Switch to new device but only if there has been any changes.
        if (audioDeviceUpdateNeeded) {
            boolean bluetoothSelectionPending = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && isBluetoothSelectionPending()
                && !selectBeforeBluetoothTeardown;
            if (!bluetoothSelectionPending) {
                routeSelectionSucceeded = setAudioDeviceInternal(newCurrentAudioDevice);
            }
            Log.d(TAG, "New device status: "
                + "internally available=" + internalAudioDevices + ", "
                + "externally available=" + audioDevices + ", "
                + "current(new)=" + currentAudioDevice);
        }

        if (wiredRouteRefreshPending
                && (routeSelectionSucceeded || newCurrentAudioDevice == AudioDevice.NONE)) {
            wiredRouteRefreshPending = false;
        }

        if ((selectBeforeBluetoothTeardown || nonBluetoothFallbackPending)
                && newCurrentAudioDevice != AudioDevice.BLUETOOTH
                && routeSelectionSucceeded) {
            newInternalAudioDevices.remove(AudioDevice.BLUETOOTH_SCO);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                bluetoothManager.onNonBluetoothCommunicationDeviceSelected();
            } else if (selectBeforeBluetoothTeardown) {
                bluetoothManager.stopScoAudio();
            }
        } else if (AudioRoutePolicy.canFinishBluetoothTeardown(
                needBluetoothScoStop,
                selectBeforeBluetoothTeardown,
                routeSelectionSucceeded)) {
            bluetoothManager.stopScoAudio();
        }

        boolean audioDeviceChanged = previousCurrentAudioDevice != currentAudioDevice || audioDeviceSetUpdated;
        notifyAudioRouteStateIfChanged(audioDeviceChanged);
        Log.d(TAG, "--- updateAudioDeviceState done");
    }

    private void notifyAudioRouteStateIfChanged(boolean audioDeviceChanged) {
        AudioDevice audioDeviceForUi = getAudioDeviceForUi();
        boolean audioDeviceForUiChanged = audioDeviceForUi != lastReportedAudioDeviceForUi;
        lastReportedAudioDeviceForUi = audioDeviceForUi;
        if ((audioDeviceChanged || audioDeviceForUiChanged) && audioManagerListener != null) {
            audioManagerListener.onAudioDeviceChanged(currentAudioDevice, audioDevices);
        }
    }

    /**
     * AudioDevice is the names of possible audio devices that we currently support.
     */
    public enum AudioDevice {
        SPEAKER_PHONE, WIRED_HEADSET, EARPIECE, BLUETOOTH, NONE,
        BLUETOOTH_SCO // BLUETOOTH_SCO is only valid internal to this class
    }

    /**
     * AudioManager state.
     */
    public enum AudioManagerState {
        UNINITIALIZED,
        PREINITIALIZED,
        RUNNING,
    }

    /**
     * Selected audio device change event.
     */
    public static interface AudioManagerListener {
        // Callback fired once audio device is changed or list of available audio devices changed.
        void onAudioDeviceChanged(
            AudioDevice selectedAudioDevice, Set<AudioDevice> availableAudioDevices);
    }

    /* Receiver which handles changes in wired headset availability. */
    private class WiredHeadsetReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshWiredHeadsetState();
        }
    }
}
