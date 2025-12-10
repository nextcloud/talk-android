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

    data class LinkPreview(val url: String) : MessageTypeContent

    data class Media(val previewUrl: String?, val drawableResourceId: Int) : MessageTypeContent

    data class Geolocation(
        val id: String,
        val name: String,
        val lat: Double,
        val lon: Double
    ) : MessageTypeContent

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
    } else if (message.hasFileAttachment) {
        getMediaContent(user, message)
    } else if (message.hasGeoLocation) {
        getGeolocationContent(message)
    } else if (message.hasPoll) {
        getPollContent(message)
    } else if (message.hasDeckCard) {
        getDeckContent(message)
    } else {
        message.extractLinkPreviewUrl(user)
            ?.let { MessageTypeContent.LinkPreview(url = it) }
            ?: MessageTypeContent.RegularText
    }

fun getMediaContent(
    user: User,
    message: ChatMessage
): MessageTypeContent.Media {
    val previewUrl = getPreviewImageUrl(user, message)
    val mimetype = message.fileParameters.mimetype
    val drawableResourceId = DrawableUtils.getDrawableResourceIdForMimeType(mimetype)

    return MessageTypeContent.Media(
        previewUrl,
        drawableResourceId
    )

    // use previewAvailable
}

// fun fetchFileInformation(url: String, activeUser: User?) {
//     Single.fromCallable { ReadFilesystemOperation(okHttpClient, activeUser, url, 0) }
//         .observeOn(Schedulers.io())
//         .subscribe(object : SingleObserver<ReadFilesystemOperation> {
//             override fun onSubscribe(d: Disposable) {
//                 // unused atm
//             }
//
//             override fun onSuccess(readFilesystemOperation: ReadFilesystemOperation) {
//                 val davResponse = readFilesystemOperation.readRemotePath()
//                 if (davResponse.data != null) {
//                     val browserFileList = davResponse.data as List<BrowserFile>
//                     if (browserFileList.isNotEmpty()) {
//                         Handler(context!!.mainLooper).post {
//                             val resourceId = getDrawableResourceIdForMimeType(browserFileList[0].mimeType)
//                             placeholder = ContextCompat.getDrawable(context!!, resourceId)
//                         }
//                     }
//                 }
//             }
//
//             override fun onError(e: Throwable) {
//                 Log.e(TAG, "Error reading file information", e)
//             }
//         })
// }

fun getPreviewImageUrl(
    user: User,
    message: ChatMessage
): String? {
    if (message.fileParameters.previewAvailable) {
        return ApiUtils.getUrlForFilePreviewWithFileId(
            user.baseUrl!!,
            message.fileParameters.id,
            sharedApplication!!.resources.getDimensionPixelSize(R.dimen.maximum_file_preview_size) // TODO adjust size?
        )
    }
    return null
}

fun getGeolocationContent(message: ChatMessage): MessageTypeContent.Geolocation {
    return MessageTypeContent.Geolocation(
        id = message.geoLocationParameters.id,
        name = message.geoLocationParameters.name,
        lat = message.geoLocationParameters.latitude!!,
        lon = message.geoLocationParameters.longitude!!
    )
}

fun getPollContent(message: ChatMessage): MessageTypeContent.Poll {
    return MessageTypeContent.Poll(
        pollId = message.pollParameters.id,
        pollName = message.pollParameters.name
    )
}

fun getDeckContent(message: ChatMessage): MessageTypeContent.Deck {
    return MessageTypeContent.Deck(
        cardName = message.deckCardParameters.name,
        stackName = message.deckCardParameters.stackName,
        boardName = message.deckCardParameters.boardName,
        cardLink = message.deckCardParameters.link
    )
}

fun getVoiceContent(message: ChatMessage): MessageTypeContent.Voice =
    MessageTypeContent.Voice(
        todo = "still todo..."
    )
