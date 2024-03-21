/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Daniel Calviño Sánchez <danxuliu@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.signaling;

import com.nextcloud.talk.models.json.signaling.NCIceCandidate;
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

public class SignalingMessageReceiverWebRtcTest {

    private SignalingMessageReceiver signalingMessageReceiver;

    @Before
    public void setUp() {
        // SignalingMessageReceiver is abstract to prevent direct instantiation without calling the appropriate
        // protected methods.
        signalingMessageReceiver = new SignalingMessageReceiver() {
        };
    }

    @Test
    public void testAddWebRtcMessageListenerWithNullListener() {
        Assert.assertThrows(IllegalArgumentException.class, () -> {
            signalingMessageReceiver.addListener(null, "theSessionId", "theRoomType");
        });
    }

    @Test
    public void testAddWebRtcMessageListenerWithNullSessionId() {
        SignalingMessageReceiver.WebRtcMessageListener mockedWebRtcMessageListener =
            mock(SignalingMessageReceiver.WebRtcMessageListener.class);

        Assert.assertThrows(IllegalArgumentException.class, () -> {
            signalingMessageReceiver.addListener(mockedWebRtcMessageListener, null, "theRoomType");
        });
    }

    @Test
    public void testAddWebRtcMessageListenerWithNullRoomType() {
        SignalingMessageReceiver.WebRtcMessageListener mockedWebRtcMessageListener =
            mock(SignalingMessageReceiver.WebRtcMessageListener.class);

        Assert.assertThrows(IllegalArgumentException.class, () -> {
            signalingMessageReceiver.addListener(mockedWebRtcMessageListener, "theSessionId", null);
        });
    }

    @Test
    public void testWebRtcMessageOffer() {
        SignalingMessageReceiver.WebRtcMessageListener mockedWebRtcMessageListener =
            mock(SignalingMessageReceiver.WebRtcMessageListener.class);

        signalingMessageReceiver.addListener(mockedWebRtcMessageListener, "theSessionId", "theRoomType");

        NCSignalingMessage signalingMessage = new NCSignalingMessage();
        signalingMessage.setFrom("theSessionId");
        signalingMessage.setType("offer");
        signalingMessage.setRoomType("theRoomType");
        NCMessagePayload messagePayload = new NCMessagePayload();
        messagePayload.setType("offer");
        messagePayload.setSdp("theSdp");
        signalingMessage.setPayload(messagePayload);
        signalingMessageReceiver.processSignalingMessage(signalingMessage);

        verify(mockedWebRtcMessageListener, only()).onOffer("theSdp", null);
    }

    @Test
    public void testWebRtcMessageOfferWithNick() {
        SignalingMessageReceiver.WebRtcMessageListener mockedWebRtcMessageListener =
            mock(SignalingMessageReceiver.WebRtcMessageListener.class);

        signalingMessageReceiver.addListener(mockedWebRtcMessageListener, "theSessionId", "theRoomType");

        NCSignalingMessage signalingMessage = new NCSignalingMessage();
        signalingMessage.setFrom("theSessionId");
        signalingMessage.setType("offer");
        signalingMessage.setRoomType("theRoomType");
        NCMessagePayload messagePayload = new NCMessagePayload();
        messagePayload.setType("offer");
        messagePayload.setSdp("theSdp");
        messagePayload.setNick("theNick");
        signalingMessage.setPayload(messagePayload);
        signalingMessageReceiver.processSignalingMessage(signalingMessage);

        verify(mockedWebRtcMessageListener, only()).onOffer("theSdp", "theNick");
    }

    @Test
    public void testWebRtcMessageAnswer() {
        SignalingMessageReceiver.WebRtcMessageListener mockedWebRtcMessageListener =
            mock(SignalingMessageReceiver.WebRtcMessageListener.class);

        signalingMessageReceiver.addListener(mockedWebRtcMessageListener, "theSessionId", "theRoomType");

        NCSignalingMessage signalingMessage = new NCSignalingMessage();
        signalingMessage.setFrom("theSessionId");
        signalingMessage.setType("answer");
        signalingMessage.setRoomType("theRoomType");
        NCMessagePayload messagePayload = new NCMessagePayload();
        messagePayload.setType("answer");
        messagePayload.setSdp("theSdp");
        signalingMessage.setPayload(messagePayload);
        signalingMessageReceiver.processSignalingMessage(signalingMessage);

        verify(mockedWebRtcMessageListener, only()).onAnswer("theSdp", null);
    }

