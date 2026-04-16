/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Daniel Calviño Sánchez <danxuliu@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.call;

import com.nextcloud.talk.models.json.participants.Participant;
import com.nextcloud.talk.signaling.SignalingMessageReceiver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper class to keep track of the participants in a call based on the signaling messages.
 * <p>
 * The CallParticipantList adds a listener for participant list messages as soon as it is created and starts tracking
 * the call participants until destroyed. Notifications about the changes can be received by adding an observer to the
 * CallParticipantList; note that no sorting is guaranteed on the participants.
 */
public class CallParticipantList {

    private final CallParticipantListNotifier callParticipantListNotifier = new CallParticipantListNotifier();

    private final SignalingMessageReceiver signalingMessageReceiver;

    public interface Observer {
        void onCallParticipantsChanged(Collection<Participant> joined, Collection<Participant> updated,
                                       Collection<Participant> left, Collection<Participant> unchanged);
        void onCallEndedForAll();
    }

    private final SignalingMessageReceiver.ParticipantListMessageListener participantListMessageListener =
            new SignalingMessageReceiver.ParticipantListMessageListener() {

        private final Map<String, Participant> callParticipants = new HashMap<>();

        @Override
        public void onUsersInRoom(List<Participant> participants) {
            // Internal signaling: full participant list; participants absent from the list have left.
            Collection<Participant> joined = new ArrayList<>();
            Collection<Participant> updated = new ArrayList<>();
            Collection<Participant> left = new ArrayList<>();
            Collection<Participant> unchanged = new ArrayList<>();

            Collection<Participant> notFound = classifyParticipants(participants, joined, updated, left, unchanged);

            for (Participant callParticipant : notFound) {
                callParticipants.remove(callParticipant.getSessionId());
                // No need to copy it, as it will be no longer used.
                callParticipant.setInCall(Participant.InCallFlags.DISCONNECTED);
            }
            left.addAll(notFound);

            notifyIfChanged(joined, updated, left, unchanged);
        }

        @Override
        public void onParticipantsUpdate(List<Participant> participants) {
            // HPB external signaling: partial update containing only changed participants.
            // Participants absent from this list are still in the call — move them to "unchanged"
            // so that observers (e.g. CallActivity) see the full set of active participants.
            Collection<Participant> joined = new ArrayList<>();
            Collection<Participant> updated = new ArrayList<>();
            Collection<Participant> left = new ArrayList<>();
            Collection<Participant> unchanged = new ArrayList<>();

            Collection<Participant> notFound = classifyParticipants(participants, joined, updated, left, unchanged);

            for (Participant callParticipant : notFound) {
                unchanged.add(copyParticipant(callParticipant));
            }

            notifyIfChanged(joined, updated, left, unchanged);
        }

        /**
         * Classifies each participant in the given list into joined/updated/left/unchanged based on
         * the current known call participants. Returns the set of previously known participants that
         * were not present in the list (callers decide what to do with them).
         */
        private Collection<Participant> classifyParticipants(List<Participant> participants,
                                                             Collection<Participant> joined,
                                                             Collection<Participant> updated,
                                                             Collection<Participant> left,
                                                             Collection<Participant> unchanged) {
            Collection<Participant> notFound = new ArrayList<>(callParticipants.values());

            for (Participant participant : participants) {
                String sessionId = participant.getSessionId();
                Participant callParticipant = callParticipants.get(sessionId);

                boolean knownCallParticipant = callParticipant != null;
                if (!knownCallParticipant && participant.getInCall() != Participant.InCallFlags.DISCONNECTED) {
                    callParticipants.put(sessionId, copyParticipant(participant));
                    joined.add(copyParticipant(participant));
                } else if (knownCallParticipant && participant.getInCall() == Participant.InCallFlags.DISCONNECTED) {
                    callParticipants.remove(sessionId);
                    // No need to copy it, as it will be no longer used.
                    callParticipant.setInCall(Participant.InCallFlags.DISCONNECTED);
                    left.add(callParticipant);
                } else if (knownCallParticipant && callParticipant.getInCall() != participant.getInCall()) {
                    callParticipant.setInCall(participant.getInCall());
                    updated.add(copyParticipant(participant));
                } else if (knownCallParticipant) {
                    unchanged.add(copyParticipant(participant));
                }

                if (knownCallParticipant) {
                    notFound.remove(callParticipant);
                }
            }

            return notFound;
        }

        private void notifyIfChanged(Collection<Participant> joined, Collection<Participant> updated,
                                     Collection<Participant> left, Collection<Participant> unchanged) {
            if (!joined.isEmpty() || !updated.isEmpty() || !left.isEmpty()) {
                callParticipantListNotifier.notifyChanged(joined, updated, left, unchanged);
            }
        }

        @Override
        public void onAllParticipantsUpdate(long inCall) {
            if (inCall != Participant.InCallFlags.DISCONNECTED) {
                // Updating all participants is expected to happen only to disconnect them.
                return;
            }

            callParticipantListNotifier.notifyCallEndedForAll();

            Collection<Participant> joined = new ArrayList<>();
            Collection<Participant> updated = new ArrayList<>();
            Collection<Participant> left = new ArrayList<>(callParticipants.size());
            Collection<Participant> unchanged = new ArrayList<>();

            for (Participant callParticipant : callParticipants.values()) {
                // No need to copy it, as it will be no longer used.
                callParticipant.setInCall(Participant.InCallFlags.DISCONNECTED);
                left.add(callParticipant);
            }
            callParticipants.clear();

            if (!left.isEmpty()) {
                callParticipantListNotifier.notifyChanged(joined, updated, left, unchanged);
            }
        }

        private Participant copyParticipant(Participant participant) {
            Participant copiedParticipant = new Participant();
            copiedParticipant.setActorId(participant.getActorId());
            copiedParticipant.setActorType(participant.getActorType());
            copiedParticipant.setInCall(participant.getInCall());
            copiedParticipant.setInternal(participant.getInternal());
            copiedParticipant.setLastPing(participant.getLastPing());
            copiedParticipant.setSessionId(participant.getSessionId());
            copiedParticipant.setType(participant.getType());
            copiedParticipant.setUserId(participant.getUserId());

            return copiedParticipant;
        }
    };

    public CallParticipantList(SignalingMessageReceiver signalingMessageReceiver) {
        this.signalingMessageReceiver = signalingMessageReceiver;
        this.signalingMessageReceiver.addListener(participantListMessageListener);
    }

    public void destroy() {
        signalingMessageReceiver.removeListener(participantListMessageListener);
    }

    public void addObserver(Observer observer) {
        callParticipantListNotifier.addObserver(observer);
    }

    public void removeObserver(Observer observer) {
        callParticipantListNotifier.removeObserver(observer);
    }
}
