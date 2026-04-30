/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017-2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.conversationlist

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.appcompat.app.AlertDialog
import androidx.compose.material3.SnackbarHostState
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import autodagger.AutoInjector
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nextcloud.talk.R
import com.nextcloud.talk.account.BrowserLoginActivity
import com.nextcloud.talk.account.ServerSelectionActivity
import com.nextcloud.talk.activities.BaseActivity
import com.nextcloud.talk.activities.CallActivity
import com.nextcloud.talk.activities.MainActivity
import com.nextcloud.talk.api.NcApiCoroutines
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.conversation.RenameConversationDialogFragment
import com.nextcloud.talk.chat.ChatActivity
import com.nextcloud.talk.contacts.ContactsActivity
import com.nextcloud.talk.contacts.ContactsViewModel
import com.nextcloud.talk.contextchat.ContextChatViewModel
import com.nextcloud.talk.conversationlist.ui.ConversationOpsAction
import com.nextcloud.talk.conversationlist.ui.ConversationsListScreen
import com.nextcloud.talk.conversationlist.ui.ConversationsListScreenCallbacks
import com.nextcloud.talk.conversationlist.ui.ConversationsListScreenState
import com.nextcloud.talk.conversationlist.viewmodels.ConversationsListViewModel
import com.nextcloud.talk.data.network.NetworkMonitor
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.events.ConversationsListFetchDataEvent
import com.nextcloud.talk.events.EventStatus
import com.nextcloud.talk.invitation.InvitationsActivity
import com.nextcloud.talk.jobs.AccountRemovalWorker
import com.nextcloud.talk.jobs.ContactAddressBookWorker.Companion.run
import com.nextcloud.talk.jobs.DeleteConversationWorker
import com.nextcloud.talk.jobs.LeaveConversationWorker
import com.nextcloud.talk.jobs.UploadAndShareFilesWorker
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.domain.SearchMessageEntry
import com.nextcloud.talk.models.json.conversations.ConversationEnums
import com.nextcloud.talk.settings.SettingsActivity
import com.nextcloud.talk.threadsoverview.ThreadsOverviewActivity
import com.nextcloud.talk.ui.chooseaccount.ChooseAccountShareToDialogFragment
import com.nextcloud.talk.ui.dialog.FilterConversationFragment
import com.nextcloud.talk.ui.dialog.FilterConversationFragment.Companion.ARCHIVE
import com.nextcloud.talk.ui.dialog.FilterConversationFragment.Companion.MENTION
import com.nextcloud.talk.ui.dialog.FilterConversationFragment.Companion.UNREAD
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.BrandingUtils
import com.nextcloud.talk.utils.CapabilitiesUtil
import com.nextcloud.talk.utils.CapabilitiesUtil.hasSpreedFeatureCapability
import com.nextcloud.talk.utils.CapabilitiesUtil.isServerEOL
import com.nextcloud.talk.utils.ClosedInterfaceImpl
import com.nextcloud.talk.utils.ConversationUtils
import com.nextcloud.talk.utils.FileUtils
import com.nextcloud.talk.utils.Mimetype
import com.nextcloud.talk.utils.NotificationUtils
import com.nextcloud.talk.utils.ParticipantPermissions
import com.nextcloud.talk.utils.ShareUtils
import com.nextcloud.talk.utils.SpreedFeatures
import com.nextcloud.talk.utils.UnifiedPushUtils
import com.nextcloud.talk.utils.UserIdUtils
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.bundle.BundleKeys.ADD_ADDITIONAL_ACCOUNT
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_FORWARD_HIDE_SOURCE_ROOM
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_FORWARD_MSG_FLAG
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_FORWARD_MSG_TEXT
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_INTERNAL_USER_ID
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_SCROLL_TO_NOTIFICATION_CATEGORY
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_SHARED_TEXT
import com.nextcloud.talk.utils.permissions.PlatformPermissionUtil
import com.nextcloud.talk.utils.power.PowerManagerUtils
import com.nextcloud.talk.utils.singletons.ApplicationWideCurrentRoomHolder
import android.text.TextUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import retrofit2.HttpException
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@SuppressLint("StringFormatInvalid")
@AutoInjector(NextcloudTalkApplication::class)
@Suppress("LargeClass", "TooManyFunctions")
class ConversationsListActivity : BaseActivity() {

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var ncApiCoroutines: NcApiCoroutines

    @Inject
    lateinit var platformPermissionUtil: PlatformPermissionUtil

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    @Inject
    lateinit var networkMonitor: NetworkMonitor

    @Inject
    lateinit var contactsViewModel: ContactsViewModel

    lateinit var conversationsListViewModel: ConversationsListViewModel
    lateinit var contextChatViewModel: ContextChatViewModel

    private var currentUser: User? = null
    private val snackbarHostState = SnackbarHostState()
    private val isMaintenanceModeState = MutableStateFlow(false)
    private val showUnreadBubbleState = MutableStateFlow(false)
    private val isFabVisibleState = MutableStateFlow(true)
    private val showNotificationWarningState = MutableStateFlow(false)
    private val isRefreshingState = MutableStateFlow(false)
    private val showAccountDialogState = MutableStateFlow(false)

    // Lazy list state – set from inside setContent, read from onPause
    private var conversationListLazyListState: androidx.compose.foundation.lazy.LazyListState? = null

    // Ensures saved scroll position is restored only once per resume cycle, not on every room-list refresh.
    private var scrollPositionRestored = false

