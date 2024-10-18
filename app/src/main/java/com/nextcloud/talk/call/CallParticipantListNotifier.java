/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Daniel Calviño Sánchez <danxuliu@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.call;

import com.nextcloud.talk.models.json.participants.Participant;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Helper class to register and notify CallParticipantList.Observers.
 * <p>
 * This class is only meant for internal use by CallParticipantList; listeners must register themselves against
 * a CallParticipantList rather than against a CallParticipantListNotifier.
 */
class CallParticipantListNotifier {

    private final Set<CallParticipantList.Observer> callParticipantListObservers = new LinkedHashSet<>();

    public synchronized void addObserver(CallParticipantList.Observer observer) {
        if (observer == null) {
            throw new IllegalArgumentException("CallParticipantList.Observer can not be null");
        }

        callParticipantListObservers.add(observer);
    }

    public synchronized void removeObserver(CallParticipantList.Observer observer) {
        callParticipantListObservers.remove(observer);
    }

    public synchronized void notifyChanged(Collection<Participant> joined, Collection<Participant> updated,
                                           Collection<Participant> left, Collection<Participant> unchanged) {
        for (CallParticipantList.Observer observer : new ArrayList<>(callParticipantListObservers)) {
            observer.onCallParticipantsChanged(joined, updated, left, unchanged);
        }
    }

    public synchronized void notifyCallEndedForAll() {
        for (CallParticipantList.Observer observer : new ArrayList<>(callParticipantListObservers)) {
            observer.onCallEndedForAll();
        }
    }
}
