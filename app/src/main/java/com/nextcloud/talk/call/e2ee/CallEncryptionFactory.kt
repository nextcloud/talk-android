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

    // ponytail: false until the WebRTC build with TalkKeyRing and the vodozemac AAR are available, then the two
    // adapters (TalkKeyRing -> FrameCrypto, VodozemacAccount -> OlmCrypto) go here and this becomes true
    const val IS_AVAILABLE = false

    fun create(
        ownSessionId: String,
        sendMessage: (sessionId: String, message: EncryptionMessage) -> Unit
    ): CallEncryption? {
        Log.w(TAG, "Frame encryption is not available in this build, can not encrypt the call of $ownSessionId")
        return null
    }
}
