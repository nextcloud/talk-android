/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2018 Mario Danic <mario@lovelyhq.com>
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

package com.nextcloud.talk.controllers

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.appcompat.widget.SwitchCompat
import androidx.emoji.widget.EmojiTextView
import androidx.recyclerview.widget.RecyclerView
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import autodagger.AutoInjector
import butterknife.BindView
import butterknife.OnClick
import com.afollestad.materialdialogs.LayoutMode.WRAP_CONTENT
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.datetime.dateTimePicker
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.drawee.view.SimpleDraweeView
import com.nextcloud.talk.R
import com.nextcloud.talk.adapters.items.UserItem
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.controllers.base.BaseController
import com.nextcloud.talk.controllers.bottomsheet.items.BasicListItemWithImage
import com.nextcloud.talk.controllers.bottomsheet.items.listItemsWithImage
import com.nextcloud.talk.events.EventStatus
import com.nextcloud.talk.jobs.DeleteConversationWorker
import com.nextcloud.talk.jobs.LeaveConversationWorker
import com.nextcloud.talk.models.database.UserEntity
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.conversations.RoomOverall
import com.nextcloud.talk.models.json.converters.EnumNotificationLevelConverter
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.models.json.participants.Participant
import com.nextcloud.talk.models.json.participants.ParticipantsOverall
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.DateUtils
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.preferences.preferencestorage.DatabaseStorageModule
import com.yarolegovich.lovelydialog.LovelySaveStateHandler
import com.yarolegovich.lovelydialog.LovelyStandardDialog
import com.yarolegovich.mp.MaterialChoicePreference
import com.yarolegovich.mp.MaterialPreferenceCategory
import com.yarolegovich.mp.MaterialPreferenceScreen
import com.yarolegovich.mp.MaterialStandardPreference
import com.yarolegovich.mp.MaterialSwitchPreference
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.common.SmoothScrollLinearLayoutManager
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.*
import javax.inject.Inject


@AutoInjector(NextcloudTalkApplication::class)
class ConversationInfoController(args: Bundle) : BaseController(args), FlexibleAdapter.OnItemClickListener {

    @BindView(R.id.notification_settings)
    lateinit var notificationsPreferenceScreen: MaterialPreferenceScreen
    @BindView(R.id.progressBar)
    lateinit var progressBar: ProgressBar
    @BindView(R.id.conversation_info_message_notifications)
    lateinit var messageNotificationLevel: MaterialChoicePreference
    @BindView(R.id.webinar_settings)
    lateinit var conversationInfoWebinar: MaterialPreferenceScreen
    @BindView(R.id.conversation_info_lobby)
    lateinit var conversationInfoLobby: MaterialSwitchPreference
    @BindView(R.id.conversation_info_name)
    lateinit var nameCategoryView: MaterialPreferenceCategory
    @BindView(R.id.start_time_preferences)
    lateinit var startTimeView: MaterialStandardPreference
    @BindView(R.id.avatar_image)
    lateinit var conversationAvatarImageView: SimpleDraweeView
    @BindView(R.id.display_name_text)
    lateinit var conversationDisplayName: EmojiTextView
    @BindView(R.id.participants_list_category)
    lateinit var participantsListCategory: MaterialPreferenceCategory
    @BindView(R.id.addParticipantsAction)
    lateinit var addParticipantsAction: MaterialStandardPreference;
    @BindView(R.id.recycler_view)
    lateinit var recyclerView: RecyclerView
    @BindView(R.id.deleteConversationAction)
    lateinit var deleteConversationAction: MaterialStandardPreference
    @BindView(R.id.leaveConversationAction)
    lateinit var leaveConversationAction: MaterialStandardPreference
    @BindView(R.id.ownOptions)
    lateinit var ownOptionsCategory: MaterialPreferenceCategory
    @BindView(R.id.muteCalls)
    lateinit var muteCalls: MaterialSwitchPreference
    @set:Inject
    lateinit var ncApi: NcApi
    @set:Inject
    lateinit var context: Context
    @set:Inject
    lateinit var eventBus: EventBus

