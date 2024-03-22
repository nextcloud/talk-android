/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Daniel Calviño Sánchez <danxuliu@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.signaling;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

public class SignalingMessageReceiverLocalParticipantTest {

    private SignalingMessageReceiver signalingMessageReceiver;

    @Before
    public void setUp() {
        // SignalingMessageReceiver is abstract to prevent direct instantiation without calling the appropriate
        // protected methods.
        signalingMessageReceiver = new SignalingMessageReceiver() {
        };
    }

    @Test
    public void testAddLocalParticipantMessageListenerWithNullListener() {
        Assert.assertThrows(IllegalArgumentException.class, () -> {
            signalingMessageReceiver.addListener((SignalingMessageReceiver.LocalParticipantMessageListener) null);
        });
    }

    @Test
    public void testExternalSignalingLocalParticipantMessageSwitchTo() {
        SignalingMessageReceiver.LocalParticipantMessageListener mockedLocalParticipantMessageListener =
            mock(SignalingMessageReceiver.LocalParticipantMessageListener.class);

        signalingMessageReceiver.addListener(mockedLocalParticipantMessageListener);

        Map<String, Object> eventMap = new HashMap<>();
        eventMap.put("type", "switchto");
        eventMap.put("target", "room");
        Map<String, Object> switchToMap = new HashMap<>();
        switchToMap.put("roomid", "theToken");
        eventMap.put("switchto", switchToMap);
        signalingMessageReceiver.processEvent(eventMap);

        verify(mockedLocalParticipantMessageListener, only()).onSwitchTo("theToken");
    }

    @Test
    public void testExternalSignalingLocalParticipantMessageAfterRemovingListener() {
        SignalingMessageReceiver.LocalParticipantMessageListener mockedLocalParticipantMessageListener =
            mock(SignalingMessageReceiver.LocalParticipantMessageListener.class);

        signalingMessageReceiver.addListener(mockedLocalParticipantMessageListener);
        signalingMessageReceiver.removeListener(mockedLocalParticipantMessageListener);

        Map<String, Object> eventMap = new HashMap<>();
        eventMap.put("type", "switchto");
        eventMap.put("target", "room");
        HashMap<String, Object> switchToMap = new HashMap<>();
        switchToMap.put("roomid", "theToken");
        eventMap.put("switchto", switchToMap);
        signalingMessageReceiver.processEvent(eventMap);

        verifyNoInteractions(mockedLocalParticipantMessageListener);
    }

    @Test
    public void testExternalSignalingLocalParticipantMessageAfterRemovingSingleListenerOfSeveral() {
        SignalingMessageReceiver.LocalParticipantMessageListener mockedLocalParticipantMessageListener1 =
            mock(SignalingMessageReceiver.LocalParticipantMessageListener.class);
        SignalingMessageReceiver.LocalParticipantMessageListener mockedLocalParticipantMessageListener2 =
            mock(SignalingMessageReceiver.LocalParticipantMessageListener.class);
        SignalingMessageReceiver.LocalParticipantMessageListener mockedLocalParticipantMessageListener3 =
            mock(SignalingMessageReceiver.LocalParticipantMessageListener.class);

        signalingMessageReceiver.addListener(mockedLocalParticipantMessageListener1);
        signalingMessageReceiver.addListener(mockedLocalParticipantMessageListener2);
        signalingMessageReceiver.addListener(mockedLocalParticipantMessageListener3);
        signalingMessageReceiver.removeListener(mockedLocalParticipantMessageListener2);

        Map<String, Object> eventMap = new HashMap<>();
        eventMap.put("type", "switchto");
        eventMap.put("target", "room");
        HashMap<String, Object> switchToMap = new HashMap<>();
        switchToMap.put("roomid", "theToken");
        eventMap.put("switchto", switchToMap);
        signalingMessageReceiver.processEvent(eventMap);

        verify(mockedLocalParticipantMessageListener1, only()).onSwitchTo("theToken");
        verify(mockedLocalParticipantMessageListener3, only()).onSwitchTo("theToken");
        verifyNoInteractions(mockedLocalParticipantMessageListener2);
    }

