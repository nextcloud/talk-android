/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.signaling

import com.nextcloud.talk.call.e2ee.EncryptionMessage

/**
 * Helper class to register and notify EncryptionMessageListeners.
 *
 * This class is only meant for internal use by SignalingMessageReceiver; listeners must register themselves against
 * a SignalingMessageReceiver rather than against an EncryptionMessageNotifier.
 */
internal class EncryptionMessageNotifier {
    private val listeners = LinkedHashSet<SignalingMessageReceiver.EncryptionMessageListener>()

    @Synchronized
    fun addListener(listener: SignalingMessageReceiver.EncryptionMessageListener?) {
        requireNotNull(listener) { "EncryptionMessageListener can not be null" }
        listeners.add(listener)
    }

    @Synchronized
    fun removeListener(listener: SignalingMessageReceiver.EncryptionMessageListener?) {
        listeners.remove(listener)
    }

    @Synchronized
    fun notifyEncryptionMessage(sessionId: String, message: EncryptionMessage) {
        for (listener in listeners.toList()) {
            listener.onEncryptionMessage(sessionId, message)
        }
    }
}
