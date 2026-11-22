/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chat

import com.nextcloud.talk.chat.ui.model.ChatMessageUi
import com.nextcloud.talk.chat.ui.model.MessageStatusIcon
import com.nextcloud.talk.chat.viewmodels.ChatViewModel.ChatItem
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class VisibleFileDownloadsTest {
    @Test
    fun `scrolling without downloads must not inspect the message list`() {
        val unreadableItems = object : AbstractList<ChatItem>() {
            override val size: Int = 100
            override fun get(index: Int): ChatItem = error("Unnecessary message lookup")
        }
        assertEquals(emptySet<Any>(), downloadingMessageKeys(unreadableItems, emptyList()))
    }

    @Test
    fun `only rows for active file downloads are tracked`() {
        val first = message(10, "file-a")
        val second = message(11, "file-b")
        val items = listOf(first, second, ChatItem.DateHeaderItem(LocalDate.of(2026, 11, 19)))
        assertEquals(setOf(first.stableKey()), downloadingMessageKeys(items, listOf("file-a", "missing")))
        assertEquals(emptySet<Any>(), downloadingMessageKeys(items, listOf("missing")))
    }

    private fun message(id: Int, fileId: String) =
        ChatItem.MessageItem(
            ChatMessageUi(
                id = id,
                message = "{file}",
                renderMarkdown = false,
                actorDisplayName = "Sender",
                isThread = false,
                threadTitle = "",
                threadReplies = 0,
                incoming = true,
                isDeleted = false,
                avatarUrl = null,
                statusIcon = MessageStatusIcon.SENT,
                timestamp = 1,
                date = LocalDate.of(2026, 11, 19),
                content = null,
                messageParameters = mapOf("file" to mapOf("id" to fileId))
            )
        )
}
