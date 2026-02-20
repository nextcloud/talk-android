/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chat

import android.util.Log
import androidx.compose.runtime.Composable
import com.nextcloud.talk.chat.ui.model.ChatMessageUi
import com.nextcloud.talk.chat.ui.model.MessageTypeContent

@Composable
fun ChatMessageView(
    message: ChatMessageUi,
    showAvatar: Boolean,
    conversationThreadId: Long? = null
) {
    when (val content = message.content) {
        MessageTypeContent.RegularText -> {
            TextMessage(
                uiMessage = message,
                showAvatar = showAvatar,
                conversationThreadId = conversationThreadId
            )
        }

        MessageTypeContent.SystemMessage -> {
            SystemMessage(message)
        }

        is MessageTypeContent.Image -> {
            ImageMessage(
                typeContent = content,
                message = message,
                conversationThreadId = conversationThreadId
            )
        }

        is MessageTypeContent.LinkPreview -> {
            LinkMessage(
                typeContent = content,
                message = message,
                conversationThreadId = conversationThreadId
            )
        }

        is MessageTypeContent.Geolocation -> {
            GeolocationMessage(
                typeContent = content,
                message = message
            )
        }

        is MessageTypeContent.Voice -> {
            VoiceMessage(
                typeContent = content,
                message = message,
                conversationThreadId = conversationThreadId
            )
        }

        is MessageTypeContent.Poll -> {
            PollMessage(
                typeContent = content,
                message = message,
                conversationThreadId = conversationThreadId
            )
        }

        is MessageTypeContent.Deck -> {
            DeckMessage(
                typeContent = content,
                message = message,
                conversationThreadId = conversationThreadId
            )
        }

        else -> {
            Log.d("ChatView", "Unknown message type: ${'$'}type")
        }
    }
}
