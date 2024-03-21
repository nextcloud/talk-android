/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Daniel Calviño Sánchez <danxuliu@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.call;

import com.nextcloud.talk.signaling.SignalingMessageReceiver;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class CallParticipantListTest {

    private SignalingMessageReceiver mockedSignalingMessageReceiver;

    private CallParticipantList callParticipantList;
    private SignalingMessageReceiver.ParticipantListMessageListener participantListMessageListener;

    @Before
    public void setUp() {
        mockedSignalingMessageReceiver = mock(SignalingMessageReceiver.class);

        callParticipantList = new CallParticipantList(mockedSignalingMessageReceiver);

        // Get internal ParticipantListMessageListener from callParticipantList set in the
        // mockedSignalingMessageReceiver.
        ArgumentCaptor<SignalingMessageReceiver.ParticipantListMessageListener> participantListMessageListenerArgumentCaptor =
            ArgumentCaptor.forClass(SignalingMessageReceiver.ParticipantListMessageListener.class);

        verify(mockedSignalingMessageReceiver).addListener(participantListMessageListenerArgumentCaptor.capture());

        participantListMessageListener = participantListMessageListenerArgumentCaptor.getValue();
    }

    @Test
    public void testDestroy() {
        callParticipantList.destroy();

        verify(mockedSignalingMessageReceiver).removeListener(participantListMessageListener);
        verifyNoMoreInteractions(mockedSignalingMessageReceiver);
    }
}
