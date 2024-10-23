/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Daniel Calviño Sánchez <danxuliu@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.signaling;

import com.nextcloud.talk.models.json.participants.Participant;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

public class SignalingMessageReceiverParticipantListTest {

    private SignalingMessageReceiver signalingMessageReceiver;

    @Before
    public void setUp() {
        // SignalingMessageReceiver is abstract to prevent direct instantiation without calling the appropriate
        // protected methods.
        signalingMessageReceiver = new SignalingMessageReceiver() {
        };
    }

    @Test
    public void testAddParticipantListMessageListenerWithNullListener() {
        Assert.assertThrows(IllegalArgumentException.class, () -> {
            signalingMessageReceiver.addListener((SignalingMessageReceiver.ParticipantListMessageListener) null);
        });
    }

    @Test
    public void testInternalSignalingParticipantListMessageUsersInRoom() {
        SignalingMessageReceiver.ParticipantListMessageListener mockedParticipantListMessageListener =
            mock(SignalingMessageReceiver.ParticipantListMessageListener.class);

        signalingMessageReceiver.addListener(mockedParticipantListMessageListener);

        List<Map<String, Object>> users = new ArrayList<>(2);
        Map<String, Object> user1 = new HashMap<>();
        user1.put("inCall", 7);
        user1.put("lastPing", 4815);
        user1.put("roomId", 108);
        user1.put("sessionId", "theSessionId1");
        user1.put("userId", "theUserId");
        // If any of the following properties is set in any of the participants all the other participants in the
        // message would have it too. But for test simplicity, and as it is not relevant for the processing, in this
        // test they are included only in one of the participants.
        user1.put("participantPermissions", 42);
        user1.put("actorType", "federated_users");
        user1.put("actorId", "theActorId");
        users.add(user1);
        Map<String, Object> user2 = new HashMap<>();
        user2.put("inCall", 0);
        user2.put("lastPing", 162342);
        user2.put("roomId", 108);
        user2.put("sessionId", "theSessionId2");
        user2.put("userId", "");
        users.add(user2);
        signalingMessageReceiver.processUsersInRoom(users);

        List<Participant> expectedParticipantList = new ArrayList<>();
        Participant expectedParticipant1 = new Participant();
        expectedParticipant1.setInCall(Participant.InCallFlags.IN_CALL | Participant.InCallFlags.WITH_AUDIO | Participant.InCallFlags.WITH_VIDEO);
        expectedParticipant1.setLastPing(4815);
        expectedParticipant1.setSessionId("theSessionId1");
        expectedParticipant1.setUserId("theUserId");
        expectedParticipant1.setActorType(Participant.ActorType.FEDERATED);
        expectedParticipant1.setActorId("theActorId");
        expectedParticipantList.add(expectedParticipant1);

        Participant expectedParticipant2 = new Participant();
        expectedParticipant2.setInCall(Participant.InCallFlags.DISCONNECTED);
        expectedParticipant2.setLastPing(162342);
        expectedParticipant2.setSessionId("theSessionId2");
        expectedParticipantList.add(expectedParticipant2);

        verify(mockedParticipantListMessageListener, only()).onUsersInRoom(expectedParticipantList);
    }

    @Test
    public void testInternalSignalingParticipantListMessageAfterRemovingListener() {
        SignalingMessageReceiver.ParticipantListMessageListener mockedParticipantListMessageListener =
            mock(SignalingMessageReceiver.ParticipantListMessageListener.class);

        signalingMessageReceiver.addListener(mockedParticipantListMessageListener);
        signalingMessageReceiver.removeListener(mockedParticipantListMessageListener);

        List<Map<String, Object>> users = new ArrayList<>(1);
        Map<String, Object> user = new HashMap<>();
        user.put("inCall", 0);
        user.put("lastPing", 4815);
        user.put("roomId", 108);
        user.put("sessionId", "theSessionId");
        user.put("userId", "");
        users.add(user);
        signalingMessageReceiver.processUsersInRoom(users);

        verifyNoInteractions(mockedParticipantListMessageListener);
    }

