/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Alain Lauzon
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.activities

import com.nextcloud.talk.call.LocalStateBroadcasterNoMcu
import com.nextcloud.talk.call.MessageSenderNoMcu
import com.nextcloud.talk.call.MutableLocalCallParticipantModel
import com.nextcloud.talk.models.json.signaling.DataChannelMessage
import com.nextcloud.talk.models.json.signaling.NCMessagePayload
import com.nextcloud.talk.models.json.signaling.NCSignalingMessage
import com.nextcloud.talk.signaling.SignalingMessageReceiver
import com.nextcloud.talk.webrtc.PeerConnectionWrapper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.webrtc.PeerConnection.IceConnectionState

/**
 * Integration tests verifying that the full pipeline — CallViewModel → ParticipantHandler →
 * LocalStateBroadcasterNoMcu — correctly sends videoOn/audioOn data channel messages to a
 * remote participant after ICE connects.
 *
 * The bug (commit 2b0f37148): CallActivity called handleCallParticipantAdded only once at
 * participant-add time, passing only the current snapshot.  Because the PeerConnectionWrapper
 * did not exist yet at that moment, the data channel was unavailable and all state messages
 * were silently dropped.  Subsequent ICE transitions were never observed.
 *
 * The fix: LocalStateBroadcasterNoMcu now accepts a live StateFlow<ParticipantUiState> and
 * collects it internally.  State is sent on every false→true transition of isConnected —
 * i.e. when the data channel is actually ready.
 *
 * These tests exercise the real interaction between CallViewModel, ParticipantHandler, and
 * LocalStateBroadcasterNoMcu, using a mocked PeerConnectionWrapper to avoid native WebRTC.
 *
 * Unlike the unit tests in LocalStateBroadcasterNoMcuTest, these tests go one level higher
 * and verify that the wiring works end-to-end through the real ParticipantHandler StateFlow.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CallParticipantStateBroadcastIntegrationTest {

    companion object {
        private const val SESSION_ID = "theRemoteSessionId"
        private const val BASE_URL = "https://cloud.example.com"
        private const val ROOM_TOKEN = "abc123"
    }

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var callViewModel: CallViewModel
    private lateinit var localStateBroadcasterNoMcu: LocalStateBroadcasterNoMcu
    private lateinit var mockedMessageSender: MessageSenderNoMcu
    private lateinit var localCallParticipantModel: MutableLocalCallParticipantModel
    private lateinit var mockedSignalingMessageReceiver: SignalingMessageReceiver
    private lateinit var mockedPeerConnectionWrapper: PeerConnectionWrapper

    @Before
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(testDispatcher)

        mockedSignalingMessageReceiver = mock()
        mockedMessageSender = mock()
        mockedPeerConnectionWrapper = mock()

        localCallParticipantModel = MutableLocalCallParticipantModel().apply {
            isAudioEnabled = true
            isSpeaking = false
            isVideoEnabled = true
        }

        callViewModel = CallViewModel()

        // No explicit scope: uses default CoroutineScope(Dispatchers.Main.immediate + SupervisorJob()).
        // Because Dispatchers.setMain(testDispatcher) is called above, Dispatchers.Main.immediate
        // delegates to testDispatcher — so advanceUntilIdle() advances the broadcaster's coroutines
        // while they stay outside testScope, avoiding UncompletedCoroutinesError.
        localStateBroadcasterNoMcu = LocalStateBroadcasterNoMcu(
            localCallParticipantModel,
            mockedMessageSender
        )
    }

    @After
    fun tearDown() {
        localStateBroadcasterNoMcu.destroy()
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    // -----------------------------------------------------------------------------------------
    // Helper: wire up participant (simulates CallActivity.addCallParticipant with the fix)
    // -----------------------------------------------------------------------------------------

    /**
     * Adds a participant to the CallViewModel and passes its live StateFlow to the broadcaster —
     * exactly as CallActivity does after the fix.
     */
    private fun addParticipantAndWire(sessionId: String = SESSION_ID) {
        callViewModel.addParticipant(BASE_URL, ROOM_TOKEN, sessionId, mockedSignalingMessageReceiver)
        val participantHandler = callViewModel.getParticipant(sessionId)!!
        localStateBroadcasterNoMcu.handleCallParticipantAdded(participantHandler.uiState)
    }

    /**
     * Sets a (mocked) PeerConnection on the participant and captures the PeerConnectionObserver
     * registered by ParticipantHandler.  The observer lets tests drive ICE transitions.
     */
    private fun setPeerConnectionAndCaptureObserver(
        sessionId: String = SESSION_ID,
        wrapper: PeerConnectionWrapper = mockedPeerConnectionWrapper
    ): PeerConnectionWrapper.PeerConnectionObserver {
        callViewModel.getParticipant(sessionId)!!.setPeerConnection(wrapper)
        val captor = argumentCaptor<PeerConnectionWrapper.PeerConnectionObserver>()
        verify(wrapper).addObserver(captor.capture())
        return captor.firstValue
    }

    private fun verifyFullStateSent(sessionId: String) {
        verify(mockedMessageSender).send(DataChannelMessage("audioOn"), sessionId)
        verify(mockedMessageSender).send(DataChannelMessage("stoppedSpeaking"), sessionId)
        verify(mockedMessageSender).send(DataChannelMessage("videoOn"), sessionId)
        verify(mockedMessageSender).send(expectedSignalingUnmuteAudio(), sessionId)
        verify(mockedMessageSender).send(expectedSignalingUnmuteVideo(), sessionId)
    }

    private fun expectedSignalingUnmuteAudio(): NCSignalingMessage {
        val msg = NCSignalingMessage()
        msg.roomType = "video"
        msg.type = "unmute"
        msg.payload = NCMessagePayload().also { it.name = "audio" }
        return msg
    }

    private fun expectedSignalingUnmuteVideo(): NCSignalingMessage {
        val msg = NCSignalingMessage()
        msg.roomType = "video"
        msg.type = "unmute"
        msg.payload = NCMessagePayload().also { it.name = "video" }
        return msg
    }

    // -----------------------------------------------------------------------------------------
    // Tests: nominal ICE flow
    // -----------------------------------------------------------------------------------------

    /**
     * Happy path: full ICE negotiation cycle NEW → CONNECTED.
     *
     * Verifies that videoOn and audioOn data channel messages are sent exactly once,
     * after ICE reaches CONNECTED — not before.
     */
    @Test
    fun `videoOn and audioOn sent after ICE CONNECTED`() =
        testScope.runTest {
            addParticipantAndWire()
            advanceUntilIdle()
            // Clear the initial-state send (isConnected=true before peer connection = no data channel)
            clearInvocations(mockedMessageSender)

            val iceObserver = setPeerConnectionAndCaptureObserver()
            advanceUntilIdle()

            iceObserver.onIceConnectionStateChanged(IceConnectionState.NEW)
            advanceUntilIdle()
            verifyNoInteractions(mockedMessageSender)

            iceObserver.onIceConnectionStateChanged(IceConnectionState.CONNECTED)
            advanceUntilIdle()

            verifyFullStateSent(SESSION_ID)
        }

    /**
     * ICE reaching COMPLETED (equivalent to CONNECTED for WebRTC) must also trigger the send.
     */
    @Test
    fun `videoOn sent when ICE reaches COMPLETED`() =
        testScope.runTest {
            addParticipantAndWire()
            advanceUntilIdle()
            clearInvocations(mockedMessageSender)

            val iceObserver = setPeerConnectionAndCaptureObserver()
            iceObserver.onIceConnectionStateChanged(IceConnectionState.NEW)
            advanceUntilIdle()
            verifyNoInteractions(mockedMessageSender)

            iceObserver.onIceConnectionStateChanged(IceConnectionState.COMPLETED)
            advanceUntilIdle()

            verifyFullStateSent(SESSION_ID)
        }

    /**
     * Two participants each get an independent observer; state is sent independently.
     */
    @Test
    fun `two participants each receive videoOn independently`() =
        testScope.runTest {
            val session1 = "session1"
            val session2 = "session2"

            addParticipantAndWire(session1)
            advanceUntilIdle()
            addParticipantAndWire(session2)
            advanceUntilIdle()
            clearInvocations(mockedMessageSender)

            // --- Participant 1 ---
            val observer1 = setPeerConnectionAndCaptureObserver(session1, mockedPeerConnectionWrapper)
            observer1.onIceConnectionStateChanged(IceConnectionState.NEW)
            advanceUntilIdle()
            verifyNoInteractions(mockedMessageSender)

            observer1.onIceConnectionStateChanged(IceConnectionState.CONNECTED)
            advanceUntilIdle()
            verifyFullStateSent(session1)
            clearInvocations(mockedMessageSender)

            // --- Participant 2 ---
            val mockedWrapper2: PeerConnectionWrapper = mock()
            val observer2 = setPeerConnectionAndCaptureObserver(session2, mockedWrapper2)
            observer2.onIceConnectionStateChanged(IceConnectionState.NEW)
            advanceUntilIdle()
            verifyNoInteractions(mockedMessageSender)

            observer2.onIceConnectionStateChanged(IceConnectionState.CONNECTED)
            advanceUntilIdle()
            verifyFullStateSent(session2)
        }

    // -----------------------------------------------------------------------------------------
    // Tests: local state variants
    // -----------------------------------------------------------------------------------------

    @Test
    fun `videoOff sent when local video is disabled`() =
        testScope.runTest {
            localCallParticipantModel.isVideoEnabled = false

            addParticipantAndWire()
            advanceUntilIdle()
            clearInvocations(mockedMessageSender)

            val iceObserver = setPeerConnectionAndCaptureObserver()
            iceObserver.onIceConnectionStateChanged(IceConnectionState.NEW)
            advanceUntilIdle()
            iceObserver.onIceConnectionStateChanged(IceConnectionState.CONNECTED)
            advanceUntilIdle()

            verify(mockedMessageSender).send(DataChannelMessage("videoOff"), SESSION_ID)
            verify(mockedMessageSender).send(DataChannelMessage("audioOn"), SESSION_ID)
            verify(mockedMessageSender).send(DataChannelMessage("stoppedSpeaking"), SESSION_ID)
        }

    @Test
    fun `audioOff sent when local audio is disabled`() =
        testScope.runTest {
            localCallParticipantModel.isAudioEnabled = false

            addParticipantAndWire()
            advanceUntilIdle()
            clearInvocations(mockedMessageSender)

            val iceObserver = setPeerConnectionAndCaptureObserver()
            iceObserver.onIceConnectionStateChanged(IceConnectionState.NEW)
            advanceUntilIdle()
            iceObserver.onIceConnectionStateChanged(IceConnectionState.CONNECTED)
            advanceUntilIdle()

            verify(mockedMessageSender).send(DataChannelMessage("audioOff"), SESSION_ID)
            verify(mockedMessageSender).send(DataChannelMessage("videoOn"), SESSION_ID)
            verify(mockedMessageSender).send(DataChannelMessage("stoppedSpeaking"), SESSION_ID)
        }

    // -----------------------------------------------------------------------------------------
    // Tests: cleanup
    // -----------------------------------------------------------------------------------------

    @Test
    fun `no state sent after broadcaster is destroyed`() =
        testScope.runTest {
            addParticipantAndWire()
            advanceUntilIdle()

            val iceObserver = setPeerConnectionAndCaptureObserver()

            localStateBroadcasterNoMcu.destroy()
            advanceUntilIdle()
            clearInvocations(mockedMessageSender)

            iceObserver.onIceConnectionStateChanged(IceConnectionState.NEW)
            iceObserver.onIceConnectionStateChanged(IceConnectionState.CONNECTED)
            advanceUntilIdle()

            verifyNoInteractions(mockedMessageSender)
        }

    // -----------------------------------------------------------------------------------------
    // Regression: explicit demonstration of the bug (before the fix)
    // -----------------------------------------------------------------------------------------

    /**
     * REGRESSION: Before the fix, CallActivity passed only the snapshot value at add time.
     * At that moment:
     *   - isConnected = true (ParticipantHandler default before any PeerConnection)
     *   - sendState() was called immediately — but data channel didn't exist yet → dropped
     *
     * After setPeerConnection(), ICE went NEW → CONNECTED, but nobody called
     * handleCallParticipantAdded again → videoOn was never sent.
     *
     * This test verifies that the old (buggy) snapshot pattern fails to deliver state after
     * ICE connects, making the regression explicit and permanent.
     */
    @Test
    fun `REGRESSION - videoOn never sent with one-shot snapshot handleCallParticipantAdded`() =
        testScope.runTest {
            callViewModel.addParticipant(BASE_URL, ROOM_TOKEN, SESSION_ID, mockedSignalingMessageReceiver)
            val participantHandler = callViewModel.getParticipant(SESSION_ID)!!

            // Buggy pattern: pass only the snapshot (the .value of the StateFlow)
            localStateBroadcasterNoMcu.handleCallParticipantAdded(participantHandler.uiState.value)
            advanceUntilIdle()

            participantHandler.setPeerConnection(mockedPeerConnectionWrapper)
            advanceUntilIdle()
            val captor = argumentCaptor<PeerConnectionWrapper.PeerConnectionObserver>()
            verify(mockedPeerConnectionWrapper).addObserver(captor.capture())

            // Clear invocations from the initial (dropped) send
            clearInvocations(mockedMessageSender)

            // ICE negotiates and connects
            captor.firstValue.onIceConnectionStateChanged(IceConnectionState.NEW)
            captor.firstValue.onIceConnectionStateChanged(IceConnectionState.CONNECTED)
            advanceUntilIdle()

            // Without a live StateFlow: handleCallParticipantAdded was called with a snapshot that
            // wrapped in a never-updating MutableStateFlow.  The ICE transition was never observed
            // → no state sent after ICE CONNECTED.
            verifyNoInteractions(mockedMessageSender)
        }
}