    @Test
    public void testWebRtcMessageAnswerWithNick() {
        SignalingMessageReceiver.WebRtcMessageListener mockedWebRtcMessageListener =
            mock(SignalingMessageReceiver.WebRtcMessageListener.class);

        signalingMessageReceiver.addListener(mockedWebRtcMessageListener, "theSessionId", "theRoomType");

        NCSignalingMessage signalingMessage = new NCSignalingMessage();
        signalingMessage.setFrom("theSessionId");
        signalingMessage.setType("answer");
        signalingMessage.setRoomType("theRoomType");
        NCMessagePayload messagePayload = new NCMessagePayload();
        messagePayload.setType("answer");
        messagePayload.setSdp("theSdp");
        messagePayload.setNick("theNick");
        signalingMessage.setPayload(messagePayload);
        signalingMessageReceiver.processSignalingMessage(signalingMessage);

        verify(mockedWebRtcMessageListener, only()).onAnswer("theSdp", "theNick");
    }

    @Test
    public void testWebRtcMessageCandidate() {
        SignalingMessageReceiver.WebRtcMessageListener mockedWebRtcMessageListener =
            mock(SignalingMessageReceiver.WebRtcMessageListener.class);

        signalingMessageReceiver.addListener(mockedWebRtcMessageListener, "theSessionId", "theRoomType");

        NCSignalingMessage signalingMessage = new NCSignalingMessage();
        signalingMessage.setFrom("theSessionId");
        signalingMessage.setType("candidate");
        signalingMessage.setRoomType("theRoomType");
        NCMessagePayload messagePayload = new NCMessagePayload();
        NCIceCandidate iceCandidate = new NCIceCandidate();
        iceCandidate.setSdpMid("theSdpMid");
        iceCandidate.setSdpMLineIndex(42);
        iceCandidate.setCandidate("theSdp");
        messagePayload.setIceCandidate(iceCandidate);
        signalingMessage.setPayload(messagePayload);
        signalingMessageReceiver.processSignalingMessage(signalingMessage);

        verify(mockedWebRtcMessageListener, only()).onCandidate("theSdpMid", 42, "theSdp");
    }

    @Test
    public void testWebRtcMessageEndOfCandidates() {
        SignalingMessageReceiver.WebRtcMessageListener mockedWebRtcMessageListener =
            mock(SignalingMessageReceiver.WebRtcMessageListener.class);

        signalingMessageReceiver.addListener(mockedWebRtcMessageListener, "theSessionId", "theRoomType");

        NCSignalingMessage signalingMessage = new NCSignalingMessage();
        signalingMessage.setFrom("theSessionId");
        signalingMessage.setType("endOfCandidates");
        signalingMessage.setRoomType("theRoomType");
        signalingMessageReceiver.processSignalingMessage(signalingMessage);

        verify(mockedWebRtcMessageListener, only()).onEndOfCandidates();
    }

    @Test
    public void testWebRtcMessageSeveralListenersSameFrom() {
        SignalingMessageReceiver.WebRtcMessageListener mockedWebRtcMessageListener1 =
            mock(SignalingMessageReceiver.WebRtcMessageListener.class);
        SignalingMessageReceiver.WebRtcMessageListener mockedWebRtcMessageListener2 =
            mock(SignalingMessageReceiver.WebRtcMessageListener.class);

        signalingMessageReceiver.addListener(mockedWebRtcMessageListener1, "theSessionId", "theRoomType");
        signalingMessageReceiver.addListener(mockedWebRtcMessageListener2, "theSessionId", "theRoomType");

        NCSignalingMessage signalingMessage = new NCSignalingMessage();
        signalingMessage.setFrom("theSessionId");
        signalingMessage.setType("endOfCandidates");
        signalingMessage.setRoomType("theRoomType");
        signalingMessageReceiver.processSignalingMessage(signalingMessage);

        verify(mockedWebRtcMessageListener1, only()).onEndOfCandidates();
        verify(mockedWebRtcMessageListener2, only()).onEndOfCandidates();
    }