    private val conversationToken: String?
    private val conversationUser: UserEntity?
    private val credentials: String?
    private var roomDisposable: Disposable? = null
    private var participantsDisposable: Disposable? = null

    private var databaseStorageModule: DatabaseStorageModule? = null
    private var conversation: Conversation? = null

    private var adapter: FlexibleAdapter<AbstractFlexibleItem<*>>? = null
    private var recyclerViewItems: MutableList<AbstractFlexibleItem<*>> = ArrayList()

    private var saveStateHandler: LovelySaveStateHandler? = null

    private val workerData: Data?
        get() {
            if (!TextUtils.isEmpty(conversationToken) && conversationUser != null) {
                val data = Data.Builder()
                data.putString(BundleKeys.KEY_ROOM_TOKEN, conversationToken)
                data.putLong(BundleKeys.KEY_INTERNAL_USER_ID, conversationUser.id)
                return data.build()
            }

            return null
        }

    init {
        setHasOptionsMenu(true)
        NextcloudTalkApplication.sharedApplication?.componentApplication?.inject(this)
        conversationUser = args.getParcelable(BundleKeys.KEY_USER_ENTITY)
        conversationToken = args.getString(BundleKeys.KEY_ROOM_TOKEN)
        credentials = ApiUtils.getCredentials(conversationUser!!.username, conversationUser.token)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                router.popCurrentController()
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun inflateView(inflater: LayoutInflater, container: ViewGroup): View {
        return inflater.inflate(R.layout.controller_conversation_info, container, false)
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        eventBus.register(this)

        if (databaseStorageModule == null) {
            databaseStorageModule = DatabaseStorageModule(conversationUser!!, conversationToken)
        }

        notificationsPreferenceScreen.setStorageModule(databaseStorageModule)
        conversationInfoWebinar.setStorageModule(databaseStorageModule)

        fetchRoomInfo()
    }

    override fun onViewBound(view: View) {
        super.onViewBound(view)

        if (saveStateHandler == null) {
            saveStateHandler = LovelySaveStateHandler()
        }

        addParticipantsAction.visibility = View.GONE
    }

    private fun setupWebinaryView() {
        if (conversationUser!!.hasSpreedFeatureCapability("webinary-lobby") && (conversation!!.type
                        == Conversation.ConversationType.ROOM_GROUP_CALL || conversation!!.type ==
                        Conversation.ConversationType.ROOM_PUBLIC_CALL) && conversation!!.canModerate(conversationUser)) {
            conversationInfoWebinar.visibility = View.VISIBLE

            val isLobbyOpenToModeratorsOnly = conversation!!.lobbyState == Conversation.LobbyState.LOBBY_STATE_MODERATORS_ONLY
            (conversationInfoLobby.findViewById<View>(R.id.mp_checkable) as SwitchCompat)
                    .isChecked = isLobbyOpenToModeratorsOnly

            reconfigureLobbyTimerView()

            startTimeView.setOnClickListener {
                MaterialDialog(activity!!, BottomSheet(WRAP_CONTENT)).show {
                    val currentTimeCalendar = Calendar.getInstance()
                    if (conversation!!.lobbyTimer != null && conversation!!.lobbyTimer != 0L) {
                        currentTimeCalendar.timeInMillis = conversation!!.lobbyTimer * 1000
                    }

                    dateTimePicker(minDateTime = Calendar.getInstance(), requireFutureDateTime =
                    true, currentDateTime = currentTimeCalendar, dateTimeCallback = { _,
                                                                                      dateTime ->
                        reconfigureLobbyTimerView(dateTime)
                        submitLobbyChanges()
                    })
                }
            }

            (conversationInfoLobby.findViewById<View>(R.id.mp_checkable) as SwitchCompat).setOnCheckedChangeListener { _, _ ->
                reconfigureLobbyTimerView()
                submitLobbyChanges()
            }
        } else {
            conversationInfoWebinar.visibility = View.GONE
        }
    }

    fun reconfigureLobbyTimerView(dateTime: Calendar? = null) {
        val isChecked = (conversationInfoLobby.findViewById<View>(R.id.mp_checkable) as SwitchCompat).isChecked

        if (dateTime != null && isChecked) {
            conversation!!.lobbyTimer = (dateTime.timeInMillis - (dateTime.time.seconds * 1000)) / 1000
        } else if (!isChecked) {
            conversation!!.lobbyTimer = 0
        }

        conversation!!.lobbyState = if (isChecked) Conversation.LobbyState
                .LOBBY_STATE_MODERATORS_ONLY else Conversation.LobbyState.LOBBY_STATE_ALL_PARTICIPANTS

        if (conversation!!.lobbyTimer != null && conversation!!.lobbyTimer != java.lang.Long.MIN_VALUE && conversation!!.lobbyTimer != 0L) {
            startTimeView.setSummary(DateUtils.getLocalDateStringFromTimestampForLobby(conversation!!.lobbyTimer))
        } else {
            startTimeView.setSummary(R.string.nc_manual)
        }

        if (isChecked) {
            startTimeView.visibility = View.VISIBLE
        } else {
            startTimeView.visibility = View.GONE
        }
    }

    fun submitLobbyChanges() {
        val state = if ((conversationInfoLobby.findViewById<View>(R.id
                        .mp_checkable) as SwitchCompat).isChecked) 1 else 0
        ncApi.setLobbyForConversation(ApiUtils.getCredentials(conversationUser!!.username,
                conversationUser.token), ApiUtils.getUrlForLobbyForConversation
        (conversationUser.baseUrl, conversation!!.token), state, conversation!!.lobbyTimer)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(object : Observer<GenericOverall> {
                    override fun onComplete() {
                    }

                    override fun onSubscribe(d: Disposable) {
                    }

                    override fun onNext(t: GenericOverall) {
                    }

                    override fun onError(e: Throwable) {
                    }

                })
    }

    private fun showLovelyDialog(dialogId: Int, savedInstanceState: Bundle) {
        when (dialogId) {
            ID_DELETE_CONVERSATION_DIALOG -> showDeleteConversationDialog(savedInstanceState)
            else -> {
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(eventStatus: EventStatus) {
        getListOfParticipants()
    }

    override fun onDetach(view: View) {
        super.onDetach(view)
        eventBus.unregister(this)
    }

    private fun showDeleteConversationDialog(savedInstanceState: Bundle?) {
        if (activity != null) {
            LovelyStandardDialog(activity, LovelyStandardDialog.ButtonLayout.HORIZONTAL)
                    .setTopColorRes(R.color.nc_darkRed)
                    .setIcon(DisplayUtils.getTintedDrawable(context!!.resources,
                            R.drawable.ic_delete_black_24dp, R.color.bg_default))
                    .setPositiveButtonColor(context!!.resources.getColor(R.color.nc_darkRed))
                    .setTitle(R.string.nc_delete_call)
                    .setMessage(conversation!!.deleteWarningMessage)
                    .setPositiveButton(R.string.nc_delete) { deleteConversation() }
                    .setNegativeButton(R.string.nc_cancel, null)
                    .setInstanceStateHandler(ID_DELETE_CONVERSATION_DIALOG, saveStateHandler!!)
                    .setSavedInstanceState(savedInstanceState)
                    .show()
        }
    }

    override fun onSaveViewState(view: View, outState: Bundle) {
        saveStateHandler!!.saveInstanceState(outState)
        super.onSaveViewState(view, outState)
    }

    override fun onRestoreViewState(view: View, savedViewState: Bundle) {
        super.onRestoreViewState(view, savedViewState)
        if (LovelySaveStateHandler.wasDialogOnScreen(savedViewState)) {
            //Dialog won't be restarted automatically, so we need to call this method.
            //Each dialog knows how to restore its state
            showLovelyDialog(LovelySaveStateHandler.getSavedDialogId(savedViewState), savedViewState)
        }
    }

    private fun setupAdapter() {
        if (activity != null) {
            if (adapter == null) {
                adapter = FlexibleAdapter(recyclerViewItems, activity, true)
            }

            val layoutManager = SmoothScrollLinearLayoutManager(activity)
            recyclerView.layoutManager = layoutManager
            recyclerView.setHasFixedSize(true)
            recyclerView.adapter = adapter

            adapter!!.addListener(this)
        }
    }

    private fun handleParticipants(participants: List<Participant>) {
        var userItem: UserItem
        var participant: Participant

        recyclerViewItems = ArrayList()
        var ownUserItem: UserItem? = null

        for (i in participants.indices) {
            participant = participants[i]
            userItem = UserItem(participant, conversationUser, null)
            userItem.isOnline = !participant.sessionId.equals("0")
            if (!TextUtils.isEmpty(participant.userId) && participant.userId == conversationUser!!.userId) {
                ownUserItem = userItem
                ownUserItem.model.sessionId = "-1"
                ownUserItem.isOnline = true
            } else {
                recyclerViewItems.add(userItem)
            }
        }


        if (ownUserItem != null) {
            recyclerViewItems.add(0, ownUserItem)
        }

        setupAdapter()

        participantsListCategory.visibility = View.VISIBLE
        adapter!!.updateDataSet(recyclerViewItems)
    }

    override fun getTitle(): String? {
        return resources!!.getString(R.string.nc_conversation_menu_conversation_info)
    }

    private fun getListOfParticipants() {
        ncApi.getPeersForCall(credentials, ApiUtils.getUrlForParticipants(conversationUser!!.baseUrl, conversationToken))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(object : Observer<ParticipantsOverall> {
                    override fun onSubscribe(d: Disposable) {
                        participantsDisposable = d
                    }

                    override fun onNext(participantsOverall: ParticipantsOverall) {
                        handleParticipants(participantsOverall.ocs.data)
                    }

                    override fun onError(e: Throwable) {

                    }

                    override fun onComplete() {
                        participantsDisposable!!.dispose()
                    }
                })

    }

    @OnClick(R.id.addParticipantsAction)
    internal fun addParticipants() {
        val bundle = Bundle()
        val existingParticipantsId = arrayListOf<String>()

        recyclerViewItems.forEach {
            val userItem = it as UserItem
            existingParticipantsId.add(userItem.model.userId)
        }

        bundle.putBoolean(BundleKeys.KEY_ADD_PARTICIPANTS, true);
        bundle.putStringArrayList(BundleKeys.KEY_EXISTING_PARTICIPANTS, existingParticipantsId)
        bundle.putString(BundleKeys.KEY_TOKEN, conversation!!.token)

        getRouter().pushController((RouterTransaction.with(ContactsController(bundle))
                .pushChangeHandler(HorizontalChangeHandler())
                .popChangeHandler(HorizontalChangeHandler())))
    }

    @OnClick(R.id.leaveConversationAction)
    internal fun leaveConversation() {
        workerData?.let {
            WorkManager.getInstance().enqueue(OneTimeWorkRequest.Builder
            (LeaveConversationWorker::class
                    .java).setInputData(it).build()
            )
            popTwoLastControllers()
        }
    }

    private fun deleteConversation() {
        workerData?.let {
            WorkManager.getInstance().enqueue(OneTimeWorkRequest.Builder
            (DeleteConversationWorker::class.java).setInputData(it).build())
            popTwoLastControllers()
        }
    }

    @OnClick(R.id.deleteConversationAction)
    internal fun deleteConversationClick() {
        showDeleteConversationDialog(null)
    }

    private fun popTwoLastControllers() {
        var backstack = router.backstack
        backstack = backstack.subList(0, backstack.size - 2)
        router.setBackstack(backstack, HorizontalChangeHandler())
    }

    private fun fetchRoomInfo() {
        ncApi.getRoom(credentials, ApiUtils.getRoom(conversationUser!!.baseUrl, conversationToken))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(object : Observer<RoomOverall> {
                    override fun onSubscribe(d: Disposable) {
                        roomDisposable = d
                    }

                    override fun onNext(roomOverall: RoomOverall) {
                        conversation = roomOverall.ocs.data

                        val conversationCopy = conversation

                        if (conversationCopy!!.canModerate(conversationUser)) {
                            addParticipantsAction.visibility = View.VISIBLE
                        } else {
                            addParticipantsAction.visibility = View.GONE
                        }

                        if (isAttached && (!isBeingDestroyed || !isDestroyed)) {
                            ownOptionsCategory.visibility = View.VISIBLE

                            setupWebinaryView()

                            if (!conversation!!.canLeave(conversationUser)) {
                                leaveConversationAction.visibility = View.GONE
                            } else {
                                leaveConversationAction.visibility = View.VISIBLE
                            }

                            if (!conversation!!.canModerate(conversationUser)) {
                                deleteConversationAction.visibility = View.GONE
                            } else {
                                deleteConversationAction.visibility = View.VISIBLE
                            }

                            if (Conversation.ConversationType.ROOM_SYSTEM == conversation!!.type) {
                                muteCalls.visibility = View.GONE
                            }

                            getListOfParticipants()

                            progressBar.visibility = View.GONE

                            nameCategoryView.visibility = View.VISIBLE

                            conversationDisplayName.text = conversation!!.displayName


                            loadConversationAvatar()
                            adjustNotificationLevelUI()

                            notificationsPreferenceScreen.visibility = View.VISIBLE
                        }
                    }

                    override fun onError(e: Throwable) {

                    }

                    override fun onComplete() {
                        roomDisposable!!.dispose()
                    }
                })
    }

    private fun adjustNotificationLevelUI() {
        if (conversation != null) {
            if (conversationUser != null && conversationUser.hasSpreedFeatureCapability("notification-levels")) {
                messageNotificationLevel.isEnabled = true
                messageNotificationLevel.alpha = 1.0f

                if (conversation!!.notificationLevel != Conversation.NotificationLevel.DEFAULT) {
                    val stringValue: String = when (EnumNotificationLevelConverter().convertToInt(conversation!!.notificationLevel)) {
                        1 -> "always"
                        2 -> "mention"
                        3 -> "never"
                        else -> "mention"
                    }

                    messageNotificationLevel.value = stringValue
                } else {
                    setProperNotificationValue(conversation)
                }
            } else {
                messageNotificationLevel.isEnabled = false
                messageNotificationLevel.alpha = 0.38f
                setProperNotificationValue(conversation)
            }
        }
    }

    private fun setProperNotificationValue(conversation: Conversation?) {
        if (conversation!!.type == Conversation.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL) {
            // hack to see if we get mentioned always or just on mention
            if (conversationUser!!.hasSpreedFeatureCapability("mention-flag")) {
                messageNotificationLevel.value = "always"
            } else {
                messageNotificationLevel.value = "mention"
            }
        } else {
            messageNotificationLevel.value = "mention"
        }
    }

    private fun loadConversationAvatar() {
        when (conversation!!.type) {
            Conversation.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL -> if (!TextUtils.isEmpty
                    (conversation!!.name)) {
                val draweeController = Fresco.newDraweeControllerBuilder()
                        .setOldController(conversationAvatarImageView.controller)
                        .setAutoPlayAnimations(true)
                        .setImageRequest(DisplayUtils.getImageRequestForUrl(ApiUtils.getUrlForAvatarWithName(conversationUser!!.baseUrl,
                                conversation!!.name, R.dimen.avatar_size_big), conversationUser))
                        .build()
                conversationAvatarImageView.controller = draweeController
            }
            Conversation.ConversationType.ROOM_GROUP_CALL -> conversationAvatarImageView.hierarchy.setPlaceholderImage(DisplayUtils
                    .getRoundedBitmapDrawableFromVectorDrawableResource(resources,
                            R.drawable.ic_people_group_white_24px))
            Conversation.ConversationType.ROOM_PUBLIC_CALL -> conversationAvatarImageView.hierarchy.setPlaceholderImage(DisplayUtils
                    .getRoundedBitmapDrawableFromVectorDrawableResource(resources,
                            R.drawable.ic_link_white_24px))
            Conversation.ConversationType.ROOM_SYSTEM -> {
                val layers = arrayOfNulls<Drawable>(2)
                layers[0] = context.getDrawable(R.drawable.ic_launcher_background)
                layers[1] = context.getDrawable(R.drawable.ic_launcher_foreground)
                val layerDrawable = LayerDrawable(layers)
                conversationAvatarImageView.hierarchy.setPlaceholderImage(DisplayUtils.getRoundedDrawable(layerDrawable))
            }

            else -> {
            }
        }
    }

    override fun onItemClick(view: View?, position: Int): Boolean {
        val userItem = adapter?.getItem(position) as UserItem
        val participant = userItem.model


        if (participant.userId != conversationUser!!.userId) {
            var items = mutableListOf(
                    BasicListItemWithImage(R.drawable.ic_pencil_grey600_24dp, context.getString(R.string.nc_promote)),
                    BasicListItemWithImage(R.drawable.ic_pencil_grey600_24dp, context.getString(R.string.nc_demote)),
                    BasicListItemWithImage(R.drawable.ic_delete_grey600_24dp,
                            context.getString(R.string.nc_remove_participant))
            )

            if (!conversation!!.canModerate(conversationUser)) {
                items = mutableListOf()
            } else {
                if (participant.type == Participant.ParticipantType.MODERATOR || participant.type == Participant.ParticipantType.OWNER) {
                    items.removeAt(0)
                } else if (participant.type == Participant.ParticipantType.USER) {
                    items.removeAt(1)
                }
            }


            if (items.isNotEmpty()) {
                MaterialDialog(activity!!, BottomSheet(WRAP_CONTENT)).show {
                    cornerRadius(res = R.dimen.corner_radius)

                    title(text = participant.displayName)
                    listItemsWithImage(items = items) { dialog, index, _ ->

                        if (index == 0) {
                            if (participant.type == Participant.ParticipantType.MODERATOR) {
                                ncApi.demoteModeratorToUser(credentials, ApiUtils.getUrlForModerators(conversationUser.baseUrl, conversation!!.token), participant.userId)
                                        .subscribeOn(Schedulers.io())
                                        .observeOn(AndroidSchedulers.mainThread())
                                        .subscribe {
                                            getListOfParticipants()
                                        }
                            } else if (participant.type == Participant.ParticipantType.USER) {
                                ncApi.promoteUserToModerator(credentials, ApiUtils.getUrlForModerators(conversationUser.baseUrl, conversation!!.token), participant.userId)
                                        .subscribeOn(Schedulers.io())
                                        .observeOn(AndroidSchedulers.mainThread())
                                        .subscribe {
                                            getListOfParticipants()
                                        }
                            }
                        } else if (index == 1) {
                            if (participant.type == Participant.ParticipantType.GUEST ||
                                    participant.type == Participant.ParticipantType.USER_FOLLOWING_LINK) {
                                ncApi.removeParticipantFromConversation(credentials, ApiUtils.getUrlForRemovingParticipantFromConversation(conversationUser.baseUrl, conversation!!.token, true), participant.sessionId)
                                        .subscribeOn(Schedulers.io())
                                        .observeOn(AndroidSchedulers.mainThread())
                                        .subscribe {
                                            getListOfParticipants()
                                        }

                            } else {
                                ncApi.removeParticipantFromConversation(credentials, ApiUtils.getUrlForRemovingParticipantFromConversation(conversationUser.baseUrl, conversation!!.token, false), participant.userId)
                                        .subscribeOn(Schedulers.io())
                                        .observeOn(AndroidSchedulers.mainThread())
                                        .subscribe {
                                            getListOfParticipants()
                                            // get participants again
                                        }
                            }
                        }
                    }
                }
            }
        }

        return true;
    }

    companion object {

        private const val ID_DELETE_CONVERSATION_DIALOG = 0
    }
}
