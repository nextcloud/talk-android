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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The checkmark of a row is shown for the item of a selected message, whatever kind of row it is. */
class ChatItemCheckedTest {

    private fun ui(id: Int) =
        ChatMessageUi(
            id = id,
            message = "message $id",
            plainMessage = "message $id",
            renderMarkdown = false,
            actorDisplayName = "Me",
            isThread = false,
            threadTitle = "",
            threadReplies = 0,
            incoming = false,
            isDeleted = false,
            avatarUrl = null,
            statusIcon = MessageStatusIcon.READ,
            timestamp = id.toLong(),
            date = LocalDate.of(2026, 10, 1),
            content = MessageTypeContent.RegularText
        )

    @Test
    fun `a text message is checked when its id is selected`() {
        val item = ChatViewModel.ChatItem.MessageItem(ui(5))

        assertTrue(item.isChecked(setOf(5, 9)))
        assertFalse(item.isChecked(setOf(9)))
        assertFalse(item.isChecked(emptySet()))
    }

    @Test
    fun `a media group is checked by its last message`() {
        val item = ChatViewModel.ChatItem.MediaGroupItem(listOf(ui(1), ui(2), ui(3)))

        assertTrue(item.isChecked(setOf(3)))
        assertFalse(item.isChecked(setOf(1)))
    }

    @Test
    fun `rows which are no message are never checked`() {
        assertFalse(ChatViewModel.ChatItem.DateHeaderItem(LocalDate.of(2026, 10, 1)).isChecked(setOf(1)))
    }
}
