/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.activities

import com.nextcloud.talk.signaling.SignalingMessageReceiver
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
import org.mockito.kotlin.mock

@OptIn(ExperimentalCoroutinesApi::class)
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

            viewModel.addParticipant(sessionId, mockReceiver)
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(viewModel.doesParticipantExist(sessionId))
            assertTrue(viewModel.participants.value.any { it.sessionKey == sessionId })
        }

    @Test
    fun `doesParticipantExist returns true when participant is added`() {
        val sessionId = "session2"
        val receiver = mock<SignalingMessageReceiver>()

        viewModel.addParticipant(sessionId, receiver)
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
        viewModel.addParticipant(sessionId, receiver)
        viewModel.onShareScreen(sessionId)

        val activeSession = viewModel.activeScreenShareSession.value
        assertEquals(sessionId, activeSession?.sessionKey)
    }

    @Test
    fun `onUnshareScreen clears active session when same session unshares`() {
        val sessionId = "screen2"
        val receiver = mock<SignalingMessageReceiver>()
        viewModel.addParticipant(sessionId, receiver)
        viewModel.onShareScreen(sessionId)
        viewModel.onUnshareScreen(sessionId)

        assertNull(viewModel.activeScreenShareSession.value)
    }

    @Test
    fun `removeParticipant removes participant and updates participants list`() =
        testScope.runTest {
            val sessionId = "session3"
            val receiver = mock<SignalingMessageReceiver>()

            viewModel.addParticipant(sessionId, receiver)
            viewModel.removeParticipant(sessionId)

            assertFalse(viewModel.doesParticipantExist(sessionId))
            assertTrue(viewModel.participants.value.isEmpty())
        }

    @Test
    fun `setActiveScreenShareSession sets proper participant`() {
        val sessionId = "screen3"
        val receiver = mock<SignalingMessageReceiver>()

        viewModel.addParticipant(sessionId, receiver)
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

            viewModel.addParticipant(sessionId, mockReceiver)
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(1, viewModel.participants.value.size)

            viewModel.onCleared()

            assertEquals(0, viewModel.participants.value.size)
        }
}
