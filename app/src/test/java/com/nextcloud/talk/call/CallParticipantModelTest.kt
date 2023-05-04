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
package com.nextcloud.talk.call

import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

class CallParticipantModelTest {
    private var callParticipantModel: MutableCallParticipantModel? = null
    private var mockedCallParticipantModelObserver: CallParticipantModel.Observer? = null

    @Before
    fun setUp() {
        callParticipantModel = MutableCallParticipantModel("theSessionId")
        mockedCallParticipantModelObserver = Mockito.mock(CallParticipantModel.Observer::class.java)
    }

    @Test
    fun testSetRaisedHand() {
        callParticipantModel!!.addObserver(mockedCallParticipantModelObserver)
        callParticipantModel!!.setRaisedHand(true, 4815162342L)
        Mockito.verify(mockedCallParticipantModelObserver, Mockito.only())?.onChange()
    }

    @Test
    fun testSetRaisedHandTwice() {
        callParticipantModel!!.addObserver(mockedCallParticipantModelObserver)
        callParticipantModel!!.setRaisedHand(true, 4815162342L)
        callParticipantModel!!.setRaisedHand(false, 4815162342108L)
        Mockito.verify(mockedCallParticipantModelObserver, Mockito.times(2))?.onChange()
    }

    @Test
    fun testSetRaisedHandTwiceWithSameValue() {
        callParticipantModel!!.addObserver(mockedCallParticipantModelObserver)
        callParticipantModel!!.setRaisedHand(true, 4815162342L)
        callParticipantModel!!.setRaisedHand(true, 4815162342L)
        Mockito.verify(mockedCallParticipantModelObserver, Mockito.only())?.onChange()
    }

    @Test
    fun testEmitReaction() {
        callParticipantModel!!.addObserver(mockedCallParticipantModelObserver)
        callParticipantModel!!.emitReaction("theReaction")
        Mockito.verify(mockedCallParticipantModelObserver, Mockito.only())?.onReaction("theReaction")
    }
}
