/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017-2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chat

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.nextcloud.talk.R
import com.nextcloud.talk.chat.ui.model.ChatMessageUi
import com.nextcloud.talk.chat.ui.model.MessageTypeContent
import kotlinx.coroutines.delay

private const val QUOTE_HIGHLIGHT_INITIAL_ALPHA = 0.15f
private const val PREVIEW_WAVEFORM_POINT_ONE = 0.1f
private const val PREVIEW_WAVEFORM_POINT_TWO = 0.2f
private const val PREVIEW_WAVEFORM_POINT_THREE = 0.4f
private const val PREVIEW_WAVEFORM_POINT_FOUR = 0.15f
private const val PREVIEW_WAVEFORM_POINT_FIVE = 0.3f
private const val PREVIEW_WAVEFORM_POINT_SIX = 0.5f

private val previewWaveform = listOf(
    PREVIEW_WAVEFORM_POINT_ONE,
    PREVIEW_WAVEFORM_POINT_TWO,
    PREVIEW_WAVEFORM_POINT_THREE,
    PREVIEW_WAVEFORM_POINT_FOUR,
    PREVIEW_WAVEFORM_POINT_FIVE,
    PREVIEW_WAVEFORM_POINT_SIX
)

private const val QUOTE_HIGHLIGHT_HOLD_MILLIS = 700L
private const val QUOTE_HIGHLIGHT_FADE_OUT_MILLIS = 1500

data class ChatMessageContext(
    val isOneToOneConversation: Boolean = false,
    val conversationThreadId: Long? = null,
    val hasChatPermission: Boolean = true
)

class ChatMessageCallbacks(
    val onLongClick: ((Int) -> Unit?)? = null,
    val onSwipeReply: ((Int) -> Unit)? = null,
    val onFileClick: (Int) -> Unit = {},
    val onPollClick: (String, String) -> Unit = { _, _ -> },
    val onVoicePlayPauseClick: (Int) -> Unit = {},
    val onVoiceSeek: (Int, Int) -> Unit = { _, _ -> },
    val onVoiceSpeedClick: (Int) -> Unit = {},
    val onReactionClick: (Int, String) -> Unit = { _, _ -> },
    val onReactionLongClick: (Int) -> Unit = {},
    val onOpenThreadClick: (Int) -> Unit = {},
    val onQuotedMessageClick: (Int) -> Unit = {},
    val onCancelUpload: (String) -> Unit = {}
)

@Composable
fun ChatMessageView(
    message: ChatMessageUi,
    highlightTriggerKey: Long? = null,
    context: ChatMessageContext = ChatMessageContext(),
    callbacks: ChatMessageCallbacks = ChatMessageCallbacks()
) {
    val interactionSource = remember { MutableInteractionSource() }
    val lastHandledHighlightKey = rememberSaveable(message.id) { mutableStateOf<Long?>(null) }
    val highlightAlpha = remember { Animatable(0f) }

    LaunchedEffect(highlightTriggerKey) {
        if (highlightTriggerKey == null || highlightTriggerKey == lastHandledHighlightKey.value) {
            return@LaunchedEffect
        }

        lastHandledHighlightKey.value = highlightTriggerKey

        highlightAlpha.snapTo(QUOTE_HIGHLIGHT_INITIAL_ALPHA)
        delay(QUOTE_HIGHLIGHT_HOLD_MILLIS)
        highlightAlpha.animateTo(0f, animationSpec = tween(durationMillis = QUOTE_HIGHLIGHT_FADE_OUT_MILLIS))
    }

    CompositionLocalProvider(
        LocalReactionClickHandler provides callbacks.onReactionClick,
        LocalReactionLongClickHandler provides callbacks.onReactionLongClick,
        LocalOpenThreadHandler provides callbacks.onOpenThreadClick,
        LocalQuotedMessageClickHandler provides callbacks.onQuotedMessageClick
    ) {
        SwipeToReplyContainer(
            replyable = message.replyable && context.hasChatPermission,
            onSwipeReply = { callbacks.onSwipeReply?.invoke(message.id) }
        ) {
            Box(
                modifier = Modifier
                    .combinedClickable(
                        interactionSource = interactionSource,
                        indication = ripple(),
                        onClick = { callbacks.onLongClick?.invoke(message.id) },
                        onDoubleClick = { callbacks.onLongClick?.invoke(message.id) },
                        onLongClick = { callbacks.onLongClick?.invoke(message.id) }
                    )
            ) {
                when (val content = message.content) {
                    MessageTypeContent.RegularText -> {
                        TextMessage(
                            uiMessage = message,
                            isOneToOneConversation = context.isOneToOneConversation,
                            conversationThreadId = context.conversationThreadId
                        )
                    }

                    MessageTypeContent.SystemMessage -> {
                        SystemMessage(message)
                    }

                    is MessageTypeContent.Media -> {
                        MediaMessage(
                            typeContent = content,
                            message = message,
                            isOneToOneConversation = context.isOneToOneConversation,
                            conversationThreadId = context.conversationThreadId,
                            onImageClick = callbacks.onFileClick
                        )
                    }

                    is MessageTypeContent.LinkPreview -> {
                        LinkMessage(
                            typeContent = content,
                            message = message,
                            isOneToOneConversation = context.isOneToOneConversation,
                            conversationThreadId = context.conversationThreadId
                        )
                    }

                    is MessageTypeContent.Geolocation -> {
                        GeolocationMessage(
                            typeContent = content,
                            message = message,
                            isOneToOneConversation = context.isOneToOneConversation,
                            conversationThreadId = context.conversationThreadId
                        )
                    }

                    is MessageTypeContent.Voice -> {
                        VoiceMessage(
                            typeContent = content,
                            message = message,
                            isOneToOneConversation = context.isOneToOneConversation,
                            conversationThreadId = context.conversationThreadId,
                            onPlayPauseClick = callbacks.onVoicePlayPauseClick,
                            onSeek = callbacks.onVoiceSeek,
                            onSpeedClick = callbacks.onVoiceSpeedClick
                        )
                    }

                    is MessageTypeContent.Poll -> {
                        PollMessage(
                            typeContent = content,
                            message = message,
                            isOneToOneConversation = context.isOneToOneConversation,
                            conversationThreadId = context.conversationThreadId,
                            onPollClick = callbacks.onPollClick
                        )
                    }

                    is MessageTypeContent.Deck -> {
                        DeckMessage(
                            typeContent = content,
                            message = message,
                            isOneToOneConversation = context.isOneToOneConversation,
                            conversationThreadId = context.conversationThreadId
                        )
                    }

                    is MessageTypeContent.UploadingMedia -> {
                        UploadingMediaMessage(
                            typeContent = content,
                            message = message,
                            isOneToOneConversation = context.isOneToOneConversation,
                            conversationThreadId = context.conversationThreadId,
                            onCancelUpload = callbacks.onCancelUpload
                        )
                    }

                    else -> {
                        Log.d("ChatView", "Unknown message type: ${'$'}content")
                    }
                }
                if (highlightAlpha.value > 0f) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = highlightAlpha.value))
                    )
                }
            }
        }
    }
}

