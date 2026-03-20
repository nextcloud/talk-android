/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chat

import android.util.Log
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.nextcloud.talk.R
import com.nextcloud.talk.chat.ui.model.ChatMessageUi
import com.nextcloud.talk.chat.ui.model.MessageReactionUi
import com.nextcloud.talk.chat.ui.model.MessageStatusIcon
import com.nextcloud.talk.chat.ui.model.MessageTypeContent
import java.time.LocalDate

private val PREVIEW_REACTIONS = listOf(
    MessageReactionUi(emoji = "👍", amount = 1, isSelfReaction = true),
    MessageReactionUi(emoji = "❤️", amount = 1, isSelfReaction = false)
)

@Composable
fun ChatMessageView(
    message: ChatMessageUi,
    isOneToOneConversation: Boolean = false,
    conversationThreadId: Long? = null,
    onLongClick: ((Int) -> Unit?)? = null,
    onFileClick: (Int) -> Unit = {},
    onPollClick: (pollId: String, pollName: String) -> Unit = { _, _ -> },
    onVoicePlayPauseClick: (Int) -> Unit = {},
    onVoiceSeek: (messageId: Int, progress: Int) -> Unit = { _, _ -> },
    onVoiceSpeedClick: (Int) -> Unit = {},
    onReactionClick: (messageId: Int, emoji: String) -> Unit = { _, _ -> },
    onReactionLongClick: (messageId: Int) -> Unit = {}
) {
    CompositionLocalProvider(
        LocalReactionClickHandler provides onReactionClick,
        LocalReactionLongClickHandler provides onReactionLongClick
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
                        isOneToOneConversation = isOneToOneConversation,
                        conversationThreadId = conversationThreadId
                    )
                }

                MessageTypeContent.SystemMessage -> {
                    SystemMessage(message)
                }

                is MessageTypeContent.Media -> {
                    MediaMessage(
                        typeContent = content,
                        message = message,
                        isOneToOneConversation = isOneToOneConversation,
                        conversationThreadId = conversationThreadId,
                        onImageClick = onFileClick
                    )
                }

                is MessageTypeContent.LinkPreview -> {
                    LinkMessage(
                        typeContent = content,
                        message = message,
                        isOneToOneConversation = isOneToOneConversation,
                        conversationThreadId = conversationThreadId
                    )
                }

                is MessageTypeContent.Geolocation -> {
                    GeolocationMessage(
                        typeContent = content,
                        message = message,
                        isOneToOneConversation = isOneToOneConversation,
                        conversationThreadId = conversationThreadId
                    )
                }

            is MessageTypeContent.Voice -> {
                VoiceMessage(
                    typeContent = content,
                    message = message,
                    isOneToOneConversation = isOneToOneConversation,
                    conversationThreadId = conversationThreadId,
                    onPlayPauseClick = onVoicePlayPauseClick,
                    onSeek = onVoiceSeek,
                    onSpeedClick = onVoiceSpeedClick
                )
            }

                is MessageTypeContent.Poll -> {
                    PollMessage(
                        typeContent = content,
                        message = message,
                        isOneToOneConversation = isOneToOneConversation,
                        conversationThreadId = conversationThreadId,
                        onPollClick = onPollClick
                    )
                }

                is MessageTypeContent.Deck -> {
                    DeckMessage(
                        typeContent = content,
                        message = message,
                        isOneToOneConversation = isOneToOneConversation,
                        conversationThreadId = conversationThreadId
                    )
                }

                else -> {
                    Log.d("ChatView", "Unknown message type: ${'$'}content")
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Regular Text")
@Composable
private fun ChatMessageViewRegularTextPreview() {
    PreviewContainer {
        val uiMessage = createBaseMessage(MessageTypeContent.RegularText)
        ChatMessageView(message = uiMessage)
    }
}

@Preview(showBackground = true, name = "Regular Text")
@Composable
private fun ChatMessageViewRegularLongTextPreview() {
    PreviewContainer {
        val uiMessage = createLongBaseMessage(MessageTypeContent.RegularText)
        ChatMessageView(message = uiMessage)
    }
}

@Preview(showBackground = true, name = "System Message")
@Composable
private fun ChatMessageViewSystemMessagePreview() {
    PreviewContainer {
        val uiMessage = createBaseMessage(MessageTypeContent.SystemMessage)
            .copy(text = "You joined the conversation")
        ChatMessageView(message = uiMessage)
    }
}

@Preview(showBackground = true, name = "Media Message")
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

@Preview(showBackground = true, name = "Media Message")
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

@Preview(showBackground = true, name = "Geolocation Message")
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

@Preview(showBackground = true, name = "Voice Message")
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
                waveform = listOf(0.1f, 0.2f, 0.4f, 0.15f, 0.3f, 0.5f)
            )
        )
        ChatMessageView(message = uiMessage)
    }
}

@Preview(showBackground = true, name = "Poll Message")
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

@Preview(showBackground = true, name = "Deck Message")
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

@Preview(showBackground = true, name = "Link Preview Message")
@Composable
private fun ChatMessageViewLinkPreviewPreview() {
    PreviewContainer {
        val uiMessage = createBaseMessage(MessageTypeContent.LinkPreview(url = "https://nextcloud.com/"))
        ChatMessageView(message = uiMessage)
    }
}

@Composable
private fun PreviewContainer(content: @Composable () -> Unit) {
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        content()
    }
}

private fun createBaseMessage(content: MessageTypeContent?): ChatMessageUi {
    return ChatMessageUi(
        id = 1,
        text = "Sample message text",
        message = "Sample message text",
        renderMarkdown = false,
        actorDisplayName = "John Doe",
        isThread = false,
        threadTitle = "",
        incoming = true,
        isDeleted = false,
        avatarUrl = null,
        statusIcon = MessageStatusIcon.SENT,
        timestamp = System.currentTimeMillis() / 1000,
        date = LocalDate.now(),
        content = content,
        reactions = PREVIEW_REACTIONS
    )
}

private fun createBaseMessageWithoutCaption(content: MessageTypeContent?): ChatMessageUi {
    return ChatMessageUi(
        id = 1,
        text = "",
        message = "",
        renderMarkdown = false,
        actorDisplayName = "John Doe",
        isThread = false,
        threadTitle = "",
        incoming = true,
        isDeleted = false,
        avatarUrl = null,
        statusIcon = MessageStatusIcon.SENT,
        timestamp = System.currentTimeMillis() / 1000,
        date = LocalDate.now(),
        content = content,
        reactions = PREVIEW_REACTIONS
    )
}

private fun createLongBaseMessage(content: MessageTypeContent?): ChatMessageUi {
    return ChatMessageUi(
        id = 1,
        text = "Sample message text that is very very very very very long",
        message = "Sample message text that is very very very very very long",
        renderMarkdown = false,
        actorDisplayName = "John Doe",
        isThread = false,
        threadTitle = "",
        incoming = true,
        isDeleted = false,
        avatarUrl = null,
        statusIcon = MessageStatusIcon.SENT,
        timestamp = System.currentTimeMillis() / 1000,
        date = LocalDate.now(),
        content = content,
        reactions = PREVIEW_REACTIONS
    )
}
