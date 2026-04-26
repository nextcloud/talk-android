/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017-2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextcloud.talk.chat.ui.model.ChatMessageUi
import com.nextcloud.talk.chat.ui.model.MessageStatusIcon
import com.nextcloud.talk.chat.ui.model.MessageTypeContent
import com.nextcloud.talk.chat.viewmodels.ChatViewModel
import com.nextcloud.talk.ui.theme.LocalViewThemeUtils
import com.nextcloud.talk.utils.preview.ComposePreviewUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.nextcloud.talk.R

private const val LONG_1000 = 1000L
private const val LOAD_MORE_BUFFER_ITEMS = 5
private const val STICKY_HEADER_HIDE_DELAY_MILLIS = 1200L
private const val PREVIEW_SAMPLE_CHAT_COUNT = 50
private const val PREVIEW_UNREAD_MARKER_OFFSET = 15

private data class QuoteHighlightEvent(val messageId: Int, val nonce: Long)

data class ChatViewState(
    val chatItems: List<ChatViewModel.ChatItem>,
    val isOneToOneConversation: Boolean,
    val conversationThreadId: Long? = null,
    val hasChatPermission: Boolean = true,
    val initialUnreadCount: Int = 0,
    val initialShowUnreadPopup: Boolean = false
)

class ChatViewCallbacks(
    val onLoadMore: (() -> Unit?)? = null,
    val advanceLocalLastReadMessageIfNeeded: ((Int) -> Unit?)? = null,
    val updateRemoteLastReadMessageIfNeeded: (() -> Unit?)? = null,
    val onLongClick: ((Int) -> Unit?)? = null,
    val onFileClick: (Int) -> Unit = {},
    val onPollClick: (String, String) -> Unit = { _, _ -> },
    val onVoicePlayPauseClick: (Int) -> Unit = {},
    val onVoiceSeek: (Int, Int) -> Unit = { _, _ -> },
    val onVoiceSpeedClick: (Int) -> Unit = {},
    val onReactionClick: (Int, String) -> Unit = { _, _ -> },
    val onReactionLongClick: (Int) -> Unit = {},
    val onOpenThreadClick: (Int) -> Unit = {},
    val onLoadQuotedMessageClick: (Int) -> Unit = {},
    val onSwipeReply: ((Int) -> Unit)? = null,
    val onCancelUpload: (String) -> Unit = {}
)