@ChatMessagePreviews
@Composable
private fun ChatMessageViewRegularTextPreview() {
    PreviewContainer {
        val uiMessage = createBaseMessage(MessageTypeContent.RegularText)
        ChatMessageView(message = uiMessage)
    }
}

@ChatMessagePreviews
@Composable
private fun ChatMessageViewRegularLongTextPreview() {
    PreviewContainer {
        val uiMessage = createLongBaseMessage(MessageTypeContent.RegularText)
        ChatMessageView(message = uiMessage)
    }
}

@ChatMessagePreviews
@Composable
private fun ChatMessageViewSystemMessagePreview() {
    PreviewContainer {
        val uiMessage = createBaseMessage(MessageTypeContent.SystemMessage)
            .copy(message = "You joined the conversation")
        ChatMessageView(message = uiMessage)
    }
}

@ChatMessagePreviews
@Composable
private fun ChatMessageViewMediaPreview() {
    PreviewContainer {
        val uiMessage = createBaseMessage(
            MessageTypeContent.Media(
                previewUrl = null,
                drawableResourceId = R.drawable.ic_mimetype_image
            )
        )
        ChatMessageView(message = uiMessage)
    }
}

@ChatMessagePreviews
@Composable
private fun ChatMessageViewMediaPreviewWithoutCaption() {
    PreviewContainer {
        val uiMessage = createBaseMessageWithoutCaption(
            MessageTypeContent.Media(
                previewUrl = null,
                drawableResourceId = R.drawable.ic_mimetype_image
            )
        )
        ChatMessageView(message = uiMessage)
    }
}

@ChatMessagePreviews
@Composable
private fun ChatMessageViewGeolocationPreview() {
    PreviewContainer {
        val uiMessage = createBaseMessage(
            MessageTypeContent.Geolocation(
                id = "geo:52.5200,13.4050",
                name = "Berlin, Germany",
                lat = 52.5200,
                lon = 13.4050
            )
        )
        ChatMessageView(message = uiMessage)
    }
}

@ChatMessagePreviews
@Composable
private fun ChatMessageViewVoicePreview() {
    PreviewContainer {
        val uiMessage = createBaseMessage(
            MessageTypeContent.Voice(
                actorId = "john",
                isPlaying = false,
                wasPlayed = false,
                isDownloading = false,
                durationSeconds = 16,
                playedSeconds = 4,
                seekbarProgress = 25,
                waveform = previewWaveform
            )
        )
        ChatMessageView(message = uiMessage)
    }
}

@ChatMessagePreviews
@Composable
private fun ChatMessageViewPollPreview() {
    PreviewContainer {
        val uiMessage = createBaseMessage(
            MessageTypeContent.Poll(
                pollId = "1",
                pollName = "What's your favorite color?"
            )
        )
        ChatMessageView(message = uiMessage)
    }
}

@ChatMessagePreviews
@Composable
private fun ChatMessageViewDeckPreview() {
    PreviewContainer {
        val uiMessage = createBaseMessage(
            MessageTypeContent.Deck(
                cardName = "Fix all bugs",
                stackName = "Todo",
                boardName = "Talk Android",
                cardLink = ""
            )
        )
        ChatMessageView(message = uiMessage)
    }
}

@ChatMessagePreviews
@Composable
private fun ChatMessageViewLinkPreviewPreview() {
    PreviewContainer {
        val uiMessage = createBaseMessage(MessageTypeContent.LinkPreview(url = "https://nextcloud.com/"))
        ChatMessageView(message = uiMessage)
    }
}
