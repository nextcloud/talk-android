/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chat

import android.util.Log
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nextcloud.talk.chat.ui.model.ChatMessageUi
import com.nextcloud.talk.chat.ui.model.MessageTypeContent

@Composable
fun ChatMessageView(
    message: ChatMessageUi,
    showAvatar: Boolean,
    conversationThreadId: Long? = null,
    onLongClick: ((Int) -> Unit?)?,
    onFileClick: (Int) -> Unit
) {
    Box(
        modifier = Modifier
            .combinedClickable(
                onClick = { onLongClick?.invoke(message.id) },
                onLongClick = { onLongClick?.invoke(message.id) }
            )
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
                    conversationThreadId = conversationThreadId,
                    onImageClick = onFileClick
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
}