    @Test
    public void testExternalSignalingLocalParticipantMessageAfterAddingListenerAgain() {
        SignalingMessageReceiver.LocalParticipantMessageListener mockedLocalParticipantMessageListener =
            mock(SignalingMessageReceiver.LocalParticipantMessageListener.class);

        signalingMessageReceiver.addListener(mockedLocalParticipantMessageListener);
        signalingMessageReceiver.addListener(mockedLocalParticipantMessageListener);

        Map<String, Object> eventMap = new HashMap<>();
        eventMap.put("type", "switchto");
        eventMap.put("target", "room");
        HashMap<String, Object> switchToMap = new HashMap<>();
        switchToMap.put("roomid", "theToken");
        eventMap.put("switchto", switchToMap);
        signalingMessageReceiver.processEvent(eventMap);

        verify(mockedLocalParticipantMessageListener, only()).onSwitchTo("theToken");
    }

    @Test
    public void testAddLocalParticipantMessageListenerWhenHandlingExternalSignalingLocalParticipantMessage() {
        SignalingMessageReceiver.LocalParticipantMessageListener mockedLocalParticipantMessageListener1 =
            mock(SignalingMessageReceiver.LocalParticipantMessageListener.class);
        SignalingMessageReceiver.LocalParticipantMessageListener mockedLocalParticipantMessageListener2 =
            mock(SignalingMessageReceiver.LocalParticipantMessageListener.class);

        doAnswer((invocation) -> {
            signalingMessageReceiver.addListener(mockedLocalParticipantMessageListener2);
            return null;
        }).when(mockedLocalParticipantMessageListener1).onSwitchTo("theToken");

        signalingMessageReceiver.addListener(mockedLocalParticipantMessageListener1);

        Map<String, Object> eventMap = new HashMap<>();
        eventMap.put("type", "switchto");
        eventMap.put("target", "room");
        HashMap<String, Object> switchToMap = new HashMap<>();
        switchToMap.put("roomid", "theToken");
        eventMap.put("switchto", switchToMap);
        signalingMessageReceiver.processEvent(eventMap);

        verify(mockedLocalParticipantMessageListener1, only()).onSwitchTo("theToken");
        verifyNoInteractions(mockedLocalParticipantMessageListener2);
    }

    @Test
    public void testRemoveLocalParticipantMessageListenerWhenHandlingExternalSignalingLocalParticipantMessage() {
        SignalingMessageReceiver.LocalParticipantMessageListener mockedLocalParticipantMessageListener1 =
            mock(SignalingMessageReceiver.LocalParticipantMessageListener.class);
        SignalingMessageReceiver.LocalParticipantMessageListener mockedLocalParticipantMessageListener2 =
            mock(SignalingMessageReceiver.LocalParticipantMessageListener.class);

        doAnswer((invocation) -> {
            signalingMessageReceiver.removeListener(mockedLocalParticipantMessageListener2);
            return null;
        }).when(mockedLocalParticipantMessageListener1).onSwitchTo("theToken");

        signalingMessageReceiver.addListener(mockedLocalParticipantMessageListener1);
        signalingMessageReceiver.addListener(mockedLocalParticipantMessageListener2);

        Map<String, Object> eventMap = new HashMap<>();
        eventMap.put("type", "switchto");
        eventMap.put("target", "room");
        HashMap<String, Object> switchToMap = new HashMap<>();
        switchToMap.put("roomid", "theToken");
        eventMap.put("switchto", switchToMap);
        signalingMessageReceiver.processEvent(eventMap);

        InOrder inOrder = inOrder(mockedLocalParticipantMessageListener1, mockedLocalParticipantMessageListener2);

        inOrder.verify(mockedLocalParticipantMessageListener1).onSwitchTo("theToken");
        inOrder.verify(mockedLocalParticipantMessageListener2).onSwitchTo("theToken");
    }
}
