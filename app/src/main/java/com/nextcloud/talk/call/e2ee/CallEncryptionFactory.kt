/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.call.e2ee

import android.util.Log

/**
 * Creates the key exchange of a call with the Olm and frame encryption implementations of this build.
 */
object CallEncryptionFactory {
    private val TAG = CallEncryptionFactory::class.java.simpleName

    /**
     * Returns null when the native libraries can not be used, the call then ends instead of being sent unencrypted.
     */
    @Suppress("TooGenericExceptionCaught")
    fun create(
        ownSessionId: String,
        sendMessage: (sessionId: String, message: EncryptionMessage) -> Unit
    ): CallEncryption? =
        try {
            CallEncryption(ownSessionId, VodozemacOlmCrypto(), TalkFrameCrypto, sendMessage)
        } catch (e: Exception) {
            Log.e(TAG, "Could not set up the key exchange for $ownSessionId", e)
            null
        } catch (e: LinkageError) {
            Log.e(TAG, "Could not load the native encryption libraries for $ownSessionId", e)
            null
        }
}
