/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.webrtc

import android.util.Log
import com.nextcloud.talk.call.e2ee.CallEncryption
import com.nextcloud.talk.call.e2ee.CallEncryptionFactory
import com.nextcloud.talk.call.e2ee.EncryptionMessage

/**
 * Owns the key exchange of a signaling session: one per joined room and session, like the web client. It is created
 * when a room is joined while calls have to be encrypted and the MCU is there, and closed when the room or the session
 * is gone.
 *
 * @param isEnabled whether calls have to be end-to-end encrypted, read at every room join
 * @param create builds the key exchange, null when the native libraries can not be used
 */
class CallEncryptionLifecycle(
    private val isEnabled: () -> Boolean,
    private val create: (ownSessionId: String, send: (String, EncryptionMessage) -> Unit) -> CallEncryption? =
        CallEncryptionFactory::create
) {
    /** The key exchange of the joined room, null while there is none. */
    @Volatile
    var current: CallEncryption? = null
        private set

    /**
     * @param alreadyInRoom whether the same room was joined with the same session already, the exchange is kept then
     */
    fun roomJoined(
        ownSessionId: String?,
        hasMCU: Boolean,
        alreadyInRoom: Boolean,
        send: (String, EncryptionMessage) -> Unit
    ) {
        if (!alreadyInRoom) {
            close()
        }
        if (!isEnabled() || !hasMCU || ownSessionId == null || current != null) {
            return
        }
        current = create(ownSessionId, send)
        Log.d(TAG, "Key exchange ${if (current != null) "created" else "not available"} for session $ownSessionId")
    }

    /** The room or the signaling session is gone, and the keys with it. */
    fun close() {
        current?.close()
        current = null
    }

    fun usersJoined(sessionIds: List<String>) {
        current?.usersJoined(sessionIds)
    }

    fun usersLeft(sessionIds: List<String>) {
        current?.usersLeft(sessionIds)
    }

    fun handleMessage(sessionId: String, message: EncryptionMessage) {
        val current = current
        if (current != null) {
            current.handleMessage(sessionId, message)
        } else {
            Log.w(TAG, "Dropped ${message.type} from $sessionId, no key exchange")
        }
    }

    private companion object {
        private val TAG = CallEncryptionLifecycle::class.java.simpleName
    }
}
