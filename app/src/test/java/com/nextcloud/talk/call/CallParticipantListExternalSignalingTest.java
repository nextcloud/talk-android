/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Daniel Calviño Sánchez <danxuliu@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.call;

import com.nextcloud.talk.models.json.participants.Participant;
import com.nextcloud.talk.signaling.SignalingMessageReceiver;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.InOrder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static com.nextcloud.talk.models.json.participants.Participant.InCallFlags.DISCONNECTED;
import static com.nextcloud.talk.models.json.participants.Participant.InCallFlags.IN_CALL;
import static com.nextcloud.talk.models.json.participants.Participant.InCallFlags.WITH_AUDIO;
import static com.nextcloud.talk.models.json.participants.Participant.InCallFlags.WITH_VIDEO;
import static com.nextcloud.talk.models.json.participants.Participant.ParticipantType.GUEST;
import static com.nextcloud.talk.models.json.participants.Participant.ParticipantType.GUEST_MODERATOR;
import static com.nextcloud.talk.models.json.participants.Participant.ParticipantType.MODERATOR;
import static com.nextcloud.talk.models.json.participants.Participant.ParticipantType.OWNER;
import static com.nextcloud.talk.models.json.participants.Participant.ParticipantType.USER;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class CallParticipantListExternalSignalingTest {

    private final ParticipantsUpdateParticipantBuilder builder = new ParticipantsUpdateParticipantBuilder();

    private CallParticipantList callParticipantList;
    private SignalingMessageReceiver.ParticipantListMessageListener participantListMessageListener;

    private CallParticipantList.Observer mockedCallParticipantListObserver;

    private Collection<Participant> expectedJoined;
    private Collection<Participant> expectedUpdated;
    private Collection<Participant> expectedLeft;
    private Collection<Participant> expectedUnchanged;

    // The order of the left participants in some tests depends on how they are internally sorted by the map, so the
    // list of left participants needs to be checked ignoring the sorting (or, rather, sorting by session ID as in
    // expectedLeft).
    // Other tests can just relay on the not guaranteed, but known internal sorting of the elements.
    private final ArgumentMatcher<List<Participant>> matchesExpectedLeftIgnoringOrder = left -> {
        Collections.sort(left, Comparator.comparing(Participant::getSessionId));
        return expectedLeft.equals(left);
    };

    private static class ParticipantsUpdateParticipantBuilder {
        private Participant newUser(long inCall, long lastPing, String sessionId, Participant.ParticipantType type,
                                    String userId) {
            Participant participant = new Participant();
            participant.setInCall(inCall);
            participant.setLastPing(lastPing);
            participant.setSessionId(sessionId);
            participant.setType(type);
            participant.setUserId(userId);
            participant.setActorType(Participant.ActorType.USERS);
            participant.setActorId(userId);

            return participant;
        }

        private Participant newGuest(long inCall, long lastPing, String sessionId, Participant.ParticipantType type) {
            Participant participant = new Participant();
            participant.setInCall(inCall);
            participant.setLastPing(lastPing);
            participant.setSessionId(sessionId);
            participant.setType(type);
            participant.setActorType(Participant.ActorType.GUESTS);
            participant.setActorId("sha1-" + sessionId);

            return participant;
        }
    }

    @Before
    public void setUp() {
        SignalingMessageReceiver mockedSignalingMessageReceiver = mock(SignalingMessageReceiver.class);

        callParticipantList = new CallParticipantList(mockedSignalingMessageReceiver);

        mockedCallParticipantListObserver = mock(CallParticipantList.Observer.class);

        // Get internal ParticipantListMessageListener from callParticipantList set in the
        // mockedSignalingMessageReceiver.
        ArgumentCaptor<SignalingMessageReceiver.ParticipantListMessageListener> participantListMessageListenerArgumentCaptor =
            ArgumentCaptor.forClass(SignalingMessageReceiver.ParticipantListMessageListener.class);

        verify(mockedSignalingMessageReceiver).addListener(participantListMessageListenerArgumentCaptor.capture());

        participantListMessageListener = participantListMessageListenerArgumentCaptor.getValue();

        expectedJoined = new ArrayList<>();
        expectedUpdated = new ArrayList<>();
        expectedLeft = new ArrayList<>();
        expectedUnchanged = new ArrayList<>();
    }

    @Test
    public void testParticipantsUpdateJoinRoom() {
        List<Participant> participants = new ArrayList<>();
        participants.add(builder.newUser(DISCONNECTED, 1, "theSessionId1", MODERATOR, "theUserId1"));

        callParticipantList.addObserver(mockedCallParticipantListObserver);

        participantListMessageListener.onParticipantsUpdate(participants);

        verifyNoInteractions(mockedCallParticipantListObserver);
    }

    @Test
    public void testParticipantsUpdateJoinRoomSeveralParticipants() {
        List<Participant> participants = new ArrayList<>();
        participants.add(builder.newUser(DISCONNECTED, 1, "theSessionId1", MODERATOR, "theUserId1"));
        participants.add(builder.newGuest(DISCONNECTED, 2, "theSessionId2", GUEST));
        participants.add(builder.newUser(DISCONNECTED, 3, "theSessionId3", USER, "theUserId3"));

        callParticipantList.addObserver(mockedCallParticipantListObserver);

        participantListMessageListener.onParticipantsUpdate(participants);

        participants = new ArrayList<>();
        participants.add(builder.newUser(DISCONNECTED, 1, "theSessionId1", MODERATOR, "theUserId1"));
        participants.add(builder.newGuest(DISCONNECTED, 2, "theSessionId2", GUEST));
        participants.add(builder.newUser(DISCONNECTED, 3, "theSessionId3", USER, "theUserId3"));
        participants.add(builder.newUser(DISCONNECTED, 4, "theSessionId4", USER, "theUserId4"));
        participants.add(builder.newUser(DISCONNECTED, 5, "theSessionId5", OWNER, "theUserId5"));

        participantListMessageListener.onParticipantsUpdate(participants);

        verifyNoInteractions(mockedCallParticipantListObserver);
    }

    @Test
    public void testParticipantsUpdateJoinRoomThenJoinCall() {
        List<Participant> participants = new ArrayList<>();
        participants.add(builder.newUser(DISCONNECTED, 1, "theSessionId1", MODERATOR, "theUserId1"));

        participantListMessageListener.onParticipantsUpdate(participants);

        participants = new ArrayList<>();
        participants.add(builder.newUser(IN_CALL | WITH_AUDIO, 1, "theSessionId1", MODERATOR, "theUserId1"));

        callParticipantList.addObserver(mockedCallParticipantListObserver);

        participantListMessageListener.onParticipantsUpdate(participants);

        expectedJoined.add(builder.newUser(IN_CALL | WITH_AUDIO, 1, "theSessionId1", MODERATOR, "theUserId1"));

        verify(mockedCallParticipantListObserver, only()).onCallParticipantsChanged(expectedJoined, expectedUpdated,
                                                                                    expectedLeft, expectedUnchanged);
    }

    @Test
    public void testParticipantsUpdateJoinRoomThenJoinCallSeveralParticipants() {
        List<Participant> participants = new ArrayList<>();
        participants.add(builder.newUser(DISCONNECTED, 1, "theSessionId1", MODERATOR, "theUserId1"));
        participants.add(builder.newGuest(DISCONNECTED, 2, "theSessionId2", GUEST));
        participants.add(builder.newUser(DISCONNECTED, 3, "theSessionId3", USER, "theUserId3"));
        participants.add(builder.newUser(DISCONNECTED, 4, "theSessionId4", USER, "theUserId4"));

        participantListMessageListener.onParticipantsUpdate(participants);

        participants = new ArrayList<>();
        participants.add(builder.newUser(DISCONNECTED, 1, "theSessionId1", MODERATOR, "theUserId1"));
        participants.add(builder.newGuest(DISCONNECTED, 2, "theSessionId2", GUEST));
        participants.add(builder.newUser(DISCONNECTED, 3, "theSessionId3", USER, "theUserId3"));
        participants.add(builder.newUser(IN_CALL, 4, "theSessionId4", USER, "theUserId4"));

        participantListMessageListener.onParticipantsUpdate(participants);

        participants = new ArrayList<>();
        participants.add(builder.newUser(IN_CALL | WITH_AUDIO, 1, "theSessionId1", MODERATOR, "theUserId1"));
        participants.add(builder.newGuest(IN_CALL, 2, "theSessionId2", GUEST));
        participants.add(builder.newUser(DISCONNECTED, 3, "theSessionId3", USER, "theUserId3"));
        participants.add(builder.newUser(IN_CALL, 4, "theSessionId4", USER, "theUserId4"));

        callParticipantList.addObserver(mockedCallParticipantListObserver);

        participantListMessageListener.onParticipantsUpdate(participants);

        expectedJoined.add(builder.newUser(IN_CALL | WITH_AUDIO, 1, "theSessionId1", MODERATOR, "theUserId1"));
        expectedJoined.add(builder.newGuest(IN_CALL, 2, "theSessionId2", GUEST));
        expectedUnchanged.add(builder.newUser(IN_CALL, 4, "theSessionId4", USER, "theUserId4"));

        verify(mockedCallParticipantListObserver, only()).onCallParticipantsChanged(expectedJoined, expectedUpdated,
                                                                                    expectedLeft, expectedUnchanged);
    }

    @Test
    public void testParticipantsUpdateJoinRoomAndCall() {
        List<Participant> participants = new ArrayList<>();
        participants.add(builder.newUser(IN_CALL | WITH_AUDIO, 1, "theSessionId1", MODERATOR, "theUserId1"));

        callParticipantList.addObserver(mockedCallParticipantListObserver);

        participantListMessageListener.onParticipantsUpdate(participants);

        expectedJoined.add(builder.newUser(IN_CALL | WITH_AUDIO, 1, "theSessionId1", MODERATOR, "theUserId1"));

        verify(mockedCallParticipantListObserver, only()).onCallParticipantsChanged(expectedJoined, expectedUpdated,
                                                                                    expectedLeft, expectedUnchanged);
    }

    @Test
    public void testParticipantsUpdateJoinRoomAndCallSeveralParticipants() {
        List<Participant> participants = new ArrayList<>();
        participants.add(builder.newUser(DISCONNECTED, 3, "theSessionId3", USER, "theUserId3"));
        participants.add(builder.newUser(IN_CALL, 4, "theSessionId4", USER, "theUserId4"));

        participantListMessageListener.onParticipantsUpdate(participants);

        callParticipantList.addObserver(mockedCallParticipantListObserver);

        participants = new ArrayList<>();
        participants.add(builder.newUser(IN_CALL | WITH_AUDIO, 1, "theSessionId1", MODERATOR, "theUserId1"));
        participants.add(builder.newGuest(IN_CALL, 2, "theSessionId2", GUEST));
        participants.add(builder.newUser(DISCONNECTED, 3, "theSessionId3", USER, "theUserId3"));
        participants.add(builder.newUser(IN_CALL, 4, "theSessionId4", USER, "theUserId4"));

        participantListMessageListener.onParticipantsUpdate(participants);

        expectedJoined.add(builder.newUser(IN_CALL | WITH_AUDIO, 1, "theSessionId1", MODERATOR, "theUserId1"));
        expectedJoined.add(builder.newGuest(IN_CALL, 2, "theSessionId2", GUEST));
        expectedUnchanged.add(builder.newUser(IN_CALL, 4, "theSessionId4", USER, "theUserId4"));

        verify(mockedCallParticipantListObserver, only()).onCallParticipantsChanged(expectedJoined, expectedUpdated,
                                                                                    expectedLeft, expectedUnchanged);
    }

    @Test
    public void testParticipantsUpdateJoinRoomAndCallRepeated() {
        List<Participant> participants = new ArrayList<>();
        participants.add(builder.newUser(IN_CALL | WITH_AUDIO, 1, "theSessionId1", MODERATOR, "theUserId1"));

        callParticipantList.addObserver(mockedCallParticipantListObserver);

        participantListMessageListener.onParticipantsUpdate(participants);
        participantListMessageListener.onParticipantsUpdate(participants);
        participantListMessageListener.onParticipantsUpdate(participants);

        expectedJoined.add(builder.newUser(IN_CALL | WITH_AUDIO, 1, "theSessionId1", MODERATOR, "theUserId1"));

        verify(mockedCallParticipantListObserver, only()).onCallParticipantsChanged(expectedJoined, expectedUpdated,
                                                                                    expectedLeft, expectedUnchanged);
    }

    @Test
    public void testParticipantsUpdateChangeCallFlags() {
        List<Participant> participants = new ArrayList<>();
        participants.add(builder.newUser(IN_CALL | WITH_AUDIO, 1, "theSessionId1", MODERATOR, "theUserId1"));

        participantListMessageListener.onParticipantsUpdate(participants);

        participants = new ArrayList<>();
        participants.add(builder.newUser(IN_CALL | WITH_AUDIO | WITH_VIDEO, 1, "theSessionId1", MODERATOR, "theUserId1"));

        callParticipantList.addObserver(mockedCallParticipantListObserver);

        participantListMessageListener.onParticipantsUpdate(participants);

        expectedUpdated.add(builder.newUser(IN_CALL | WITH_AUDIO | WITH_VIDEO, 1, "theSessionId1", MODERATOR, "theUserId1"));

        verify(mockedCallParticipantListObserver, only()).onCallParticipantsChanged(expectedJoined, expectedUpdated,
                                                                                    expectedLeft, expectedUnchanged);
    }

    @Test
    public void testParticipantsUpdateChangeCallFlagsSeveralParticipants() {
        List<Participant> participants = new ArrayList<>();
        participants.add(builder.newUser(IN_CALL | WITH_AUDIO, 1, "theSessionId1", MODERATOR, "theUserId1"));
        participants.add(builder.newGuest(IN_CALL | WITH_AUDIO | WITH_VIDEO, 2, "theSessionId2", GUEST));
        participants.add(builder.newUser(DISCONNECTED, 3, "theSessionId3", USER, "theUserId3"));
        participants.add(builder.newUser(IN_CALL, 4, "theSessionId4", USER, "theUserId4"));

        participantListMessageListener.onParticipantsUpdate(participants);

        participants = new ArrayList<>();
        participants.add(builder.newUser(IN_CALL, 1, "theSessionId1", MODERATOR, "theUserId1"));
        participants.add(builder.newGuest(IN_CALL | WITH_AUDIO | WITH_VIDEO, 2, "theSessionId2", GUEST));
        participants.add(builder.newUser(DISCONNECTED, 3, "theSessionId3", USER, "theUserId3"));
        participants.add(builder.newUser(IN_CALL | WITH_VIDEO, 4, "theSessionId4", USER, "theUserId4"));

        callParticipantList.addObserver(mockedCallParticipantListObserver);

        participantListMessageListener.onParticipantsUpdate(participants);

        expectedUpdated.add(builder.newUser(IN_CALL, 1, "theSessionId1", MODERATOR, "theUserId1"));
        expectedUpdated.add(builder.newUser(IN_CALL | WITH_VIDEO, 4, "theSessionId4", USER, "theUserId4"));
        expectedUnchanged.add(builder.newGuest(IN_CALL | WITH_AUDIO | WITH_VIDEO, 2, "theSessionId2", GUEST));

        verify(mockedCallParticipantListObserver, only()).onCallParticipantsChanged(expectedJoined, expectedUpdated,
                                                                                    expectedLeft, expectedUnchanged);
    }

    @Test
    public void testParticipantsUpdateChangeLastPing() {
        List<Participant> participants = new ArrayList<>();
        participants.add(builder.newUser(IN_CALL | WITH_AUDIO, 1, "theSessionId1", MODERATOR, "theUserId1"));

        participantListMessageListener.onParticipantsUpdate(participants);

        participants = new ArrayList<>();
        participants.add(builder.newUser(IN_CALL | WITH_AUDIO, 42, "theSessionId1", MODERATOR, "theUserId1"));

        callParticipantList.addObserver(mockedCallParticipantListObserver);

        participantListMessageListener.onParticipantsUpdate(participants);

        verifyNoInteractions(mockedCallParticipantListObserver);
    }

    @Test
    public void testParticipantsUpdateChangeLastPingSeveralParticipants() {
        List<Participant> participants = new ArrayList<>();
        participants.add(builder.newUser(IN_CALL | WITH_AUDIO, 1, "theSessionId1", MODERATOR, "theUserId1"));
        participants.add(builder.newGuest(IN_CALL | WITH_AUDIO, 2, "theSessionId2", GUEST));
        participants.add(builder.newUser(IN_CALL | WITH_AUDIO, 3, "theSessionId3", USER, "theUserId3"));

        participantListMessageListener.onParticipantsUpdate(participants);

        participants = new ArrayList<>();
        participants.add(builder.newUser(IN_CALL | WITH_AUDIO, 42, "theSessionId1", MODERATOR, "theUserId1"));
        participants.add(builder.newGuest(IN_CALL | WITH_AUDIO, 108, "theSessionId2", GUEST));
        participants.add(builder.newUser(IN_CALL | WITH_AUDIO, 815, "theSessionId3", USER, "theUserId3"));

        callParticipantList.addObserver(mockedCallParticipantListObserver);

        participantListMessageListener.onParticipantsUpdate(participants);

        verifyNoInteractions(mockedCallParticipantListObserver);
    }

    @Test
    public void testParticipantsUpdateChangeParticipantType() {
        List<Participant> participants = new ArrayList<>();
        participants.add(builder.newUser(IN_CALL | WITH_AUDIO, 1, "theSessionId1", MODERATOR, "theUserId1"));

        participantListMessageListener.onParticipantsUpdate(participants);

        participants = new ArrayList<>();
        participants.add(builder.newUser(IN_CALL | WITH_AUDIO, 1, "theSessionId1", USER, "theUserId1"));

        callParticipantList.addObserver(mockedCallParticipantListObserver);

        participantListMessageListener.onParticipantsUpdate(participants);

        verifyNoInteractions(mockedCallParticipantListObserver);
    }

    @Test
    public void testParticipantsUpdateChangeParticipantTypeeSeveralParticipants() {
        List<Participant> participants = new ArrayList<>();
        participants.add(builder.newUser(IN_CALL | WITH_AUDIO, 1, "theSessionId1", MODERATOR, "theUserId1"));
        participants.add(builder.newGuest(IN_CALL | WITH_AUDIO, 2, "theSessionId2", GUEST));
        participants.add(builder.newUser(IN_CALL | WITH_AUDIO, 3, "theSessionId3", USER, "theUserId3"));

        participantListMessageListener.onParticipantsUpdate(participants);

        participants = new ArrayList<>();
        participants.add(builder.newUser(IN_CALL | WITH_AUDIO, 1, "theSessionId1", USER, "theUserId1"));
        participants.add(builder.newGuest(IN_CALL | WITH_AUDIO, 2, "theSessionId2", GUEST_MODERATOR));
        participants.add(builder.newUser(IN_CALL | WITH_AUDIO, 3, "theSessionId3", MODERATOR, "theUserId3"));

        callParticipantList.addObserver(mockedCallParticipantListObserver);

        participantListMessageListener.onParticipantsUpdate(participants);

        verifyNoInteractions(mockedCallParticipantListObserver);
    }

    @Test
    public void testParticipantsUpdateLeaveCall() {
        List<Participant> participants = new ArrayList<>();
        participants.add(builder.newUser(IN_CALL | WITH_AUDIO, 1, "theSessionId1", MODERATOR, "theUserId1"));

        participantListMessageListener.onParticipantsUpdate(participants);

        callParticipantList.addObserver(mockedCallParticipantListObserver);

        participants = new ArrayList<>();
        participants.add(builder.newUser(DISCONNECTED, 1, "theSessionId1", MODERATOR, "theUserId1"));

        participantListMessageListener.onParticipantsUpdate(participants);

        expectedLeft.add(builder.newUser(DISCONNECTED, 1, "theSessionId1", MODERATOR, "theUserId1"));

        verify(mockedCallParticipantListObserver, only()).onCallParticipantsChanged(expectedJoined, expectedUpdated,
                                                                                    expectedLeft, expectedUnchanged);
    }

    @Test
    public void testParticipantsUpdateLeaveCallSeveralParticipants() {
        List<Participant> participants = new ArrayList<>();
        participants.add(builder.newUser(IN_CALL | WITH_AUDIO, 1, "theSessionId1", MODERATOR, "theUserId1"));
        participants.add(builder.newGuest(IN_CALL, 2, "theSessionId2", GUEST));
        participants.add(builder.newUser(DISCONNECTED, 3, "theSessionId3", USER, "theUserId3"));
        participants.add(builder.newUser(IN_CALL, 4, "theSessionId4", USER, "theUserId4"));

        participantListMessageListener.onParticipantsUpdate(participants);

        callParticipantList.addObserver(mockedCallParticipantListObserver);

        participants = new ArrayList<>();
        participants.add(builder.newUser(DISCONNECTED, 1, "theSessionId1", MODERATOR, "theUserId1"));
        participants.add(builder.newGuest(DISCONNECTED, 2, "theSessionId2", GUEST));
        participants.add(builder.newUser(DISCONNECTED, 3, "theSessionId3", USER, "theUserId3"));
        participants.add(builder.newUser(IN_CALL, 4, "theSessionId4", USER, "theUserId4"));

        participantListMessageListener.onParticipantsUpdate(participants);

        expectedLeft.add(builder.newUser(DISCONNECTED, 1, "theSessionId1", MODERATOR, "theUserId1"));
        expectedLeft.add(builder.newGuest(DISCONNECTED, 2, "theSessionId2", GUEST));
        expectedUnchanged.add(builder.newUser(IN_CALL, 4, "theSessionId4", USER, "theUserId4"));

        verify(mockedCallParticipantListObserver, only()).onCallParticipantsChanged(expectedJoined, expectedUpdated,
                                                                                    expectedLeft, expectedUnchanged);
    }

    @Test
    public void testParticipantsUpdateLeaveCallThenLeaveRoom() {
        List<Participant> participants = new ArrayList<>();
        participants.add(builder.newUser(IN_CALL | WITH_AUDIO, 1, "theSessionId1", MODERATOR, "theUserId1"));

        participantListMessageListener.onParticipantsUpdate(participants);

        participants = new ArrayList<>();
        participants.add(builder.newUser(DISCONNECTED, 1, "theSessionId1", MODERATOR, "theUserId1"));

        participantListMessageListener.onParticipantsUpdate(participants);

        callParticipantList.addObserver(mockedCallParticipantListObserver);

        participants = new ArrayList<>();

        participantListMessageListener.onParticipantsUpdate(participants);

        verifyNoInteractions(mockedCallParticipantListObserver);
    }

    @Test
    public void testParticipantsUpdateLeaveCallThenLeaveRoomSeveralParticipants() {
        List<Participant> participants = new ArrayList<>();
        participants.add(builder.newUser(IN_CALL | WITH_AUDIO, 1, "theSessionId1", MODERATOR, "theUserId1"));
        participants.add(builder.newGuest(IN_CALL, 2, "theSessionId2", GUEST));
        participants.add(builder.newUser(DISCONNECTED, 3, "theSessionId3", USER, "theUserId3"));
        participants.add(builder.newUser(IN_CALL, 4, "theSessionId4", USER, "theUserId4"));

        participantListMessageListener.onParticipantsUpdate(participants);

        participants = new ArrayList<>();
        participants.add(builder.newUser(DISCONNECTED, 1, "theSessionId1", MODERATOR, "theUserId1"));
        participants.add(builder.newGuest(DISCONNECTED, 2, "theSessionId2", GUEST));
        participants.add(builder.newUser(DISCONNECTED, 3, "theSessionId3", USER, "theUserId3"));
        participants.add(builder.newUser(IN_CALL, 4, "theSessionId4", USER, "theUserId4"));

        participantListMessageListener.onParticipantsUpdate(participants);

        callParticipantList.addObserver(mockedCallParticipantListObserver);

        participants = new ArrayList<>();
        participants.add(builder.newUser(DISCONNECTED, 3, "theSessionId3", USER, "theUserId3"));
        participants.add(builder.newUser(IN_CALL, 4, "theSessionId4", USER, "theUserId4"));

        participantListMessageListener.onParticipantsUpdate(participants);

        verifyNoInteractions(mockedCallParticipantListObserver);
    }

    @Test
    public void testParticipantsUpdateLeaveCallAndRoom() {
        List<Participant> participants = new ArrayList<>();
        participants.add(builder.newUser(IN_CALL | WITH_AUDIO, 1, "theSessionId1", MODERATOR, "theUserId1"));

        participantListMessageListener.onParticipantsUpdate(participants);

        callParticipantList.addObserver(mockedCallParticipantListObserver);

        participants = new ArrayList<>();

        participantListMessageListener.onParticipantsUpdate(participants);

        expectedLeft.add(builder.newUser(DISCONNECTED, 1, "theSessionId1", MODERATOR, "theUserId1"));

        verify(mockedCallParticipantListObserver, only()).onCallParticipantsChanged(expectedJoined, expectedUpdated,
                                                                                    expectedLeft, expectedUnchanged);
    }

    @Test
    public void testParticipantsUpdateLeaveCallAndRoomSeveralParticipants() {
        List<Participant> participants = new ArrayList<>();
        participants.add(builder.newUser(IN_CALL | WITH_AUDIO, 1, "theSessionId1", MODERATOR, "theUserId1"));
        participants.add(builder.newGuest(IN_CALL, 2, "theSessionId2", GUEST));
        participants.add(builder.newUser(DISCONNECTED, 3, "theSessionId3", USER, "theUserId3"));
        participants.add(builder.newUser(IN_CALL, 4, "theSessionId4", USER, "theUserId4"));

        participantListMessageListener.onParticipantsUpdate(participants);

        callParticipantList.addObserver(mockedCallParticipantListObserver);

        participants = new ArrayList<>();
        participants.add(builder.newUser(DISCONNECTED, 3, "theSessionId3", USER, "theUserId3"));
        participants.add(builder.newUser(IN_CALL, 4, "theSessionId4", USER, "theUserId4"));

        participantListMessageListener.onParticipantsUpdate(participants);

        expectedLeft.add(builder.newUser(DISCONNECTED, 1, "theSessionId1", MODERATOR, "theUserId1"));
        expectedLeft.add(builder.newGuest(DISCONNECTED, 2, "theSessionId2", GUEST));
        expectedUnchanged.add(builder.newUser(IN_CALL, 4, "theSessionId4", USER, "theUserId4"));

        verify(mockedCallParticipantListObserver).onCallParticipantsChanged(eq(expectedJoined), eq(expectedUpdated),
                                                                            argThat(matchesExpectedLeftIgnoringOrder), eq(expectedUnchanged));
    }

    @Test
    public void testParticipantsUpdateSeveralEventsSeveralParticipants() {
        List<Participant> participants = new ArrayList<>();
        participants.add(builder.newUser(IN_CALL | WITH_AUDIO, 1, "theSessionId1", MODERATOR, "theUserId1"));
        participants.add(builder.newGuest(IN_CALL, 2, "theSessionId2", GUEST));
        participants.add(builder.newUser(DISCONNECTED, 3, "theSessionId3", USER, "theUserId3"));
        participants.add(builder.newUser(IN_CALL, 4, "theSessionId4", USER, "theUserId4"));
        participants.add(builder.newUser(IN_CALL, 5, "theSessionId5", OWNER, "theUserId5"));
        // theSessionId6 has not joined yet.
        participants.add(builder.newGuest(IN_CALL | WITH_VIDEO, 7, "theSessionId7", GUEST));
        participants.add(builder.newUser(DISCONNECTED, 8, "theSessionId8", USER, "theUserId8"));
        participants.add(builder.newUser(IN_CALL | WITH_AUDIO, 9, "theSessionId9", MODERATOR, "theUserId9"));

        participantListMessageListener.onParticipantsUpdate(participants);

        callParticipantList.addObserver(mockedCallParticipantListObserver);

        participants = new ArrayList<>();
        // theSessionId1 is gone.
        participants.add(builder.newGuest(DISCONNECTED, 2, "theSessionId2", GUEST));
        participants.add(builder.newUser(DISCONNECTED, 3, "theSessionId3", USER, "theUserId3"));
        participants.add(builder.newUser(IN_CALL, 4, "theSessionId4", USER, "theUserId4"));
        participants.add(builder.newUser(IN_CALL | WITH_AUDIO | WITH_VIDEO, 5, "theSessionId5", OWNER, "theUserId5"));
        participants.add(builder.newGuest(IN_CALL | WITH_AUDIO, 6, "theSessionId6", GUEST));
        participants.add(builder.newGuest(IN_CALL, 7, "theSessionId7", GUEST));
        participants.add(builder.newUser(IN_CALL, 8, "theSessionId8", USER, "theUserId8"));
        participants.add(builder.newUser(IN_CALL | WITH_AUDIO, 42, "theSessionId9", USER, "theUserId9"));

        participantListMessageListener.onParticipantsUpdate(participants);

        expectedJoined.add(builder.newGuest(IN_CALL | WITH_AUDIO, 6, "theSessionId6", GUEST));
        expectedJoined.add(builder.newUser(IN_CALL, 8, "theSessionId8", USER, "theUserId8"));
        expectedUpdated.add(builder.newUser(IN_CALL | WITH_AUDIO | WITH_VIDEO, 5, "theSessionId5", OWNER, "theUserId5"));
        expectedUpdated.add(builder.newGuest(IN_CALL, 7, "theSessionId7", GUEST));
        expectedLeft.add(builder.newUser(DISCONNECTED, 1, "theSessionId1", MODERATOR, "theUserId1"));
        expectedLeft.add(builder.newGuest(DISCONNECTED, 2, "theSessionId2", GUEST));
        expectedUnchanged.add(builder.newUser(IN_CALL, 4, "theSessionId4", USER, "theUserId4"));
        // Last ping and participant type are not seen as changed, even if they did.
        expectedUnchanged.add(builder.newUser(IN_CALL | WITH_AUDIO, 42, "theSessionId9", USER, "theUserId9"));

        verify(mockedCallParticipantListObserver).onCallParticipantsChanged(eq(expectedJoined), eq(expectedUpdated),
                                                                            argThat(matchesExpectedLeftIgnoringOrder), eq(expectedUnchanged));
    }

    @Test
    public void testAllParticipantsUpdateDisconnected() {
        List<Participant> participants = new ArrayList<>();
        participants.add(builder.newUser(IN_CALL, 1, "theSessionId1", MODERATOR, "theUserId1"));

        participantListMessageListener.onParticipantsUpdate(participants);

        callParticipantList.addObserver(mockedCallParticipantListObserver);

        participantListMessageListener.onAllParticipantsUpdate(DISCONNECTED);

        expectedLeft.add(builder.newUser(DISCONNECTED, 1, "theSessionId1", MODERATOR, "theUserId1"));

        InOrder inOrder = inOrder(mockedCallParticipantListObserver);

        inOrder.verify(mockedCallParticipantListObserver).onCallEndedForAll();
        inOrder.verify(mockedCallParticipantListObserver).onCallParticipantsChanged(expectedJoined, expectedUpdated,
                                                                                    expectedLeft, expectedUnchanged);
    }

    @Test
    public void testAllParticipantsUpdateDisconnectedWithSeveralParticipants() {
        List<Participant> participants = new ArrayList<>();
        participants.add(builder.newUser(IN_CALL, 1, "theSessionId1", MODERATOR, "theUserId1"));
        participants.add(builder.newUser(DISCONNECTED, 2, "theSessionId2", USER, "theUserId2"));
        participants.add(builder.newUser(IN_CALL | WITH_AUDIO, 3, "theSessionId3", USER, "theUserId3"));
        participants.add(builder.newGuest(IN_CALL | WITH_AUDIO | WITH_VIDEO, 4, "theSessionId4", GUEST));

        participantListMessageListener.onParticipantsUpdate(participants);

        callParticipantList.addObserver(mockedCallParticipantListObserver);

        participantListMessageListener.onAllParticipantsUpdate(DISCONNECTED);

        expectedLeft.add(builder.newUser(DISCONNECTED, 1, "theSessionId1", MODERATOR, "theUserId1"));
        expectedLeft.add(builder.newUser(DISCONNECTED, 3, "theSessionId3", USER, "theUserId3"));
        expectedLeft.add(builder.newGuest(DISCONNECTED, 4, "theSessionId4", GUEST));

        InOrder inOrder = inOrder(mockedCallParticipantListObserver);

        inOrder.verify(mockedCallParticipantListObserver).onCallEndedForAll();
        inOrder.verify(mockedCallParticipantListObserver).onCallParticipantsChanged(eq(expectedJoined), eq(expectedUpdated),
            argThat(matchesExpectedLeftIgnoringOrder), eq(expectedUnchanged));
    }

    @Test
    public void testAllParticipantsUpdateDisconnectedNoOneInCall() {
        callParticipantList.addObserver(mockedCallParticipantListObserver);

        participantListMessageListener.onAllParticipantsUpdate(DISCONNECTED);

        InOrder inOrder = inOrder(mockedCallParticipantListObserver);

        inOrder.verify(mockedCallParticipantListObserver).onCallEndedForAll();
        verifyNoMoreInteractions(mockedCallParticipantListObserver);
    }

    @Test
    public void testAllParticipantsUpdateDisconnectedThenJoinCallAgain() {
        List<Participant> participants = new ArrayList<>();
        participants.add(builder.newUser(IN_CALL, 1, "theSessionId1", MODERATOR, "theUserId1"));

        participantListMessageListener.onParticipantsUpdate(participants);

        participantListMessageListener.onAllParticipantsUpdate(DISCONNECTED);

        callParticipantList.addObserver(mockedCallParticipantListObserver);

        participants = new ArrayList<>();
        participants.add(builder.newUser(IN_CALL, 1, "theSessionId1", MODERATOR, "theUserId1"));

        participantListMessageListener.onParticipantsUpdate(participants);

        expectedJoined.add(builder.newUser(IN_CALL, 1, "theSessionId1", MODERATOR, "theUserId1"));

        verify(mockedCallParticipantListObserver, only()).onCallParticipantsChanged(expectedJoined, expectedUpdated,
                                                                                    expectedLeft, expectedUnchanged);
    }
}
