/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MentionChipModelParsingTest {

    private val activeUserId = "alice"
    private val baseUrl = "https://cloud.example.com"

    private fun mentionParams(
        type: String,
        id: String,
        name: String = "Alice",
        server: String? = null
    ): Map<String, Map<String, String>> {
        val params = mutableMapOf("type" to type, "id" to id, "name" to name)
        server?.let { params["server"] = it }
        return mapOf("mention1" to params)
    }

    @Test
    fun `local user id equals rawId`() {
        val model = parseMentionChipModel(
            key = "mention1",
            messageParameters = mentionParams(type = "user", id = "bob"),
            activeUserId = activeUserId,
            activeUserBaseUrl = baseUrl,
            roomToken = null
        )
        assertEquals("bob", model!!.id)
        assertEquals("bob", model.rawId)
        assertFalse(model.isFederated)
    }

    @Test
    fun `federated user id is rawId at server`() {
        val model = parseMentionChipModel(
            key = "mention1",
            messageParameters = mentionParams(type = "user", id = "bob", server = "remote.example.com"),
            activeUserId = activeUserId,
            activeUserBaseUrl = baseUrl,
            roomToken = null
        )
        assertEquals("bob@remote.example.com", model!!.id)
        assertEquals("bob", model.rawId)
        assertTrue(model.isFederated)
    }

    @Test
    fun `unknown mention type returns null`() {
        val model = parseMentionChipModel(
            key = "mention1",
            messageParameters = mapOf("mention1" to mapOf("type" to "unknown", "id" to "x", "name" to "X")),
            activeUserId = activeUserId,
            activeUserBaseUrl = baseUrl,
            roomToken = null
        )
        assertNull(model)
    }

    @Test
    fun `missing key returns null`() {
        val model = parseMentionChipModel(
            key = "mention99",
            messageParameters = mentionParams(type = "user", id = "bob"),
            activeUserId = activeUserId,
            activeUserBaseUrl = baseUrl,
            roomToken = null
        )
        assertNull(model)
    }

    @Test
    fun `isSelfMention is true when rawId equals activeUserId`() {
        val model = parseMentionChipModel(
            key = "mention1",
            messageParameters = mentionParams(type = "user", id = activeUserId),
            activeUserId = activeUserId,
            activeUserBaseUrl = baseUrl,
            roomToken = null
        )
        assertTrue(model!!.isSelfMention)
    }

    @Test
    fun `isSelfMention is false when rawId differs from activeUserId`() {
        val model = parseMentionChipModel(
            key = "mention1",
            messageParameters = mentionParams(type = "user", id = "carol"),
            activeUserId = activeUserId,
            activeUserBaseUrl = baseUrl,
            roomToken = null
        )
        assertFalse(model!!.isSelfMention)
    }

    @Test
    fun `isSelfMention is false when activeUserId is null`() {
        val model = parseMentionChipModel(
            key = "mention1",
            messageParameters = mentionParams(type = "user", id = "carol"),
            activeUserId = null,
            activeUserBaseUrl = baseUrl,
            roomToken = null
        )
        assertFalse(model!!.isSelfMention)
    }
}
