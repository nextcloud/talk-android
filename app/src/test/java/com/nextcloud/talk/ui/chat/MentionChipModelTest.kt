/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MentionChipModelTest {

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
            messageParameters = mentionParams(type = "user", id = activeUserId, name = "Alice"),
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
            messageParameters = mentionParams(
                type = "user",
                id = "bob",
                name = "Bob",
                server = "remote.example.com"
            ),
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

    @Test
    fun `navigation guard skips when already in 1-on-1 with that user`() {
        val userId = "bob"
        val isAlreadyInOneToOne = true
        val conversationParticipantName = "bob"

        val shouldNavigate = shouldNavigateToUserConversation(
            isCurrentConversationOneToOne = isAlreadyInOneToOne,
            currentConversationName = conversationParticipantName,
            targetUserId = userId
        )
        assertFalse(shouldNavigate)
    }

    @Test
    fun `navigation guard proceeds when in a group conversation`() {
        val shouldNavigate = shouldNavigateToUserConversation(
            isCurrentConversationOneToOne = false,
            currentConversationName = "devs",
            targetUserId = "bob"
        )
        assertTrue(shouldNavigate)
    }

    @Test
    fun `navigation guard proceeds when in 1-on-1 with a different user`() {
        val shouldNavigate = shouldNavigateToUserConversation(
            isCurrentConversationOneToOne = true,
            currentConversationName = "carol",
            targetUserId = "bob"
        )
        assertTrue(shouldNavigate)
    }

    /**
     * Pure extraction of the guard condition in ChatActivity.onMessageEvent so it can be
     * verified independently of the Activity lifecycle.
     */
    private fun shouldNavigateToUserConversation(
        isCurrentConversationOneToOne: Boolean,
        currentConversationName: String?,
        targetUserId: String
    ): Boolean = !isCurrentConversationOneToOne || currentConversationName != targetUserId
}
