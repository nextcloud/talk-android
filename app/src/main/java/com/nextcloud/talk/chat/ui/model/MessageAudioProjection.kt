/*
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.chat.ui.model

import com.nextcloud.talk.chat.data.io.MessageAudioKey
import com.nextcloud.talk.chat.data.io.MessageAudioState
import com.nextcloud.talk.chat.viewmodels.ChatViewModel
import com.nextcloud.talk.ui.PlaybackSpeed

private const val MILLIS_PER_SECOND = 1000L

fun List<ChatViewModel.ChatItem>.withMessageAudio(
    state: MessageAudioState,
    accountId: Long,
    roomToken: String
): List<ChatViewModel.ChatItem> =
    map { item ->
        if (item !is ChatViewModel.ChatItem.MessageItem) return@map item
        val message = item.uiMessage
        val active = state.belongsTo(MessageAudioKey(accountId, roomToken, message.id))
        val content = when (val content = message.content) {
            is MessageTypeContent.Voice ->
                if (active) content.withPlayback(state) else content.copy(isPlaying = false)
            is MessageTypeContent.AudioFile ->
                if (active) content.withPlayback(state) else content.copy(isPlaying = false)
            else -> content
        }
        if (content == message.content) item else item.copy(uiMessage = message.copy(content = content))
    }

private fun MessageTypeContent.Voice.withPlayback(state: MessageAudioState) =
    copy(
        isPlaying = state.isPlaying,
        playedSeconds = (state.positionMs / MILLIS_PER_SECOND).toInt(),
        durationSeconds = if (state.durationMs > 0) (state.durationMs / MILLIS_PER_SECOND).toInt() else durationSeconds,
        seekbarProgress = state.progress,
        wasPlayed = wasPlayed || state.positionMs > 0,
        playbackSpeed = PlaybackSpeed.entries.firstOrNull { it.value == state.speed } ?: playbackSpeed
    )

private fun MessageTypeContent.AudioFile.withPlayback(state: MessageAudioState) =
    copy(
        isPlaying = state.isPlaying,
        playedSeconds = (state.positionMs / MILLIS_PER_SECOND).toInt(),
        durationSeconds = if (state.durationMs > 0) (state.durationMs / MILLIS_PER_SECOND).toInt() else durationSeconds,
        seekbarProgress = state.progress
    )
