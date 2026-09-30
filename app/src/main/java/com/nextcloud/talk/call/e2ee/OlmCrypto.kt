/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.call.e2ee

/**
 * An Olm message as it travels inside the key exchange payloads: type 0 is a pre-key message, type 1 a normal one.
 */
data class OlmMessage(val type: Int, val body: String) {
    companion object {
        const val TYPE_PRE_KEY = 0
        const val TYPE_NORMAL = 1
    }
}

/**
 * An Olm session with one other participant, used in both directions.
 */
interface OlmSession {
    fun encrypt(plaintext: String): OlmMessage

    fun decrypt(message: OlmMessage): String
}

/**
 * The Olm account of one call session, backed by vodozemac in the app and by a fake in tests.
 */
interface OlmCrypto {
    /** Curve25519 identity key, unpadded base64. */
    val identityKey: String

    /** Creates a one-time key and marks it as published. */
    fun createOneTimeKey(): String

    fun createOutboundSession(theirIdentityKey: String, theirOneTimeKey: String): OlmSession

    /**
     * Creates the session from a pre-key message and returns it with the decrypted message, as the message can not
     * be decrypted again afterwards.
     */
    fun createInboundSession(preKeyMessage: OlmMessage): Pair<OlmSession, String>
}
