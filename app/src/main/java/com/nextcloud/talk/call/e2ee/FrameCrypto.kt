/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.call.e2ee

import org.webrtc.RtpReceiver
import org.webrtc.RtpSender

/**
 * The frame keys of one participant. Cryptors attached from it keep working after [dispose], but nothing can be
 * attached anymore then.
 */
interface FrameKeyRing {
    /** Stores the 32 byte key material for the given index and makes it the current key. */
    fun setKey(key: ByteArray, keyIndex: Int): Boolean

    /** Encrypts what the sender sends from now on, false when the ring is disposed already. */
    fun attachEncryptor(sender: RtpSender): Boolean

    /** Decrypts what the receiver receives from now on, false when the ring is disposed already. */
    fun attachDecryptor(receiver: RtpReceiver): Boolean

    fun dispose()
}

/**
 * Frame encryption as provided by the WebRTC library.
 */
interface FrameCrypto {
    fun createKeyRing(): FrameKeyRing

    /** Derives the next key material from the given one, like the web client's ratchet. */
    fun ratchetKey(key: ByteArray): ByteArray
}
