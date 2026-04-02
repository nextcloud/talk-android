/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationlist.ui

import android.app.Activity
import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nextcloud.talk.R
import com.nextcloud.talk.components.ColoredStatusBar
import com.nextcloud.talk.conversationlist.viewmodels.ConversationsListViewModel
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.domain.SearchMessageEntry
import com.nextcloud.talk.models.json.chat.ChatMessageJson
import com.nextcloud.talk.models.json.conversations.ConversationEnums
import com.nextcloud.talk.models.json.participants.Participant
import com.nextcloud.talk.ui.dialog.ChooseAccountDialogCompose
import com.nextcloud.talk.ui.dialog.FilterConversationFragment.Companion.ARCHIVE
import com.nextcloud.talk.ui.dialog.FilterConversationFragment.Companion.DEFAULT
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.DisplayUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val SEARCH_DEBOUNCE_MS = 300
private const val SEARCH_MIN_CHARS = 1

@Suppress("LongParameterList")
data class ConversationsListScreenState(
    val currentUser: User?,
    val credentials: String,
    val showLogo: Boolean,
    val viewThemeUtils: ViewThemeUtils,
    val isShowEcosystem: Boolean,
    val snackbarHostState: SnackbarHostState,
    val isMaintenanceModeFlow: StateFlow<Boolean>,
    val isOnlineFlow: StateFlow<Boolean>,
    val showUnreadBubbleFlow: StateFlow<Boolean>,
    val isFabVisibleFlow: StateFlow<Boolean>,
    val showNotificationWarningFlow: StateFlow<Boolean>,
    val isRefreshingFlow: StateFlow<Boolean>,
    val showShareToFlow: StateFlow<Boolean>,
    val forwardMessageFlow: StateFlow<Boolean>,
    val hasMultipleAccountsFlow: StateFlow<Boolean>,
    val showAccountDialogFlow: StateFlow<Boolean>
)

