/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.activities

import com.nextcloud.talk.signaling.SignalingMessageReceiver
import com.nextcloud.talk.webrtc.PeerConnectionWrapper
import com.nextcloud.talk.webrtc.PeerConnectionWrapper.DataChannelMessageListener
import com.nextcloud.talk.webrtc.PeerConnectionWrapper.PeerConnectionObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.webrtc.PeerConnection.IceConnectionState

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions")
class CallViewModelTest {

    private lateinit var viewModel: CallViewModel
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = CallViewModel()
    }

    @Test
    fun `addParticipant adds new participant and updates participants list`() =
        testScope.runTest {
            val sessionId = "session1"
            val mockReceiver = mock<SignalingMessageReceiver>()

            viewModel.addParticipant(
                baseUrl = "",
                roomToken = "",
                sessionId = sessionId,
                signalingMessageReceiver = mockReceiver
            )
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(viewModel.doesParticipantExist(sessionId))
            assertTrue(viewModel.participants.value.any { it.sessionKey == sessionId })
        }

    @Test
    fun `doesParticipantExist returns true when participant is added`() {
        val sessionId = "session2"
        val receiver = mock<SignalingMessageReceiver>()

        viewModel.addParticipant(
            baseUrl = "",
            roomToken = "",
            sessionId = sessionId,
            signalingMessageReceiver = receiver
        )
        assertTrue(viewModel.doesParticipantExist(sessionId))
    }

    @Test
    fun `doesParticipantExist returns false for unknown participant`() {
        assertFalse(viewModel.doesParticipantExist("unknown"))
    }

    @Test
    fun `onShareScreen sets active screen share session`() {
        val sessionId = "screen1"
        val receiver = mock<SignalingMessageReceiver>()
        viewModel.addParticipant(
            baseUrl = "",
            roomToken = "",
            sessionId = sessionId,
            signalingMessageReceiver = receiver
        )
        viewModel.onShareScreen(sessionId)

        val activeSession = viewModel.activeScreenShareSession.value
        assertEquals(sessionId, activeSession?.sessionKey)
    }

    @Test
    fun `onUnshareScreen clears active session when same session unshares`() {
        val sessionId = "screen2"
        val receiver = mock<SignalingMessageReceiver>()
        viewModel.addParticipant(
            baseUrl = "",
            roomToken = "",
            sessionId = sessionId,
            signalingMessageReceiver = receiver
        )
        viewModel.onShareScreen(sessionId)
        viewModel.onUnshareScreen(sessionId)

        assertNull(viewModel.activeScreenShareSession.value)
    }

    @Test
    fun `removeParticipant removes participant and updates participants list`() =
        testScope.runTest {
            val sessionId = "session3"
            val receiver = mock<SignalingMessageReceiver>()

            viewModel.addParticipant(
                baseUrl = "",
                roomToken = "",
                sessionId = sessionId,
                signalingMessageReceiver = receiver
            )
            viewModel.removeParticipant(sessionId)

            assertFalse(viewModel.doesParticipantExist(sessionId))
            assertTrue(viewModel.participants.value.isEmpty())
        }

    @Test
    fun `setActiveScreenShareSession sets proper participant`() {
        val sessionId = "screen3"
        val receiver = mock<SignalingMessageReceiver>()

        viewModel.addParticipant(
            baseUrl = "",
            roomToken = "",
            sessionId = sessionId,
            signalingMessageReceiver = receiver
        )
        viewModel.setActiveScreenShareSession(sessionId)

        val active = viewModel.activeScreenShareSession.value
        assertNotNull(active)
        assertEquals(sessionId, active?.sessionKey)
    }

    @Test
    fun `onCleared destroys all participant handlers`() =
        testScope.runTest {
            val sessionId = "session1"
            val mockReceiver = mock<SignalingMessageReceiver>()

            viewModel.addParticipant(
                baseUrl = "",
                roomToken = "",
                sessionId = sessionId,
                signalingMessageReceiver = mockReceiver
            )
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(1, viewModel.participants.value.size)

            viewModel.onCleared()

            assertEquals(0, viewModel.participants.value.size)
        }

    @Test
    fun `participants that start speaking while not visible are promoted`() {
        addParticipants("a", "b", "c")
        viewModel.setVisibleSessionKeys(setOf("a"))

        startSpeaking("c")
        startSpeaking("b")

        assertEquals(listOf("b", "c"), viewModel.speakers.value)
    }

    @Test
    fun `participants that keep speaking are not promoted again`() {
        addParticipants("a", "b", "c")
        startSpeaking("a")
        startSpeaking("b")

        addParticipants("d")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("b", "a"), viewModel.speakers.value)
    }

    @Test
    fun `visible participants that start speaking are not promoted`() {
        addParticipants("a", "b")
        viewModel.setVisibleSessionKeys(setOf("a", "b"))

        startSpeaking("a")

        assertEquals(emptyList<String>(), viewModel.speakers.value)
    }

    @Test
    fun `speakers that left the call are dropped`() {
        addParticipants("a", "b")
        startSpeaking("b")

        viewModel.removeParticipant("b")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(emptyList<String>(), viewModel.speakers.value)
    }

    private fun startSpeaking(sessionId: String) {
        val peerConnectionWrapper: PeerConnectionWrapper = mock {}
        viewModel.getParticipant(sessionId)!!.setPeerConnection(peerConnectionWrapper)
        val listenerCaptor = argumentCaptor<DataChannelMessageListener>()
        verify(peerConnectionWrapper).addListener(listenerCaptor.capture())
        val observerCaptor = argumentCaptor<PeerConnectionObserver>()
        verify(peerConnectionWrapper).addObserver(observerCaptor.capture())

        observerCaptor.firstValue.onIceConnectionStateChanged(IceConnectionState.CONNECTED)
        listenerCaptor.firstValue.onAudioOn()
        listenerCaptor.firstValue.onSpeaking()
        testDispatcher.scheduler.advanceUntilIdle()
    }

    private fun addParticipants(vararg sessionIds: String) {
        val receiver = mock<SignalingMessageReceiver>()
        sessionIds.forEach {
            viewModel.addParticipant(baseUrl = "", roomToken = "", sessionId = it, signalingMessageReceiver = receiver)
        }
    }
}
