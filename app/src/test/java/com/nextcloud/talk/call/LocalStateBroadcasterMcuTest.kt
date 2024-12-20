/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Daniel Calviño Sánchez <danxuliu@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.call

import com.nextcloud.talk.models.json.signaling.DataChannelMessage
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.TestScheduler
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.times
import java.util.concurrent.TimeUnit

@Suppress("LongMethod")
class LocalStateBroadcasterMcuTest {

    private var localCallParticipantModel: MutableLocalCallParticipantModel? = null
    private var mockedMessageSender: MessageSender? = null

    private var localStateBroadcasterMcu: LocalStateBroadcasterMcu? = null

    @Before
    fun setUp() {
        localCallParticipantModel = MutableLocalCallParticipantModel()
        localCallParticipantModel!!.isAudioEnabled = true
        localCallParticipantModel!!.isSpeaking = true
        localCallParticipantModel!!.isVideoEnabled = true
        mockedMessageSender = Mockito.mock(MessageSender::class.java)
    }

    @Test
    fun testStateSentWithExponentialBackoffWhenParticipantAdded() {
        val testScheduler = TestScheduler()
        RxJavaPlugins.setIoSchedulerHandler { testScheduler }

        localStateBroadcasterMcu = LocalStateBroadcasterMcu(
            localCallParticipantModel,
            mockedMessageSender
        )

        val callParticipantModel = MutableCallParticipantModel("theSessionId")

        localStateBroadcasterMcu!!.handleCallParticipantAdded(callParticipantModel)

        // Sending will be done in another thread, so just adding the participant does not send anything until that
        // other thread could run.
        Mockito.verifyNoInteractions(mockedMessageSender)

        val expectedAudioOn = DataChannelMessage("audioOn")
        val expectedSpeaking = DataChannelMessage("speaking")
        val expectedVideoOn = DataChannelMessage("videoOn")

        testScheduler.advanceTimeBy(0, TimeUnit.SECONDS)

        var messageCount = 1
        Mockito.verify(mockedMessageSender!!, times(messageCount)).sendToAll(expectedAudioOn)
        Mockito.verify(mockedMessageSender!!, times(messageCount)).sendToAll(expectedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(messageCount)).sendToAll(expectedVideoOn)
        Mockito.verifyNoMoreInteractions(mockedMessageSender)

        testScheduler.advanceTimeBy(1, TimeUnit.SECONDS)

        messageCount = 2
        Mockito.verify(mockedMessageSender!!, times(messageCount)).sendToAll(expectedAudioOn)
        Mockito.verify(mockedMessageSender!!, times(messageCount)).sendToAll(expectedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(messageCount)).sendToAll(expectedVideoOn)
        Mockito.verifyNoMoreInteractions(mockedMessageSender)

        testScheduler.advanceTimeBy(2, TimeUnit.SECONDS)

        messageCount = 3
        Mockito.verify(mockedMessageSender!!, times(messageCount)).sendToAll(expectedAudioOn)
        Mockito.verify(mockedMessageSender!!, times(messageCount)).sendToAll(expectedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(messageCount)).sendToAll(expectedVideoOn)
        Mockito.verifyNoMoreInteractions(mockedMessageSender)

        testScheduler.advanceTimeBy(4, TimeUnit.SECONDS)

        messageCount = 4
        Mockito.verify(mockedMessageSender!!, times(messageCount)).sendToAll(expectedAudioOn)
        Mockito.verify(mockedMessageSender!!, times(messageCount)).sendToAll(expectedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(messageCount)).sendToAll(expectedVideoOn)
        Mockito.verifyNoMoreInteractions(mockedMessageSender)

        testScheduler.advanceTimeBy(8, TimeUnit.SECONDS)

        messageCount = 5
        Mockito.verify(mockedMessageSender!!, times(messageCount)).sendToAll(expectedAudioOn)
        Mockito.verify(mockedMessageSender!!, times(messageCount)).sendToAll(expectedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(messageCount)).sendToAll(expectedVideoOn)
        Mockito.verifyNoMoreInteractions(mockedMessageSender)

        testScheduler.advanceTimeBy(16, TimeUnit.SECONDS)

        messageCount = 6
        Mockito.verify(mockedMessageSender!!, times(messageCount)).sendToAll(expectedAudioOn)
        Mockito.verify(mockedMessageSender!!, times(messageCount)).sendToAll(expectedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(messageCount)).sendToAll(expectedVideoOn)
        Mockito.verifyNoMoreInteractions(mockedMessageSender)

        testScheduler.advanceTimeBy(100, TimeUnit.SECONDS)

        Mockito.verifyNoMoreInteractions(mockedMessageSender)
    }

