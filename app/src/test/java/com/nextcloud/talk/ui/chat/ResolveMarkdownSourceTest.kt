/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chat

import com.nextcloud.talk.chat.ui.model.ChatMessageUi
import com.nextcloud.talk.chat.ui.model.MessageStatusIcon
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ResolveMarkdownSourceTest {

    private fun makeMessage(plainMessage: String, params: Map<String, Map<String, String>> = emptyMap()) =
        ChatMessageUi(
            id = 1,
            message = plainMessage,
            plainMessage = plainMessage,
            renderMarkdown = true,
            actorDisplayName = "Test",
            isThread = false,
            threadTitle = "",
            threadReplies = 0,
            incoming = true,
            isDeleted = false,
            avatarUrl = null,
            statusIcon = MessageStatusIcon.SENT,
            timestamp = 0L,
            date = LocalDate.now(),
            content = null,
            messageParameters = params
        )

    @Test
    fun `mention tokens are replaced with at-name`() {
        val message = makeMessage(
            "Hello {mention1}!",
            mapOf("mention1" to mapOf("type" to "user", "name" to "alice"))
        )
        assertEquals("Hello @alice!", resolveMarkdownSource(message))
    }

    @Test
    fun `non-mention parameter tokens are replaced with name only`() {
        val message = makeMessage(
            "See {file1} here",
            mapOf("file1" to mapOf("type" to "file", "name" to "report.pdf"))
        )
        assertEquals("See report.pdf here", resolveMarkdownSource(message))
    }

    @Test
    fun `message without parameters is returned unchanged`() {
        val message = makeMessage("No params here")
        assertEquals("No params here", resolveMarkdownSource(message))
    }

    @Test
    fun `multiple tokens are all replaced`() {
        val message = makeMessage(
            "{user1} and {user2} joined",
            mapOf(
                "user1" to mapOf("type" to "user", "name" to "bob"),
                "user2" to mapOf("type" to "guest", "name" to "carol")
            )
        )
        assertEquals("@bob and @carol joined", resolveMarkdownSource(message))
    }

    @Test
    fun `call mention type receives at-name prefix`() {
        val message = makeMessage(
            "Calling {call1}",
            mapOf("call1" to mapOf("type" to "call", "name" to "all"))
        )
        assertEquals("Calling @all", resolveMarkdownSource(message))
    }

    @Test
    fun `user-group mention type receives at-name prefix`() {
        val message = makeMessage(
            "Hello {group1}",
            mapOf("group1" to mapOf("type" to "user-group", "name" to "devs"))
        )
        assertEquals("Hello @devs", resolveMarkdownSource(message))
    }
}
