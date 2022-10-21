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
package com.nextcloud.talk.signaling;

import com.nextcloud.talk.models.json.participants.Participant;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Helper class to register and notify ParticipantListMessageListeners.
 *
 * This class is only meant for internal use by SignalingMessageReceiver; listeners must register themselves against
 * a SignalingMessageReceiver rather than against a ParticipantListMessageNotifier.
 */
class ParticipantListMessageNotifier {

    private final Set<SignalingMessageReceiver.ParticipantListMessageListener> participantListMessageListeners = new LinkedHashSet<>();

    public synchronized void addListener(SignalingMessageReceiver.ParticipantListMessageListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("participantListMessageListeners can not be null");
        }

        participantListMessageListeners.add(listener);
    }

    public synchronized void removeListener(SignalingMessageReceiver.ParticipantListMessageListener listener) {
        participantListMessageListeners.remove(listener);
    }

    public synchronized void notifyUsersInRoom(List<Participant> participants) {
        for (SignalingMessageReceiver.ParticipantListMessageListener listener : new ArrayList<>(participantListMessageListeners)) {
            listener.onUsersInRoom(participants);
        }
    }

    public synchronized void notifyParticipantsUpdate(List<Participant> participants) {
        for (SignalingMessageReceiver.ParticipantListMessageListener listener : new ArrayList<>(participantListMessageListeners)) {
            listener.onParticipantsUpdate(participants);
        }
    }

    public synchronized void notifyAllParticipantsUpdate(long inCall) {
        for (SignalingMessageReceiver.ParticipantListMessageListener listener : new ArrayList<>(participantListMessageListeners)) {
            listener.onAllParticipantsUpdate(inCall);
        }
    }
}
