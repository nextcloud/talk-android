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

import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Helper class to register and notify PeerConnectionObserver.
 *
 * This class is only meant for internal use by PeerConnectionWrapper; observers must register themselves against
 * a PeerConnectionWrapper rather than against a PeerConnectionNotifier.
 */
public class PeerConnectionNotifier {

    private final Set<PeerConnectionWrapper.PeerConnectionObserver> peerConnectionObservers = new LinkedHashSet<>();

    public synchronized void addObserver(PeerConnectionWrapper.PeerConnectionObserver observer) {
        if (observer == null) {
            throw new IllegalArgumentException("PeerConnectionObserver can not be null");
        }

        peerConnectionObservers.add(observer);
    }

    public synchronized void removeObserver(PeerConnectionWrapper.PeerConnectionObserver observer) {
        peerConnectionObservers.remove(observer);
    }

    public synchronized void notifyStreamAdded(MediaStream stream) {
        for (PeerConnectionWrapper.PeerConnectionObserver observer : new ArrayList<>(peerConnectionObservers)) {
            observer.onStreamAdded(stream);
        }
    }

    public synchronized void notifyStreamRemoved(MediaStream stream) {
        for (PeerConnectionWrapper.PeerConnectionObserver observer : new ArrayList<>(peerConnectionObservers)) {
            observer.onStreamRemoved(stream);
        }
    }

    public synchronized void notifyIceConnectionStateChanged(PeerConnection.IceConnectionState state) {
        for (PeerConnectionWrapper.PeerConnectionObserver observer : new ArrayList<>(peerConnectionObservers)) {
            observer.onIceConnectionStateChanged(state);
        }
    }
}