    @Test
    public void testInternalSignalingParticipantListMessageAfterRemovingSingleListenerOfSeveral() {
        SignalingMessageReceiver.ParticipantListMessageListener mockedParticipantListMessageListener1 =
            mock(SignalingMessageReceiver.ParticipantListMessageListener.class);
        SignalingMessageReceiver.ParticipantListMessageListener mockedParticipantListMessageListener2 =
            mock(SignalingMessageReceiver.ParticipantListMessageListener.class);
        SignalingMessageReceiver.ParticipantListMessageListener mockedParticipantListMessageListener3 =
            mock(SignalingMessageReceiver.ParticipantListMessageListener.class);

        signalingMessageReceiver.addListener(mockedParticipantListMessageListener1);
        signalingMessageReceiver.addListener(mockedParticipantListMessageListener2);
        signalingMessageReceiver.addListener(mockedParticipantListMessageListener3);
        signalingMessageReceiver.removeListener(mockedParticipantListMessageListener2);

        List<Map<String, Object>> users = new ArrayList<>(1);
        Map<String, Object> user = new HashMap<>();
        user.put("inCall", 0);
        user.put("lastPing", 4815);
        user.put("roomId", 108);
        user.put("sessionId", "theSessionId");
        user.put("userId", "");
        users.add(user);
        signalingMessageReceiver.processUsersInRoom(users);

        List<Participant> expectedParticipantList = new ArrayList<>();
        Participant expectedParticipant = new Participant();
        expectedParticipant.setInCall(Participant.InCallFlags.DISCONNECTED);
        expectedParticipant.setLastPing(4815);
        expectedParticipant.setSessionId("theSessionId");
        expectedParticipantList.add(expectedParticipant);

        verify(mockedParticipantListMessageListener1, only()).onUsersInRoom(expectedParticipantList);
        verify(mockedParticipantListMessageListener3, only()).onUsersInRoom(expectedParticipantList);
        verifyNoInteractions(mockedParticipantListMessageListener2);
    }

    @Test
    public void testInternalSignalingParticipantListMessageAfterAddingListenerAgain() {
        SignalingMessageReceiver.ParticipantListMessageListener mockedParticipantListMessageListener =
            mock(SignalingMessageReceiver.ParticipantListMessageListener.class);

        signalingMessageReceiver.addListener(mockedParticipantListMessageListener);
        signalingMessageReceiver.addListener(mockedParticipantListMessageListener);

        List<Map<String, Object>> users = new ArrayList<>(1);
        Map<String, Object> user = new HashMap<>();
        user.put("inCall", 0);
        user.put("lastPing", 4815);
        user.put("roomId", 108);
        user.put("sessionId", "theSessionId");
        user.put("userId", "");
        users.add(user);
        signalingMessageReceiver.processUsersInRoom(users);

        List<Participant> expectedParticipantList = new ArrayList<>();
        Participant expectedParticipant = new Participant();
        expectedParticipant.setInCall(Participant.InCallFlags.DISCONNECTED);
        expectedParticipant.setLastPing(4815);
        expectedParticipant.setSessionId("theSessionId");
        expectedParticipantList.add(expectedParticipant);

        verify(mockedParticipantListMessageListener, only()).onUsersInRoom(expectedParticipantList);
    }

    @Test
    public void testAddParticipantListMessageListenerWhenHandlingInternalSignalingParticipantListMessage() {
        SignalingMessageReceiver.ParticipantListMessageListener mockedParticipantListMessageListener1 =
            mock(SignalingMessageReceiver.ParticipantListMessageListener.class);
        SignalingMessageReceiver.ParticipantListMessageListener mockedParticipantListMessageListener2 =
            mock(SignalingMessageReceiver.ParticipantListMessageListener.class);

        List<Participant> expectedParticipantList = new ArrayList<>();
        Participant expectedParticipant = new Participant();
        expectedParticipant.setInCall(Participant.InCallFlags.DISCONNECTED);
        expectedParticipant.setLastPing(4815);
        expectedParticipant.setSessionId("theSessionId");
        expectedParticipantList.add(expectedParticipant);

        doAnswer((invocation) -> {
            signalingMessageReceiver.addListener(mockedParticipantListMessageListener2);
            return null;
        }).when(mockedParticipantListMessageListener1).onUsersInRoom(expectedParticipantList);

        signalingMessageReceiver.addListener(mockedParticipantListMessageListener1);

        List<Map<String, Object>> users = new ArrayList<>(1);
        Map<String, Object> user = new HashMap<>();
        user.put("inCall", 0);
        user.put("lastPing", 4815);
        user.put("roomId", 108);
        user.put("sessionId", "theSessionId");
        user.put("userId", "");
        users.add(user);
        signalingMessageReceiver.processUsersInRoom(users);

        verify(mockedParticipantListMessageListener1, only()).onUsersInRoom(expectedParticipantList);
        verifyNoInteractions(mockedParticipantListMessageListener2);
    }

