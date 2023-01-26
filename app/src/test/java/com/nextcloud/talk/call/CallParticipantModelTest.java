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

import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class CallParticipantModelTest {

    private MutableCallParticipantModel callParticipantModel;

    private CallParticipantModel.Observer mockedCallParticipantModelObserver;

    @Before
    public void setUp() {
        callParticipantModel = new MutableCallParticipantModel("theSessionId");

        mockedCallParticipantModelObserver = mock(CallParticipantModel.Observer.class);
    }

    @Test
    public void testSetRaisedHand() {
        callParticipantModel.addObserver(mockedCallParticipantModelObserver);

        callParticipantModel.setRaisedHand(true, 4815162342L);

        verify(mockedCallParticipantModelObserver, only()).onChange();
    }

    @Test
    public void testSetRaisedHandTwice() {
        callParticipantModel.addObserver(mockedCallParticipantModelObserver);

        callParticipantModel.setRaisedHand(true, 4815162342L);
        callParticipantModel.setRaisedHand(false, 4815162342108L);

        verify(mockedCallParticipantModelObserver, times(2)).onChange();
    }

    @Test
    public void testSetRaisedHandTwiceWithSameValue() {
        callParticipantModel.addObserver(mockedCallParticipantModelObserver);

        callParticipantModel.setRaisedHand(true, 4815162342L);
        callParticipantModel.setRaisedHand(true, 4815162342L);

        verify(mockedCallParticipantModelObserver, only()).onChange();
    }
}
