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
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.webrtc.PeerConnection

class LocalStateBroadcasterNoMcuTest {

    private var localCallParticipantModel: MutableLocalCallParticipantModel? = null
    private var mockedMessageSenderNoMcu: MessageSenderNoMcu? = null

    private var localStateBroadcasterNoMcu: LocalStateBroadcasterNoMcu? = null

    @Before
    fun setUp() {
        localCallParticipantModel = MutableLocalCallParticipantModel()
        localCallParticipantModel!!.isAudioEnabled = true
        localCallParticipantModel!!.isSpeaking = true
        localCallParticipantModel!!.isVideoEnabled = true
        mockedMessageSenderNoMcu = Mockito.mock(MessageSenderNoMcu::class.java)
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

    private fun getExpectedUnmuteVideo(): NCSignalingMessage {
        val expectedUnmuteVideo = NCSignalingMessage()
        expectedUnmuteVideo.roomType = "video"
        expectedUnmuteVideo.type = "unmute"

        val payload = NCMessagePayload()
        payload.name = "video"
        expectedUnmuteVideo.payload = payload

        return expectedUnmuteVideo
    }

    @Test
    fun testStateSentWhenIceConnected() {
        localStateBroadcasterNoMcu = LocalStateBroadcasterNoMcu(
            localCallParticipantModel,
            mockedMessageSenderNoMcu
        )

        val callParticipantModel = MutableCallParticipantModel("theSessionId")

        localStateBroadcasterNoMcu!!.handleCallParticipantAdded(callParticipantModel)

        callParticipantModel.setIceConnectionState(PeerConnection.IceConnectionState.CHECKING)

        Mockito.verifyNoInteractions(mockedMessageSenderNoMcu)

        callParticipantModel.setIceConnectionState(PeerConnection.IceConnectionState.CONNECTED)

        val expectedAudioOn = DataChannelMessage("audioOn")
        val expectedSpeaking = DataChannelMessage("speaking")
        val expectedVideoOn = DataChannelMessage("videoOn")

        val expectedUnmuteAudio = getExpectedUnmuteAudio()
        val expectedUnmuteVideo = getExpectedUnmuteVideo()

        Mockito.verify(mockedMessageSenderNoMcu!!).send(expectedAudioOn, "theSessionId")
        Mockito.verify(mockedMessageSenderNoMcu!!).send(expectedSpeaking, "theSessionId")
        Mockito.verify(mockedMessageSenderNoMcu!!).send(expectedVideoOn, "theSessionId")
        Mockito.verify(mockedMessageSenderNoMcu!!).send(expectedUnmuteAudio, "theSessionId")
        Mockito.verify(mockedMessageSenderNoMcu!!).send(expectedUnmuteVideo, "theSessionId")
        Mockito.verifyNoMoreInteractions(mockedMessageSenderNoMcu)
    }

    @Test
    fun testStateSentWhenIceCompleted() {
        localStateBroadcasterNoMcu = LocalStateBroadcasterNoMcu(
            localCallParticipantModel,
            mockedMessageSenderNoMcu
        )

        val callParticipantModel = MutableCallParticipantModel("theSessionId")

        localStateBroadcasterNoMcu!!.handleCallParticipantAdded(callParticipantModel)

        callParticipantModel.setIceConnectionState(PeerConnection.IceConnectionState.CHECKING)

        Mockito.verifyNoInteractions(mockedMessageSenderNoMcu)

        callParticipantModel.setIceConnectionState(PeerConnection.IceConnectionState.COMPLETED)

        val expectedAudioOn = DataChannelMessage("audioOn")
        val expectedSpeaking = DataChannelMessage("speaking")
        val expectedVideoOn = DataChannelMessage("videoOn")

        val expectedUnmuteAudio = getExpectedUnmuteAudio()
        val expectedUnmuteVideo = getExpectedUnmuteVideo()

        Mockito.verify(mockedMessageSenderNoMcu!!).send(expectedAudioOn, "theSessionId")
        Mockito.verify(mockedMessageSenderNoMcu!!).send(expectedSpeaking, "theSessionId")
        Mockito.verify(mockedMessageSenderNoMcu!!).send(expectedVideoOn, "theSessionId")
        Mockito.verify(mockedMessageSenderNoMcu!!).send(expectedUnmuteAudio, "theSessionId")
        Mockito.verify(mockedMessageSenderNoMcu!!).send(expectedUnmuteVideo, "theSessionId")
        Mockito.verifyNoMoreInteractions(mockedMessageSenderNoMcu)
    }

    @Test
    fun testStateNotSentWhenIceCompletedAfterConnected() {
        localStateBroadcasterNoMcu = LocalStateBroadcasterNoMcu(
            localCallParticipantModel,
            mockedMessageSenderNoMcu
        )

        val callParticipantModel = MutableCallParticipantModel("theSessionId")

        localStateBroadcasterNoMcu!!.handleCallParticipantAdded(callParticipantModel)

        callParticipantModel.setIceConnectionState(PeerConnection.IceConnectionState.CHECKING)

        Mockito.verifyNoInteractions(mockedMessageSenderNoMcu)

        callParticipantModel.setIceConnectionState(PeerConnection.IceConnectionState.CONNECTED)

        val expectedAudioOn = DataChannelMessage("audioOn")
        val expectedSpeaking = DataChannelMessage("speaking")
        val expectedVideoOn = DataChannelMessage("videoOn")

        val expectedUnmuteAudio = getExpectedUnmuteAudio()
        val expectedUnmuteVideo = getExpectedUnmuteVideo()

        Mockito.verify(mockedMessageSenderNoMcu!!).send(expectedAudioOn, "theSessionId")
        Mockito.verify(mockedMessageSenderNoMcu!!).send(expectedSpeaking, "theSessionId")
        Mockito.verify(mockedMessageSenderNoMcu!!).send(expectedVideoOn, "theSessionId")
        Mockito.verify(mockedMessageSenderNoMcu!!).send(expectedUnmuteAudio, "theSessionId")
        Mockito.verify(mockedMessageSenderNoMcu!!).send(expectedUnmuteVideo, "theSessionId")
        Mockito.verifyNoMoreInteractions(mockedMessageSenderNoMcu)

        callParticipantModel.setIceConnectionState(PeerConnection.IceConnectionState.COMPLETED)

        Mockito.verifyNoMoreInteractions(mockedMessageSenderNoMcu)
    }

    @Test
    fun testStateNotSentWhenIceConnectedAgain() {
        localStateBroadcasterNoMcu = LocalStateBroadcasterNoMcu(
            localCallParticipantModel,
            mockedMessageSenderNoMcu
        )

        val callParticipantModel = MutableCallParticipantModel("theSessionId")

        localStateBroadcasterNoMcu!!.handleCallParticipantAdded(callParticipantModel)

        callParticipantModel.setIceConnectionState(PeerConnection.IceConnectionState.CHECKING)

        Mockito.verifyNoInteractions(mockedMessageSenderNoMcu)

        callParticipantModel.setIceConnectionState(PeerConnection.IceConnectionState.CONNECTED)

        val expectedAudioOn = DataChannelMessage("audioOn")
        val expectedSpeaking = DataChannelMessage("speaking")
        val expectedVideoOn = DataChannelMessage("videoOn")

        val expectedUnmuteAudio = getExpectedUnmuteAudio()
        val expectedUnmuteVideo = getExpectedUnmuteVideo()

        Mockito.verify(mockedMessageSenderNoMcu!!).send(expectedAudioOn, "theSessionId")
        Mockito.verify(mockedMessageSenderNoMcu!!).send(expectedSpeaking, "theSessionId")
        Mockito.verify(mockedMessageSenderNoMcu!!).send(expectedVideoOn, "theSessionId")
        Mockito.verify(mockedMessageSenderNoMcu!!).send(expectedUnmuteAudio, "theSessionId")
        Mockito.verify(mockedMessageSenderNoMcu!!).send(expectedUnmuteVideo, "theSessionId")
        Mockito.verifyNoMoreInteractions(mockedMessageSenderNoMcu)

        callParticipantModel.setIceConnectionState(PeerConnection.IceConnectionState.COMPLETED)

        Mockito.verifyNoMoreInteractions(mockedMessageSenderNoMcu)

        // Completed -> Connected could happen with an ICE restart
        callParticipantModel.setIceConnectionState(PeerConnection.IceConnectionState.CONNECTED)

        Mockito.verifyNoMoreInteractions(mockedMessageSenderNoMcu)

        callParticipantModel.setIceConnectionState(PeerConnection.IceConnectionState.DISCONNECTED)

        Mockito.verifyNoMoreInteractions(mockedMessageSenderNoMcu)

        callParticipantModel.setIceConnectionState(PeerConnection.IceConnectionState.CONNECTED)

        Mockito.verifyNoMoreInteractions(mockedMessageSenderNoMcu)

        // Failed -> Checking could happen with an ICE restart
        callParticipantModel.setIceConnectionState(PeerConnection.IceConnectionState.FAILED)
        callParticipantModel.setIceConnectionState(PeerConnection.IceConnectionState.CHECKING)

        Mockito.verifyNoMoreInteractions(mockedMessageSenderNoMcu)

        callParticipantModel.setIceConnectionState(PeerConnection.IceConnectionState.CONNECTED)

        Mockito.verifyNoMoreInteractions(mockedMessageSenderNoMcu)
    }

    @Test
    fun testStateNotSentToOtherParticipantsWhenIceConnected() {
        localStateBroadcasterNoMcu = LocalStateBroadcasterNoMcu(
            localCallParticipantModel,
            mockedMessageSenderNoMcu
        )

        val callParticipantModel = MutableCallParticipantModel("theSessionId")
        val callParticipantModel2 = MutableCallParticipantModel("theSessionId2")

        localStateBroadcasterNoMcu!!.handleCallParticipantAdded(callParticipantModel)
        localStateBroadcasterNoMcu!!.handleCallParticipantAdded(callParticipantModel2)

        callParticipantModel.setIceConnectionState(PeerConnection.IceConnectionState.CHECKING)
        callParticipantModel2.setIceConnectionState(PeerConnection.IceConnectionState.CHECKING)

        Mockito.verifyNoInteractions(mockedMessageSenderNoMcu)

        callParticipantModel.setIceConnectionState(PeerConnection.IceConnectionState.CONNECTED)

        val expectedAudioOn = DataChannelMessage("audioOn")
        val expectedSpeaking = DataChannelMessage("speaking")
        val expectedVideoOn = DataChannelMessage("videoOn")

        val expectedUnmuteAudio = getExpectedUnmuteAudio()
        val expectedUnmuteVideo = getExpectedUnmuteVideo()

        Mockito.verify(mockedMessageSenderNoMcu!!).send(expectedAudioOn, "theSessionId")
        Mockito.verify(mockedMessageSenderNoMcu!!).send(expectedSpeaking, "theSessionId")
        Mockito.verify(mockedMessageSenderNoMcu!!).send(expectedVideoOn, "theSessionId")
        Mockito.verify(mockedMessageSenderNoMcu!!).send(expectedUnmuteAudio, "theSessionId")
        Mockito.verify(mockedMessageSenderNoMcu!!).send(expectedUnmuteVideo, "theSessionId")
        Mockito.verifyNoMoreInteractions(mockedMessageSenderNoMcu)

        callParticipantModel2.setIceConnectionState(PeerConnection.IceConnectionState.CONNECTED)

        Mockito.verify(mockedMessageSenderNoMcu!!).send(expectedAudioOn, "theSessionId2")
        Mockito.verify(mockedMessageSenderNoMcu!!).send(expectedSpeaking, "theSessionId2")
        Mockito.verify(mockedMessageSenderNoMcu!!).send(expectedVideoOn, "theSessionId2")
        Mockito.verify(mockedMessageSenderNoMcu!!).send(expectedUnmuteAudio, "theSessionId2")
        Mockito.verify(mockedMessageSenderNoMcu!!).send(expectedUnmuteVideo, "theSessionId2")
        Mockito.verifyNoMoreInteractions(mockedMessageSenderNoMcu)
    }

    @Test
    fun testStateNotSentWhenIceConnectedAfterParticipantIsRemoved() {
        // This should not happen, as peer connections are expected to be ended when a call participant is removed, but
        // just in case.

        localStateBroadcasterNoMcu = LocalStateBroadcasterNoMcu(
            localCallParticipantModel,
            mockedMessageSenderNoMcu
        )

        val callParticipantModel = MutableCallParticipantModel("theSessionId")

        localStateBroadcasterNoMcu!!.handleCallParticipantAdded(callParticipantModel)

        callParticipantModel.setIceConnectionState(PeerConnection.IceConnectionState.CHECKING)

        Mockito.verifyNoInteractions(mockedMessageSenderNoMcu)

        localStateBroadcasterNoMcu!!.handleCallParticipantRemoved(callParticipantModel)

        callParticipantModel.setIceConnectionState(PeerConnection.IceConnectionState.CONNECTED)

        Mockito.verifyNoInteractions(mockedMessageSenderNoMcu)
    }

    @Test
    fun testStateNotSentWhenIceCompletedAfterParticipantIsRemoved() {
        // This should not happen, as peer connections are expected to be ended when a call participant is removed, but
        // just in case.

        localStateBroadcasterNoMcu = LocalStateBroadcasterNoMcu(
            localCallParticipantModel,
            mockedMessageSenderNoMcu
        )

        val callParticipantModel = MutableCallParticipantModel("theSessionId")

        localStateBroadcasterNoMcu!!.handleCallParticipantAdded(callParticipantModel)

        callParticipantModel.setIceConnectionState(PeerConnection.IceConnectionState.CHECKING)

        Mockito.verifyNoInteractions(mockedMessageSenderNoMcu)

        localStateBroadcasterNoMcu!!.handleCallParticipantRemoved(callParticipantModel)

        callParticipantModel.setIceConnectionState(PeerConnection.IceConnectionState.COMPLETED)

        Mockito.verifyNoInteractions(mockedMessageSenderNoMcu)
    }

    @Test
    fun testStateNotSentWhenIceConnectedAfterDestroyed() {
        localStateBroadcasterNoMcu = LocalStateBroadcasterNoMcu(
            localCallParticipantModel,
            mockedMessageSenderNoMcu
        )

        val callParticipantModel = MutableCallParticipantModel("theSessionId")
        val callParticipantModel2 = MutableCallParticipantModel("theSessionId2")

        localStateBroadcasterNoMcu!!.handleCallParticipantAdded(callParticipantModel)
        localStateBroadcasterNoMcu!!.handleCallParticipantAdded(callParticipantModel2)

        callParticipantModel.setIceConnectionState(PeerConnection.IceConnectionState.CHECKING)
        callParticipantModel2.setIceConnectionState(PeerConnection.IceConnectionState.CHECKING)

        Mockito.verifyNoInteractions(mockedMessageSenderNoMcu)

        localStateBroadcasterNoMcu!!.destroy()

        callParticipantModel.setIceConnectionState(PeerConnection.IceConnectionState.CONNECTED)
        callParticipantModel2.setIceConnectionState(PeerConnection.IceConnectionState.CONNECTED)

        Mockito.verifyNoInteractions(mockedMessageSenderNoMcu)
    }

    @Test
    fun testStateNotSentWhenIceCompletedAfterDestroyed() {
        localStateBroadcasterNoMcu = LocalStateBroadcasterNoMcu(
            localCallParticipantModel,
            mockedMessageSenderNoMcu
        )

        val callParticipantModel = MutableCallParticipantModel("theSessionId")

        localStateBroadcasterNoMcu!!.handleCallParticipantAdded(callParticipantModel)

        callParticipantModel.setIceConnectionState(PeerConnection.IceConnectionState.CHECKING)

        Mockito.verifyNoInteractions(mockedMessageSenderNoMcu)

        localStateBroadcasterNoMcu!!.destroy()

        callParticipantModel.setIceConnectionState(PeerConnection.IceConnectionState.COMPLETED)

        Mockito.verifyNoInteractions(mockedMessageSenderNoMcu)
    }
}
