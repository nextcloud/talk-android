/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:Suppress("TooManyFunctions", "MatchingDeclarationName")

package com.nextcloud.talk.conversationlist.ui

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.transform.CircleCropTransformation
import com.nextcloud.talk.R

sealed class TopBarMode {
    data object SearchBarIdle : TopBarMode()

    data class SearchActive(val query: String) : TopBarMode()

    data class TitleBar(val title: String, val showAccountChooser: Boolean = false) : TopBarMode()
}

data class ConversationListTopBarState(
    val mode: TopBarMode,
    val showAvatarBadge: Boolean,
    val avatarUrl: String?,
    val credentials: String,
    val showFilterActive: Boolean,
    val showThreadsButton: Boolean
)

@Suppress("LongParameterList")
data class ConversationListTopBarActions(
    val onSearchQueryChange: (String) -> Unit = {},
    val onSearchActivate: () -> Unit = {},
    val onSearchClose: () -> Unit = {},
    val onFilterClick: () -> Unit = {},
    val onThreadsClick: () -> Unit = {},
    val onAvatarClick: () -> Unit = {},
    val onNavigateBack: () -> Unit = {},
    val onAccountChooserClick: () -> Unit = {}
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListTopBar(
    state: ConversationListTopBarState,
    actions: ConversationListTopBarActions,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }

    if (state.mode is TopBarMode.SearchActive) {
        BackHandler {
            actions.onSearchQueryChange("")
            actions.onSearchClose()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
    ) {
        when (val mode = state.mode) {
            is TopBarMode.SearchBarIdle -> TopBarIdleContent(
                state = state,
                actions = actions
            )
            is TopBarMode.SearchActive -> TopBarSearchActiveContent(
                query = mode.query,
                onQueryChange = actions.onSearchQueryChange,
                onSearchClose = actions.onSearchClose,
                focusRequester = focusRequester
            )
            is TopBarMode.TitleBar -> TopBarTitleContent(
                title = mode.title,
                showAccountChooser = mode.showAccountChooser,
                avatarUrl = state.avatarUrl,
                credentials = state.credentials,
                onNavigateBack = actions.onNavigateBack,
                onAccountChooserClick = actions.onAccountChooserClick
            )
        }
    }
}

@Composable
private fun TopBarIdleContent(state: ConversationListTopBarState, actions: ConversationListTopBarActions) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IdleSearchBarCard(
            state = state,
            actions = actions,
            modifier = Modifier
                .weight(1f)
                .height(50.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        AvatarButton(
            avatarUrl = state.avatarUrl,
            credentials = state.credentials,
            showBadge = state.showAvatarBadge,
            onClick = actions.onAvatarClick
        )
    }
}

@Composable
private fun IdleSearchBarCard(
    state: ConversationListTopBarState,
    actions: ConversationListTopBarActions,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(25.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Text(
                text = stringResource(R.string.appbar_search_in, stringResource(R.string.nc_app_product_name)),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp, end = if (state.showThreadsButton) 80.dp else 48.dp)
                    .clickable { actions.onSearchActivate() },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IdleSearchBarActions(
                state = state,
                actions = actions,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

@Composable
private fun IdleSearchBarActions(
    state: ConversationListTopBarState,
    actions: ConversationListTopBarActions,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = actions.onFilterClick,
            modifier = Modifier.size(48.dp)
        ) {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = if (state.showThreadsButton) Alignment.CenterEnd else Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_baseline_filter_list_24),
                    contentDescription = stringResource(R.string.nc_filter),
                    tint = if (state.showFilterActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
        if (state.showThreadsButton) {
            IconButton(
                onClick = actions.onThreadsClick,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.outline_forum_24),
                    contentDescription = stringResource(R.string.threads),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBarSearchActiveContent(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearchClose: () -> Unit,
    focusRequester: FocusRequester
) {
    Column {
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = {
                    onQueryChange("")
                    onSearchClose()
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.nc_cancel),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            title = {
                SearchTextField(
                    query = query,
                    onQueryChange = onQueryChange,
                    focusRequester = focusRequester,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            actions = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = {
                        onQueryChange("")
                        focusRequester.requestFocus()
                    }) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.ic_close_search),
                            contentDescription = stringResource(R.string.nc_search_clear),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            windowInsets = WindowInsets(0)
        )
        HorizontalDivider()
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
private fun SearchTextField(
    query: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.focusRequester(focusRequester),
        singleLine = true,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface
        ),
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Search,
            keyboardType = KeyboardType.Text
        ),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(
                            R.string.appbar_search_in,
                            stringResource(R.string.nc_app_product_name)
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                innerTextField()
            }
        }
    )
}

@Suppress("LongParameterList")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBarTitleContent(
    title: String,
    showAccountChooser: Boolean,
    avatarUrl: String?,
    credentials: String,
    onNavigateBack: () -> Unit,
    onAccountChooserClick: () -> Unit
) {
    TopAppBar(
        title = { Text(text = title) },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.back_button)
                )
            }
        },
        actions = {
            if (showAccountChooser) {
                IconButton(onClick = onAccountChooserClick) {
                    AsyncImage(
                        model = buildAvatarImageRequest(avatarUrl, credentials),
                        contentDescription = stringResource(R.string.nc_settings),
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape),
                        placeholder = painterResource(R.drawable.ic_user),
                        error = painterResource(R.drawable.ic_user)
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        windowInsets = WindowInsets(0)
    )
}

@Composable
private fun AvatarButton(avatarUrl: String?, credentials: String, showBadge: Boolean, onClick: () -> Unit) {
    BadgedBox(
        badge = {
            if (showBadge) {
                Badge(
                    containerColor = colorResource(R.color.badge_color),
                    modifier = Modifier.offset(x = (-BADGE_OFFSET_DP).dp, y = BADGE_OFFSET_DP.dp)
                )
            }
        }
    ) {
        IconButton(onClick = onClick) {
            AsyncImage(
                model = buildAvatarImageRequest(avatarUrl, credentials),
                contentDescription = stringResource(R.string.nc_settings),
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape),
                placeholder = painterResource(R.drawable.ic_user),
                error = painterResource(R.drawable.ic_user)
            )
        }
    }
}

@Composable
private fun buildAvatarImageRequest(url: String?, credentials: String): ImageRequest {
    val context = LocalContext.current
    return ImageRequest.Builder(context)
        .data(url)
        .addHeader("Authorization", credentials)
        .crossfade(true)
        .transformations(CircleCropTransformation())
        .placeholder(R.drawable.ic_user)
        .error(R.drawable.ic_user)
        .build()
}

private const val BADGE_OFFSET_DP = 8

@Preview(name = "Idle - no filter, no threads - Light")
@Preview(name = "Idle - no filter, no threads - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Idle - no filter, no threads - RTL Arabic", locale = "ar")
@Composable
private fun PreviewTopBarIdle() {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            ConversationListTopBar(
                state = ConversationListTopBarState(
                    mode = TopBarMode.SearchBarIdle,
                    showAvatarBadge = false,
                    avatarUrl = null,
                    credentials = "",
                    showFilterActive = false,
                    showThreadsButton = false
                ),
                actions = ConversationListTopBarActions(
                    onSearchQueryChange = {},
                    onSearchActivate = {},
                    onSearchClose = {},
                    onFilterClick = {},
                    onThreadsClick = {},
                    onAvatarClick = {},
                    onNavigateBack = {},
                    onAccountChooserClick = {}
                )
            )
        }
    }
}