    @Test
    public void testRemoveParticipantListMessageListenerWhenHandlingInternalSignalingParticipantListMessage() {
        SignalingMessageReceiver.ParticipantListMessageListener mockedParticipantListMessageListener1 =
            mock(SignalingMessageReceiver.ParticipantListMessageListener.class);
        SignalingMessageReceiver.ParticipantListMessageListener mockedParticipantListMessageListener2 =
            mock(SignalingMessageReceiver.ParticipantListMessageListener.class);

        List<Participant> expectedParticipantList = new ArrayList<>();
        Participant expectedParticipant = new Participant();
        expectedParticipant.setInCall(Participant.InCallFlags.DISCONNECTED);
        expectedParticipant.setLastPing(4815);
        expectedParticipant.setSessionId("theSessionId");
        expectedParticipantList.add(expectedParticipant);

        doAnswer((invocation) -> {
            signalingMessageReceiver.removeListener(mockedParticipantListMessageListener2);
            return null;
        }).when(mockedParticipantListMessageListener1).onUsersInRoom(expectedParticipantList);

        signalingMessageReceiver.addListener(mockedParticipantListMessageListener1);
        signalingMessageReceiver.addListener(mockedParticipantListMessageListener2);

        List<Map<String, Object>> users = new ArrayList<>(1);
        Map<String, Object> user = new HashMap<>();
        user.put("inCall", 0);
        user.put("lastPing", 4815);
        user.put("roomId", 108);
        user.put("sessionId", "theSessionId");
        user.put("userId", "");
        users.add(user);
        signalingMessageReceiver.processUsersInRoom(users);

        InOrder inOrder = inOrder(mockedParticipantListMessageListener1, mockedParticipantListMessageListener2);

        inOrder.verify(mockedParticipantListMessageListener1).onUsersInRoom(expectedParticipantList);
        inOrder.verify(mockedParticipantListMessageListener2).onUsersInRoom(expectedParticipantList);
    }

    @Test
    public void testExternalSignalingParticipantListMessageParticipantsUpdate() {
        SignalingMessageReceiver.ParticipantListMessageListener mockedParticipantListMessageListener =
            mock(SignalingMessageReceiver.ParticipantListMessageListener.class);

        signalingMessageReceiver.addListener(mockedParticipantListMessageListener);

        Map<String, Object> eventMap = new HashMap<>();
        eventMap.put("type", "update");
        eventMap.put("target", "participants");
        Map<String, Object> updateMap = new HashMap<>();
        updateMap.put("roomId", 108);
        List<Map<String, Object>> users = new ArrayList<>(2);
        Map<String, Object> user1 = new HashMap<>();
        user1.put("inCall", 7);
        user1.put("lastPing", 4815);
        user1.put("sessionId", "theSessionId1");
        user1.put("participantType", 3);
        user1.put("userId", "theUserId");
        // If any of the following properties is set in any of the participants all the other participants in the
        // message would have it too. But for test simplicity, and as it is not relevant for the processing, in this
        // test they are included only in one of the participants.
        user1.put("nextcloudSessionId", "theNextcloudSessionId");
        user1.put("participantPermissions", 42);
        user1.put("actorType", "federated_users");
        user1.put("actorId", "theActorId");
        users.add(user1);
        Map<String, Object> user2 = new HashMap<>();
        user2.put("inCall", 0);
        user2.put("lastPing", 162342);
        user2.put("sessionId", "theSessionId2");
        user2.put("participantType", 4);
        users.add(user2);
        updateMap.put("users", users);
        eventMap.put("update", updateMap);
        signalingMessageReceiver.processEvent(eventMap);

        List<Participant> expectedParticipantList = new ArrayList<>(2);
        Participant expectedParticipant1 = new Participant();
        expectedParticipant1.setInCall(Participant.InCallFlags.IN_CALL | Participant.InCallFlags.WITH_AUDIO | Participant.InCallFlags.WITH_VIDEO);
        expectedParticipant1.setLastPing(4815);
        expectedParticipant1.setSessionId("theSessionId1");
        expectedParticipant1.setType(Participant.ParticipantType.USER);
        expectedParticipant1.setUserId("theUserId");
        expectedParticipant1.setActorType(Participant.ActorType.FEDERATED);
        expectedParticipant1.setActorId("theActorId");
        expectedParticipantList.add(expectedParticipant1);

        Participant expectedParticipant2 = new Participant();
        expectedParticipant2.setInCall(Participant.InCallFlags.DISCONNECTED);
        expectedParticipant2.setLastPing(162342);
        expectedParticipant2.setSessionId("theSessionId2");
        expectedParticipant2.setType(Participant.ParticipantType.GUEST);
        expectedParticipantList.add(expectedParticipant2);

        verify(mockedParticipantListMessageListener, only()).onParticipantsUpdate(expectedParticipantList);
    }

