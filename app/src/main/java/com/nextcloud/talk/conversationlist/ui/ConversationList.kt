/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.conversationlist.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
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
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
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
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
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

private const val TAGS_REVEAL_PULL_RESISTANCE = 0.5f
private const val TAGS_REVEAL_SNAP_THRESHOLD_FRACTION = 0.35f
private const val TAGS_REVEAL_MIN_SCALE = 0.85f
private const val TAGS_REVEAL_FADE_IN_FRACTION = 0.7f

@Suppress("MagicNumber")
private val tagsRevealPopOrigin = TransformOrigin(pivotFractionX = 0.5f, pivotFractionY = 1f)

/** Reports a layout height of only [revealPx] instead of [content]'s natural height, bottom-clipping the rest. */
@Composable
private fun PullRevealHeader(
    revealPx: () -> Float,
    naturalHeightPx: () -> Float,
    onNaturalHeightPxChanged: (Float) -> Unit,
    content: @Composable () -> Unit
) {
    Layout(
        content = {
            Box(
                modifier = Modifier.graphicsLayer {
                    val natural = naturalHeightPx()
                    val fraction = if (natural > 0f) (revealPx() / natural).coerceIn(0f, 1f) else 0f
                    alpha = (fraction / TAGS_REVEAL_FADE_IN_FRACTION).coerceIn(0f, 1f)
                    val scale = TAGS_REVEAL_MIN_SCALE + (1f - TAGS_REVEAL_MIN_SCALE) * fraction
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = tagsRevealPopOrigin
                }
            ) {
                content()
            }
        },
        modifier = Modifier.clipToBounds()
    ) { measurables, constraints ->
        val placeable = measurables.first().measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
        onNaturalHeightPxChanged(placeable.height.toFloat())
        val height = revealPx().roundToInt().coerceIn(0, placeable.height)
        layout(placeable.width, height) {
            placeable.placeRelative(0, height - placeable.height)
        }
    }
}

/** Bundles the reveal-height accessors so [rememberPullRevealNestedScrollConnection] stays under the param limit. */
private class PullRevealController(
    val revealPx: () -> Float,
    val naturalHeightPx: () -> Float,
    val onRevealPxChanged: (Float) -> Unit,
    val refreshDistanceFraction: () -> Float
)

// This must be the outermost nested-scroll connection (wrapping PullToRefreshBox, not attached
// to the LazyColumn inside it): pre-scroll dispatch runs outermost-first, so it needs first claim
// on "pulling down while at the top" before PullToRefreshBox's own overscroll detection sees it.
// revealPx is plain, synchronously-mutated state rather than an Animatable driven via snapTo from
// a launched coroutine — that queues a new coroutine per scroll callback during a drag, and they
// compete for the Animatable's mutation mutex, causing stutter. A coroutine only runs once per
// gesture, for the release-time settle animation.