@Preview(name = "Idle - filter active - Light")
@Preview(name = "Idle - filter active - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Idle - filter active - RTL Arabic", locale = "ar")
@Composable
private fun PreviewTopBarIdleFilterActive() {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            ConversationListTopBar(
                state = ConversationListTopBarState(
                    mode = TopBarMode.SearchBarIdle,
                    showAvatarBadge = false,
                    avatarUrl = null,
                    credentials = "",
                    showFilterActive = true,
                    showThreadsButton = false
                ),
                actions = ConversationListTopBarActions(
                    onSearchQueryChange = {},
                    onSearchActivate = {},
                    onSearchClose = {},
                    onFilterClick = {},
                    onThreadsClick = {},
                    onAvatarClick = {},
                    onNavigateBack = {},
                    onAccountChooserClick = {}
                )
            )
        }
    }
}

@Preview(name = "Idle - with threads button - Light")
@Preview(name = "Idle - with threads button - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Idle - with threads button - RTL Arabic", locale = "ar")
@Composable
private fun PreviewTopBarIdleWithThreads() {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            ConversationListTopBar(
                state = ConversationListTopBarState(
                    mode = TopBarMode.SearchBarIdle,
                    showAvatarBadge = false,
                    avatarUrl = null,
                    credentials = "",
                    showFilterActive = false,
                    showThreadsButton = true
                ),
                actions = ConversationListTopBarActions(
                    onSearchQueryChange = {},
                    onSearchActivate = {},
                    onSearchClose = {},
                    onFilterClick = {},
                    onThreadsClick = {},
                    onAvatarClick = {},
                    onNavigateBack = {},
                    onAccountChooserClick = {}
                )
            )
        }
    }
}

@Preview(name = "Idle - avatar badge visible - Light")
@Preview(name = "Idle - avatar badge visible - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Idle - avatar badge visible - RTL Arabic", locale = "ar")
@Composable
private fun PreviewTopBarIdleWithBadge() {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            ConversationListTopBar(
                state = ConversationListTopBarState(
                    mode = TopBarMode.SearchBarIdle,
                    showAvatarBadge = true,
                    avatarUrl = null,
                    credentials = "",
                    showFilterActive = false,
                    showThreadsButton = false
                ),
                actions = ConversationListTopBarActions(
                    onSearchQueryChange = {},
                    onSearchActivate = {},
                    onSearchClose = {},
                    onFilterClick = {},
                    onThreadsClick = {},
                    onAvatarClick = {},
                    onNavigateBack = {},
                    onAccountChooserClick = {}
                )
            )
        }
    }
}

