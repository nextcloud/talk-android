/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Daniel Calviño Sánchez <danxuliu@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.call

import com.nextcloud.talk.models.json.signaling.DataChannelMessage
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

        Mockito.verify(mockedMessageSender!!).sendToAll(expectedAudioOn)
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

        Mockito.verify(mockedMessageSender!!).sendToAll(expectedAudioOff)
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

        val inOrder = Mockito.inOrder(mockedMessageSender)

        inOrder.verify(mockedMessageSender!!).sendToAll(expectedAudioOn)
        inOrder.verify(mockedMessageSender!!).sendToAll(expectedSpeaking)
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

        val inOrder = Mockito.inOrder(mockedMessageSender)

        inOrder.verify(mockedMessageSender!!).sendToAll(expectedStoppedSpeaking)
        inOrder.verify(mockedMessageSender!!).sendToAll(expectedAudioOff)
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

        Mockito.verify(mockedMessageSender!!).sendToAll(expectedVideoOn)
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

        Mockito.verify(mockedMessageSender!!).sendToAll(expectedVideoOff)
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
