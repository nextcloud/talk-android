/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017-2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.conversationinfo

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog as ComposeAlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import com.nextcloud.talk.utils.setExpeditedIfSupported
import androidx.work.WorkInfo
import androidx.work.WorkManager
import autodagger.AutoInjector
import com.afollestad.materialdialogs.LayoutMode.WRAP_CONTENT
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.datetime.datePicker
import com.afollestad.materialdialogs.datetime.timePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.BaseActivity
import com.nextcloud.talk.activities.MainActivity
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.chat.ChatActivity
import com.nextcloud.talk.components.ColoredStatusBar
import com.nextcloud.talk.contacts.CompanionClass.Companion.KEY_HIDE_ALREADY_EXISTING_PARTICIPANTS
import com.nextcloud.talk.contacts.ContactsActivity
import com.nextcloud.talk.conversationinfo.model.ParticipantModel
import com.nextcloud.talk.conversationinfo.ui.ConversationInfoScreen
import com.nextcloud.talk.conversationinfo.ui.ConversationInfoScreenCallbacks
import com.nextcloud.talk.conversationinfo.ui.ParticipantOpsAction
import com.nextcloud.talk.conversationinfo.viewmodel.ConversationInfoViewModel
import com.nextcloud.talk.conversationinfoedit.ConversationInfoEditActivity
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.DialogBanParticipantBinding
import com.nextcloud.talk.events.EventStatus
import com.nextcloud.talk.extensions.getParcelableArrayListExtraProvider
import com.nextcloud.talk.extensions.getParcelableExtraProvider
import com.nextcloud.talk.extensions.loadUserAvatar
import com.nextcloud.talk.jobs.AddParticipantsToConversationWorker
import com.nextcloud.talk.jobs.DeleteConversationWorker
import com.nextcloud.talk.jobs.LeaveConversationWorker
import com.nextcloud.talk.models.json.autocomplete.AutocompleteUser
import com.nextcloud.talk.models.json.conversations.ConversationEnums
import com.nextcloud.talk.models.json.converters.EnumActorTypeConverter
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.models.json.participants.Participant
import com.nextcloud.talk.models.json.participants.Participant.ActorType.CIRCLES
import com.nextcloud.talk.models.json.participants.Participant.ActorType.GROUPS
import com.nextcloud.talk.models.json.upcomingEvents.UpcomingEvent
import com.nextcloud.talk.shareditems.activities.SharedItemsActivity
import com.nextcloud.talk.threadsoverview.ThreadsOverviewActivity
import com.nextcloud.talk.ui.dialog.DialogBanListFragment
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.CapabilitiesUtil
import com.nextcloud.talk.utils.ConversationUtils
import com.nextcloud.talk.utils.DateConstants
import com.nextcloud.talk.utils.DateUtils
import com.nextcloud.talk.utils.ShareUtils
import com.nextcloud.talk.utils.ShortcutManagerHelper
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.json.JSONObject
import retrofit2.HttpException
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Calendar
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
@Suppress("LargeClass", "TooManyFunctions")
class ConversationInfoActivity : BaseActivity() {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    @Inject
    lateinit var ncApi: NcApi

    @Inject
    lateinit var dateUtils: DateUtils

    lateinit var viewModel: ConversationInfoViewModel

    private lateinit var conversationToken: String
    private var conversationUser: User? = null
    private lateinit var credentials: String

    private var startGroupChat: Boolean = false

    private var securePasswordViewState: ConversationInfoViewModel.SecurePasswordViewState
        by mutableStateOf(ConversationInfoViewModel.SecurePasswordViewState.None)
    private var showPasswordDialog by mutableStateOf(false)

    private val workerData: Data?
        get() {
            val user = conversationUser ?: return null
            return if (conversationToken.isNotEmpty()) {
                Data.Builder()
                    .putString(KEY_ROOM_TOKEN, conversationToken)
                    .putLong(BundleKeys.KEY_INTERNAL_USER_ID, user.id!!)
                    .build()
            } else {
                null
            }
        }

