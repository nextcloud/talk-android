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
