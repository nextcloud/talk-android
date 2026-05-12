/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.conversationlist.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.transform.CircleCropTransformation
import com.nextcloud.talk.R
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.domain.SearchMessageEntry
import com.nextcloud.talk.models.json.participants.Participant
import com.nextcloud.talk.utils.ApiUtils
import kotlin.math.abs
import kotlin.math.roundToInt

private const val MSG_KEY_EXCERPT_LENGTH = 20

/**
 * The full conversation list: pull-to-refresh + LazyColumn.
 * Replaces RecyclerView + FlexibleAdapter + SwipeRefreshLayout.
 */
@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationList(
    entries: List<ConversationListEntry>,
    isRefreshing: Boolean,
    currentUser: User,
    credentials: String,
    onConversationClick: (ConversationModel) -> Unit,
    onConversationLongClick: (ConversationModel) -> Unit,
    onMessageResultClick: (SearchMessageEntry) -> Unit,
    onContactClick: (Participant) -> Unit,
    onLoadMoreClick: () -> Unit,
    onRefresh: () -> Unit,
    searchQuery: String = "",
    /** Called whenever scroll direction changes; true = scrolled down, false = scrolled up. */
    onScrollChanged: (scrolledDown: Boolean) -> Unit = {},
    /** Called when the list stops scrolling; delivers the last-visible item index. */
    onScrollStopped: (lastVisibleIndex: Int) -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    /** Extra bottom padding added as LazyColumn contentPadding so the last item is reachable above the nav bar. */
    contentBottomPadding: Dp = 0.dp,
    onSwipeConversation: (ConversationOpsAction, ConversationModel) -> Unit = { _, _ -> }
) {
    var prevIndex by remember { mutableIntStateOf(listState.firstVisibleItemIndex) }
    var prevOffset by remember { mutableIntStateOf(listState.firstVisibleItemScrollOffset) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                if (index != prevIndex || offset != prevOffset) {
                    val scrolledDown = index > prevIndex || (index == prevIndex && offset > prevOffset)
                    onScrollChanged(scrolledDown)
                    prevIndex = index
                    prevOffset = offset
                }
            }
    }

    // Unread-bubble: notify Activity when scrolling stops
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { isScrolling ->
                if (!isScrolling) {
                    val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    onScrollStopped(lastVisible)
                }
            }
    }

    // Unread-bubble: also trigger the check after entries are first loaded (or updated)
    LaunchedEffect(entries) {
        if (entries.isEmpty()) {
            onScrollStopped(0)
            return@LaunchedEffect
        }
        // Wait until the LazyColumn has measured visible items so the last-visible index is accurate.
        snapshotFlow { listState.layoutInfo.visibleItemsInfo }
            .first { it.isNotEmpty() }
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        onScrollStopped(lastVisible)
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = contentBottomPadding)
        ) {
            itemsIndexed(
                items = entries,
                key = { index, entry ->
                    when (entry) {
                        is ConversationListEntry.Header ->
                            "header_${entry.title}"
                        is ConversationListEntry.ConversationEntry ->
                            "conv_${entry.model.token}"
                        is ConversationListEntry.MessageResultEntry ->
                            "msg_${entry.result.conversationToken}_" +
                                "${entry.result.messageId ?: entry.result.messageExcerpt.take(MSG_KEY_EXCERPT_LENGTH)}"
                        is ConversationListEntry.ContactEntry ->
                            // Contacts can legitimately appear multiple times in search results.
                            "contact_${entry.participant.actorId}_${entry.participant.actorType}_$index"
                        ConversationListEntry.LoadMore ->
                            "load_more"
                    }
                }
            ) { _, entry ->
                when (entry) {
                    is ConversationListEntry.Header ->
                        ConversationSectionHeader(title = entry.title)

                    is ConversationListEntry.ConversationEntry ->
                        SwipeableConversationItem(
                            model = entry.model,
                            onSwipe = { action -> onSwipeConversation(action, entry.model) }
                        ) {
                            ConversationListItem(
                                model = entry.model,
                                currentUser = currentUser,
                                callbacks = ConversationListItemCallbacks(
                                    onClick = { onConversationClick(entry.model) },
                                    onLongClick = { onConversationLongClick(entry.model) }
                                ),
                                searchQuery = searchQuery
                            )
                        }

                    is ConversationListEntry.MessageResultEntry ->
                        MessageResultListItem(
                            result = entry.result,
                            credentials = credentials,
                            onClick = { onMessageResultClick(entry.result) }
                        )

                    is ConversationListEntry.ContactEntry ->
                        ContactResultListItem(
                            participant = entry.participant,
                            currentUser = currentUser,
                            credentials = credentials,
                            searchQuery = searchQuery,
                            onClick = { onContactClick(entry.participant) }
                        )

                    ConversationListEntry.LoadMore ->
                        LoadMoreListItem(onClick = onLoadMoreClick)
                }
            }
        }
    }
}