    @Test
    public void testExternalSignalingParticipantListMessageAllParticipantsUpdate() {
        SignalingMessageReceiver.ParticipantListMessageListener mockedParticipantListMessageListener =
            mock(SignalingMessageReceiver.ParticipantListMessageListener.class);

        signalingMessageReceiver.addListener(mockedParticipantListMessageListener);

        Map<String, Object> eventMap = new HashMap<>();
        eventMap.put("type", "update");
        eventMap.put("target", "participants");
        Map<String, Object> updateMap = new HashMap<>();
        updateMap.put("roomId", 108);
        updateMap.put("all", true);
        updateMap.put("incall", 0);
        eventMap.put("update", updateMap);
        signalingMessageReceiver.processEvent(eventMap);

        verify(mockedParticipantListMessageListener, only()).onAllParticipantsUpdate(Participant.InCallFlags.DISCONNECTED);
    }

    @Test
    public void testExternalSignalingParticipantListMessageAfterRemovingListener() {
        SignalingMessageReceiver.ParticipantListMessageListener mockedParticipantListMessageListener =
            mock(SignalingMessageReceiver.ParticipantListMessageListener.class);

        signalingMessageReceiver.addListener(mockedParticipantListMessageListener);
        signalingMessageReceiver.removeListener(mockedParticipantListMessageListener);

        Map<String, Object> eventMap = new HashMap<>();
        eventMap.put("type", "update");
        eventMap.put("target", "participants");
        HashMap<String, Object> updateMap = new HashMap<>();
        updateMap.put("roomId", 108);
        updateMap.put("all", true);
        updateMap.put("incall", 0);
        eventMap.put("update", updateMap);
        signalingMessageReceiver.processEvent(eventMap);

        verifyNoInteractions(mockedParticipantListMessageListener);
    }

    @Test
    public void testExternalSignalingParticipantListMessageAfterRemovingSingleListenerOfSeveral() {
        SignalingMessageReceiver.ParticipantListMessageListener mockedParticipantListMessageListener1 =
            mock(SignalingMessageReceiver.ParticipantListMessageListener.class);
        SignalingMessageReceiver.ParticipantListMessageListener mockedParticipantListMessageListener2 =
            mock(SignalingMessageReceiver.ParticipantListMessageListener.class);
        SignalingMessageReceiver.ParticipantListMessageListener mockedParticipantListMessageListener3 =
            mock(SignalingMessageReceiver.ParticipantListMessageListener.class);

        signalingMessageReceiver.addListener(mockedParticipantListMessageListener1);
        signalingMessageReceiver.addListener(mockedParticipantListMessageListener2);
        signalingMessageReceiver.addListener(mockedParticipantListMessageListener3);
        signalingMessageReceiver.removeListener(mockedParticipantListMessageListener2);

        Map<String, Object> eventMap = new HashMap<>();
        eventMap.put("type", "update");
        eventMap.put("target", "participants");
        HashMap<String, Object> updateMap = new HashMap<>();
        updateMap.put("roomId", 108);
        updateMap.put("all", true);
        updateMap.put("incall", 0);
        eventMap.put("update", updateMap);
        signalingMessageReceiver.processEvent(eventMap);

        verify(mockedParticipantListMessageListener1, only()).onAllParticipantsUpdate(Participant.InCallFlags.DISCONNECTED);
        verify(mockedParticipantListMessageListener3, only()).onAllParticipantsUpdate(Participant.InCallFlags.DISCONNECTED);
        verifyNoInteractions(mockedParticipantListMessageListener2);
    }

