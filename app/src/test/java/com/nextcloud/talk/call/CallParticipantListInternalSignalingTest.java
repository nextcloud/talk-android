/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Daniel Calviño Sánchez <danxuliu@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.call;

import com.nextcloud.talk.models.json.participants.ParticipantDto;
import com.nextcloud.talk.signaling.SignalingMessageReceiver;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static com.nextcloud.talk.models.json.participants.ParticipantDto.InCallFlags.DISCONNECTED;
import static com.nextcloud.talk.models.json.participants.ParticipantDto.InCallFlags.IN_CALL;
import static com.nextcloud.talk.models.json.participants.ParticipantDto.InCallFlags.WITH_AUDIO;
import static com.nextcloud.talk.models.json.participants.ParticipantDto.InCallFlags.WITH_VIDEO;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

public class CallParticipantListInternalSignalingTest {

    private final UsersInRoomParticipantBuilder builder = new UsersInRoomParticipantBuilder();

    private CallParticipantList callParticipantList;
    private SignalingMessageReceiver.ParticipantListMessageListener participantListMessageListener;

    private CallParticipantList.Observer mockedCallParticipantListObserver;

    private Collection<ParticipantDto> expectedJoined;
    private Collection<ParticipantDto> expectedUpdated;
    private Collection<ParticipantDto> expectedLeft;
    private Collection<ParticipantDto> expectedUnchanged;

    // The order of the left participants in some tests depends on how they are internally sorted by the map, so the
    // list of left participants needs to be checked ignoring the sorting (or, rather, sorting by session ID as in
    // expectedLeft).
    // Other tests can just relay on the not guaranteed, but known internal sorting of the elements.
    private final ArgumentMatcher<List<ParticipantDto>> matchesExpectedLeftIgnoringOrder = left -> {
        Collections.sort(left, Comparator.comparing(ParticipantDto::getSessionId));
        return expectedLeft.equals(left);
    };

    private static class UsersInRoomParticipantBuilder {
        private ParticipantDto newUser(long inCall, long lastPing, String sessionId, String userId) {
            ParticipantDto participant = new ParticipantDto();
            participant.setInCall(inCall);
            participant.setLastPing(lastPing);
            participant.setSessionId(sessionId);
            participant.setUserId(userId);
            participant.setActorType(ParticipantDto.ActorType.USERS);
            participant.setActorId(userId);

            return participant;
        }

