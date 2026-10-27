/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.call

import android.content.Intent
import android.util.Log
import com.nextcloud.talk.models.json.signaling.NCSignalingMessageDto
import com.nextcloud.talk.webrtc.PeerConnectionWrapper
import com.nextcloud.talk.webrtc.PeerConnectionWrapper.PeerConnectionObserver
import org.webrtc.MediaStream
import org.webrtc.PeerConnection.IceConnectionState

/**
 * Shares the local screen in a call, from asking for the permission until the screen sharing is stopped.
 *
 * Without MCU the screen is sent through one connection per remote participant, with MCU through a single publisher
 * connection, for which the MCU then sends offers to the remote participants on request. Failed connections are
 * created again a few times, and while the call reconnects the screen keeps being captured, so it is shared again
 * once back in the call.
 *
 * The state is changed both from the main thread and from the signaling thread, so the methods can be called from
 * any thread unless stated otherwise.
 */
class LocalScreenShareController(private val host: Host, private val runOnMainThread: (Runnable) -> Unit) {

    /**
     * Provides what the screen sharing needs from the call.
     */
    interface Host {
        val hasMCU: Boolean

        /**
         * The own signaling session, which the publisher connection is created for with MCU.
         */
        val ownSessionId: String?

        val participantSessionIds: Collection<String>

        /**
         * Whether the call is joined, so connections can be created.
         */
        val isCallActive: Boolean

        val isScreenSharingAllowed: Boolean

        /**
         * Adds the media projection type to the call foreground service, which is needed before the screen can be
         * captured.
         */
        fun startForegroundServiceScreenSharing(onResult: (isReady: Boolean) -> Unit)

        fun stopForegroundServiceScreenSharing()

        /**
         * Returns null if the capture can not be created, for example, because the call is being hung up.
         */
        fun createScreenCapture(permissionResultData: Intent, onStop: () -> Unit): ScreenCapture?

        /**
         * @throws IllegalStateException if the connection has to be end-to-end encrypted but the keys are missing
         */
        fun createPeerConnection(sessionId: String, stream: MediaStream): PeerConnectionWrapper

        fun send(message: NCSignalingMessageDto, sessionId: String)

        /**
         * Called on the main thread when [isSharing] or [isPending] changed.
         */
        fun onStateChanged()

        /**
         * Called on the main thread when the screen sharing could not be started or failed.
         */
        fun onScreenSharingFailed()

        /**
         * Called when a connection could not be created because it could not be end-to-end encrypted.
         */
        fun onEncryptionFailed()
    }

    // Guards the whole state; host methods that may tear the screen sharing down are not called while holding it.
    private val lock = Any()

    @Volatile
    private var capture: ScreenCapture? = null

    // Set from asking for the permission until the screen sharing started, failed or was cancelled.
    @Volatile
    private var isPendingInternal = false

    private val connections: MutableList<PeerConnectionWrapper> = ArrayList()
    private val observers: MutableMap<PeerConnectionWrapper, PeerConnectionObserver> = HashMap()

    // Failed connections are created again a few times per remote session before giving up.
    private val retryCounts: MutableMap<String, Int> = HashMap()

    private var isPublisherConnected = false

    val isSharing: Boolean
        get() = capture != null

    val isPending: Boolean
        get() = isPendingInternal

    /**
     * Marks the screen sharing as being started, which has to be followed by [onPermissionGranted] or
     * [cancelPending]. Returns false if the screen is already shared or being started.
     */
    fun requestStart(): Boolean {
        synchronized(lock) {
            if (capture != null || isPendingInternal) {
                return false
            }
            isPendingInternal = true
        }
        notifyStateChanged()
        return true
    }

    fun cancelPending() {
        synchronized(lock) {
            isPendingInternal = false
        }
        notifyStateChanged()
    }

    /**
     * Must be called on the main thread.
     */
    fun onPermissionGranted(permissionResultData: Intent) {
        if (!isPendingInternal || !host.isCallActive) {
            Log.d(TAG, "Screen sharing was cancelled while asking for the permission")
            cancelPending()
            return
        }

        // On Android 14+ the media projection can only be started once the foreground service has the media
        // projection type, which in turn requires the permission to have been granted.
        host.startForegroundServiceScreenSharing { isReady ->
            if (isReady) {
                start(permissionResultData)
            } else {
                cancelPending()
                host.onScreenSharingFailed()
            }
        }
    }