    @Test
    public void testExternalSignalingParticipantListMessageAfterAddingListenerAgain() {
        SignalingMessageReceiver.ParticipantListMessageListener mockedParticipantListMessageListener =
            mock(SignalingMessageReceiver.ParticipantListMessageListener.class);

        signalingMessageReceiver.addListener(mockedParticipantListMessageListener);
        signalingMessageReceiver.addListener(mockedParticipantListMessageListener);

        Map<String, Object> eventMap = new HashMap<>();
        eventMap.put("type", "update");
        eventMap.put("target", "participants");
        HashMap<String, Object> updateMap = new HashMap<>();
        updateMap.put("roomId", 108);
        updateMap.put("all", true);
        updateMap.put("incall", 0);
        eventMap.put("update", updateMap);
        signalingMessageReceiver.processEvent(eventMap);

        verify(mockedParticipantListMessageListener, only()).onAllParticipantsUpdate(Participant.InCallFlags.DISCONNECTED);
    }

    @Test
    public void testAddParticipantListMessageListenerWhenHandlingExternalSignalingParticipantListMessage() {
        SignalingMessageReceiver.ParticipantListMessageListener mockedParticipantListMessageListener1 =
            mock(SignalingMessageReceiver.ParticipantListMessageListener.class);
        SignalingMessageReceiver.ParticipantListMessageListener mockedParticipantListMessageListener2 =
            mock(SignalingMessageReceiver.ParticipantListMessageListener.class);

        doAnswer((invocation) -> {
            signalingMessageReceiver.addListener(mockedParticipantListMessageListener2);
            return null;
        }).when(mockedParticipantListMessageListener1).onAllParticipantsUpdate(Participant.InCallFlags.DISCONNECTED);

        signalingMessageReceiver.addListener(mockedParticipantListMessageListener1);

        Map<String, Object> eventMap = new HashMap<>();
        eventMap.put("type", "update");
        eventMap.put("target", "participants");
        HashMap<String, Object> updateMap = new HashMap<>();
        updateMap.put("roomId", 108);
        updateMap.put("all", true);
        updateMap.put("incall", 0);
        eventMap.put("update", updateMap);
        signalingMessageReceiver.processEvent(eventMap);

        verify(mockedParticipantListMessageListener1, only()).onAllParticipantsUpdate(Participant.InCallFlags.DISCONNECTED);
        verifyNoInteractions(mockedParticipantListMessageListener2);
    }

    @Test
    public void testRemoveParticipantListMessageListenerWhenHandlingExternalSignalingParticipantListMessage() {
        SignalingMessageReceiver.ParticipantListMessageListener mockedParticipantListMessageListener1 =
            mock(SignalingMessageReceiver.ParticipantListMessageListener.class);
        SignalingMessageReceiver.ParticipantListMessageListener mockedParticipantListMessageListener2 =
            mock(SignalingMessageReceiver.ParticipantListMessageListener.class);

        doAnswer((invocation) -> {
            signalingMessageReceiver.removeListener(mockedParticipantListMessageListener2);
            return null;
        }).when(mockedParticipantListMessageListener1).onAllParticipantsUpdate(Participant.InCallFlags.DISCONNECTED);

        signalingMessageReceiver.addListener(mockedParticipantListMessageListener1);
        signalingMessageReceiver.addListener(mockedParticipantListMessageListener2);

        Map<String, Object> eventMap = new HashMap<>();
        eventMap.put("type", "update");
        eventMap.put("target", "participants");
        HashMap<String, Object> updateMap = new HashMap<>();
        updateMap.put("roomId", 108);
        updateMap.put("all", true);
        updateMap.put("incall", 0);
        eventMap.put("update", updateMap);
        signalingMessageReceiver.processEvent(eventMap);

        InOrder inOrder = inOrder(mockedParticipantListMessageListener1, mockedParticipantListMessageListener2);

        inOrder.verify(mockedParticipantListMessageListener1).onAllParticipantsUpdate(Participant.InCallFlags.DISCONNECTED);
        inOrder.verify(mockedParticipantListMessageListener2).onAllParticipantsUpdate(Participant.InCallFlags.DISCONNECTED);
    }
}
