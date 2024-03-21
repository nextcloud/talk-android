/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Daniel Calviño Sánchez <danxuliu@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.signaling;

import com.nextcloud.talk.models.json.signaling.NCMessagePayload;
import com.nextcloud.talk.models.json.signaling.NCSignalingMessage;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

public class SignalingMessageReceiverCallParticipantTest {

    private SignalingMessageReceiver signalingMessageReceiver;

    @Before
    public void setUp() {
        // SignalingMessageReceiver is abstract to prevent direct instantiation without calling the appropriate
        // protected methods.
        signalingMessageReceiver = new SignalingMessageReceiver() {
        };
    }

    @Test
    public void testAddCallParticipantMessageListenerWithNullListener() {
        Assert.assertThrows(IllegalArgumentException.class, () -> {
            signalingMessageReceiver.addListener(null, "theSessionId");
        });
    }

    @Test
    public void testAddCallParticipantMessageListenerWithNullSessionId() {
        SignalingMessageReceiver.CallParticipantMessageListener mockedCallParticipantMessageListener =
            mock(SignalingMessageReceiver.CallParticipantMessageListener.class);

        Assert.assertThrows(IllegalArgumentException.class, () -> {
            signalingMessageReceiver.addListener(mockedCallParticipantMessageListener, null);
        });
    }

    @Test
    public void testCallParticipantMessageRaiseHand() {
        SignalingMessageReceiver.CallParticipantMessageListener mockedCallParticipantMessageListener =
            mock(SignalingMessageReceiver.CallParticipantMessageListener.class);

        signalingMessageReceiver.addListener(mockedCallParticipantMessageListener, "theSessionId");

        NCSignalingMessage signalingMessage = new NCSignalingMessage();
        signalingMessage.setFrom("theSessionId");
        signalingMessage.setType("raiseHand");
        signalingMessage.setRoomType("theRoomType");
        NCMessagePayload messagePayload = new NCMessagePayload();
        messagePayload.setType("raiseHand");
        messagePayload.setState(Boolean.TRUE);
        messagePayload.setTimestamp(4815162342L);
        signalingMessage.setPayload(messagePayload);
        signalingMessageReceiver.processSignalingMessage(signalingMessage);

        verify(mockedCallParticipantMessageListener, only()).onRaiseHand(true, 4815162342L);
    }

    @Test
    public void testCallParticipantMessageReaction() {
        SignalingMessageReceiver.CallParticipantMessageListener mockedCallParticipantMessageListener =
            mock(SignalingMessageReceiver.CallParticipantMessageListener.class);

        signalingMessageReceiver.addListener(mockedCallParticipantMessageListener, "theSessionId");

        NCSignalingMessage signalingMessage = new NCSignalingMessage();
        signalingMessage.setFrom("theSessionId");
        signalingMessage.setType("reaction");
        signalingMessage.setRoomType("theRoomType");
        NCMessagePayload messagePayload = new NCMessagePayload();
        messagePayload.setType("reaction");
        messagePayload.setReaction("theReaction");
        signalingMessage.setPayload(messagePayload);
        signalingMessageReceiver.processSignalingMessage(signalingMessage);

        verify(mockedCallParticipantMessageListener, only()).onReaction("theReaction");
    }

    @Test
    public void testCallParticipantMessageUnshareScreen() {
        SignalingMessageReceiver.CallParticipantMessageListener mockedCallParticipantMessageListener =
            mock(SignalingMessageReceiver.CallParticipantMessageListener.class);

        signalingMessageReceiver.addListener(mockedCallParticipantMessageListener, "theSessionId");

        NCSignalingMessage signalingMessage = new NCSignalingMessage();
        signalingMessage.setFrom("theSessionId");
        signalingMessage.setType("unshareScreen");
        signalingMessage.setRoomType("theRoomType");
        signalingMessageReceiver.processSignalingMessage(signalingMessage);

        verify(mockedCallParticipantMessageListener, only()).onUnshareScreen();
    }

