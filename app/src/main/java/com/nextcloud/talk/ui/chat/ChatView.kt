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
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextcloud.talk.R
import com.nextcloud.talk.chat.ui.model.ChatMessageUi
import com.nextcloud.talk.chat.ui.model.MessageStatusIcon
import com.nextcloud.talk.chat.ui.model.MessageTypeContent
import com.nextcloud.talk.chat.viewmodels.ChatViewModel
import com.nextcloud.talk.ui.theme.LocalViewThemeUtils
import com.nextcloud.talk.utils.preview.ComposePreviewUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val LONG_1000 = 1000L
private const val LOAD_MORE_BUFFER_ITEMS = 5
private const val STICKY_HEADER_HIDE_DELAY_MILLIS = 1200L
private const val UNREAD_MARKER_LAYOUT_TIMEOUT_MS = 500L
private const val PREVIEW_SAMPLE_CHAT_COUNT = 50
private const val PREVIEW_UNREAD_MARKER_OFFSET = 15

private data class QuoteHighlightEvent(val messageId: Int, val nonce: Long)

data class ChatViewState(
    val chatItems: List<ChatViewModel.ChatItem>,
    val isOneToOneConversation: Boolean,
    val conversationThreadId: Long? = null,
    val hasChatPermission: Boolean = true,
    val initialUnreadCount: Int = 0,
    val initialShowUnreadPopup: Boolean = false,
    val chatMode: ChatViewModel.ChatMode = ChatViewModel.ChatMode.DEFAULT_MODE,
    val highlightedMessageId: Int? = null,
    val highlightedSearchTerm: String? = null,
    val downloadingFileState: List<String> = listOf(),
    val stickyHeaderTopOffset: Dp = 0.dp
)