@Suppress("Detekt.LongMethod", "Detekt.ComplexMethod")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatView(
    state: ChatViewState,
    callbacks: ChatViewCallbacks = ChatViewCallbacks(),
    listState: LazyListState = rememberLazyListState()
) {
    val viewThemeUtils = LocalViewThemeUtils.current
    val colorScheme = viewThemeUtils.getColorScheme(LocalContext.current)

    val showUnreadPopup = remember { mutableStateOf(state.initialShowUnreadPopup) }
    val coroutineScope = rememberCoroutineScope()

    val lastNewestIdRef = remember {
        object {
            var value: Int? = null
        }
    }

    var unreadCount by remember { mutableIntStateOf(state.initialUnreadCount) }

    val isAtNewest by remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 &&
                listState.firstVisibleItemScrollOffset == 0
        }
    }

    val isNearNewest by remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex <= 2
        }
    }

    val showScrollToNewest by remember { derivedStateOf { !isNearNewest } }

    val latestChatItems by rememberUpdatedState(state.chatItems)
    val latestOnLoadQuotedMessageClick by rememberUpdatedState(callbacks.onLoadQuotedMessageClick)
    var quoteHighlightEvent by remember { mutableStateOf<QuoteHighlightEvent?>(null) }

    val handleQuotedMessageClick: (Int) -> Unit = remember(coroutineScope, listState) {
        { messageId ->
            coroutineScope.launch {
                val targetIndex = latestChatItems.indexOfFirst { item ->
                    (item as? ChatViewModel.ChatItem.MessageItem)?.uiMessage?.id == messageId
                }

                if (targetIndex >= 0) {
                    val isVisible = listState.layoutInfo.visibleItemsInfo.any { it.index == targetIndex }
                    if (!isVisible) {
                        listState.scrollToItem(targetIndex)
                        listState.centerItemInViewportIfVisible(targetIndex)
                    }
                    quoteHighlightEvent = QuoteHighlightEvent(
                        messageId = messageId,
                        nonce = System.nanoTime()
                    )
                } else {
                    latestOnLoadQuotedMessageClick(messageId)
                }
            }
        }
    }

    // Track newest message and show unread popup
    LaunchedEffect(state.chatItems) {
        if (state.chatItems.isEmpty()) return@LaunchedEffect

        val newestId = state.chatItems.firstNotNullOfOrNull { it.messageOrNull()?.id }
        val previousNewestId = lastNewestIdRef.value

        val isNearBottom = listState.firstVisibleItemIndex <= 2
        val hasNewMessage = previousNewestId != null && newestId != previousNewestId

        if (hasNewMessage) {
            if (isNearBottom) {
                listState.animateScrollToItem(0)
                unreadCount = 0
            } else {
                unreadCount++
                showUnreadPopup.value = true
            }
        }

        lastNewestIdRef.value = newestId
    }

    // Hide unread popup when user scrolls to newest
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .map { it <= 2 }
            .distinctUntilChanged()
            .collect { nearBottom ->
                if (nearBottom) {
                    showUnreadPopup.value = false
                    unreadCount = 0
                }
            }
    }

    // Load more when near end
    LaunchedEffect(listState, state.chatItems.size) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val total = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible to total
        }
            .distinctUntilChanged()
            .collect { (lastVisible, total) ->
                if (total == 0) return@collect

                val buffer = LOAD_MORE_BUFFER_ITEMS
                val shouldLoadMore = lastVisible >= (total - 1 - buffer)

                if (shouldLoadMore) {
                    callbacks.onLoadMore?.invoke()
                }
            }
    }

    // Sticky date header
    val stickyDateHeaderText by remember(listState, state.chatItems) {
        derivedStateOf {
            state.chatItems.getOrNull(
                listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            )?.let { item ->
                when (item) {
                    is ChatViewModel.ChatItem.MessageItem ->
                        formatTime(item.uiMessage.timestamp * LONG_1000)

                    is ChatViewModel.ChatItem.DateHeaderItem ->
                        formatTime(item.date)

                    is ChatViewModel.ChatItem.UnreadMessagesMarkerItem ->
                        formatTime(item.date)
                }
            } ?: ""
        }
    }

    var stickyDateHeader by remember { mutableStateOf(false) }

    LaunchedEffect(listState, isNearNewest) {
        if (!isNearNewest) {
            callbacks.updateRemoteLastReadMessageIfNeeded?.invoke()
            snapshotFlow { listState.isScrollInProgress }
                .collectLatest { scrolling ->
                    if (scrolling) {
                        stickyDateHeader = true
                    } else {
                        delay(STICKY_HEADER_HIDE_DELAY_MILLIS)
                        stickyDateHeader = false
                    }
                }
        } else {
            stickyDateHeader = false
        }
    }

    LaunchedEffect(isAtNewest) {
        if (!isAtNewest) return@LaunchedEffect

        latestChatItems
            .getOrNull(listState.firstVisibleItemIndex)
            ?.let { item ->
                if (item is ChatViewModel.ChatItem.MessageItem) {
                    callbacks.advanceLocalLastReadMessageIfNeeded?.invoke(item.uiMessage.id)
                }
            }
    }

    val stickyDateHeaderAlpha by animateFloatAsState(
        targetValue = if (stickyDateHeader && stickyDateHeaderText.isNotEmpty()) 1f else 0f,
        animationSpec = tween(durationMillis = if (stickyDateHeader) 500 else 1000),
        label = ""
    )

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            reverseLayout = true,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 20.dp),
            modifier = Modifier
                .padding(start = 12.dp, end = 12.dp)
                .fillMaxSize()
        ) {
            items(
                items = state.chatItems,
                key = { it.stableKey() }
            ) { chatItem ->
                when (chatItem) {
                    is ChatViewModel.ChatItem.MessageItem -> {
                        ChatMessageView(
                            message = chatItem.uiMessage,
                            highlightTriggerKey = quoteHighlightEvent
                                ?.takeIf { it.messageId == chatItem.uiMessage.id }
                                ?.nonce,
                            context = ChatMessageContext(
                                isOneToOneConversation = state.isOneToOneConversation,
                                conversationThreadId = state.conversationThreadId,
                                hasChatPermission = state.hasChatPermission
                            ),
                            callbacks = ChatMessageCallbacks(
                                onLongClick = callbacks.onLongClick,
                                onSwipeReply = callbacks.onSwipeReply,
                                onFileClick = callbacks.onFileClick,
                                onPollClick = callbacks.onPollClick,
                                onVoicePlayPauseClick = callbacks.onVoicePlayPauseClick,
                                onVoiceSeek = callbacks.onVoiceSeek,
                                onVoiceSpeedClick = callbacks.onVoiceSpeedClick,
                                onReactionClick = callbacks.onReactionClick,
                                onReactionLongClick = callbacks.onReactionLongClick,
                                onOpenThreadClick = callbacks.onOpenThreadClick,
                                onQuotedMessageClick = handleQuotedMessageClick,
                                onCancelUpload = callbacks.onCancelUpload
                            )
                        )
                    }

                    is ChatViewModel.ChatItem.DateHeaderItem -> {
                        DateHeader(chatItem.date)
                    }

                    is ChatViewModel.ChatItem.UnreadMessagesMarkerItem -> {
                        UnreadMessagesMarker()
                    }
                }
            }
        }

        // Sticky date header
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
                .alpha(stickyDateHeaderAlpha),
            shape = RoundedCornerShape(16.dp),
            color = colorScheme.secondaryContainer,
            tonalElevation = 2.dp
        ) {
            Text(
                stickyDateHeaderText,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                color = colorScheme.onSecondaryContainer
            )
        }

        // Unread messages popup
        if (showUnreadPopup.value) {
            UnreadMessagesPopup(
                unreadCount = unreadCount,
                onClick = {
                    coroutineScope.launch { listState.scrollToItem(0) }
                    unreadCount = 0
                    showUnreadPopup.value = false
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp)
            )
        }

        // Floating scroll-to-newest button
        AnimatedVisibility(
            visible = showScrollToNewest,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 24.dp),
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            Surface(
                onClick = {
                    coroutineScope.launch { listState.scrollToItem(0) }
                    unreadCount = 0
                },
                shape = CircleShape,
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                tonalElevation = 2.dp
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Scroll to newest",
                    modifier = Modifier
                        .size(44.dp)
                        .padding(8.dp),
                    tint = colorScheme.surfaceContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun UnreadMessagesPopup(unreadCount: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val viewThemeUtils = LocalViewThemeUtils.current
    val colorScheme = viewThemeUtils.getColorScheme(LocalContext.current)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = colorScheme.secondaryContainer,
        tonalElevation = 3.dp,
        modifier = modifier
    ) {
        Text(
            text = pluralStringResource(R.plurals.nc_new_messages_count, unreadCount, unreadCount),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            color = colorScheme.onSecondaryContainer
        )
    }
}