    @Test
    public void testCallParticipantMessageSeveralListenersSameFrom() {
        SignalingMessageReceiver.CallParticipantMessageListener mockedCallParticipantMessageListener1 =
            mock(SignalingMessageReceiver.CallParticipantMessageListener.class);
        SignalingMessageReceiver.CallParticipantMessageListener mockedCallParticipantMessageListener2 =
            mock(SignalingMessageReceiver.CallParticipantMessageListener.class);

        signalingMessageReceiver.addListener(mockedCallParticipantMessageListener1, "theSessionId");
        signalingMessageReceiver.addListener(mockedCallParticipantMessageListener2, "theSessionId");

        NCSignalingMessage signalingMessage = new NCSignalingMessage();
        signalingMessage.setFrom("theSessionId");
        signalingMessage.setType("unshareScreen");
        signalingMessage.setRoomType("theRoomType");
        signalingMessageReceiver.processSignalingMessage(signalingMessage);

        verify(mockedCallParticipantMessageListener1, only()).onUnshareScreen();
        verify(mockedCallParticipantMessageListener2, only()).onUnshareScreen();
    }

    @Test
    public void testCallParticipantMessageNotMatchingSessionId() {
        SignalingMessageReceiver.CallParticipantMessageListener mockedCallParticipantMessageListener =
            mock(SignalingMessageReceiver.CallParticipantMessageListener.class);

        signalingMessageReceiver.addListener(mockedCallParticipantMessageListener, "theSessionId");

        NCSignalingMessage signalingMessage = new NCSignalingMessage();
        signalingMessage.setFrom("notMatchingSessionId");
        signalingMessage.setType("unshareScreen");
        signalingMessage.setRoomType("theRoomType");
        signalingMessageReceiver.processSignalingMessage(signalingMessage);

        verifyNoInteractions(mockedCallParticipantMessageListener);
    }

    @Test
    public void testCallParticipantMessageAfterRemovingListener() {
        SignalingMessageReceiver.CallParticipantMessageListener mockedCallParticipantMessageListener =
            mock(SignalingMessageReceiver.CallParticipantMessageListener.class);

        signalingMessageReceiver.addListener(mockedCallParticipantMessageListener, "theSessionId");
        signalingMessageReceiver.removeListener(mockedCallParticipantMessageListener);

        NCSignalingMessage signalingMessage = new NCSignalingMessage();
        signalingMessage.setFrom("theSessionId");
        signalingMessage.setType("unshareScreen");
        signalingMessage.setRoomType("theRoomType");
        signalingMessageReceiver.processSignalingMessage(signalingMessage);

        verifyNoInteractions(mockedCallParticipantMessageListener);
    }

    @Test
    public void testCallParticipantMessageAfterRemovingSingleListenerOfSeveral() {
        SignalingMessageReceiver.CallParticipantMessageListener mockedCallParticipantMessageListener1 =
            mock(SignalingMessageReceiver.CallParticipantMessageListener.class);
        SignalingMessageReceiver.CallParticipantMessageListener mockedCallParticipantMessageListener2 =
            mock(SignalingMessageReceiver.CallParticipantMessageListener.class);
        SignalingMessageReceiver.CallParticipantMessageListener mockedCallParticipantMessageListener3 =
            mock(SignalingMessageReceiver.CallParticipantMessageListener.class);

        signalingMessageReceiver.addListener(mockedCallParticipantMessageListener1, "theSessionId");
        signalingMessageReceiver.addListener(mockedCallParticipantMessageListener2, "theSessionId");
        signalingMessageReceiver.addListener(mockedCallParticipantMessageListener3, "theSessionId");
        signalingMessageReceiver.removeListener(mockedCallParticipantMessageListener2);

        NCSignalingMessage signalingMessage = new NCSignalingMessage();
        signalingMessage.setFrom("theSessionId");
        signalingMessage.setType("unshareScreen");
        signalingMessage.setRoomType("theRoomType");
        signalingMessageReceiver.processSignalingMessage(signalingMessage);

        verify(mockedCallParticipantMessageListener1, only()).onUnshareScreen();
        verify(mockedCallParticipantMessageListener3, only()).onUnshareScreen();
        verifyNoInteractions(mockedCallParticipantMessageListener2);
    }

