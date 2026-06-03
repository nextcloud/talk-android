/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatMessageLinkPreviewUrlTest {

    private val chatMessage = ChatMessage()

    @Test
    fun `extracts plain url with fallback regex`() {
        val message = "Check this out https://nextcloud.com/docs"

        val extracted = chatMessage.extractUrlCandidateForLinkPreview(message, serverReferenceRegex = null)

        assertEquals("https://nextcloud.com/docs", extracted)
    }

    @Test
    fun `extracts markdown link url without parentheses`() {
        val message = "Look at [Nextcloud](https://nextcloud.com/features) now"

        val extracted = chatMessage.extractUrlCandidateForLinkPreview(message, serverReferenceRegex = null)

        assertEquals("https://nextcloud.com/features", extracted)
    }

    @Test
    fun `prefers markdown url when server regex does not match markdown syntax`() {
        val message = "Look at [Nextcloud](https://nextcloud.com/features) now"
        val serverRegex = """(\\s|\\n|^)(https?:\\/\\/)((?:[-A-Z0-9+_]+\\.)+[-A-Z]+)(\\s|\\n|$)"""

        val extracted = chatMessage.extractUrlCandidateForLinkPreview(message, serverReferenceRegex = serverRegex)

        assertEquals("https://nextcloud.com/features", extracted)
    }
}
