/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.call.e2ee

import com.nextcloud.talk.olm.VodozemacAccount
import com.nextcloud.talk.olm.VodozemacMessage
import com.nextcloud.talk.olm.VodozemacMessageKind
import com.nextcloud.talk.olm.VodozemacSession

/**
 * Olm through vodozemac, which talks libolm's protocol and can exchange keys with the web client.
 *
 * The native objects are released by vodozemac's cleaner once they are no longer referenced.
 */
class VodozemacOlmCrypto(private val account: VodozemacAccount = VodozemacAccount()) : OlmCrypto {
    override val identityKey: String by lazy { account.identityKey() }

    override fun createOneTimeKey(): String = account.createOneTimeKey()

    override fun createOutboundSession(theirIdentityKey: String, theirOneTimeKey: String): OlmSession =
        VodozemacOlmSession(account.createOutboundSession(theirIdentityKey, theirOneTimeKey))

    override fun createInboundSession(preKeyMessage: OlmMessage): Pair<OlmSession, String> {
        val inbound = account.createInboundSession(preKeyMessage.toVodozemac())
        return VodozemacOlmSession(inbound.session) to inbound.plaintext
    }

    private class VodozemacOlmSession(private val session: VodozemacSession) : OlmSession {
        override fun encrypt(plaintext: String): OlmMessage = session.encrypt(plaintext).toOlmMessage()

        override fun decrypt(message: OlmMessage): String = session.decrypt(message.toVodozemac())
    }

    private companion object {
        fun OlmMessage.toVodozemac(): VodozemacMessage {
            val kind = when (type) {
                OlmMessage.TYPE_PRE_KEY -> VodozemacMessageKind.PRE_KEY
                OlmMessage.TYPE_NORMAL -> VodozemacMessageKind.NORMAL
                else -> throw IllegalArgumentException("Unknown Olm message type $type")
            }
            return VodozemacMessage(kind, body)
        }

        fun VodozemacMessage.toOlmMessage(): OlmMessage {
            val type = when (kind) {
                VodozemacMessageKind.PRE_KEY -> OlmMessage.TYPE_PRE_KEY
                VodozemacMessageKind.NORMAL -> OlmMessage.TYPE_NORMAL
            }
            return OlmMessage(type, body)
        }
    }
}