@Composable
private fun rememberPullRevealNestedScrollConnection(
    listState: LazyListState,
    isEnabled: Boolean,
    controller: PullRevealController,
    coroutineScope: CoroutineScope
): NestedScrollConnection =
    remember(listState, isEnabled) {
        object : NestedScrollConnection {
            /** Tracks the in-flight settle animation so a fresh drag/fling can cancel a stale one. */
            var settleJob: Job? = null

            // True once this gesture has already pulled the tags row fully open; further pulling
            // is then absorbed here instead of leaking into the refresh indicator below, which may
            // only be engaged by a separate, later pull. Reset on gesture end in onPreFling.
            var reachedMaxThisGesture = false
            val revealPx = controller.revealPx
            val naturalHeightPx = controller.naturalHeightPx
            val onRevealPxChanged = controller.onRevealPxChanged
            val refreshDistanceFraction = controller.refreshDistanceFraction

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!isEnabled) return Offset.Zero
                val atOwnTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                return when {
                    // Refresh indicator has no pull progress of its own yet, so retract the tags
                    // row first instead of letting it collapse underneath the indicator.
                    available.y < 0f && revealPx() > 0f && refreshDistanceFraction() <= 0f -> {
                        settleJob?.cancel()
                        val newValue = (revealPx() + available.y).coerceAtLeast(0f)
                        val consumedY = newValue - revealPx()
                        onRevealPxChanged(newValue)
                        Offset(0f, consumedY)
                    }

                    available.y > 0f && atOwnTop && revealPx() < naturalHeightPx() -> {
                        settleJob?.cancel()
                        val newValue = (revealPx() + available.y * TAGS_REVEAL_PULL_RESISTANCE)
                            .coerceAtMost(naturalHeightPx())
                        onRevealPxChanged(newValue)
                        if (newValue >= naturalHeightPx()) {
                            reachedMaxThisGesture = true
                        }
                        available
                    }

                    available.y > 0f && atOwnTop && reachedMaxThisGesture -> available

                    else -> Offset.Zero
                }
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                reachedMaxThisGesture = false
                // Fire-and-forget: awaiting the settle animation here would block the fling this
                // suspend function is expected to hand off to the LazyColumn below.
                val max = naturalHeightPx()
                if (isEnabled && revealPx() > 0f && revealPx() < max) {
                    settleJob?.cancel()
                    settleJob = coroutineScope.launch {
                        val target = if (revealPx() >= max * TAGS_REVEAL_SNAP_THRESHOLD_FRACTION) max else 0f
                        animate(
                            initialValue = revealPx(),
                            targetValue = target,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        ) { value, _ -> onRevealPxChanged(value) }
                    }
                }
                return Velocity.Zero
            }
        }
    }

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
    onSwipeConversation: (ConversationOpsAction, ConversationModel) -> Unit = { _, _ -> },
    isOnline: Boolean = true,
    /** Rubber-band pull-to-reveal content shown above the list; null hides the feature entirely. */
    tagsRowContent: (@Composable () -> Unit)? = null
) {
    var tagsRevealPx by remember { mutableFloatStateOf(0f) }
    var tagsNaturalHeightPx by remember { mutableFloatStateOf(0f) }
    val pullToRefreshState = rememberPullToRefreshState()
    val tagsRevealController = remember {
        PullRevealController(
            revealPx = { tagsRevealPx },
            naturalHeightPx = { tagsNaturalHeightPx },
            onRevealPxChanged = { tagsRevealPx = it },
            refreshDistanceFraction = { pullToRefreshState.distanceFraction }
        )
    }
    val tagsRevealCoroutineScope = rememberCoroutineScope()
    val tagsRevealConnection = rememberPullRevealNestedScrollConnection(
        listState = listState,
        isEnabled = tagsRowContent != null,
        controller = tagsRevealController,
        coroutineScope = tagsRevealCoroutineScope
    )

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

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { isScrolling ->
                if (!isScrolling) {
                    val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    onScrollStopped(lastVisible)
                }
            }
    }

    LaunchedEffect(entries) {
        if (entries.isEmpty()) {
            onScrollStopped(0)
            return@LaunchedEffect
        }
        snapshotFlow { listState.layoutInfo.visibleItemsInfo }
            .first { it.isNotEmpty() }
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        onScrollStopped(lastVisible)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(tagsRevealConnection)
    ) {
        if (tagsRowContent != null) {
            PullRevealHeader(
                revealPx = { tagsRevealPx },
                naturalHeightPx = { tagsNaturalHeightPx },
                onNaturalHeightPxChanged = { tagsNaturalHeightPx = it },
                content = tagsRowContent
            )
        }
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            state = pullToRefreshState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            indicator = {
                // Offset cancels the tags row's push-down, keeping this pinned near the search bar.
                PullToRefreshDefaults.Indicator(
                    state = pullToRefreshState,
                    isRefreshing = isRefreshing,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset { IntOffset(0, -tagsRevealPx.roundToInt()) }
                )
            }
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
                                    "${
                                        entry.result.messageId
                                            ?: entry.result.messageExcerpt.take(MSG_KEY_EXCERPT_LENGTH)
                                    }"

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
                                onSwipe = { action -> onSwipeConversation(action, entry.model) },
                                enabled = isOnline
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
}

private enum class SwipeValue { Settled, StartToEnd, EndToStart }

private const val POP_SCALE_PEAK = 1.35f

private const val DESTRUCTIVE_SWIPE_THRESHOLD = 0.40f

private const val NON_DESTRUCTIVE_SWIPE_THRESHOLD = 0.20f

private const val NON_DESTRUCTIVE_SWIPE_END_LIMIT = 0.3f

private val swipePopAnimationSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMedium
)

