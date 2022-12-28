/*
 * Nextcloud Talk application
 *
 * @author Daniel Calviño Sánchez
 * Copyright (C) 2022 Daniel Calviño Sánchez <danxuliu@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.nextcloud.talk.webrtc;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Helper class to register and notify DataChannelMessageListeners.
 *
 * This class is only meant for internal use by PeerConnectionWrapper; listeners must register themselves against
 * a PeerConnectionWrapper rather than against a DataChannelMessageNotifier.
 */
public class DataChannelMessageNotifier {

    private final Set<PeerConnectionWrapper.DataChannelMessageListener> dataChannelMessageListeners = new LinkedHashSet<>();

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