    private fun start(permissionResultData: Intent) {
        val newCapture = startCapture(permissionResultData) ?: return

        val isCancelled = synchronized(lock) {
            // stop() may have been called from the signaling thread while the capture was being started.
            if (isPendingInternal) {
                capture = newCapture
                isPendingInternal = false
                isPublisherConnected = false
            }
            capture !== newCapture
        }
        if (isCancelled) {
            Log.d(TAG, "Screen sharing was cancelled while starting it")
            newCapture.release()
            host.stopForegroundServiceScreenSharing()
            notifyStateChanged()
        } else {
            notifyStateChanged()
            if (host.hasMCU) {
                addConnection(host.ownSessionId)
            } else {
                host.participantSessionIds.forEach { addConnection(it) }
            }
        }
    }

    /**
     * Returns the started capture, or null if the screen sharing could not be started or was cancelled.
     */
    private fun startCapture(permissionResultData: Intent): ScreenCapture? {
        val canStart = capture == null && isPendingInternal && host.isScreenSharingAllowed && host.isCallActive
        val newCapture = if (canStart) {
            host.createScreenCapture(permissionResultData) { runOnMainThread { stop() } }
        } else {
            null
        }
        return when {
            newCapture == null -> {
                cancelPending()
                host.stopForegroundServiceScreenSharing()
                null
            }

            !newCapture.start() -> {
                newCapture.release()
                cancelPending()
                host.stopForegroundServiceScreenSharing()
                host.onScreenSharingFailed()
                null
            }

            else -> newCapture
        }
    }

    /**
     * Only the first call after starting the screen sharing tears it down. If the screen sharing is still being
     * started it is cancelled instead.
     */
    fun stop() {
        val stoppedCapture: ScreenCapture?
        var stoppedConnections: List<PeerConnectionWrapper> = emptyList()
        var wasPending = false
        synchronized(lock) {
            stoppedCapture = capture
            if (stoppedCapture == null) {
                wasPending = isPendingInternal
                isPendingInternal = false
            } else {
                capture = null
                isPublisherConnected = false
                retryCounts.clear()
                stoppedConnections = connections.toList()
                connections.clear()
            }
        }
        if (stoppedCapture == null) {
            if (wasPending) {
                notifyStateChanged()
            }
            return
        }

        sendScreenMessage("unshareScreen", host.participantSessionIds)

        stoppedConnections.forEach { closeConnection(it) }

        stoppedCapture.release()

        host.stopForegroundServiceScreenSharing()
        notifyStateChanged()
    }

    /**
     * Closes the connections while the call reconnects, but keeps capturing the screen, so it is shared again once
     * back in the call without asking the user for the permission again.
     */
    fun suspend() {
        val suspendedConnections = synchronized(lock) {
            isPublisherConnected = false
            retryCounts.clear()
            connections.toList().also { connections.clear() }
        }
        suspendedConnections.forEach { closeConnection(it) }
    }

    /**
     * Sets up the publisher connection with MCU if the screen is shared, for example, after reconnecting.
     */
    fun ensurePublisher() {
        if (host.hasMCU) {
            addConnection(host.ownSessionId)
        }
    }

    fun onParticipantJoined(sessionId: String) {
        if (capture == null) {
            return
        }
        if (host.hasMCU) {
            // Participants joining before the publisher is connected are requested once it is connected.
            val isConnected = synchronized(lock) { isPublisherConnected }
            if (isConnected) {
                sendScreenMessage("sendoffer", listOf(sessionId))
            }
        } else {
            addConnection(sessionId)
        }
    }

    fun onParticipantLeft(sessionId: String?) {
        val connection = synchronized(lock) {
            // The connection is created again from scratch if the participant joins again.
            retryCounts.remove(sessionId)
            connections.firstOrNull { it.sessionId == sessionId }?.also { connections.remove(it) }
        } ?: return
        closeConnection(connection)
    }

