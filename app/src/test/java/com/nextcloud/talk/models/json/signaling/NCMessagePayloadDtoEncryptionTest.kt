/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models.json.signaling

import com.bluelinelabs.logansquare.LoganSquare
import com.nextcloud.talk.call.e2ee.EncryptionMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The key exchange payloads as the web client sends them survive LoganSquare in both directions.
 */
class NCMessagePayloadDtoEncryptionTest {

    @Test
    fun parsesStartMessage() {
        val payload = LoganSquare.parse(
            """{"id":"abc","type":"encryption.start","identity":"idKey","key":"oneTimeKey"}""",
            NCMessagePayloadDto::class.java
        )

        val message = EncryptionMessage.fromPayload(payload)!!
        assertEquals("encryption.start", message.type)
        assertEquals("abc", message.id)
        assertEquals("idKey", message.identity)
        assertEquals("oneTimeKey", message.key)
    }

    @Test
    fun parsesOlmMessage() {
        val payload = LoganSquare.parse(
            """{"id":"abc","type":"encryption.finish","key":{"type":0,"body":"AwoQ"}}""",
            NCMessagePayloadDto::class.java
        )

        val olmKey = EncryptionMessage.fromPayload(payload)!!.olmKey!!
        assertEquals(0, olmKey.type)
        assertEquals("AwoQ", olmKey.body)
    }

    private fun roundTrip(message: EncryptionMessage): Pair<String, EncryptionMessage> {
        val json = LoganSquare.serialize(message.toPayload())
        return json to EncryptionMessage.fromPayload(LoganSquare.parse(json, NCMessagePayloadDto::class.java))!!
    }

    @Test
    fun serializesStartMessage() {
        val start = EncryptionMessage("encryption.start", id = "abc", identity = "idKey", key = "oneTimeKey")

        val (json, parsed) = roundTrip(start)

        assertTrue(json, json.contains(""""key":"oneTimeKey""""))
        assertEquals(start, parsed)
    }

    @Test
    fun serializesOlmMessage() {
        val setKey = EncryptionMessage("encryption.setkey", id = "abc", key = mapOf("type" to 1, "body" to "AwoQ"))

        val (json, parsed) = roundTrip(setKey)

        assertTrue(json, json.contains(""""body":"AwoQ"""") && json.contains(""""type":1"""))
        assertEquals("abc", parsed.id)
        assertEquals(setKey.olmKey, parsed.olmKey)
    }

    @Test
    fun serializesErrorMessage() {
        val error = EncryptionMessage("encryption.error", error = "Finish has wrong id")

        val (json, parsed) = roundTrip(error)

        assertEquals("""{"error":"Finish has wrong id","type":"encryption.error"}""", json)
        assertEquals(error, parsed)
    }

    @Test
    fun otherPayloadsAreNotEncryptionMessages() {
        val payload = LoganSquare.parse("""{"type":"offer","sdp":"v=0"}""", NCMessagePayloadDto::class.java)

        assertFalse(EncryptionMessage.isEncryptionMessage(payload.type))
        assertEquals(null, EncryptionMessage.fromPayload(payload))
    }
}
