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

import com.nextcloud.talk.models.json.signaling.NCMessagePayload;
import com.nextcloud.talk.models.json.signaling.NCSignalingMessage;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

public class SignalingMessageReceiverTest {

    private SignalingMessageReceiver signalingMessageReceiver;

    @Before
    public void setUp() {
        // SignalingMessageReceiver is abstract to prevent direct instantiation without calling the appropriate
        // protected methods.
        signalingMessageReceiver = new SignalingMessageReceiver() {
        };
    }

    @Test
    public void testOfferWithOfferAndWebRtcMessageListeners() {
        SignalingMessageReceiver.OfferMessageListener mockedOfferMessageListener =
            mock(SignalingMessageReceiver.OfferMessageListener.class);
        SignalingMessageReceiver.WebRtcMessageListener mockedWebRtcMessageListener =
            mock(SignalingMessageReceiver.WebRtcMessageListener.class);

        signalingMessageReceiver.addListener(mockedOfferMessageListener);
        signalingMessageReceiver.addListener(mockedWebRtcMessageListener, "theSessionId", "theRoomType", "theSid");

        NCSignalingMessage signalingMessage = new NCSignalingMessage();
        signalingMessage.setFrom("theSessionId");
        signalingMessage.setType("offer");
        signalingMessage.setRoomType("theRoomType");
        NCMessagePayload messagePayload = new NCMessagePayload();
        messagePayload.setType("offer");
        messagePayload.setSdp("theSdp");
        messagePayload.setNick("theNick");
        signalingMessage.setPayload(messagePayload);
        signalingMessage.setSid("theSid");
        signalingMessageReceiver.processSignalingMessage(signalingMessage);

        InOrder inOrder = inOrder(mockedOfferMessageListener, mockedWebRtcMessageListener);

        inOrder.verify(mockedOfferMessageListener).onOffer("theSessionId", "theRoomType", "theSid", "theSdp", "theNick");
        inOrder.verify(mockedWebRtcMessageListener).onOffer("theSdp", "theNick");
    }

    @Test
    public void testAddWebRtcMessageListenerWhenHandlingOffer() {
        SignalingMessageReceiver.OfferMessageListener mockedOfferMessageListener =
            mock(SignalingMessageReceiver.OfferMessageListener.class);
        SignalingMessageReceiver.WebRtcMessageListener mockedWebRtcMessageListener =
            mock(SignalingMessageReceiver.WebRtcMessageListener.class);

        doAnswer((invocation) -> {
            signalingMessageReceiver.addListener(mockedWebRtcMessageListener, "theSessionId", "theRoomType", "theSid");
            return null;
        }).when(mockedOfferMessageListener).onOffer("theSessionId", "theRoomType", "theSid", "theSdp", "theNick");

        signalingMessageReceiver.addListener(mockedOfferMessageListener);

        NCSignalingMessage signalingMessage = new NCSignalingMessage();
        signalingMessage.setFrom("theSessionId");
        signalingMessage.setType("offer");
        signalingMessage.setRoomType("theRoomType");
        NCMessagePayload messagePayload = new NCMessagePayload();
        messagePayload.setType("offer");
        messagePayload.setSdp("theSdp");
        messagePayload.setNick("theNick");
        signalingMessage.setPayload(messagePayload);
        signalingMessage.setSid("theSid");
        signalingMessageReceiver.processSignalingMessage(signalingMessage);

        InOrder inOrder = inOrder(mockedOfferMessageListener, mockedWebRtcMessageListener);

        inOrder.verify(mockedOfferMessageListener).onOffer("theSessionId", "theRoomType", "theSid", "theSdp", "theNick");
        inOrder.verify(mockedWebRtcMessageListener).onOffer("theSdp", "theNick");
    }

    @Test
    public void testRemoveWebRtcMessageListenerWhenHandlingOffer() {
        SignalingMessageReceiver.OfferMessageListener mockedOfferMessageListener =
            mock(SignalingMessageReceiver.OfferMessageListener.class);
        SignalingMessageReceiver.WebRtcMessageListener mockedWebRtcMessageListener =
            mock(SignalingMessageReceiver.WebRtcMessageListener.class);

        doAnswer((invocation) -> {
            signalingMessageReceiver.removeListener(mockedWebRtcMessageListener);
            return null;
        }).when(mockedOfferMessageListener).onOffer("theSessionId", "theRoomType", "theSid", "theSdp", "theNick");

        signalingMessageReceiver.addListener(mockedOfferMessageListener);
        signalingMessageReceiver.addListener(mockedWebRtcMessageListener, "theSessionId", "theRoomType", "theSid");

        NCSignalingMessage signalingMessage = new NCSignalingMessage();
        signalingMessage.setFrom("theSessionId");
        signalingMessage.setType("offer");
        signalingMessage.setRoomType("theRoomType");
        NCMessagePayload messagePayload = new NCMessagePayload();
        messagePayload.setType("offer");
        messagePayload.setSdp("theSdp");
        messagePayload.setNick("theNick");
        signalingMessage.setPayload(messagePayload);
        signalingMessage.setSid("theSid");
        signalingMessageReceiver.processSignalingMessage(signalingMessage);

        verify(mockedOfferMessageListener, only()).onOffer("theSessionId", "theRoomType", "theSid", "theSdp", "theNick");
        verifyNoInteractions(mockedWebRtcMessageListener);
    }
}