    private val addParticipantsResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        executeIfResultOk(result) { intent ->
            val selectedAutocompleteUsers =
                intent?.getParcelableArrayListExtraProvider<AutocompleteUser>("selectedParticipants")
                    ?: emptyList()
            val user = conversationUser ?: return@executeIfResultOk
            if (startGroupChat) {
                viewModel.createRoomFromOneToOne(
                    user,
                    viewModel.uiState.value.participants.map { it.participant },
                    selectedAutocompleteUsers,
                    conversationToken
                )
            } else {
                addParticipantsToConversation(selectedAutocompleteUsers)
            }
        }
    }

    private val editConversationResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            conversationUser?.let { viewModel.getRoom(it, conversationToken) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        conversationToken = requireNotNull(
            intent.getStringExtra(KEY_ROOM_TOKEN)
        ) { "Missing room token" }

        val upcomingEvent = intent.getParcelableExtraProvider<UpcomingEvent>(BundleKeys.KEY_UPCOMING_EVENT)
        val upcomingEventSummary = upcomingEvent?.summary
        val upcomingEventTime = upcomingEvent?.start?.let { start ->
            val startDateTime = Instant.ofEpochSecond(start).atZone(ZoneId.systemDefault())
            val currentTime = ZonedDateTime.now(ZoneId.systemDefault())
            dateUtils.getStringForMeetingStartDateTime(startDateTime, currentTime)
        }

        viewModel = ViewModelProvider(this, viewModelFactory)[ConversationInfoViewModel::class.java]

        lifecycleScope.launch {
            currentUserProvider.getCurrentUser()
                .onSuccess { user ->
                    conversationUser = user
                    credentials = ApiUtils.getCredentials(user.username, user.token)!!
                    viewModel.getRoom(user, conversationToken)
                    if (upcomingEventSummary != null || upcomingEventTime != null) {
                        viewModel.setUpcomingEvent(upcomingEventSummary, upcomingEventTime)
                    }
                }
                .onFailure {
                    Log.e(TAG, "Failed to get current user")
                    finish()
                }
        }

        viewModel.securePasswordViewState.observe(this) { securePasswordViewState = it }

        setupCompose()
    }

    override fun onStart() {
        super.onStart()
        this.lifecycle.addObserver(ConversationInfoViewModel.LifeCycleObserver)
    }

    override fun onStop() {
        super.onStop()
        this.lifecycle.removeObserver(ConversationInfoViewModel.LifeCycleObserver)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(eventStatus: EventStatus) {
        conversationUser?.let { viewModel.loadParticipants(it, conversationToken) }
    }

    private fun setupCompose() {
        val colorScheme = viewThemeUtils.getColorScheme(this)
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val snackbarHostState = remember { SnackbarHostState() }

            LaunchedEffect(Unit) {
                viewModel.uiEvent.collect { event ->
                    val user = conversationUser ?: return@collect
                    when (event) {
                        is ConversationInfoUiEvent.ShowSnackbar ->
                            snackbarHostState.showSnackbar(getString(event.resId))
                        is ConversationInfoUiEvent.ShowSnackbarText ->
                            snackbarHostState.showSnackbar(event.text)
                        is ConversationInfoUiEvent.NavigateToChat -> {
                            startActivity(
                                Intent(this@ConversationInfoActivity, ChatActivity::class.java).apply {
                                    putExtra(KEY_ROOM_TOKEN, event.token)
                                }
                            )
                        }
                        ConversationInfoUiEvent.RefreshParticipants ->
                            viewModel.loadParticipants(user, conversationToken)
                    }
                }
            }

            MaterialTheme(colorScheme = colorScheme) {
                ColoredStatusBar()
                ConversationInfoScreen(
                    state = uiState,
                    callbacks = buildCallbacks(onShowPasswordDialog = { showPasswordDialog = true })
                )
                GuestAccessPasswordDialogHost()
            }
        }
    }

    @Composable
    private fun GuestAccessPasswordDialogHost() {
        if (!showPasswordDialog) return
        GuestAccessPasswordDialog(
            validationState = securePasswordViewState,
            onPasswordChanged = ::onGuestPasswordChanged,
            onDismiss = ::dismissGuestPasswordDialog,
            onSave = ::onGuestPasswordSave
        )
    }

    private fun onGuestPasswordChanged(password: String) {
        val user = conversationUser ?: return
        val validatePasswordUrl = user.capabilities?.passwordCapability?.api?.validatePasswordApi ?: ""
        viewModel.securePassword(credentials, validatePasswordUrl, password)
    }

    private fun onGuestPasswordSave(password: String, copyAfterSave: Boolean) {
        val user = conversationUser ?: return
        if (copyAfterSave) {
            copyPasswordToClipboard(password)
        }
        val apiVersion = ApiUtils.getConversationApiVersion(user, intArrayOf(ApiUtils.API_V4, ApiUtils.API_V1))
        viewModel.setPassword(
            user = user,
            url = ApiUtils.getUrlForRoomPassword(apiVersion, user.baseUrl!!, conversationToken),
            password = password
        )
        dismissGuestPasswordDialog()
    }

    private fun dismissGuestPasswordDialog() {
        showPasswordDialog = false
        viewModel.resetSecurePasswordViewState()
    }

    private fun copyPasswordToClipboard(password: String) {
        val clipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val label = resources.getString(R.string.nc_app_product_name)
        clipboardManager.setPrimaryClip(ClipData.newPlainText(label, password))
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun buildCallbacks(onShowPasswordDialog: () -> Unit) =
        ConversationInfoScreenCallbacks(
            onNavigateBack = { onBackPressedDispatcher.onBackPressed() },
            onEditConversation = {
                editConversationResult.launch(
                    Intent(this, ConversationInfoEditActivity::class.java).apply {
                        putExtra(KEY_ROOM_TOKEN, conversationToken)
                    }
                )
            },
            onMessageNotificationLevelClick = { showNotificationLevelDialog() },
            onCallNotificationsClick = { viewModel.toggleCallNotifications() },
            onImportantConversationClick = {
                val user = conversationUser ?: return@ConversationInfoScreenCallbacks
                viewModel.toggleImportantConversation(credentials, user.baseUrl!!, conversationToken)
            },
            onSensitiveConversationClick = {
                val user = conversationUser ?: return@ConversationInfoScreenCallbacks
                viewModel.toggleSensitiveConversation(credentials, user.baseUrl!!, conversationToken)
            },
            onLobbyClick = { conversationUser?.let { viewModel.toggleLobby(it, conversationToken) } },
            onLobbyTimerClick = { showLobbyTimerDialog() },
            onAllowGuestsClick = {
                val user = conversationUser ?: return@ConversationInfoScreenCallbacks
                viewModel.allowGuests(user, conversationToken, !viewModel.uiState.value.guestsAllowed)
            },
            onPasswordProtectionClick = {
                val user = conversationUser ?: return@ConversationInfoScreenCallbacks
                if (viewModel.uiState.value.hasPassword) {
                    val apiVersion =
                        ApiUtils.getConversationApiVersion(user, intArrayOf(ApiUtils.API_V4, ApiUtils.API_V1))
                    viewModel.setPassword(
                        user = user,
                        url = ApiUtils.getUrlForRoomPassword(apiVersion, user.baseUrl!!, conversationToken),
                        password = ""
                    )
                } else {
                    onShowPasswordDialog()
                }
            },
            onResendInvitationsClick = {
                conversationUser?.let { viewModel.resendInvitations(it, conversationToken) }
            },
            onSharedItemsClick = { showSharedItems() },
            onThreadsClick = { openThreadsOverview() },
            onRecordingConsentClick = {
                conversationUser?.let { viewModel.toggleRecordingConsent(it, conversationToken) }
            },
            onMessageExpirationClick = { showMessageExpirationDialog() },
            onShareConversationClick = {
                val user = conversationUser ?: return@ConversationInfoScreenCallbacks
                val state = viewModel.uiState.value
                ShareUtils.shareConversationLink(
                    this,
                    user.baseUrl,
                    state.conversationToken,
                    CapabilitiesUtil.canGeneratePrettyURL(user)
                )
            },
            onLockConversationClick = {
                conversationUser?.let { viewModel.toggleLock(it, conversationToken) }
            },
            onParticipantClick = { model -> handleParticipantClick(model) },
            onParticipantOpsDismiss = { viewModel.setParticipantForOps(null) },
            onParticipantOpsAction = { action, model -> handleParticipantOpsAction(action, model) },
            onAddParticipantsClick = {
                startGroupChat = false
                selectParticipantsToAdd()
            },
            onStartGroupChatClick = {
                startGroupChat = true
                selectParticipantsToAdd()
            },
            onListBansClick = { listBans() },
            onArchiveClick = { conversationUser?.let { viewModel.toggleArchive(it, conversationToken) } },
            onLeaveConversationClick = { leaveConversation() },
            onClearHistoryClick = { showClearHistoryDialog() },
            onDeleteConversationClick = { showDeleteConversationDialog() }
        )

    private fun showSharedItems() {
        val conv = viewModel.uiState.value.conversation ?: return
        startActivity(
            Intent(this, SharedItemsActivity::class.java).apply {
                putExtra(BundleKeys.KEY_CONVERSATION_NAME, conv.displayName)
                putExtra(KEY_ROOM_TOKEN, conversationToken)
                putExtra(
                    SharedItemsActivity.KEY_USER_IS_OWNER_OR_MODERATOR,
                    ConversationUtils.isParticipantOwnerOrModerator(conv)
                )
                putExtra(
                    SharedItemsActivity.KEY_IS_ONE_2_ONE,
                    conv.type == ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL
                )
            }
        )
    }

    fun openThreadsOverview() {
        val user = conversationUser ?: return
        val threadsUrl = ApiUtils.getUrlForRecentThreads(
            version = 1,
            baseUrl = user.baseUrl,
            token = conversationToken
        )
        startActivity(
            Intent(context, ThreadsOverviewActivity::class.java).apply {
                putExtra(KEY_ROOM_TOKEN, conversationToken)
                putExtra(ThreadsOverviewActivity.KEY_APPBAR_TITLE, getString(R.string.recent_threads))
                putExtra(ThreadsOverviewActivity.KEY_THREADS_SOURCE_URL, threadsUrl)
            }
        )
    }

    private fun showLobbyTimerDialog() {
        val user = conversationUser ?: return
        val currentLobbyTimer = viewModel.uiState.value.conversation?.lobbyTimer ?: 0L
        MaterialDialog(this, BottomSheet(WRAP_CONTENT)).show {
            val cal = Calendar.getInstance()
            if (currentLobbyTimer != 0L) cal.timeInMillis = currentLobbyTimer * DateConstants.SECOND_DIVIDER
            datePicker { _, date ->
                showTimePicker(date) { selectedDate ->
                    viewModel.setLobbyTimerAndSubmit(
                        user,
                        conversationToken,
                        selectedDate.timeInMillis / DateConstants.SECOND_DIVIDER
                    )
                }
            }
        }
    }

    private fun showTimePicker(selectedDate: Calendar, onTimeSelected: (Calendar) -> Unit) {
        val currentTime = Calendar.getInstance()
        MaterialDialog(this, BottomSheet(WRAP_CONTENT)).show {
            cancelable(false)
            timePicker(
                currentTime = Calendar.getInstance(),
                show24HoursView = true,
                timeCallback = { _, time ->
                    selectedDate.set(Calendar.HOUR_OF_DAY, time.get(Calendar.HOUR_OF_DAY))
                    selectedDate.set(Calendar.MINUTE, time.get(Calendar.MINUTE))
                    if (selectedDate.timeInMillis < currentTime.timeInMillis) {
                        selectedDate.set(Calendar.HOUR_OF_DAY, currentTime.get(Calendar.HOUR_OF_DAY))
                        selectedDate.set(Calendar.MINUTE, currentTime.get(Calendar.MINUTE))
                    }
                    onTimeSelected(selectedDate)
                }
            )
        }
    }

    private fun showNotificationLevelDialog() {
        val descriptions = resources.getStringArray(R.array.message_notification_levels)
        val dialogBuilder = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.nc_plain_old_messages)
            .setItems(descriptions) { _, position -> viewModel.saveNotificationLevel(position) }
            .setNegativeButton(R.string.nc_cancel, null)
        viewThemeUtils.dialog.colorMaterialAlertDialogBackground(this, dialogBuilder)
        val dialog = dialogBuilder.show()
        viewThemeUtils.platform.colorTextButtons(dialog.getButton(AlertDialog.BUTTON_NEGATIVE))
    }

    private fun showMessageExpirationDialog() {
        val descriptions = resources.getStringArray(R.array.message_expiring_descriptions)
        val dialogBuilder = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.nc_expire_messages)
            .setItems(descriptions) { _, position -> viewModel.saveMessageExpiration(position) }
            .setNegativeButton(R.string.nc_cancel, null)
        viewThemeUtils.dialog.colorMaterialAlertDialogBackground(this, dialogBuilder)
        val dialog = dialogBuilder.show()
        viewThemeUtils.platform.colorTextButtons(dialog.getButton(AlertDialog.BUTTON_NEGATIVE))
    }

    private fun showDeleteConversationDialog() {
        val dialogBuilder = MaterialAlertDialogBuilder(this)
            .setIcon(viewThemeUtils.dialog.colorMaterialAlertDialogIcon(context, R.drawable.ic_delete_black_24dp))
            .setTitle(R.string.nc_delete_call)
            .setMessage(R.string.nc_delete_conversation_more)
            .setPositiveButton(R.string.nc_delete) { _, _ -> deleteConversation() }
            .setNegativeButton(R.string.nc_cancel, null)
        viewThemeUtils.dialog.colorMaterialAlertDialogBackground(this, dialogBuilder)
        val dialog = dialogBuilder.show()
        viewThemeUtils.platform.colorTextButtons(
            dialog.getButton(AlertDialog.BUTTON_POSITIVE),
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
        )
    }

    private fun listBans() {
        val transaction = supportFragmentManager.beginTransaction()
        transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
        transaction.add(android.R.id.content, DialogBanListFragment(conversationToken))
            .addToBackStack(null)
            .commit()
    }

    private fun executeIfResultOk(result: ActivityResult, onResult: (intent: Intent?) -> Unit) {
        if (result.resultCode == RESULT_OK) {
            onResult(result.data)
        } else {
            Log.e(ChatActivity.TAG, "resultCode for received intent was != ok")
        }
    }

    private fun selectParticipantsToAdd() {
        val existingParticipants = ArrayList<AutocompleteUser>()
        for (model in viewModel.uiState.value.participants) {
            existingParticipants.add(
                AutocompleteUser(
                    model.participant.calculatedActorId!!,
                    model.participant.displayName,
                    model.participant.calculatedActorType.name.lowercase()
                )
            )
        }
        addParticipantsResult.launch(
            Intent(this, ContactsActivity::class.java).apply {
                putExtra(BundleKeys.KEY_ADD_PARTICIPANTS, true)
                putParcelableArrayListExtra("selectedParticipants", existingParticipants)
                putExtra(KEY_HIDE_ALREADY_EXISTING_PARTICIPANTS, true)
                putExtra(BundleKeys.KEY_TOKEN, conversationToken)
            }
        )
    }

    private fun addParticipantsToConversation(autocompleteUsers: List<AutocompleteUser>) {
        val user = conversationUser ?: return
        val groupIds = mutableSetOf<String>()
        val emailIds = mutableSetOf<String>()
        val circleIds = mutableSetOf<String>()
        val userIds = mutableSetOf<String>()

        autocompleteUsers.forEach { participant ->
            when (participant.source) {
                GROUPS.name.lowercase() -> groupIds.add(participant.id!!)
                Participant.ActorType.EMAILS.name.lowercase() -> emailIds.add(participant.id!!)
                CIRCLES.name.lowercase() -> circleIds.add(participant.id!!)
                else -> userIds.add(participant.id!!)
            }
        }

        val data = Data.Builder()
            .putLong(BundleKeys.KEY_INTERNAL_USER_ID, user.id!!)
            .putString(BundleKeys.KEY_TOKEN, conversationToken)
            .putStringArray(BundleKeys.KEY_SELECTED_USERS, userIds.toTypedArray())
            .putStringArray(BundleKeys.KEY_SELECTED_GROUPS, groupIds.toTypedArray())
            .putStringArray(BundleKeys.KEY_SELECTED_EMAILS, emailIds.toTypedArray())
            .putStringArray(BundleKeys.KEY_SELECTED_CIRCLES, circleIds.toTypedArray())
            .build()

        val addParticipantsWorker =
            OneTimeWorkRequest.Builder(AddParticipantsToConversationWorker::class.java)
                .setInputData(data)
                .setExpeditedIfSupported()
                .build()
        WorkManager.getInstance().enqueue(addParticipantsWorker)
        WorkManager.getInstance(context).getWorkInfoByIdLiveData(addParticipantsWorker.id)
            .observeForever { workInfo: WorkInfo? ->
                if (workInfo?.state == WorkInfo.State.SUCCEEDED) {
                    viewModel.loadParticipants(user, conversationToken)
                }
            }
    }

    private fun leaveConversation() {
        workerData?.let { data ->
            val workRequest = OneTimeWorkRequest.Builder(LeaveConversationWorker::class.java)
                .setInputData(data)
                .setExpeditedIfSupported()
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "leave_conversation_work",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
            WorkManager.getInstance(context).getWorkInfoByIdLiveData(workRequest.id)
                .observe(this) { workInfo: WorkInfo? ->
                    if (workInfo != null) {
                        when (workInfo.state) {
                            WorkInfo.State.SUCCEEDED -> {
                                conversationUser?.id?.let { userId ->
                                    ShortcutManagerHelper.disableConversationShortcut(
                                        context,
                                        conversationToken,
                                        userId,
                                        context.resources.getString(R.string.nc_shortcut_conversation_deleted)
                                    )
                                }

                                startActivity(
                                    Intent(context, MainActivity::class.java).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                    }
                                )
                            }
                            WorkInfo.State.FAILED -> {
                                val errorType = workInfo.outputData.getString("error_type")
                                lifecycleScope.launch {
                                    viewModel.emitSnackbar(
                                        if (errorType ==
                                            LeaveConversationWorker.ERROR_NO_OTHER_MODERATORS_OR_OWNERS_LEFT
                                        ) {
                                            R.string.nc_last_moderator_leaving_room_warning
                                        } else {
                                            R.string.nc_common_error_sorry
                                        }
                                    )
                                }
                            }
                            else -> { /* unused */ }
                        }
                    }
                }
        }
    }

    private fun showClearHistoryDialog() {
        val dialogBuilder = MaterialAlertDialogBuilder(this)
            .setIcon(viewThemeUtils.dialog.colorMaterialAlertDialogIcon(context, R.drawable.ic_delete_black_24dp))
            .setTitle(R.string.nc_clear_history)
            .setMessage(R.string.nc_clear_history_warning)
            .setPositiveButton(R.string.nc_delete_all) { _, _ -> clearHistory() }
            .setNegativeButton(R.string.nc_cancel, null)
        viewThemeUtils.dialog.colorMaterialAlertDialogBackground(this, dialogBuilder)
        val dialog = dialogBuilder.show()
        viewThemeUtils.platform.colorTextButtons(
            dialog.getButton(AlertDialog.BUTTON_POSITIVE),
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
        )
    }

    private fun clearHistory() {
        val user = conversationUser ?: return
        val caps = viewModel.uiState.value.spreedCapabilities ?: return
        val apiVersion = ApiUtils.getChatApiVersion(caps, intArrayOf(1))
        viewModel.clearChatHistory(user, ApiUtils.getUrlForChat(apiVersion, user.baseUrl!!, conversationToken))
    }

    private fun deleteConversation() {
        workerData?.let {
            WorkManager.getInstance(context).enqueue(
                OneTimeWorkRequest.Builder(DeleteConversationWorker::class.java)
                    .setInputData(it)
                    .setExpeditedIfSupported()
                    .build()
            )

            conversationUser?.id?.let { userId ->
                ShortcutManagerHelper.disableConversationShortcut(
                    context,
                    conversationToken,
                    userId,
                    context.resources.getString(R.string.nc_shortcut_conversation_deleted)
                )
            }

            startActivity(
                Intent(context, MainActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP) }
            )
        }
    }

    private fun toggleModeratorStatus(apiVersion: Int, participant: Participant) {
        val user = conversationUser ?: return
        val subscriber = participantActionObserver()
        if (participant.type == Participant.ParticipantType.MODERATOR ||
            participant.type == Participant.ParticipantType.GUEST_MODERATOR
        ) {
            ncApi.demoteAttendeeFromModerator(
                credentials,
                ApiUtils.getUrlForRoomModerators(apiVersion, user.baseUrl!!, conversationToken),
                participant.attendeeId,
                null
            )?.subscribeOn(Schedulers.io())?.observeOn(AndroidSchedulers.mainThread())?.subscribe(subscriber)
        } else if (participant.type == Participant.ParticipantType.USER ||
            participant.type == Participant.ParticipantType.GUEST
        ) {
            ncApi.promoteAttendeeToModerator(
                credentials,
                ApiUtils.getUrlForRoomModerators(apiVersion, user.baseUrl!!, conversationToken),
                participant.attendeeId,
                null
            )?.subscribeOn(Schedulers.io())?.observeOn(AndroidSchedulers.mainThread())?.subscribe(subscriber)
        }
    }

    private fun toggleModeratorStatusLegacy(apiVersion: Int, participant: Participant) {
        val user = conversationUser ?: return
        val subscriber = participantActionObserver()
        if (participant.type == Participant.ParticipantType.MODERATOR) {
            ncApi.demoteModeratorToUser(
                credentials,
                ApiUtils.getUrlForRoomModerators(apiVersion, user.baseUrl!!, conversationToken),
                participant.userId
            )?.subscribeOn(Schedulers.io())?.observeOn(AndroidSchedulers.mainThread())?.subscribe(subscriber)
        } else if (participant.type == Participant.ParticipantType.USER) {
            ncApi.promoteUserToModerator(
                credentials,
                ApiUtils.getUrlForRoomModerators(apiVersion, user.baseUrl!!, conversationToken),
                participant.userId
            )?.subscribeOn(Schedulers.io())?.observeOn(AndroidSchedulers.mainThread())?.subscribe(subscriber)
        }
    }

    private fun participantActionObserver(): Observer<GenericOverall> =
        object : Observer<GenericOverall> {
            override fun onSubscribe(d: Disposable) { /* unused */ }
            override fun onNext(genericOverall: GenericOverall) {
                conversationUser?.let { viewModel.loadParticipants(it, conversationToken) }
            }

            @SuppressLint("LongLogTag")
            override fun onError(e: Throwable) {
                Log.e(TAG, "Error changing the participant type", e)
                showParticipantActionError(e)
            }
            override fun onComplete() { /* unused */ }
        }

    private fun removeAttendeeFromConversation(apiVersion: Int, participant: Participant) {
        val user = conversationUser ?: return
        val observer = participantActionObserver()
        if (apiVersion >= ApiUtils.API_V4) {
            ncApi.removeAttendeeFromConversation(
                credentials,
                ApiUtils.getUrlForAttendees(apiVersion, user.baseUrl!!, conversationToken),
                participant.attendeeId
            )?.subscribeOn(Schedulers.io())?.observeOn(AndroidSchedulers.mainThread())?.subscribe(observer)
        } else {
            if (participant.type == Participant.ParticipantType.GUEST ||
                participant.type == Participant.ParticipantType.USER_FOLLOWING_LINK
            ) {
                ncApi.removeParticipantFromConversation(
                    credentials,
                    ApiUtils.getUrlForRemovingParticipantFromConversation(user.baseUrl!!, conversationToken, true),
                    participant.sessionId
                )?.subscribeOn(Schedulers.io())?.observeOn(AndroidSchedulers.mainThread())?.subscribe(observer)
            } else {
                ncApi.removeParticipantFromConversation(
                    credentials,
                    ApiUtils.getUrlForRemovingParticipantFromConversation(user.baseUrl!!, conversationToken, false),
                    participant.userId
                )?.subscribeOn(Schedulers.io())?.observeOn(AndroidSchedulers.mainThread())?.subscribe(observer)
            }
        }
    }

    private fun banActor(actorType: String, actorId: String, internalNote: String) {
        conversationUser?.let { viewModel.banActor(it, conversationToken, actorType, actorId, internalNote) }
    }

    private fun handleParticipantClick(model: ParticipantModel) {
        viewModel.setParticipantForOps(model)
    }

    @Suppress("ReturnCount")
    private fun handleParticipantOpsAction(action: ParticipantOpsAction, model: ParticipantModel) {
        val state = viewModel.uiState.value
        val conv = state.conversation ?: return
        val caps = state.spreedCapabilities ?: return
        if (!ConversationUtils.canModerate(conv, caps)) return
        val user = conversationUser ?: return
        val participant = model.participant
        val apiVersion = ApiUtils.getConversationApiVersion(user, intArrayOf(ApiUtils.API_V4, 1))
        when (action) {
            ParticipantOpsAction.PromoteToModerator,
            ParticipantOpsAction.DemoteFromModerator ->
                if (apiVersion >= ApiUtils.API_V4) {
                    toggleModeratorStatus(apiVersion, participant)
                } else {
                    toggleModeratorStatusLegacy(apiVersion, participant)
                }

            ParticipantOpsAction.PromoteToOwner ->
                changeParticipantType(apiVersion, participant, promote = true, PARTICIPANT_TYPE_OWNER)

            ParticipantOpsAction.DemoteOwnerToModerator ->
                changeParticipantType(apiVersion, participant, promote = false, PARTICIPANT_TYPE_MODERATOR)

            ParticipantOpsAction.DemoteOwnerToUser ->
                changeParticipantType(apiVersion, participant, promote = false, PARTICIPANT_TYPE_USER)

            ParticipantOpsAction.RemoveFromConversation -> removeAttendeeFromConversation(apiVersion, participant)
            ParticipantOpsAction.Ban -> handleBan(participant)
        }
    }

    private fun showParticipantActionError(e: Throwable) {
        val messageRes = if (participantActionErrorReason(e) == ERROR_LAST_MODERATOR) {
            R.string.nc_last_moderator_cannot_be_demoted
        } else {
            R.string.nc_participant_type_change_failed
        }
        lifecycleScope.launch { viewModel.emitSnackbar(messageRes) }
    }

    /**
     * The moderators endpoint reports why it refused in the OCS data as
     * `{"ocs":{"data":{"error":"<reason>"}}}`.
     */
    @Suppress("Detekt.TooGenericExceptionCaught")
    private fun participantActionErrorReason(e: Throwable): String? =
        try {
            (e as? HttpException)?.response()?.errorBody()?.string()
                ?.let { JSONObject(it) }
                ?.optJSONObject("ocs")
                ?.optJSONObject("data")
                ?.optString("error")
                ?.takeIf { it.isNotEmpty() }
        } catch (exception: Exception) {
            Log.w(TAG, "Could not read the participant action error", exception)
            null
        }

    private fun changeParticipantType(
        apiVersion: Int,
        participant: Participant,
        promote: Boolean,
        participantType: Int
    ) {
        val user = conversationUser ?: return
        val url = ApiUtils.getUrlForRoomModerators(apiVersion, user.baseUrl!!, conversationToken)
        val call = if (promote) {
            ncApi.promoteAttendeeToModerator(credentials, url, participant.attendeeId, participantType)
        } else {
            ncApi.demoteAttendeeFromModerator(credentials, url, participant.attendeeId, participantType)
        }
        call?.subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(participantActionObserver())
    }

    private fun handleBan(participant: Participant) {
        val user = conversationUser ?: return
        val apiVersion = ApiUtils.getConversationApiVersion(user, intArrayOf(ApiUtils.API_V4, 1))
        val dialogBinding = DialogBanParticipantBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(this).setView(dialogBinding.root).create()
        dialogBinding.avatarImage.loadUserAvatar(
            user,
            participant.actorId!!,
            requestBigSize = true,
            ignoreCache = false
        )
        dialogBinding.displayNameText.text = participant.actorId
        dialogBinding.buttonBan.setOnClickListener {
            banActor(
                EnumActorTypeConverter().convertToString(participant.actorType!!),
                participant.actorId!!,
                dialogBinding.banParticipantEdit.text.toString()
            )
            removeAttendeeFromConversation(apiVersion, participant)
            dialog.dismiss()
        }
        dialogBinding.buttonClose.setOnClickListener { dialog.dismiss() }
        viewThemeUtils.material.colorTextInputLayout(dialogBinding.banParticipantEditLayout)
        viewThemeUtils.material.colorMaterialButtonPrimaryFilled(dialogBinding.buttonBan)
        viewThemeUtils.material.colorMaterialButtonText(dialogBinding.buttonClose)
        dialog.show()
    }

    companion object {
        private val TAG = ConversationInfoActivity::class.java.simpleName

        private const val ERROR_LAST_MODERATOR = "last-moderator"

        // Participant types the moderators endpoint accepts as a target level
        private const val PARTICIPANT_TYPE_OWNER: Int = 1
        private const val PARTICIPANT_TYPE_MODERATOR: Int = 2
        private const val PARTICIPANT_TYPE_USER: Int = 3
    }
}

