/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.models.json.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationUserDtoTest {

    @Test
    fun `a federated user has a cloud id and is shown with their server`() {
        val user = NotificationUserDto("user", "alice", "Alice", "https://cloud.example.com")

        assertEquals("alice@cloud.example.com", user.cloudId)
        assertEquals("Alice (cloud.example.com)", user.nameWithServer)
    }

    @Test
    fun `a federated user on a server without https keeps the scheme in their cloud id`() {
        val user = NotificationUserDto("user", "alice", "Alice", "http://cloud.example.com")

        assertEquals("alice@http://cloud.example.com", user.cloudId)
        assertEquals("Alice (http://cloud.example.com)", user.nameWithServer)
    }

    @Test
    fun `a local user has no cloud id and is shown with their name only`() {
        val user = NotificationUserDto("user", "alice", "Alice")

        assertNull(user.cloudId)
        assertEquals("Alice", user.nameWithServer)
    }
}
