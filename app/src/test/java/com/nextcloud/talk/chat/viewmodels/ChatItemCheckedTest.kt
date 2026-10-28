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
    fun `a media group is checked by any one of its files`() {
        val item = ChatViewModel.ChatItem.MediaGroupItem(listOf(ui(1), ui(2), ui(3)))

        listOf(1, 2, 3).forEach { assertTrue("file $it", item.isChecked(setOf(it))) }
        assertTrue(item.isChecked(setOf(1, 9)))
        assertFalse(item.isChecked(setOf(9)))
        assertFalse(item.isChecked(emptySet()))
    }

    @Test
    fun `a media group of two is checked by its first file`() {
        val item = ChatViewModel.ChatItem.MediaGroupItem(listOf(ui(1), ui(2)))

        assertTrue(item.isChecked(setOf(1)))
        assertTrue(item.isChecked(setOf(2)))
    }

    /** The toggle of a row, as ChatActivity does it: an already selected row is deselected by its selected id. */
    private fun toggle(items: List<ChatViewModel.ChatItem>, selection: MessageSelection, id: Int) {
        val selectedId = ChatViewModel.selectedIdOfRow(items, selection.ids.value, id)
        selection.toggle(selectedId ?: id, null)
    }

    @Test
    fun `the selected id of a row is the selected file of its group, whichever file is asked`() {
        val group = ChatViewModel.ChatItem.MediaGroupItem(listOf(ui(1), ui(2), ui(3)))
        val items = listOf(group, ChatViewModel.ChatItem.MessageItem(ui(4)))

        assertNull(ChatViewModel.selectedIdOfRow(items, emptySet(), 2))
        listOf(1, 2, 3).forEach { assertEquals(2, ChatViewModel.selectedIdOfRow(items, setOf(2), it)) }
        assertNull(ChatViewModel.selectedIdOfRow(items, setOf(4), 1))
        assertEquals(4, ChatViewModel.selectedIdOfRow(items, setOf(4), 4))
        assertNull(ChatViewModel.selectedIdOfRow(items, setOf(4), 7))
    }

    @Test
    fun `any file of a group selected then toggled again by any file leaves the group once and then clear`() {
        listOf(2, 3).forEach { size ->
            for (first in 1..size) {
                for (second in 1..size) {
                    val items = listOf(ChatViewModel.ChatItem.MediaGroupItem((1..size).map { ui(it) }))
                    val selection = MessageSelection()

                    toggle(items, selection, first)
                    assertEquals(setOf(first), selection.ids.value)
                    assertTrue(items[0].isChecked(selection.ids.value))

                    toggle(items, selection, second)
                    assertEquals("size $size, $first then $second", emptySet<Int>(), selection.ids.value)
                    assertFalse(items[0].isChecked(selection.ids.value))
                    assertFalse(selection.isActive)
                }
            }
        }
    }

    @Test
    fun `a group next to a text counts once and keeps the count of the other rows`() {
        val items = listOf(
            ChatViewModel.ChatItem.MediaGroupItem(listOf(ui(1), ui(2))),
            ChatViewModel.ChatItem.MessageItem(ui(3))
        )
        val selection = MessageSelection()

        toggle(items, selection, 1)
        toggle(items, selection, 3)
        toggle(items, selection, 2)

        assertEquals(setOf(3), selection.ids.value)
        assertEquals(listOf(1, 2, 3), ChatViewModel.messageIdsToForward(items, setOf(1, 3)))
    }

    @Test
    fun `rows which are no message are never checked`() {
        assertFalse(ChatViewModel.ChatItem.DateHeaderItem(LocalDate.of(2026, 10, 1)).isChecked(setOf(1)))
    }
}
