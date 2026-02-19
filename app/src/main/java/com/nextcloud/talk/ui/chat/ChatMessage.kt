/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chat

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.nextcloud.talk.chat.data.model.ChatMessage

@Composable
fun ChatMessage(
    message: ChatMessage,
    showAvatar: Boolean,
    conversationThreadId: Long? = null,
    isBlinkingState: MutableState<Boolean> = mutableStateOf(false)
) {
    when (message.getCalculateMessageType()) {
        ChatMessage.MessageType.REGULAR_TEXT_MESSAGE -> {
            if (message.isLinkPreview()) {
                LinkMessage(
                    message = message,
                    conversationThreadId = conversationThreadId,
                    state = isBlinkingState
                )
            } else {
                TextMessage(
                    message = message,
                    showAvatar = showAvatar,
                    conversationThreadId = conversationThreadId,
                    state = isBlinkingState
                )
            }
        }

        ChatMessage.MessageType.SYSTEM_MESSAGE -> {
            if (!message.shouldFilter()) {
                SystemMessage(message)
            }
        }

        ChatMessage.MessageType.VOICE_MESSAGE -> {
            VoiceMessage(
                message = message,
                conversationThreadId = conversationThreadId,
                state = isBlinkingState
            )
        }

        ChatMessage.MessageType.SINGLE_NC_ATTACHMENT_MESSAGE -> {
            ImageMessage(
                message = message,
                conversationThreadId = conversationThreadId,
                state = isBlinkingState
            )
        }

        ChatMessage.MessageType.SINGLE_NC_GEOLOCATION_MESSAGE -> {
            GeolocationMessage(
                message = message,
                conversationThreadId = conversationThreadId,
                state = isBlinkingState
            )
        }

        ChatMessage.MessageType.POLL_MESSAGE -> {
            PollMessage(
                message = message,
                conversationThreadId = conversationThreadId,
                state = isBlinkingState
            )
        }

        ChatMessage.MessageType.DECK_CARD -> {
            DeckMessage(
                message = message,
                conversationThreadId = conversationThreadId,
                state = isBlinkingState
            )
        }

        else -> {
            Log.d("ChatView", "Unknown message type: ${'$'}type")
        }
    }
}

private fun ChatMessage.shouldFilter(): Boolean =
    systemMessageType in setOf(
        ChatMessage.SystemMessageType.REACTION,
        ChatMessage.SystemMessageType.REACTION_DELETED,
        ChatMessage.SystemMessageType.REACTION_REVOKED,
        ChatMessage.SystemMessageType.POLL_VOTED,
        ChatMessage.SystemMessageType.MESSAGE_EDITED,
        ChatMessage.SystemMessageType.THREAD_CREATED
    ) ||
        (parentMessageId != null && systemMessageType == ChatMessage.SystemMessageType.MESSAGE_DELETED)
