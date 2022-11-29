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
package com.nextcloud.talk.call;

import com.nextcloud.talk.models.json.participants.Participant;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Helper class to register and notify CallParticipantList.Observers.
 *
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