@Preview(name = "Search active - empty query - Light")
@Preview(name = "Search active - empty query - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Search active - empty query - RTL Arabic", locale = "ar")
@Composable
private fun PreviewTopBarSearchEmpty() {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            ConversationListTopBar(
                state = ConversationListTopBarState(
                    mode = TopBarMode.SearchActive(query = ""),
                    showAvatarBadge = false,
                    avatarUrl = null,
                    credentials = "",
                    showFilterActive = false,
                    showThreadsButton = false
                ),
                actions = ConversationListTopBarActions(
                    onSearchQueryChange = {},
                    onSearchActivate = {},
                    onSearchClose = {},
                    onFilterClick = {},
                    onThreadsClick = {},
                    onAvatarClick = {},
                    onNavigateBack = {},
                    onAccountChooserClick = {}
                )
            )
        }
    }
}

@Preview(name = "Search active - with text - Light")
@Preview(name = "Search active - with text - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Search active - with text - RTL Arabic", locale = "ar")
@Composable
private fun PreviewTopBarSearchWithText() {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            ConversationListTopBar(
                state = ConversationListTopBarState(
                    mode = TopBarMode.SearchActive(query = "Nextcloud"),
                    showAvatarBadge = false,
                    avatarUrl = null,
                    credentials = "",
                    showFilterActive = false,
                    showThreadsButton = false
                ),
                actions = ConversationListTopBarActions(
                    onSearchQueryChange = {},
                    onSearchActivate = {},
                    onSearchClose = {},
                    onFilterClick = {},
                    onThreadsClick = {},
                    onAvatarClick = {},
                    onNavigateBack = {},
                    onAccountChooserClick = {}
                )
            )
        }
    }
}

@Preview(name = "TitleBar - Share To - Light")
@Preview(name = "TitleBar - Share To - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "TitleBar - Share To - RTL Arabic", locale = "ar")
@Composable
private fun PreviewTopBarShareTo() {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            ConversationListTopBar(
                state = ConversationListTopBarState(
                    mode = TopBarMode.TitleBar(title = "Send to\u2026"),
                    showAvatarBadge = false,
                    avatarUrl = null,
                    credentials = "",
                    showFilterActive = false,
                    showThreadsButton = false
                ),
                actions = ConversationListTopBarActions(
                    onSearchQueryChange = {},
                    onSearchActivate = {},
                    onSearchClose = {},
                    onFilterClick = {},
                    onThreadsClick = {},
                    onAvatarClick = {},
                    onNavigateBack = {},
                    onAccountChooserClick = {}
                )
            )
        }
    }
}

@Preview(name = "TitleBar - Share To, multi-account - Light")
@Preview(name = "TitleBar - Share To, multi-account - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "TitleBar - Share To, multi-account - RTL Arabic", locale = "ar")
@Composable
private fun PreviewTopBarShareToMultiAccount() {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            ConversationListTopBar(
                state = ConversationListTopBarState(
                    mode = TopBarMode.TitleBar(title = "Send to\u2026", showAccountChooser = true),
                    showAvatarBadge = false,
                    avatarUrl = null,
                    credentials = "",
                    showFilterActive = false,
                    showThreadsButton = false
                ),
                actions = ConversationListTopBarActions(
                    onSearchQueryChange = {},
                    onSearchActivate = {},
                    onSearchClose = {},
                    onFilterClick = {},
                    onThreadsClick = {},
                    onAvatarClick = {},
                    onNavigateBack = {},
                    onAccountChooserClick = {}
                )
            )
        }
    }
}

@Preview(name = "TitleBar - Forward To - Light")
@Preview(name = "TitleBar - Forward To - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "TitleBar - Forward To - RTL Arabic", locale = "ar")
@Composable
private fun PreviewTopBarForward() {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            ConversationListTopBar(
                state = ConversationListTopBarState(
                    mode = TopBarMode.TitleBar(title = "Forward to\u2026"),
                    showAvatarBadge = false,
                    avatarUrl = null,
                    credentials = "",
                    showFilterActive = false,
                    showThreadsButton = false
                ),
                actions = ConversationListTopBarActions(
                    onSearchQueryChange = {},
                    onSearchActivate = {},
                    onSearchClose = {},
                    onFilterClick = {},
                    onThreadsClick = {},
                    onAvatarClick = {},
                    onNavigateBack = {},
                    onAccountChooserClick = {}
                )
            )
        }
    }
}
