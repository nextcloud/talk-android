/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.ui

import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nextcloud.talk.R
import com.nextcloud.talk.chat.MenuItemData

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatToolbar(state: ChatToolbarState, callbacks: ChatToolbarCallbacks, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = callbacks.onNavigateUp) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back_button))
                }
            },
            title = {
                if (state.isSearchMode) {
                    SearchField(state = state, callbacks = callbacks)
                } else {
                    ConversationHeader(state = state, onClick = callbacks.onTitleClick.takeIf { state.titleClickable })
                }
            },
            actions = { ToolbarActions(state = state, callbacks = callbacks) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = MaterialTheme.colorScheme.onSurface
            ),
            windowInsets = WindowInsets(0, 0, 0, 0)
        )

        if (state.isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surface
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ToolbarActions(state: ChatToolbarState, callbacks: ChatToolbarCallbacks) {
    if (state.isSearchMode) {
        IconButton(onClick = callbacks.onSearchPrevious) {
            Icon(
                painterResource(R.drawable.ic_keyboard_arrow_up),
                stringResource(R.string.message_search_previous_result)
            )
        }
        IconButton(onClick = callbacks.onSearchNext) {
            Icon(
                painterResource(R.drawable.ic_keyboard_arrow_down),
                stringResource(R.string.message_search_next_result)
            )
        }
        IconButton(onClick = callbacks.onSearchClose) {
            Icon(painterResource(R.drawable.ic_clear_24), stringResource(R.string.close_icon))
        }
    } else {
        if (state.threadNotificationIcon != null) {
            IconButton(onClick = callbacks.onThreadNotification) {
                Icon(painterResource(state.threadNotificationIcon), stringResource(R.string.thread_notifications))
            }
        }
        if (state.showEventMenu) {
            IconButton(onClick = callbacks.onEventMenu) {
                Icon(
                    painterResource(R.drawable.baseline_calendar_today_24),
                    stringResource(R.string.nc_event_conversation_menu)
                )
            }
        }
        if (state.showVoiceCall) {
            CallButton(
                iconRes = R.drawable.ic_call_white_24dp,
                contentDescription = stringResource(R.string.nc_conversation_menu_voice_call),
                onClick = callbacks.onVoiceCall,
                onLongClick = callbacks.onSilentVoiceCall.takeIf { state.supportsSilentCall }
            )
        }
        if (state.showVideoCall) {
            CallButton(
                iconRes = R.drawable.ic_videocam_white_24px,
                contentDescription = stringResource(R.string.nc_conversation_menu_video_call),
                onClick = callbacks.onVideoCall,
                onLongClick = callbacks.onSilentVideoCall.takeIf { state.supportsSilentCall }
            )
        }
        if (state.showSearch) {
            IconButton(onClick = callbacks.onSearchOpen) {
                Icon(painterResource(R.drawable.ic_search_white_24dp), stringResource(R.string.nc_search))
            }
        }
        if (state.overflowItems.isNotEmpty()) {
            OverflowMenuButton(items = state.overflowItems)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CallButton(iconRes: Int, contentDescription: String, onClick: () -> Unit, onLongClick: (() -> Unit)?) {
    var showMenu by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(48.dp)
            .semantics { role = Role.Button }
            .combinedClickable(
                onClick = onClick,
                onLongClick = if (onLongClick != null) ({ showMenu = true }) else null
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface
        )
        if (onLongClick != null) {
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.call_without_notification)) },
                    onClick = {
                        onLongClick()
                        showMenu = false
                    },
                    leadingIcon = {
                        Icon(painterResource(R.drawable.ic_baseline_notifications_off_24), contentDescription = null)
                    }
                )
            }
        }
    }
}

@Composable
private fun OverflowMenuButton(items: List<MenuItemData>) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_more_vert_24px),
                contentDescription = stringResource(R.string.nc_common_more)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.title) },
                    onClick = {
                        item.onClick()
                        expanded = false
                    },
                    leadingIcon = item.icon?.let { iconRes ->
                        { Icon(painterResource(iconRes), contentDescription = null) }
                    }
                )
            }
        }
    }
}

