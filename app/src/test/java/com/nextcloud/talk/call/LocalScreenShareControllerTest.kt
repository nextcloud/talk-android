/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.call

import android.content.Intent
import com.nextcloud.talk.models.json.signaling.NCSignalingMessageDto
import com.nextcloud.talk.webrtc.PeerConnectionWrapper
import com.nextcloud.talk.webrtc.PeerConnectionWrapper.PeerConnectionObserver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.Mockito.verify
import org.webrtc.MediaStream
import org.webrtc.PeerConnection.IceConnectionState

@Suppress("TooManyFunctions")
class LocalScreenShareControllerTest {

    private class FakeScreenCapture(private val canStart: Boolean = true) : ScreenCapture {
        override val stream: MediaStream = Mockito.mock(MediaStream::class.java)
        var isStarted = false
        var isReleased = false

        override fun start(): Boolean {
            isStarted = canStart
            return canStart
        }

        override fun release() {
            isReleased = true
        }
    }

    private class FakeHost : LocalScreenShareController.Host {
        override var hasMCU = false
        override var ownSessionId: String? = OWN_SESSION_ID
        override var participantSessionIds: Collection<String> = listOf(SESSION_ID_1, SESSION_ID_2)
        override var isCallActive = true
        override var isScreenSharingAllowed = true

        var foregroundServiceResult: ((Boolean) -> Unit)? = null
        var isForegroundServiceScreenSharing = false
        var canCreateConnections = true
        var capture: FakeScreenCapture? = FakeScreenCapture()
        var onCaptureStop: (() -> Unit)? = null
        val connections = ArrayList<PeerConnectionWrapper>()
        val sentMessages = ArrayList<Pair<String?, String>>()
        var stateChangedCount = 0
        var failedCount = 0
        var encryptionFailedCount = 0

        override fun startForegroundServiceScreenSharing(onResult: (isReady: Boolean) -> Unit) {
            isForegroundServiceScreenSharing = true
            foregroundServiceResult = onResult
        }

        override fun stopForegroundServiceScreenSharing() {
            isForegroundServiceScreenSharing = false
        }

        override fun createScreenCapture(permissionResultData: Intent, onStop: () -> Unit): ScreenCapture? {
            onCaptureStop = onStop
            return capture
        }

        override fun createPeerConnection(sessionId: String, stream: MediaStream): PeerConnectionWrapper {
            check(canCreateConnections) { "No keys" }
            val connection = Mockito.mock(PeerConnectionWrapper::class.java)
            Mockito.`when`(connection.sessionId).thenReturn(sessionId)
            connections.add(connection)
            return connection
        }

        override fun send(message: NCSignalingMessageDto, sessionId: String) {
            assertEquals("screen", message.roomType)
            sentMessages.add(Pair(message.type, sessionId))
        }

        override fun onStateChanged() {
            stateChangedCount++
        }

        override fun onScreenSharingFailed() {
            failedCount++
        }

        override fun onEncryptionFailed() {
            encryptionFailedCount++
        }

        fun connectionTo(sessionId: String): PeerConnectionWrapper = connections.last { it.sessionId == sessionId }

        fun messages(type: String): List<String> = sentMessages.filter { it.first == type }.map { it.second }
    }

    private lateinit var host: FakeHost
    private lateinit var controller: LocalScreenShareController
    private val permissionResultData = Mockito.mock(Intent::class.java)

    @Before
    fun setUp() {
        host = FakeHost()
        controller = LocalScreenShareController(host) { it.run() }
    }

    private fun startSharing() {
        assertTrue(controller.requestStart())
        controller.onPermissionGranted(permissionResultData)
        host.foregroundServiceResult!!.invoke(true)
    }

    private fun setIceConnectionState(connection: PeerConnectionWrapper, state: IceConnectionState) {
        val captor = ArgumentCaptor.forClass(PeerConnectionObserver::class.java)
        verify(connection).addObserver(captor.capture())
        captor.value.onIceConnectionStateChanged(state)
    }

    @Test
    fun testStartWithoutMcuConnectsToEachParticipant() {
        startSharing()

        assertTrue(controller.isSharing)
        assertFalse(controller.isPending)
        assertTrue(host.capture!!.isStarted)
        assertTrue(host.isForegroundServiceScreenSharing)
        assertEquals(listOf(SESSION_ID_1, SESSION_ID_2), host.connections.map { it.sessionId })
    }

    @Test
    fun testStartWithMcuConnectsToOwnPublisher() {
        host.hasMCU = true

        startSharing()

        assertEquals(listOf(OWN_SESSION_ID), host.connections.map { it.sessionId })
    }

