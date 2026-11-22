/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.chat

import com.nextcloud.talk.chat.data.io.MessageAudioKey
import com.nextcloud.talk.chat.data.io.MessageAudioState
import com.nextcloud.talk.chat.data.io.MessageAudioTrack
import com.nextcloud.talk.chat.ui.model.MessageTypeContent
import com.nextcloud.talk.chat.ui.model.withMessageAudio
import com.nextcloud.talk.chat.viewmodels.ChatViewModel
import com.nextcloud.talk.ui.PlaybackSpeed
import com.nextcloud.talk.ui.chat.createBaseMessageWithoutCaption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceMessageSpeedTest {
    private val voice = MessageTypeContent.Voice("sender", false, false, false, 30, 0, 0, emptyList())
    private val message = createBaseMessageWithoutCaption(voice)
    private val items = listOf(ChatViewModel.ChatItem.MessageItem(message))
    private val track = MessageAudioTrack(MessageAudioKey(1, "room", message.id), "file.m4a", "Sender", "Room", true)

    private fun projected(state: MessageAudioState, account: Long = 1, room: String = "room") =
        (items.withMessageAudio(state, account, room).first() as ChatViewModel.ChatItem.MessageItem)
            .uiMessage.content as MessageTypeContent.Voice

    @Test
    fun `recreated chat rows restore real progress and one and a half speed`() {
        val state = MessageAudioState(track, true, true, 12_000, 30_000, 1.5f)
        val result = projected(state)
        assertTrue(result.isPlaying)
        assertEquals(12, result.playedSeconds)
        assertEquals(40, result.seekbarProgress)
        assertEquals(PlaybackSpeed.FASTER, result.playbackSpeed)
    }

    @Test
    fun `paused double speed is reflected when returning to the chat`() {
        val result = projected(MessageAudioState(track, false, false, 12_000, 30_000, 2f))
        assertFalse(result.isPlaying)
        assertEquals(PlaybackSpeed.FASTEST, result.playbackSpeed)
        assertEquals(12, result.playedSeconds)
    }

    @Test
    fun `another account or room cannot inherit playing state or speed`() {
        val state = MessageAudioState(track, true, true, 12_000, 30_000, 2f)
        for (result in listOf(projected(state, account = 2), projected(state, room = "other"))) {
            assertFalse(result.isPlaying)
            assertEquals(PlaybackSpeed.NORMAL, result.playbackSpeed)
            assertEquals(0, result.playedSeconds)
        }
    }

    @Test
    fun `stopping clears a stale playing icon`() {
        val stale = listOf(ChatViewModel.ChatItem.MessageItem(message.copy(content = voice.copy(isPlaying = true))))
        val result = stale.withMessageAudio(MessageAudioState(), 1, "room").first()
            as ChatViewModel.ChatItem.MessageItem
        assertFalse((result.uiMessage.content as MessageTypeContent.Voice).isPlaying)
    }
}
