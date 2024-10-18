/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Daniel Calviño Sánchez <danxuliu@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.webrtc;

import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Helper class to register and notify PeerConnectionObserver.
 * <p>
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