@Composable
private fun ConversationHeader(state: ChatToolbarState, onClick: (() -> Unit)?) {
    val rowModifier = if (onClick != null) {
        Modifier.combinedClickable(onClick = onClick)
    } else {
        Modifier
    }
    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (state.avatarUrl != null) {
            ConversationAvatar(
                avatarUrl = state.avatarUrl,
                credentials = state.credentials,
                userStatus = state.userStatus,
                modifier = Modifier.size(42.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Column {
            Text(
                text = state.title,
                fontSize = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (state.subtitle.isNotEmpty()) {
                Text(
                    text = state.subtitle,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun ConversationAvatar(
    avatarUrl: String,
    credentials: String?,
    userStatus: String?,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        val context = LocalContext.current
        val request = ImageRequest.Builder(context)
            .data(avatarUrl)
            .apply { credentials?.let { addHeader("Authorization", it) } }
            .crossfade(true)
            .build()

        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
        )

        if (userStatus != null) {
            UserStatusBadge(
                status = userStatus,
                modifier = Modifier
                    .size(18.dp)
                    .align(Alignment.BottomEnd)
            )
        }
    }
}

@Composable
private fun UserStatusBadge(status: String, modifier: Modifier = Modifier) {
    val iconRes = when (status) {
        "online" -> R.drawable.online_status
        "away" -> R.drawable.ic_user_status_away
        "busy" -> R.drawable.ic_user_status_busy
        "dnd" -> R.drawable.ic_user_status_dnd
        else -> null
    }
    if (iconRes != null) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = modifier.clip(CircleShape),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.surface, BlendMode.DstOver)
        )
    }
}

@Composable
private fun SearchField(state: ChatToolbarState, callbacks: ChatToolbarCallbacks) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    BasicTextField(
        value = state.searchQuery,
        onValueChange = callbacks.onSearchQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        singleLine = true,
        textStyle = TextStyle(
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { callbacks.onSearchSubmit() }),
        decorationBox = { innerTextField ->
            if (state.searchQuery.isEmpty()) {
                Text(
                    text = stringResource(R.string.message_search_hint),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    fontSize = 16.sp
                )
            }
            innerTextField()
        }
    )
}

private const val PREVIEW_WIDTH_DP = 360

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Normal mode · Light", widthDp = PREVIEW_WIDTH_DP)
@Preview(name = "Normal mode · Dark", widthDp = PREVIEW_WIDTH_DP, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NormalModePreview() {
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        Surface {
            ChatToolbar(
                state = ChatToolbarState(
                    title = "Alice",
                    subtitle = "🌵 On a trip",
                    avatarUrl = null,
                    userStatus = "online",
                    showVoiceCall = true,
                    showVideoCall = true,
                    showSearch = true,
                    overflowItems = listOf(
                        MenuItemData(title = "Conversation info", onClick = {}),
                        MenuItemData(title = "Shared items", onClick = {})
                    ),
                    titleClickable = true
                ),
                callbacks = ChatToolbarCallbacks()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Normal mode · RTL / Arabic", widthDp = PREVIEW_WIDTH_DP, locale = "ar")
@Composable
private fun NormalModeRtlPreview() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        Surface {
            ChatToolbar(
                state = ChatToolbarState(
                    title = "أليس",
                    subtitle = "متصل",
                    showVoiceCall = true,
                    showVideoCall = true
                ),
                callbacks = ChatToolbarCallbacks()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Search mode · Light", widthDp = PREVIEW_WIDTH_DP)
@Preview(name = "Search mode · Dark", widthDp = PREVIEW_WIDTH_DP, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SearchModePreview() {
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        Surface {
            ChatToolbar(
                state = ChatToolbarState(
                    title = "Alice",
                    isSearchMode = true,
                    searchQuery = "hello"
                ),
                callbacks = ChatToolbarCallbacks()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Search mode · RTL / Arabic", widthDp = PREVIEW_WIDTH_DP, locale = "ar")
@Composable
private fun SearchModeRtlPreview() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        Surface {
            ChatToolbar(
                state = ChatToolbarState(
                    title = "أليس",
                    isSearchMode = true,
                    searchQuery = ""
                ),
                callbacks = ChatToolbarCallbacks()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Loading · Light", widthDp = PREVIEW_WIDTH_DP)
@Preview(name = "Loading · Dark", widthDp = PREVIEW_WIDTH_DP, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LoadingPreview() {
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        Surface {
            ChatToolbar(
                state = ChatToolbarState(
                    title = "Team chat",
                    isSearchMode = true,
                    isLoading = true
                ),
                callbacks = ChatToolbarCallbacks()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Thread view · Light", widthDp = PREVIEW_WIDTH_DP)
@Preview(name = "Thread view · Dark", widthDp = PREVIEW_WIDTH_DP, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ThreadViewPreview() {
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        Surface {
            ChatToolbar(
                state = ChatToolbarState(
                    title = "Q3 planning discussion",
                    subtitle = "12 replies",
                    threadNotificationIcon = R.drawable.baseline_notifications_24
                ),
                callbacks = ChatToolbarCallbacks()
            )
        }
    }
}
