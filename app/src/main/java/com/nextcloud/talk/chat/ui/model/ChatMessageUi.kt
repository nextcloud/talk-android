/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.ui.model

import android.text.TextUtils
import androidx.compose.runtime.Immutable
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.data.database.model.SendStatus
import com.nextcloud.talk.utils.DrawableUtils
import java.time.LocalDate

// immutable class for chat message UI. only val, no vars!
@Immutable
data class ChatMessageUi(
    val id: Int,
    val text: String,
    val message: String, // what is the difference between message and text? remove one?
    val renderMarkdown: Boolean,
    val actorDisplayName: String,
    val isThread: Boolean,
    val threadTitle: String,
    val incoming: Boolean,
    val isDeleted: Boolean,
    val avatarUrl: String?,
    val statusIcon: MessageStatusIcon,
    val timestamp: Long,
    val date: LocalDate,
    val content: MessageTypeContent?,
    val parentMessage: ChatMessageUi? = null
)

sealed interface MessageTypeContent {
    object RegularText : MessageTypeContent
    object SystemMessage : MessageTypeContent

    data class LinkPreview(
        // TODO
        val todo: String
    ) : MessageTypeContent

    data class Image(val imageUrl: String, val drawableResourceId: Int) : MessageTypeContent

    data class Geolocation(val lat: Double, val lon: Double) : MessageTypeContent

    data class Poll(val pollId: String, val pollName: String) : MessageTypeContent

    data class Deck(val cardName: String, val stackName: String, val boardName: String, val cardLink: String) :
        MessageTypeContent

    data class Voice(
        // TODO
        val todo: String
    ) : MessageTypeContent
}

enum class MessageStatusIcon {
    FAILED,
    SENDING,
    READ,
    SENT
}

// Domain model (ChatMessage) to UI model (ChatMessageUi)
fun ChatMessage.toUiModel(
    chatMessage: ChatMessage,
    lastCommonReadMessageId: Int,
    parentMessage: ChatMessage?
): ChatMessageUi =
    ChatMessageUi(
        id = jsonMessageId,
        text = text,
        message = message.orEmpty(), // what is the difference between message and text? remove one?
        renderMarkdown = renderMarkdown == true,
        actorDisplayName = actorDisplayName.orEmpty(),
        threadTitle = threadTitle.orEmpty(),
        isThread = isThread,
        incoming = incoming,
        isDeleted = isDeleted,
        avatarUrl = avatarUrl,
        statusIcon = resolveStatusIcon(
            jsonMessageId,
            lastCommonReadMessageId,
            isTemporary,
            sendStatus
        ),
        timestamp = timestamp,
        date = dateKey(),
        content = getMessageTypeContent(chatMessage),
        parentMessage = parentMessage?.toUiModel(parentMessage, 0, null)
    )

fun resolveStatusIcon(
    jsonMessageId: Int,
    lastCommonReadMessageId: Int,
    isTemporary: Boolean,
    sendStatus: SendStatus?
): MessageStatusIcon {
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

fun getMessageTypeContent(message: ChatMessage): MessageTypeContent? =
    if (!TextUtils.isEmpty(message.systemMessage)) {
        MessageTypeContent.SystemMessage
    } else if (message.isVoiceMessage) {
        getVoiceContent(message)
    } else if (message.hasFileAttachment()) {
        getImageContent(message)
    } else if (message.hasGeoLocation()) {
        getGeolocationContent(message)
    } else if (message.isLinkPreview()) {
        getLinkPreviewContent(message)
    } else if (message.isPoll()) {
        getPollContent(message)
    } else if (message.isDeckCard()) {
        getDeckContent(message)
    } else {
        MessageTypeContent.RegularText
    }

fun getLinkPreviewContent(message: ChatMessage): MessageTypeContent.LinkPreview =
    MessageTypeContent.LinkPreview(
        todo = "still todo..."
    )

fun getImageContent(message: ChatMessage): MessageTypeContent.Image {
    val imageUri = message.imageUrl
    val mimetype = message.selectedIndividualHashMap!!["mimetype"]
    val drawableResourceId = DrawableUtils.getDrawableResourceIdForMimeType(mimetype)

    return MessageTypeContent.Image(
        imageUri!!,
        drawableResourceId
    )
}

fun getGeolocationContent(message: ChatMessage): MessageTypeContent.Geolocation? {
    if (message.messageParameters != null && message.messageParameters!!.isNotEmpty()) {
        for (key in message.messageParameters!!.keys) {
            val individualHashMap: Map<String?, String?> = message.messageParameters!![key]!!
            if (individualHashMap["type"] == "geo-location") {
                val lat = individualHashMap["latitude"]
                val lng = individualHashMap["longitude"]

                if (lat != null && lng != null) {
                    val latitude = lat.toDouble()
                    val longitude = lng.toDouble()
                    return MessageTypeContent.Geolocation(
                        lat = latitude,
                        lon = longitude
                    )
                }
            }
        }
    }
    return null
}

fun getPollContent(message: ChatMessage): MessageTypeContent.Poll? {
    if (message.messageParameters != null && message.messageParameters!!.size > 0) {
        for (key in message.messageParameters!!.keys) {
            val individualHashMap: Map<String?, String?> = message.messageParameters!![key]!!
            if (individualHashMap["type"] == "talk-poll") {
                val pollId = individualHashMap["id"]
                val pollName = individualHashMap["name"].toString()
                return MessageTypeContent.Poll(
                    pollId = pollId!!,
                    pollName = pollName
                )
            }
        }
    }
    return null
}

fun getDeckContent(message: ChatMessage): MessageTypeContent.Deck? {
    if (message.messageParameters != null && message.messageParameters!!.isNotEmpty()) {
        for (key in message.messageParameters!!.keys) {
            val individualHashMap: Map<String?, String?> = message.messageParameters!![key]!!
            if (individualHashMap["type"] == "deck-card") {
                val cardName = individualHashMap["name"]
                val stackName = individualHashMap["stackname"]
                val boardName = individualHashMap["boardname"]
                val cardLink = individualHashMap["link"]

                return MessageTypeContent.Deck(
                    cardName = cardName!!,
                    stackName = stackName!!,
                    boardName = boardName!!,
                    cardLink = cardLink!!
                )
            }
        }
    }
    return null
}

fun getVoiceContent(message: ChatMessage): MessageTypeContent.Voice =
    MessageTypeContent.Voice(
        todo = "still todo..."
    )
