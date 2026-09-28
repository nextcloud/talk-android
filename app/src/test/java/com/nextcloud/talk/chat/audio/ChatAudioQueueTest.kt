/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatAudioQueueTest {

    private data class Message(val id: Int, val kind: ChatAudioKind?)

    private fun build(messages: List<Message>, startId: Int) =
        ChatAudioQueue.build(messages, startId, { it.id }, { it.kind }).map { it.id }

    @Test
    fun `queue starts with the tapped message and continues with newer messages of the same kind`() {
        val messages = listOf(
            Message(1, ChatAudioKind.VOICE_MESSAGE),
            Message(2, ChatAudioKind.VOICE_MESSAGE),
            Message(3, null),
            Message(4, ChatAudioKind.AUDIO_FILE),
            Message(5, ChatAudioKind.VOICE_MESSAGE)
        )

        assertEquals(listOf(2, 5), build(messages, startId = 2))
        assertEquals(listOf(4), build(messages, startId = 4))
    }

    @Test
    fun `older messages are never queued`() {
        val messages = listOf(
            Message(1, ChatAudioKind.VOICE_MESSAGE),
            Message(2, ChatAudioKind.VOICE_MESSAGE)
        )

        assertEquals(listOf(2), build(messages, startId = 2))
    }

    @Test
    fun `unknown or unplayable start message results in an empty queue`() {
        val messages = listOf(Message(1, ChatAudioKind.VOICE_MESSAGE), Message(2, null))

        assertTrue(build(messages, startId = 42).isEmpty())
        assertTrue(build(messages, startId = 2).isEmpty())
        assertTrue(build(emptyList(), startId = 1).isEmpty())
    }

    @Test
    fun `queue size is limited`() {
        val messages = (1..ChatAudioQueue.MAX_SIZE * 2).map { Message(it, ChatAudioKind.VOICE_MESSAGE) }

        val queue = build(messages, startId = 1)

        assertEquals(ChatAudioQueue.MAX_SIZE, queue.size)
        assertEquals(1, queue.first())
    }
}