    @Test
    fun testRequestStartIsRefusedWhilePendingOrSharing() {
        assertTrue(controller.requestStart())
        assertFalse(controller.requestStart())

        controller.onPermissionGranted(permissionResultData)
        host.foregroundServiceResult!!.invoke(true)

        assertFalse(controller.requestStart())
    }

    @Test
    fun testPermissionGrantedAfterStopIsIgnored() {
        controller.requestStart()
        controller.stop()

        controller.onPermissionGranted(permissionResultData)

        assertFalse(controller.isPending)
        assertFalse(controller.isSharing)
        assertFalse(host.isForegroundServiceScreenSharing)
    }

    @Test
    fun testForegroundServiceFailure() {
        controller.requestStart()
        controller.onPermissionGranted(permissionResultData)

        host.foregroundServiceResult!!.invoke(false)

        assertFalse(controller.isPending)
        assertFalse(controller.isSharing)
        assertEquals(1, host.failedCount)
    }

    @Test
    fun testCaptureFailure() {
        host.capture = FakeScreenCapture(canStart = false)

        startSharing()

        assertFalse(controller.isPending)
        assertFalse(controller.isSharing)
        assertTrue(host.capture!!.isReleased)
        assertFalse(host.isForegroundServiceScreenSharing)
        assertEquals(1, host.failedCount)
        assertTrue(host.connections.isEmpty())
    }

    @Test
    fun testStopWhileWaitingForForegroundService() {
        controller.requestStart()
        controller.onPermissionGranted(permissionResultData)
        controller.stop()

        host.foregroundServiceResult!!.invoke(true)

        assertFalse(controller.isSharing)
        assertFalse(host.capture!!.isStarted)
        assertFalse(host.isForegroundServiceScreenSharing)
    }

    @Test
    fun testStartIsAbortedWhenNotAllowedAnymore() {
        controller.requestStart()
        controller.onPermissionGranted(permissionResultData)
        host.isScreenSharingAllowed = false

        host.foregroundServiceResult!!.invoke(true)

        assertFalse(controller.isSharing)
        assertFalse(controller.isPending)
        assertFalse(host.isForegroundServiceScreenSharing)
    }

    @Test
    fun testStop() {
        startSharing()
        val connections = host.connections.toList()
        val stateChangedCount = host.stateChangedCount

        controller.stop()
        controller.stop()

        assertFalse(controller.isSharing)
        assertTrue(host.capture!!.isReleased)
        assertFalse(host.isForegroundServiceScreenSharing)
        assertEquals(listOf(SESSION_ID_1, SESSION_ID_2), host.messages("unshareScreen"))
        connections.forEach { verify(it).removePeerConnection() }
        assertEquals(stateChangedCount + 1, host.stateChangedCount)
    }

    @Test
    fun testStopWhenMediaProjectionStops() {
        startSharing()

        host.onCaptureStop!!.invoke()

        assertFalse(controller.isSharing)
        assertTrue(host.capture!!.isReleased)
    }

    @Test
    fun testOffersAreRequestedOnceThePublisherIsConnected() {
        host.hasMCU = true
        startSharing()
        val publisher = host.connectionTo(OWN_SESSION_ID)

        controller.onParticipantJoined(SESSION_ID_3)
        assertTrue(host.messages("sendoffer").isEmpty())

        setIceConnectionState(publisher, IceConnectionState.CONNECTED)
        setIceConnectionState(publisher, IceConnectionState.CONNECTED)
        assertEquals(listOf(SESSION_ID_1, SESSION_ID_2), host.messages("sendoffer"))

        controller.onParticipantJoined(SESSION_ID_3)
        assertEquals(listOf(SESSION_ID_1, SESSION_ID_2, SESSION_ID_3), host.messages("sendoffer"))
    }

    @Test
    fun testConnectedEventAfterStopDoesNotAffectNextScreenSharing() {
        host.hasMCU = true
        startSharing()
        val oldPublisher = host.connectionTo(OWN_SESSION_ID)
        controller.stop()

        setIceConnectionState(oldPublisher, IceConnectionState.CONNECTED)
        assertTrue(host.messages("sendoffer").isEmpty())

        host.capture = FakeScreenCapture()
        startSharing()
        setIceConnectionState(host.connectionTo(OWN_SESSION_ID), IceConnectionState.CONNECTED)
        assertEquals(listOf(SESSION_ID_1, SESSION_ID_2), host.messages("sendoffer"))
    }

    @Test
    fun testJoinedAndLeftParticipantsWithoutMcu() {
        startSharing()

        controller.onParticipantJoined(SESSION_ID_3)
        val connection = host.connectionTo(SESSION_ID_3)
        controller.onParticipantLeft(SESSION_ID_3)

        verify(connection).removePeerConnection()
        assertEquals(3, host.connections.size)
    }