    private var nextUnreadConversationScrollPosition = 0
    private var credentials: String? = null
    private val showShareToScreenState = MutableStateFlow(false)
    private val forwardMessageState = MutableStateFlow(false)
    private val hasMultipleAccountsState = MutableStateFlow(false)
    private val showShareToScreen get() = showShareToScreenState.value
    private val forwardMessage get() = forwardMessageState.value
    private var filesToShare: ArrayList<String>? = null
    private var selectedConversation: ConversationModel? = null
    private var textToPaste: String? = ""
    private var selectedMessageId: String? = null
    private var pendingDirectShareToken: String? = null

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (forwardMessage) {
                finish()
            } else {
                finishAffinity()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        currentUser = currentUserProviderOld.currentUser.blockingGet()

        conversationsListViewModel = ViewModelProvider(this, viewModelFactory)[ConversationsListViewModel::class.java]
        contextChatViewModel = ViewModelProvider(this, viewModelFactory)[ContextChatViewModel::class.java]

        setSupportActionBar(null)
        forwardMessageState.value = intent.getBooleanExtra(KEY_FORWARD_MSG_FLAG, false)
        if (savedInstanceState != null) {
            showAccountDialogState.value = savedInstanceState.getBoolean(KEY_ACCOUNT_DIALOG_VISIBLE, false)
        }
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        setContent {
            ConversationsListScreen(
                viewModel = conversationsListViewModel,
                state = buildScreenState(),
                callbacks = buildScreenCallbacks()
            )
        }

        initObservers()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_ACCOUNT_DIALOG_VISIBLE, showAccountDialogState.value)
    }

    private fun buildScreenState() =
        ConversationsListScreenState(
            currentUser = currentUser,
            credentials = credentials ?: "",
            showLogo = BrandingUtils.isOriginalNextcloudClient(applicationContext),
            viewThemeUtils = viewThemeUtils,
            isShowEcosystem = appPreferences.isShowEcosystem && !resources.getBoolean(R.bool.is_branded_client),
            snackbarHostState = snackbarHostState,
            isMaintenanceModeFlow = isMaintenanceModeState,
            isOnlineFlow = networkMonitor.isOnline,
            showUnreadBubbleFlow = showUnreadBubbleState,
            isFabVisibleFlow = isFabVisibleState,
            showNotificationWarningFlow = showNotificationWarningState,
            isRefreshingFlow = isRefreshingState,
            showShareToFlow = showShareToScreenState,
            forwardMessageFlow = forwardMessageState,
            hasMultipleAccountsFlow = hasMultipleAccountsState,
            showAccountDialogFlow = showAccountDialogState,
            selectedConversationForOpsFlow = conversationsListViewModel.selectedConversationForOps
        )

    @Suppress("LongMethod")
    private fun buildScreenCallbacks() =
        ConversationsListScreenCallbacks(
            onLazyListStateAvailable = { listState -> conversationListLazyListState = listState },
            onScrollChanged = { isFabVisibleState.value = !it },
            onScrollStopped = { checkToShowUnreadBubble(it) },
            onConversationClick = { handleConversation(it) },
            onConversationLongClick = { handleConversationLongClick(it) },
            onMessageResultClick = { showContextChatForMessage(it) },
            onContactClick = { contactsViewModel.createRoom(ROOM_TYPE_ONE_ONE, null, it.actorId!!, null) },
            onLoadMoreClick = { conversationsListViewModel.loadMoreMessages(context) },
            onRefresh = {
                isMaintenanceModeState.value = false
                isRefreshingState.value = true
                appPreferences.setConversationListPositionAndOffset(0, 0)
                fetchRooms()
                fetchPendingInvitations()
            },
            onFabClick = {
                run(context)
                showNewConversationsScreen()
            },
            onUnreadBubbleClick = {
                lifecycleScope.launch {
                    val listState = conversationListLazyListState ?: return@launch
                    val viewportHeight = listState.layoutInfo.viewportEndOffset
                    val avgItemHeight = listState.layoutInfo.visibleItemsInfo
                        .map { it.size }
                        .average()
                        .takeIf { it.isFinite() }
                        ?.toInt() ?: 0
                    val scrollOffset = -(viewportHeight / 2) + (avgItemHeight / 2)
                    listState.scrollToItem(nextUnreadConversationScrollPosition, scrollOffset)
                }
                showUnreadBubbleState.value = false
            },
            onNotificationWarningNotNow = {
                appPreferences.setNotificationWarningLastPostponedDate(System.currentTimeMillis())
                showNotificationWarningState.value = false
            },
            onNotificationWarningShowSettings = {
                val bundle = Bundle()
                bundle.putBoolean(KEY_SCROLL_TO_NOTIFICATION_CATEGORY, true)
                val settingsIntent = Intent(context, SettingsActivity::class.java)
                settingsIntent.putExtras(bundle)
                startActivity(settingsIntent)
            },
            onFederationHintClick = { startActivity(Intent(context, InvitationsActivity::class.java)) },
            onFilterClick = {
                FilterConversationFragment
                    .newInstance(conversationsListViewModel.filterStateFlow.value.toMutableMap())
                    .show(supportFragmentManager, FilterConversationFragment.TAG)
            },
            onThreadsClick = { openFollowedThreadsOverview() },
            onAvatarClick = {
                if (resources.getBoolean(R.bool.multiaccount_support)) {
                    showChooseAccountDialog()
                } else {
                    startActivity(Intent(context, SettingsActivity::class.java))
                }
            },
            onNavigateBack = { onBackPressedDispatcher.onBackPressed() },
            onAccountChooserClick = {
                ChooseAccountShareToDialogFragment.newInstance()
                    .show(supportFragmentManager, ChooseAccountShareToDialogFragment.TAG)
            },
            onNewConversation = { showNewConversationsScreen() },
            onAccountDialogDismiss = { showAccountDialogState.value = false },
            onConversationOpsDismiss = { conversationsListViewModel.setSelectedConversationForOps(null) },
            onConversationOpsAction = { action, conversation -> handleConversationOpsAction(action, conversation) }
        )

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        // handle notification permission on API level >= 33
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !platformPermissionUtil.isPostNotificationsPermissionGranted() &&
            (ClosedInterfaceImpl().isGooglePlayServicesAvailable ||
                appPreferences.useUnifiedPush ||
                UnifiedPushUtils.hasEmbeddedDistributor(context))
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_POST_NOTIFICATIONS_PERMISSION
            )
        }
    }

    override fun onResume() {
        super.onResume()
        scrollPositionRestored = false

        showNotificationWarningState.value = shouldShowNotificationWarning()
        showShareToScreenState.value = hasActivityActionSendIntent()

        // Home screen shortcut tap: shortcut uses ACTION_VIEW with KEY_ROOM_TOKEN to open the
        // conversation directly without showing the share picker.
        if (Intent.ACTION_VIEW == intent.action && intent.hasExtra(KEY_ROOM_TOKEN)) {
            pendingDirectShareToken = intent.getStringExtra(KEY_ROOM_TOKEN)
        }

        // Share sheet shortcut tap: Android delivers ACTION_SEND with EXTRA_SHORTCUT_ID.
        // Extract the room token from the shortcut ID so we can pre-select the conversation.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hasActivityActionSendIntent()) {
            val shortcutId = intent.getStringExtra(Intent.EXTRA_SHORTCUT_ID)
            if (shortcutId != null) {
                pendingDirectShareToken = DirectShareHelper.extractTokenFromShortcutId(shortcutId)
            }
        }

        if (!eventBus.isRegistered(this)) {
            eventBus.register(this)
        }

        if (currentUser != null) {
            if (isServerEOL(currentUser!!.serverVersion?.major)) {
                showServerEOLDialog()
                return
            }
            credentials = ApiUtils.getCredentials(currentUser!!.username, currentUser!!.token)

            if (currentUser!!.id != appPreferences.getConversationListLastUserId()) {
                appPreferences.setConversationListPositionAndOffset(0, 0)
            }

            hasMultipleAccountsState.value = userManager.users.blockingGet().size > 1
            conversationsListViewModel.setHideRoomToken(intent.getStringExtra(KEY_FORWARD_HIDE_SOURCE_ROOM))
            fetchRooms()
            fetchPendingInvitations()
        } else {
            Log.e(TAG, "currentUser was null")
            showSnackbar(getString(R.string.nc_common_error_sorry))
        }

        conversationsListViewModel.checkIfThreadsExist()
        conversationsListViewModel.reloadFilterFromStorage(UserIdUtils.getIdForUser(currentUser))
    }

    override fun onPause() {
        super.onPause()
        conversationListLazyListState?.let { state ->
            val firstOffset = state.layoutInfo.visibleItemsInfo.firstOrNull()?.offset ?: 0
            appPreferences.setConversationListPositionAndOffset(state.firstVisibleItemIndex, firstOffset)
        }
        appPreferences.setConversationListLastUserId(currentUser?.id ?: -1L)
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun initObservers() {
        conversationsListViewModel.getRoomsViewState.observe(this) { state ->
            when (state) {
                is ConversationsListViewModel.GetRoomsSuccessState -> {
                    isRefreshingState.value = false
                }

                is ConversationsListViewModel.GetRoomsErrorState -> {
                    isRefreshingState.value = false
                    handleHttpExceptions(state.throwable)
                }

                else -> {}
            }
        }

        lifecycleScope.launch {
            conversationsListViewModel.getRoomsFlow
                .onEach { list ->
                    // Add app shortcut for note to self
                    val noteToSelf = list
                        .firstOrNull { ConversationUtils.isNoteToSelfConversation(it) }
                    val isNoteToSelfAvailable = noteToSelf != null
                    handleNoteToSelfShortcut(isNoteToSelfAvailable, noteToSelf?.token ?: "")

                    // Update Direct Share targets
                    if (currentUser != null) {
                        lifecycleScope.launch {
                            DirectShareHelper.publishShareTargetShortcuts(
                                context,
                                currentUser!!,
                                list
                            )
                        }
                    }

                    // check for Direct Share
                    val token = pendingDirectShareToken
                    if (token != null) {
                        pendingDirectShareToken = null
                        val conversation = list.firstOrNull { it.token == token }
                        if (conversation != null) handleConversation(conversation)
                    }

                    if (!scrollPositionRestored) {
                        scrollPositionRestored = true
                        val pair = appPreferences.conversationListPositionAndOffset
                        lifecycleScope.launch {
                            conversationListLazyListState?.scrollToItem(pair.first, pair.second)
                        }
                    }
                }.collect()
        }

        lifecycleScope.launch {
            contactsViewModel.roomViewState.onEach { state ->
                when (state) {
                    is ContactsViewModel.RoomUiState.Success -> {
                        val conversation = state.conversation
                        val bundle = Bundle()
                        bundle.putString(KEY_ROOM_TOKEN, conversation?.token)
                        val chatIntent = Intent(context, ChatActivity::class.java)
                        chatIntent.putExtras(bundle)
                        chatIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        startActivity(chatIntent)
                    }

                    else -> {}
                }
            }.collect()
        }

        lifecycleScope.launch {
            conversationsListViewModel.filterStateFlow.collect { filterState ->
                if (filterState[ARCHIVE] == true) showUnreadBubbleState.value = false
            }
        }

        lifecycleScope.launch {
            conversationsListViewModel.readUnreadState.collect { state ->
                when (state) {
                    is ConversationsListViewModel.ConversationReadUnreadUiState.Success -> {
                        fetchRooms()
                        val resId = if (state.isMarkedRead) R.string.marked_as_read else R.string.marked_as_unread
                        showSnackbar(String.format(resources.getString(resId), state.conversationDisplayName))
                        conversationsListViewModel.resetReadUnreadState()
                    }
                    is ConversationsListViewModel.ConversationReadUnreadUiState.Error -> {
                        showSnackbar(resources.getString(R.string.nc_common_error_sorry))
                        conversationsListViewModel.resetReadUnreadState()
                    }
                    ConversationsListViewModel.ConversationReadUnreadUiState.None -> { /* no-op */ }
                }
            }
        }
    }

    private fun handleNoteToSelfShortcut(noteToSelfAvailable: Boolean, noteToSelfToken: String) {
        if (noteToSelfAvailable) {
            val bundle = Bundle()
            bundle.putString(KEY_ROOM_TOKEN, noteToSelfToken)
            bundle.putBoolean(BundleKeys.KEY_FOCUS_INPUT, true)
            val intent = Intent(context, ChatActivity::class.java)
            intent.putExtras(bundle)
            intent.action = Intent.ACTION_VIEW
            val openNotesString = resources.getString(R.string.open_notes)

            val shortcut = ShortcutInfoCompat.Builder(context, NOTE_TO_SELF_SHORTCUT_ID)
                .setShortLabel(openNotesString)
                .setLongLabel(openNotesString)
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_pencil_grey600_24dp))
                .setIntent(intent)
                .build()

            ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
        } else {
            ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(NOTE_TO_SELF_SHORTCUT_ID))
        }
    }

    fun showOnlyNearFutureEvents() {
        // Reset all filters so the ViewModel's default view (non-archived, non-future-events) is shown
        conversationsListViewModel.applyFilter(
            mapOf(
                MENTION to false,
                UNREAD to false,
                ARCHIVE to false,
                FilterConversationFragment.DEFAULT to true
            )
        )
    }

    private fun handleConversationLongClick(model: ConversationModel) {
        if (!showShareToScreen && networkMonitor.isOnline.value) {
            conversationsListViewModel.setSelectedConversationForOps(model)
        }
    }

    private fun showContextChatForMessage(result: SearchMessageEntry) {
        contextChatViewModel.getContextForChatMessages(
            credentials = credentials ?: "",
            baseUrl = currentUser?.baseUrl ?: "",
            token = result.conversationToken,
            threadId = result.threadId,
            messageId = result.messageId ?: "",
            title = result.title
        )
    }

    fun filterConversation() {
        conversationsListViewModel.reloadFilterFromStorage(UserIdUtils.getIdForUser(currentUser))
    }

    private fun showChooseAccountDialog() {
        showAccountDialogState.value = true
    }

    private fun hasActivityActionSendIntent(): Boolean =
        Intent.ACTION_SEND == intent.action || Intent.ACTION_SEND_MULTIPLE == intent.action

    fun showSnackbar(text: String) {
        lifecycleScope.launch { snackbarHostState.showSnackbar(text) }
    }

    fun fetchRooms() {
        conversationsListViewModel.getRooms(currentUser!!)
    }

    private fun fetchPendingInvitations() {
        if (hasSpreedFeatureCapability(currentUser?.capabilities?.spreedCapability, SpreedFeatures.FEDERATION_V1)) {
            conversationsListViewModel.getFederationInvitations()
        }
    }

    private fun handleHttpExceptions(throwable: Throwable) {
        if (!networkMonitor.isOnline.value) return

        if (throwable is HttpException) {
            when (throwable.code()) {
                HTTP_UNAUTHORIZED -> showUnauthorizedDialog()
                HTTP_CLIENT_UPGRADE_REQUIRED -> showOutdatedClientDialog()
                HTTP_SERVICE_UNAVAILABLE -> showServiceUnavailableDialog(throwable)
                else -> {
                    Log.e(TAG, "Http Exception in ConversationListActivity", throwable)
                    showErrorDialog()
                }
            }
        } else {
            Log.e(TAG, "Exception in ConversationListActivity", throwable)
            showErrorDialog()
        }
    }

    private fun showErrorDialog() {
        val dialogBuilder = MaterialAlertDialogBuilder(this)
            .setIcon(
                viewThemeUtils.dialog.colorMaterialAlertDialogIcon(
                    context,
                    R.drawable.ic_baseline_error_outline_24dp
                )
            )
            .setTitle(R.string.error_loading_chats)
            .setCancelable(false)
            .setNegativeButton(R.string.close, null)

        if (resources!!.getBoolean(R.bool.multiaccount_support) && userManager.users.blockingGet().size > 1) {
            dialogBuilder.setPositiveButton(R.string.nc_switch_account) { _, _ ->
                showChooseAccountDialog()
            }
        }

        if (resources!!.getBoolean(R.bool.multiaccount_support)) {
            dialogBuilder.setNeutralButton(R.string.nc_account_chooser_add_account) { _, _ ->
                val intent = Intent(this, ServerSelectionActivity::class.java)
                intent.putExtra(ADD_ADDITIONAL_ACCOUNT, true)
                startActivity(intent)
            }
        }

        viewThemeUtils.dialog.colorMaterialAlertDialogBackground(this, dialogBuilder)
        val dialog = dialogBuilder.show()
        viewThemeUtils.platform.colorTextButtons(
            dialog.getButton(AlertDialog.BUTTON_POSITIVE),
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE),
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
        )
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    private fun checkToShowUnreadBubble(lastVisibleIndex: Int) {
        if (conversationsListViewModel.isSearchActiveFlow.value) {
            nextUnreadConversationScrollPosition = 0
            showUnreadBubbleState.value = false
            return
        }
        try {
            val entries = conversationsListViewModel.conversationListEntriesFlow.value
            val firstUnreadPosition = findFirstOffscreenUnreadPosition(entries, lastVisibleIndex)
            if (firstUnreadPosition != null) {
                nextUnreadConversationScrollPosition = firstUnreadPosition
                showUnreadBubbleState.value = true
            } else {
                nextUnreadConversationScrollPosition = 0
                showUnreadBubbleState.value = false
            }
        } catch (e: Exception) {
            Log.d(TAG, "Exception in checkToShowUnreadBubble", e)
        }
    }

    private fun findFirstOffscreenUnreadPosition(
        entries: List<com.nextcloud.talk.conversationlist.ui.ConversationListEntry>,
        lastVisibleIndex: Int
    ): Int? {
        entries.forEachIndexed { index, entry ->
            if (index > lastVisibleIndex &&
                entry is com.nextcloud.talk.conversationlist.ui.ConversationListEntry.ConversationEntry
            ) {
                val model = entry.model
                if (model.unreadMention ||
                    (
                        model.unreadMessages > 0 &&
                            model.type == ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL
                        )
                ) {
                    return index
                }
            }
        }
        return null
    }

    private fun showNewConversationsScreen() {
        val intent = Intent(context, ContactsActivity::class.java)
        startActivity(intent)
    }

    public override fun onDestroy() {
        super.onDestroy()
    }

    @Suppress("Detekt.ComplexMethod")
    private fun handleConversation(conversation: ConversationModel?) {
        selectedConversation = conversation
        if (selectedConversation != null) {
            val hasChatPermission = ParticipantPermissions(
                currentUser?.capabilities?.spreedCapability,
                selectedConversation!!
            )
                .hasChatPermission()
            if (showShareToScreen) {
                if (hasChatPermission &&
                    !isReadOnlyConversation(selectedConversation!!) &&
                    !shouldShowLobby(selectedConversation!!)
                ) {
                    handleSharedData()
                } else {
                    showSnackbar(getString(R.string.send_to_forbidden))
                }
            } else if (forwardMessage) {
                if (hasChatPermission && !isReadOnlyConversation(selectedConversation!!)) {
                    openConversation(intent.getStringExtra(KEY_FORWARD_MSG_TEXT))
                    forwardMessageState.value = false
                } else {
                    showSnackbar(getString(R.string.send_to_forbidden))
                }
            } else {
                openConversation()
            }
        }
    }

    private fun shouldShowLobby(conversation: ConversationModel): Boolean {
        val participantPermissions = ParticipantPermissions(
            currentUser?.capabilities?.spreedCapability,
            selectedConversation!!
        )
        return conversation.lobbyState == ConversationEnums.LobbyState.LOBBY_STATE_MODERATORS_ONLY &&
            !ConversationUtils.canModerate(conversation, currentUser?.capabilities?.spreedCapability) &&
            !participantPermissions.canIgnoreLobby()
    }

    private fun isReadOnlyConversation(conversation: ConversationModel): Boolean =
        conversation.conversationReadOnlyState ===
            ConversationEnums.ConversationReadOnlyState.CONVERSATION_READ_ONLY

    private fun handleSharedData() {
        collectDataFromIntent()
        if (textToPaste!!.isNotEmpty()) {
            openConversation(textToPaste)
        } else if (filesToShare != null && filesToShare!!.isNotEmpty()) {
            showSendFilesConfirmDialog()
        } else {
            showSnackbar(context.resources.getString(R.string.nc_common_error_sorry))
        }
    }

    private fun showSendFilesConfirmDialog() {
        if (platformPermissionUtil.isFilesPermissionGranted()) {
            val fileNamesWithLineBreaks = StringBuilder("\n")
            for (file in filesToShare!!) {
                val filename = FileUtils.getFileName(file.toUri(), context)
                fileNamesWithLineBreaks.append(filename).append("\n")
            }
            val confirmationQuestion: String = if (filesToShare!!.size == 1) {
                String.format(
                    resources!!.getString(R.string.nc_upload_confirm_send_single),
                    selectedConversation!!.displayName
                )
            } else {
                String.format(
                    resources!!.getString(R.string.nc_upload_confirm_send_multiple),
                    selectedConversation!!.displayName
                )
            }
            val dialogBuilder = MaterialAlertDialogBuilder(this)
                .setIcon(viewThemeUtils.dialog.colorMaterialAlertDialogIcon(context, R.drawable.upload))
                .setTitle(confirmationQuestion)
                .setMessage(fileNamesWithLineBreaks.toString())
                .setPositiveButton(R.string.nc_yes) { _, _ ->
                    upload()
                    openConversation()
                }
                .setNegativeButton(R.string.nc_no) { _, _ ->
                    Log.d(TAG, "sharing files aborted, going back to share-to screen")
                }

            viewThemeUtils.dialog
                .colorMaterialAlertDialogBackground(this, dialogBuilder)
            val dialog = dialogBuilder.show()
            viewThemeUtils.platform.colorTextButtons(
                dialog.getButton(AlertDialog.BUTTON_POSITIVE),
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            )
        } else {
            UploadAndShareFilesWorker.requestStoragePermission(this)
        }
    }

    private fun clearIntentAction() {
        intent.action = ""
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    private fun collectDataFromIntent() {
        filesToShare = ArrayList()
        val intentAction = intent?.action ?: return
        if (intentAction != Intent.ACTION_SEND && intentAction != Intent.ACTION_SEND_MULTIPLE) return
        try {
            val mimeType = intent.type
            if (Mimetype.TEXT_PLAIN == mimeType && intent.getStringExtra(Intent.EXTRA_TEXT) != null) {
                // Share from Google Chrome sets text/plain MIME type, but also provides a content:// URI
                // with a *screenshot* of the current page in getClipData().
                // Here we assume that when sharing a web page the user would prefer to send the URL
                // of the current page rather than a screenshot.
                textToPaste = intent.getStringExtra(Intent.EXTRA_TEXT)
            } else {
                extractFilesFromClipData()
            }
            if (filesToShare!!.isEmpty() && textToPaste!!.isEmpty()) {
                showSnackbar(context.resources.getString(R.string.nc_common_error_sorry))
                Log.e(TAG, "failed to get data from intent")
            }
        } catch (e: Exception) {
            showSnackbar(context.resources.getString(R.string.nc_common_error_sorry))
            Log.e(TAG, "Something went wrong when extracting data from intent")
        }
    }

    private fun extractFilesFromClipData() {
        val clipData = intent.clipData
        if (clipData != null) {
            for (i in 0 until clipData.itemCount) {
                val item = clipData.getItemAt(i)
                when {
                    item.uri != null -> filesToShare!!.add(item.uri.toString())
                    item.text != null -> {
                        textToPaste = item.text.toString()
                        return
                    }

                    else -> Log.w(TAG, "datatype not yet implemented for share-to")
                }
            }
        } else {
            filesToShare!!.add(intent.data.toString())
        }
    }

    private fun upload() {
        if (selectedConversation == null) {
            showSnackbar(context.resources.getString(R.string.nc_common_error_sorry))
            Log.e(TAG, "not able to upload any files because conversation was null.")
            return
        }
        try {
            filesToShare?.forEach {
                UploadAndShareFilesWorker.upload(
                    it,
                    selectedConversation!!.token,
                    selectedConversation!!.displayName,
                    null
                )
            }
        } catch (e: IllegalArgumentException) {
            showSnackbar(context.resources.getString(R.string.nc_upload_failed))
            Log.e(TAG, "Something went wrong when trying to upload file", e)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            UploadAndShareFilesWorker.REQUEST_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "upload starting after permissions were granted")
                    showSendFilesConfirmDialog()
                } else {
                    showSnackbar(context.getString(R.string.read_storage_no_permission))
                }
            }

            REQUEST_POST_NOTIFICATIONS_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Notification permission was granted")

                    if (!PowerManagerUtils().isIgnoringBatteryOptimizations() &&
                        ClosedInterfaceImpl().isGooglePlayServicesAvailable
                    ) {
                        val dialogText = String.format(
                            context.resources.getString(R.string.nc_ignore_battery_optimization_dialog_text),
                            context.resources.getString(R.string.nc_app_name)
                        )

                        val dialogBuilder = MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.nc_ignore_battery_optimization_dialog_title)
                            .setMessage(dialogText)
                            .setPositiveButton(R.string.nc_ok) { _, _ ->
                                startActivity(
                                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                )
                            }
                            .setNegativeButton(R.string.nc_common_dismiss, null)
                        viewThemeUtils.dialog.colorMaterialAlertDialogBackground(this, dialogBuilder)
                        val dialog = dialogBuilder.show()
                        viewThemeUtils.platform.colorTextButtons(
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE),
                            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                        )
                    }
                } else {
                    Log.d(
                        TAG,
                        "Notification permission is denied. Either because user denied it when being asked. " +
                            "Or permission is already denied and android decided to not offer the dialog."
                    )
                }
            }
        }
    }

    private fun shouldShowNotificationWarning(): Boolean {
        fun shouldShowWarningIfDateTooOld(date1: Long): Boolean {
            val currentTimeMillis = System.currentTimeMillis()
            val differenceMillis = currentTimeMillis - date1
            val daysForWarningInMillis = TimeUnit.DAYS.toMillis(DAYS_FOR_NOTIFICATION_WARNING)
            return differenceMillis > daysForWarningInMillis
        }

        fun shouldShowNotificationWarningByUserChoice(): Boolean {
            if (appPreferences.showRegularNotificationWarning) {
                val lastWarningDate = appPreferences.getNotificationWarningLastPostponedDate()
                return if (lastWarningDate == NOTIFICATION_WARNING_DATE_NOT_SET) {
                    true
                } else {
                    shouldShowWarningIfDateTooOld(lastWarningDate)
                }
            } else {
                return false
            }
        }

        val notificationPermissionNotGranted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !platformPermissionUtil.isPostNotificationsPermissionGranted()
        val batteryOptimizationNotIgnored = !PowerManagerUtils().isIgnoringBatteryOptimizations()

        val messagesChannelNotEnabled = !NotificationUtils.isMessagesNotificationChannelEnabled(this)
        val callsChannelNotEnabled = !NotificationUtils.isCallsNotificationChannelEnabled(this)

        val serverNotificationAppInstalled =
            currentUser?.capabilities?.notificationsCapability?.features?.isNotEmpty() == true

        val settingsOfUserAreWrong = notificationPermissionNotGranted ||
            batteryOptimizationNotIgnored ||
            messagesChannelNotEnabled ||
            callsChannelNotEnabled ||
            !serverNotificationAppInstalled

        return settingsOfUserAreWrong &&
            shouldShowNotificationWarningByUserChoice() &&
            ClosedInterfaceImpl().isGooglePlayServicesAvailable
    }

    private fun openConversation(textToPaste: String? = "") {
        if (CallActivity.active &&
            selectedConversation!!.token != ApplicationWideCurrentRoomHolder.getInstance().currentRoomToken
        ) {
            showSnackbar(context.getString(R.string.restrict_join_other_room_while_call))
            return
        }

        val bundle = Bundle()
        bundle.putString(KEY_ROOM_TOKEN, selectedConversation!!.token)
        bundle.putString(KEY_SHARED_TEXT, textToPaste)
        if (selectedMessageId != null) {
            bundle.putString(BundleKeys.KEY_MESSAGE_ID, selectedMessageId)
            selectedMessageId = null
        }

        val intent = Intent(context, ChatActivity::class.java)
        intent.putExtras(bundle)
        startActivity(intent)

        clearIntentAction()
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.BACKGROUND)
    fun onMessageEvent(eventStatus: EventStatus) {
        if (currentUser != null && eventStatus.userId == currentUser!!.id) {
            when (eventStatus.eventType) {
                EventStatus.EventType.CONVERSATION_UPDATE -> if (eventStatus.isAllGood && !isRefreshingState.value) {
                    fetchRooms()
                }

                else -> {}
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(conversationsListFetchDataEvent: ConversationsListFetchDataEvent?) {
        fetchRooms()
        conversationsListViewModel.clearSelectedConversationForOpsWithDelay(BOTTOM_SHEET_DELAY)
    }

    private fun handleConversationOpsAction(action: ConversationOpsAction, conversation: ConversationModel) {
        when (action) {
            is ConversationOpsAction.AddToFavorites -> addConversationToFavorites(conversation)
            is ConversationOpsAction.RemoveFromFavorites -> removeConversationFromFavorites(conversation)
            is ConversationOpsAction.MarkAsRead -> markConversationAsRead(conversation)
            is ConversationOpsAction.MarkAsUnread -> markConversationAsUnread(conversation)
            is ConversationOpsAction.ShareLink -> shareConversationLink(conversation)
            is ConversationOpsAction.Rename -> renameConversation(conversation)
            is ConversationOpsAction.ToggleArchive -> handleArchiving(conversation)
            is ConversationOpsAction.Leave -> leaveConversation(conversation)
            is ConversationOpsAction.Delete -> showDeleteConversationDialog(conversation)
        }
    }

    private fun shareConversationLink(conversation: ConversationModel) {
        val canGeneratePrettyURL = CapabilitiesUtil.canGeneratePrettyURL(currentUser!!)
        ShareUtils.shareConversationLink(
            this,
            currentUser?.baseUrl,
            conversation.token,
            conversation.name,
            canGeneratePrettyURL
        )
    }

    @Suppress("Detekt.TooGenericExceptionCaught", "TooGenericExceptionCaught")
    private fun handleArchiving(conversation: ConversationModel) {
        val apiVersion = ApiUtils.getConversationApiVersion(currentUser!!, intArrayOf(ApiUtils.API_V4, ApiUtils.API_V1))
        val url = ApiUtils.getUrlForArchive(apiVersion, currentUser?.baseUrl, conversation.token)
        lifecycleScope.launch {
            try {
                if (conversation.hasArchived) {
                    withContext(Dispatchers.IO) { ncApiCoroutines.unarchiveConversation(credentials!!, url) }
                    fetchRooms()
                    showSnackbar(
                        String.format(resources.getString(R.string.unarchived_conversation), conversation.displayName)
                    )
                } else {
                    withContext(Dispatchers.IO) { ncApiCoroutines.archiveConversation(credentials!!, url) }
                    fetchRooms()
                    showSnackbar(
                        String.format(resources.getString(R.string.archived_conversation), conversation.displayName)
                    )
                }
            } catch (e: Exception) {
                showSnackbar(resources.getString(R.string.nc_common_error_sorry))
            }
        }
    }

    @Suppress("Detekt.TooGenericExceptionCaught", "TooGenericExceptionCaught")
    private fun addConversationToFavorites(conversation: ConversationModel) {
        val apiVersion = ApiUtils.getConversationApiVersion(currentUser!!, intArrayOf(ApiUtils.API_V4, ApiUtils.API_V1))
        val url = ApiUtils.getUrlForRoomFavorite(apiVersion, currentUser?.baseUrl!!, conversation.token)
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { ncApiCoroutines.addConversationToFavorites(credentials!!, url) }
                fetchRooms()
                showSnackbar(
                    String.format(resources.getString(R.string.added_to_favorites), conversation.displayName)
                )
            } catch (e: Exception) {
                showSnackbar(resources.getString(R.string.nc_common_error_sorry))
            }
        }
    }

    @Suppress("Detekt.TooGenericExceptionCaught", "TooGenericExceptionCaught")
    private fun removeConversationFromFavorites(conversation: ConversationModel) {
        val apiVersion = ApiUtils.getConversationApiVersion(currentUser!!, intArrayOf(ApiUtils.API_V4, ApiUtils.API_V1))
        val url = ApiUtils.getUrlForRoomFavorite(apiVersion, currentUser?.baseUrl!!, conversation.token)
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { ncApiCoroutines.removeConversationFromFavorites(credentials!!, url) }
                fetchRooms()
                showSnackbar(
                    String.format(resources.getString(R.string.removed_from_favorites), conversation.displayName)
                )
            } catch (e: Exception) {
                showSnackbar(resources.getString(R.string.nc_common_error_sorry))
            }
        }
    }

    private fun markConversationAsUnread(conversation: ConversationModel) {
        conversationsListViewModel.markConversationAsUnread(conversation)
    }

    private fun markConversationAsRead(conversation: ConversationModel) {
        conversationsListViewModel.markConversationAsRead(conversation)
    }

    private fun renameConversation(conversation: ConversationModel) {
        if (!TextUtils.isEmpty(conversation.token)) {
            RenameConversationDialogFragment
                .newInstance(conversation.token!!, conversation.displayName!!)
                .show(supportFragmentManager, RenameConversationDialogFragment::class.simpleName)
        }
    }

    @SuppressLint("StringFormatInvalid")
    private fun leaveConversation(conversation: ConversationModel) {
        val data = Data.Builder()
            .putString(KEY_ROOM_TOKEN, conversation.token)
            .putLong(KEY_INTERNAL_USER_ID, currentUser?.id!!)
            .build()
        val worker = OneTimeWorkRequest.Builder(LeaveConversationWorker::class.java).setInputData(data).build()
        WorkManager.getInstance().enqueue(worker)
        WorkManager.getInstance(this).getWorkInfoByIdLiveData(worker.id).observeForever { workInfo ->
            when (workInfo?.state) {
                WorkInfo.State.SUCCEEDED -> {
                    showSnackbar(
                        String.format(resources.getString(R.string.left_conversation), conversation.displayName)
                    )
                    startActivity(Intent(this, MainActivity::class.java))
                }
                WorkInfo.State.FAILED -> showSnackbar(resources.getString(R.string.nc_common_error_sorry))
                else -> {}
            }
        }
    }

    fun showDeleteConversationDialog(conversation: ConversationModel) {
        val dialogBuilder = MaterialAlertDialogBuilder(this)
            .setIcon(
                viewThemeUtils.dialog
                    .colorMaterialAlertDialogIcon(context, R.drawable.ic_delete_black_24dp)
            )
            .setTitle(R.string.nc_delete_call)
            .setMessage(R.string.nc_delete_conversation_more)
            .setPositiveButton(R.string.nc_delete) { _, _ ->
                deleteConversation(conversation)
            }
            .setNegativeButton(R.string.nc_cancel) { _, _ ->
            }

        viewThemeUtils.dialog
            .colorMaterialAlertDialogBackground(this, dialogBuilder)
        val dialog = dialogBuilder.show()
        viewThemeUtils.platform.colorTextButtons(
            dialog.getButton(AlertDialog.BUTTON_POSITIVE),
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
        )
    }

    private fun showUnauthorizedDialog() {
        val dialogBuilder = MaterialAlertDialogBuilder(this)
            .setIcon(
                viewThemeUtils.dialog.colorMaterialAlertDialogIcon(
                    context,
                    R.drawable.ic_delete_black_24dp
                )
            )
            .setTitle(R.string.nc_dialog_invalid_password)
            .setMessage(R.string.nc_dialog_reauth_or_delete)
            .setCancelable(false)
            .setPositiveButton(R.string.nc_settings_remove_account) { _, _ ->
                deleteUserAndRestartApp()
            }
            .setNegativeButton(R.string.nc_settings_reauthorize) { _, _ ->
                val intent = Intent(context, BrowserLoginActivity::class.java)
                val bundle = Bundle()
                bundle.putString(BundleKeys.KEY_BASE_URL, currentUser!!.baseUrl!!)
                bundle.putBoolean(BundleKeys.KEY_REAUTHORIZE_ACCOUNT, true)
                intent.putExtras(bundle)
                startActivity(intent)
            }

        viewThemeUtils.dialog.colorMaterialAlertDialogBackground(this, dialogBuilder)
        val dialog = dialogBuilder.show()
        viewThemeUtils.platform.colorTextButtons(
            dialog.getButton(AlertDialog.BUTTON_POSITIVE),
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
        )
    }

    @SuppressLint("CheckResult")
    private fun deleteUserAndRestartApp() {
        userManager.scheduleUserForDeletionWithId(currentUser!!.id!!).blockingGet()
        val accountRemovalWork = OneTimeWorkRequest.Builder(AccountRemovalWorker::class.java).build()
        WorkManager.getInstance(applicationContext).enqueue(accountRemovalWork)

        WorkManager.getInstance(context).getWorkInfoByIdLiveData(accountRemovalWork.id)
            .observeForever { workInfo: WorkInfo? ->

                when (workInfo?.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        val text = String.format(
                            context.resources.getString(R.string.nc_deleted_user),
                            currentUser!!.displayName
                        )
                        Toast.makeText(
                            context,
                            text,
                            Toast.LENGTH_LONG
                        ).show()
                        restartApp()
                    }

                    WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                        Toast.makeText(
                            context,
                            context.resources.getString(R.string.nc_common_error_sorry),
                            Toast.LENGTH_LONG
                        ).show()
                        Log.e(TAG, "something went wrong when deleting user with id " + currentUser!!.userId)
                        restartApp()
                    }

                    else -> {}
                }
            }
    }

    private fun restartApp() {
        val intent = Intent(context, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
    }

    private fun showOutdatedClientDialog() {
        val dialogBuilder = MaterialAlertDialogBuilder(this)
            .setIcon(
                viewThemeUtils.dialog.colorMaterialAlertDialogIcon(
                    context,
                    R.drawable.ic_info_white_24dp
                )
            )
            .setTitle(R.string.nc_dialog_outdated_client)
            .setMessage(R.string.nc_dialog_outdated_client_description)
            .setCancelable(false)
            .setPositiveButton(R.string.nc_dialog_outdated_client_option_update) { _, _ ->
                try {
                    startActivity(
                        Intent(Intent.ACTION_VIEW, (CLIENT_UPGRADE_MARKET_LINK + packageName).toUri())
                    )
                } catch (e: ActivityNotFoundException) {
                    startActivity(
                        Intent(Intent.ACTION_VIEW, (CLIENT_UPGRADE_GPLAY_LINK + packageName).toUri())
                    )
                }
            }

        if (resources!!.getBoolean(R.bool.multiaccount_support) && userManager.users.blockingGet().size > 1) {
            dialogBuilder.setNegativeButton(R.string.nc_switch_account) { _, _ ->
                showChooseAccountDialog()
            }
        }

        if (resources!!.getBoolean(R.bool.multiaccount_support)) {
            dialogBuilder.setNeutralButton(R.string.nc_account_chooser_add_account) { _, _ ->
                val intent = Intent(this, ServerSelectionActivity::class.java)
                intent.putExtra(ADD_ADDITIONAL_ACCOUNT, true)
                startActivity(intent)
            }
        }

        viewThemeUtils.dialog.colorMaterialAlertDialogBackground(this, dialogBuilder)
        val dialog = dialogBuilder.show()
        viewThemeUtils.platform.colorTextButtons(
            dialog.getButton(AlertDialog.BUTTON_POSITIVE),
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE),
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
        )
    }

    private fun showServiceUnavailableDialog(httpException: HttpException) {
        if (httpException.response()?.headers()?.get(MAINTENANCE_MODE_HEADER_KEY) == "1") {
            isMaintenanceModeState.value = true
        } else {
            showErrorDialog()
        }
    }

    private fun showServerEOLDialog() {
        val dialogBuilder = MaterialAlertDialogBuilder(this)
            .setIcon(viewThemeUtils.dialog.colorMaterialAlertDialogIcon(context, R.drawable.ic_warning_white))
            .setTitle(R.string.nc_settings_server_eol_title)
            .setMessage(R.string.nc_settings_server_eol)
            .setCancelable(false)
            .setPositiveButton(R.string.nc_settings_remove_account) { _, _ ->
                deleteUserAndRestartApp()
            }

        if (resources!!.getBoolean(R.bool.multiaccount_support) && userManager.users.blockingGet().size > 1) {
            dialogBuilder.setNegativeButton(R.string.nc_switch_account) { _, _ ->
                showChooseAccountDialog()
            }
        }

        if (resources!!.getBoolean(R.bool.multiaccount_support)) {
            dialogBuilder.setNeutralButton(R.string.nc_account_chooser_add_account) { _, _ ->
                val intent = Intent(this, ServerSelectionActivity::class.java)
                intent.putExtra(ADD_ADDITIONAL_ACCOUNT, true)
                startActivity(intent)
            }
        }

        viewThemeUtils.dialog.colorMaterialAlertDialogBackground(this, dialogBuilder)
        val dialog = dialogBuilder.show()
        viewThemeUtils.platform.colorTextButtons(
            dialog.getButton(AlertDialog.BUTTON_POSITIVE),
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE),
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
        )
    }

    private fun deleteConversation(conversation: ConversationModel) {
        val data = Data.Builder()
        data.putLong(
            KEY_INTERNAL_USER_ID,
            currentUser?.id!!
        )
        data.putString(KEY_ROOM_TOKEN, conversation.token)

        val deleteConversationWorker =
            OneTimeWorkRequest.Builder(DeleteConversationWorker::class.java).setInputData(data.build()).build()
        WorkManager.getInstance().enqueue(deleteConversationWorker)

        WorkManager.getInstance(context).getWorkInfoByIdLiveData(deleteConversationWorker.id)
            .observeForever { workInfo: WorkInfo? ->
                if (workInfo != null) {
                    when (workInfo.state) {
                        WorkInfo.State.SUCCEEDED -> {
                            showSnackbar(
                                String.format(
                                    context.resources.getString(R.string.deleted_conversation),
                                    conversation.displayName
                                )
                            )
                        }

                        WorkInfo.State.FAILED -> {
                            showSnackbar(context.resources.getString(R.string.nc_common_error_sorry))
                        }

                        else -> {
                        }
                    }
                }
            }
    }

    fun openFollowedThreadsOverview() {
        val threadsUrl = ApiUtils.getUrlForSubscribedThreads(
            version = 1,
            baseUrl = currentUser!!.baseUrl
        )

        val bundle = Bundle()
        bundle.putString(ThreadsOverviewActivity.KEY_APPBAR_TITLE, getString(R.string.threads))
        bundle.putString(ThreadsOverviewActivity.KEY_THREADS_SOURCE_URL, threadsUrl)
        val threadsOverviewIntent = Intent(context, ThreadsOverviewActivity::class.java)
        threadsOverviewIntent.putExtras(bundle)
        startActivity(threadsOverviewIntent)
    }

    companion object {
        private val TAG = ConversationsListActivity::class.java.simpleName
        const val BOTTOM_SHEET_DELAY: Long = 2500
        const val SEARCH_DEBOUNCE_INTERVAL_MS = 300
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_CLIENT_UPGRADE_REQUIRED = 426
        const val CLIENT_UPGRADE_MARKET_LINK = "market://details?id="
        const val CLIENT_UPGRADE_GPLAY_LINK = "https://play.google.com/store/apps/details?id="
        const val HTTP_SERVICE_UNAVAILABLE = 503
        const val MAINTENANCE_MODE_HEADER_KEY = "X-Nextcloud-Maintenance-Mode"
        const val REQUEST_POST_NOTIFICATIONS_PERMISSION = 111
        const val DAYS_FOR_NOTIFICATION_WARNING = 5L
        const val NOTIFICATION_WARNING_DATE_NOT_SET = 0L
        const val ROOM_TYPE_ONE_ONE = "1"
        private const val NOTE_TO_SELF_SHORTCUT_ID = "NOTE_TO_SELF_SHORTCUT_ID"
        private const val KEY_ACCOUNT_DIALOG_VISIBLE = "account_dialog_visible"
    }
}
