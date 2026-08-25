/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.viewmodels

import com.nextcloud.talk.chat.ui.model.ChatMessageUi
import com.nextcloud.talk.chat.ui.model.MessageStatusIcon
import com.nextcloud.talk.chat.ui.model.MessageTypeContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ChatViewModelTest {

    // The unread marker latch: the marker position must only be derived from the visible window
    // when the window provably reaches back to the unread boundary — otherwise a window of
    // only-unread messages (e.g. after a capped fetch of the newest messages) would place the
    // marker in the middle of the unread messages.

    @Test
    fun `marker is placed at the first message above the boundary when the boundary is visible`() {
        val messages = listOf(uiMessage(39), uiMessage(40), uiMessage(41), uiMessage(42))

        assertEquals(41, ChatViewModel.findFirstUnreadMessageId(messages, lastReadMessage = 40))
    }

    @Test
    fun `marker is placed correctly when the boundary message itself is missing`() {
        // the last read message may have been deleted or expired — an older read message is
        // equally valid proof that the window reaches the boundary
        val messages = listOf(uiMessage(38), uiMessage(41), uiMessage(42))

        assertEquals(41, ChatViewModel.findFirstUnreadMessageId(messages, lastReadMessage = 40))
    }

    @Test
    fun `no marker is placed when the window floats entirely above the boundary`() {
        val messages = listOf(uiMessage(141), uiMessage(142), uiMessage(143))

        assertNull(ChatViewModel.findFirstUnreadMessageId(messages, lastReadMessage = 40))
    }

    @Test
    fun `temporary messages with negative ids are no proof of the boundary`() {
        val messages = listOf(uiMessage(-5), uiMessage(141), uiMessage(142))

        assertNull(ChatViewModel.findFirstUnreadMessageId(messages, lastReadMessage = 40))
    }

    @Test
    fun `no marker is placed when everything is read`() {
        val messages = listOf(uiMessage(38), uiMessage(39), uiMessage(40))

        assertNull(ChatViewModel.findFirstUnreadMessageId(messages, lastReadMessage = 40))
    }

    private fun uiMessage(id: Int): ChatMessageUi =
        ChatMessageUi(
            id = id,
            message = "message $id",
            renderMarkdown = false,
            actorDisplayName = "Other User",
            isThread = false,
            threadTitle = "",
            threadReplies = 0,
            incoming = true,
            isDeleted = false,
            avatarUrl = null,
            statusIcon = MessageStatusIcon.SENT,
            timestamp = id.toLong(),
            date = LocalDate.of(2026, 8, 12),
            content = MessageTypeContent.RegularText
        )
}
