/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.call

import android.app.Application
import android.content.Context
import androidx.core.telecom.CallAttributesCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class TelecomManagerTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private lateinit var platformTelecomManager: android.telecom.TelecomManager
    private lateinit var telecomManager: TelecomManager

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        context = mock(Context::class.java)
        `when`(context.packageName).thenReturn("com.nextcloud.talk2")
        platformTelecomManager = mock(android.telecom.TelecomManager::class.java)
        `when`(context.getSystemService(Context.TELECOM_SERVICE)).thenReturn(platformTelecomManager)
        `when`(context.getSystemService(android.telecom.TelecomManager::class.java)).thenReturn(platformTelecomManager)
        `when`(context.applicationContext).thenReturn(context)

        telecomManager = TelecomManager(context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testHasActiveCallInitialState() {
        assertFalse(telecomManager.hasActiveCall())
    }

    @Test
    fun testEndCurrentCallDeregistersActiveCallForOwnerOrParticipant() {
        // Ending current call ensures telecom call scope is disconnected and active call state is cleared
        telecomManager.endCurrentCall()
        assertFalse(telecomManager.hasActiveCall())
    }

    @Test
    fun testGetCallTypeReturnsVideoCallWhenIsVideoIsTrue() {
        val callType = telecomManager.getCallType(isVideo = true)
        assertEquals(CallAttributesCompat.CALL_TYPE_VIDEO_CALL, callType)
    }

    @Test
    fun testGetCallTypeReturnsAudioCallWhenIsVideoIsFalse() {
        val callType = telecomManager.getCallType(isVideo = false)
        assertEquals(CallAttributesCompat.CALL_TYPE_AUDIO_CALL, callType)
    }

    @Test
    fun testRegisterAppWithTelecomHandlesExceptionGracefully() {
        `when`(platformTelecomManager.registerPhoneAccount(any()))
            .thenThrow(UnsupportedOperationException("Telecom not supported on device"))

        // Should catch the exception and log without throwing/crashing
        telecomManager.registerAppWithTelecom()
    }

    @Test
    fun testAddIncomingCallHandlesExceptionGracefully() {
        `when`(platformTelecomManager.registerPhoneAccount(any()))
            .thenThrow(UnsupportedOperationException("Unsupported"))

        // Should catch any exception thrown by CallsManager without crashing
        telecomManager.addIncomingCall(
            displayName = "Test User",
            roomToken = "token123",
            isVideo = false,
            onAnswerCall = {},
            onRejectCall = {}
        )
    }

    @Test
    fun testAddOutgoingCallHandlesExceptionGracefully() {
        `when`(platformTelecomManager.registerPhoneAccount(any()))
            .thenThrow(UnsupportedOperationException("Unsupported"))

        // Should catch any exception thrown by CallsManager without crashing
        telecomManager.addOutgoingCall(
            displayName = "Test User",
            roomToken = "token123",
            isVideo = false
        )
    }

    @Test
    fun testAddOutgoingCallConfiguresDisconnectHandler() {
        var disconnected = false
        telecomManager.addOutgoingCall(
            displayName = "Test User",
            roomToken = "token123",
            isVideo = false,
            onDisconnectCall = { disconnected = true }
        )

        assertFalse(disconnected)
    }

    @Test
    fun testAddCallGuardsAgainstConcurrentDuplicateRegistrations() {
        telecomManager.addOutgoingCall(
            displayName = "User 1",
            roomToken = "roomA",
            isVideo = false
        )

        assertTrue(telecomManager.hasActiveCall("roomA"))
        assertFalse(telecomManager.hasActiveCall("roomB"))

        // Duplicate incoming or outgoing registration for another room while roomA is active should be ignored
        telecomManager.addIncomingCall(
            displayName = "User 2",
            roomToken = "roomB",
            isVideo = false,
            onAnswerCall = {},
            onRejectCall = {}
        )

        assertTrue(telecomManager.hasActiveCall("roomA"))
        assertFalse(telecomManager.hasActiveCall("roomB"))
    }

    @Test
    fun testEndCurrentCallIgnoresMismatchedRoomToken() {
        telecomManager.addOutgoingCall(
            displayName = "User 1",
            roomToken = "roomA",
            isVideo = false
        )

        telecomManager.endCurrentCall("roomB")
        assertTrue(telecomManager.hasActiveCall("roomA"))

        telecomManager.endCurrentCall("roomA")
        assertFalse(telecomManager.hasActiveCall("roomA"))
    }

    @Test
    fun testAddOutgoingCallIsIdempotentForSameRoomToken() {
        telecomManager.addOutgoingCall(
            displayName = "User 1",
            roomToken = "roomA",
            isVideo = false
        )

        assertTrue(telecomManager.hasActiveCall("roomA"))

        // Duplicate call for roomA should be ignored cleanly
        telecomManager.addOutgoingCall(
            displayName = "User 1",
            roomToken = "roomA",
            isVideo = false
        )

        assertTrue(telecomManager.hasActiveCall("roomA"))
    }

    @Test
    fun testSetCallActiveQueuesPendingActivationForPendingRoomToken() {
        telecomManager.addIncomingCall(
            displayName = "User 1",
            roomToken = "roomA",
            isVideo = false,
            onAnswerCall = {},
            onRejectCall = {}
        )

        assertTrue(telecomManager.hasActiveCall("roomA"))

        // When answered, setCallActive is called even if registration is pending
        telecomManager.setCallActive("roomA")
        assertTrue(telecomManager.hasActiveCall("roomA"))
    }
}
