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
    private fun displayed(messages: List<ChatMessage>, sequence: MutableMap<String, Long>): List<String?> {
        val sorted = messages.sortedWith(compareBy({ it.timestamp }, { it.jsonMessageId })).toMutableList()
        ChatViewModel.registerPendingSendSequence(sorted, sequence, sequence.size.toLong())
        ChatViewModel.reorderKnownSendSequence(sorted, sequence)
        return sorted.map { it.referenceId }
    }

    @Test
    fun `a sent message keeps its place above the pending messages of its batch`() {
        val sequence = mutableMapOf<String, Long>()
        // a later message gets a larger id, see OfflineFirstChatRepository
        displayed(listOf(pending(-3, "a"), pending(-2, "b"), pending(-1, "c")), sequence)

        // the server copy of "a" is later and has a positive id, so the database sorts it below "b" and "c"
        val afterFirst = displayed(listOf(sent(500, "a", SENT_AT + 2), pending(-2, "b"), pending(-1, "c")), sequence)

        assertEquals(listOf("a", "b", "c"), afterFirst)
    }

    @Test
    fun `pending messages are registered in the order they were added`() {
        val sequence = mutableMapOf<String, Long>()

        val next = ChatViewModel.registerPendingSendSequence(
            listOf(pending(-5, "second"), pending(-9, "first"), sent(42, "other", SENT_AT)),
            sequence,
            7
        )

        assertEquals(mapOf("first" to 7L, "second" to 8L), sequence)
        assertEquals(9L, next)
    }

    @Test
    fun `messages of others keep their place`() {
        val sequence = mutableMapOf<String, Long>()
        displayed(listOf(pending(-2, "a"), pending(-1, "b")), sequence)

        val shown = displayed(
            listOf(sent(10, "other", SENT_AT - 5), sent(500, "a", SENT_AT + 2), pending(-1, "b")),
            sequence
        )

        assertEquals(listOf("other", "a", "b"), shown)
    }

    companion object {
        private const val SENT_AT = 1_000L
    }
}