data class ChatViewCallbacks(
    val onLoadMore: ((Int, ChatViewModel.LoadMoreDirection) -> Unit?)? = null,
    val advanceLocalLastReadMessageIfNeeded: ((Int) -> Unit?)? = null,
    val updateRemoteLastReadMessageIfNeeded: (() -> Unit?)? = null,
    val onJumpToBottom: (() -> Unit)? = null,
    val onLoadQuotedMessageClick: (Int) -> Unit = {},
    val messageCallbacks: ChatMessageCallbacks = ChatMessageCallbacks()
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
    val isDefaultMode = state.chatMode == ChatViewModel.ChatMode.DEFAULT_MODE

    val isAtNewest by remember(listState, isDefaultMode) {
        derivedStateOf {
            isDefaultMode &&
                listState.firstVisibleItemIndex == 0 &&
                listState.firstVisibleItemScrollOffset == 0
        }
    }

    val isNearNewest by remember(listState, isDefaultMode) {
        derivedStateOf {
            isDefaultMode &&
                listState.firstVisibleItemIndex <= 2
        }
    }

    val showScrollToNewest by remember(isNearNewest, isDefaultMode) {
        derivedStateOf { !isNearNewest || !isDefaultMode }
    }

    val latestChatItems by rememberUpdatedState(state.chatItems)
    val latestOnLoadQuotedMessageClick by rememberUpdatedState(callbacks.onLoadQuotedMessageClick)
    var quoteHighlightEvent by remember { mutableStateOf<QuoteHighlightEvent?>(null) }
    var isNewerBoundaryLoadArmed by remember(state.chatMode, state.highlightedMessageId) {
        mutableStateOf(state.chatMode == ChatViewModel.ChatMode.DEFAULT_MODE)
    }
    var didScrollToUnreadMarker by remember { mutableStateOf(false) }
    var hadUnreadMarker by remember { mutableStateOf(false) }

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

    // Scroll once to unread marker when it becomes available (it can appear after initial load).
    LaunchedEffect(state.chatItems, isDefaultMode) {
        if (!isDefaultMode) return@LaunchedEffect

        val markerIndex = state.chatItems.indexOfFirst {
            it is ChatViewModel.ChatItem.UnreadMessagesMarkerItem
        }

        if (markerIndex < 0) {
            didScrollToUnreadMarker = false
            return@LaunchedEffect
        }

        if (didScrollToUnreadMarker) return@LaunchedEffect

        // While marker exists, keep popup hidden and reset unread popup count once.
        showUnreadPopup.value = false
        unreadCount = 0
        listState.scrollToItem(markerIndex)
        // Wait for the layout pass to complete so visibleItemsInfo reflects the new scroll position.
        withTimeoutOrNull(UNREAD_MARKER_LAYOUT_TIMEOUT_MS) {
            snapshotFlow {
                listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == markerIndex }
            }.filterNotNull().first()
        }
        listState.centerItemInViewportIfVisible(markerIndex)
        didScrollToUnreadMarker = true
    }

    // Track newest message and show unread popup.
    LaunchedEffect(state.chatItems) {
        if (state.chatItems.isEmpty()) return@LaunchedEffect
        if (!isDefaultMode) return@LaunchedEffect

        val hasUnreadMarker = state.chatItems.any { it is ChatViewModel.ChatItem.UnreadMessagesMarkerItem }
        if (hasUnreadMarker && !hadUnreadMarker) {
            showUnreadPopup.value = false
            unreadCount = 0
        }
        hadUnreadMarker = hasUnreadMarker

        val newestId = state.chatItems.firstNotNullOfOrNull { it.messageOrNull()?.id }
        val previousNewestId = lastNewestIdRef.value

        if (previousNewestId == null) {
            lastNewestIdRef.value = newestId
            return@LaunchedEffect
        }

        val isNearBottom = listState.firstVisibleItemIndex <= 2
        val hasNewMessage = newestId != previousNewestId &&
            state.chatMode == ChatViewModel.ChatMode.DEFAULT_MODE

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

    // Load older/newer messages when approaching the currently loaded block boundaries.
    LaunchedEffect(listState, state.chatItems.size, state.chatMode) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val firstVisible = layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0
            val total = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            Triple(firstVisible, lastVisible, total)
        }
            .distinctUntilChanged()
            .collect { (firstVisible, lastVisible, total) ->
                if (total == 0) return@collect

                val buffer = LOAD_MORE_BUFFER_ITEMS
                if (!isNewerBoundaryLoadArmed && firstVisible > buffer) {
                    isNewerBoundaryLoadArmed = true
                }

                val shouldLoadOlder = lastVisible >= (total - 1 - buffer)
                val shouldLoadNewer =
                    isNewerBoundaryLoadArmed &&
                        (
                            state.chatMode == ChatViewModel.ChatMode.SEARCH_MODE ||
                                state.chatMode == ChatViewModel.ChatMode.OLD_CHATBLOCK_MODE
                            ) &&
                        firstVisible <= buffer

                val oldestLoadedMessageId = latestChatItems
                    .asReversed()
                    .firstNotNullOfOrNull { (it as? ChatViewModel.ChatItem.MessageItem)?.uiMessage?.id }
                val newestLoadedMessageId = latestChatItems
                    .firstNotNullOfOrNull { (it as? ChatViewModel.ChatItem.MessageItem)?.uiMessage?.id }

                if (shouldLoadOlder && oldestLoadedMessageId != null) {
                    callbacks.onLoadMore?.invoke(oldestLoadedMessageId, ChatViewModel.LoadMoreDirection.OLDER)
                }

                if (shouldLoadNewer && newestLoadedMessageId != null) {
                    callbacks.onLoadMore?.invoke(newestLoadedMessageId, ChatViewModel.LoadMoreDirection.NEWER)
                }
            }
    }

    // Sticky date header
    val density = LocalDensity.current
    val overflowPx = with(density) { state.stickyHeaderTopOffset.roundToPx() }

    val stickyDateHeaderText by remember(listState, state.chatItems, overflowPx) {
        derivedStateOf {
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            val viewportEnd = listState.layoutInfo.viewportEndOffset
            // In reverseLayout=true, offsets increase from bottom (newest) to top (oldest).
            // An item's bottom edge is at screen Y = viewportEnd - offset; it is visible when that
            // is >= overflowPx, i.e. offset <= viewportEnd - overflowPx.
            val targetItem = if (overflowPx > 0) {
                visibleItems.filter { it.offset <= viewportEnd - overflowPx }.lastOrNull()
            } else {
                visibleItems.lastOrNull()
            }
            targetItem?.let { itemInfo ->
                state.chatItems.getOrNull(itemInfo.index)?.let { item ->
                    when (item) {
                        is ChatViewModel.ChatItem.MessageItem ->
                            formatTime(item.uiMessage.timestamp * LONG_1000)

                        is ChatViewModel.ChatItem.DateHeaderItem ->
                            formatTime(item.date)

                        is ChatViewModel.ChatItem.UnreadMessagesMarkerItem ->
                            formatTime(item.date)

                        else -> ""
                    }
                } ?: ""
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

    LaunchedEffect(isAtNewest, state.chatItems) {
        if (!isAtNewest) return@LaunchedEffect

        state.chatItems
            .firstNotNullOfOrNull { (it as? ChatViewModel.ChatItem.MessageItem)?.uiMessage?.id }
            ?.let { newestId ->
                callbacks.advanceLocalLastReadMessageIfNeeded?.invoke(newestId)
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
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(bottom = 20.dp),
            modifier = Modifier
                .fillMaxSize()
        ) {
            items(
                items = state.chatItems,
                key = { it.stableKey() }
            ) { chatItem ->
                when (chatItem) {
                    is ChatViewModel.ChatItem.MessageItem -> {
                        Box(
                            modifier = Modifier.padding(
                                top = if (!chatItem.uiMessage.isGrouped) 4.dp else 0.dp
                            )
                        ) {
                            ChatMessageView(
                                message = chatItem.uiMessage,
                                highlightTriggerKey = quoteHighlightEvent
                                    ?.takeIf { it.messageId == chatItem.uiMessage.id }
                                    ?.nonce,
                                isSelected = state.highlightedMessageId == chatItem.uiMessage.id,
                                highlightSearchTerm = state.highlightedSearchTerm,
                                context = ChatMessageContext(
                                    isOneToOneConversation = state.isOneToOneConversation,
                                    conversationThreadId = state.conversationThreadId,
                                    hasChatPermission = state.hasChatPermission,
                                    downloadingFileState = state.downloadingFileState
                                ),
                                callbacks = ChatMessageCallbacks(
                                    onLongClick = callbacks.messageCallbacks.onLongClick,
                                    onSwipeReply = callbacks.messageCallbacks.onSwipeReply,
                                    onFileClick = callbacks.messageCallbacks.onFileClick,
                                    onPollClick = callbacks.messageCallbacks.onPollClick,
                                    onVoicePlayPauseClick = callbacks.messageCallbacks.onVoicePlayPauseClick,
                                    onVoiceSeek = callbacks.messageCallbacks.onVoiceSeek,
                                    onVoiceSpeedClick = callbacks.messageCallbacks.onVoiceSpeedClick,
                                    onReactionClick = callbacks.messageCallbacks.onReactionClick,
                                    onReactionLongClick = callbacks.messageCallbacks.onReactionLongClick,
                                    onOpenThreadClick = callbacks.messageCallbacks.onOpenThreadClick,
                                    onQuotedMessageClick = handleQuotedMessageClick,
                                    onSystemMessageExpandClick = callbacks.messageCallbacks.onSystemMessageExpandClick,
                                    onAvatarClick = callbacks.messageCallbacks.onAvatarClick
                                )
                            )
                        }
                    }

                    is ChatViewModel.ChatItem.DateHeaderItem -> {
                        Box(modifier = Modifier.padding(top = 6.dp, start = 12.dp, end = 12.dp)) {
                            DateHeader(chatItem.date)
                        }
                    }

                    is ChatViewModel.ChatItem.UnreadMessagesMarkerItem -> {
                        Box(modifier = Modifier.padding(top = 6.dp, start = 12.dp, end = 12.dp)) {
                            UnreadMessagesMarker()
                        }
                    }

                    is ChatViewModel.ChatItem.LoadGapItem -> {
                        Box(modifier = Modifier.padding(top = 6.dp, start = 12.dp, end = 12.dp)) {
                            DateHeaderLabel(text = stringResource(R.string.chat_messages_load_gap))
                        }
                    }
                }
            }
        }

        // Sticky date header
        DateHeaderLabel(
            text = stickyDateHeaderText,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = state.stickyHeaderTopOffset + 2.dp)
                .alpha(stickyDateHeaderAlpha)
        )

        // Unread messages popup
        if (showUnreadPopup.value && isDefaultMode) {
            UnreadMessagesPopup(
                unreadCount = unreadCount,
                onClick = {
                    callbacks.onJumpToBottom?.invoke()
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
                    callbacks.onJumpToBottom?.invoke()
                    coroutineScope.launch { listState.scrollToItem(0) }
                    unreadCount = 0
                },
                shape = CircleShape,
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                tonalElevation = 2.dp
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.scroll_to_bottom),
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
fun DateHeaderLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
fun DateHeader(date: LocalDate) {
    val text = when (date) {
        LocalDate.now() -> stringResource(R.string.nc_date_header_today)
        LocalDate.now().minusDays(1) -> stringResource(R.string.nc_date_header_yesterday)
        else -> date.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
    }
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        DateHeaderLabel(text = text)
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
