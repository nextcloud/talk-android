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

public class SignalingMessageReceiverOfferTest {

    private SignalingMessageReceiver signalingMessageReceiver;

    @Before
    public void setUp() {
        // SignalingMessageReceiver is abstract to prevent direct instantiation without calling the appropriate
        // protected methods.
        signalingMessageReceiver = new SignalingMessageReceiver() {
        };
    }

    @Test
    public void testAddOfferMessageListenerWithNullListener() {
        Assert.assertThrows(IllegalArgumentException.class, () -> {
            signalingMessageReceiver.addListener((SignalingMessageReceiver.OfferMessageListener) null);
        });
    }

    @Test
    public void testOfferMessage() {
        SignalingMessageReceiver.OfferMessageListener mockedOfferMessageListener =
            mock(SignalingMessageReceiver.OfferMessageListener.class);

        signalingMessageReceiver.addListener(mockedOfferMessageListener);

        NCSignalingMessage signalingMessage = new NCSignalingMessage();
        signalingMessage.setFrom("theSessionId");
        signalingMessage.setType("offer");
        signalingMessage.setRoomType("theRoomType");
        NCMessagePayload messagePayload = new NCMessagePayload();
        messagePayload.setType("offer");
        messagePayload.setSdp("theSdp");
        signalingMessage.setPayload(messagePayload);
        signalingMessageReceiver.processSignalingMessage(signalingMessage);

        verify(mockedOfferMessageListener, only()).onOffer("theSessionId", "theRoomType", "theSdp", null);
    }

    @Test
    public void testOfferMessageWithNick() {
        SignalingMessageReceiver.OfferMessageListener mockedOfferMessageListener =
            mock(SignalingMessageReceiver.OfferMessageListener.class);

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
        signalingMessageReceiver.processSignalingMessage(signalingMessage);

        verify(mockedOfferMessageListener, only()).onOffer("theSessionId", "theRoomType", "theSdp", "theNick");
    }

    @Test
    public void testOfferMessageAfterRemovingListener() {
        SignalingMessageReceiver.OfferMessageListener mockedOfferMessageListener =
            mock(SignalingMessageReceiver.OfferMessageListener.class);

        signalingMessageReceiver.addListener(mockedOfferMessageListener);
        signalingMessageReceiver.removeListener(mockedOfferMessageListener);

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

        verifyNoInteractions(mockedOfferMessageListener);
    }

    @Test
    public void testOfferMessageAfterRemovingSingleListenerOfSeveral() {
        SignalingMessageReceiver.OfferMessageListener mockedOfferMessageListener1 =
            mock(SignalingMessageReceiver.OfferMessageListener.class);
        SignalingMessageReceiver.OfferMessageListener mockedOfferMessageListener2 =
            mock(SignalingMessageReceiver.OfferMessageListener.class);
        SignalingMessageReceiver.OfferMessageListener mockedOfferMessageListener3 =
            mock(SignalingMessageReceiver.OfferMessageListener.class);

        signalingMessageReceiver.addListener(mockedOfferMessageListener1);
        signalingMessageReceiver.addListener(mockedOfferMessageListener2);
        signalingMessageReceiver.addListener(mockedOfferMessageListener3);
        signalingMessageReceiver.removeListener(mockedOfferMessageListener2);

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

        verify(mockedOfferMessageListener1, only()).onOffer("theSessionId", "theRoomType", "theSdp", "theNick");
        verify(mockedOfferMessageListener3, only()).onOffer("theSessionId", "theRoomType", "theSdp", "theNick");
        verifyNoInteractions(mockedOfferMessageListener2);
    }

    @Test
    public void testOfferMessageAfterAddingListenerAgain() {
        SignalingMessageReceiver.OfferMessageListener mockedOfferMessageListener =
            mock(SignalingMessageReceiver.OfferMessageListener.class);

        signalingMessageReceiver.addListener(mockedOfferMessageListener);
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
        signalingMessageReceiver.processSignalingMessage(signalingMessage);

        verify(mockedOfferMessageListener, only()).onOffer("theSessionId", "theRoomType", "theSdp", "theNick");
    }

    @Test
    public void testAddOfferMessageListenerWhenHandlingOffer() {
        SignalingMessageReceiver.OfferMessageListener mockedOfferMessageListener1 =
            mock(SignalingMessageReceiver.OfferMessageListener.class);
        SignalingMessageReceiver.OfferMessageListener mockedOfferMessageListener2 =
            mock(SignalingMessageReceiver.OfferMessageListener.class);

        doAnswer((invocation) -> {
            signalingMessageReceiver.addListener(mockedOfferMessageListener2);
            return null;
        }).when(mockedOfferMessageListener1).onOffer("theSessionId", "theRoomType", "theSdp", "theNick");

        signalingMessageReceiver.addListener(mockedOfferMessageListener1);

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

        verify(mockedOfferMessageListener1, only()).onOffer("theSessionId", "theRoomType", "theSdp", "theNick");
        verifyNoInteractions(mockedOfferMessageListener2);
    }

    @Test
    public void testRemoveOfferMessageListenerWhenHandlingOffer() {
        SignalingMessageReceiver.OfferMessageListener mockedOfferMessageListener1 =
            mock(SignalingMessageReceiver.OfferMessageListener.class);
        SignalingMessageReceiver.OfferMessageListener mockedOfferMessageListener2 =
            mock(SignalingMessageReceiver.OfferMessageListener.class);

        doAnswer((invocation) -> {
            signalingMessageReceiver.removeListener(mockedOfferMessageListener2);
            return null;
        }).when(mockedOfferMessageListener1).onOffer("theSessionId", "theRoomType", "theSdp", "theNick");

        signalingMessageReceiver.addListener(mockedOfferMessageListener1);
        signalingMessageReceiver.addListener(mockedOfferMessageListener2);

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

        InOrder inOrder = inOrder(mockedOfferMessageListener1, mockedOfferMessageListener2);

        inOrder.verify(mockedOfferMessageListener1).onOffer("theSessionId", "theRoomType", "theSdp", "theNick");
        inOrder.verify(mockedOfferMessageListener2).onOffer("theSessionId", "theRoomType", "theSdp", "theNick");
    }
}