@Composable
@Suppress("LongMethod")
private fun GuestAccessPasswordDialog(
    validationState: ConversationInfoViewModel.SecurePasswordViewState,
    onPasswordChanged: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (password: String, copyAfterSave: Boolean) -> Unit
) {
    var password by rememberSaveable { mutableStateOf("") }
    val secureText = stringResource(R.string.nc_password_secure)
    val warningMessage = passwordWarningMessage(validationState, secureText)
    val isPasswordValid = password.isNotBlank() && warningMessage == secureText

    ComposeAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(id = R.string.nc_guest_access_password_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        onPasswordChanged(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = {
                        Text(text = stringResource(id = R.string.nc_guest_access_password_dialog_hint))
                    },
                    supportingText = {
                        warningMessage?.let {
                            Text(
                                text = it,
                                color = if (!isPasswordValid) {
                                    colorResource(R.color.nc_darkRed)
                                } else {
                                    colorResource(R.color.nc_darkGreen)
                                }
                            )
                        }
                    }
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { onSave(password, true) },
                    enabled = isPasswordValid
                ) {
                    Text(text = stringResource(R.string.nc_copy_password))
                }
                TextButton(
                    onClick = { onSave(password, false) },
                    enabled = isPasswordValid
                ) {
                    Text(text = stringResource(R.string.save))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.nc_cancel))
            }
        }
    )
}

@Composable
private fun passwordWarningMessage(
    validationState: ConversationInfoViewModel.SecurePasswordViewState,
    secureText: String
): String? =
    when (validationState) {
        is ConversationInfoViewModel.SecurePasswordViewState.Success -> {
            validationState.result.passed?.let { passed ->
                if (passed) secureText else validationState.result.reason
            }
        }

        is ConversationInfoViewModel.SecurePasswordViewState.Error -> {
            stringResource(R.string.nc_common_error_sorry)
        }

        ConversationInfoViewModel.SecurePasswordViewState.None -> ""
    }
