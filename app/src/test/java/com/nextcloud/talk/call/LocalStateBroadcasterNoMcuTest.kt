/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Daniel Calviño Sánchez <danxuliu@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.call

import com.nextcloud.talk.activities.ParticipantUiState
import com.nextcloud.talk.models.json.signaling.DataChannelMessage
import com.nextcloud.talk.models.json.signaling.NCMessagePayload
import com.nextcloud.talk.models.json.signaling.NCSignalingMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

@OptIn(ExperimentalCoroutinesApi::class)
class LocalStateBroadcasterNoMcuTest {

    private lateinit var localCallParticipantModel: MutableLocalCallParticipantModel
    private lateinit var mockedMessageSenderNoMcu: MessageSenderNoMcu

    private lateinit var localStateBroadcasterNoMcu: LocalStateBroadcasterNoMcu

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        localCallParticipantModel = MutableLocalCallParticipantModel()
        localCallParticipantModel.isAudioEnabled = true
        localCallParticipantModel.isSpeaking = true
        localCallParticipantModel.isVideoEnabled = true
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
    fun testStateSentWhenParticipantConnects() =
        testScope.runTest {
            localStateBroadcasterNoMcu = LocalStateBroadcasterNoMcu(
                localCallParticipantModel,
                mockedMessageSenderNoMcu,
                testScope
            )

            val initialState = createTestParticipantUiState(
                sessionId = "theSessionId",
                isConnected = false
            )

            localStateBroadcasterNoMcu.handleCallParticipantAdded(initialState)

            advanceUntilIdle()

            // Verify nothing is sent because isConnected is false
            Mockito.verifyNoInteractions(mockedMessageSenderNoMcu)

            // State 2: The same participant's state is updated to connected
            val connectedState = initialState.copy(isConnected = true)

            localStateBroadcasterNoMcu.handleCallParticipantAdded(connectedState)

            advanceUntilIdle() // Allow the broadcaster to react

            verifyStateSent("theSessionId")
            Mockito.verifyNoMoreInteractions(mockedMessageSenderNoMcu)
        }

    @Test
    fun testStateNotSentAfterParticipantIsRemoved() =
        testScope.runTest {
            localStateBroadcasterNoMcu = LocalStateBroadcasterNoMcu(
                localCallParticipantModel,
                mockedMessageSenderNoMcu,
                testScope
            )

            val initialState = createTestParticipantUiState(
                sessionId = "theSessionId",
                isConnected = false
            )

            localStateBroadcasterNoMcu.handleCallParticipantAdded(initialState)
            localStateBroadcasterNoMcu.handleCallParticipantRemoved("theSessionId")

            advanceUntilIdle()

            Mockito.verifyNoInteractions(mockedMessageSenderNoMcu)
        }

    @Test
    fun testStateNotSentAfterDestroyed() =
        testScope.runTest {
            localStateBroadcasterNoMcu = LocalStateBroadcasterNoMcu(
                localCallParticipantModel,
                mockedMessageSenderNoMcu,
                testScope
            )

            val initialState = createTestParticipantUiState(
                sessionId = "theSessionId",
                isConnected = false
            )

            localStateBroadcasterNoMcu.handleCallParticipantAdded(initialState)
            localStateBroadcasterNoMcu.destroy()

            advanceUntilIdle()

            Mockito.verifyNoInteractions(mockedMessageSenderNoMcu)
        }

    private fun verifyStateSent(sessionId: String) {
        val expectedAudioOn = DataChannelMessage("audioOn")
        val expectedSpeaking = DataChannelMessage("speaking")
        val expectedVideoOn = DataChannelMessage("videoOn")

        val expectedUnmuteAudio = getExpectedUnmuteAudio()
        val expectedUnmuteVideo = getExpectedUnmuteVideo()

        Mockito.verify(mockedMessageSenderNoMcu).send(expectedAudioOn, sessionId)
        Mockito.verify(mockedMessageSenderNoMcu).send(expectedSpeaking, sessionId)
        Mockito.verify(mockedMessageSenderNoMcu).send(expectedVideoOn, sessionId)
        Mockito.verify(mockedMessageSenderNoMcu).send(expectedUnmuteAudio, sessionId)
        Mockito.verify(mockedMessageSenderNoMcu).send(expectedUnmuteVideo, sessionId)
    }

    private fun createTestParticipantUiState(
        sessionId: String = "theSessionId",
        isConnected: Boolean = false
    ): ParticipantUiState =
        ParticipantUiState(
            sessionKey = sessionId,
            nick = "Guest",
            isConnected = isConnected,
            isAudioEnabled = false,
            isStreamEnabled = false,
            isScreenStreamEnabled = false,
            raisedHand = false,
            isInternal = false,
            baseUrl = "",
            roomToken = ""
        )
}
