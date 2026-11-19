/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.ui.model

import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.capabilities.CapabilitiesDto
import com.nextcloud.talk.models.json.capabilities.CoreCapabilityDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkPreviewContentTest {

    private val user = User(
        userId = "alice",
        username = "alice",
        baseUrl = "https://cloud.example.com",
        token = "token",
        capabilities = CapabilitiesDto().apply {
            coreCapability = CoreCapabilityDto().apply { referenceApi = "true" }
        }
    )

    private val message = ChatMessage().apply {
        message = "Have a look at https://nextcloud.com/"
    }

    @Test
    fun `message with link shows reference widget`() {
        val content = getMessageTypeContent(user, message)

        assertTrue(content is MessageTypeContent.LinkPreview)
        assertEquals("https://nextcloud.com/", (content as MessageTypeContent.LinkPreview).url)
    }

    @Test
    fun `message with link in classified conversation shows no reference widget`() {
        val content = getMessageTypeContent(user, message, isClassified = true)

        assertEquals(MessageTypeContent.RegularText, content)
    }
}
