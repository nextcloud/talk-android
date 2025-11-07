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
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

@OptIn(ExperimentalCoroutinesApi::class)
class ParticipantHandlerTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var signalingMessageReceiver: SignalingMessageReceiver
    private lateinit var onParticipantShareScreen: (String?) -> Unit
    private lateinit var onParticipantUnshareScreen: (String?) -> Unit

    private lateinit var handler: ParticipantHandler

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        signalingMessageReceiver = mock {}
        onParticipantShareScreen = mock {}
        onParticipantUnshareScreen = mock {}
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `Initial state is correct`() =
        runTest {
            // Given
            val sessionId = "session-123"

            // When
            handler = ParticipantHandler(
                sessionId = sessionId,
                baseUrl = "",
                roomToken = "",
                signalingMessageReceiver = signalingMessageReceiver,
                onParticipantShareScreen = onParticipantShareScreen,
                onParticipantUnshareScreen = onParticipantUnshareScreen
            )

            // Then
            val expectedState = ParticipantUiState(
                sessionKey = sessionId,
                baseUrl = "",
                roomToken = "",
                nick = "Guest",
                isConnected = false,
                isAudioEnabled = false,
                isStreamEnabled = false,
                isScreenStreamEnabled = false,
                raisedHand = false,
                isInternal = false
            )
            assertEquals(expectedState, handler.uiState.value)
        }
}