    @Test
    fun testParticipantsJoiningWhileNotSharingAreIgnored() {
        controller.onParticipantJoined(SESSION_ID_3)

        assertTrue(host.connections.isEmpty())
    }

    @Test
    fun testFailedConnectionWithoutMcuIsRetriedBeforeGivingUp() {
        startSharing()

        repeat(MAX_RETRIES) {
            val connection = host.connectionTo(SESSION_ID_1)
            setIceConnectionState(connection, IceConnectionState.FAILED)
            verify(connection).removePeerConnection()
        }
        assertEquals(List(MAX_RETRIES) { SESSION_ID_1 }, host.messages("unshareScreen"))

        setIceConnectionState(host.connectionTo(SESSION_ID_1), IceConnectionState.FAILED)

        // The other participants still get the screen.
        assertTrue(controller.isSharing)
        assertEquals(0, host.failedCount)
        assertEquals(2 + MAX_RETRIES, host.connections.size)
    }

    @Test
    fun testRejoiningParticipantResetsRetries() {
        startSharing()
        repeat(MAX_RETRIES + 1) {
            setIceConnectionState(host.connectionTo(SESSION_ID_1), IceConnectionState.FAILED)
        }

        controller.onParticipantLeft(SESSION_ID_1)
        controller.onParticipantJoined(SESSION_ID_1)
        setIceConnectionState(host.connectionTo(SESSION_ID_1), IceConnectionState.FAILED)

        assertEquals(2 + MAX_RETRIES + 2, host.connections.size)
    }

    @Test
    fun testSuccessfulConnectionResetsRetries() {
        startSharing()

        repeat(MAX_RETRIES) {
            setIceConnectionState(host.connectionTo(SESSION_ID_1), IceConnectionState.FAILED)
        }
        setIceConnectionState(host.connectionTo(SESSION_ID_1), IceConnectionState.CONNECTED)
        setIceConnectionState(host.connectionTo(SESSION_ID_1), IceConnectionState.FAILED)

        assertEquals(2 + MAX_RETRIES + 1, host.connections.size)
    }

    @Test
    fun testFailedPublisherStopsScreenSharingAfterRetries() {
        host.hasMCU = true
        startSharing()

        repeat(MAX_RETRIES + 1) {
            setIceConnectionState(host.connectionTo(OWN_SESSION_ID), IceConnectionState.FAILED)
        }

        assertFalse(controller.isSharing)
        assertEquals(1, host.failedCount)
        assertEquals(1 + MAX_RETRIES, host.connections.size)
    }

    @Test
    fun testFailedConnectionIsNotRetriedWhileReconnecting() {
        startSharing()
        host.isCallActive = false

        setIceConnectionState(host.connectionTo(SESSION_ID_1), IceConnectionState.FAILED)

        assertEquals(2, host.connections.size)
        assertTrue(controller.isSharing)
    }

    @Test
    fun testSuspendKeepsCapturingAndSharesAgainWithMcu() {
        host.hasMCU = true
        startSharing()
        val publisher = host.connectionTo(OWN_SESSION_ID)

        controller.suspend()

        verify(publisher).removePeerConnection()
        assertTrue(controller.isSharing)
        assertFalse(host.capture!!.isReleased)
        assertTrue(host.isForegroundServiceScreenSharing)

        host.ownSessionId = NEW_OWN_SESSION_ID
        controller.ensurePublisher()

        assertEquals(NEW_OWN_SESSION_ID, host.connections.last().sessionId)
    }

    @Test
    fun testSuspendSharesAgainWithJoinedParticipantsWithoutMcu() {
        startSharing()

        controller.suspend()
        controller.ensurePublisher()
        assertEquals(2, host.connections.size)

        controller.onParticipantJoined(SESSION_ID_1)
        assertEquals(SESSION_ID_1, host.connections.last().sessionId)
        assertEquals(3, host.connections.size)
    }

    @Test
    fun testEncryptionFailure() {
        host.canCreateConnections = false

        startSharing()

        assertEquals(2, host.encryptionFailedCount)
        assertTrue(host.connections.isEmpty())
        // Ending the call is up to the host.
        assertTrue(controller.isSharing)
    }

    companion object {
        private const val OWN_SESSION_ID = "own-session-id"
        private const val NEW_OWN_SESSION_ID = "new-own-session-id"
        private const val SESSION_ID_1 = "session-id-1"
        private const val SESSION_ID_2 = "session-id-2"
        private const val SESSION_ID_3 = "session-id-3"
        private const val MAX_RETRIES = 3
    }
}