    @Test
    public void testWebRtcMessageNotMatchingSessionId() {
        SignalingMessageReceiver.WebRtcMessageListener mockedWebRtcMessageListener =
            mock(SignalingMessageReceiver.WebRtcMessageListener.class);

        signalingMessageReceiver.addListener(mockedWebRtcMessageListener, "theSessionId", "theRoomType");

        NCSignalingMessage signalingMessage = new NCSignalingMessage();
        signalingMessage.setFrom("notMatchingSessionId");
        signalingMessage.setType("endOfCandidates");
        signalingMessage.setRoomType("theRoomType");
        signalingMessageReceiver.processSignalingMessage(signalingMessage);

        verifyNoInteractions(mockedWebRtcMessageListener);
    }

    @Test
    public void testWebRtcMessageNotMatchingRoomType() {
        SignalingMessageReceiver.WebRtcMessageListener mockedWebRtcMessageListener =
            mock(SignalingMessageReceiver.WebRtcMessageListener.class);

        signalingMessageReceiver.addListener(mockedWebRtcMessageListener, "theSessionId", "theRoomType");

        NCSignalingMessage signalingMessage = new NCSignalingMessage();
        signalingMessage.setFrom("theSessionId");
        signalingMessage.setType("endOfCandidates");
        signalingMessage.setRoomType("notMatchingRoomType");
        signalingMessageReceiver.processSignalingMessage(signalingMessage);

        verifyNoInteractions(mockedWebRtcMessageListener);
    }

    @Test
    public void testWebRtcMessageAfterRemovingListener() {
        SignalingMessageReceiver.WebRtcMessageListener mockedWebRtcMessageListener =
            mock(SignalingMessageReceiver.WebRtcMessageListener.class);

        signalingMessageReceiver.addListener(mockedWebRtcMessageListener, "theSessionId", "theRoomType");
        signalingMessageReceiver.removeListener(mockedWebRtcMessageListener);

        NCSignalingMessage signalingMessage = new NCSignalingMessage();
        signalingMessage.setFrom("theSessionId");
        signalingMessage.setType("endOfCandidates");
        signalingMessage.setRoomType("theRoomType");
        signalingMessageReceiver.processSignalingMessage(signalingMessage);

        verifyNoInteractions(mockedWebRtcMessageListener);
    }

    @Test
    public void testWebRtcMessageAfterRemovingSingleListenerOfSeveral() {
        SignalingMessageReceiver.WebRtcMessageListener mockedWebRtcMessageListener1 =
            mock(SignalingMessageReceiver.WebRtcMessageListener.class);
        SignalingMessageReceiver.WebRtcMessageListener mockedWebRtcMessageListener2 =
            mock(SignalingMessageReceiver.WebRtcMessageListener.class);
        SignalingMessageReceiver.WebRtcMessageListener mockedWebRtcMessageListener3 =
            mock(SignalingMessageReceiver.WebRtcMessageListener.class);

        signalingMessageReceiver.addListener(mockedWebRtcMessageListener1, "theSessionId", "theRoomType");
        signalingMessageReceiver.addListener(mockedWebRtcMessageListener2, "theSessionId", "theRoomType");
        signalingMessageReceiver.addListener(mockedWebRtcMessageListener3, "theSessionId", "theRoomType");
        signalingMessageReceiver.removeListener(mockedWebRtcMessageListener2);

        NCSignalingMessage signalingMessage = new NCSignalingMessage();
        signalingMessage.setFrom("theSessionId");
        signalingMessage.setType("endOfCandidates");
        signalingMessage.setRoomType("theRoomType");
        signalingMessageReceiver.processSignalingMessage(signalingMessage);

        verify(mockedWebRtcMessageListener1, only()).onEndOfCandidates();
        verify(mockedWebRtcMessageListener3, only()).onEndOfCandidates();
        verifyNoInteractions(mockedWebRtcMessageListener2);
    }

