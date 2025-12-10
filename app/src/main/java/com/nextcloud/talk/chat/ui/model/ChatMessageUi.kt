/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.ui.model

import android.text.TextUtils
import androidx.compose.runtime.Stable
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.data.database.model.SendStatus
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.DrawableUtils
import java.security.MessageDigest
import java.time.LocalDate

// immutable class for chat message UI. only val, no vars!
@Stable // TODO: or @Immutable ?
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

    data class Image(val imagePreviewUrl: String?, val drawableResourceId: Int) : MessageTypeContent

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
    user: User,
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
        content = getMessageTypeContent(user, chatMessage),
        // setting parent message recursively might be a problem regarding recompositions? extract only what is needed
        // for UI?
        parentMessage = parentMessage?.toUiModel(user,parentMessage, 0, null)
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

fun getMessageTypeContent(
    user: User,
    message: ChatMessage
): MessageTypeContent? =
    if (!TextUtils.isEmpty(message.systemMessage)) {
        MessageTypeContent.SystemMessage
    } else if (message.isVoiceMessage) {
        getVoiceContent(message)
    } else if (message.hasFileAttachment()) {
        // add handling of file types here?
        getImageContent(user, message)
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

fun getImageContent(
    user: User,
    message: ChatMessage
): MessageTypeContent.Image {
    val imageUri = getPreviewImageUrl(user, message)
    val mimetype = getMimetype(message)
    val drawableResourceId = DrawableUtils.getDrawableResourceIdForMimeType(mimetype)

    return MessageTypeContent.Image(
        imageUri,
        drawableResourceId
    )
}

fun getPreviewImageUrl(
    user: User,
    message: ChatMessage
): String? {
    if (!message.messageParameters.isNullOrEmpty()) {
        for ((_, individualHashMap) in message.messageParameters!!) {
            if (isHashMapEntryEqualTo(individualHashMap, "type", "file")) {
                if (!message.isVoiceMessage) {
                    if (user != null && user!!.baseUrl != null) {
                        return ApiUtils.getUrlForFilePreviewWithFileId(
                            user!!.baseUrl!!,
                            individualHashMap["id"]!!,
                            sharedApplication!!.resources.getDimensionPixelSize(R.dimen.maximum_file_preview_size)
                        )
                    }
                }
            }
        }
    }
    return null
}

fun getMimetype(
    message: ChatMessage
): String? {
    if (!message.messageParameters.isNullOrEmpty()) {
        for ((_, individualHashMap) in message.messageParameters!!) {
            if (isHashMapEntryEqualTo(individualHashMap, "type", "file")) {
                return individualHashMap["mimetype"]
            }
        }
    }
    return null
}

private fun isHashMapEntryEqualTo(map: HashMap<String?, String?>, key: String, searchTerm: String): Boolean =
    map != null && MessageDigest.isEqual(map[key]!!.toByteArray(), searchTerm.toByteArray())


fun getGeolocationContent(message: ChatMessage): MessageTypeContent.Geolocation? {
    if (!message.messageParameters.isNullOrEmpty()) {
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
    if (!message.messageParameters.isNullOrEmpty()) {
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
    if (!message.messageParameters.isNullOrEmpty()) {
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
