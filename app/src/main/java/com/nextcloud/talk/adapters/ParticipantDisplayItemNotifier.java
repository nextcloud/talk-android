/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Daniel Calviño Sánchez <danxuliu@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.adapters;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Helper class to register and notify ParticipantDisplayItem.Observers.
 * <p>
 * This class is only meant for internal use by ParticipantDisplayItem; observers must register themselves against a
 * ParticipantDisplayItem rather than against a ParticipantDisplayItemNotifier.
 */
class ParticipantDisplayItemNotifier {

    private final Set<ParticipantDisplayItem.Observer> participantDisplayItemObservers = new LinkedHashSet<>();

    public synchronized void addObserver(ParticipantDisplayItem.Observer observer) {
        if (observer == null) {
            throw new IllegalArgumentException("ParticipantDisplayItem.Observer can not be null");
        }

        participantDisplayItemObservers.add(observer);
    }

    public synchronized void removeObserver(ParticipantDisplayItem.Observer observer) {
        participantDisplayItemObservers.remove(observer);
    }

    public synchronized void notifyChange() {
        for (ParticipantDisplayItem.Observer observer : new ArrayList<>(participantDisplayItemObservers)) {
            observer.onChange();
        }
    }
}
