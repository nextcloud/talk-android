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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

/**
 * Unit tests for [LocalStateBroadcasterNoMcu].
 *
 * All tests drive state via [MutableStateFlow] — the same mechanism used by [CallActivity] in
 * production.  This ensures that the tests fail if the StateFlow is not collected internally (i.e.
 * if the fix is reverted and only a snapshot is taken at call time).
 *
 * The broadcaster is created WITHOUT an explicit scope so it uses its default
 * `CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())`.  Because [Dispatchers.setMain]
 * is called in setUp, `Dispatchers.Main.immediate` delegates to [testDispatcher]; this means
 * [advanceUntilIdle] advances the broadcaster's coroutines while they remain outside
 * `testScope`, avoiding [kotlinx.coroutines.test.UncompletedCoroutinesError].
 */
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

    @After
    fun tearDown() {
        if (::localStateBroadcasterNoMcu.isInitialized) {
            localStateBroadcasterNoMcu.destroy()
        }
        Dispatchers.resetMain()
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

    /**
     * Happy path: participant starts disconnected, then ICE connects.
     *
     * Drives the state via MutableStateFlow — this test FAILS if LocalStateBroadcasterNoMcu
     * only takes a snapshot at handleCallParticipantAdded time instead of collecting the flow.
     */
    @Test
    fun testStateSentWhenParticipantConnects() =
        testScope.runTest {
            localStateBroadcasterNoMcu = LocalStateBroadcasterNoMcu(
                localCallParticipantModel,
                mockedMessageSenderNoMcu
                // No explicit scope: uses default Dispatchers.Main.immediate = testDispatcher
            )

            val uiStateFlow = MutableStateFlow(
                createTestParticipantUiState(sessionId = "theSessionId", isConnected = false)
            )

            localStateBroadcasterNoMcu.handleCallParticipantAdded(uiStateFlow)
            advanceUntilIdle()

            // Not connected yet — nothing should be sent
            Mockito.verifyNoInteractions(mockedMessageSenderNoMcu)

            // ICE connects — state must be sent now
            uiStateFlow.value = uiStateFlow.value.copy(isConnected = true)
            advanceUntilIdle()

            verifyStateSent("theSessionId")
            Mockito.verifyNoMoreInteractions(mockedMessageSenderNoMcu)
        }

    /**
     * Regression test for the bug where videoOn/audioOn were never sent after ICE connected.
     *
     * Real sequence in CallActivity:
     *   1. addParticipant() → ParticipantHandler.uiState starts with isConnected=true (no peer yet)
     *   2. setPeerConnection() → ICE=NEW → isConnected=false
     *   3. ICE negotiation completes → isConnected=true
     *
     * The fix: LocalStateBroadcasterNoMcu collects the StateFlow and sends state on each
     * false→true transition.  A snapshot taken at step 1 would miss the step-3 transition.
     */
    @Test
    fun testStateSentAfterTransientDisconnect() =
        testScope.runTest {
            localStateBroadcasterNoMcu = LocalStateBroadcasterNoMcu(
                localCallParticipantModel,
                mockedMessageSenderNoMcu
            )

            val uiStateFlow = MutableStateFlow(
                createTestParticipantUiState(sessionId = "theSessionId", isConnected = true)
            )

            // Step 1: initial state is connected (ParticipantHandler default before peer connection)
            localStateBroadcasterNoMcu.handleCallParticipantAdded(uiStateFlow)
            advanceUntilIdle()

            // The initial connected state triggers a send (data channel not ready yet in
            // production — message will be dropped, but that is acceptable behaviour)
            Mockito.clearInvocations(mockedMessageSenderNoMcu)

            // Step 2: setPeerConnection() → ICE=NEW → disconnected
            uiStateFlow.value = uiStateFlow.value.copy(isConnected = false)
            advanceUntilIdle()
            Mockito.verifyNoInteractions(mockedMessageSenderNoMcu)

            // Step 3: ICE completed → data channel now available → state must be sent
            uiStateFlow.value = uiStateFlow.value.copy(isConnected = true)
            advanceUntilIdle()
            verifyStateSent("theSessionId")
            Mockito.verifyNoMoreInteractions(mockedMessageSenderNoMcu)
        }

    @Test
    fun testStateNotSentAfterParticipantIsRemoved() =
        testScope.runTest {
            localStateBroadcasterNoMcu = LocalStateBroadcasterNoMcu(
                localCallParticipantModel,
                mockedMessageSenderNoMcu
            )

            val uiStateFlow = MutableStateFlow(
                createTestParticipantUiState(sessionId = "theSessionId", isConnected = false)
            )

            localStateBroadcasterNoMcu.handleCallParticipantAdded(uiStateFlow)
            localStateBroadcasterNoMcu.handleCallParticipantRemoved("theSessionId")

            // State transitions after removal must not trigger a send
            uiStateFlow.value = uiStateFlow.value.copy(isConnected = true)
            advanceUntilIdle()

            Mockito.verifyNoInteractions(mockedMessageSenderNoMcu)
        }

    @Test
    fun testStateNotSentAfterDestroyed() =
        testScope.runTest {
            localStateBroadcasterNoMcu = LocalStateBroadcasterNoMcu(
                localCallParticipantModel,
                mockedMessageSenderNoMcu
            )

            val uiStateFlow = MutableStateFlow(
                createTestParticipantUiState(sessionId = "theSessionId", isConnected = false)
            )

            localStateBroadcasterNoMcu.handleCallParticipantAdded(uiStateFlow)
            localStateBroadcasterNoMcu.destroy()

            // State transitions after destroy must not trigger a send
            uiStateFlow.value = uiStateFlow.value.copy(isConnected = true)
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
