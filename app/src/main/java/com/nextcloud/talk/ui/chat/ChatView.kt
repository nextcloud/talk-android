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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextcloud.talk.R
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.chat.viewmodels.ChatViewModel
import com.nextcloud.talk.data.user.model.User
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val LONG_1000 = 1000L
private val AUTHOR_TEXT_SIZE = 12.sp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GetNewChatView(
    chatItems: List<ChatViewModel.ChatItem>,
    showAvatar: Boolean,
    conversationThreadId: Long? = null,
    onLoadMore: (() -> Unit?)?,
    advanceLocalLastReadMessageIfNeeded: ((Int) -> Unit?)?,
    updateRemoteLastReadMessageIfNeeded: (() -> Unit?)?
) {
    val listState = rememberLazyListState()
    val showUnreadPopup = remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val lastNewestIdRef = remember {
        object {
            var value: String? = null
        }
    }

    // Track unread messages count.
    var unreadCount by remember { mutableIntStateOf(0) }

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
    val showScrollToNewest by remember { derivedStateOf { !isAtNewest } }

    val latestChatItems by rememberUpdatedState(chatItems)

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

                val buffer = 5
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
                        formatTime(item.message.timestamp * LONG_1000)

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
                        delay(1200)
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
                    advanceLocalLastReadMessageIfNeeded?.invoke(item.message.jsonMessageId)
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
            items(chatItems, key = { it.stableKey() }) { chatItem ->
                when (chatItem) {
                    is ChatViewModel.ChatItem.MessageItem -> {
                        val isBlinkingState = remember { mutableStateOf(false) }
                        ChatMessage(
                            message = chatItem.message,
                            showAvatar = showAvatar,
                            conversationThreadId = conversationThreadId,
                            isBlinkingState = isBlinkingState
                        )
                    }

                    is ChatViewModel.ChatItem.DateHeaderItem -> {
                        DateHeader(chatItem.date)
                    }

                    is ChatViewModel.ChatItem.UnreadMessagesMarkerItem -> {
                        UnreadMessagesMarker(chatItem.date)
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
            tonalElevation = 2.dp
        ) {
            Text(
                stickyDateHeaderText,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
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
                color = colorScheme.surface.copy(alpha = 0.9f),
                tonalElevation = 2.dp
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Scroll to newest",
                    modifier = Modifier
                        .size(36.dp)
                        .padding(8.dp),
                    tint = colorScheme.onSurface.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@Composable
fun UnreadMessagesPopup(unreadCount: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 3.dp,
        modifier = modifier
    ) {
        Text(
            text = "$unreadCount new message${if (unreadCount > 1) "s" else ""}",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
}

@Composable
fun DateHeader(date: LocalDate) {
    val text = when (date) {
        LocalDate.now() -> "Today"
        LocalDate.now().minusDays(1) -> "Yesterday"
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
                    Color.Gray.copy(alpha = 0.2f),
                    RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
            fontSize = 12.sp
        )
    }
}

@Composable
fun UnreadMessagesMarker(date: LocalDate) {
    Box(
        modifier = Modifier
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Unread messages",
            modifier = Modifier
                .background(
                    Color.Gray.copy(alpha = 0.2f),
                    RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
            fontSize = 12.sp
        )
    }
}

@Deprecated("do not use Compose Chat Adapter")
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

            ChatMessage(
                message = message,
                showAvatar = true,
                isBlinkingState = isBlinkingState
            )
        }
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
