/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.call.e2ee

import org.webrtc.FrameDecryptor
import org.webrtc.FrameEncryptor
import org.webrtc.TalkKeyRing

/**
 * Frame encryption of the Talk WebRTC build, compatible with the web client's frame format.
 */
object TalkFrameCrypto : FrameCrypto {
    override fun createKeyRing(): FrameKeyRing = TalkFrameKeyRing(TalkKeyRing())

    override fun ratchetKey(key: ByteArray): ByteArray =
        checkNotNull(TalkKeyRing.ratchetKey(key)) { "Can not ratchet an empty key" }

    private class TalkFrameKeyRing(private val keyRing: TalkKeyRing) : FrameKeyRing {
        override fun setKey(key: ByteArray, keyIndex: Int): Boolean = keyRing.setKey(key, keyIndex)

        override fun createFrameEncryptor(): FrameEncryptor = keyRing.createFrameEncryptor()

        override fun createFrameDecryptor(): FrameDecryptor = keyRing.createFrameDecryptor()

        override fun dispose() = keyRing.dispose()
    }
}