    @Test
    public void testWebRtcMessageAfterAddingListenerAgainForDifferentFrom() {
        SignalingMessageReceiver.WebRtcMessageListener mockedWebRtcMessageListener =
            mock(SignalingMessageReceiver.WebRtcMessageListener.class);

        signalingMessageReceiver.addListener(mockedWebRtcMessageListener, "theSessionId", "theRoomType");
        signalingMessageReceiver.addListener(mockedWebRtcMessageListener, "theSessionId2", "theRoomType");

        NCSignalingMessage signalingMessage = new NCSignalingMessage();
        signalingMessage.setFrom("theSessionId");
        signalingMessage.setType("endOfCandidates");
        signalingMessage.setRoomType("theRoomType");
        signalingMessageReceiver.processSignalingMessage(signalingMessage);

        verifyNoInteractions(mockedWebRtcMessageListener);

        signalingMessage.setFrom("theSessionId2");
        signalingMessage.setType("endOfCandidates");
        signalingMessage.setRoomType("theRoomType");
        signalingMessageReceiver.processSignalingMessage(signalingMessage);

        verify(mockedWebRtcMessageListener, only()).onEndOfCandidates();
    }

    @Test
    public void testAddWebRtcMessageListenerWhenHandlingWebRtcMessage() {
        SignalingMessageReceiver.WebRtcMessageListener mockedWebRtcMessageListener1 =
            mock(SignalingMessageReceiver.WebRtcMessageListener.class);
        SignalingMessageReceiver.WebRtcMessageListener mockedWebRtcMessageListener2 =
            mock(SignalingMessageReceiver.WebRtcMessageListener.class);

        doAnswer((invocation) -> {
            signalingMessageReceiver.addListener(mockedWebRtcMessageListener2, "theSessionId", "theRoomType");
            return null;
        }).when(mockedWebRtcMessageListener1).onEndOfCandidates();

        signalingMessageReceiver.addListener(mockedWebRtcMessageListener1, "theSessionId", "theRoomType");

        NCSignalingMessage signalingMessage = new NCSignalingMessage();
        signalingMessage.setFrom("theSessionId");
        signalingMessage.setType("endOfCandidates");
        signalingMessage.setRoomType("theRoomType");
        signalingMessageReceiver.processSignalingMessage(signalingMessage);

        verify(mockedWebRtcMessageListener1, only()).onEndOfCandidates();
        verifyNoInteractions(mockedWebRtcMessageListener2);
    }

    @Test
    public void testRemoveWebRtcMessageListenerWhenHandlingWebRtcMessage() {
        SignalingMessageReceiver.WebRtcMessageListener mockedWebRtcMessageListener1 =
            mock(SignalingMessageReceiver.WebRtcMessageListener.class);
        SignalingMessageReceiver.WebRtcMessageListener mockedWebRtcMessageListener2 =
            mock(SignalingMessageReceiver.WebRtcMessageListener.class);

        doAnswer((invocation) -> {
            signalingMessageReceiver.removeListener(mockedWebRtcMessageListener2);
            return null;
        }).when(mockedWebRtcMessageListener1).onEndOfCandidates();

        signalingMessageReceiver.addListener(mockedWebRtcMessageListener1, "theSessionId", "theRoomType");
        signalingMessageReceiver.addListener(mockedWebRtcMessageListener2, "theSessionId", "theRoomType");

        NCSignalingMessage signalingMessage = new NCSignalingMessage();
        signalingMessage.setFrom("theSessionId");
        signalingMessage.setType("endOfCandidates");
        signalingMessage.setRoomType("theRoomType");
        signalingMessageReceiver.processSignalingMessage(signalingMessage);

        InOrder inOrder = inOrder(mockedWebRtcMessageListener1, mockedWebRtcMessageListener2);

        inOrder.verify(mockedWebRtcMessageListener1).onEndOfCandidates();
        inOrder.verify(mockedWebRtcMessageListener2).onEndOfCandidates();
    }
}
