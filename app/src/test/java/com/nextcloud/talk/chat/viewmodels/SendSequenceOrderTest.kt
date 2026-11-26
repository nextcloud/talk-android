/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.viewmodels

import com.nextcloud.talk.chat.data.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Test

/** A batch of sent messages keeps its order while the server copies replace the pending messages one by one. */
class SendSequenceOrderTest {

    private fun pending(id: Int, referenceId: String, timestamp: Long = SENT_AT) =
        ChatMessage().apply {
            jsonMessageId = id
            this.referenceId = referenceId
            this.timestamp = timestamp
            isTemporary = true
        }

    private fun sent(id: Int, referenceId: String, timestamp: Long) =
        ChatMessage().apply {
            jsonMessageId = id
            this.referenceId = referenceId
            this.timestamp = timestamp
        }

    /** The list as the database sorts it, "timestamp ASC, id ASC", corrected like the chat does it. */
    private fun displayed(messages: List<ChatMessage>, sentInOrder: List<String>): List<String?> {
        val sequence = sentInOrder.withIndex().associate { (index, referenceId) -> referenceId to index.toLong() }
        val sorted = messages.sortedWith(compareBy({ it.timestamp }, { it.jsonMessageId })).toMutableList()
        ChatViewModel.reorderKnownSendSequence(sorted, sequence)
        return sorted.map { it.referenceId }
    }

    @Test
    fun `a sent message keeps its place above the pending messages of its batch`() {
        // the server copy of "a" is later and has a positive id, so the database sorts it below "b" and "c"
        val shown = displayed(
            listOf(sent(500, "a", SENT_AT + 2), pending(-2, "b"), pending(-1, "c")),
            sentInOrder = listOf("a", "b", "c")
        )

        assertEquals(listOf("a", "b", "c"), shown)
    }

    @Test
    fun `messages of others keep their place`() {
        val shown = displayed(
            listOf(sent(10, "other", SENT_AT - 5), sent(500, "a", SENT_AT + 2), pending(-1, "b")),
            sentInOrder = listOf("a", "b")
        )

        assertEquals(listOf("other", "a", "b"), shown)
    }

    @Test
    fun `a pending message not sent from this chat keeps the order of the server`() {
        // e.g. a message that failed in an earlier session: it is not recorded, so a later retry is not pinned
        val shown = displayed(
            listOf(sent(500, "a", SENT_AT), sent(501, "old", SENT_AT + 300)),
            sentInOrder = listOf("a")
        )

        assertEquals(listOf("a", "old"), shown)
    }

    companion object {
        private const val SENT_AT = 1_000L
    }
}