@Composable
fun DateHeader(date: LocalDate) {
    val viewThemeUtils = LocalViewThemeUtils.current
    val colorScheme = viewThemeUtils.getColorScheme(LocalContext.current)

    val text = when (date) {
        LocalDate.now() -> stringResource(R.string.nc_date_header_today)
        LocalDate.now().minusDays(1) -> stringResource(R.string.nc_date_header_yesterday)
        else -> date.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            modifier = Modifier
                .background(
                    colorScheme.secondaryContainer,
                    RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
            fontSize = 12.sp,
            color = colorScheme.onSecondaryContainer
        )
    }
}

@Composable
fun UnreadMessagesMarker() {
    val viewThemeUtils = LocalViewThemeUtils.current
    val colorScheme = viewThemeUtils.getColorScheme(LocalContext.current)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = colorScheme.outlineVariant,
            thickness = 1.dp
        )

        Text(
            text = stringResource(R.string.nc_new_messages),
            modifier = Modifier.padding(horizontal = 12.dp),
            fontSize = 12.sp,
            color = colorScheme.onSurfaceVariant
        )

        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = colorScheme.outlineVariant,
            thickness = 1.dp
        )
    }
}

fun formatTime(timestampMillis: Long): String {
    val instant = Instant.ofEpochMilli(timestampMillis)
    val dateTime = instant.atZone(ZoneId.systemDefault()).toLocalDate()
    return formatTime(dateTime)
}

