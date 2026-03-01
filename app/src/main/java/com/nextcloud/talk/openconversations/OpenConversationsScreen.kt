/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2026 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.openconversations

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.transform.CircleCropTransformation
import com.nextcloud.talk.R
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.conversations.ConversationEnums
import com.nextcloud.talk.openconversations.viewmodels.OpenConversationsViewModel
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.DisplayUtils

@Composable
fun OpenConversationsScreen(
    viewState: OpenConversationsViewModel.ViewState,
    searchTerm: String,
    userBaseUrl: String?,
    listenerInput: OpenConversationsScreenListenerInput
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage = stringResource(R.string.nc_common_error_sorry)

    LaunchedEffect(viewState) {
        if (viewState is OpenConversationsViewModel.FetchConversationsErrorState) {
            snackbarHostState.showSnackbar(errorMessage)
        }
    }

    OpenConversationsScreenContent(
        viewState = viewState,
        searchTerm = searchTerm,
        userBaseUrl = userBaseUrl,
        snackbarHostState = snackbarHostState,
        listenerInput = listenerInput
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OpenConversationsScreenContent(
    viewState: OpenConversationsViewModel.ViewState,
    searchTerm: String,
    userBaseUrl: String?,
    snackbarHostState: SnackbarHostState,
    listenerInput: OpenConversationsScreenListenerInput
) {
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) focusRequester.requestFocus()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            OpenConversationsTopBar(
                isSearchActive = isSearchActive,
                searchTerm = searchTerm,
                focusRequester = focusRequester,
                listenerInput = OpenConversationsTopBarListenerInput(
                    onSearchTermChange = listenerInput.onSearchTermChange,
                    onSearchActivate = { isSearchActive = true },
                    onSearchDismiss = {
                        isSearchActive = false
                        listenerInput.onSearchTermChange("")
                        keyboardController?.hide()
                    },
                    onBackClick = listenerInput.onBackClick
                )
            )
        }
    ) { paddingValues ->
        ConversationsBody(
            viewState = viewState,
            userBaseUrl = userBaseUrl,
            onConversationClick = listenerInput.onConversationClick,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OpenConversationsTopBar(
    isSearchActive: Boolean,
    searchTerm: String,
    focusRequester: FocusRequester,
    listenerInput: OpenConversationsTopBarListenerInput
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    TopAppBar(
        navigationIcon = {
            IconButton(
                onClick = { if (isSearchActive) listenerInput.onSearchDismiss() else listenerInput.onBackClick() }
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.ic_arrow_back_black_24dp),
                    contentDescription = stringResource(R.string.back_button)
                )
            }
        },
        title = {
            if (isSearchActive) {
                TextField(
                    value = searchTerm,
                    onValueChange = listenerInput.onSearchTermChange,
                    placeholder = { Text(stringResource(R.string.nc_search)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                    trailingIcon = {
                        if (searchTerm.isNotEmpty()) {
                            IconButton(onClick = { listenerInput.onSearchTermChange("") }) {
                                Icon(
                                    imageVector = ImageVector.vectorResource(R.drawable.ic_close_search),
                                    contentDescription = stringResource(R.string.nc_search_clear)
                                )
                            }
                        }
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                )
            } else {
                Text(text = stringResource(R.string.openConversations))
            }
        },
        actions = {
            if (!isSearchActive) {
                IconButton(onClick = listenerInput.onSearchActivate) {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.ic_search_white_24dp),
                        contentDescription = stringResource(R.string.search_icon)
                    )
                }
            }
        }
    )
}

@Composable
private fun ConversationsBody(
    viewState: OpenConversationsViewModel.ViewState,
    userBaseUrl: String?,
    onConversationClick: (Conversation) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        when (viewState) {
            is OpenConversationsViewModel.FetchConversationsStartState -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            is OpenConversationsViewModel.FetchConversationsSuccessState -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(viewState.conversations) { conversation ->
                        OpenConversationItem(
                            conversation = conversation,
                            userBaseUrl = userBaseUrl,
                            onClick = { onConversationClick(conversation) }
                        )
                    }
                }
            }
            is OpenConversationsViewModel.FetchConversationsEmptyState -> {
                EmptyConversationsView(modifier = Modifier.align(Alignment.Center))
            }
            is OpenConversationsViewModel.FetchConversationsErrorState -> {
                // Error shown via Snackbar; no additional UI needed here
            }
        }
    }
}

