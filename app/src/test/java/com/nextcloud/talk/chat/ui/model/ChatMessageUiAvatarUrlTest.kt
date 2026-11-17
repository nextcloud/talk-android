/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.ui.model

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito
import java.time.LocalDate

class ChatMessageUiAvatarUrlTest {

    @Test
    fun `a federated author gets the avatar proxied by the own server`() {
        assertEquals(
            "https://cloud.example.com/ocs/v2.php/apps/spreed/api/v1/proxy/room1/user-avatar/64" +
                "?cloudId=alice@remote.example.com&darkTheme=0",
            authoredBy("federated_users", "alice@remote.example.com").avatarUrl(darkTheme = false)
        )
    }

    @Test
    fun `a federated author gets the dark avatar in the dark theme`() {
        assertEquals(
            "https://cloud.example.com/ocs/v2.php/apps/spreed/api/v1/proxy/room1/user-avatar/64" +
                "?cloudId=alice@remote.example.com&darkTheme=1",
            authoredBy("federated_users", "alice@remote.example.com").avatarUrl(darkTheme = true)
        )
    }

    @Test
    fun `a federated author outside of a conversation gets no avatar url`() {
        val message = authoredBy("federated_users", "alice@remote.example.com").copy(roomToken = null)

        assertNull(message.avatarUrl(darkTheme = false))
    }

    @Test
    fun `a local author gets the avatar of the own server`() {
        Mockito.mockStatic(Uri::class.java).use { uri ->
            uri.`when`<String> { Uri.encode("bob") }.thenReturn("bob")

            assertEquals(
                "https://cloud.example.com/index.php/avatar/bob/64",
                authoredBy("users", "bob").avatarUrl(darkTheme = false)
            )
            assertEquals(
                "https://cloud.example.com/index.php/avatar/bob/64/dark",
                authoredBy("users", "bob").avatarUrl(darkTheme = true)
            )
        }
    }

    @Test
    fun `a message without a server gets no avatar url`() {
        val message = authoredBy("users", "bob").copy(activeUserBaseUrl = null)

        assertNull(message.avatarUrl(darkTheme = false))
    }

    private fun authoredBy(type: String, id: String): ChatMessageUi =
        ChatMessageUi(
            id = 1,
            message = "",
            plainMessage = "",
            renderMarkdown = false,
            actorDisplayName = "Name",
            actorType = type,
            actorId = id,
            isThread = false,
            threadTitle = "",
            threadReplies = 0,
            incoming = true,
            isDeleted = false,
            statusIcon = MessageStatusIcon.SENT,
            timestamp = 0,
            date = LocalDate.of(2026, 8, 12),
            content = MessageTypeContent.RegularText,
            roomToken = "room1",
            activeUserBaseUrl = "https://cloud.example.com"
        )
}
