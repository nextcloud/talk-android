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

@Suppress("TooManyFunctions")
class LocalStateBroadcasterTest {

    private class LocalStateBroadcaster(
        localCallParticipantModel: LocalCallParticipantModel?,
        messageSender: MessageSender?
    ) : com.nextcloud.talk.call.LocalStateBroadcaster(localCallParticipantModel, messageSender) {

        override fun handleCallParticipantAdded(callParticipantModel: CallParticipantModel) {
            // Not used in base class tests
        }

        override fun handleCallParticipantRemoved(callParticipantModel: CallParticipantModel) {
            // Not used in base class tests
        }
    }

    private var localCallParticipantModel: MutableLocalCallParticipantModel? = null
    private var mockedMessageSender: MessageSender? = null

    private var localStateBroadcaster: LocalStateBroadcaster? = null

    @Before
    fun setUp() {
        localCallParticipantModel = MutableLocalCallParticipantModel()
        mockedMessageSender = Mockito.mock(MessageSender::class.java)
    }

    @Test
    fun testEnableAudio() {
        localCallParticipantModel!!.isAudioEnabled = false

        localStateBroadcaster = LocalStateBroadcaster(localCallParticipantModel, mockedMessageSender)

        localCallParticipantModel!!.isAudioEnabled = true

        val expectedAudioOn = DataChannelMessage("audioOn")

        val expectedUnmuteAudio = NCSignalingMessage()
        expectedUnmuteAudio.roomType = "video"
        expectedUnmuteAudio.type = "unmute"
        val payload = NCMessagePayload()
        payload.name = "audio"
        expectedUnmuteAudio.payload = payload

        Mockito.verify(mockedMessageSender!!).sendToAll(expectedAudioOn)
        Mockito.verify(mockedMessageSender!!).sendToAll(expectedUnmuteAudio)
        Mockito.verifyNoMoreInteractions(mockedMessageSender)
    }

    @Test
    fun testEnableAudioTwice() {
        localCallParticipantModel!!.isAudioEnabled = true

        localStateBroadcaster = LocalStateBroadcaster(localCallParticipantModel, mockedMessageSender)

        localCallParticipantModel!!.isAudioEnabled = true

        Mockito.verifyNoMoreInteractions(mockedMessageSender)
    }

    @Test
    fun testDisableAudio() {
        localCallParticipantModel!!.isAudioEnabled = true

        localStateBroadcaster = LocalStateBroadcaster(localCallParticipantModel, mockedMessageSender)

        localCallParticipantModel!!.isAudioEnabled = false

        val expectedAudioOff = DataChannelMessage("audioOff")

        val expectedMuteAudio = NCSignalingMessage()
        expectedMuteAudio.roomType = "video"
        expectedMuteAudio.type = "mute"
        val payload = NCMessagePayload()
        payload.name = "audio"
        expectedMuteAudio.payload = payload

        Mockito.verify(mockedMessageSender!!).sendToAll(expectedAudioOff)
        Mockito.verify(mockedMessageSender!!).sendToAll(expectedMuteAudio)
        Mockito.verifyNoMoreInteractions(mockedMessageSender)
    }

    @Test
    fun testDisableAudioTwice() {
        localCallParticipantModel!!.isAudioEnabled = false

        localStateBroadcaster = LocalStateBroadcaster(localCallParticipantModel, mockedMessageSender)

        localCallParticipantModel!!.isAudioEnabled = false

        Mockito.verifyNoMoreInteractions(mockedMessageSender)
    }

    @Test
    fun testEnableSpeaking() {
        localCallParticipantModel!!.isAudioEnabled = true
        localCallParticipantModel!!.isSpeaking = false

        localStateBroadcaster = LocalStateBroadcaster(localCallParticipantModel, mockedMessageSender)

        localCallParticipantModel!!.isSpeaking = true

        val expectedSpeaking = DataChannelMessage("speaking")

        Mockito.verify(mockedMessageSender!!).sendToAll(expectedSpeaking)
        Mockito.verifyNoMoreInteractions(mockedMessageSender)
    }