/** Possible swipe states for conversation row actions. */
private enum class SwipeValue { Settled, StartToEnd, EndToStart }

private const val POP_SCALE_PEAK = 1.35f

@Suppress("LongMethod")
@Composable
private fun SwipeableConversationItem(
    model: ConversationModel,
    onSwipe: (ConversationOpsAction) -> Unit,
    content: @Composable () -> Unit
) {
    val currentModel by rememberUpdatedState(model)
    val currentOnSwipe by rememberUpdatedState(onSwipe)
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val offsetX = remember { Animatable(0f) }
    val popScale = remember { Animatable(1f) }
    val popAnimationSpec = remember {
        spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
    }
    var itemWidth by remember { mutableIntStateOf(0) }
    var hapticFiredRight by remember { mutableStateOf(false) }
    var hapticFiredLeft by remember { mutableStateOf(false) }

    val swipeDirection by remember {
        derivedStateOf {
            when {
                offsetX.value > 1f -> SwipeValue.StartToEnd
                offsetX.value < -1f -> SwipeValue.EndToStart
                else -> SwipeValue.Settled
            }
        }
    }
    val startToEndProgress by remember {
        derivedStateOf {
            val threshold = itemWidth * 0.20f
            if (threshold > 0f) (offsetX.value / threshold).coerceIn(0f, 1f) else 0f
        }
    }
    val endToStartProgress by remember {
        derivedStateOf {
            val threshold = itemWidth * 0.40f
            if (threshold > 0f) (-offsetX.value / threshold).coerceIn(0f, 1f) else 0f
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { size -> itemWidth = size.width }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val startToEndThreshold = itemWidth * 0.20f
                    val startToEndLimit = itemWidth * 0.3f
                    val endToStartThreshold = -itemWidth * 0.40f
                    val endToStartLimit = -itemWidth.toFloat()

                    val down = awaitFirstDown(requireUnconsumed = false)
                    var horizontalStarted = false
                    val dragStart = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ ->
                        val dx = abs(change.position.x - change.previousPosition.x)
                        val dy = abs(change.position.y - change.previousPosition.y)
                        if (dx > dy) {
                            change.consume()
                            horizontalStarted = true
                        }
                    }
                    if (dragStart != null && horizontalStarted) {
                        hapticFiredRight = false
                        hapticFiredLeft = false
                        coroutineScope.launch { popScale.snapTo(1f) }
                        horizontalDrag(dragStart.id) { change ->
                            val delta = change.position.x - change.previousPosition.x
                            val newOffset = (offsetX.value + delta).coerceIn(endToStartLimit, startToEndLimit)
                            coroutineScope.launch { offsetX.snapTo(newOffset) }
                            if (!hapticFiredRight && startToEndThreshold > 0 && newOffset >= startToEndThreshold) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                hapticFiredRight = true
                                coroutineScope.launch {
                                    popScale.snapTo(POP_SCALE_PEAK)
                                    popScale.animateTo(1f, popAnimationSpec)
                                }
                            }
                            if (!hapticFiredLeft && endToStartThreshold < 0 && newOffset <= endToStartThreshold) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                hapticFiredLeft = true
                                coroutineScope.launch {
                                    popScale.snapTo(POP_SCALE_PEAK)
                                    popScale.animateTo(1f, popAnimationSpec)
                                }
                            }
                            change.consume()
                        }
                        val finalOffset = offsetX.value
                        if (hapticFiredRight && finalOffset >= startToEndThreshold) {
                            val action = if (currentModel.unreadMessages > 0) {
                                ConversationOpsAction.MarkAsRead
                            } else {
                                ConversationOpsAction.MarkAsUnread
                            }
                            currentOnSwipe(action)
                        } else if (hapticFiredLeft && finalOffset <= endToStartThreshold) {
                            currentOnSwipe(ConversationOpsAction.Leave)
                        }
                        coroutineScope.launch { offsetX.animateTo(0f, spring()) }
                    }
                }
            }
    ) {
        SwipeBackground(
            modifier = Modifier.matchParentSize(),
            swipeDirection = swipeDirection,
            model = currentModel,
            progress = if (swipeDirection == SwipeValue.StartToEnd) startToEndProgress else endToStartProgress,
            popScale = popScale.value
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .background(MaterialTheme.colorScheme.surface)
        ) {
            content()
        }
    }
}

