/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.call.e2ee

import com.nextcloud.talk.models.json.signaling.NCMessagePayloadDto
import com.nextcloud.talk.models.json.signaling.OlmMessageDto

/**
 * The payload of a key exchange signaling message, with the field names of the web client.
 *
 * [key] is the one-time key (a String) in a start message and an Olm message (a Map with "type" and "body")
 * otherwise.
 */
data class EncryptionMessage(
    val type: String,
    val id: String? = null,
    val identity: String? = null,
    val key: Any? = null,
    val error: String? = null
) {
    val olmKey: OlmMessage?
        get() {
            val map = key as? Map<*, *> ?: return null
            val type = (map["type"] as? Number)?.toInt() ?: return null
            val body = map["body"] as? String ?: return null
            return OlmMessage(type, body)
        }

    // The untyped "key" can only be parsed by LoganSquare, so the typed fields are set for sending
    fun toPayload(): NCMessagePayloadDto =
        NCMessagePayloadDto().also {
            it.type = type
            it.id = id
            it.identity = identity
            it.keyOneTimeKey = key as? String
            it.keyOlmMessage = olmKey?.let { olmKey -> OlmMessageDto(olmKey.type, olmKey.body) }
            it.error = error
        }

    companion object {
        const val START = "encryption.start"
        const val FINISH = "encryption.finish"
        const val SET_KEY = "encryption.setkey"
        const val GOT_KEY = "encryption.gotkey"
        const val ERROR = "encryption.error"

        private val TYPES = setOf(START, FINISH, SET_KEY, GOT_KEY, ERROR)

        fun isEncryptionMessage(type: String?): Boolean = type in TYPES

        fun fromPayload(payload: NCMessagePayloadDto): EncryptionMessage? {
            val type = payload.type?.takeIf { isEncryptionMessage(it) } ?: return null
            val key = payload.key
                ?: payload.keyOneTimeKey
                ?: payload.keyOlmMessage?.let { mapOf("type" to it.type, "body" to it.body) }
            return EncryptionMessage(type, payload.id, payload.identity, key, payload.error)
        }

        fun olmKey(message: OlmMessage): Map<String, Any> = mapOf("type" to message.type, "body" to message.body)
    }
}