@Suppress("LongParameterList")
data class ConversationsListScreenCallbacks(
    val onLazyListStateAvailable: (LazyListState?) -> Unit,
    val onScrollChanged: (scrolledDown: Boolean) -> Unit,
    val onScrollStopped: (lastVisibleIndex: Int) -> Unit,
    val onConversationClick: (ConversationModel) -> Unit,
    val onConversationLongClick: (ConversationModel) -> Unit,
    val onMessageResultClick: (SearchMessageEntry) -> Unit,
    val onContactClick: (Participant) -> Unit,
    val onLoadMoreClick: () -> Unit,
    val onRefresh: () -> Unit,
    val onFabClick: () -> Unit,
    val onUnreadBubbleClick: () -> Unit,
    val onNotificationWarningNotNow: () -> Unit,
    val onNotificationWarningShowSettings: () -> Unit,
    val onFederationHintClick: () -> Unit,
    val onFilterClick: () -> Unit,
    val onThreadsClick: () -> Unit,
    val onAvatarClick: () -> Unit,
    val onNavigateBack: () -> Unit,
    val onAccountChooserClick: () -> Unit,
    val onNewConversation: () -> Unit,
    val onAccountDialogDismiss: () -> Unit
)

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun ConversationsListScreen(
    viewModel: ConversationsListViewModel,
    state: ConversationsListScreenState,
    callbacks: ConversationsListScreenCallbacks
) {
    val context = LocalContext.current
    val activity = context as Activity
    val colorScheme = remember { state.viewThemeUtils.getColorScheme(context) }
    val coroutineScope = rememberCoroutineScope()

    // ViewModel state
    val entries by viewModel.conversationListEntriesFlow.collectAsStateWithLifecycle()
    val rooms by viewModel.getRoomsStateFlow.collectAsStateWithLifecycle()
    val isShimmerVisible by viewModel.isShimmerVisible.collectAsStateWithLifecycle()
    val isSearchActive by viewModel.isSearchActiveFlow.collectAsStateWithLifecycle()
    val searchQuery by viewModel.currentSearchQueryFlow.collectAsStateWithLifecycle()
    val isSearchLoading by viewModel.isSearchLoadingFlow.collectAsStateWithLifecycle()
    val filterState by viewModel.filterStateFlow.collectAsStateWithLifecycle()
    val showAvatarBadge by viewModel.showAvatarBadge.collectAsStateWithLifecycle()
    val threadsState by viewModel.threadsExistState.collectAsStateWithLifecycle()
    val federationHintVisible by viewModel.federationInvitationHintVisible.collectAsStateWithLifecycle()

    // Activity-level state
    val isMaintenanceMode by state.isMaintenanceModeFlow.collectAsStateWithLifecycle()
    val isOnline by state.isOnlineFlow.collectAsStateWithLifecycle()
    val showUnreadBubble by state.showUnreadBubbleFlow.collectAsStateWithLifecycle()
    val isFabVisible by state.isFabVisibleFlow.collectAsStateWithLifecycle()
    val showNotificationWarning by state.showNotificationWarningFlow.collectAsStateWithLifecycle()
    val isRefreshing by state.isRefreshingFlow.collectAsStateWithLifecycle()
    val showShareTo by state.showShareToFlow.collectAsStateWithLifecycle()
    val isForward by state.forwardMessageFlow.collectAsStateWithLifecycle()
    val hasMultipleAccounts by state.hasMultipleAccountsFlow.collectAsStateWithLifecycle()
    val showAccountDialog by state.showAccountDialogFlow.collectAsStateWithLifecycle()

    // Derived state
    val isArchivedFilterActive = filterState[ARCHIVE] == true

    val effectiveShimmerVisible = isShimmerVisible

    val isRoomsEmpty = rooms.isEmpty() && !effectiveShimmerVisible
    val showSearchNoResults = isSearchActive && entries.isEmpty() && searchQuery.isNotEmpty() && !isSearchLoading
    val showFilterActive = filterState.any { (k, v) -> k != DEFAULT && v }
    val showThreadsButton =
        threadsState is ConversationsListViewModel.ThreadsExistUiState.Success &&
            (threadsState as ConversationsListViewModel.ThreadsExistUiState.Success).threadsExistence == true

    val mode: TopBarMode = when {
        showShareTo -> TopBarMode.TitleBar(
            title = stringResource(R.string.send_to_three_dots),
            showAccountChooser = hasMultipleAccounts
        )

        isForward -> TopBarMode.TitleBar(
            title = stringResource(R.string.nc_forward_to_three_dots),
            showAccountChooser = false
        )

        isSearchActive -> TopBarMode.SearchActive(query = searchQuery)
        else -> TopBarMode.SearchBarIdle
    }

    val avatarUrl = remember(state.currentUser) {
        ApiUtils.getUrlForAvatar(
            state.currentUser?.baseUrl,
            state.currentUser?.userId,
            true,
            darkMode = DisplayUtils.isDarkModeOn(context)
        )
    }

    val lazyListState = rememberLazyListState()
    DisposableEffect(lazyListState) {
        callbacks.onLazyListStateAvailable(lazyListState)
        onDispose { callbacks.onLazyListStateAvailable(null) }
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotEmpty()) {
            delay(SEARCH_DEBOUNCE_MS.toLong())
            if (searchQuery.length >= SEARCH_MIN_CHARS) {
                viewModel.getSearchQuery(context, searchQuery)
            }
        }
    }

    MaterialTheme(colorScheme = colorScheme) {
        ColoredStatusBar()

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
                    StatusBannerRow(isOffline = !isOnline, isMaintenanceMode = isMaintenanceMode)
                    ConversationListTopBar(
                        state = ConversationListTopBarState(
                            mode = mode,
                            showAvatarBadge = showAvatarBadge,
                            avatarUrl = avatarUrl,
                            credentials = state.credentials,
                            showFilterActive = showFilterActive,
                            showThreadsButton = showThreadsButton
                        ),
                        actions = ConversationListTopBarActions(
                            onSearchQueryChange = { viewModel.setSearchQuery(it) },
                            onSearchActivate = { viewModel.setIsSearchActive(true) },
                            onSearchClose = {
                                viewModel.setIsSearchActive(false)
                                coroutineScope.launch { lazyListState.scrollToItem(0) }
                            },
                            onFilterClick = callbacks.onFilterClick,
                            onThreadsClick = callbacks.onThreadsClick,
                            onAvatarClick = callbacks.onAvatarClick,
                            onNavigateBack = callbacks.onNavigateBack,
                            onAccountChooserClick = callbacks.onAccountChooserClick
                        )
                    )
                }
            },
            floatingActionButton = {
                ConversationListFab(
                    isVisible = isFabVisible && !isSearchActive,
                    isEnabled = isOnline,
                    onClick = callbacks.onFabClick
                )
            },
            snackbarHost = {
                SnackbarHost(
                    hostState = state.snackbarHostState,
                    modifier = Modifier.navigationBarsPadding()
                )
            }
        ) { paddingValues ->
            val layoutDirection = LocalLayoutDirection.current
            Box(
                modifier = Modifier
                    .padding(
                        top = paddingValues.calculateTopPadding(),
                        start = paddingValues.calculateStartPadding(layoutDirection),
                        end = paddingValues.calculateEndPadding(layoutDirection)
                    )
                    .fillMaxSize()
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    NotificationWarningCard(
                        visible = showNotificationWarning,
                        onNotNow = callbacks.onNotificationWarningNotNow,
                        onShowSettings = callbacks.onNotificationWarningShowSettings
                    )
                    FederationInvitationHintCard(
                        visible = federationHintVisible,
                        onClick = callbacks.onFederationHintClick
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        when {
                            showSearchNoResults ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .imePadding(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    SearchNoResultsView()
                                }

                            state.currentUser != null ->
                                ConversationList(
                                    entries = entries,
                                    isRefreshing = isRefreshing,
                                    currentUser = state.currentUser,
                                    credentials = state.credentials,
                                    searchQuery = searchQuery,
                                    onConversationClick = callbacks.onConversationClick,
                                    onConversationLongClick = callbacks.onConversationLongClick,
                                    onMessageResultClick = callbacks.onMessageResultClick,
                                    onContactClick = callbacks.onContactClick,
                                    onLoadMoreClick = callbacks.onLoadMoreClick,
                                    onRefresh = callbacks.onRefresh,
                                    onScrollChanged = callbacks.onScrollChanged,
                                    onScrollStopped = callbacks.onScrollStopped,
                                    listState = lazyListState,
                                    contentBottomPadding = paddingValues.calculateBottomPadding()
                                )
                        }
                        // Empty-state overlay (centered; handles its own visibility)
                        if (!effectiveShimmerVisible) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                ConversationsEmptyStateView(
                                    isListEmpty = isRoomsEmpty,
                                    showNoArchivedView = isArchivedFilterActive,
                                    showLogo = state.showLogo,
                                    onCreateNewConversation = callbacks.onNewConversation
                                )
                            }
                        }
                        // Shimmer overlay (on top while first load is in progress)
                        ConversationListSkeleton(isVisible = effectiveShimmerVisible)
                    }
                }

                // Unread-mention bubble (bottom-center overlay)
                UnreadMentionBubble(
                    visible = showUnreadBubble && !isSearchActive,
                    onClick = callbacks.onUnreadBubbleClick,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp)
                )
            }

            // Account-chooser dialog
            if (showAccountDialog) {
                val dialog = remember { ChooseAccountDialogCompose() }
                val shouldDismiss = remember { mutableStateOf(false) }
                LaunchedEffect(shouldDismiss.value) {
                    if (shouldDismiss.value) callbacks.onAccountDialogDismiss()
                }
                dialog.GetChooseAccountDialog(shouldDismiss, activity, state.isShowEcosystem)
            }
        }
    }
}