    @Test
    fun testEnableSpeakingTwice() {
        localCallParticipantModel!!.isAudioEnabled = true
        localCallParticipantModel!!.isSpeaking = true

        localStateBroadcaster = LocalStateBroadcaster(localCallParticipantModel, mockedMessageSender)

        localCallParticipantModel!!.isSpeaking = true

        Mockito.verifyNoMoreInteractions(mockedMessageSender)
    }

    @Test
    fun testEnableSpeakingWithAudioDisabled() {
        localCallParticipantModel!!.isAudioEnabled = false
        localCallParticipantModel!!.isSpeaking = false

        localStateBroadcaster = LocalStateBroadcaster(localCallParticipantModel, mockedMessageSender)

        localCallParticipantModel!!.isSpeaking = true

        Mockito.verifyNoInteractions(mockedMessageSender)
    }

    @Test
    fun testEnableAudioWhileSpeaking() {
        localCallParticipantModel!!.isAudioEnabled = false
        localCallParticipantModel!!.isSpeaking = false

        localStateBroadcaster = LocalStateBroadcaster(localCallParticipantModel, mockedMessageSender)

        localCallParticipantModel!!.isSpeaking = true
        localCallParticipantModel!!.isAudioEnabled = true

        val expectedAudioOn = DataChannelMessage("audioOn")
        val expectedSpeaking = DataChannelMessage("speaking")

        val expectedUnmuteAudio = NCSignalingMessage()
        expectedUnmuteAudio.roomType = "video"
        expectedUnmuteAudio.type = "unmute"
        val payload = NCMessagePayload()
        payload.name = "audio"
        expectedUnmuteAudio.payload = payload

        val inOrder = Mockito.inOrder(mockedMessageSender)

        inOrder.verify(mockedMessageSender!!).sendToAll(expectedAudioOn)
        inOrder.verify(mockedMessageSender!!).sendToAll(expectedSpeaking)
        Mockito.verify(mockedMessageSender!!).sendToAll(expectedUnmuteAudio)
        Mockito.verifyNoMoreInteractions(mockedMessageSender)
    }

    @Test
    fun testDisableSpeaking() {
        localCallParticipantModel!!.isAudioEnabled = true
        localCallParticipantModel!!.isSpeaking = true

        localStateBroadcaster = LocalStateBroadcaster(localCallParticipantModel, mockedMessageSender)

        localCallParticipantModel!!.isSpeaking = false

        val expectedStoppedSpeaking = DataChannelMessage("stoppedSpeaking")

        Mockito.verify(mockedMessageSender!!).sendToAll(expectedStoppedSpeaking)
        Mockito.verifyNoMoreInteractions(mockedMessageSender)
    }

    @Test
    fun testDisableSpeakingTwice() {
        localCallParticipantModel!!.isAudioEnabled = true
        localCallParticipantModel!!.isSpeaking = false

        localStateBroadcaster = LocalStateBroadcaster(localCallParticipantModel, mockedMessageSender)

        localCallParticipantModel!!.isSpeaking = false

        Mockito.verifyNoMoreInteractions(mockedMessageSender)
    }

    @Test
    fun testDisableAudioWhileSpeaking() {
        localCallParticipantModel!!.isAudioEnabled = true
        localCallParticipantModel!!.isSpeaking = true

        localStateBroadcaster = LocalStateBroadcaster(localCallParticipantModel, mockedMessageSender)

        localCallParticipantModel!!.isAudioEnabled = false

        val expectedStoppedSpeaking = DataChannelMessage("stoppedSpeaking")
        val expectedAudioOff = DataChannelMessage("audioOff")

        val expectedMuteAudio = NCSignalingMessage()
        expectedMuteAudio.roomType = "video"
        expectedMuteAudio.type = "mute"
        val payload = NCMessagePayload()
        payload.name = "audio"
        expectedMuteAudio.payload = payload

        val inOrder = Mockito.inOrder(mockedMessageSender)

        inOrder.verify(mockedMessageSender!!).sendToAll(expectedStoppedSpeaking)
        inOrder.verify(mockedMessageSender!!).sendToAll(expectedAudioOff)
        Mockito.verify(mockedMessageSender!!).sendToAll(expectedMuteAudio)
        Mockito.verifyNoMoreInteractions(mockedMessageSender)
    }

