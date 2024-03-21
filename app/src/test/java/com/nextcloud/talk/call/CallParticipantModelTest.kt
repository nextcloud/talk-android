/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Daniel Calviño Sánchez <danxuliu@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
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