@Suppress("LongParameterList")
private fun previewConvModel(
    displayName: String,
    token: String,
    type: ConversationEnums.ConversationType = ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL,
    unreadMessages: Int = 0,
    unreadMention: Boolean = false,
    unreadMentionDirect: Boolean = false,
    favorite: Boolean = false,
    status: String? = null,
    statusIcon: String? = null,
    remoteServer: String? = null,
    lastMessage: ChatMessageJson? = null
) = ConversationModel(
    internalId = "1@$token",
    accountId = 1L,
    token = token,
    name = displayName.lowercase(),
    displayName = displayName,
    description = "",
    type = type,
    participantType = Participant.ParticipantType.USER,
    sessionId = "",
    actorId = "user1",
    actorType = "users",
    objectType = ConversationEnums.ObjectType.DEFAULT,
    notificationLevel = ConversationEnums.NotificationLevel.DEFAULT,
    conversationReadOnlyState = ConversationEnums.ConversationReadOnlyState.CONVERSATION_READ_WRITE,
    lobbyState = ConversationEnums.LobbyState.LOBBY_STATE_ALL_PARTICIPANTS,
    lobbyTimer = 0L,
    canLeaveConversation = true,
    canDeleteConversation = false,
    unreadMentionDirect = unreadMentionDirect,
    notificationCalls = 0,
    avatarVersion = "",
    hasCustomAvatar = false,
    callStartTime = 0L,
    unreadMessages = unreadMessages,
    unreadMention = unreadMention,
    favorite = favorite,
    status = status,
    statusIcon = statusIcon,
    remoteServer = remoteServer,
    lastMessage = lastMessage,
    lastActivity = System.currentTimeMillis() / 1000L - 600L
)