    @Test
    fun testStateSentWithExponentialBackoffIsTheCurrentState() {
        // This test could have been included in "testStateSentWithExponentialBackoffWhenParticipantAdded", but was
        // kept separate for clarity.

        val testScheduler = TestScheduler()
        RxJavaPlugins.setIoSchedulerHandler { testScheduler }

        localStateBroadcasterMcu = LocalStateBroadcasterMcu(
            localCallParticipantModel,
            mockedMessageSender
        )

        val callParticipantModel = MutableCallParticipantModel("theSessionId")

        localStateBroadcasterMcu!!.handleCallParticipantAdded(callParticipantModel)

        // Sending will be done in another thread, so just adding the participant does not send anything until that
        // other thread could run.
        Mockito.verifyNoInteractions(mockedMessageSender)

        val expectedAudioOn = DataChannelMessage("audioOn")
        val expectedSpeaking = DataChannelMessage("speaking")
        val expectedVideoOn = DataChannelMessage("videoOn")

        testScheduler.advanceTimeBy(0, TimeUnit.SECONDS)

        Mockito.verify(mockedMessageSender!!, times(1)).sendToAll(expectedAudioOn)
        Mockito.verify(mockedMessageSender!!, times(1)).sendToAll(expectedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(1)).sendToAll(expectedVideoOn)
        Mockito.verifyNoMoreInteractions(mockedMessageSender)

        localCallParticipantModel!!.isSpeaking = false

        val expectedStoppedSpeaking = DataChannelMessage("stoppedSpeaking")

        // Changing the state causes the normal state update to be sent, independently of the initial state
        Mockito.verify(mockedMessageSender!!, times(1)).sendToAll(expectedStoppedSpeaking)

        testScheduler.advanceTimeBy(1, TimeUnit.SECONDS)

        Mockito.verify(mockedMessageSender!!, times(2)).sendToAll(expectedAudioOn)
        Mockito.verify(mockedMessageSender!!, times(2)).sendToAll(expectedStoppedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(2)).sendToAll(expectedVideoOn)
        Mockito.verifyNoMoreInteractions(mockedMessageSender)

        localCallParticipantModel!!.isAudioEnabled = false

        val expectedAudioOff = DataChannelMessage("audioOff")

        // Changing the state causes the normal state update to be sent, independently of the initial state
        Mockito.verify(mockedMessageSender!!, times(1)).sendToAll(expectedAudioOff)

        testScheduler.advanceTimeBy(2, TimeUnit.SECONDS)

        Mockito.verify(mockedMessageSender!!, times(2)).sendToAll(expectedAudioOff)
        Mockito.verify(mockedMessageSender!!, times(3)).sendToAll(expectedStoppedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(3)).sendToAll(expectedVideoOn)
        Mockito.verifyNoMoreInteractions(mockedMessageSender)

        localCallParticipantModel!!.isVideoEnabled = false

        val expectedVideoOff = DataChannelMessage("videoOff")

        // Changing the state causes the normal state update to be sent, independently of the initial state
        Mockito.verify(mockedMessageSender!!, times(1)).sendToAll(expectedVideoOff)

        testScheduler.advanceTimeBy(4, TimeUnit.SECONDS)

        Mockito.verify(mockedMessageSender!!, times(3)).sendToAll(expectedAudioOff)
        Mockito.verify(mockedMessageSender!!, times(4)).sendToAll(expectedStoppedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(2)).sendToAll(expectedVideoOff)
        Mockito.verifyNoMoreInteractions(mockedMessageSender)

        localCallParticipantModel!!.isVideoEnabled = true

        // Changing the state causes the normal state update to be sent, independently of the initial state
        Mockito.verify(mockedMessageSender!!, times(4)).sendToAll(expectedVideoOn)

        testScheduler.advanceTimeBy(8, TimeUnit.SECONDS)

        Mockito.verify(mockedMessageSender!!, times(4)).sendToAll(expectedAudioOff)
        Mockito.verify(mockedMessageSender!!, times(5)).sendToAll(expectedStoppedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(5)).sendToAll(expectedVideoOn)
        Mockito.verifyNoMoreInteractions(mockedMessageSender)
    }

