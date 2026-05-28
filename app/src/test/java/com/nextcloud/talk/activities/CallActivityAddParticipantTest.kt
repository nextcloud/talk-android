/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Alain Lauzon
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.activities

import android.app.Application
import com.nextcloud.talk.call.LocalStateBroadcaster
import com.nextcloud.talk.signaling.SignalingMessageReceiver
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Field

/**
 * Robolectric test verifying that [CallActivity.addCallParticipant] passes the **live StateFlow**
 * from [ParticipantHandler.uiState] to [LocalStateBroadcaster.handleCallParticipantAdded].
 *
 * Before the fix, the method passed only the snapshot value [StateFlow.value], meaning
 * [LocalStateBroadcasterNoMcu] could not observe future ICE state changes and videoOn/audioOn
 * were never sent once the data channel was ready.
 *
 * This test:
 *  - creates [CallActivity] via Robolectric WITHOUT calling [Activity.onCreate] (avoiding native
 *    WebRTC and Dagger initialisation)
 *  - injects the required fields via reflection
 *  - calls [addCallParticipant] via reflection
 *  - verifies that the argument passed to [LocalStateBroadcaster.handleCallParticipantAdded] is
 *    the exact same [StateFlow] object as [CallViewModel.getParticipant]!!.uiState
 *
 * If the buggy snapshot pattern is used (`uiState.value` instead of `uiState`), the verification
 * fails because the [StateFlow] overload is never called.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    // Use the plain Application to avoid NextcloudTalkApplication.onCreate() which initialises
    // Dagger, WebRTC, and WorkManager — none of which are needed for this focused test.
    application = Application::class,
    sdk = [33]
)
class CallActivityAddParticipantTest {

    private val mockLocalStateBroadcaster: LocalStateBroadcaster = mock()
    private val mockSignalingMessageReceiver: SignalingMessageReceiver = mock()

    private lateinit var callViewModel: CallViewModel
    private lateinit var activity: CallActivity

    @Before
    fun setUp() {
        callViewModel = CallViewModel()

        // Build the Activity without triggering onCreate — this avoids Dagger injection,
        // EglBase.create() (native), and all network calls.
        activity = Robolectric.buildActivity(CallActivity::class.java).get()

        // Inject the minimum set of fields required by addCallParticipant.
        setField("callViewModel", callViewModel)
        setField("localStateBroadcaster", mockLocalStateBroadcaster)
        setField("signalingMessageReceiver", mockSignalingMessageReceiver)
        setField("baseUrl", "https://test.example.com")
        setField("roomToken", "testRoom")
        // hasExternalSignalingServer = true skips the OfferAnswerNickProvider branch
        setField("hasExternalSignalingServer", true)
    }

    // -----------------------------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------------------------

    /**
     * The live [StateFlow] from [ParticipantHandler.uiState] must be passed to
     * [LocalStateBroadcaster.handleCallParticipantAdded], not just the current snapshot.
     *
     * Fails with the buggy code because the snapshot call goes to the
     * [handleCallParticipantAdded(ParticipantUiState)] overload, not the StateFlow one.
     */
    @Test
    fun `addCallParticipant passes the live StateFlow to handleCallParticipantAdded`() {
        val sessionId = "robolectric-session-1"

        invokeAddCallParticipant(sessionId)

        val captor = argumentCaptor<StateFlow<ParticipantUiState>>()
        verify(mockLocalStateBroadcaster).handleCallParticipantAdded(captor.capture())

        val capturedFlow = captor.firstValue

        // The captured argument must be the SAME StateFlow object that ParticipantHandler
        // exposes — not a copy, not a one-shot MutableStateFlow wrapping the snapshot.
        assertSame(
            "Expected the live ParticipantHandler.uiState StateFlow, got a different object",
            callViewModel.getParticipant(sessionId)!!.uiState,
            capturedFlow
        )
    }

    // -----------------------------------------------------------------------------------------
    // Reflection helpers
    // -----------------------------------------------------------------------------------------

    private fun invokeAddCallParticipant(sessionId: String) {
        val method = CallActivity::class.java.getDeclaredMethod("addCallParticipant", String::class.java)
        method.isAccessible = true
        method.invoke(activity, sessionId)
    }

    private fun setField(fieldName: String, value: Any?) {
        val field = findField(CallActivity::class.java, fieldName)
            ?: error("Field '$fieldName' not found in CallActivity hierarchy")
        field.isAccessible = true
        field.set(activity, value)
    }

    private fun findField(clazz: Class<*>, fieldName: String): Field? {
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName)
            } catch (e: NoSuchFieldException) {
                current = current.superclass
            }
        }
        return null
    }
}
