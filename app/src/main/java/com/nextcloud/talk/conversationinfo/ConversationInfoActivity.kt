/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Ezhil Shanmugham <ezhil56x.contact@gmail.com>
 * SPDX-FileCopyrightText: 2021-2023 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2021-2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2021-2022 Tim Krüger <t@timkrueger.me>
 * SPDX-FileCopyrightText: 2017-2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.conversationinfo

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import autodagger.AutoInjector
import com.afollestad.materialdialogs.LayoutMode.WRAP_CONTENT
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.datetime.datePicker
import com.afollestad.materialdialogs.datetime.timePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.nextcloud.android.common.ui.theme.utils.ColorRole
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.BaseActivity
import com.nextcloud.talk.activities.MainActivity
import com.nextcloud.talk.adapters.items.ParticipantItem
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.bottomsheet.items.BasicListItemWithImage
import com.nextcloud.talk.bottomsheet.items.listItemsWithImage
import com.nextcloud.talk.contacts.ContactsActivity
import com.nextcloud.talk.conversationinfo.viewmodel.ConversationInfoViewModel
import com.nextcloud.talk.conversationinfoedit.ConversationInfoEditActivity
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.ActivityConversationInfoBinding
import com.nextcloud.talk.databinding.DialogBanParticipantBinding
import com.nextcloud.talk.events.EventStatus
import com.nextcloud.talk.extensions.loadConversationAvatar
import com.nextcloud.talk.extensions.loadNoteToSelfAvatar
import com.nextcloud.talk.extensions.loadSystemAvatar
import com.nextcloud.talk.extensions.loadUserAvatar
import com.nextcloud.talk.jobs.DeleteConversationWorker
import com.nextcloud.talk.jobs.LeaveConversationWorker
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.domain.converters.DomainEnumNotificationLevelConverter
import com.nextcloud.talk.models.json.capabilities.SpreedCapability
import com.nextcloud.talk.models.json.conversations.ConversationEnums
import com.nextcloud.talk.models.json.converters.EnumActorTypeConverter
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.models.json.participants.Participant
import com.nextcloud.talk.models.json.participants.Participant.ActorType.CIRCLES
import com.nextcloud.talk.models.json.participants.Participant.ActorType.GROUPS
import com.nextcloud.talk.models.json.participants.Participant.ActorType.USERS
import com.nextcloud.talk.models.json.participants.ParticipantsOverall
import com.nextcloud.talk.repositories.conversations.ConversationsRepository
import com.nextcloud.talk.shareditems.activities.SharedItemsActivity
import com.nextcloud.talk.ui.dialog.DialogBanListFragment
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.CapabilitiesUtil
import com.nextcloud.talk.utils.CapabilitiesUtil.hasSpreedFeatureCapability
import com.nextcloud.talk.utils.ConversationUtils
import com.nextcloud.talk.utils.DateConstants
import com.nextcloud.talk.utils.DateUtils
import com.nextcloud.talk.utils.ShareUtils
import com.nextcloud.talk.utils.SpreedFeatures
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.preferences.preferencestorage.DatabaseStorageModule
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.common.SmoothScrollLinearLayoutManager
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.Calendar
import java.util.Collections
import java.util.Locale
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class ConversationInfoActivity :
    BaseActivity(),
    FlexibleAdapter.OnItemClickListener {
    private lateinit var binding: ActivityConversationInfoBinding

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    @Inject
    lateinit var ncApi: NcApi

    @Inject
    lateinit var conversationsRepository: ConversationsRepository

    @Inject
    lateinit var dateUtils: DateUtils

    lateinit var viewModel: ConversationInfoViewModel

    private lateinit var spreedCapabilities: SpreedCapability

    private lateinit var conversationToken: String
    private lateinit var conversationUser: User
    private var hasAvatarSpacing: Boolean = false
    private lateinit var credentials: String
    private var roomDisposable: Disposable? = null
    private var participantsDisposable: Disposable? = null

    private var databaseStorageModule: DatabaseStorageModule? = null

    // private var conversation: Conversation? = null
    private var conversation: ConversationModel? = null

    private var adapter: FlexibleAdapter<ParticipantItem>? = null
    private var userItems: MutableList<ParticipantItem> = ArrayList()

    private lateinit var optionsMenu: Menu

    private val workerData: Data?
        get() {
            if (!TextUtils.isEmpty(conversationToken)) {
                val data = Data.Builder()
                data.putString(BundleKeys.KEY_ROOM_TOKEN, conversationToken)
                data.putLong(BundleKeys.KEY_INTERNAL_USER_ID, conversationUser.id!!)
                return data.build()
            }

            return null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        binding = ActivityConversationInfoBinding.inflate(layoutInflater)
        setupActionBar()
        setContentView(binding.root)
        setupSystemColors()

        viewModel =
            ViewModelProvider(this, viewModelFactory)[ConversationInfoViewModel::class.java]

        conversationUser = currentUserProvider.currentUser.blockingGet()

        conversationToken = intent.getStringExtra(BundleKeys.KEY_ROOM_TOKEN)!!
        hasAvatarSpacing = intent.getBooleanExtra(BundleKeys.KEY_ROOM_ONE_TO_ONE, false)
        credentials = ApiUtils.getCredentials(conversationUser.username, conversationUser.token)!!
    }

    override fun onStart() {
        super.onStart()
        this.lifecycle.addObserver(ConversationInfoViewModel.LifeCycleObserver)
    }

    override fun onStop() {
        super.onStop()
        this.lifecycle.removeObserver(ConversationInfoViewModel.LifeCycleObserver)
    }

    override fun onResume() {
        super.onResume()

        if (databaseStorageModule == null) {
            databaseStorageModule = DatabaseStorageModule(conversationUser, conversationToken)
        }

        binding.deleteConversationAction.setOnClickListener { showDeleteConversationDialog() }
        binding.leaveConversationAction.setOnClickListener { leaveConversation() }
        binding.clearConversationHistory.setOnClickListener { showClearHistoryDialog() }
        binding.addParticipantsAction.setOnClickListener { addParticipants() }
        binding.listBansButton.setOnClickListener { listBans() }

        viewModel.getRoom(conversationUser, conversationToken)

        themeTextViews()
        themeSwitchPreferences()

        binding.addParticipantsAction.visibility = GONE

        binding.progressBar.let { viewThemeUtils.platform.colorCircularProgressBar(it, ColorRole.PRIMARY) }
        initObservers()
    }

    private fun initObservers() {
        initViewStateObserver()

        viewModel.getCapabilitiesViewState.observe(this) { state ->
            when (state) {
                is ConversationInfoViewModel.GetCapabilitiesSuccessState -> {
                    spreedCapabilities = state.spreedCapabilities

                    handleConversation()
                }

                else -> {}
            }
        }

        viewModel.getBanActorState.observe(this) { state ->
            when (state) {
                is ConversationInfoViewModel.BanActorSuccessState -> {
                    getListOfParticipants() // Refresh the list of participants
                }

                ConversationInfoViewModel.BanActorErrorState -> {
                    Snackbar.make(binding.root, "Error banning actor", Snackbar.LENGTH_SHORT).show()
                }

                else -> {}
            }
        }

        viewModel.getConversationReadOnlyState.observe(this) { state ->
            when (state) {
                is ConversationInfoViewModel.SetConversationReadOnlyViewState.Success -> {
                }
                is ConversationInfoViewModel.SetConversationReadOnlyViewState.Error -> {
                    Snackbar.make(binding.root, R.string.conversation_read_only_failed, Snackbar.LENGTH_LONG).show()
                }
                is ConversationInfoViewModel.SetConversationReadOnlyViewState.None -> {
                }
            }
        }

        viewModel.clearChatHistoryViewState.observe(this) { uiState ->
            when (uiState) {
                is ConversationInfoViewModel.ClearChatHistoryViewState.None -> {
                }
                is ConversationInfoViewModel.ClearChatHistoryViewState.Success -> {
                    Snackbar.make(
                        binding.root,
                        context.getString(R.string.nc_clear_history_success),
                        Snackbar.LENGTH_LONG
                    ).show()
                }
                is ConversationInfoViewModel.ClearChatHistoryViewState.Error -> {
                    Snackbar.make(binding.root, R.string.nc_common_error_sorry, Snackbar.LENGTH_LONG).show()
                    Log.e(TAG, "failed to clear chat history", uiState.exception)
                }
            }
        }
    }

    private fun initViewStateObserver() {
        viewModel.viewState.observe(this) { state ->
            when (state) {
                is ConversationInfoViewModel.GetRoomSuccessState -> {
                    conversation = state.conversationModel
                    viewModel.getCapabilities(conversationUser, conversationToken, conversation!!)
                    if (ConversationUtils.isNoteToSelfConversation(conversation)) {
                        binding.shareConversationButton.visibility = GONE
                    }
                    val canGeneratePrettyURL = CapabilitiesUtil.canGeneratePrettyURL(conversationUser)
                    binding.shareConversationButton.setOnClickListener {
                        ShareUtils.shareConversationLink(
                            this,
                            conversationUser.baseUrl,
                            conversation?.token,
                            conversation?.name,
                            canGeneratePrettyURL
                        )
                    }
                }

                is ConversationInfoViewModel.GetRoomErrorState -> {
                    Snackbar.make(binding.root, R.string.nc_common_error_sorry, Snackbar.LENGTH_LONG).show()
                }

                else -> {}
            }
        }
    }

    private fun setupActionBar() {
        setSupportActionBar(binding.conversationInfoToolbar)
        binding.conversationInfoToolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setIcon(ColorDrawable(resources!!.getColor(android.R.color.transparent, null)))
        supportActionBar?.title = if (hasAvatarSpacing) {
            " " + resources!!.getString(R.string.nc_conversation_menu_conversation_info)
        } else {
            resources!!.getString(R.string.nc_conversation_menu_conversation_info)
        }
        viewThemeUtils.material.themeToolbar(binding.conversationInfoToolbar)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        optionsMenu = menu
        return true
    }

    fun showOptionsMenu() {
        if (::optionsMenu.isInitialized) {
            optionsMenu.clear()
            if (CapabilitiesUtil.hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.AVATAR)) {
                menuInflater.inflate(R.menu.menu_conversation_info, optionsMenu)
            }
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        super.onPrepareOptionsMenu(menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.edit) {
            val bundle = Bundle()
            bundle.putString(BundleKeys.KEY_ROOM_TOKEN, conversationToken)

            val intent = Intent(this, ConversationInfoEditActivity::class.java)
            intent.putExtras(bundle)
            startActivity(intent)
        }
        return true
    }

    private fun themeSwitchPreferences() {
        binding.run {
            listOf(
                binding.webinarInfoView.lobbySwitch,
                binding.notificationSettingsView.callNotificationsSwitch,
                binding.notificationSettingsView.importantConversationSwitch,
                binding.guestAccessView.allowGuestsSwitch,
                binding.guestAccessView.passwordProtectionSwitch,
                binding.recordingConsentView.recordingConsentForConversationSwitch
            ).forEach(viewThemeUtils.talk::colorSwitch)
        }
    }

    private fun themeTextViews() {
        binding.run {
            listOf(
                binding.notificationSettingsView.notificationSettingsCategory,
                binding.webinarInfoView.webinarSettingsCategory,
                binding.guestAccessView.guestAccessSettingsCategory,
                binding.sharedItemsTitle,
                binding.recordingConsentView.recordingConsentSettingsCategory,
                binding.conversationSettingsTitle,
                binding.participantsListCategory
            )
        }.forEach(viewThemeUtils.platform::colorTextView)
    }

    private fun showSharedItems() {
        val intent = Intent(this, SharedItemsActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        intent.putExtra(BundleKeys.KEY_CONVERSATION_NAME, conversation?.displayName)
        intent.putExtra(BundleKeys.KEY_ROOM_TOKEN, conversationToken)
        intent.putExtra(
            SharedItemsActivity.KEY_USER_IS_OWNER_OR_MODERATOR,
            ConversationUtils.isParticipantOwnerOrModerator(conversation!!)
        )
        startActivity(intent)
    }

    private fun setupWebinaryView() {
        if (CapabilitiesUtil.hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.WEBINARY_LOBBY) &&
            webinaryRoomType(conversation!!) &&
            ConversationUtils.canModerate(conversation!!, spreedCapabilities)
        ) {
            binding.webinarInfoView.webinarSettings.visibility = VISIBLE

            val isLobbyOpenToModeratorsOnly =
                conversation!!.lobbyState == ConversationEnums.LobbyState.LOBBY_STATE_MODERATORS_ONLY
            binding.webinarInfoView.lobbySwitch.isChecked = isLobbyOpenToModeratorsOnly

            reconfigureLobbyTimerView()

            binding.webinarInfoView.startTimeButton.setOnClickListener {
                MaterialDialog(this, BottomSheet(WRAP_CONTENT)).show {
                    val currentTimeCalendar = Calendar.getInstance()
                    if (conversation!!.lobbyTimer != 0L) {
                        currentTimeCalendar.timeInMillis = conversation!!.lobbyTimer * DateConstants.SECOND_DIVIDER
                    }

                    datePicker { _, date ->
                        showTimePicker(date)
                    }
                }
            }

            binding.webinarInfoView.webinarSettingsLobby.setOnClickListener {
                binding.webinarInfoView.lobbySwitch.isChecked = !binding.webinarInfoView.lobbySwitch.isChecked
                reconfigureLobbyTimerView()
                submitLobbyChanges()
            }
        } else {
            binding.webinarInfoView.webinarSettings.visibility = GONE
        }
    }

    private fun showTimePicker(selectedDate: Calendar) {
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
                    reconfigureLobbyTimerView(selectedDate)
                    submitLobbyChanges()
                }
            )
        }
    }

    private fun webinaryRoomType(conversation: ConversationModel): Boolean =
        conversation.type == ConversationEnums.ConversationType.ROOM_GROUP_CALL ||
            conversation.type == ConversationEnums.ConversationType.ROOM_PUBLIC_CALL

    private fun reconfigureLobbyTimerView(dateTime: Calendar? = null) {
        val isChecked = binding.webinarInfoView.lobbySwitch.isChecked

        if (dateTime != null && isChecked) {
            conversation!!.lobbyTimer = (
                dateTime.timeInMillis - (dateTime.time.seconds * DateConstants.SECOND_DIVIDER)
                ) / DateConstants.SECOND_DIVIDER
        } else if (!isChecked) {
            conversation!!.lobbyTimer = 0
        }

        conversation!!.lobbyState = if (isChecked) {
            ConversationEnums.LobbyState.LOBBY_STATE_MODERATORS_ONLY
        } else {
            ConversationEnums.LobbyState.LOBBY_STATE_ALL_PARTICIPANTS
        }

        if (
            conversation!!.lobbyTimer != null &&
            conversation!!.lobbyTimer != Long.MIN_VALUE &&
            conversation!!.lobbyTimer != 0L
        ) {
            binding.webinarInfoView.startTimeButtonSummary.text = (
                dateUtils.getLocalDateTimeStringFromTimestamp(
                    conversation!!.lobbyTimer!! * DateConstants.SECOND_DIVIDER
                )
                )
        } else {
            binding.webinarInfoView.startTimeButtonSummary.setText(R.string.nc_manual)
        }

        if (isChecked) {
            binding.webinarInfoView.startTimeButton.visibility = VISIBLE
        } else {
            binding.webinarInfoView.startTimeButton.visibility = GONE
        }
    }

    private fun submitLobbyChanges() {
        val state = if (binding.webinarInfoView.lobbySwitch.isChecked) {
            1
        } else {
            0
        }

        val apiVersion = ApiUtils.getConversationApiVersion(conversationUser, intArrayOf(ApiUtils.API_V4, 1))

        ncApi.setLobbyForConversation(
            ApiUtils.getCredentials(conversationUser.username, conversationUser.token),
            ApiUtils.getUrlForRoomWebinaryLobby(apiVersion, conversationUser.baseUrl!!, conversation!!.token),
            state,
            conversation!!.lobbyTimer
        )
            ?.subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(object : Observer<GenericOverall> {
                override fun onComplete() {
                    // unused atm
                }

                override fun onSubscribe(d: Disposable) {
                    // unused atm
                }

                override fun onNext(t: GenericOverall) {
                    // unused atm
                }

                override fun onError(e: Throwable) {
                    Log.e(TAG, "Failed to setLobbyForConversation", e)
                    Snackbar.make(binding.root, R.string.nc_common_error_sorry, Snackbar.LENGTH_LONG).show()
                }
            })
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(eventStatus: EventStatus) {
        getListOfParticipants()
    }

    private fun showDeleteConversationDialog() {
        binding.conversationInfoName.let {
            val dialogBuilder = MaterialAlertDialogBuilder(it.context)
                .setIcon(
                    viewThemeUtils.dialog.colorMaterialAlertDialogIcon(
                        context,
                        R.drawable.ic_delete_black_24dp
                    )
                )
                .setTitle(R.string.nc_delete_call)
                .setMessage(R.string.nc_delete_conversation_more)
                .setPositiveButton(R.string.nc_delete) { _, _ ->
                    deleteConversation()
                }
                .setNegativeButton(R.string.nc_cancel) { _, _ ->
                    // unused atm
                }

            viewThemeUtils.dialog
                .colorMaterialAlertDialogBackground(it.context, dialogBuilder)
            val dialog = dialogBuilder.show()
            viewThemeUtils.platform.colorTextButtons(
                dialog.getButton(AlertDialog.BUTTON_POSITIVE),
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            )
        }
    }

    private fun setupAdapter() {
        if (adapter == null) {
            adapter = FlexibleAdapter(userItems, this, true)
        }

        val layoutManager = SmoothScrollLinearLayoutManager(this)
        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.setHasFixedSize(false)
        binding.recyclerView.adapter = adapter
        binding.recyclerView.isNestedScrollingEnabled = false
        adapter!!.addListener(this)
    }

    private fun handleParticipants(participants: List<Participant>) {
        var userItem: ParticipantItem
        var participant: Participant

        userItems = ArrayList()
        var ownUserItem: ParticipantItem? = null

        for (i in participants.indices) {
            participant = participants[i]
            userItem = ParticipantItem(this, participant, conversationUser, viewThemeUtils, conversation!!)
            if (participant.sessionId != null) {
                userItem.isOnline = !participant.sessionId.equals("0")
            } else {
                userItem.isOnline = participant.sessionIds.isNotEmpty()
            }

            if (participant.calculatedActorType == USERS &&
                participant.calculatedActorId == conversationUser.userId
            ) {
                ownUserItem = userItem
                ownUserItem.model.sessionId = "-1"
                ownUserItem.isOnline = true
            } else {
                userItems.add(userItem)
            }
        }

        Collections.sort(userItems, ParticipantItemComparator())

        if (ownUserItem != null) {
            userItems.add(0, ownUserItem)
        }

        setupAdapter()

        binding.participants.visibility = VISIBLE
        adapter!!.updateDataSet(userItems)
    }

    private fun getListOfParticipants() {
        // FIXME Fix API checking with guests?
        val apiVersion: Int = ApiUtils.getConversationApiVersion(conversationUser, intArrayOf(ApiUtils.API_V4, 1))

        val fieldMap = HashMap<String, Boolean>()
        fieldMap["includeStatus"] = true

        ncApi.getPeersForCall(
            credentials,
            ApiUtils.getUrlForParticipants(
                apiVersion,
                conversationUser.baseUrl!!,
                conversationToken
            ),
            fieldMap
        )
            ?.subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(object : Observer<ParticipantsOverall> {
                override fun onSubscribe(d: Disposable) {
                    participantsDisposable = d
                }

                @Suppress("Detekt.TooGenericExceptionCaught")
                override fun onNext(participantsOverall: ParticipantsOverall) {
                    handleParticipants(participantsOverall.ocs!!.data!!)
                }

                override fun onError(e: Throwable) {
                    // unused atm
                }

                override fun onComplete() {
                    participantsDisposable!!.dispose()
                }
            })
    }

    private fun listBans() {
        val fragmentManager = supportFragmentManager
        val newFragment = DialogBanListFragment(conversationToken)
        val transaction = fragmentManager.beginTransaction()
        transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
        transaction
            .add(android.R.id.content, newFragment)
            .addToBackStack(null)
            .commit()
    }

    private fun addParticipants() {
        val bundle = Bundle()
        val existingParticipantsId = arrayListOf<String>()

        for (userItem in userItems) {
            if (userItem.model.calculatedActorType == USERS) {
                existingParticipantsId.add(userItem.model.calculatedActorId!!)
            }
        }

        bundle.putBoolean(BundleKeys.KEY_ADD_PARTICIPANTS, true)
        bundle.putStringArrayList(BundleKeys.KEY_EXISTING_PARTICIPANTS, existingParticipantsId)
        bundle.putString(BundleKeys.KEY_TOKEN, conversation!!.token)

        val intent = Intent(this, ContactsActivity::class.java)
        intent.putExtras(bundle)
        startActivity(intent)
    }

    private fun leaveConversation() {
        workerData?.let { data ->
            val workRequest = OneTimeWorkRequest.Builder(LeaveConversationWorker::class.java)
                .setInputData(data)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "leave_conversation_work",
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )

            WorkManager.getInstance(context).getWorkInfoByIdLiveData(workRequest.id)
                .observe(this, { workInfo: WorkInfo? ->
                    if (workInfo != null) {
                        when (workInfo.state) {
                            WorkInfo.State.SUCCEEDED -> {
                                val intent = Intent(context, MainActivity::class.java)
                                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                startActivity(intent)
                            }
                            WorkInfo.State.FAILED -> {
                                val errorType = workInfo.outputData.getString("error_type")
                                if (errorType == LeaveConversationWorker.ERROR_NO_OTHER_MODERATORS_OR_OWNERS_LEFT) {
                                    Snackbar.make(
                                        binding.root,
                                        R.string.nc_last_moderator_leaving_room_warning,
                                        Snackbar.LENGTH_LONG
                                    ).show()
                                } else {
                                    Snackbar.make(
                                        binding.root,
                                        R.string.nc_common_error_sorry,
                                        Snackbar.LENGTH_LONG
                                    ).show()
                                }
                            }
                            else -> {
                            }
                        }
                    }
                })
        }
    }

    private fun showClearHistoryDialog() {
        binding.conversationInfoName.context.let {
            val dialogBuilder = MaterialAlertDialogBuilder(it)
                .setIcon(
                    viewThemeUtils.dialog.colorMaterialAlertDialogIcon(
                        context,
                        R.drawable.ic_delete_black_24dp
                    )
                )
                .setTitle(R.string.nc_clear_history)
                .setMessage(R.string.nc_clear_history_warning)
                .setPositiveButton(R.string.nc_delete_all) { _, _ ->
                    clearHistory()
                }
                .setNegativeButton(R.string.nc_cancel) { _, _ ->
                    // unused atm
                }

            viewThemeUtils.dialog
                .colorMaterialAlertDialogBackground(it, dialogBuilder)
            val dialog = dialogBuilder.show()
            viewThemeUtils.platform.colorTextButtons(
                dialog.getButton(AlertDialog.BUTTON_POSITIVE),
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            )
        }
    }

    private fun clearHistory() {
        val apiVersion = ApiUtils.getChatApiVersion(spreedCapabilities, intArrayOf(1))
        viewModel.clearChatHistory(apiVersion, conversationToken)
    }

    private fun deleteConversation() {
        workerData?.let {
            WorkManager.getInstance(context).enqueue(
                OneTimeWorkRequest.Builder(
                    DeleteConversationWorker::class.java
                ).setInputData(it).build()
            )
            val intent = Intent(context, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
        }
    }

    @Suppress("LongMethod")
    private fun handleConversation() {
        val conversationCopy = conversation!!

        setUpNotificationSettings(databaseStorageModule!!)

        if (CapabilitiesUtil.hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.RICH_OBJECT_LIST_MEDIA) &&
            conversationCopy.remoteServer.isNullOrEmpty()
        ) {
            binding.sharedItemsButton.setOnClickListener { showSharedItems() }
        } else {
            binding.sharedItems.visibility = GONE
        }

        if (ConversationUtils.canModerate(conversationCopy, spreedCapabilities)) {
            binding.addParticipantsAction.visibility = VISIBLE
            if (CapabilitiesUtil.hasSpreedFeatureCapability(
                    spreedCapabilities,
                    SpreedFeatures.CLEAR_HISTORY
                )
            ) {
                binding.clearConversationHistory.visibility = VISIBLE
            } else {
                binding.clearConversationHistory.visibility = GONE
            }
            showOptionsMenu()
        } else {
            binding.addParticipantsAction.visibility = GONE

            if (ConversationUtils.isNoteToSelfConversation(conversation)) {
                binding.notificationSettingsView.notificationSettings.visibility = VISIBLE
            } else {
                binding.clearConversationHistory.visibility = GONE
            }
        }

        if (!hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.ARCHIVE_CONVERSATIONS)) {
            binding.archiveConversationBtn.visibility = GONE
            binding.archiveConversationTextHint.visibility = GONE
        }

        binding.archiveConversationBtn.setOnClickListener {
            this.lifecycleScope.launch {
                if (conversation!!.hasArchived) {
                    viewModel.unarchiveConversation(conversationUser, conversationToken)
                    binding.archiveConversationIcon
                        .setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.outline_archive_24, null))
                    binding.archiveConversationText.text = resources.getString(R.string.archive_conversation)
                    binding.archiveConversationTextHint.text = resources.getString(R.string.archive_hint)
                } else {
                    viewModel.archiveConversation(conversationUser, conversationToken)
                    binding.archiveConversationIcon
                        .setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.ic_eye, null))
                    binding.archiveConversationText.text = resources.getString(R.string.unarchive_conversation)
                    binding.archiveConversationTextHint.text = resources.getString(R.string.unarchive_hint)
                }
            }
        }

        if (conversation!!.hasArchived) {
            binding.archiveConversationIcon
                .setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.ic_eye, null))
            binding.archiveConversationText.text = resources.getString(R.string.unarchive_conversation)
            binding.archiveConversationTextHint.text = resources.getString(R.string.unarchive_hint)
        } else {
            binding.archiveConversationIcon
                .setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.outline_archive_24, null))
            binding.archiveConversationText.text = resources.getString(R.string.archive_conversation)
            binding.archiveConversationTextHint.text = resources.getString(R.string.archive_hint)
        }
        if (ConversationUtils.isConversationReadOnlyAvailable(conversationCopy, spreedCapabilities)) {
            binding.lockConversation.visibility = VISIBLE
            binding.lockConversationSwitch.isChecked = databaseStorageModule!!.getBoolean("lock_switch", false)

            binding.lockConversation.setOnClickListener {
                val isLocked = binding.lockConversationSwitch.isChecked
                binding.lockConversationSwitch.isChecked = !isLocked
                lifecycleScope.launch {
                    databaseStorageModule!!.saveBoolean("lock_switch", !isLocked)
                }
                val state = if (isLocked) 0 else 1
                makeConversationReadOnly(conversationToken, state)
            }
        } else {
            binding.lockConversation.visibility = GONE
        }

        if (!isDestroyed) {
            binding.dangerZoneOptions.visibility = VISIBLE

            setupWebinaryView()

            if (!conversation!!.canLeaveConversation) {
                binding.leaveConversationAction.visibility = GONE
            } else {
                binding.leaveConversationAction.visibility = VISIBLE
            }

            if (!conversation!!.canDeleteConversation) {
                binding.deleteConversationAction.visibility = GONE
            } else {
                binding.deleteConversationAction.visibility = VISIBLE
            }

            if (ConversationEnums.ConversationType.ROOM_SYSTEM == conversation!!.type) {
                binding.notificationSettingsView.callNotificationsSwitch.visibility = GONE
            }

            binding.listBansButton.visibility =
                if (ConversationUtils.canModerate(conversationCopy, spreedCapabilities) &&
                    ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL != conversation!!.type
                ) {
                    VISIBLE
                } else {
                    GONE
                }

            if (conversation!!.notificationCalls === null) {
                binding.notificationSettingsView.callNotificationsSwitch.visibility = GONE
            } else {
                binding.notificationSettingsView.callNotificationsSwitch.isChecked =
                    (conversationCopy.notificationCalls == 1)
            }

            getListOfParticipants()

            binding.progressBar.visibility = GONE

            binding.conversationInfoName.visibility = VISIBLE

            binding.displayNameText.text = conversation!!.displayName

            if (conversation!!.description != null && conversation!!.description!!.isNotEmpty()) {
                binding.descriptionText.text = conversation!!.description
                binding.conversationDescription.visibility = VISIBLE
            }

            loadConversationAvatar()
            adjustNotificationLevelUI()
            initRecordingConsentOption()
            initExpiringMessageOption()

            binding.let {
                GuestAccessHelper(
                    this@ConversationInfoActivity,
                    it,
                    conversation!!,
                    spreedCapabilities,
                    conversationUser,
                    viewModel,
                    this
                ).setupGuestAccess()
            }
            if (ConversationUtils.isNoteToSelfConversation(conversation!!)) {
                binding.notificationSettingsView.notificationSettings.visibility = GONE
            } else {
                binding.notificationSettingsView.notificationSettings.visibility = VISIBLE
            }
        }
    }

    private fun makeConversationReadOnly(roomToken: String, state: Int) {
        viewModel.setConversationReadOnly(roomToken, state)
    }

    private fun initRecordingConsentOption() {
        fun hide() {
            binding.recordingConsentView.recordingConsentSettingsCategory.visibility = GONE
            binding.recordingConsentView.recordingConsentForConversation.visibility = GONE
            binding.recordingConsentView.recordingConsentAll.visibility = GONE
        }

        fun showAlwaysRequiredInfo() {
            binding.recordingConsentView.recordingConsentForConversation.visibility = GONE
            binding.recordingConsentView.recordingConsentAll.visibility = VISIBLE
        }

        fun showSwitch() {
            binding.recordingConsentView.recordingConsentForConversation.visibility = VISIBLE
            binding.recordingConsentView.recordingConsentAll.visibility = GONE

            if (conversation!!.hasCall) {
                binding.recordingConsentView.recordingConsentForConversation.isEnabled = false
                binding.recordingConsentView.recordingConsentForConversation.alpha = LOW_EMPHASIS_OPACITY
            } else {
                binding.recordingConsentView.recordingConsentForConversationSwitch.isChecked =
                    conversation!!.recordingConsentRequired == RECORDING_CONSENT_REQUIRED_FOR_CONVERSATION

                binding.recordingConsentView.recordingConsentForConversation.setOnClickListener {
                    binding.recordingConsentView.recordingConsentForConversationSwitch.isChecked =
                        !binding.recordingConsentView.recordingConsentForConversationSwitch.isChecked
                    submitRecordingConsentChanges()
                }
            }
        }

        if (ConversationUtils.isParticipantOwnerOrModerator(conversation!!) &&
            !ConversationUtils.isNoteToSelfConversation(conversation!!)
        ) {
            when (CapabilitiesUtil.getRecordingConsentType(spreedCapabilities)) {
                CapabilitiesUtil.RECORDING_CONSENT_NOT_REQUIRED -> hide()
                CapabilitiesUtil.RECORDING_CONSENT_REQUIRED -> showAlwaysRequiredInfo()
                CapabilitiesUtil.RECORDING_CONSENT_DEPEND_ON_CONVERSATION -> showSwitch()
            }
        } else {
            hide()
        }
    }

    private fun submitRecordingConsentChanges() {
        val state = if (binding.recordingConsentView.recordingConsentForConversationSwitch.isChecked) {
            RECORDING_CONSENT_REQUIRED_FOR_CONVERSATION
        } else {
            RECORDING_CONSENT_NOT_REQUIRED_FOR_CONVERSATION
        }

        val apiVersion = ApiUtils.getConversationApiVersion(conversationUser, intArrayOf(ApiUtils.API_V4, 1))

        ncApi.setRecordingConsent(
            ApiUtils.getCredentials(conversationUser.username, conversationUser.token),
            ApiUtils.getUrlForRecordingConsent(apiVersion, conversationUser.baseUrl!!, conversation!!.token),
            state
        )
            ?.subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(object : Observer<GenericOverall> {
                override fun onComplete() {
                    // unused atm
                }

                override fun onSubscribe(d: Disposable) {
                    // unused atm
                }

                override fun onNext(t: GenericOverall) {
                    // unused atm
                }

                override fun onError(e: Throwable) {
                    Snackbar.make(binding.root, R.string.nc_common_error_sorry, Snackbar.LENGTH_LONG).show()
                    Log.e(TAG, "Error when setting recording consent option for conversation", e)
                }
            })
    }

    private fun initExpiringMessageOption() {
        if (ConversationUtils.isParticipantOwnerOrModerator(conversation!!) &&
            CapabilitiesUtil.hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.MESSAGE_EXPIRATION)
        ) {
            databaseStorageModule?.setMessageExpiration(conversation!!.messageExpiration)
            val value = databaseStorageModule!!.getString("conversation_settings_dropdown", "")
            val pos = resources.getStringArray(R.array.message_expiring_values).indexOf(value)
            val text = resources.getStringArray(R.array.message_expiring_descriptions)[pos]
            binding.conversationSettingsDropdown.setText(text)
            binding.conversationSettingsDropdown
                .setSimpleItems(resources.getStringArray(R.array.message_expiring_descriptions))
            binding.conversationSettingsDropdown.setOnItemClickListener { _, _, position, _ ->
                val v: String = resources.getStringArray(R.array.message_expiring_values)[position]
                lifecycleScope.launch {
                    databaseStorageModule!!.saveString("conversation_settings_dropdown", v)
                }
            }
            binding.messageExpirationSettings.visibility = VISIBLE
        } else {
            binding.messageExpirationSettings.visibility = GONE
        }
    }

    private fun adjustNotificationLevelUI() {
        if (conversation != null) {
            if (CapabilitiesUtil.hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.NOTIFICATION_LEVELS)) {
                binding.notificationSettingsView.conversationInfoMessageNotificationsDropdown.isEnabled = true
                binding.notificationSettingsView.conversationInfoMessageNotificationsDropdown.alpha = 1.0f

                if (conversation!!.notificationLevel != ConversationEnums.NotificationLevel.DEFAULT) {
                    val stringValue: String =
                        when (
                            DomainEnumNotificationLevelConverter()
                                .convertToInt(conversation!!.notificationLevel!!)
                        ) {
                            NOTIFICATION_LEVEL_ALWAYS -> resources.getString(R.string.nc_notify_me_always)
                            NOTIFICATION_LEVEL_MENTION -> resources.getString(R.string.nc_notify_me_mention)
                            NOTIFICATION_LEVEL_NEVER -> resources.getString(R.string.nc_notify_me_never)
                            else -> resources.getString(R.string.nc_notify_me_mention)
                        }

                    binding.notificationSettingsView.conversationInfoMessageNotificationsDropdown.setText(
                        stringValue
                    )
                } else {
                    setProperNotificationValue(conversation)
                }
            } else {
                binding.notificationSettingsView.conversationInfoMessageNotificationsDropdown.isEnabled = false
                binding.notificationSettingsView.conversationInfoMessageNotificationsDropdown.alpha =
                    LOW_EMPHASIS_OPACITY
                setProperNotificationValue(conversation)
            }
            binding.notificationSettingsView.conversationInfoMessageNotificationsDropdown
                .setSimpleItems(resources.getStringArray(R.array.message_notification_levels))
        }
    }

    private fun setProperNotificationValue(conversation: ConversationModel?) {
        if (conversation!!.type == ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL) {
            if (CapabilitiesUtil.hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.MENTION_FLAG)) {
                binding.notificationSettingsView.conversationInfoMessageNotificationsDropdown.setText(
                    resources.getString(R.string.nc_notify_me_always)
                )
            } else {
                binding.notificationSettingsView.conversationInfoMessageNotificationsDropdown.setText(
                    resources.getString(R.string.nc_notify_me_mention)
                )
            }
        } else {
            binding.notificationSettingsView.conversationInfoMessageNotificationsDropdown.setText(
                resources.getString(R.string.nc_notify_me_mention)
            )
        }
    }

    private fun loadConversationAvatar() {
        when (conversation!!.type) {
            ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL -> if (!TextUtils.isEmpty(
                    conversation!!.name
                )
            ) {
                conversation!!.name?.let {
                    binding.avatarImage.loadUserAvatar(
                        conversationUser,
                        it,
                        true,
                        false
                    )
                }
            }

            ConversationEnums.ConversationType.ROOM_GROUP_CALL, ConversationEnums.ConversationType.ROOM_PUBLIC_CALL -> {
                binding.avatarImage.loadConversationAvatar(
                    conversationUser,
                    conversation!!,
                    false,
                    viewThemeUtils
                )
            }

            ConversationEnums.ConversationType.ROOM_SYSTEM -> {
                binding.avatarImage.loadSystemAvatar()
            }

            ConversationEnums.ConversationType.NOTE_TO_SELF -> {
                binding.avatarImage.loadNoteToSelfAvatar()
            }

            else -> {
                // unused atm
            }
        }
    }

    private fun toggleModeratorStatus(apiVersion: Int, participant: Participant) {
        val subscriber = object : Observer<GenericOverall> {
            override fun onSubscribe(d: Disposable) {
                // unused atm
            }

            override fun onNext(genericOverall: GenericOverall) {
                getListOfParticipants()
            }

            @SuppressLint("LongLogTag")
            override fun onError(e: Throwable) {
                Log.e(TAG, "Error toggling moderator status", e)
            }

            override fun onComplete() {
                // unused atm
            }
        }

        if (participant.type == Participant.ParticipantType.MODERATOR ||
            participant.type == Participant.ParticipantType.GUEST_MODERATOR
        ) {
            ncApi.demoteAttendeeFromModerator(
                credentials,
                ApiUtils.getUrlForRoomModerators(
                    apiVersion,
                    conversationUser.baseUrl!!,
                    conversation!!.token
                ),
                participant.attendeeId
            )
                ?.subscribeOn(Schedulers.io())
                ?.observeOn(AndroidSchedulers.mainThread())
                ?.subscribe(subscriber)
        } else if (participant.type == Participant.ParticipantType.USER ||
            participant.type == Participant.ParticipantType.GUEST
        ) {
            ncApi.promoteAttendeeToModerator(
                credentials,
                ApiUtils.getUrlForRoomModerators(
                    apiVersion,
                    conversationUser.baseUrl!!,
                    conversation!!.token
                ),
                participant.attendeeId
            )
                ?.subscribeOn(Schedulers.io())
                ?.observeOn(AndroidSchedulers.mainThread())
                ?.subscribe(subscriber)
        }
    }

    private fun toggleModeratorStatusLegacy(apiVersion: Int, participant: Participant) {
        val subscriber = object : Observer<GenericOverall> {
            override fun onSubscribe(d: Disposable) {
                // unused atm
            }

            override fun onNext(genericOverall: GenericOverall) {
                getListOfParticipants()
            }

            @SuppressLint("LongLogTag")
            override fun onError(e: Throwable) {
                Log.e(TAG, "Error toggling moderator status (legacy)", e)
            }

            override fun onComplete() {
                // unused atm
            }
        }

        if (participant.type == Participant.ParticipantType.MODERATOR) {
            ncApi.demoteModeratorToUser(
                credentials,
                ApiUtils.getUrlForRoomModerators(
                    apiVersion,
                    conversationUser.baseUrl!!,
                    conversation!!.token
                ),
                participant.userId
            )
                ?.subscribeOn(Schedulers.io())
                ?.observeOn(AndroidSchedulers.mainThread())
                ?.subscribe(subscriber)
        } else if (participant.type == Participant.ParticipantType.USER) {
            ncApi.promoteUserToModerator(
                credentials,
                ApiUtils.getUrlForRoomModerators(
                    apiVersion,
                    conversationUser.baseUrl!!,
                    conversation!!.token
                ),
                participant.userId
            )
                ?.subscribeOn(Schedulers.io())
                ?.observeOn(AndroidSchedulers.mainThread())
                ?.subscribe(subscriber)
        }
    }

    private fun banActor(actorType: String, actorId: String, internalNote: String) {
        viewModel.banActor(conversationUser, conversationToken, actorType, actorId, internalNote)
    }

    private fun removeAttendeeFromConversation(apiVersion: Int, participant: Participant) {
        if (apiVersion >= ApiUtils.API_V4) {
            ncApi.removeAttendeeFromConversation(
                credentials,
                ApiUtils.getUrlForAttendees(
                    apiVersion,
                    conversationUser.baseUrl!!,
                    conversation!!.token
                ),
                participant.attendeeId
            )
                ?.subscribeOn(Schedulers.io())
                ?.observeOn(AndroidSchedulers.mainThread())
                ?.subscribe(object : Observer<GenericOverall> {
                    override fun onSubscribe(d: Disposable) {
                        // unused atm
                    }

                    override fun onNext(genericOverall: GenericOverall) {
                        getListOfParticipants()
                    }

                    @SuppressLint("LongLogTag")
                    override fun onError(e: Throwable) {
                        Log.e(TAG, "Error removing attendee from conversation", e)
                    }

                    override fun onComplete() {
                        // unused atm
                    }
                })
        } else {
            if (participant.type == Participant.ParticipantType.GUEST ||
                participant.type == Participant.ParticipantType.USER_FOLLOWING_LINK
            ) {
                ncApi.removeParticipantFromConversation(
                    credentials,
                    ApiUtils.getUrlForRemovingParticipantFromConversation(
                        conversationUser.baseUrl!!,
                        conversation!!.token,
                        true
                    ),
                    participant.sessionId
                )
                    ?.subscribeOn(Schedulers.io())
                    ?.observeOn(AndroidSchedulers.mainThread())
                    ?.subscribe(object : Observer<GenericOverall> {
                        override fun onSubscribe(d: Disposable) {
                            // unused atm
                        }

                        override fun onNext(genericOverall: GenericOverall) {
                            getListOfParticipants()
                        }

                        @SuppressLint("LongLogTag")
                        override fun onError(e: Throwable) {
                            Log.e(TAG, "Error removing guest from conversation", e)
                        }

                        override fun onComplete() {
                            // unused atm
                        }
                    })
            } else {
                ncApi.removeParticipantFromConversation(
                    credentials,
                    ApiUtils.getUrlForRemovingParticipantFromConversation(
                        conversationUser.baseUrl!!,
                        conversation!!.token,
                        false
                    ),
                    participant.userId
                )
                    ?.subscribeOn(Schedulers.io())
                    ?.observeOn(AndroidSchedulers.mainThread())
                    ?.subscribe(object : Observer<GenericOverall> {
                        override fun onSubscribe(d: Disposable) {
                            // unused atm
                        }

                        override fun onNext(genericOverall: GenericOverall) {
                            getListOfParticipants()
                        }

                        @SuppressLint("LongLogTag")
                        override fun onError(e: Throwable) {
                            Log.e(TAG, "Error removing user from conversation", e)
                        }

                        override fun onComplete() {
                            // unused atm
                        }
                    })
            }
        }
    }

    @SuppressLint("CheckResult", "StringFormatInvalid")
    override fun onItemClick(view: View?, position: Int): Boolean {
        if (!ConversationUtils.canModerate(conversation!!, spreedCapabilities)) {
            return true
        }

        val userItem = adapter?.getItem(position) as ParticipantItem
        val participant = userItem.model
        val apiVersion = ApiUtils.getConversationApiVersion(conversationUser, intArrayOf(ApiUtils.API_V4, 1))

        if (participant.calculatedActorType == USERS && participant.calculatedActorId == conversationUser.userId) {
            if (participant.attendeePin?.isNotEmpty() == true) {
                launchRemoveAttendeeFromConversationDialog(
                    participant,
                    apiVersion,
                    context.getString(R.string.nc_attendee_pin, participant.attendeePin),
                    R.drawable.ic_lock_grey600_24px
                )
            }
        } else if (participant.type == Participant.ParticipantType.OWNER) {
            // Can not moderate owner
        } else if (participant.calculatedActorType == GROUPS) {
            launchRemoveAttendeeFromConversationDialog(
                participant,
                apiVersion,
                context.getString(R.string.nc_remove_group_and_members)
            )
        } else if (participant.calculatedActorType == CIRCLES) {
            launchRemoveAttendeeFromConversationDialog(
                participant,
                apiVersion,
                context.getString(R.string.nc_remove_team_and_members)
            )
        } else {
            launchDefaultActions(participant, apiVersion)
        }
        return true
    }

    @SuppressLint("CheckResult")
    private fun launchDefaultActions(participant: Participant, apiVersion: Int) {
        val items = getDefaultActionItems(participant)

        if (CapabilitiesUtil.isBanningAvailable(conversationUser.capabilities?.spreedCapability!!)) {
            items.add(BasicListItemWithImage(R.drawable.baseline_block_24, context.getString(R.string.ban_participant)))
        }

        when (participant.type) {
            Participant.ParticipantType.MODERATOR, Participant.ParticipantType.GUEST_MODERATOR -> {
                items.removeAt(1)
            }

            Participant.ParticipantType.USER, Participant.ParticipantType.GUEST -> {
                items.removeAt(2)
            }

            else -> {
                // Self joined users can not be promoted nor demoted
                items.removeAt(2)
                items.removeAt(1)
            }
        }

        if (participant.attendeePin == null || participant.attendeePin!!.isEmpty()) {
            items.removeAt(0)
        }

        if (items.isNotEmpty()) {
            MaterialDialog(this, BottomSheet(WRAP_CONTENT)).show {
                cornerRadius(res = R.dimen.corner_radius)

                title(text = participant.displayName)
                listItemsWithImage(items = items) { _, index, _ ->
                    var actionToTrigger = index
                    if (participant.attendeePin == null || participant.attendeePin!!.isEmpty()) {
                        actionToTrigger++
                    }
                    if (participant.type == Participant.ParticipantType.USER_FOLLOWING_LINK) {
                        actionToTrigger++
                    }

                    when (actionToTrigger) {
                        DEMOTE_OR_PROMOTE -> {
                            if (apiVersion >= ApiUtils.API_V4) {
                                toggleModeratorStatus(apiVersion, participant)
                            } else {
                                toggleModeratorStatusLegacy(apiVersion, participant)
                            }
                        }

                        REMOVE_FROM_CONVERSATION -> {
                            removeAttendeeFromConversation(apiVersion, participant)
                        }

                        BAN_FROM_CONVERSATION -> {
                            handleBan(participant)
                        }

                        else -> {}
                    }
                }
            }
        }
    }

    @SuppressLint("StringFormatInvalid")
    private fun getDefaultActionItems(participant: Participant): MutableList<BasicListItemWithImage> {
        val items = mutableListOf(
            BasicListItemWithImage(
                R.drawable.ic_lock_grey600_24px,
                context.getString(R.string.nc_attendee_pin, participant.attendeePin)
            ),
            BasicListItemWithImage(
                R.drawable.ic_pencil_grey600_24dp,
                context.getString(R.string.nc_promote)
            ),
            BasicListItemWithImage(
                R.drawable.ic_pencil_grey600_24dp,
                context.getString(R.string.nc_demote)
            ),
            BasicListItemWithImage(
                R.drawable.ic_delete_grey600_24dp,
                context.getString(R.string.nc_remove_participant)
            )
        )
        return items
    }

    @SuppressLint("CheckResult")
    private fun launchRemoveAttendeeFromConversationDialog(
        participant: Participant,
        apiVersion: Int,
        itemText: String,
        @DrawableRes itemIcon: Int = R.drawable.ic_delete_grey600_24dp
    ) {
        val items = mutableListOf(BasicListItemWithImage(itemIcon, itemText))
        MaterialDialog(this, BottomSheet(WRAP_CONTENT)).show {
            cornerRadius(res = R.dimen.corner_radius)

            title(text = participant.displayName)
            listItemsWithImage(items = items) { _, index, _ ->
                if (index == 0) {
                    removeAttendeeFromConversation(apiVersion, participant)
                }
            }
        }
    }

    private fun MaterialDialog.handleBan(participant: Participant) {
        val apiVersion = ApiUtils.getConversationApiVersion(conversationUser, intArrayOf(ApiUtils.API_V4, 1))
        val binding = DialogBanParticipantBinding.inflate(layoutInflater)
        val actorTypeConverter = EnumActorTypeConverter()
        val dialog = MaterialAlertDialogBuilder(context).setView(binding.root).create()
        binding.avatarImage.loadUserAvatar(
            conversationUser,
            participant.actorId!!,
            requestBigSize = true,
            ignoreCache = false
        )
        binding.displayNameText.text = participant.actorId
        binding.buttonBan.setOnClickListener {
            banActor(
                actorTypeConverter.convertToString(participant.actorType!!),
                participant.actorId!!,
                binding.banParticipantEdit.text.toString()
            )
            removeAttendeeFromConversation(apiVersion, participant)
            dialog.dismiss()
        }
        binding.buttonClose.setOnClickListener { dialog.dismiss() }
        viewThemeUtils.material.colorTextInputLayout(binding.banParticipantEditLayout)
        viewThemeUtils.material.colorMaterialButtonPrimaryFilled(binding.buttonBan)
        viewThemeUtils.material.colorMaterialButtonText(binding.buttonClose)
        dialog.show()
    }

    private fun setUpNotificationSettings(module: DatabaseStorageModule) {
        binding.notificationSettingsView.notificationSettingsImportantConversation.setOnClickListener {
            val isChecked = binding.notificationSettingsView.importantConversationSwitch.isChecked
            binding.notificationSettingsView.importantConversationSwitch.isChecked = !isChecked
            lifecycleScope.launch {
                module.saveBoolean("important_conversation_switch", !isChecked)
            }
        }
        binding.notificationSettingsView.notificationSettingsCallNotifications.setOnClickListener {
            val isChecked = binding.notificationSettingsView.callNotificationsSwitch.isChecked
            binding.notificationSettingsView.callNotificationsSwitch.isChecked = !isChecked
            lifecycleScope.launch {
                module.saveBoolean("call_notifications_switch", !isChecked)
            }
        }
        binding.notificationSettingsView.conversationInfoMessageNotificationsDropdown
            .setOnItemClickListener { _, _, position, _ ->
                val value = resources.getStringArray(R.array.message_notification_levels_entry_values)[position]
                Log.i(TAG, "saved $value to module from $position")
                lifecycleScope.launch {
                    module.saveString("conversation_info_message_notifications_dropdown", value)
                }
            }

        binding.notificationSettingsView.importantConversationSwitch.isChecked = module
            .getBoolean("important_conversation_switch", false)

        if (conversation!!.remoteServer.isNullOrEmpty()) {
            binding.notificationSettingsView.notificationSettingsCallNotifications.visibility = VISIBLE
            binding.notificationSettingsView.callNotificationsSwitch.isChecked = module
                .getBoolean("call_notifications_switch", true)
        } else {
            binding.notificationSettingsView.notificationSettingsCallNotifications.visibility = GONE
        }
    }

    companion object {
        private val TAG = ConversationInfoActivity::class.java.simpleName
        private const val NOTIFICATION_LEVEL_ALWAYS: Int = 1
        private const val NOTIFICATION_LEVEL_MENTION: Int = 2
        private const val NOTIFICATION_LEVEL_NEVER: Int = 3
        private const val LOW_EMPHASIS_OPACITY: Float = 0.38f
        private const val RECORDING_CONSENT_NOT_REQUIRED_FOR_CONVERSATION: Int = 0
        private const val RECORDING_CONSENT_REQUIRED_FOR_CONVERSATION: Int = 1
        private const val DEMOTE_OR_PROMOTE = 1
        private const val REMOVE_FROM_CONVERSATION = 2
        private const val BAN_FROM_CONVERSATION = 3
    }

    /**
     * Comparator for participants, sorts by online-status, moderator-status and display name.
     */
    class ParticipantItemComparator : Comparator<ParticipantItem> {
        override fun compare(left: ParticipantItem, right: ParticipantItem): Int {
            val leftIsGroup = left.model.actorType == GROUPS || left.model.actorType == CIRCLES
            val rightIsGroup = right.model.actorType == GROUPS || right.model.actorType == CIRCLES
            if (leftIsGroup != rightIsGroup) {
                // Groups below participants
                return if (rightIsGroup) {
                    -1
                } else {
                    1
                }
            }

            if (left.isOnline && !right.isOnline) {
                return -1
            } else if (!left.isOnline && right.isOnline) {
                return 1
            }

            val moderatorTypes = ArrayList<Participant.ParticipantType>()
            moderatorTypes.add(Participant.ParticipantType.MODERATOR)
            moderatorTypes.add(Participant.ParticipantType.OWNER)
            moderatorTypes.add(Participant.ParticipantType.GUEST_MODERATOR)

            if (moderatorTypes.contains(left.model.type) && !moderatorTypes.contains(right.model.type)) {
                return -1
            } else if (!moderatorTypes.contains(left.model.type) && moderatorTypes.contains(right.model.type)) {
                return 1
            }

            return left.model.displayName!!.lowercase(Locale.ROOT).compareTo(
                right.model.displayName!!.lowercase(Locale.ROOT)
            )
        }
    }
}