private class SwipeParams(
    val coroutineScope: CoroutineScope,
    val haptic: HapticFeedback,
    val offsetX: Animatable<Float, AnimationVector1D>,
    val popScale: Animatable<Float, AnimationVector1D>,
    val getModel: () -> ConversationModel,
    val onSwipe: (ConversationOpsAction) -> Unit
)

private data class SwipeThresholds(
    val startToEndThreshold: Float,
    val startToEndLimit: Float,
    val endToStartThreshold: Float,
    val endToStartLimit: Float
)

@Composable
private fun SwipeableConversationItem(
    model: ConversationModel,
    onSwipe: (ConversationOpsAction) -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val currentModel by rememberUpdatedState(model)
    val currentOnSwipe by rememberUpdatedState(onSwipe)
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val popScale = remember { Animatable(1f) }
    var itemWidth by remember { mutableIntStateOf(0) }

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
            val threshold = itemWidth * NON_DESTRUCTIVE_SWIPE_THRESHOLD
            if (threshold > 0f) (offsetX.value / threshold).coerceIn(0f, 1f) else 0f
        }
    }
    val endToStartProgress by remember {
        derivedStateOf {
            val threshold = itemWidth * DESTRUCTIVE_SWIPE_THRESHOLD
            if (threshold > 0f) (-offsetX.value / threshold).coerceIn(0f, 1f) else 0f
        }
    }

    val swipeParams = SwipeParams(coroutineScope, haptic, offsetX, popScale, { currentModel }, { currentOnSwipe(it) })
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { size -> itemWidth = size.width }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitSwipeGesture({ itemWidth }, swipeParams)
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

private suspend fun PointerInputScope.awaitSwipeGesture(getItemWidth: () -> Int, params: SwipeParams) {
    awaitEachGesture {
        val width = getItemWidth()
        val thresholds = SwipeThresholds(
            startToEndThreshold = width * NON_DESTRUCTIVE_SWIPE_THRESHOLD,
            startToEndLimit = width * NON_DESTRUCTIVE_SWIPE_END_LIMIT,
            endToStartThreshold = -width * DESTRUCTIVE_SWIPE_THRESHOLD,
            endToStartLimit = -width.toFloat()
        )
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
        if (dragStart == null || !horizontalStarted) return@awaitEachGesture
        handleConfirmedSwipeDrag(dragStart.id, params, thresholds)
    }
}

private suspend fun AwaitPointerEventScope.handleConfirmedSwipeDrag(
    dragId: PointerId,
    params: SwipeParams,
    thresholds: SwipeThresholds
) {
    var hapticFiredRight = false
    var hapticFiredLeft = false
    params.coroutineScope.launch { params.popScale.snapTo(1f) }
    horizontalDrag(dragId) { change ->
        val delta = change.position.x - change.previousPosition.x
        val newOffset = (params.offsetX.value + delta).coerceIn(thresholds.endToStartLimit, thresholds.startToEndLimit)
        params.coroutineScope.launch { params.offsetX.snapTo(newOffset) }
        if (!hapticFiredRight && thresholds.startToEndThreshold > 0 && newOffset >= thresholds.startToEndThreshold) {
            params.haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            hapticFiredRight = true
            params.coroutineScope.launch {
                params.popScale.snapTo(POP_SCALE_PEAK)
                params.popScale.animateTo(1f, swipePopAnimationSpec)
            }
        }
        if (!hapticFiredLeft && thresholds.endToStartThreshold < 0 && newOffset <= thresholds.endToStartThreshold) {
            params.haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            hapticFiredLeft = true
            params.coroutineScope.launch {
                params.popScale.snapTo(POP_SCALE_PEAK)
                params.popScale.animateTo(1f, swipePopAnimationSpec)
            }
        }
        change.consume()
    }
    val finalOffset = params.offsetX.value
    when {
        hapticFiredRight && finalOffset >= thresholds.startToEndThreshold ->
            params.onSwipe(resolveReadUnreadAction(params.getModel()))
        hapticFiredLeft && finalOffset <= thresholds.endToStartThreshold ->
            params.onSwipe(ConversationOpsAction.Leave)
    }
    params.coroutineScope.launch { params.offsetX.animateTo(0f, spring()) }
}

private fun resolveReadUnreadAction(model: ConversationModel): ConversationOpsAction =
    if (model.unreadMessages > 0) ConversationOpsAction.MarkAsRead else ConversationOpsAction.MarkAsUnread

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
