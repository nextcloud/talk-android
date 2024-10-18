/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Daniel Calviño Sánchez <danxuliu@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.webrtc;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Helper class to register and notify DataChannelMessageListeners.
 * <p>
 * This class is only meant for internal use by PeerConnectionWrapper; listeners must register themselves against
 * a PeerConnectionWrapper rather than against a DataChannelMessageNotifier.
 */
public class DataChannelMessageNotifier {

    public final Set<PeerConnectionWrapper.DataChannelMessageListener> dataChannelMessageListeners =
        new LinkedHashSet<>();

    public synchronized void addListener(PeerConnectionWrapper.DataChannelMessageListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("DataChannelMessageListener can not be null");
        }

        dataChannelMessageListeners.add(listener);
    }

    public synchronized void removeListener(PeerConnectionWrapper.DataChannelMessageListener listener) {
        dataChannelMessageListeners.remove(listener);
    }

    public synchronized void notifyAudioOn() {
        for (PeerConnectionWrapper.DataChannelMessageListener listener : new ArrayList<>(dataChannelMessageListeners)) {
            listener.onAudioOn();
        }
    }

    public synchronized void notifyAudioOff() {
        for (PeerConnectionWrapper.DataChannelMessageListener listener : new ArrayList<>(dataChannelMessageListeners)) {
            listener.onAudioOff();
        }
    }

    public synchronized void notifyVideoOn() {
        for (PeerConnectionWrapper.DataChannelMessageListener listener : new ArrayList<>(dataChannelMessageListeners)) {
            listener.onVideoOn();
        }
    }

    public synchronized void notifyVideoOff() {
        for (PeerConnectionWrapper.DataChannelMessageListener listener : new ArrayList<>(dataChannelMessageListeners)) {
            listener.onVideoOff();
        }
    }

    public synchronized void notifyNickChanged(String nick) {
        for (PeerConnectionWrapper.DataChannelMessageListener listener : new ArrayList<>(dataChannelMessageListeners)) {
            listener.onNickChanged(nick);
        }
    }
}