    @Test
    fun testStateSentWithExponentialBackoffRestartedWhenAnotherParticipantAdded() {
        val testScheduler = TestScheduler()
        RxJavaPlugins.setIoSchedulerHandler { testScheduler }

        localStateBroadcasterMcu = LocalStateBroadcasterMcu(
            localCallParticipantModel,
            mockedMessageSender
        )

        val callParticipantModel = MutableCallParticipantModel("theSessionId")

        localStateBroadcasterMcu!!.handleCallParticipantAdded(callParticipantModel)

        // Sending will be done in another thread, so just adding the participant does not send anything until that
        // other thread could run.
        Mockito.verifyNoInteractions(mockedMessageSender)

        val expectedAudioOn = DataChannelMessage("audioOn")
        val expectedSpeaking = DataChannelMessage("speaking")
        val expectedVideoOn = DataChannelMessage("videoOn")

        testScheduler.advanceTimeBy(0, TimeUnit.SECONDS)

        var dataChannelMessageCount = 1
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedAudioOn)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedVideoOn)
        Mockito.verifyNoMoreInteractions(mockedMessageSender)

        testScheduler.advanceTimeBy(1, TimeUnit.SECONDS)

        dataChannelMessageCount = 2
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedAudioOn)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedVideoOn)
        Mockito.verifyNoMoreInteractions(mockedMessageSender)

        testScheduler.advanceTimeBy(2, TimeUnit.SECONDS)

        dataChannelMessageCount = 3
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedAudioOn)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedVideoOn)
        Mockito.verifyNoMoreInteractions(mockedMessageSender)

        testScheduler.advanceTimeBy(4, TimeUnit.SECONDS)

        dataChannelMessageCount = 4
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedAudioOn)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedVideoOn)
        Mockito.verifyNoMoreInteractions(mockedMessageSender)

        val callParticipantModel2 = MutableCallParticipantModel("theSessionId2")

        localStateBroadcasterMcu!!.handleCallParticipantAdded(callParticipantModel2)

        testScheduler.advanceTimeBy(0, TimeUnit.SECONDS)

        dataChannelMessageCount = 5
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedAudioOn)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedVideoOn)
        Mockito.verifyNoMoreInteractions(mockedMessageSender)

        testScheduler.advanceTimeBy(1, TimeUnit.SECONDS)

        dataChannelMessageCount = 6
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedAudioOn)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedVideoOn)
        Mockito.verifyNoMoreInteractions(mockedMessageSender)

        testScheduler.advanceTimeBy(2, TimeUnit.SECONDS)

        dataChannelMessageCount = 7
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedAudioOn)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedVideoOn)
        Mockito.verifyNoMoreInteractions(mockedMessageSender)

        testScheduler.advanceTimeBy(4, TimeUnit.SECONDS)

        dataChannelMessageCount = 8
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedAudioOn)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedVideoOn)
        Mockito.verifyNoMoreInteractions(mockedMessageSender)

        testScheduler.advanceTimeBy(8, TimeUnit.SECONDS)

        dataChannelMessageCount = 9
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedAudioOn)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedVideoOn)
        Mockito.verifyNoMoreInteractions(mockedMessageSender)

        testScheduler.advanceTimeBy(16, TimeUnit.SECONDS)

        dataChannelMessageCount = 10
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedAudioOn)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedVideoOn)
        Mockito.verifyNoMoreInteractions(mockedMessageSender)

        testScheduler.advanceTimeBy(100, TimeUnit.SECONDS)

        Mockito.verifyNoMoreInteractions(mockedMessageSender)
    }

    @Test
    fun testStateStillSentWithExponentialBackoffWhenParticipantRemoved() {
        // For simplicity the exponential backoff is not aborted when the participant that triggered it is removed.

        val testScheduler = TestScheduler()
        RxJavaPlugins.setIoSchedulerHandler { testScheduler }

        localStateBroadcasterMcu = LocalStateBroadcasterMcu(
            localCallParticipantModel,
            mockedMessageSender
        )

        val callParticipantModel = MutableCallParticipantModel("theSessionId")

        localStateBroadcasterMcu!!.handleCallParticipantAdded(callParticipantModel)

        // Sending will be done in another thread, so just adding the participant does not send anything until that
        // other thread could run.
        Mockito.verifyNoInteractions(mockedMessageSender)

        val expectedAudioOn = DataChannelMessage("audioOn")
        val expectedSpeaking = DataChannelMessage("speaking")
        val expectedVideoOn = DataChannelMessage("videoOn")

        testScheduler.advanceTimeBy(0, TimeUnit.SECONDS)

        var dataChannelMessageCount = 1
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedAudioOn)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedVideoOn)
        Mockito.verifyNoMoreInteractions(mockedMessageSender)

        testScheduler.advanceTimeBy(1, TimeUnit.SECONDS)

        dataChannelMessageCount = 2
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedAudioOn)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedVideoOn)
        Mockito.verifyNoMoreInteractions(mockedMessageSender)

        testScheduler.advanceTimeBy(2, TimeUnit.SECONDS)

        dataChannelMessageCount = 3
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedAudioOn)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedVideoOn)
        Mockito.verifyNoMoreInteractions(mockedMessageSender)

        testScheduler.advanceTimeBy(4, TimeUnit.SECONDS)

        dataChannelMessageCount = 4
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedAudioOn)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedVideoOn)
        Mockito.verifyNoMoreInteractions(mockedMessageSender)

        localStateBroadcasterMcu!!.handleCallParticipantRemoved(callParticipantModel)

        testScheduler.advanceTimeBy(8, TimeUnit.SECONDS)

        dataChannelMessageCount = 5
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedAudioOn)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedVideoOn)
        Mockito.verifyNoMoreInteractions(mockedMessageSender)

        testScheduler.advanceTimeBy(16, TimeUnit.SECONDS)

        dataChannelMessageCount = 6
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedAudioOn)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedVideoOn)
        Mockito.verifyNoMoreInteractions(mockedMessageSender)

        testScheduler.advanceTimeBy(100, TimeUnit.SECONDS)

        Mockito.verifyNoMoreInteractions(mockedMessageSender)
    }

    @Test
    fun testStateNoLongerSentOnceDestroyed() {
        val testScheduler = TestScheduler()
        RxJavaPlugins.setIoSchedulerHandler { testScheduler }

        localStateBroadcasterMcu = LocalStateBroadcasterMcu(
            localCallParticipantModel,
            mockedMessageSender
        )

        val callParticipantModel = MutableCallParticipantModel("theSessionId")

        localStateBroadcasterMcu!!.handleCallParticipantAdded(callParticipantModel)

        // Sending will be done in another thread, so just adding the participant does not send anything until that
        // other thread could run.
        Mockito.verifyNoInteractions(mockedMessageSender)

        val expectedAudioOn = DataChannelMessage("audioOn")
        val expectedSpeaking = DataChannelMessage("speaking")
        val expectedVideoOn = DataChannelMessage("videoOn")

        testScheduler.advanceTimeBy(0, TimeUnit.SECONDS)

        var messageCount = 1
        Mockito.verify(mockedMessageSender!!, times(messageCount)).sendToAll(expectedAudioOn)
        Mockito.verify(mockedMessageSender!!, times(messageCount)).sendToAll(expectedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(messageCount)).sendToAll(expectedVideoOn)
        Mockito.verifyNoMoreInteractions(mockedMessageSender)

        testScheduler.advanceTimeBy(1, TimeUnit.SECONDS)

        messageCount = 2
        Mockito.verify(mockedMessageSender!!, times(messageCount)).sendToAll(expectedAudioOn)
        Mockito.verify(mockedMessageSender!!, times(messageCount)).sendToAll(expectedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(messageCount)).sendToAll(expectedVideoOn)
        Mockito.verifyNoMoreInteractions(mockedMessageSender)

        testScheduler.advanceTimeBy(2, TimeUnit.SECONDS)

        messageCount = 3
        Mockito.verify(mockedMessageSender!!, times(messageCount)).sendToAll(expectedAudioOn)
        Mockito.verify(mockedMessageSender!!, times(messageCount)).sendToAll(expectedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(messageCount)).sendToAll(expectedVideoOn)
        Mockito.verifyNoMoreInteractions(mockedMessageSender)

        localStateBroadcasterMcu!!.destroy()

        testScheduler.advanceTimeBy(100, TimeUnit.SECONDS)

        Mockito.verifyNoMoreInteractions(mockedMessageSender)
    }
}