@Composable
private fun SwipeBackground(
    modifier: Modifier = Modifier,
    swipeDirection: SwipeValue,
    model: ConversationModel,
    progress: Float,
    popScale: Float
) {
    when (swipeDirection) {
        SwipeValue.StartToEnd -> {
            val iconColor = MaterialTheme.colorScheme.onPrimaryContainer
            Box(
                modifier = modifier
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (model.unreadMessages > 0) {
                    ReadIcon(
                        progress = progress,
                        popScale = popScale,
                        color = iconColor,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    UnreadIcon(
                        progress = progress,
                        popScale = popScale,
                        color = iconColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
        SwipeValue.EndToStart -> {
            Box(
                modifier = modifier
                    .background(MaterialTheme.colorScheme.error)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                LeaveIcon(
                    progress = progress,
                    popScale = popScale,
                    color = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        SwipeValue.Settled -> {}
    }
}

@Composable
private fun ConversationSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun MessageResultListItem(result: SearchMessageEntry, credentials: String, onClick: () -> Unit) {
    val primaryColor = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(result.thumbnailURL)
                .addHeader("Authorization", credentials)
                .crossfade(true)
                .transformations(CircleCropTransformation())
                .build(),
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape),
            placeholder = painterResource(R.drawable.ic_user),
            error = painterResource(R.drawable.ic_user)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = buildHighlightedText(result.title, result.searchTerm, primaryColor),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = colorResource(R.color.conversation_item_header)
            )
            Text(
                text = buildHighlightedText(result.messageExcerpt, result.searchTerm, primaryColor),
                style = MaterialTheme.typography.bodyMedium,
                color = colorResource(R.color.textColorMaxContrast),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

internal fun buildHighlightedText(text: String, searchTerm: String, highlightColor: Color): AnnotatedString =
    buildAnnotatedString {
        if (searchTerm.isBlank()) {
            append(text)
            return@buildAnnotatedString
        }
        val lowerText = text.lowercase()
        val lowerTerm = searchTerm.lowercase()
        var lastIndex = 0
        var matchIndex = lowerText.indexOf(lowerTerm, lastIndex)
        while (matchIndex != -1) {
            append(text.substring(lastIndex, matchIndex))
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = highlightColor)) {
                append(text.substring(matchIndex, matchIndex + searchTerm.length))
            }
            lastIndex = matchIndex + searchTerm.length
            matchIndex = lowerText.indexOf(lowerTerm, lastIndex)
        }
        append(text.substring(lastIndex))
    }

@Composable
private fun ContactResultListItem(
    participant: Participant,
    currentUser: User,
    credentials: String,
    searchQuery: String,
    onClick: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val avatarUrl = remember(currentUser.baseUrl, participant.actorId) {
        ApiUtils.getUrlForAvatar(currentUser.baseUrl, participant.actorId, false)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(avatarUrl)
                .addHeader("Authorization", credentials)
                .crossfade(true)
                .transformations(CircleCropTransformation())
                .build(),
            contentDescription = participant.displayName,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape),
            placeholder = painterResource(R.drawable.ic_user),
            error = painterResource(R.drawable.ic_user)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = buildHighlightedText(participant.displayName ?: "", searchQuery, primaryColor),
            style = MaterialTheme.typography.bodyLarge,
            color = colorResource(R.color.conversation_item_header),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun LoadMoreListItem(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.load_more_results),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
