/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MentionChipClickabilityTest {

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
    fun `local user that is not the active user is clickable`() {
        val model = parseMentionChipModel(
            key = "mention1",
            messageParameters = mentionParams(type = "user", id = "bob", name = "Bob"),
            activeUserId = activeUserId,
            activeUserBaseUrl = baseUrl,
            roomToken = null
        )
        assertNotNull(model)
        assertTrue(model!!.isClickableUserMention)
    }

    @Test
    fun `self-mention is not clickable`() {
        val model = parseMentionChipModel(
            key = "mention1",
            messageParameters = mentionParams(type = "user", id = activeUserId),
            activeUserId = activeUserId,
            activeUserBaseUrl = baseUrl,
            roomToken = null
        )
        assertNotNull(model)
        assertFalse(model!!.isClickableUserMention)
    }

    @Test
    fun `federated user mention is not clickable`() {
        val model = parseMentionChipModel(
            key = "mention1",
            messageParameters = mentionParams(type = "user", id = "bob", server = "remote.example.com"),
            activeUserId = activeUserId,
            activeUserBaseUrl = baseUrl,
            roomToken = null
        )
        assertNotNull(model)
        assertFalse(model!!.isClickableUserMention)
    }

    @Test
    fun `group mention is not clickable`() {
        val model = parseMentionChipModel(
            key = "mention1",
            messageParameters = mentionParams(type = "user-group", id = "devs", name = "Devs"),
            activeUserId = activeUserId,
            activeUserBaseUrl = baseUrl,
            roomToken = null
        )
        assertNotNull(model)
        assertFalse(model!!.isClickableUserMention)
    }

    @Test
    fun `call mention is not clickable`() {
        val model = parseMentionChipModel(
            key = "mention1",
            messageParameters = mentionParams(type = "call", id = "room", name = "all"),
            activeUserId = activeUserId,
            activeUserBaseUrl = baseUrl,
            roomToken = null
        )
        assertNotNull(model)
        assertFalse(model!!.isClickableUserMention)
    }

    @Test
    fun `guest mention is not clickable`() {
        val model = parseMentionChipModel(
            key = "mention1",
            messageParameters = mentionParams(type = "guest", id = "guest/abc", name = "Guest"),
            activeUserId = activeUserId,
            activeUserBaseUrl = baseUrl,
            roomToken = null
        )
        assertNotNull(model)
        assertFalse(model!!.isClickableUserMention)
    }
}
