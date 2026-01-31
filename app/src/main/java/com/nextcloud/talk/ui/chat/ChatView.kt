package com.nextcloud.talk.ui.chat

import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextcloud.talk.R
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.data.user.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val LONG_1000 = 1000L
private const val SCROLL_DELAY = 20L
private const val ANIMATION_DURATION = 2500L
private val AUTHOR_TEXT_SIZE = 12.sp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GetNewChatView(messages: List<ChatMessage>, conversationThreadId: Long? = null) {
    val listState = rememberLazyListState()
    val displayedMessages = remember(messages) { messages.reversed() }

    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize()
    ) {
        items(displayedMessages, key = { it.id }) { message ->
            val isBlinkingState = remember { mutableStateOf(false) }

            GetComposableForMessage(
                message = message,
                conversationThreadId = conversationThreadId,
                isBlinkingState = isBlinkingState
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GetView(messages: List<ChatMessage>, messageIdToBlink: String, user: User?) {
    val listState = rememberLazyListState()
    val isBlinkingState = remember { mutableStateOf(true) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        state = listState,
        modifier = Modifier.padding(16.dp)
    ) {
        stickyHeader {
            if (messages.isEmpty()) {
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxSize()
                ) {
                    ShimmerGroup()
                }
            } else {
                val timestamp = messages[listState.firstVisibleItemIndex].timestamp
                val dateString = formatTime(timestamp * LONG_1000)
                val color = colorScheme.onSurfaceVariant
                val backgroundColor =
                    LocalResources.current.getColor(R.color.bg_message_list_incoming_bubble, null)
                Row(
                    horizontalArrangement = Arrangement.Absolute.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        dateString,
                        fontSize = AUTHOR_TEXT_SIZE,
                        color = color,
                        modifier = Modifier
                            .padding(8.dp)
                            .shadow(
                                16.dp,
                                spotColor = colorScheme.primary,
                                ambientColor = colorScheme.primary
                            )
                            .background(color = Color(backgroundColor), shape = RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        items(messages) { message ->
            val incoming = message.actorId != user?.userId

            GetComposableForMessage(
                message = message,
                isBlinkingState = isBlinkingState
            )
        }
    }

    if (messages.isNotEmpty()) {
        LaunchedEffect(Dispatchers.Main) {
            delay(SCROLL_DELAY)
            val pos = searchMessages(
                messages,
                messageIdToBlink
            )
            if (pos > 0) {
                listState.scrollToItem(pos)
            }
            delay(ANIMATION_DURATION)
            isBlinkingState.value = false
        }
    }
}

@Composable
fun GetComposableForMessage(
    message: ChatMessage,
    conversationThreadId: Long? = null,
    isBlinkingState: MutableState<Boolean> = mutableStateOf(false)
) {
    when (val type = message.getCalculateMessageType()) {
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
                    conversationThreadId = conversationThreadId,
                    state = isBlinkingState
                )
            }
        }

        else -> {
            Log.d("ChatView", "Unknown message type: $type")
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

fun formatTime(timestampMillis: Long): String {
    val instant = Instant.ofEpochMilli(timestampMillis)
    val dateTime = instant.atZone(ZoneId.systemDefault()).toLocalDate()
    val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")
    return dateTime.format(formatter)
}

fun searchMessages(messages: List<ChatMessage>, searchId: String): Int {
    messages.forEachIndexed { index, message ->
        if (message.id == searchId) return index
    }
    return -1
}
