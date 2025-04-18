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
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.util.Log;

import com.nextcloud.talk.events.ProximitySensorEvent;
import com.nextcloud.talk.utils.ContextExtensionsKt;
import com.nextcloud.talk.utils.ReceiverFlag;
import com.nextcloud.talk.utils.power.PowerManagerUtils;

import org.greenrobot.eventbus.EventBus;
import org.webrtc.ThreadUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

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

    private AudioDevice userSelectedAudioDevice;
    private AudioDevice currentAudioDevice;
    private AudioDevice defaultAudioDevice;

    private ProximitySensor proximitySensor = null;

    private Set<AudioDevice> audioDevices = new HashSet<>();

    private Set<AudioDevice> internalAudioDevices = new HashSet<>();

    private final BroadcastReceiver wiredHeadsetReceiver;
    private AudioManager.OnAudioFocusChangeListener audioFocusChangeListener;

    private final PowerManagerUtils powerManagerUtils;

    private WebRtcAudioManager(Context context, boolean useProximitySensor) {
        Log.d(TAG, "ctor");
        ThreadUtils.checkIsOnMainThread();
        this.context = context;
        audioManager = ((AudioManager) context.getSystemService(Context.AUDIO_SERVICE));
        bluetoothManager = WebRtcBluetoothManager.create(context, this);
        wiredHeadsetReceiver = new WiredHeadsetReceiver();
        amState = AudioManagerState.UNINITIALIZED;

        powerManagerUtils = new PowerManagerUtils();
        powerManagerUtils.updatePhoneState(PowerManagerUtils.PhoneState.WITH_PROXIMITY_SENSOR_LOCK);

        this.useProximitySensor = useProximitySensor;
        updateAudioDeviceState();

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

        // Create an AudioManager.OnAudioFocusChangeListener instance.
        audioFocusChangeListener = new AudioManager.OnAudioFocusChangeListener() {
            // Called on the listener to notify if the audio focus for this listener has been changed.
            // The |focusChange| value indicates whether the focus was gained, whether the focus was lost,
            // and whether that loss is transient, or whether the new focus holder will hold it for an
            // unknown amount of time.
            // TODO(henrika): possibly extend support of handling audio-focus changes. Only contains
            // logging for now.
            @Override
            public void onAudioFocusChange(int focusChange) {
                String typeOfChange = "AUDIOFOCUS_NOT_DEFINED";
                switch (focusChange) {
                    case AudioManager.AUDIOFOCUS_GAIN:
                        typeOfChange = "AUDIOFOCUS_GAIN";
                        break;
                    case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
                        typeOfChange = "AUDIOFOCUS_GAIN_TRANSIENT";
                        break;
                    case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE:
                        typeOfChange = "AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE";
                        break;
                    case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
                        typeOfChange = "AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK";
                        break;
                    case AudioManager.AUDIOFOCUS_LOSS:
                        typeOfChange = "AUDIOFOCUS_LOSS";
                        break;
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                        typeOfChange = "AUDIOFOCUS_LOSS_TRANSIENT";
                        break;
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                        typeOfChange = "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK";
                        break;
                    default:
                        typeOfChange = "AUDIOFOCUS_INVALID";
                        break;
                }
                Log.d(TAG, "onAudioFocusChange: " + typeOfChange);
            }
        };

        // Request audio playout focus (without ducking) and install listener for changes in focus.
        int result = audioManager.requestAudioFocus(audioFocusChangeListener,
                                                    AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
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
        audioDevices.clear();
        internalAudioDevices.clear();

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

        if(bluetoothManager.started()) {
            bluetoothManager.stop();
        }

        // Restore previously stored audio states.
        setSpeakerphoneOn(savedIsSpeakerPhoneOn);
        setMicrophoneMute(savedIsMicrophoneMute);
        audioManager.setMode(savedAudioMode);

        // Abandon audio focus. Gives the previous focus owner, if any, focus.
        audioManager.abandonAudioFocus(audioFocusChangeListener);
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
    private void setAudioDeviceInternal(AudioDevice audioDevice) {
        Log.d(TAG, "setAudioDeviceInternal(device=" + audioDevice + ")");

        if (audioDevices.contains(audioDevice)) {
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
                    break;
            }
            currentAudioDevice = audioDevice;
        }
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
     */
    public void selectAudioDevice(AudioDevice device) {
        ThreadUtils.checkIsOnMainThread();
        if (!audioDevices.contains(device)) {
            Log.e(TAG, "Can not select " + device + " from available " + audioDevices);
        }
        userSelectedAudioDevice = device;
        updateAudioDeviceState();
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
     * Checks whether a wired headset is connected or not. This is not a valid indication that audio playback is
     * actually over the wired headset as audio routing depends on other conditions. We only use it as an early
     * indicator (during initialization) of an attached wired headset.
     */
    @Deprecated
    private boolean hasWiredHeadset() {
        @SuppressLint("WrongConstant") final AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_ALL);
        for (AudioDeviceInfo device : devices) {
            final int type = device.getType();
            if (type == AudioDeviceInfo.TYPE_WIRED_HEADSET) {
                Log.d(TAG, "hasWiredHeadset: found wired headset");
                return true;
            } else if (type == AudioDeviceInfo.TYPE_USB_DEVICE) {
                Log.d(TAG, "hasWiredHeadset: found USB audio device");
                return true;
            }
        }
        return false;
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
            || bluetoothManager.getState() == WebRtcBluetoothManager.State.HEADSET_UNAVAILABLE
            || bluetoothManager.getState() == WebRtcBluetoothManager.State.SCO_DISCONNECTING) {
            bluetoothManager.updateDevice();
        }

        Set<AudioDevice> newInternalAudioDevices = new HashSet<>();

        if (bluetoothManager.getState() == WebRtcBluetoothManager.State.SCO_CONNECTED
            || bluetoothManager.getState() == WebRtcBluetoothManager.State.SCO_CONNECTING
            || bluetoothManager.getState() == WebRtcBluetoothManager.State.HEADSET_AVAILABLE) {
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

        // Correct user selected audio devices if needed.
        if (userSelectedAudioDevice == AudioDevice.BLUETOOTH
            && bluetoothManager.getState() == WebRtcBluetoothManager.State.HEADSET_UNAVAILABLE) {
            userSelectedAudioDevice = AudioDevice.SPEAKER_PHONE;
        }
        if (userSelectedAudioDevice == AudioDevice.SPEAKER_PHONE && hasWiredHeadset) {
            userSelectedAudioDevice = AudioDevice.WIRED_HEADSET;
        }
        if (userSelectedAudioDevice == AudioDevice.WIRED_HEADSET && !hasWiredHeadset) {
            userSelectedAudioDevice = AudioDevice.SPEAKER_PHONE;
        }


        // Need to start Bluetooth if it is available and user either selected it explicitly or
        // user did not select any output device.
        boolean needBluetoothScoStart =
            bluetoothManager.getState() == WebRtcBluetoothManager.State.HEADSET_AVAILABLE
                && (userSelectedAudioDevice == AudioDevice.NONE
                || userSelectedAudioDevice == AudioDevice.BLUETOOTH);

        // Need to stop Bluetooth audio if user selected different device and
        // Bluetooth SCO connection is established or in the process.
        boolean needBluetoothScoStop =
            (bluetoothManager.getState() == WebRtcBluetoothManager.State.SCO_CONNECTED
                || bluetoothManager.getState() == WebRtcBluetoothManager.State.SCO_CONNECTING)
                && (userSelectedAudioDevice != AudioDevice.NONE
                && userSelectedAudioDevice != AudioDevice.BLUETOOTH);

        if (bluetoothManager.getState() == WebRtcBluetoothManager.State.HEADSET_AVAILABLE
            || bluetoothManager.getState() == WebRtcBluetoothManager.State.SCO_CONNECTING
            || bluetoothManager.getState() == WebRtcBluetoothManager.State.SCO_CONNECTED) {
            Log.d(TAG, "Need BT audio: start=" + needBluetoothScoStart + ", "
                + "stop=" + needBluetoothScoStop + ", "
                + "BT state=" + bluetoothManager.getState());
        }

        // Start or stop Bluetooth SCO connection given states set earlier.
        if (needBluetoothScoStop) {
            bluetoothManager.stopScoAudio();
            bluetoothManager.updateDevice();
        } else if (needBluetoothScoStart && !bluetoothManager.startScoAudio()) {
            // Remove BLUETOOTH and BLUETOOTH_SCO from list of available devices since SCO start has
            // reported no longer available or too many failed attempts.
            newInternalAudioDevices.remove(AudioDevice.BLUETOOTH);
            newInternalAudioDevices.remove(AudioDevice.BLUETOOTH_SCO);
        }

        boolean audioDeviceSetUpdated = !internalAudioDevices.equals(newInternalAudioDevices);
        internalAudioDevices = newInternalAudioDevices;
        // BLUETOOTH_SCO isn't allowed to be in the externally accessible list of devices
        audioDevices = new HashSet<>(internalAudioDevices);
        audioDevices.remove(AudioDevice.BLUETOOTH_SCO);


        // Update selected audio device.
        AudioDevice newCurrentAudioDevice;

        if ((bluetoothManager.getState() == WebRtcBluetoothManager.State.SCO_CONNECTED)
            && newInternalAudioDevices.contains(AudioDevice.BLUETOOTH_SCO))
        {
            // If Bluetooth SCO is connected and available to use, then it has been selected by user or
            // auto-selected and it should be used as output audio device.
            newCurrentAudioDevice = AudioDevice.BLUETOOTH;
        } else if (hasWiredHeadset) {
            // If a wired headset is connected, but Bluetooth SCO is not, then wired headset is used as
            // audio device.
            newCurrentAudioDevice = AudioDevice.WIRED_HEADSET;
        } else {
            // No wired headset and no Bluetooth SCO, hence the audio-device list can contain speaker
            // phone (on a tablet), or speaker phone and earpiece (on mobile phone).
            // |userSelectedAudioDevice| may contain either AudioDevice.SPEAKER_PHONE or AudioDevice.EARPIECE
            // depending on the user's selection. |defaultAudioDevice|, which is set in code depending on
            // call is audio only or video, to be used if user hasn't made an explicit selection
            if ((userSelectedAudioDevice == AudioDevice.NONE) && (defaultAudioDevice != AudioDevice.NONE))
                newCurrentAudioDevice = defaultAudioDevice;
            else
                newCurrentAudioDevice = userSelectedAudioDevice;
        }
        // Switch to new device but only if there has been any changes.
        if (newCurrentAudioDevice != currentAudioDevice || audioDeviceSetUpdated) {
            // Do the required device switch.
            setAudioDeviceInternal(newCurrentAudioDevice);
            Log.d(TAG, "New device status: "
                + "internally available=" + internalAudioDevices + ", "
                + "externally available=" + audioDevices + ", "
                + "current(new)=" + newCurrentAudioDevice);
            if (audioManagerListener != null) {
                // Notify a listening client that audio device has been changed.
                audioManagerListener.onAudioDeviceChanged(currentAudioDevice, audioDevices);
            }
        }
        Log.d(TAG, "--- updateAudioDeviceState done");
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
        private static final int STATE_UNPLUGGED = 0;
        private static final int STATE_PLUGGED = 1;
        private static final int HAS_NO_MIC = 0;

        @Override
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra("state", STATE_UNPLUGGED);
            // int microphone = intent.getIntExtra("microphone", HAS_NO_MIC);
            // String name = intent.getStringExtra("name");
            hasWiredHeadset = (state == STATE_PLUGGED);
            updateAudioDeviceState();
        }
    }
}
