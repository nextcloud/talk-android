/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.call.e2ee

import org.webrtc.FrameDecryptor
import org.webrtc.FrameEncryptor

/**
 * The frame keys of one participant. Encryptors and decryptors created from it keep using it after [dispose].
 */
interface FrameKeyRing {
    /** Stores the 32 byte key material for the given index and makes it the current key. */
    fun setKey(key: ByteArray, keyIndex: Int): Boolean

    fun createFrameEncryptor(): FrameEncryptor

    fun createFrameDecryptor(): FrameDecryptor

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
