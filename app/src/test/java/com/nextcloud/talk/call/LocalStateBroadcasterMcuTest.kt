/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Daniel Calviño Sánchez <danxuliu@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.call

import com.nextcloud.talk.models.json.signaling.DataChannelMessage
import com.nextcloud.talk.models.json.signaling.NCMessagePayload
import com.nextcloud.talk.models.json.signaling.NCSignalingMessage
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

    private fun getExpectedUnmuteAudio(): NCSignalingMessage {
        val expectedUnmuteAudio = NCSignalingMessage()
        expectedUnmuteAudio.roomType = "video"
        expectedUnmuteAudio.type = "unmute"

        val payload = NCMessagePayload()
        payload.name = "audio"
        expectedUnmuteAudio.payload = payload

        return expectedUnmuteAudio
    }

    private fun getExpectedMuteAudio(): NCSignalingMessage {
        val expectedMuteAudio = NCSignalingMessage()
        expectedMuteAudio.roomType = "video"
        expectedMuteAudio.type = "mute"

        val payload = NCMessagePayload()
        payload.name = "audio"
        expectedMuteAudio.payload = payload

        return expectedMuteAudio
    }

    private fun getExpectedUnmuteVideo(): NCSignalingMessage {
        val expectedUnmuteVideo = NCSignalingMessage()
        expectedUnmuteVideo.roomType = "video"
        expectedUnmuteVideo.type = "unmute"

        val payload = NCMessagePayload()
        payload.name = "video"
        expectedUnmuteVideo.payload = payload

        return expectedUnmuteVideo
    }

    private fun getExpectedMuteVideo(): NCSignalingMessage {
        val expectedMuteVideo = NCSignalingMessage()
        expectedMuteVideo.roomType = "video"
        expectedMuteVideo.type = "mute"

        val payload = NCMessagePayload()
        payload.name = "video"
        expectedMuteVideo.payload = payload

        return expectedMuteVideo
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

        val expectedUnmuteAudio = getExpectedUnmuteAudio()
        val expectedUnmuteVideo = getExpectedUnmuteVideo()

        testScheduler.advanceTimeBy(0, TimeUnit.SECONDS)

        var messageCount = 1
        Mockito.verify(mockedMessageSender!!, times(messageCount)).sendToAll(expectedAudioOn)
        Mockito.verify(mockedMessageSender!!, times(messageCount)).sendToAll(expectedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(messageCount)).sendToAll(expectedVideoOn)
        Mockito.verify(mockedMessageSender!!, times(messageCount)).send(expectedUnmuteAudio, "theSessionId")
        Mockito.verify(mockedMessageSender!!, times(messageCount)).send(expectedUnmuteVideo, "theSessionId")
        Mockito.verifyNoMoreInteractions(mockedMessageSender)

        testScheduler.advanceTimeBy(1, TimeUnit.SECONDS)

        messageCount = 2
        Mockito.verify(mockedMessageSender!!, times(messageCount)).sendToAll(expectedAudioOn)
        Mockito.verify(mockedMessageSender!!, times(messageCount)).sendToAll(expectedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(messageCount)).sendToAll(expectedVideoOn)
        Mockito.verify(mockedMessageSender!!, times(messageCount)).send(expectedUnmuteAudio, "theSessionId")
        Mockito.verify(mockedMessageSender!!, times(messageCount)).send(expectedUnmuteVideo, "theSessionId")
        Mockito.verifyNoMoreInteractions(mockedMessageSender)

        testScheduler.advanceTimeBy(2, TimeUnit.SECONDS)

        messageCount = 3
        Mockito.verify(mockedMessageSender!!, times(messageCount)).sendToAll(expectedAudioOn)
        Mockito.verify(mockedMessageSender!!, times(messageCount)).sendToAll(expectedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(messageCount)).sendToAll(expectedVideoOn)
        Mockito.verify(mockedMessageSender!!, times(messageCount)).send(expectedUnmuteAudio, "theSessionId")
        Mockito.verify(mockedMessageSender!!, times(messageCount)).send(expectedUnmuteVideo, "theSessionId")
        Mockito.verifyNoMoreInteractions(mockedMessageSender)

        testScheduler.advanceTimeBy(4, TimeUnit.SECONDS)

        messageCount = 4
        Mockito.verify(mockedMessageSender!!, times(messageCount)).sendToAll(expectedAudioOn)
        Mockito.verify(mockedMessageSender!!, times(messageCount)).sendToAll(expectedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(messageCount)).sendToAll(expectedVideoOn)
        Mockito.verify(mockedMessageSender!!, times(messageCount)).send(expectedUnmuteAudio, "theSessionId")
        Mockito.verify(mockedMessageSender!!, times(messageCount)).send(expectedUnmuteVideo, "theSessionId")
        Mockito.verifyNoMoreInteractions(mockedMessageSender)

        testScheduler.advanceTimeBy(8, TimeUnit.SECONDS)

        messageCount = 5
        Mockito.verify(mockedMessageSender!!, times(messageCount)).sendToAll(expectedAudioOn)
        Mockito.verify(mockedMessageSender!!, times(messageCount)).sendToAll(expectedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(messageCount)).sendToAll(expectedVideoOn)
        Mockito.verify(mockedMessageSender!!, times(messageCount)).send(expectedUnmuteAudio, "theSessionId")
        Mockito.verify(mockedMessageSender!!, times(messageCount)).send(expectedUnmuteVideo, "theSessionId")
        Mockito.verifyNoMoreInteractions(mockedMessageSender)

        testScheduler.advanceTimeBy(16, TimeUnit.SECONDS)

        messageCount = 6
        Mockito.verify(mockedMessageSender!!, times(messageCount)).sendToAll(expectedAudioOn)
        Mockito.verify(mockedMessageSender!!, times(messageCount)).sendToAll(expectedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(messageCount)).sendToAll(expectedVideoOn)
        Mockito.verify(mockedMessageSender!!, times(messageCount)).send(expectedUnmuteAudio, "theSessionId")
        Mockito.verify(mockedMessageSender!!, times(messageCount)).send(expectedUnmuteVideo, "theSessionId")
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

        val expectedUnmuteAudio = getExpectedUnmuteAudio()
        val expectedUnmuteVideo = getExpectedUnmuteVideo()

        testScheduler.advanceTimeBy(0, TimeUnit.SECONDS)

        Mockito.verify(mockedMessageSender!!, times(1)).sendToAll(expectedAudioOn)
        Mockito.verify(mockedMessageSender!!, times(1)).sendToAll(expectedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(1)).sendToAll(expectedVideoOn)
        Mockito.verify(mockedMessageSender!!, times(1)).send(expectedUnmuteAudio, "theSessionId")
        Mockito.verify(mockedMessageSender!!, times(1)).send(expectedUnmuteVideo, "theSessionId")
        Mockito.verifyNoMoreInteractions(mockedMessageSender)

        localCallParticipantModel!!.isSpeaking = false

        val expectedStoppedSpeaking = DataChannelMessage("stoppedSpeaking")

        // Changing the state causes the normal state update to be sent, independently of the initial state
        Mockito.verify(mockedMessageSender!!, times(1)).sendToAll(expectedStoppedSpeaking)

        testScheduler.advanceTimeBy(1, TimeUnit.SECONDS)

        Mockito.verify(mockedMessageSender!!, times(2)).sendToAll(expectedAudioOn)
        Mockito.verify(mockedMessageSender!!, times(2)).sendToAll(expectedStoppedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(2)).sendToAll(expectedVideoOn)
        Mockito.verify(mockedMessageSender!!, times(2)).send(expectedUnmuteAudio, "theSessionId")
        Mockito.verify(mockedMessageSender!!, times(2)).send(expectedUnmuteVideo, "theSessionId")
        Mockito.verifyNoMoreInteractions(mockedMessageSender)

        localCallParticipantModel!!.isAudioEnabled = false

        val expectedAudioOff = DataChannelMessage("audioOff")
        val expectedMuteAudio = getExpectedMuteAudio()

        // Changing the state causes the normal state update to be sent, independently of the initial state
        Mockito.verify(mockedMessageSender!!, times(1)).sendToAll(expectedAudioOff)
        Mockito.verify(mockedMessageSender!!, times(1)).sendToAll(expectedMuteAudio)

        testScheduler.advanceTimeBy(2, TimeUnit.SECONDS)

        Mockito.verify(mockedMessageSender!!, times(2)).sendToAll(expectedAudioOff)
        Mockito.verify(mockedMessageSender!!, times(3)).sendToAll(expectedStoppedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(3)).sendToAll(expectedVideoOn)
        Mockito.verify(mockedMessageSender!!, times(1)).sendToAll(expectedMuteAudio)
        Mockito.verify(mockedMessageSender!!, times(1)).send(expectedMuteAudio, "theSessionId")
        Mockito.verify(mockedMessageSender!!, times(3)).send(expectedUnmuteVideo, "theSessionId")
        Mockito.verifyNoMoreInteractions(mockedMessageSender)

        localCallParticipantModel!!.isVideoEnabled = false

        val expectedVideoOff = DataChannelMessage("videoOff")
        val expectedMuteVideo = getExpectedMuteVideo()

        // Changing the state causes the normal state update to be sent, independently of the initial state
        Mockito.verify(mockedMessageSender!!, times(1)).sendToAll(expectedVideoOff)
        Mockito.verify(mockedMessageSender!!, times(1)).sendToAll(expectedMuteVideo)

        testScheduler.advanceTimeBy(4, TimeUnit.SECONDS)

        Mockito.verify(mockedMessageSender!!, times(3)).sendToAll(expectedAudioOff)
        Mockito.verify(mockedMessageSender!!, times(4)).sendToAll(expectedStoppedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(2)).sendToAll(expectedVideoOff)
        Mockito.verify(mockedMessageSender!!, times(1)).sendToAll(expectedMuteAudio)
        Mockito.verify(mockedMessageSender!!, times(1)).sendToAll(expectedMuteVideo)
        Mockito.verify(mockedMessageSender!!, times(2)).send(expectedMuteAudio, "theSessionId")
        Mockito.verify(mockedMessageSender!!, times(1)).send(expectedMuteVideo, "theSessionId")
        Mockito.verifyNoMoreInteractions(mockedMessageSender)

        localCallParticipantModel!!.isVideoEnabled = true

        // Changing the state causes the normal state update to be sent, independently of the initial state
        Mockito.verify(mockedMessageSender!!, times(4)).sendToAll(expectedVideoOn)
        Mockito.verify(mockedMessageSender!!, times(1)).sendToAll(expectedUnmuteVideo)

        testScheduler.advanceTimeBy(8, TimeUnit.SECONDS)

        Mockito.verify(mockedMessageSender!!, times(4)).sendToAll(expectedAudioOff)
        Mockito.verify(mockedMessageSender!!, times(5)).sendToAll(expectedStoppedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(5)).sendToAll(expectedVideoOn)
        Mockito.verify(mockedMessageSender!!, times(1)).sendToAll(expectedMuteAudio)
        Mockito.verify(mockedMessageSender!!, times(1)).sendToAll(expectedMuteVideo)
        Mockito.verify(mockedMessageSender!!, times(1)).sendToAll(expectedUnmuteVideo)
        Mockito.verify(mockedMessageSender!!, times(3)).send(expectedMuteAudio, "theSessionId")
        Mockito.verify(mockedMessageSender!!, times(4)).send(expectedUnmuteVideo, "theSessionId")
        Mockito.verifyNoMoreInteractions(mockedMessageSender)
    }

    @Test
    fun testStateSentWithExponentialBackoffWhenAnotherParticipantAdded() {
        // The state sent through data channels should be restarted, although the state sent through signaling
        // messages should be independent for each participant.

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

        val expectedUnmuteAudio = getExpectedUnmuteAudio()
        val expectedUnmuteVideo = getExpectedUnmuteVideo()

        testScheduler.advanceTimeBy(0, TimeUnit.SECONDS)

        var dataChannelMessageCount = 1
        var signalingMessageCount1 = 1
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedAudioOn)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedVideoOn)
        Mockito.verify(mockedMessageSender!!, times(signalingMessageCount1)).send(expectedUnmuteAudio, "theSessionId")
        Mockito.verify(mockedMessageSender!!, times(signalingMessageCount1)).send(expectedUnmuteVideo, "theSessionId")
        Mockito.verifyNoMoreInteractions(mockedMessageSender)

        testScheduler.advanceTimeBy(1, TimeUnit.SECONDS)

        dataChannelMessageCount = 2
        signalingMessageCount1 = 2
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedAudioOn)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedVideoOn)
        Mockito.verify(mockedMessageSender!!, times(signalingMessageCount1)).send(expectedUnmuteAudio, "theSessionId")
        Mockito.verify(mockedMessageSender!!, times(signalingMessageCount1)).send(expectedUnmuteVideo, "theSessionId")
        Mockito.verifyNoMoreInteractions(mockedMessageSender)

        testScheduler.advanceTimeBy(2, TimeUnit.SECONDS)

        dataChannelMessageCount = 3
        signalingMessageCount1 = 3
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedAudioOn)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedVideoOn)
        Mockito.verify(mockedMessageSender!!, times(signalingMessageCount1)).send(expectedUnmuteAudio, "theSessionId")
        Mockito.verify(mockedMessageSender!!, times(signalingMessageCount1)).send(expectedUnmuteVideo, "theSessionId")
        Mockito.verifyNoMoreInteractions(mockedMessageSender)

        testScheduler.advanceTimeBy(4, TimeUnit.SECONDS)

        dataChannelMessageCount = 4
        signalingMessageCount1 = 4
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedAudioOn)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedVideoOn)
        Mockito.verify(mockedMessageSender!!, times(signalingMessageCount1)).send(expectedUnmuteAudio, "theSessionId")
        Mockito.verify(mockedMessageSender!!, times(signalingMessageCount1)).send(expectedUnmuteVideo, "theSessionId")
        Mockito.verifyNoMoreInteractions(mockedMessageSender)

        val callParticipantModel2 = MutableCallParticipantModel("theSessionId2")

        localStateBroadcasterMcu!!.handleCallParticipantAdded(callParticipantModel2)

        testScheduler.advanceTimeBy(0, TimeUnit.SECONDS)

        dataChannelMessageCount = 5
        var signalingMessageCount2 = 1
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedAudioOn)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedVideoOn)
        Mockito.verify(mockedMessageSender!!, times(signalingMessageCount1)).send(expectedUnmuteAudio, "theSessionId")
        Mockito.verify(mockedMessageSender!!, times(signalingMessageCount1)).send(expectedUnmuteVideo, "theSessionId")
        Mockito.verify(mockedMessageSender!!, times(signalingMessageCount2)).send(expectedUnmuteAudio, "theSessionId2")
        Mockito.verify(mockedMessageSender!!, times(signalingMessageCount2)).send(expectedUnmuteVideo, "theSessionId2")
        Mockito.verifyNoMoreInteractions(mockedMessageSender)

        testScheduler.advanceTimeBy(1, TimeUnit.SECONDS)

        dataChannelMessageCount = 6
        signalingMessageCount2 = 2
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedAudioOn)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedVideoOn)
        Mockito.verify(mockedMessageSender!!, times(signalingMessageCount1)).send(expectedUnmuteAudio, "theSessionId")
        Mockito.verify(mockedMessageSender!!, times(signalingMessageCount1)).send(expectedUnmuteVideo, "theSessionId")
        Mockito.verify(mockedMessageSender!!, times(signalingMessageCount2)).send(expectedUnmuteAudio, "theSessionId2")
        Mockito.verify(mockedMessageSender!!, times(signalingMessageCount2)).send(expectedUnmuteVideo, "theSessionId2")
        Mockito.verifyNoMoreInteractions(mockedMessageSender)

        testScheduler.advanceTimeBy(2, TimeUnit.SECONDS)

        dataChannelMessageCount = 7
        signalingMessageCount2 = 3
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedAudioOn)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedVideoOn)
        Mockito.verify(mockedMessageSender!!, times(signalingMessageCount1)).send(expectedUnmuteAudio, "theSessionId")
        Mockito.verify(mockedMessageSender!!, times(signalingMessageCount1)).send(expectedUnmuteVideo, "theSessionId")
        Mockito.verify(mockedMessageSender!!, times(signalingMessageCount2)).send(expectedUnmuteAudio, "theSessionId2")
        Mockito.verify(mockedMessageSender!!, times(signalingMessageCount2)).send(expectedUnmuteVideo, "theSessionId2")
        Mockito.verifyNoMoreInteractions(mockedMessageSender)

        testScheduler.advanceTimeBy(4, TimeUnit.SECONDS)

        dataChannelMessageCount = 8
        signalingMessageCount2 = 4
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedAudioOn)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedVideoOn)
        Mockito.verify(mockedMessageSender!!, times(signalingMessageCount1)).send(expectedUnmuteAudio, "theSessionId")
        Mockito.verify(mockedMessageSender!!, times(signalingMessageCount1)).send(expectedUnmuteVideo, "theSessionId")
        Mockito.verify(mockedMessageSender!!, times(signalingMessageCount2)).send(expectedUnmuteAudio, "theSessionId2")
        Mockito.verify(mockedMessageSender!!, times(signalingMessageCount2)).send(expectedUnmuteVideo, "theSessionId2")
        Mockito.verifyNoMoreInteractions(mockedMessageSender)

        // 0+1+2+4+1=8 seconds since last signaling messages for participant 1
        testScheduler.advanceTimeBy(1, TimeUnit.SECONDS)

        signalingMessageCount1 = 5
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedAudioOn)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedVideoOn)
        Mockito.verify(mockedMessageSender!!, times(signalingMessageCount1)).send(expectedUnmuteAudio, "theSessionId")
        Mockito.verify(mockedMessageSender!!, times(signalingMessageCount1)).send(expectedUnmuteVideo, "theSessionId")
        Mockito.verify(mockedMessageSender!!, times(signalingMessageCount2)).send(expectedUnmuteAudio, "theSessionId2")
        Mockito.verify(mockedMessageSender!!, times(signalingMessageCount2)).send(expectedUnmuteVideo, "theSessionId2")
        Mockito.verifyNoMoreInteractions(mockedMessageSender)

        // 1+7=8 seconds since last data channel messages and signaling messages for participant 2
        testScheduler.advanceTimeBy(7, TimeUnit.SECONDS)

        dataChannelMessageCount = 9
        signalingMessageCount2 = 5
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedAudioOn)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedVideoOn)
        Mockito.verify(mockedMessageSender!!, times(signalingMessageCount1)).send(expectedUnmuteAudio, "theSessionId")
        Mockito.verify(mockedMessageSender!!, times(signalingMessageCount1)).send(expectedUnmuteVideo, "theSessionId")
        Mockito.verify(mockedMessageSender!!, times(signalingMessageCount2)).send(expectedUnmuteAudio, "theSessionId2")
        Mockito.verify(mockedMessageSender!!, times(signalingMessageCount2)).send(expectedUnmuteVideo, "theSessionId2")
        Mockito.verifyNoMoreInteractions(mockedMessageSender)

        // 7+9=16 seconds since last signaling messages for participant 1
        testScheduler.advanceTimeBy(9, TimeUnit.SECONDS)

        signalingMessageCount1 = 6
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedAudioOn)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedVideoOn)
        Mockito.verify(mockedMessageSender!!, times(signalingMessageCount1)).send(expectedUnmuteAudio, "theSessionId")
        Mockito.verify(mockedMessageSender!!, times(signalingMessageCount1)).send(expectedUnmuteVideo, "theSessionId")
        Mockito.verify(mockedMessageSender!!, times(signalingMessageCount2)).send(expectedUnmuteAudio, "theSessionId2")
        Mockito.verify(mockedMessageSender!!, times(signalingMessageCount2)).send(expectedUnmuteVideo, "theSessionId2")
        Mockito.verifyNoMoreInteractions(mockedMessageSender)

        // 9+7=16 seconds since last data channel messages and signaling messages for participant 2
        testScheduler.advanceTimeBy(7, TimeUnit.SECONDS)

        dataChannelMessageCount = 10
        signalingMessageCount2 = 6
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedAudioOn)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedVideoOn)
        Mockito.verify(mockedMessageSender!!, times(signalingMessageCount1)).send(expectedUnmuteAudio, "theSessionId")
        Mockito.verify(mockedMessageSender!!, times(signalingMessageCount1)).send(expectedUnmuteVideo, "theSessionId")
        Mockito.verify(mockedMessageSender!!, times(signalingMessageCount2)).send(expectedUnmuteAudio, "theSessionId2")
        Mockito.verify(mockedMessageSender!!, times(signalingMessageCount2)).send(expectedUnmuteVideo, "theSessionId2")
        Mockito.verifyNoMoreInteractions(mockedMessageSender)

        testScheduler.advanceTimeBy(100, TimeUnit.SECONDS)

        Mockito.verifyNoMoreInteractions(mockedMessageSender)
    }

    @Test
    fun testStateSentWithExponentialBackoffWhenParticipantRemoved() {
        // For simplicity the exponential backoff is not aborted when the participant that triggered it is removed.
        // However, the signaling messages are stopped when the participant is removed.

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

        val expectedUnmuteAudio = getExpectedUnmuteAudio()
        val expectedUnmuteVideo = getExpectedUnmuteVideo()

        testScheduler.advanceTimeBy(0, TimeUnit.SECONDS)

        var dataChannelMessageCount = 1
        var signalingMessageCount = 1
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedAudioOn)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedVideoOn)
        Mockito.verify(mockedMessageSender!!, times(signalingMessageCount)).send(expectedUnmuteAudio, "theSessionId")
        Mockito.verify(mockedMessageSender!!, times(signalingMessageCount)).send(expectedUnmuteVideo, "theSessionId")
        Mockito.verifyNoMoreInteractions(mockedMessageSender)

        testScheduler.advanceTimeBy(1, TimeUnit.SECONDS)

        dataChannelMessageCount = 2
        signalingMessageCount = 2
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedAudioOn)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedVideoOn)
        Mockito.verify(mockedMessageSender!!, times(signalingMessageCount)).send(expectedUnmuteAudio, "theSessionId")
        Mockito.verify(mockedMessageSender!!, times(signalingMessageCount)).send(expectedUnmuteVideo, "theSessionId")
        Mockito.verifyNoMoreInteractions(mockedMessageSender)

        testScheduler.advanceTimeBy(2, TimeUnit.SECONDS)

        dataChannelMessageCount = 3
        signalingMessageCount = 3
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedAudioOn)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedVideoOn)
        Mockito.verify(mockedMessageSender!!, times(signalingMessageCount)).send(expectedUnmuteAudio, "theSessionId")
        Mockito.verify(mockedMessageSender!!, times(signalingMessageCount)).send(expectedUnmuteVideo, "theSessionId")
        Mockito.verifyNoMoreInteractions(mockedMessageSender)

        testScheduler.advanceTimeBy(4, TimeUnit.SECONDS)

        dataChannelMessageCount = 4
        signalingMessageCount = 4
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedAudioOn)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedVideoOn)
        Mockito.verify(mockedMessageSender!!, times(signalingMessageCount)).send(expectedUnmuteAudio, "theSessionId")
        Mockito.verify(mockedMessageSender!!, times(signalingMessageCount)).send(expectedUnmuteVideo, "theSessionId")
        Mockito.verifyNoMoreInteractions(mockedMessageSender)

        localStateBroadcasterMcu!!.handleCallParticipantRemoved(callParticipantModel)

        testScheduler.advanceTimeBy(8, TimeUnit.SECONDS)

        dataChannelMessageCount = 5
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedAudioOn)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedVideoOn)
        Mockito.verify(mockedMessageSender!!, times(signalingMessageCount)).send(expectedUnmuteAudio, "theSessionId")
        Mockito.verify(mockedMessageSender!!, times(signalingMessageCount)).send(expectedUnmuteVideo, "theSessionId")
        Mockito.verifyNoMoreInteractions(mockedMessageSender)

        testScheduler.advanceTimeBy(16, TimeUnit.SECONDS)

        dataChannelMessageCount = 6
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedAudioOn)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(dataChannelMessageCount)).sendToAll(expectedVideoOn)
        Mockito.verify(mockedMessageSender!!, times(signalingMessageCount)).send(expectedUnmuteAudio, "theSessionId")
        Mockito.verify(mockedMessageSender!!, times(signalingMessageCount)).send(expectedUnmuteVideo, "theSessionId")
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
        val callParticipantModel2 = MutableCallParticipantModel("theSessionId2")

        localStateBroadcasterMcu!!.handleCallParticipantAdded(callParticipantModel)
        localStateBroadcasterMcu!!.handleCallParticipantAdded(callParticipantModel2)

        // Sending will be done in another thread, so just adding the participant does not send anything until that
        // other thread could run.
        Mockito.verifyNoInteractions(mockedMessageSender)

        val expectedAudioOn = DataChannelMessage("audioOn")
        val expectedSpeaking = DataChannelMessage("speaking")
        val expectedVideoOn = DataChannelMessage("videoOn")

        val expectedUnmuteAudio = getExpectedUnmuteAudio()
        val expectedUnmuteVideo = getExpectedUnmuteVideo()

        testScheduler.advanceTimeBy(0, TimeUnit.SECONDS)

        var messageCount = 1
        Mockito.verify(mockedMessageSender!!, times(messageCount)).sendToAll(expectedAudioOn)
        Mockito.verify(mockedMessageSender!!, times(messageCount)).sendToAll(expectedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(messageCount)).sendToAll(expectedVideoOn)
        Mockito.verify(mockedMessageSender!!, times(messageCount)).send(expectedUnmuteAudio, "theSessionId")
        Mockito.verify(mockedMessageSender!!, times(messageCount)).send(expectedUnmuteVideo, "theSessionId")
        Mockito.verify(mockedMessageSender!!, times(messageCount)).send(expectedUnmuteAudio, "theSessionId2")
        Mockito.verify(mockedMessageSender!!, times(messageCount)).send(expectedUnmuteVideo, "theSessionId2")
        Mockito.verifyNoMoreInteractions(mockedMessageSender)

        testScheduler.advanceTimeBy(1, TimeUnit.SECONDS)

        messageCount = 2
        Mockito.verify(mockedMessageSender!!, times(messageCount)).sendToAll(expectedAudioOn)
        Mockito.verify(mockedMessageSender!!, times(messageCount)).sendToAll(expectedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(messageCount)).sendToAll(expectedVideoOn)
        Mockito.verify(mockedMessageSender!!, times(messageCount)).send(expectedUnmuteAudio, "theSessionId")
        Mockito.verify(mockedMessageSender!!, times(messageCount)).send(expectedUnmuteVideo, "theSessionId")
        Mockito.verify(mockedMessageSender!!, times(messageCount)).send(expectedUnmuteAudio, "theSessionId2")
        Mockito.verify(mockedMessageSender!!, times(messageCount)).send(expectedUnmuteVideo, "theSessionId2")
        Mockito.verifyNoMoreInteractions(mockedMessageSender)

        testScheduler.advanceTimeBy(2, TimeUnit.SECONDS)

        messageCount = 3
        Mockito.verify(mockedMessageSender!!, times(messageCount)).sendToAll(expectedAudioOn)
        Mockito.verify(mockedMessageSender!!, times(messageCount)).sendToAll(expectedSpeaking)
        Mockito.verify(mockedMessageSender!!, times(messageCount)).sendToAll(expectedVideoOn)
        Mockito.verify(mockedMessageSender!!, times(messageCount)).send(expectedUnmuteAudio, "theSessionId")
        Mockito.verify(mockedMessageSender!!, times(messageCount)).send(expectedUnmuteVideo, "theSessionId")
        Mockito.verify(mockedMessageSender!!, times(messageCount)).send(expectedUnmuteAudio, "theSessionId2")
        Mockito.verify(mockedMessageSender!!, times(messageCount)).send(expectedUnmuteVideo, "theSessionId2")
        Mockito.verifyNoMoreInteractions(mockedMessageSender)

        localStateBroadcasterMcu!!.destroy()

        testScheduler.advanceTimeBy(100, TimeUnit.SECONDS)

        Mockito.verifyNoMoreInteractions(mockedMessageSender)
    }
}
