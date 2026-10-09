/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatAudioQueueTest {

    private data class Message(val id: Int, val kind: ChatAudioKind?)

    private fun build(messages: List<Message>, startId: Int) =
        ChatAudioQueue.build(messages, startId, { it.id }, { it.kind })
            ?.let { ChatAudioQueue.Selection(it.items.map { message -> message.id }, it.startIndex) }

    private val mixed = listOf(
        Message(1, ChatAudioKind.VOICE_MESSAGE),
        Message(2, ChatAudioKind.AUDIO_FILE),
        Message(3, ChatAudioKind.VOICE_MESSAGE),
        Message(4, null),
        Message(5, ChatAudioKind.AUDIO_FILE),
        Message(6, ChatAudioKind.VOICE_MESSAGE)
    )

    @Test
    fun `voice messages continue with the newer voice messages only`() {
        assertEquals(ChatAudioQueue.Selection(listOf(3, 6), 0), build(mixed, startId = 3))
    }

    @Test
    fun `audio files are played among all audio files of the conversation`() {
        assertEquals(ChatAudioQueue.Selection(listOf(2, 5), 1), build(mixed, startId = 5))
        assertEquals(ChatAudioQueue.Selection(listOf(2, 5), 0), build(mixed, startId = 2))
    }

    @Test
    fun `unknown or unplayable start message results in no queue`() {
        assertNull(build(mixed, startId = 42))
        assertNull(build(mixed, startId = 4))
        assertNull(build(emptyList(), startId = 1))
    }

    @Test
    fun `voice message queue size is limited`() {
        val messages = (1..ChatAudioQueue.MAX_SIZE * 2).map { Message(it, ChatAudioKind.VOICE_MESSAGE) }

        val queue = build(messages, startId = 1)

        assertEquals(ChatAudioQueue.Selection((1..ChatAudioQueue.MAX_SIZE).toList(), 0), queue)
    }

    @Test
    fun `audio file queue is limited to the files around the tapped one`() {
        val messages = (1..ChatAudioQueue.MAX_SIZE * 2).map { Message(it, ChatAudioKind.AUDIO_FILE) }
        val half = ChatAudioQueue.MAX_SIZE / 2

        val middle = build(messages, startId = ChatAudioQueue.MAX_SIZE)
        val newest = build(messages, startId = messages.size)

        assertEquals(ChatAudioQueue.MAX_SIZE, middle?.items?.size)
        assertEquals(ChatAudioQueue.MAX_SIZE - half, middle?.items?.first())
        assertEquals(half, middle?.startIndex)
        assertEquals(messages.size - ChatAudioQueue.MAX_SIZE + 1, newest?.items?.first())
        assertEquals(ChatAudioQueue.MAX_SIZE - 1, newest?.startIndex)
    }
}