    private fun addConnection(sessionId: String?) {
        val isEncryptionFailed = synchronized(lock) {
            val stream = capture?.stream ?: return
            if (sessionId == null || connections.any { it.sessionId == sessionId }) {
                return
            }

            val connection = try {
                host.createPeerConnection(sessionId, stream)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Not creating a screen share connection to $sessionId", e)
                null
            }
            if (connection != null) {
                val observer = ConnectionObserver(connection)
                observers[connection] = observer
                connection.addObserver(observer)
                connections.add(connection)
            }
            connection == null
        }
        // Ending the call also stops the screen sharing, so it is not done while holding the lock.
        if (isEncryptionFailed) {
            host.onEncryptionFailed()
        }
    }

    private fun closeConnection(connection: PeerConnectionWrapper) {
        synchronized(lock) {
            observers.remove(connection)
        }?.let { connection.removeObserver(it) }
        connection.removePeerConnection()
    }

    /**
     * With MCU "sendoffer" requests the MCU to send the offer for the screen publisher to the remote participant.
     */
    private fun sendScreenMessage(type: String, sessionIds: Collection<String>) {
        sessionIds.forEach { sessionId ->
            host.send(NCSignalingMessageDto(type = type, roomType = ROOM_TYPE_SCREEN), sessionId)
        }
    }

    private fun notifyStateChanged() {
        runOnMainThread { host.onStateChanged() }
    }

    private inner class ConnectionObserver(private val connection: PeerConnectionWrapper) : PeerConnectionObserver {
        override fun onStreamAdded(mediaStream: MediaStream) {
            // unused atm
        }

        override fun onStreamRemoved(mediaStream: MediaStream) {
            // unused atm
        }

        override fun onIceConnectionStateChanged(iceConnectionState: IceConnectionState) {
            runOnMainThread {
                if (iceConnectionState == IceConnectionState.CONNECTED) {
                    onConnected()
                } else if (iceConnectionState == IceConnectionState.FAILED) {
                    // Closed connections are ignored there.
                    onFailed()
                }
            }
        }

        /**
         * Must be called on the main thread.
         */
        private fun onConnected() {
            // The check and the update are done together, as the screen sharing may be stopped from the signaling
            // thread in between.
            val isFirstPublisherConnection = synchronized(lock) {
                // The event may have been queued before the connection was closed, even if sharing started again.
                if (!connections.contains(connection)) {
                    return
                }
                retryCounts.remove(connection.sessionId)
                (host.hasMCU && !isPublisherConnected).also {
                    if (it) {
                        isPublisherConnected = true
                    }
                }
            }
            if (isFirstPublisherConnection) {
                sendScreenMessage("sendoffer", host.participantSessionIds)
            }
        }

        /**
         * Must be called on the main thread.
         */
        private fun onFailed() {
            val sessionId = connection.sessionId
            val shouldRetry = synchronized(lock) {
                if (!connections.remove(connection)) {
                    return
                }
                if (host.hasMCU) {
                    isPublisherConnected = false
                }
                val retryCount = retryCounts[sessionId] ?: 0
                retryCounts[sessionId] = retryCount + 1
                retryCount < MAX_RETRIES
            }
            closeConnection(connection)

            if (!host.isCallActive) {
                // The connections are set up again once reconnected.
                return
            }
            if (shouldRetry) {
                Log.w(TAG, "Screen share connection to $sessionId failed, creating it again")
                // The remote participants close the connection receiving the screen, so the new offer is not handled
                // by the failed connection.
                sendScreenMessage("unshareScreen", if (host.hasMCU) host.participantSessionIds else listOf(sessionId))
                addConnection(sessionId)
            } else if (host.hasMCU) {
                Log.e(TAG, "Screen share publisher connection failed")
                stop()
                host.onScreenSharingFailed()
            } else {
                Log.e(TAG, "Screen share connection to $sessionId failed, not sharing the screen with it anymore")
            }
        }
    }

    companion object {
        private val TAG = LocalScreenShareController::class.java.simpleName

        private const val ROOM_TYPE_SCREEN = "screen"
        private const val MAX_RETRIES = 3
    }
}
