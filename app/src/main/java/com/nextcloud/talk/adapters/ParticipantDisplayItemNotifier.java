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
package com.nextcloud.talk.adapters;

import com.nextcloud.talk.signaling.SignalingMessageReceiver;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Helper class to register and notify ParticipantDisplayItem.Observers.
 *
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
