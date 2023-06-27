/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Andy Scherzinger
 * @author Tim Krüger
 * @author Marcel Hibbe
 * @author Ezhil Shanmugham
 * Copyright (C) 2022-2023 Marcel Hibbe (dev@mhibbe.de)
 * Copyright (C) 2021-2022 Tim Krüger <t@timkrueger.me>
 * Copyright (C) 2021 Andy Scherzinger (info@andy-scherzinger.de)
 * Copyright (C) 2017-2018 Mario Danic <mario@lovelyhq.com>
 * Copyright (C) 2023 Ezhil Shanmugham <ezhil56x.contact@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.conversationinfo

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Parcelable
import android.text.TextUtils
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import autodagger.AutoInjector
import com.afollestad.materialdialogs.LayoutMode.WRAP_CONTENT
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.datetime.dateTimePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nextcloud.android.common.ui.theme.utils.ColorRole
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.BaseActivity
import com.nextcloud.talk.activities.MainActivity
import com.nextcloud.talk.adapters.items.ParticipantItem
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.contacts.ContactsActivity
import com.nextcloud.talk.controllers.bottomsheet.items.BasicListItemWithImage
import com.nextcloud.talk.controllers.bottomsheet.items.listItemsWithImage
import com.nextcloud.talk.conversationinfoedit.ConversationInfoEditActivity
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.ActivityConversationInfoBinding
import com.nextcloud.talk.events.EventStatus
import com.nextcloud.talk.extensions.loadConversationAvatar
import com.nextcloud.talk.extensions.loadSystemAvatar
import com.nextcloud.talk.extensions.loadUserAvatar
import com.nextcloud.talk.jobs.DeleteConversationWorker
import com.nextcloud.talk.jobs.LeaveConversationWorker
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.conversations.RoomOverall
import com.nextcloud.talk.models.json.converters.EnumNotificationLevelConverter
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.models.json.participants.Participant
import com.nextcloud.talk.models.json.participants.Participant.ActorType.CIRCLES
import com.nextcloud.talk.models.json.participants.Participant.ActorType.GROUPS
import com.nextcloud.talk.models.json.participants.Participant.ActorType.USERS
import com.nextcloud.talk.models.json.participants.ParticipantsOverall
import com.nextcloud.talk.repositories.conversations.ConversationsRepository
import com.nextcloud.talk.shareditems.activities.SharedItemsActivity
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.DateConstants
import com.nextcloud.talk.utils.DateUtils
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.database.user.CapabilitiesUtilNew
import com.nextcloud.talk.utils.preferences.preferencestorage.DatabaseStorageModule
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.common.SmoothScrollLinearLayoutManager
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.parceler.Parcels
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
    lateinit var ncApi: NcApi

    @Inject
    lateinit var conversationsRepository: ConversationsRepository

    @Inject
    lateinit var dateUtils: DateUtils

    private lateinit var conversationToken: String
    private lateinit var conversationUser: User
    private var hasAvatarSpacing: Boolean = false
    private lateinit var credentials: String
    private var roomDisposable: Disposable? = null
    private var participantsDisposable: Disposable? = null

    private var databaseStorageModule: DatabaseStorageModule? = null
    private var conversation: Conversation? = null

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

        conversationUser = intent.getParcelableExtra(BundleKeys.KEY_USER_ENTITY)!!
        conversationToken = intent.getStringExtra(BundleKeys.KEY_ROOM_TOKEN)!!
        hasAvatarSpacing = intent.getBooleanExtra(BundleKeys.KEY_ROOM_ONE_TO_ONE, false)
        credentials = ApiUtils.getCredentials(conversationUser.username, conversationUser.token)
    }

    override fun onResume() {
        super.onResume()

        if (databaseStorageModule == null) {
            databaseStorageModule = DatabaseStorageModule(conversationUser, conversationToken)
        }
        setUpNotificationSettings(databaseStorageModule!!)
        binding.deleteConversationAction.setOnClickListener { showDeleteConversationDialog() }
        binding.leaveConversationAction.setOnClickListener { leaveConversation() }
        binding.clearConversationHistory.setOnClickListener { showClearHistoryDialog() }
        binding.addParticipantsAction.setOnClickListener { addParticipants() }

        if (CapabilitiesUtilNew.hasSpreedFeatureCapability(conversationUser, "rich-object-list-media")) {
            binding.sharedItemsButton.setOnClickListener { showSharedItems() }
        } else {
            binding.categorySharedItems.visibility = GONE
        }

        fetchRoomInfo()

        themeTextViews()
        themeSwitchPreferences()

        binding.addParticipantsAction.visibility = GONE

        binding.progressBar.let { viewThemeUtils.platform.colorCircularProgressBar(it, ColorRole.PRIMARY) }
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
            if (CapabilitiesUtilNew.isConversationAvatarEndpointAvailable(conversationUser)) {
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
            bundle.putParcelable(BundleKeys.KEY_USER_ENTITY, conversationUser)
            bundle.putParcelable(
                BundleKeys.KEY_ACTIVE_CONVERSATION,
                Parcels.wrap(conversation)
            )
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
                binding.guestAccessView.passwordProtectionSwitch
            ).forEach(viewThemeUtils.talk::colorSwitch)
        }
    }

    private fun themeTextViews() {
        binding.run {
            listOf(
                binding.notificationSettingsView.notificationSettingsCategory,
                binding.webinarInfoView.webinarSettingsCategory,
                binding.guestAccessView.guestAccessSettingsCategory,
                binding.categorySharedItems,
                binding.conversationSettings,
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
        intent.putExtra(BundleKeys.KEY_USER_ENTITY, conversationUser as Parcelable)
        intent.putExtra(SharedItemsActivity.KEY_USER_IS_OWNER_OR_MODERATOR, conversation?.isParticipantOwnerOrModerator)
        startActivity(intent)
    }

    private fun setupWebinaryView() {
        if (CapabilitiesUtilNew.hasSpreedFeatureCapability(conversationUser, "webinary-lobby") &&
            webinaryRoomType(conversation!!) &&
            conversation!!.canModerate(conversationUser)
        ) {
            binding.webinarInfoView.webinarSettings.visibility = VISIBLE

            val isLobbyOpenToModeratorsOnly =
                conversation!!.lobbyState == Conversation.LobbyState.LOBBY_STATE_MODERATORS_ONLY
            binding.webinarInfoView.lobbySwitch.isChecked = isLobbyOpenToModeratorsOnly

            reconfigureLobbyTimerView()

            binding.webinarInfoView.startTimeButton.setOnClickListener {
                MaterialDialog(this, BottomSheet(WRAP_CONTENT)).show {
                    val currentTimeCalendar = Calendar.getInstance()
                    if (conversation!!.lobbyTimer != null && conversation!!.lobbyTimer != 0L) {
                        currentTimeCalendar.timeInMillis = conversation!!.lobbyTimer!! * DateConstants.SECOND_DIVIDER
                    }

                    dateTimePicker(
                        minDateTime = Calendar.getInstance(),
                        requireFutureDateTime = true,
                        currentDateTime = currentTimeCalendar,
                        show24HoursView = true,
                        dateTimeCallback = { _, dateTime ->
                            reconfigureLobbyTimerView(dateTime)
                            submitLobbyChanges()
                        }
                    )
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

    private fun webinaryRoomType(conversation: Conversation): Boolean {
        return conversation.type == Conversation.ConversationType.ROOM_GROUP_CALL ||
            conversation.type == Conversation.ConversationType.ROOM_PUBLIC_CALL
    }

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
            Conversation.LobbyState.LOBBY_STATE_MODERATORS_ONLY
        } else {
            Conversation.LobbyState.LOBBY_STATE_ALL_PARTICIPANTS
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

        val apiVersion = ApiUtils.getConversationApiVersion(conversationUser, intArrayOf(ApiUtils.APIv4, 1))

        ncApi.setLobbyForConversation(
            ApiUtils.getCredentials(conversationUser.username, conversationUser.token),
            ApiUtils.getUrlForRoomWebinaryLobby(apiVersion, conversationUser.baseUrl, conversation!!.token),
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
                    // unused atm
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
        binding.recyclerView.setHasFixedSize(true)
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
            userItem = ParticipantItem(this, participant, conversationUser, viewThemeUtils)
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
        val apiVersion: Int = ApiUtils.getConversationApiVersion(conversationUser, intArrayOf(ApiUtils.APIv4, 1))

        val fieldMap = HashMap<String, Boolean>()
        fieldMap["includeStatus"] = true

        ncApi.getPeersForCall(
            credentials,
            ApiUtils.getUrlForParticipants(
                apiVersion,
                conversationUser.baseUrl,
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
        workerData?.let {
            WorkManager.getInstance(context).enqueue(
                OneTimeWorkRequest.Builder(
                    LeaveConversationWorker::class
                        .java
                ).setInputData(it).build()
            )

            val intent = Intent(context, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
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
        val apiVersion = ApiUtils.getChatApiVersion(conversationUser, intArrayOf(1))

        ncApi.clearChatHistory(
            credentials,
            ApiUtils.getUrlForChat(apiVersion, conversationUser.baseUrl, conversationToken)
        )
            ?.subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(object : Observer<GenericOverall> {
                override fun onSubscribe(d: Disposable) {
                    // unused atm
                }

                override fun onNext(genericOverall: GenericOverall) {
                    Toast.makeText(context, context.getString(R.string.nc_clear_history_success), Toast.LENGTH_LONG)
                        .show()
                }

                override fun onError(e: Throwable) {
                    Toast.makeText(context, R.string.nc_common_error_sorry, Toast.LENGTH_LONG).show()
                    Log.e(TAG, "failed to clear chat history", e)
                }

                override fun onComplete() {
                    // unused atm
                }
            })
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

    private fun fetchRoomInfo() {
        val apiVersion: Int
        // FIXME Fix API checking with guests?
        apiVersion = ApiUtils.getConversationApiVersion(conversationUser, intArrayOf(ApiUtils.APIv4, 1))

        ncApi.getRoom(credentials, ApiUtils.getUrlForRoom(apiVersion, conversationUser.baseUrl, conversationToken))
            ?.subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(object : Observer<RoomOverall> {
                override fun onSubscribe(d: Disposable) {
                    roomDisposable = d
                }

                @Suppress("Detekt.TooGenericExceptionCaught")
                override fun onNext(roomOverall: RoomOverall) {
                    conversation = roomOverall.ocs!!.data

                    val conversationCopy = conversation

                    if (conversationCopy!!.canModerate(conversationUser)) {
                        binding.addParticipantsAction.visibility = VISIBLE
                        if (CapabilitiesUtilNew.hasSpreedFeatureCapability(
                                conversationUser,
                                "clear-history"
                            )
                        ) {
                            binding.clearConversationHistory.visibility = VISIBLE
                        } else {
                            binding.clearConversationHistory.visibility = GONE
                        }
                        showOptionsMenu()
                    } else {
                        binding.addParticipantsAction.visibility = GONE
                        binding.clearConversationHistory.visibility = GONE
                    }

                    if (!isDestroyed) {
                        binding.dangerZoneOptions.visibility = VISIBLE

                        setupWebinaryView()

                        if (!conversation!!.canLeave()) {
                            binding.leaveConversationAction.visibility = GONE
                        } else {
                            binding.leaveConversationAction.visibility = VISIBLE
                        }

                        if (!conversation!!.canDelete(conversationUser)) {
                            binding.deleteConversationAction.visibility = GONE
                        } else {
                            binding.deleteConversationAction.visibility = VISIBLE
                        }

                        if (Conversation.ConversationType.ROOM_SYSTEM == conversation!!.type) {
                            binding.notificationSettingsView.callNotificationsSwitch.visibility = GONE
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
                        initExpiringMessageOption()

                        binding.let {
                            GuestAccessHelper(
                                this@ConversationInfoActivity,
                                it,
                                conversation!!,
                                conversationUser
                            ).setupGuestAccess()
                        }

                        binding.notificationSettingsView.notificationSettings.visibility = VISIBLE
                    }
                }

                override fun onError(e: Throwable) {
                    Log.e(TAG, "failed to fetch room info", e)
                }

                override fun onComplete() {
                    roomDisposable!!.dispose()
                }
            })
    }

    private fun initExpiringMessageOption() {
        if (conversation!!.isParticipantOwnerOrModerator &&
            CapabilitiesUtilNew.hasSpreedFeatureCapability(conversationUser, "message-expiration")
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
                databaseStorageModule!!.saveString("conversation_settings_dropdown", v)
            }

            binding.conversationSettingsDropdown.visibility = VISIBLE
            binding.conversationInfoExpireMessagesExplanation.visibility = VISIBLE
        } else {
            binding.conversationSettings.visibility = GONE
        }
    }

    private fun adjustNotificationLevelUI() {
        if (conversation != null) {
            if (CapabilitiesUtilNew.hasSpreedFeatureCapability(conversationUser, "notification-levels")) {
                binding.notificationSettingsView.conversationInfoMessageNotificationsDropdown.isEnabled = true
                binding.notificationSettingsView.conversationInfoMessageNotificationsDropdown.alpha = 1.0f

                if (conversation!!.notificationLevel != Conversation.NotificationLevel.DEFAULT) {
                    val stringValue: String =
                        when (EnumNotificationLevelConverter().convertToInt(conversation!!.notificationLevel)) {
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

    private fun setProperNotificationValue(conversation: Conversation?) {
        if (conversation!!.type == Conversation.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL) {
            if (CapabilitiesUtilNew.hasSpreedFeatureCapability(conversationUser, "mention-flag")) {
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
            Conversation.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL -> if (!TextUtils.isEmpty(conversation!!.name)) {
                conversation!!.name?.let {
                    binding.avatarImage.loadUserAvatar(
                        conversationUser,
                        it,
                        true,
                        false
                    )
                }
            }

            Conversation.ConversationType.ROOM_GROUP_CALL, Conversation.ConversationType.ROOM_PUBLIC_CALL -> {
                binding.avatarImage.loadConversationAvatar(
                    conversationUser,
                    conversation!!,
                    false,
                    viewThemeUtils
                )
            }

            Conversation.ConversationType.ROOM_SYSTEM -> {
                binding.avatarImage.loadSystemAvatar()
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
                    conversationUser.baseUrl,
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
                    conversationUser.baseUrl,
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
                Log.e(TAG, "Error toggling moderator status", e)
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
                    conversationUser.baseUrl,
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
                    conversationUser.baseUrl,
                    conversation!!.token
                ),
                participant.userId
            )
                ?.subscribeOn(Schedulers.io())
                ?.observeOn(AndroidSchedulers.mainThread())
                ?.subscribe(subscriber)
        }
    }

    private fun removeAttendeeFromConversation(apiVersion: Int, participant: Participant) {
        if (apiVersion >= ApiUtils.APIv4) {
            ncApi.removeAttendeeFromConversation(
                credentials,
                ApiUtils.getUrlForAttendees(
                    apiVersion,
                    conversationUser.baseUrl,
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
                        conversationUser.baseUrl,
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
                        conversationUser.baseUrl,
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

    @SuppressLint("CheckResult")
    override fun onItemClick(view: View?, position: Int): Boolean {
        if (!conversation!!.canModerate(conversationUser)) {
            return true
        }

        val userItem = adapter?.getItem(position) as ParticipantItem
        val participant = userItem.model

        val apiVersion = ApiUtils.getConversationApiVersion(conversationUser, intArrayOf(ApiUtils.APIv4, 1))

        if (participant.calculatedActorType == USERS && participant.calculatedActorId == conversationUser.userId) {
            if (participant.attendeePin?.isNotEmpty() == true) {
                val items = mutableListOf(
                    BasicListItemWithImage(
                        R.drawable.ic_lock_grey600_24px,
                        context.getString(R.string.nc_attendee_pin, participant.attendeePin)
                    )
                )
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
            return true
        }

        if (participant.type == Participant.ParticipantType.OWNER) {
            // Can not moderate owner
            return true
        }

        if (participant.calculatedActorType == GROUPS) {
            val items = mutableListOf(
                BasicListItemWithImage(
                    R.drawable.ic_delete_grey600_24dp,
                    context.getString(R.string.nc_remove_group_and_members)
                )
            )
            MaterialDialog(this, BottomSheet(WRAP_CONTENT)).show {
                cornerRadius(res = R.dimen.corner_radius)

                title(text = participant.displayName)
                listItemsWithImage(items = items) { _, index, _ ->
                    if (index == 0) {
                        removeAttendeeFromConversation(apiVersion, participant)
                    }
                }
            }
            return true
        }

        if (participant.calculatedActorType == CIRCLES) {
            val items = mutableListOf(
                BasicListItemWithImage(
                    R.drawable.ic_delete_grey600_24dp,
                    context.getString(R.string.nc_remove_circle_and_members)
                )
            )
            MaterialDialog(this, BottomSheet(WRAP_CONTENT)).show {
                cornerRadius(res = R.dimen.corner_radius)

                title(text = participant.displayName)
                listItemsWithImage(items = items) { _, index, _ ->
                    if (index == 0) {
                        removeAttendeeFromConversation(apiVersion, participant)
                    }
                }
            }
            return true
        }

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

        if (participant.type == Participant.ParticipantType.MODERATOR ||
            participant.type == Participant.ParticipantType.GUEST_MODERATOR
        ) {
            items.removeAt(1)
        } else if (participant.type == Participant.ParticipantType.USER ||
            participant.type == Participant.ParticipantType.GUEST
        ) {
            items.removeAt(2)
        } else {
            // Self joined users can not be promoted nor demoted
            items.removeAt(2)
            items.removeAt(1)
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

                    if (actionToTrigger == 0) {
                        // Pin, nothing to do
                    } else if (actionToTrigger == 1) {
                        // Promote/demote
                        if (apiVersion >= ApiUtils.APIv4) {
                            toggleModeratorStatus(apiVersion, participant)
                        } else {
                            toggleModeratorStatusLegacy(apiVersion, participant)
                        }
                    } else if (actionToTrigger == 2) {
                        // Remove from conversation
                        removeAttendeeFromConversation(apiVersion, participant)
                    }
                }
            }
        }
        return true
    }

    private fun setUpNotificationSettings(module: DatabaseStorageModule) {
        binding.notificationSettingsView.notificationSettingsImportantConversation.setOnClickListener {
            val isChecked = binding.notificationSettingsView.importantConversationSwitch.isChecked
            binding.notificationSettingsView.importantConversationSwitch.isChecked = !isChecked
            module.saveBoolean("important_conversation_switch", !isChecked)
        }
        binding.notificationSettingsView.notificationSettingsCallNotifications.setOnClickListener {
            val isChecked = binding.notificationSettingsView.callNotificationsSwitch.isChecked
            binding.notificationSettingsView.callNotificationsSwitch.isChecked = !isChecked
            module.saveBoolean("call_notifications_switch", !isChecked)
        }
        binding.notificationSettingsView.conversationInfoMessageNotificationsDropdown
            .setOnItemClickListener { _, _, position, _ ->
                val value = resources.getStringArray(R.array.message_notification_levels_entry_values)[position]
                Log.i(TAG, "saved $value to module from $position")
                module.saveString("conversation_info_message_notifications_dropdown", value)
            }

        binding.notificationSettingsView.importantConversationSwitch.isChecked = module
            .getBoolean("important_conversation_switch", false)

        binding.notificationSettingsView.callNotificationsSwitch.isChecked = module
            .getBoolean("call_notifications_switch", true)
    }

    companion object {
        private const val TAG = "ConversationInfo"
        private const val NOTIFICATION_LEVEL_ALWAYS: Int = 1
        private const val NOTIFICATION_LEVEL_MENTION: Int = 2
        private const val NOTIFICATION_LEVEL_NEVER: Int = 3
        private const val LOW_EMPHASIS_OPACITY: Float = 0.38f
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