fun formatTime(localDate: LocalDate): String {
    val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")
    val text = when (localDate) {
        LocalDate.now() -> "Today"
        LocalDate.now().minusDays(1) -> "Yesterday"
        else -> localDate.format(formatter)
    }
    return text
}

private suspend fun LazyListState.centerItemInViewportIfVisible(index: Int) {
    val targetItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
    val itemCenter = targetItem.offset + (targetItem.size / 2f)
    val distanceToCenter = itemCenter - viewportCenter

    if (distanceToCenter != 0f) {
        animateScrollBy(distanceToCenter)
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatViewPreview() {
    val context = LocalContext.current
    val previewUtils = remember { ComposePreviewUtils.getInstance(context) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = 12)

    CompositionLocalProvider(
        LocalViewThemeUtils provides previewUtils.viewThemeUtils
    ) {
        ChatView(
            state = ChatViewState(
                chatItems = createSampleChatItems(PREVIEW_SAMPLE_CHAT_COUNT),
                isOneToOneConversation = false,
                initialUnreadCount = 3,
                initialShowUnreadPopup = true
            ),
            listState = listState
        )
    }
}

private fun createSampleChatItems(count: Int): List<ChatViewModel.ChatItem> {
    val now = LocalDate.now()
    return buildList {
        for (i in count downTo 1) {
            val date = if (i > count / 2) now else now.minusDays(1)

            // Add date header when date changes (since list is reversed, we check if next i is different day)
            if (i == count || (i <= count / 2 && (i + 1) > count / 2)) {
                add(ChatViewModel.ChatItem.DateHeaderItem(date))
            }

            // Add unread marker once at a visible position in the preview (initial index 12)
            if (i == count - PREVIEW_UNREAD_MARKER_OFFSET) {
                add(ChatViewModel.ChatItem.UnreadMessagesMarkerItem(date))
            }

            val isIncoming = i % 2 == 0
            val text = "Sample message #$i"
            add(
                ChatViewModel.ChatItem.MessageItem(
                    ChatMessageUi(
                        id = i,
                        message = text,
                        plainMessage = text,
                        renderMarkdown = false,
                        actorDisplayName = if (isIncoming) "Alice" else "Me",
                        isThread = false,
                        threadTitle = "",
                        threadReplies = 0,
                        incoming = isIncoming,
                        isDeleted = false,
                        avatarUrl = null,
                        statusIcon = if (isIncoming) MessageStatusIcon.SENT else MessageStatusIcon.READ,
                        timestamp = System.currentTimeMillis() / 1000 - (count - i) * 60,
                        date = date,
                        content = MessageTypeContent.RegularText
                    )
                )
            )
        }
    }
}