    @Test
    public void testCallParticipantMessageAfterAddingListenerAgainForDifferentFrom() {
        SignalingMessageReceiver.CallParticipantMessageListener mockedCallParticipantMessageListener =
            mock(SignalingMessageReceiver.CallParticipantMessageListener.class);

        signalingMessageReceiver.addListener(mockedCallParticipantMessageListener, "theSessionId");
        signalingMessageReceiver.addListener(mockedCallParticipantMessageListener, "theSessionId2");

        NCSignalingMessage signalingMessage = new NCSignalingMessage();
        signalingMessage.setFrom("theSessionId");
        signalingMessage.setType("unshareScreen");
        signalingMessage.setRoomType("theRoomType");
        signalingMessageReceiver.processSignalingMessage(signalingMessage);

        verifyNoInteractions(mockedCallParticipantMessageListener);

        signalingMessage.setFrom("theSessionId2");
        signalingMessage.setType("unshareScreen");
        signalingMessage.setRoomType("theRoomType");
        signalingMessageReceiver.processSignalingMessage(signalingMessage);

        verify(mockedCallParticipantMessageListener, only()).onUnshareScreen();
    }

    @Test
    public void testAddCallParticipantMessageListenerWhenHandlingCallParticipantMessage() {
        SignalingMessageReceiver.CallParticipantMessageListener mockedCallParticipantMessageListener1 =
            mock(SignalingMessageReceiver.CallParticipantMessageListener.class);
        SignalingMessageReceiver.CallParticipantMessageListener mockedCallParticipantMessageListener2 =
            mock(SignalingMessageReceiver.CallParticipantMessageListener.class);

        doAnswer((invocation) -> {
            signalingMessageReceiver.addListener(mockedCallParticipantMessageListener2, "theSessionId");
            return null;
        }).when(mockedCallParticipantMessageListener1).onUnshareScreen();

        signalingMessageReceiver.addListener(mockedCallParticipantMessageListener1, "theSessionId");

        NCSignalingMessage signalingMessage = new NCSignalingMessage();
        signalingMessage.setFrom("theSessionId");
        signalingMessage.setType("unshareScreen");
        signalingMessage.setRoomType("theRoomType");
        signalingMessageReceiver.processSignalingMessage(signalingMessage);

        verify(mockedCallParticipantMessageListener1, only()).onUnshareScreen();
        verifyNoInteractions(mockedCallParticipantMessageListener2);
    }

    @Test
    public void testRemoveCallParticipantMessageListenerWhenHandlingCallParticipantMessage() {
        SignalingMessageReceiver.CallParticipantMessageListener mockedCallParticipantMessageListener1 =
            mock(SignalingMessageReceiver.CallParticipantMessageListener.class);
        SignalingMessageReceiver.CallParticipantMessageListener mockedCallParticipantMessageListener2 =
            mock(SignalingMessageReceiver.CallParticipantMessageListener.class);

        doAnswer((invocation) -> {
            signalingMessageReceiver.removeListener(mockedCallParticipantMessageListener2);
            return null;
        }).when(mockedCallParticipantMessageListener1).onUnshareScreen();

        signalingMessageReceiver.addListener(mockedCallParticipantMessageListener1, "theSessionId");
        signalingMessageReceiver.addListener(mockedCallParticipantMessageListener2, "theSessionId");

        NCSignalingMessage signalingMessage = new NCSignalingMessage();
        signalingMessage.setFrom("theSessionId");
        signalingMessage.setType("unshareScreen");
        signalingMessage.setRoomType("theRoomType");
        signalingMessageReceiver.processSignalingMessage(signalingMessage);

        InOrder inOrder = inOrder(mockedCallParticipantMessageListener1, mockedCallParticipantMessageListener2);

        inOrder.verify(mockedCallParticipantMessageListener1).onUnshareScreen();
        inOrder.verify(mockedCallParticipantMessageListener2).onUnshareScreen();
    }
}