    @Test
    fun testDisableSpeakingWithAudioDisabled() {
        localCallParticipantModel!!.isAudioEnabled = false
        localCallParticipantModel!!.isSpeaking = true

        localStateBroadcaster = LocalStateBroadcaster(localCallParticipantModel, mockedMessageSender)

        localCallParticipantModel!!.isSpeaking = false

        Mockito.verifyNoInteractions(mockedMessageSender)
    }

    @Test
    fun testEnableVideo() {
        localCallParticipantModel!!.isVideoEnabled = false

        localStateBroadcaster = LocalStateBroadcaster(localCallParticipantModel, mockedMessageSender)

        localCallParticipantModel!!.isVideoEnabled = true

        val expectedVideoOn = DataChannelMessage("videoOn")

        val expectedUnmuteVideo = NCSignalingMessage()
        expectedUnmuteVideo.roomType = "video"
        expectedUnmuteVideo.type = "unmute"
        val payload = NCMessagePayload()
        payload.name = "video"
        expectedUnmuteVideo.payload = payload

        Mockito.verify(mockedMessageSender!!).sendToAll(expectedVideoOn)
        Mockito.verify(mockedMessageSender!!).sendToAll(expectedUnmuteVideo)
        Mockito.verifyNoMoreInteractions(mockedMessageSender)
    }

    @Test
    fun testEnableVideoTwice() {
        localCallParticipantModel!!.isVideoEnabled = true

        localStateBroadcaster = LocalStateBroadcaster(localCallParticipantModel, mockedMessageSender)

        localCallParticipantModel!!.isVideoEnabled = true

        Mockito.verifyNoMoreInteractions(mockedMessageSender)
    }

    @Test
    fun testDisableVideo() {
        localCallParticipantModel!!.isVideoEnabled = true

        localStateBroadcaster = LocalStateBroadcaster(localCallParticipantModel, mockedMessageSender)

        localCallParticipantModel!!.isVideoEnabled = false

        val expectedVideoOff = DataChannelMessage("videoOff")

        val expectedMuteVideo = NCSignalingMessage()
        expectedMuteVideo.roomType = "video"
        expectedMuteVideo.type = "mute"
        val payload = NCMessagePayload()
        payload.name = "video"
        expectedMuteVideo.payload = payload

        Mockito.verify(mockedMessageSender!!).sendToAll(expectedVideoOff)
        Mockito.verify(mockedMessageSender!!).sendToAll(expectedMuteVideo)
        Mockito.verifyNoMoreInteractions(mockedMessageSender)
    }

    @Test
    fun testDisableVideoTwice() {
        localCallParticipantModel!!.isVideoEnabled = false

        localStateBroadcaster = LocalStateBroadcaster(localCallParticipantModel, mockedMessageSender)

        localCallParticipantModel!!.isVideoEnabled = false

        Mockito.verifyNoMoreInteractions(mockedMessageSender)
    }

    @Test
    fun testChangeStateAfterDestroying() {
        localCallParticipantModel!!.isAudioEnabled = false
        localCallParticipantModel!!.isSpeaking = false
        localCallParticipantModel!!.isVideoEnabled = false

        localStateBroadcaster = LocalStateBroadcaster(localCallParticipantModel, mockedMessageSender)

        localStateBroadcaster!!.destroy()
        localCallParticipantModel!!.isAudioEnabled = true
        localCallParticipantModel!!.isSpeaking = true
        localCallParticipantModel!!.isVideoEnabled = true

        Mockito.verifyNoMoreInteractions(mockedMessageSender)
    }
}
