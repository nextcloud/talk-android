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

@Suppress("Detekt.LongMethod", "Detekt.ComplexMethod", "LongParameterList")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatView(
    chatItems: List<ChatViewModel.ChatItem>,
    isOneToOneConversation: Boolean,
    conversationThreadId: Long? = null,
    onLoadMore: (() -> Unit?)? = null,
    advanceLocalLastReadMessageIfNeeded: ((Int) -> Unit?)? = null,
    updateRemoteLastReadMessageIfNeeded: (() -> Unit?)? = null,
    onLongClick: ((Int) -> Unit?)? = null,
    onFileClick: (Int) -> Unit = {},
    onPollClick: (pollId: String, pollName: String) -> Unit = { _, _ -> },
    onVoicePlayPauseClick: (Int) -> Unit = {},
    onVoiceSeek: (messageId: Int, progress: Int) -> Unit = { _, _ -> },
    onVoiceSpeedClick: (Int) -> Unit = {},
    onReactionClick: (messageId: Int, emoji: String) -> Unit = { _, _ -> },
    onReactionLongClick: (messageId: Int) -> Unit = {},
    onOpenThreadClick: (messageId: Int) -> Unit = {},
    onLoadQuotedMessageClick: (messageId: Int) -> Unit = {},
    onSwipeReply: ((Int) -> Unit)? = null,
    hasChatPermission: Boolean = true,
    listState: LazyListState = rememberLazyListState(),
    initialUnreadCount: Int = 0,
    initialShowUnreadPopup: Boolean = false
) {
    val viewThemeUtils = LocalViewThemeUtils.current
    val colorScheme = viewThemeUtils.getColorScheme(LocalContext.current)

    val showUnreadPopup = remember { mutableStateOf(initialShowUnreadPopup) }
    val coroutineScope = rememberCoroutineScope()

    val lastNewestIdRef = remember {
        object {
            var value: Int? = null
        }
    }

    // Track unread messages count.
    var unreadCount by remember { mutableIntStateOf(initialUnreadCount) }

    // Determine if user is at newest message
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

    // Show floating scroll-to-newest button when not at newest
    val showScrollToNewest by remember { derivedStateOf { !isNearNewest } }

    val latestChatItems by rememberUpdatedState(chatItems)
    val latestOnLoadQuotedMessageClick by rememberUpdatedState(onLoadQuotedMessageClick)
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
    LaunchedEffect(chatItems) {
        if (chatItems.isEmpty()) return@LaunchedEffect

        val newestId = chatItems.firstNotNullOfOrNull { it.messageOrNull()?.id }
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
    LaunchedEffect(listState, chatItems.size) {
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
                    onLoadMore?.invoke()
                }
            }
    }

    // Sticky date header
    val stickyDateHeaderText by remember(listState, chatItems) {
        derivedStateOf {
            chatItems.getOrNull(
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
        // Only listen to scroll if user is away from newest messages. This ensures the stickyHeader is not shown on
        // every new received message when being at the bottom of the list (because this triggers a scroll).
        if (!isNearNewest) {
            updateRemoteLastReadMessageIfNeeded?.invoke()
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
                // It might not always be a chat message. Not calling advanceLocalLastReadMessageIfNeeded should not
                // matter. This should be triggered often enough so it's okay when it's true the next times.
                if (item is ChatViewModel.ChatItem.MessageItem) {
                    advanceLocalLastReadMessageIfNeeded?.invoke(item.uiMessage.id)
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
                items = chatItems,
                // key = { "" + it.stableKey() + it.hashCode() }   // TODO remove hash
                key = { it.stableKey() }
            ) { chatItem ->
                when (chatItem) {
                    is ChatViewModel.ChatItem.MessageItem -> {
                        ChatMessageView(
                            message = chatItem.uiMessage,
                            highlightTriggerKey = quoteHighlightEvent
                                ?.takeIf { it.messageId == chatItem.uiMessage.id }
                                ?.nonce,
                            isOneToOneConversation = isOneToOneConversation,
                            conversationThreadId = conversationThreadId,
                            onFileClick = onFileClick,
                            onLongClick = onLongClick,
                            onSwipeReply = onSwipeReply,
                            hasChatPermission = hasChatPermission,
                            onPollClick = onPollClick,
                            onVoicePlayPauseClick = onVoicePlayPauseClick,
                            onVoiceSeek = onVoiceSeek,
                            onVoiceSpeedClick = onVoiceSpeedClick,
                            onReactionClick = onReactionClick,
                            onReactionLongClick = onReactionLongClick,
                            onOpenThreadClick = onOpenThreadClick,
                            onQuotedMessageClick = handleQuotedMessageClick
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
            text = "$unreadCount new message${if (unreadCount > 1) "s" else ""}",
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
            text = "Unread messages",
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
    // Scroll away from bottom (index 0) to show the floating button and unread marker
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = 12)

    CompositionLocalProvider(
        LocalViewThemeUtils provides previewUtils.viewThemeUtils
    ) {
        ChatView(
            chatItems = createSampleChatItems(PREVIEW_SAMPLE_CHAT_COUNT),
            isOneToOneConversation = false,
            listState = listState,
            initialUnreadCount = 3,
            initialShowUnreadPopup = true
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
