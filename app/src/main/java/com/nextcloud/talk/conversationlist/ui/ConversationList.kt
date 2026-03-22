/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.conversationlist.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
    listState: LazyListState = rememberLazyListState()
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

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
            items(
                items = entries,
                key = { entry ->
                    when (entry) {
                        is ConversationListEntry.Header ->
                            "header_${entry.title}"
                        is ConversationListEntry.ConversationEntry ->
                            "conv_${entry.model.token}"
                        is ConversationListEntry.MessageResultEntry ->
                            "msg_${entry.result.conversationToken}_" +
                                "${entry.result.messageId ?: entry.result.messageExcerpt.take(MSG_KEY_EXCERPT_LENGTH)}"
                        is ConversationListEntry.ContactEntry ->
                            "contact_${entry.participant.actorId}_${entry.participant.actorType}"
                        ConversationListEntry.LoadMore ->
                            "load_more"
                    }
                }
            ) { entry ->
                when (entry) {
                    is ConversationListEntry.Header ->
                        ConversationSectionHeader(title = entry.title)

                    is ConversationListEntry.ConversationEntry ->
                        ConversationListItem(
                            model = entry.model,
                            currentUser = currentUser,
                            callbacks = ConversationListItemCallbacks(
                                onClick = { onConversationClick(entry.model) },
                                onLongClick = { onConversationLongClick(entry.model) }
                            ),
                            searchQuery = searchQuery
                        )

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
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
