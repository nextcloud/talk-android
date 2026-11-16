/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatMessageDisplayNameTest {

    @Test
    fun `a federated user is shown with their server`() {
        val message = authoredBy("federated_users", "alice@cloud.example.com")

        assertEquals("Alice (cloud.example.com)", message.getNullsafeActorDisplayNameWithServer())
    }

    @Test
    fun `a federated user id containing an at sign is split at the last one`() {
        val message = authoredBy("federated_users", "alice@example.org@cloud.example.com")

        assertEquals("Alice (cloud.example.com)", message.getNullsafeActorDisplayNameWithServer())
    }

    @Test
    fun `a local user is shown without a server`() {
        val message = authoredBy("users", "alice")

        assertEquals("Alice", message.getNullsafeActorDisplayNameWithServer())
    }

    @Test
    fun `a federated user who pinned a message is shown with their server`() {
        val message = authoredBy("users", "alice").apply {
            pinnedActorType = "federated_users"
            pinnedActorId = "bob@cloud.example.com"
            pinnedActorDisplayName = "Bob"
        }

        assertEquals("Bob (cloud.example.com)", message.getPinnedActorDisplayNameWithServer())
    }

    private fun authoredBy(type: String, id: String): ChatMessage =
        ChatMessage().apply {
            actorType = type
            actorId = id
            actorDisplayName = "Alice"
        }
}
