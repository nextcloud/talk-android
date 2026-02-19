/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.ui.model

import androidx.compose.runtime.Immutable
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.data.database.model.SendStatus
import java.time.LocalDate

// immutable class for chat message UI. only val, no vars!
@Immutable
data class ChatMessageUi(
    val id: Int,
    val type: ChatMessage.MessageType,
    val text: String,
    val message: String, // what is the difference between message and text? remove one?
    val renderMarkdown: Boolean,
    val isLinkPreview: Boolean,
    val actorDisplayName: String,
    val isThread: Boolean,
    val threadTitle: String,
    val incoming: Boolean,
    val isDeleted: Boolean,
    val avatarUrl: String?,
    val imageUrl: String?,
    val statusIcon: MessageStatusIcon,
    val timestamp: Long,
    val date: LocalDate
)

enum class MessageStatusIcon {
    FAILED,
    SENDING,
    READ,
    SENT,
}

fun resolveStatusIcon(
    jsonMessageId: Int,
    lastCommonReadMessageId: Int,
    isTemporary: Boolean,
    sendStatus: SendStatus?
) : MessageStatusIcon {
    val status = if (sendStatus == SendStatus.FAILED) {
        MessageStatusIcon.FAILED
    } else if (isTemporary) {
        MessageStatusIcon.SENDING
    } else if (jsonMessageId <= lastCommonReadMessageId) {
        MessageStatusIcon.READ
    } else {
        MessageStatusIcon.SENT
    }
    return status
}

// Domain model (ChatMessage) to UI model (ChatMessageUi)
fun ChatMessage.toUiModel(
    lastCommonReadMessageId: Int
): ChatMessageUi {

    return ChatMessageUi(
        id = jsonMessageId,
        type = getCalculateMessageType(),
        text = text,
        message = message.orEmpty(), // what is the difference between message and text? remove one?
        renderMarkdown = renderMarkdown == true,
        isLinkPreview = isLinkPreview(),
        actorDisplayName = actorDisplayName.orEmpty(),
        threadTitle = threadTitle.orEmpty(),
        isThread = isThread,
        incoming = incoming,
        isDeleted = isDeleted,
        avatarUrl = avatarUrl,
        imageUrl = imageUrl,
        statusIcon = resolveStatusIcon(
            jsonMessageId,
            lastCommonReadMessageId,
            isTemporary,
            sendStatus
        ),
        timestamp = timestamp,
        date = dateKey()
    )
}