@Composable
private fun OpenConversationItem(conversation: Conversation, userBaseUrl: String?, onClick: () -> Unit) {
    val context = LocalContext.current
    val isDark = DisplayUtils.isDarkModeOn(context)
    val avatarUrl = ApiUtils.getUrlForConversationAvatarWithVersion(
        1,
        userBaseUrl,
        conversation.token,
        isDark,
        conversation.avatarVersion.ifEmpty { null }
    )
    val errorPlaceholder = when (conversation.type) {
        ConversationEnums.ConversationType.ROOM_GROUP_CALL -> R.drawable.ic_circular_group
        ConversationEnums.ConversationType.ROOM_PUBLIC_CALL -> R.drawable.ic_circular_link
        else -> R.drawable.account_circle_96dp
    }
    val imageRequest = ImageRequest.Builder(context)
        .data(avatarUrl)
        .transformations(CircleCropTransformation())
        .error(errorPlaceholder)
        .placeholder(errorPlaceholder)
        .build()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = stringResource(R.string.avatar),
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = conversation.displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (conversation.description.isNotEmpty()) {
                Text(
                    text = conversation.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorResource(R.color.textColorMaxContrast),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun EmptyConversationsView(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.baseline_info_24),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.size(16.dp))
        Text(
            text = stringResource(R.string.nc_no_open_conversations_headline),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = stringResource(R.string.nc_no_open_conversations_text),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

class OpenConversationsScreenListenerInput(
    val onSearchTermChange: (String) -> Unit,
    val onConversationClick: (Conversation) -> Unit,
    val onBackClick: () -> Unit
)

class OpenConversationsTopBarListenerInput(
    val onSearchTermChange: (String) -> Unit,
    val onSearchActivate: () -> Unit,
    val onSearchDismiss: () -> Unit,
    val onBackClick: () -> Unit
)

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewOpenConversationsSuccess() {
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        Surface {
            OpenConversationsScreenContent(
                viewState = OpenConversationsViewModel.FetchConversationsSuccessState(
                    conversations = listOf(
                        Conversation(
                            token = "abc1",
                            displayName = "Design Team",
                            description = "All design discussions",
                            type = ConversationEnums.ConversationType.ROOM_GROUP_CALL
                        ),
                        Conversation(
                            token = "abc2",
                            displayName = "General",
                            description = "",
                            type = ConversationEnums.ConversationType.ROOM_PUBLIC_CALL
                        ),
                        Conversation(
                            token = "abc3",
                            displayName = "Jane Doe",
                            description = "Direct message",
                            type = ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL
                        )
                    )
                ),
                searchTerm = "",
                userBaseUrl = null,
                snackbarHostState = remember { SnackbarHostState() },
                listenerInput = OpenConversationsScreenListenerInput(
                    onSearchTermChange = {},
                    onConversationClick = {},
                    onBackClick = {}
                )
            )
        }
    }
}

@Preview(name = "Light – Loading", showBackground = true)
@Preview(name = "Dark – Loading", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewOpenConversationsLoading() {
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        Surface {
            OpenConversationsScreenContent(
                viewState = OpenConversationsViewModel.FetchConversationsStartState,
                searchTerm = "",
                userBaseUrl = null,
                snackbarHostState = remember { SnackbarHostState() },
                listenerInput = OpenConversationsScreenListenerInput(
                    onSearchTermChange = {},
                    onConversationClick = {},
                    onBackClick = {}
                )
            )
        }
    }
}

@Preview(name = "Light – Empty", showBackground = true)
@Preview(name = "Dark – Empty", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewOpenConversationsEmpty() {
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        Surface {
            OpenConversationsScreenContent(
                viewState = OpenConversationsViewModel.FetchConversationsEmptyState,
                searchTerm = "",
                userBaseUrl = null,
                snackbarHostState = remember { SnackbarHostState() },
                listenerInput = OpenConversationsScreenListenerInput(
                    onSearchTermChange = {},
                    onConversationClick = {},
                    onBackClick = {}
                )
            )
        }
    }
}

@Preview(name = "RTL – Arabic", showBackground = true, locale = "ar")
@Composable
private fun PreviewOpenConversationsRtl() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        Surface {
            OpenConversationsScreenContent(
                viewState = OpenConversationsViewModel.FetchConversationsSuccessState(
                    conversations = listOf(
                        Conversation(
                            token = "abc1",
                            displayName = "فريق التصميم",
                            description = "جميع نقاشات التصميم",
                            type = ConversationEnums.ConversationType.ROOM_GROUP_CALL
                        ),
                        Conversation(
                            token = "abc2",
                            displayName = "عام",
                            description = "",
                            type = ConversationEnums.ConversationType.ROOM_PUBLIC_CALL
                        )
                    )
                ),
                searchTerm = "",
                userBaseUrl = null,
                snackbarHostState = remember { SnackbarHostState() },
                listenerInput = OpenConversationsScreenListenerInput(
                    onSearchTermChange = {},
                    onConversationClick = {},
                    onBackClick = {}
                )
            )
        }
    }
}