private fun previewMsg(
    actorId: String = "other",
    actorDisplayName: String = "Bob",
    message: String = "Hello there",
    messageType: String = "comment",
    messageParameters: HashMap<String?, HashMap<String?, String?>>? = null
) = ChatMessageJson(
    id = 1L,
    actorId = actorId,
    actorDisplayName = actorDisplayName,
    message = message,
    messageType = messageType,
    messageParameters = messageParameters
)

private fun previewCurrentUser() =
    User(
        id = 1L,
        userId = "user1",
        username = "user1",
        baseUrl = "https://cloud.example.com",
        token = "token",
        displayName = "Test User",
        capabilities = null
    )

private fun previewConvEntries(): List<ConversationListEntry> =
    listOf(
        ConversationListEntry.ConversationEntry(
            previewConvModel(
                "Alice",
                "tok1",
                status = "online",
                unreadMessages = 2,
                unreadMention = true,
                unreadMentionDirect = true,
                lastMessage = previewMsg(message = "Did you see my message?")
            )
        ),
        ConversationListEntry.ConversationEntry(
            previewConvModel(
                "Project Team",
                "tok2",
                type = ConversationEnums.ConversationType.ROOM_GROUP_CALL,
                unreadMessages = 3,
                unreadMention = true,
                lastMessage = previewMsg(actorDisplayName = "Carol", message = "@user1 please review the PR")
            )
        ),
        ConversationListEntry.ConversationEntry(
            previewConvModel(
                "Bob",
                "tok3",
                favorite = true,
                status = "away",
                lastMessage = previewMsg(
                    actorDisplayName = "Bob",
                    message = "{file}",
                    messageParameters = hashMapOf(
                        "file" to hashMapOf("name" to "voice.mp3", "mimetype" to "audio/mpeg")
                    )
                )
            )
        ),
        ConversationListEntry.ConversationEntry(
            previewConvModel(
                "Dev Team",
                "tok4",
                type = ConversationEnums.ConversationType.ROOM_GROUP_CALL,
                unreadMessages = 1500,
                lastMessage = previewMsg(actorDisplayName = "Dave", message = "So many messages!")
            )
        )
    )

@Composable
private fun ConversationsListScreenPreviewContent(
    showThreadsButton: Boolean = false,
    showUnreadBubble: Boolean = false
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Scaffold(
            contentWindowInsets = WindowInsets(0),
            topBar = {
                ConversationListTopBar(
                    state = ConversationListTopBarState(
                        mode = TopBarMode.SearchBarIdle,
                        showAvatarBadge = false,
                        avatarUrl = null,
                        credentials = "",
                        showFilterActive = false,
                        showThreadsButton = showThreadsButton
                    ),
                    actions = ConversationListTopBarActions()
                )
            },
            floatingActionButton = {
                ConversationListFab(isVisible = true, isEnabled = true, onClick = {})
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .padding(
                        top = paddingValues.calculateTopPadding(),
                        start = paddingValues.calculateStartPadding(LocalLayoutDirection.current),
                        end = paddingValues.calculateEndPadding(LocalLayoutDirection.current)
                    )
                    .fillMaxSize()
            ) {
                ConversationList(
                    entries = previewConvEntries(),
                    isRefreshing = false,
                    currentUser = previewCurrentUser(),
                    credentials = "",
                    onConversationClick = {},
                    onConversationLongClick = {},
                    onMessageResultClick = {},
                    onContactClick = {},
                    onLoadMoreClick = {},
                    onRefresh = {},
                    contentBottomPadding = paddingValues.calculateBottomPadding()
                )
                UnreadMentionBubble(
                    visible = showUnreadBubble,
                    onClick = {},
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp)
                )
            }
        }
    }
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "RTL Arabic", locale = "ar")
@Composable
private fun ConversationsListScreenDarkPreview() {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        ConversationsListScreenPreviewContent(showThreadsButton = !isSystemInDarkTheme())
    }
}
