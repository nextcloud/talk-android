/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017-2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.ui.model

import androidx.compose.runtime.Stable
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.data.database.model.SendStatus
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.ui.PlaybackSpeed
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.DrawableUtils
import java.time.LocalDate

// immutable class for chat message UI. only val, no vars!
@Stable // Consider evaluating @Immutable once all nested types comply.
data class ChatMessageUi(
    val id: Int,
    val message: String,
    val plainMessage: String = "",
    val renderMarkdown: Boolean,
    val actorDisplayName: String,
    val isThread: Boolean,
    val threadTitle: String,
    val threadReplies: Int,
    val incoming: Boolean,
    val isDeleted: Boolean,
    val avatarUrl: String?,
    val statusIcon: MessageStatusIcon,
    val timestamp: Long,
    val date: LocalDate,
    val content: MessageTypeContent?,
    val roomToken: String? = null,
    val activeUserId: String? = null,
    val activeUserBaseUrl: String? = null,
    val messageParameters: Map<String, Map<String, String>> = emptyMap(),
    val reactions: List<MessageReactionUi> = emptyList(),
    val isEdited: Boolean = false,
    val parentMessage: ChatMessageUi? = null,
    val replyable: Boolean = false,
    val isGrouped: Boolean = false,
    val isGroupedWithNext: Boolean = false
)

data class MessageReactionUi(val emoji: String, val amount: Int, val isSelfReaction: Boolean)

sealed interface MessageTypeContent {
    object RegularText : MessageTypeContent
    object SystemMessage : MessageTypeContent

    data class LinkPreview(val url: String) : MessageTypeContent

    data class Media(val previewUrl: String?, val drawableResourceId: Int) : MessageTypeContent

    data class Geolocation(val id: String, val name: String, val lat: Double, val lon: Double) : MessageTypeContent

    data class Poll(val pollId: String, val pollName: String) : MessageTypeContent

    data class Deck(val cardName: String, val stackName: String, val boardName: String, val cardLink: String) :
        MessageTypeContent

    data class Voice(
        val actorId: String?,
        val isPlaying: Boolean,
        val wasPlayed: Boolean,
        val isDownloading: Boolean,
        val durationSeconds: Int,
        val playedSeconds: Int,
        val seekbarProgress: Int,
        val waveform: List<Float>,
        val playbackSpeed: PlaybackSpeed = PlaybackSpeed.NORMAL
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
        message = getRichText(),
        plainMessage = message.orEmpty(),
        renderMarkdown = renderMarkdown == true,
        actorDisplayName = actorDisplayName.orEmpty(),
        threadTitle = threadTitle.orEmpty(),
        isThread = isThread,
        threadReplies = threadReplies ?: 0,
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
        roomToken = token,
        activeUserId = user.userId,
        activeUserBaseUrl = user.baseUrl,
        messageParameters = normalizeMessageParameters(),
        reactions = getReactionUiModels(),
        isEdited = lastEditTimestamp != 0L,
        // setting parent message recursively might be a problem regarding recompositions? extract only what is needed
        // for UI?
        parentMessage = parentMessage?.toUiModel(
            user = user,
            chatMessage = parentMessage,
            lastCommonReadMessageId = 0,
            parentMessage = null
        ),
        replyable = replyable,
        isGrouped = isGrouped,
        isGroupedWithNext = isGroupedWithNext
    )

private fun ChatMessage.normalizeMessageParameters(): Map<String, Map<String, String>> =
    messageParameters
        .orEmpty()
        .mapNotNull { (key, params) ->
            if (key == null) {
                null
            } else {
                val normalizedParams = params
                    .orEmpty()
                    .mapNotNull { (nestedKey, value) ->
                        if (nestedKey == null || value == null) {
                            null
                        } else {
                            nestedKey to value
                        }
                    }
                    .toMap()
                key to normalizedParams
            }
        }
        .toMap()

private fun ChatMessage.getReactionUiModels(): List<MessageReactionUi> {
    val selfReactions = reactionsSelf.orEmpty().toSet()

    return reactions.orEmpty()
        .filterValues { amount -> amount > 0 }
        .map { (emoji, amount) ->
            MessageReactionUi(
                emoji = emoji,
                amount = amount,
                isSelfReaction = selfReactions.contains(emoji)
            )
        }
        .sortedWith(compareByDescending<MessageReactionUi> { it.isSelfReaction }.thenByDescending { it.amount })
}

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

fun getMessageTypeContent(user: User, message: ChatMessage): MessageTypeContent? =
    if (message.isSystemMessage) {
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

fun getMediaContent(user: User, message: ChatMessage): MessageTypeContent.Media {
    val previewUrl = getPreviewImageUrl(user, message)
    val mimetype = message.fileParameters.mimetype
    val drawableResourceId = DrawableUtils.getDrawableResourceIdForMimeType(mimetype)

    return MessageTypeContent.Media(
        previewUrl,
        drawableResourceId
    )
}

fun getPreviewImageUrl(user: User, message: ChatMessage): String? {
    if (message.fileParameters.previewAvailable) {
        return ApiUtils.getUrlForFilePreviewWithFileId(
            user.baseUrl!!,
            message.fileParameters.id,
            sharedApplication!!.resources.getDimensionPixelSize(R.dimen.maximum_file_preview_size)
        )
    }
    return null
}

fun getGeolocationContent(message: ChatMessage): MessageTypeContent.Geolocation =
    MessageTypeContent.Geolocation(
        id = message.geoLocationParameters.id,
        name = message.geoLocationParameters.name,
        lat = message.geoLocationParameters.latitude!!,
        lon = message.geoLocationParameters.longitude!!
    )

fun getPollContent(message: ChatMessage): MessageTypeContent.Poll =
    MessageTypeContent.Poll(
        pollId = message.pollParameters.id,
        pollName = message.pollParameters.name
    )

fun getDeckContent(message: ChatMessage): MessageTypeContent.Deck =
    MessageTypeContent.Deck(
        cardName = message.deckCardParameters.name,
        stackName = message.deckCardParameters.stackName,
        boardName = message.deckCardParameters.boardName,
        cardLink = message.deckCardParameters.link
    )

fun getVoiceContent(message: ChatMessage): MessageTypeContent.Voice =
    MessageTypeContent.Voice(
        actorId = message.actorId,
        isPlaying = message.isPlayingVoiceMessage,
        wasPlayed = message.wasPlayedVoiceMessage,
        isDownloading = message.isDownloadingVoiceMessage,
        durationSeconds = message.voiceMessageDuration,
        playedSeconds = message.voiceMessagePlayedSeconds,
        seekbarProgress = message.voiceMessageSeekbarProgress,
        waveform = message.voiceMessageFloatArray?.toList().orEmpty()
    )
