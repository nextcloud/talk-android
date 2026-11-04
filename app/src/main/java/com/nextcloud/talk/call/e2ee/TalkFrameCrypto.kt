/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.call.e2ee

import org.webrtc.RtpReceiver
import org.webrtc.RtpSender
import org.webrtc.TalkKeyRing

/**
 * Frame encryption of the Talk WebRTC build, compatible with the web client's frame format.
 */
object TalkFrameCrypto : FrameCrypto {
    override fun createKeyRing(): FrameKeyRing = TalkFrameKeyRing(TalkKeyRing())

    override fun ratchetKey(key: ByteArray): ByteArray =
        checkNotNull(TalkKeyRing.ratchetKey(key)) { "Can not ratchet an empty key" }

    // Attaching and disposing are serialized: dispose() frees the native cryptors, so a cryptor created on the
    // signaling thread has to be attached before the ring can go away
    private class TalkFrameKeyRing(private val keyRing: TalkKeyRing) : FrameKeyRing {
        private var disposed = false

        @Synchronized
        override fun setKey(key: ByteArray, keyIndex: Int): Boolean = !disposed && keyRing.setKey(key, keyIndex)

        @Synchronized
        override fun attachEncryptor(sender: RtpSender): Boolean {
            if (disposed) return false
            sender.setFrameEncryptor(keyRing.createFrameEncryptor())
            return true
        }

        @Synchronized
        override fun attachDecryptor(receiver: RtpReceiver): Boolean {
            if (disposed) return false
            receiver.setFrameDecryptor(keyRing.createFrameDecryptor())
            return true
        }

        @Synchronized
        override fun dispose() {
            if (!disposed) {
                disposed = true
                keyRing.dispose()
            }
        }
    }
}