        private ParticipantDto newGuest(long inCall, long lastPing, String sessionId) {
            ParticipantDto participant = new ParticipantDto();
            participant.setInCall(inCall);
            participant.setLastPing(lastPing);
            participant.setSessionId(sessionId);
            participant.setActorType(ParticipantDto.ActorType.GUESTS);
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
    public void testUsersInRoomJoinRoom() {
        List<ParticipantDto> participants = new ArrayList<>();
        participants.add(builder.newUser(DISCONNECTED, 1, "theSessionId1", "theUserId1"));

        callParticipantList.addObserver(mockedCallParticipantListObserver);

        participantListMessageListener.onUsersInRoom(participants);

        verifyNoInteractions(mockedCallParticipantListObserver);
    }

    @Test
    public void testUsersInRoomJoinRoomSeveralParticipants() {
        List<ParticipantDto> participants = new ArrayList<>();
        participants.add(builder.newUser(DISCONNECTED, 1, "theSessionId1", "theUserId1"));
        participants.add(builder.newGuest(DISCONNECTED, 2, "theSessionId2"));
        participants.add(builder.newUser(DISCONNECTED, 3, "theSessionId3", "theUserId3"));

        callParticipantList.addObserver(mockedCallParticipantListObserver);

        participantListMessageListener.onUsersInRoom(participants);

        participants = new ArrayList<>();
        participants.add(builder.newUser(DISCONNECTED, 1, "theSessionId1", "theUserId1"));
        participants.add(builder.newGuest(DISCONNECTED, 2, "theSessionId2"));
        participants.add(builder.newUser(DISCONNECTED, 3, "theSessionId3", "theUserId3"));
        participants.add(builder.newUser(DISCONNECTED, 4, "theSessionId4", "theUserId4"));
        participants.add(builder.newUser(DISCONNECTED, 5, "theSessionId5", "theUserId5"));

        participantListMessageListener.onUsersInRoom(participants);

        verifyNoInteractions(mockedCallParticipantListObserver);
    }

    @Test
    public void testUsersInRoomJoinRoomThenJoinCall() {
        List<ParticipantDto> participants = new ArrayList<>();
        participants.add(builder.newUser(DISCONNECTED, 1, "theSessionId1", "theUserId1"));

        participantListMessageListener.onUsersInRoom(participants);

        participants = new ArrayList<>();
        participants.add(builder.newUser(IN_CALL | WITH_AUDIO, 1, "theSessionId1", "theUserId1"));

        callParticipantList.addObserver(mockedCallParticipantListObserver);

        participantListMessageListener.onUsersInRoom(participants);

        expectedJoined.add(builder.newUser(IN_CALL | WITH_AUDIO, 1, "theSessionId1", "theUserId1"));

        verify(mockedCallParticipantListObserver, only()).onCallParticipantsChanged(expectedJoined, expectedUpdated,
                                                                                    expectedLeft, expectedUnchanged);
    }

    @Test
    public void testUsersInRoomJoinRoomThenJoinCallSeveralParticipants() {
        List<ParticipantDto> participants = new ArrayList<>();
        participants.add(builder.newUser(DISCONNECTED, 1, "theSessionId1", "theUserId1"));
        participants.add(builder.newGuest(DISCONNECTED, 2, "theSessionId2"));
        participants.add(builder.newUser(DISCONNECTED, 3, "theSessionId3", "theUserId3"));
        participants.add(builder.newUser(DISCONNECTED, 4, "theSessionId4", "theUserId4"));

        participantListMessageListener.onUsersInRoom(participants);

        participants = new ArrayList<>();
        participants.add(builder.newUser(DISCONNECTED, 1, "theSessionId1", "theUserId1"));
        participants.add(builder.newGuest(DISCONNECTED, 2, "theSessionId2"));
        participants.add(builder.newUser(DISCONNECTED, 3, "theSessionId3", "theUserId3"));
        participants.add(builder.newUser(IN_CALL, 4, "theSessionId4", "theUserId4"));

        participantListMessageListener.onUsersInRoom(participants);

        participants = new ArrayList<>();
        participants.add(builder.newUser(IN_CALL | WITH_AUDIO, 1, "theSessionId1", "theUserId1"));
        participants.add(builder.newGuest(IN_CALL, 2, "theSessionId2"));
        participants.add(builder.newUser(DISCONNECTED, 3, "theSessionId3", "theUserId3"));
        participants.add(builder.newUser(IN_CALL, 4, "theSessionId4", "theUserId4"));

        callParticipantList.addObserver(mockedCallParticipantListObserver);

        participantListMessageListener.onUsersInRoom(participants);

        expectedJoined.add(builder.newUser(IN_CALL | WITH_AUDIO, 1, "theSessionId1", "theUserId1"));
        expectedJoined.add(builder.newGuest(IN_CALL, 2, "theSessionId2"));
        expectedUnchanged.add(builder.newUser(IN_CALL, 4, "theSessionId4", "theUserId4"));

        verify(mockedCallParticipantListObserver, only()).onCallParticipantsChanged(expectedJoined, expectedUpdated,
                                                                                    expectedLeft, expectedUnchanged);
    }

    @Test
    public void testUsersInRoomJoinRoomAndCall() {
        List<ParticipantDto> participants = new ArrayList<>();
        participants.add(builder.newUser(IN_CALL | WITH_AUDIO, 1, "theSessionId1", "theUserId1"));

        callParticipantList.addObserver(mockedCallParticipantListObserver);

        participantListMessageListener.onUsersInRoom(participants);

        expectedJoined.add(builder.newUser(IN_CALL | WITH_AUDIO, 1, "theSessionId1", "theUserId1"));

        verify(mockedCallParticipantListObserver, only()).onCallParticipantsChanged(expectedJoined, expectedUpdated,
                                                                                    expectedLeft, expectedUnchanged);
    }

    @Test
    public void testUsersInRoomJoinRoomAndCallSeveralParticipants() {
        List<ParticipantDto> participants = new ArrayList<>();
        participants.add(builder.newUser(DISCONNECTED, 3, "theSessionId3", "theUserId3"));
        participants.add(builder.newUser(IN_CALL, 4, "theSessionId4", "theUserId4"));

        participantListMessageListener.onUsersInRoom(participants);

        callParticipantList.addObserver(mockedCallParticipantListObserver);

        participants = new ArrayList<>();
        participants.add(builder.newUser(IN_CALL | WITH_AUDIO, 1, "theSessionId1", "theUserId1"));
        participants.add(builder.newGuest(IN_CALL, 2, "theSessionId2"));
        participants.add(builder.newUser(DISCONNECTED, 3, "theSessionId3", "theUserId3"));
        participants.add(builder.newUser(IN_CALL, 4, "theSessionId4", "theUserId4"));

        participantListMessageListener.onUsersInRoom(participants);

        expectedJoined.add(builder.newUser(IN_CALL | WITH_AUDIO, 1, "theSessionId1", "theUserId1"));
        expectedJoined.add(builder.newGuest(IN_CALL, 2, "theSessionId2"));
        expectedUnchanged.add(builder.newUser(IN_CALL, 4, "theSessionId4", "theUserId4"));

        verify(mockedCallParticipantListObserver, only()).onCallParticipantsChanged(expectedJoined, expectedUpdated,
                                                                                    expectedLeft, expectedUnchanged);
    }

    @Test
    public void testUsersInRoomJoinRoomAndCallRepeated() {
        List<ParticipantDto> participants = new ArrayList<>();
        participants.add(builder.newUser(IN_CALL | WITH_AUDIO, 1, "theSessionId1", "theUserId1"));

        callParticipantList.addObserver(mockedCallParticipantListObserver);

        participantListMessageListener.onUsersInRoom(participants);
        participantListMessageListener.onUsersInRoom(participants);
        participantListMessageListener.onUsersInRoom(participants);

        expectedJoined.add(builder.newUser(IN_CALL | WITH_AUDIO, 1, "theSessionId1", "theUserId1"));

        verify(mockedCallParticipantListObserver, only()).onCallParticipantsChanged(expectedJoined, expectedUpdated,
                                                                                    expectedLeft, expectedUnchanged);
    }

    @Test
    public void testUsersInRoomChangeCallFlags() {
        List<ParticipantDto> participants = new ArrayList<>();
        participants.add(builder.newUser(IN_CALL | WITH_AUDIO, 1, "theSessionId1", "theUserId1"));

        participantListMessageListener.onUsersInRoom(participants);

        participants = new ArrayList<>();
        participants.add(builder.newUser(IN_CALL | WITH_AUDIO | WITH_VIDEO, 1, "theSessionId1", "theUserId1"));

        callParticipantList.addObserver(mockedCallParticipantListObserver);

        participantListMessageListener.onUsersInRoom(participants);

        expectedUpdated.add(builder.newUser(IN_CALL | WITH_AUDIO | WITH_VIDEO, 1, "theSessionId1", "theUserId1"));

        verify(mockedCallParticipantListObserver, only()).onCallParticipantsChanged(expectedJoined, expectedUpdated,
                                                                                    expectedLeft, expectedUnchanged);
    }

    @Test
    public void testUsersInRoomChangeCallFlagsSeveralParticipants() {
        List<ParticipantDto> participants = new ArrayList<>();
        participants.add(builder.newUser(IN_CALL | WITH_AUDIO, 1, "theSessionId1", "theUserId1"));
        participants.add(builder.newGuest(IN_CALL | WITH_AUDIO | WITH_VIDEO, 2, "theSessionId2"));
        participants.add(builder.newUser(DISCONNECTED, 3, "theSessionId3", "theUserId3"));
        participants.add(builder.newUser(IN_CALL, 4, "theSessionId4", "theUserId4"));

        participantListMessageListener.onUsersInRoom(participants);

        participants = new ArrayList<>();
        participants.add(builder.newUser(IN_CALL, 1, "theSessionId1", "theUserId1"));
        participants.add(builder.newGuest(IN_CALL | WITH_AUDIO | WITH_VIDEO, 2, "theSessionId2"));
        participants.add(builder.newUser(DISCONNECTED, 3, "theSessionId3", "theUserId3"));
        participants.add(builder.newUser(IN_CALL | WITH_VIDEO, 4, "theSessionId4", "theUserId4"));

        callParticipantList.addObserver(mockedCallParticipantListObserver);

        participantListMessageListener.onUsersInRoom(participants);

        expectedUpdated.add(builder.newUser(IN_CALL, 1, "theSessionId1", "theUserId1"));
        expectedUpdated.add(builder.newUser(IN_CALL | WITH_VIDEO, 4, "theSessionId4", "theUserId4"));
        expectedUnchanged.add(builder.newGuest(IN_CALL | WITH_AUDIO | WITH_VIDEO, 2, "theSessionId2"));

        verify(mockedCallParticipantListObserver, only()).onCallParticipantsChanged(expectedJoined, expectedUpdated,
                                                                                    expectedLeft, expectedUnchanged);
    }

    @Test
    public void testUsersInRoomChangeLastPing() {
        List<ParticipantDto> participants = new ArrayList<>();
        participants.add(builder.newUser(IN_CALL | WITH_AUDIO, 1, "theSessionId1", "theUserId1"));

        participantListMessageListener.onUsersInRoom(participants);

        participants = new ArrayList<>();
        participants.add(builder.newUser(IN_CALL | WITH_AUDIO, 42, "theSessionId1", "theUserId1"));

        callParticipantList.addObserver(mockedCallParticipantListObserver);

        participantListMessageListener.onUsersInRoom(participants);

        verifyNoInteractions(mockedCallParticipantListObserver);
    }

    @Test
    public void testUsersInRoomChangeLastPingSeveralParticipants() {
        List<ParticipantDto> participants = new ArrayList<>();
        participants.add(builder.newUser(IN_CALL | WITH_AUDIO, 1, "theSessionId1", "theUserId1"));
        participants.add(builder.newGuest(IN_CALL | WITH_AUDIO, 2, "theSessionId2"));
        participants.add(builder.newUser(IN_CALL | WITH_AUDIO, 3, "theSessionId3", "theUserId3"));

        participantListMessageListener.onUsersInRoom(participants);

        participants = new ArrayList<>();
        participants.add(builder.newUser(IN_CALL | WITH_AUDIO, 42, "theSessionId1", "theUserId1"));
        participants.add(builder.newGuest(IN_CALL | WITH_AUDIO, 108, "theSessionId2"));
        participants.add(builder.newUser(IN_CALL | WITH_AUDIO, 815, "theSessionId3", "theUserId3"));

        callParticipantList.addObserver(mockedCallParticipantListObserver);

        participantListMessageListener.onUsersInRoom(participants);

        verifyNoInteractions(mockedCallParticipantListObserver);
    }

    @Test
    public void testUsersInRoomLeaveCall() {
        List<ParticipantDto> participants = new ArrayList<>();
        participants.add(builder.newUser(IN_CALL | WITH_AUDIO, 1, "theSessionId1", "theUserId1"));

        participantListMessageListener.onUsersInRoom(participants);

        callParticipantList.addObserver(mockedCallParticipantListObserver);

        participants = new ArrayList<>();
        participants.add(builder.newUser(DISCONNECTED, 1, "theSessionId1", "theUserId1"));

        participantListMessageListener.onUsersInRoom(participants);

        expectedLeft.add(builder.newUser(DISCONNECTED, 1, "theSessionId1", "theUserId1"));

        verify(mockedCallParticipantListObserver, only()).onCallParticipantsChanged(expectedJoined, expectedUpdated,
                                                                                    expectedLeft, expectedUnchanged);
    }

    @Test
    public void testUsersInRoomLeaveCallSeveralParticipants() {
        List<ParticipantDto> participants = new ArrayList<>();
        participants.add(builder.newUser(IN_CALL | WITH_AUDIO, 1, "theSessionId1", "theUserId1"));
        participants.add(builder.newGuest(IN_CALL, 2, "theSessionId2"));
        participants.add(builder.newUser(DISCONNECTED, 3, "theSessionId3", "theUserId3"));
        participants.add(builder.newUser(IN_CALL, 4, "theSessionId4", "theUserId4"));

        participantListMessageListener.onUsersInRoom(participants);

        callParticipantList.addObserver(mockedCallParticipantListObserver);

        participants = new ArrayList<>();
        participants.add(builder.newUser(DISCONNECTED, 1, "theSessionId1", "theUserId1"));
        participants.add(builder.newGuest(DISCONNECTED, 2, "theSessionId2"));
        participants.add(builder.newUser(DISCONNECTED, 3, "theSessionId3", "theUserId3"));
        participants.add(builder.newUser(IN_CALL, 4, "theSessionId4", "theUserId4"));

        participantListMessageListener.onUsersInRoom(participants);

        expectedLeft.add(builder.newUser(DISCONNECTED, 1, "theSessionId1", "theUserId1"));
        expectedLeft.add(builder.newGuest(DISCONNECTED, 2, "theSessionId2"));
        expectedUnchanged.add(builder.newUser(IN_CALL, 4, "theSessionId4", "theUserId4"));

        verify(mockedCallParticipantListObserver, only()).onCallParticipantsChanged(expectedJoined, expectedUpdated,
                                                                                    expectedLeft, expectedUnchanged);
    }

    @Test
    public void testUsersInRoomLeaveCallThenLeaveRoom() {
        List<ParticipantDto> participants = new ArrayList<>();
        participants.add(builder.newUser(IN_CALL | WITH_AUDIO, 1, "theSessionId1", "theUserId1"));

        participantListMessageListener.onUsersInRoom(participants);

        participants = new ArrayList<>();
        participants.add(builder.newUser(DISCONNECTED, 1, "theSessionId1", "theUserId1"));

        participantListMessageListener.onUsersInRoom(participants);

        callParticipantList.addObserver(mockedCallParticipantListObserver);

        participants = new ArrayList<>();

        participantListMessageListener.onUsersInRoom(participants);

        verifyNoInteractions(mockedCallParticipantListObserver);
    }

    @Test
    public void testUsersInRoomLeaveCallThenLeaveRoomSeveralParticipants() {
        List<ParticipantDto> participants = new ArrayList<>();
        participants.add(builder.newUser(IN_CALL | WITH_AUDIO, 1, "theSessionId1", "theUserId1"));
        participants.add(builder.newGuest(IN_CALL, 2, "theSessionId2"));
        participants.add(builder.newUser(DISCONNECTED, 3, "theSessionId3", "theUserId3"));
        participants.add(builder.newUser(IN_CALL, 4, "theSessionId4", "theUserId4"));

        participantListMessageListener.onUsersInRoom(participants);

        participants = new ArrayList<>();
        participants.add(builder.newUser(DISCONNECTED, 1, "theSessionId1", "theUserId1"));
        participants.add(builder.newGuest(DISCONNECTED, 2, "theSessionId2"));
        participants.add(builder.newUser(DISCONNECTED, 3, "theSessionId3", "theUserId3"));
        participants.add(builder.newUser(IN_CALL, 4, "theSessionId4", "theUserId4"));

        participantListMessageListener.onUsersInRoom(participants);

        callParticipantList.addObserver(mockedCallParticipantListObserver);

        participants = new ArrayList<>();
        participants.add(builder.newUser(DISCONNECTED, 3, "theSessionId3", "theUserId3"));
        participants.add(builder.newUser(IN_CALL, 4, "theSessionId4", "theUserId4"));

        participantListMessageListener.onUsersInRoom(participants);

        verifyNoInteractions(mockedCallParticipantListObserver);
    }

    @Test
    public void testUsersInRoomLeaveCallAndRoom() {
        List<ParticipantDto> participants = new ArrayList<>();
        participants.add(builder.newUser(IN_CALL | WITH_AUDIO, 1, "theSessionId1", "theUserId1"));

        participantListMessageListener.onUsersInRoom(participants);

        callParticipantList.addObserver(mockedCallParticipantListObserver);

        participants = new ArrayList<>();

        participantListMessageListener.onUsersInRoom(participants);

        expectedLeft.add(builder.newUser(DISCONNECTED, 1, "theSessionId1", "theUserId1"));

        verify(mockedCallParticipantListObserver, only()).onCallParticipantsChanged(expectedJoined, expectedUpdated,
                                                                                    expectedLeft, expectedUnchanged);
    }

    @Test
    public void testUsersInRoomLeaveCallAndRoomSeveralParticipants() {
        List<ParticipantDto> participants = new ArrayList<>();
        participants.add(builder.newUser(IN_CALL | WITH_AUDIO, 1, "theSessionId1", "theUserId1"));
        participants.add(builder.newGuest(IN_CALL, 2, "theSessionId2"));
        participants.add(builder.newUser(DISCONNECTED, 3, "theSessionId3", "theUserId3"));
        participants.add(builder.newUser(IN_CALL, 4, "theSessionId4", "theUserId4"));

        participantListMessageListener.onUsersInRoom(participants);

        callParticipantList.addObserver(mockedCallParticipantListObserver);

        participants = new ArrayList<>();
        participants.add(builder.newUser(DISCONNECTED, 3, "theSessionId3", "theUserId3"));
        participants.add(builder.newUser(IN_CALL, 4, "theSessionId4", "theUserId4"));

        participantListMessageListener.onUsersInRoom(participants);

        expectedLeft.add(builder.newUser(DISCONNECTED, 1, "theSessionId1", "theUserId1"));
        expectedLeft.add(builder.newGuest(DISCONNECTED, 2, "theSessionId2"));
        expectedUnchanged.add(builder.newUser(IN_CALL, 4, "theSessionId4", "theUserId4"));

        verify(mockedCallParticipantListObserver).onCallParticipantsChanged(eq(expectedJoined), eq(expectedUpdated),
                                                                            argThat(matchesExpectedLeftIgnoringOrder), eq(expectedUnchanged));
    }

    @Test
    public void testUsersInRoomSeveralEventsSeveralParticipants() {
        List<ParticipantDto> participants = new ArrayList<>();
        participants.add(builder.newUser(IN_CALL | WITH_AUDIO, 1, "theSessionId1", "theUserId1"));
        participants.add(builder.newGuest(IN_CALL, 2, "theSessionId2"));
        participants.add(builder.newUser(DISCONNECTED, 3, "theSessionId3", "theUserId3"));
        participants.add(builder.newUser(IN_CALL, 4, "theSessionId4", "theUserId4"));
        participants.add(builder.newUser(IN_CALL, 5, "theSessionId5", "theUserId5"));
        // theSessionId6 has not joined yet.
        participants.add(builder.newGuest(IN_CALL | WITH_VIDEO, 7, "theSessionId7"));
        participants.add(builder.newUser(DISCONNECTED, 8, "theSessionId8", "theUserId8"));
        participants.add(builder.newUser(IN_CALL | WITH_AUDIO, 9, "theSessionId9", "theUserId9"));

        participantListMessageListener.onUsersInRoom(participants);

        callParticipantList.addObserver(mockedCallParticipantListObserver);

        participants = new ArrayList<>();
        // theSessionId1 is gone.
        participants.add(builder.newGuest(DISCONNECTED, 2, "theSessionId2"));
        participants.add(builder.newUser(DISCONNECTED, 3, "theSessionId3", "theUserId3"));
        participants.add(builder.newUser(IN_CALL, 4, "theSessionId4", "theUserId4"));
        participants.add(builder.newUser(IN_CALL | WITH_AUDIO | WITH_VIDEO, 5, "theSessionId5", "theUserId5"));
        participants.add(builder.newGuest(IN_CALL | WITH_AUDIO, 6, "theSessionId6"));
        participants.add(builder.newGuest(IN_CALL, 7, "theSessionId7"));
        participants.add(builder.newUser(IN_CALL, 8, "theSessionId8", "theUserId8"));
        participants.add(builder.newUser(IN_CALL | WITH_AUDIO, 42, "theSessionId9", "theUserId9"));

        participantListMessageListener.onUsersInRoom(participants);

        expectedJoined.add(builder.newGuest(IN_CALL | WITH_AUDIO, 6, "theSessionId6"));
        expectedJoined.add(builder.newUser(IN_CALL, 8, "theSessionId8", "theUserId8"));
        expectedUpdated.add(builder.newUser(IN_CALL | WITH_AUDIO | WITH_VIDEO, 5, "theSessionId5", "theUserId5"));
        expectedUpdated.add(builder.newGuest(IN_CALL, 7, "theSessionId7"));
        expectedLeft.add(builder.newUser(DISCONNECTED, 1, "theSessionId1", "theUserId1"));
        expectedLeft.add(builder.newGuest(DISCONNECTED, 2, "theSessionId2"));
        expectedUnchanged.add(builder.newUser(IN_CALL, 4, "theSessionId4", "theUserId4"));
        // Last ping is not seen as changed, even if it did.
        expectedUnchanged.add(builder.newUser(IN_CALL | WITH_AUDIO, 42, "theSessionId9", "theUserId9"));

        verify(mockedCallParticipantListObserver).onCallParticipantsChanged(eq(expectedJoined),
                                                                            eq(expectedUpdated),
                                                                            argThat(matchesExpectedLeftIgnoringOrder),
                                                                            eq(expectedUnchanged));
    }
}
