/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Marcel Hibbe
 * @author Andy Scherzinger
 * Copyright (C) 2017 Mario Danic <mario@lovelyhq.com>
 * Copyright (C) 2022 Marcel Hibbe <dev@mhibbe.de>
 * Copyright (C) 2022 Andy Scherzinger <info@andy-scherzinger.de>
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

import android.app.SearchManager
import android.content.Context
import android.graphics.PorterDuff
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.appcompat.widget.SearchView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.MenuItemCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import autodagger.AutoInjector
import com.bluelinelabs.logansquare.LoganSquare
import com.nextcloud.talk.R
import com.nextcloud.talk.adapters.items.ContactItem
import com.nextcloud.talk.adapters.items.GenericTextHeaderItem
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.controllers.base.BaseController
import com.nextcloud.talk.controllers.bottomsheet.ConversationOperationEnum
import com.nextcloud.talk.controllers.util.viewBinding
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.ControllerContactsRvBinding
import com.nextcloud.talk.events.OpenConversationEvent
import com.nextcloud.talk.jobs.AddParticipantsToConversation
import com.nextcloud.talk.models.RetrofitBucket
import com.nextcloud.talk.models.json.autocomplete.AutocompleteOverall
import com.nextcloud.talk.models.json.autocomplete.AutocompleteUser
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.conversations.RoomOverall
import com.nextcloud.talk.models.json.converters.EnumActorTypeConverter
import com.nextcloud.talk.models.json.participants.Participant
import com.nextcloud.talk.ui.dialog.ContactsBottomDialog
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.ConductorRemapping
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.database.user.CapabilitiesUtilNew
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.SelectableAdapter
import eu.davidea.flexibleadapter.common.SmoothScrollLinearLayoutManager
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import okhttp3.ResponseBody
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.parceler.Parcels
import java.io.IOException
import java.util.Collections
import java.util.Locale
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class ContactsController(args: Bundle) :
    BaseController(R.layout.controller_contacts_rv),
    SearchView.OnQueryTextListener,
    FlexibleAdapter.OnItemClickListener {
    private val binding: ControllerContactsRvBinding by viewBinding(ControllerContactsRvBinding::bind)

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var eventBus: EventBus

    @Inject
    lateinit var ncApi: NcApi

    private var credentials: String? = null
    private var currentUser: User? = null
    private var contactsQueryDisposable: Disposable? = null
    private var cacheQueryDisposable: Disposable? = null
    private var adapter: FlexibleAdapter<*>? = null
    private var contactItems: MutableList<AbstractFlexibleItem<*>>? = null
    private var layoutManager: SmoothScrollLinearLayoutManager? = null
    private var searchItem: MenuItem? = null
    private var searchView: SearchView? = null
    private var isNewConversationView = false
    private var isPublicCall = false
    private var userHeaderItems: HashMap<String, GenericTextHeaderItem> = HashMap<String, GenericTextHeaderItem>()
    private var alreadyFetching = false
    private var doneMenuItem: MenuItem? = null
    private var selectedUserIds: MutableSet<String> = HashSet()
    private var selectedGroupIds: MutableSet<String> = HashSet()
    private var selectedCircleIds: MutableSet<String> = HashSet()
    private var selectedEmails: MutableSet<String> = HashSet()
    private var existingParticipants: List<String>? = null
    private var isAddingParticipantsView = false
    private var conversationToken: String? = null
    private var contactsBottomDialog: ContactsBottomDialog? = null

    init {
        setHasOptionsMenu(true)
        sharedApplication!!.componentApplication.inject(this)

        if (args.containsKey(BundleKeys.KEY_NEW_CONVERSATION)) {
            isNewConversationView = true
            existingParticipants = ArrayList()
        } else if (args.containsKey(BundleKeys.KEY_ADD_PARTICIPANTS)) {
            isAddingParticipantsView = true
            conversationToken = args.getString(BundleKeys.KEY_TOKEN)
            existingParticipants = ArrayList()
            if (args.containsKey(BundleKeys.KEY_EXISTING_PARTICIPANTS)) {
                existingParticipants = args.getStringArrayList(BundleKeys.KEY_EXISTING_PARTICIPANTS)
            }
        }
        selectedUserIds = HashSet()
        selectedGroupIds = HashSet()
        selectedEmails = HashSet()
        selectedCircleIds = HashSet()
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        eventBus.register(this)
        if (isNewConversationView) {
            toggleConversationPrivacyLayout(!isPublicCall)
        }
        if (isAddingParticipantsView) {
            binding.joinConversationViaLink.joinConversationViaLinkRelativeLayout.visibility = View.GONE
            binding.conversationPrivacyToggle.callHeaderLayout.visibility = View.GONE
        } else {
            binding.joinConversationViaLink.joinConversationViaLinkRelativeLayout.setOnClickListener {
                joinConversationViaLink()
            }
            binding.conversationPrivacyToggle.callHeaderLayout.setOnClickListener {
                toggleCallHeader()
            }
        }
    }

    override fun onViewBound(view: View) {
        super.onViewBound(view)
        currentUser = userManager.currentUser.blockingGet()
        if (currentUser != null) {
            credentials = ApiUtils.getCredentials(currentUser!!.username, currentUser!!.token)
        }
        if (adapter == null) {
            contactItems = ArrayList<AbstractFlexibleItem<*>>()
            adapter = FlexibleAdapter(contactItems, activity, false)
            if (currentUser != null) {
                fetchData()
            }
        }
        setupAdapter()
        prepareViews()
    }

    private fun setupAdapter() {
        adapter?.setNotifyChangeOfUnfilteredItems(true)?.mode = SelectableAdapter.Mode.MULTI
        adapter?.setStickyHeaderElevation(HEADER_ELEVATION)
            ?.setUnlinkAllItemsOnRemoveHeaders(true)
            ?.setDisplayHeadersAtStartUp(true)
            ?.setStickyHeaders(true)
        adapter?.addListener(this)
    }

    private fun selectionDone() {
        if (!isAddingParticipantsView) {
            if (!isPublicCall && selectedCircleIds.size + selectedGroupIds.size + selectedUserIds.size == 1) {
                val userId: String
                var sourceType: String? = null
                var roomType = "1"
                when {
                    selectedGroupIds.size == 1 -> {
                        roomType = "2"
                        userId = selectedGroupIds.iterator().next()
                    }
                    selectedCircleIds.size == 1 -> {
                        roomType = "2"
                        sourceType = "circles"
                        userId = selectedCircleIds.iterator().next()
                    }
                    else -> {
                        userId = selectedUserIds.iterator().next()
                    }
                }
                createRoom(roomType, sourceType, userId)
            } else {
                val bundle = Bundle()
                val roomType: Conversation.ConversationType = if (isPublicCall) {
                    Conversation.ConversationType.ROOM_PUBLIC_CALL
                } else {
                    Conversation.ConversationType.ROOM_GROUP_CALL
                }
                val userIdsArray = ArrayList(selectedUserIds)
                val groupIdsArray = ArrayList(selectedGroupIds)
                val emailsArray = ArrayList(selectedEmails)
                val circleIdsArray = ArrayList(selectedCircleIds)
                bundle.putParcelable(BundleKeys.KEY_CONVERSATION_TYPE, Parcels.wrap(roomType))
                bundle.putStringArrayList(BundleKeys.KEY_INVITED_PARTICIPANTS, userIdsArray)
                bundle.putStringArrayList(BundleKeys.KEY_INVITED_GROUP, groupIdsArray)
                bundle.putStringArrayList(BundleKeys.KEY_INVITED_EMAIL, emailsArray)
                bundle.putStringArrayList(BundleKeys.KEY_INVITED_CIRCLE, circleIdsArray)
                bundle.putSerializable(BundleKeys.KEY_OPERATION_CODE, ConversationOperationEnum.OPS_CODE_INVITE_USERS)
                prepareAndShowBottomSheetWithBundle(bundle)
            }
        } else {
            addParticipantsToConversation()
        }
    }

    private fun createRoom(roomType: String, sourceType: String?, userId: String) {
        val apiVersion: Int = ApiUtils.getConversationApiVersion(currentUser, intArrayOf(ApiUtils.APIv4, 1))
        val retrofitBucket: RetrofitBucket = ApiUtils.getRetrofitBucketForCreateRoom(
            apiVersion,
            currentUser!!.baseUrl,
            roomType,
            sourceType,
            userId,
            null
        )
        ncApi.createRoom(
            credentials,
            retrofitBucket.url, retrofitBucket.queryMap
        )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : Observer<RoomOverall> {
                override fun onSubscribe(d: Disposable) {
                    // unused atm
                }

                override fun onNext(roomOverall: RoomOverall) {
                    val bundle = Bundle()
                    bundle.putParcelable(BundleKeys.KEY_USER_ENTITY, currentUser)
                    bundle.putString(BundleKeys.KEY_ROOM_TOKEN, roomOverall.ocs!!.data!!.token)
                    bundle.putString(BundleKeys.KEY_ROOM_ID, roomOverall.ocs!!.data!!.roomId)

                    // FIXME once APIv2 or later is used only, the createRoom already returns all the data
                    ncApi.getRoom(
                        credentials,
                        ApiUtils.getUrlForRoom(
                            apiVersion, currentUser!!.baseUrl,
                            roomOverall.ocs!!.data!!.token
                        )
                    )
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(object : Observer<RoomOverall> {
                            override fun onSubscribe(d: Disposable) {
                                // unused atm
                            }

                            override fun onNext(roomOverall: RoomOverall) {
                                bundle.putParcelable(
                                    BundleKeys.KEY_ACTIVE_CONVERSATION,
                                    Parcels.wrap(roomOverall.ocs!!.data!!)
                                )
                                ConductorRemapping.remapChatController(
                                    router, currentUser!!.id!!,
                                    roomOverall.ocs!!.data!!.token!!, bundle, true
                                )
                            }

                            override fun onError(e: Throwable) {
                                // unused atm
                            }

                            override fun onComplete() {
                                // unused atm
                            }
                        })
                }

                override fun onError(e: Throwable) {
                    // unused atm
                }

                override fun onComplete() {
                    // unused atm
                }
            })
    }

    private fun addParticipantsToConversation() {
        val userIdsArray: Array<String> = selectedUserIds.toTypedArray()
        val groupIdsArray: Array<String> = selectedGroupIds.toTypedArray()
        val emailsArray: Array<String> = selectedEmails.toTypedArray()
        val circleIdsArray: Array<String> = selectedCircleIds.toTypedArray()
        val data = Data.Builder()
        data.putLong(BundleKeys.KEY_INTERNAL_USER_ID, currentUser!!.id!!)
        data.putString(BundleKeys.KEY_TOKEN, conversationToken)
        data.putStringArray(BundleKeys.KEY_SELECTED_USERS, userIdsArray)
        data.putStringArray(BundleKeys.KEY_SELECTED_GROUPS, groupIdsArray)
        data.putStringArray(BundleKeys.KEY_SELECTED_EMAILS, emailsArray)
        data.putStringArray(BundleKeys.KEY_SELECTED_CIRCLES, circleIdsArray)
        val addParticipantsToConversationWorker: OneTimeWorkRequest = OneTimeWorkRequest.Builder(
            AddParticipantsToConversation::class.java
        ).setInputData(data.build()).build()
        WorkManager.getInstance().enqueue(addParticipantsToConversationWorker)
        router.popCurrentController()
    }

    private fun initSearchView() {
        if (activity != null) {
            val searchManager: SearchManager? = activity?.getSystemService(Context.SEARCH_SERVICE) as SearchManager?
            if (searchItem != null) {
                searchView = MenuItemCompat.getActionView(searchItem) as SearchView
                viewThemeUtils.talk.themeSearchView(searchView!!)
                searchView!!.maxWidth = Int.MAX_VALUE
                searchView!!.inputType = InputType.TYPE_TEXT_VARIATION_FILTER
                var imeOptions: Int = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_FULLSCREEN
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    appPreferences?.isKeyboardIncognito == true
                ) {
                    imeOptions = imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                }
                searchView!!.imeOptions = imeOptions
                searchView!!.queryHint = resources!!.getString(R.string.nc_search)
                if (searchManager != null) {
                    searchView!!.setSearchableInfo(searchManager.getSearchableInfo(activity?.componentName))
                }
                searchView!!.setOnQueryTextListener(this)
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.home -> {
                router.popCurrentController()
            }
            R.id.contacts_selection_done -> {
                selectionDone()
                true
            }
            else -> {
                super.onOptionsItemSelected(item)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_contacts, menu)
        searchItem = menu.findItem(R.id.action_search)
        doneMenuItem = menu.findItem(R.id.contacts_selection_done)
        initSearchView()
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)

        if (searchItem != null) {
            viewThemeUtils.platform.colorToolbarMenuIcon(
                binding.titleTextView.context,
                searchItem!!
            )
        }

        checkAndHandleDoneMenuItem()
        if (adapter?.hasFilter() == true) {
            searchItem!!.expandActionView()
            searchView!!.setQuery(adapter!!.getFilter(String::class.java) as CharSequence, false)
        }
    }

    private fun fetchData() {
        dispose(null)
        alreadyFetching = true
        userHeaderItems = HashMap<String, GenericTextHeaderItem>()
        val query = adapter!!.getFilter(String::class.java) as String?
        val retrofitBucket: RetrofitBucket =
            ApiUtils.getRetrofitBucketForContactsSearchFor14(currentUser!!.baseUrl, query)
        val modifiedQueryMap: HashMap<String, Any?> = HashMap<String, Any?>(retrofitBucket.queryMap)
        modifiedQueryMap.put("limit", CONTACTS_BATCH_SIZE)
        if (isAddingParticipantsView) {
            modifiedQueryMap.put("itemId", conversationToken)
        }
        val shareTypesList: ArrayList<String> = ArrayList()
        // users
        shareTypesList.add("0")
        if (!isAddingParticipantsView) {
            // groups
            shareTypesList.add("1")
        } else if (CapabilitiesUtilNew.hasSpreedFeatureCapability(currentUser, "invite-groups-and-mails")) {
            // groups
            shareTypesList.add("1")
            // emails
            shareTypesList.add("4")
        }
        if (CapabilitiesUtilNew.hasSpreedFeatureCapability(currentUser, "circles-support")) {
            // circles
            shareTypesList.add("7")
        }
        modifiedQueryMap.put("shareTypes[]", shareTypesList)
        ncApi.getContactsWithSearchParam(
            credentials,
            retrofitBucket.url, shareTypesList, modifiedQueryMap
        )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .retry(RETRIES)
            .subscribe(object : Observer<ResponseBody> {
                override fun onSubscribe(d: Disposable) {
                    contactsQueryDisposable = d
                }

                override fun onNext(responseBody: ResponseBody) {
                    val newUserItemList = processAutocompleteUserList(responseBody)

                    userHeaderItems = HashMap<String, GenericTextHeaderItem>()
                    contactItems!!.addAll(newUserItemList)

                    sortUserItems(newUserItemList)

                    if (newUserItemList.size > 0) {
                        adapter?.updateDataSet(newUserItemList as List<Nothing>?)
                    } else {
                        adapter?.filterItems()
                    }

                    withNullableControllerViewBinding {
                        binding.controllerGenericRv.swipeRefreshLayout.isRefreshing = false
                    }
                }

                override fun onError(e: Throwable) {
                    withNullableControllerViewBinding {
                        binding.controllerGenericRv.swipeRefreshLayout.isRefreshing = false
                    }
                    dispose(contactsQueryDisposable)
                }

                override fun onComplete() {
                    withNullableControllerViewBinding {
                        binding.controllerGenericRv.swipeRefreshLayout.isRefreshing = false
                    }
                    dispose(contactsQueryDisposable)
                    alreadyFetching = false
                    disengageProgressBar()
                }
            })
    }

    private fun processAutocompleteUserList(responseBody: ResponseBody): MutableList<AbstractFlexibleItem<*>> {
        try {
            val autocompleteOverall: AutocompleteOverall = LoganSquare.parse<AutocompleteOverall>(
                responseBody.string(),
                AutocompleteOverall::class.java
            )
            val autocompleteUsersList: ArrayList<AutocompleteUser> = ArrayList<AutocompleteUser>()
            autocompleteUsersList.addAll(autocompleteOverall.ocs!!.data!!)
            return processAutocompleteUserList(autocompleteUsersList)
        } catch (ioe: IOException) {
            Log.e(TAG, "Parsing response body failed while getting contacts", ioe)
        }

        return ArrayList<AbstractFlexibleItem<*>>()
    }

    private fun processAutocompleteUserList(
        autocompleteUsersList: ArrayList<AutocompleteUser>
    ): MutableList<AbstractFlexibleItem<*>> {
        var participant: Participant
        val actorTypeConverter = EnumActorTypeConverter()
        val newUserItemList: MutableList<AbstractFlexibleItem<*>> = ArrayList<AbstractFlexibleItem<*>>()
        for (autocompleteUser in autocompleteUsersList) {
            if (autocompleteUser.id != currentUser!!.userId &&
                !existingParticipants!!.contains(autocompleteUser.id!!)
            ) {
                participant = createParticipant(autocompleteUser, actorTypeConverter)
                val headerTitle = getHeaderTitle(participant)
                var genericTextHeaderItem: GenericTextHeaderItem
                if (!userHeaderItems.containsKey(headerTitle)) {
                    genericTextHeaderItem = GenericTextHeaderItem(headerTitle, viewThemeUtils)
                    userHeaderItems.put(headerTitle, genericTextHeaderItem)
                }
                val newContactItem = ContactItem(
                    participant,
                    currentUser,
                    userHeaderItems[headerTitle],
                    viewThemeUtils
                )
                if (!contactItems!!.contains(newContactItem)) {
                    newUserItemList.add(newContactItem)
                }
            }
        }
        return newUserItemList
    }

    private fun getHeaderTitle(participant: Participant): String {
        return when {
            participant.calculatedActorType == Participant.ActorType.GROUPS -> {
                resources!!.getString(R.string.nc_groups)
            }
            participant.calculatedActorType == Participant.ActorType.CIRCLES -> {
                resources!!.getString(R.string.nc_circles)
            }
            else -> {
                participant.displayName!!.substring(0, 1).toUpperCase(Locale.getDefault())
            }
        }
    }

    private fun createParticipant(
        autocompleteUser: AutocompleteUser,
        actorTypeConverter: EnumActorTypeConverter
    ): Participant {
        val participant = Participant()
        participant.actorId = autocompleteUser.id
        participant.actorType = actorTypeConverter.getFromString(autocompleteUser.source)
        participant.displayName = autocompleteUser.label
        participant.source = autocompleteUser.source

        return participant
    }

    private fun sortUserItems(newUserItemList: MutableList<AbstractFlexibleItem<*>>) {
        Collections.sort(
            newUserItemList,
            { o1: AbstractFlexibleItem<*>, o2: AbstractFlexibleItem<*> ->
                val firstName: String = if (o1 is ContactItem) {
                    (o1 as ContactItem).model.displayName!!
                } else {
                    (o1 as GenericTextHeaderItem).model
                }
                val secondName: String = if (o2 is ContactItem) {
                    (o2 as ContactItem).model.displayName!!
                } else {
                    (o2 as GenericTextHeaderItem).model
                }
                if (o1 is ContactItem && o2 is ContactItem) {
                    val firstSource: String = (o1 as ContactItem).model.source!!
                    val secondSource: String = (o2 as ContactItem).model.source!!
                    if (firstSource == secondSource) {
                        return@sort firstName.compareTo(secondName, ignoreCase = true)
                    }

                    // First users
                    if ("users" == firstSource) {
                        return@sort -1
                    } else if ("users" == secondSource) {
                        return@sort 1
                    }

                    // Then groups
                    if ("groups" == firstSource) {
                        return@sort -1
                    } else if ("groups" == secondSource) {
                        return@sort 1
                    }

                    // Then circles
                    if ("circles" == firstSource) {
                        return@sort -1
                    } else if ("circles" == secondSource) {
                        return@sort 1
                    }

                    // Otherwise fall back to name sorting
                    return@sort firstName.compareTo(secondName, ignoreCase = true)
                }
                firstName.compareTo(secondName, ignoreCase = true)
            }
        )

        Collections.sort(
            contactItems
        ) { o1: AbstractFlexibleItem<*>, o2: AbstractFlexibleItem<*> ->
            val firstName: String = if (o1 is ContactItem) {
                (o1 as ContactItem).model.displayName!!
            } else {
                (o1 as GenericTextHeaderItem).model
            }
            val secondName: String = if (o2 is ContactItem) {
                (o2 as ContactItem).model.displayName!!
            } else {
                (o2 as GenericTextHeaderItem).model
            }
            if (o1 is ContactItem && o2 is ContactItem) {
                if ("groups" == (o1 as ContactItem).model.source &&
                    "groups" == (o2 as ContactItem).model.source
                ) {
                    return@sort firstName.compareTo(secondName, ignoreCase = true)
                } else if ("groups" == (o1 as ContactItem).model.source) {
                    return@sort -1
                } else if ("groups" == (o2 as ContactItem).model.source) {
                    return@sort 1
                }
            }
            firstName.compareTo(secondName, ignoreCase = true)
        }
    }

    private fun prepareViews() {
        layoutManager = SmoothScrollLinearLayoutManager(activity)
        binding.controllerGenericRv.recyclerView.layoutManager = layoutManager
        binding.controllerGenericRv.recyclerView.setHasFixedSize(true)
        binding.controllerGenericRv.recyclerView.adapter = adapter
        binding.controllerGenericRv.swipeRefreshLayout.setOnRefreshListener { fetchData() }

        viewThemeUtils.androidx.themeSwipeRefreshLayout(binding.controllerGenericRv.swipeRefreshLayout)

        binding.joinConversationViaLink.joinConversationViaLinkImageView
            .background
            .setColorFilter(
                ResourcesCompat.getColor(resources!!, R.color.colorBackgroundDarker, null),
                PorterDuff.Mode.SRC_IN
            )

        viewThemeUtils.platform.colorImageViewButton(binding.conversationPrivacyToggle.publicCallLink)
        disengageProgressBar()
    }

    private fun disengageProgressBar() {
        if (!alreadyFetching) {
            binding.loadingContent.visibility = View.GONE
            binding.controllerGenericRv.root.visibility = View.VISIBLE
            if (isNewConversationView) {
                binding.conversationPrivacyToggle.callHeaderLayout.visibility = View.VISIBLE
                binding.joinConversationViaLink.joinConversationViaLinkRelativeLayout.visibility = View.VISIBLE
            }
        }
    }

    private fun dispose(disposable: Disposable?) {
        if (disposable != null && !disposable.isDisposed) {
            disposable.dispose()
        } else if (disposable == null) {
            if (contactsQueryDisposable != null && !contactsQueryDisposable!!.isDisposed) {
                contactsQueryDisposable!!.dispose()
                contactsQueryDisposable = null
            }
            if (cacheQueryDisposable != null && !cacheQueryDisposable!!.isDisposed) {
                cacheQueryDisposable!!.dispose()
                cacheQueryDisposable = null
            }
        }
    }

    override fun onSaveViewState(view: View, outState: Bundle) {
        adapter?.onSaveInstanceState(outState)
        super.onSaveViewState(view, outState)
    }

    override fun onRestoreViewState(view: View, savedViewState: Bundle) {
        super.onRestoreViewState(view, savedViewState)
        if (adapter != null) {
            adapter?.onRestoreInstanceState(savedViewState)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        dispose(null)
    }

    override fun onQueryTextChange(newText: String): Boolean {
        if (newText != "" && adapter?.hasNewFilter(newText) == true) {
            adapter?.setFilter(newText)
            fetchData()
        } else if (newText == "") {
            adapter?.setFilter("")
            adapter?.updateDataSet(contactItems as List<Nothing>?)
        }

        withNullableControllerViewBinding {
            binding.controllerGenericRv.swipeRefreshLayout.isEnabled = !adapter!!.hasFilter()
        }

        return true
    }

    override fun onQueryTextSubmit(query: String): Boolean {
        return onQueryTextChange(query)
    }

    private fun checkAndHandleDoneMenuItem() {
        if (adapter != null && doneMenuItem != null) {
            doneMenuItem!!.isVisible =
                selectedCircleIds.size + selectedEmails.size + selectedGroupIds.size + selectedUserIds.size > 0 ||
                isPublicCall
        } else if (doneMenuItem != null) {
            doneMenuItem!!.isVisible = false
        }
    }

    override val title: String
        get() = when {
            isAddingParticipantsView -> {
                resources!!.getString(R.string.nc_add_participants)
            }
            isNewConversationView -> {
                resources!!.getString(R.string.nc_select_participants)
            }
            else -> {
                resources!!.getString(R.string.nc_app_product_name)
            }
        }

    private fun prepareAndShowBottomSheetWithBundle(bundle: Bundle) {
        // 11: create conversation-enter name for new conversation
        // 10: get&join room when enter link
        contactsBottomDialog = ContactsBottomDialog(activity!!, bundle)
        contactsBottomDialog?.show()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(openConversationEvent: OpenConversationEvent) {
        ConductorRemapping.remapChatController(
            router, currentUser!!.id!!,
            openConversationEvent.conversation!!.token!!,
            openConversationEvent.bundle!!, true
        )
        contactsBottomDialog?.dismiss()
    }

    override fun onDetach(view: View) {
        super.onDetach(view)
        eventBus.unregister(this)
    }

    override fun onItemClick(view: View, position: Int): Boolean {
        if (adapter?.getItem(position) is ContactItem) {
            if (!isNewConversationView && !isAddingParticipantsView) {
                createRoom(adapter?.getItem(position) as ContactItem)
            } else {
                val participant: Participant = (adapter?.getItem(position) as ContactItem).model
                updateSelection((adapter?.getItem(position) as ContactItem))
            }
        }
        return true
    }

    private fun updateSelection(contactItem: ContactItem) {
        contactItem.model.selected = !contactItem.model.selected
        updateSelectionLists(contactItem.model)
        if (CapabilitiesUtilNew.hasSpreedFeatureCapability(currentUser, "last-room-activity") &&
            !CapabilitiesUtilNew.hasSpreedFeatureCapability(currentUser, "invite-groups-and-mails") &&
            isValidGroupSelection(contactItem, contactItem.model, adapter)
        ) {
            val currentItems: List<ContactItem> = adapter?.currentItems as List<ContactItem>
            var internalParticipant: Participant
            for (i in currentItems.indices) {
                internalParticipant = currentItems[i].model
                if (internalParticipant.calculatedActorId == contactItem.model.calculatedActorId &&
                    internalParticipant.calculatedActorType == Participant.ActorType.GROUPS &&
                    internalParticipant.selected
                ) {
                    internalParticipant.selected = false
                    selectedGroupIds.remove(internalParticipant.calculatedActorId!!)
                }
            }
        }
        adapter?.notifyDataSetChanged()
        checkAndHandleDoneMenuItem()
    }

    private fun createRoom(contactItem: ContactItem) {
        var roomType = "1"
        if ("groups" == contactItem.model.source) {
            roomType = "2"
        }
        val apiVersion: Int = ApiUtils.getConversationApiVersion(currentUser, intArrayOf(ApiUtils.APIv4, 1))
        val retrofitBucket: RetrofitBucket = ApiUtils.getRetrofitBucketForCreateRoom(
            apiVersion,
            currentUser!!.baseUrl,
            roomType,
            null,
            contactItem.model.calculatedActorId,
            null
        )
        ncApi.createRoom(
            credentials,
            retrofitBucket.url, retrofitBucket.queryMap
        )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : Observer<RoomOverall> {
                override fun onSubscribe(d: Disposable) {
                    // unused atm
                }

                override fun onNext(roomOverall: RoomOverall) {
                    if (activity != null) {
                        val bundle = Bundle()
                        bundle.putParcelable(BundleKeys.KEY_USER_ENTITY, currentUser)
                        bundle.putString(BundleKeys.KEY_ROOM_TOKEN, roomOverall.ocs!!.data!!.token)
                        bundle.putString(BundleKeys.KEY_ROOM_ID, roomOverall.ocs!!.data!!.roomId)
                        bundle.putParcelable(
                            BundleKeys.KEY_ACTIVE_CONVERSATION,
                            Parcels.wrap(roomOverall.ocs!!.data!!)
                        )
                        ConductorRemapping.remapChatController(
                            router,
                            currentUser!!.id!!,
                            roomOverall.ocs!!.data!!.token!!,
                            bundle,
                            true
                        )
                    }
                }

                override fun onError(e: Throwable) {
                    // unused atm
                }

                override fun onComplete() {
                    // unused atm
                }
            })
    }

    private fun updateSelectionLists(participant: Participant) {
        if ("groups" == participant.source) {
            if (participant.selected) {
                selectedGroupIds.add(participant.calculatedActorId!!)
            } else {
                selectedGroupIds.remove(participant.calculatedActorId!!)
            }
        } else if ("emails" == participant.source) {
            if (participant.selected) {
                selectedEmails.add(participant.calculatedActorId!!)
            } else {
                selectedEmails.remove(participant.calculatedActorId!!)
            }
        } else if ("circles" == participant.source) {
            if (participant.selected) {
                selectedCircleIds.add(participant.calculatedActorId!!)
            } else {
                selectedCircleIds.remove(participant.calculatedActorId!!)
            }
        } else {
            if (participant.selected) {
                selectedUserIds.add(participant.calculatedActorId!!)
            } else {
                selectedUserIds.remove(participant.calculatedActorId!!)
            }
        }
    }

    private fun isValidGroupSelection(
        contactItem: ContactItem,
        participant: Participant,
        adapter: FlexibleAdapter<*>?
    ): Boolean {
        return "groups" == contactItem.model.source && participant.selected && adapter?.selectedItemCount!! > 1
    }

    private fun joinConversationViaLink() {
        val bundle = Bundle()
        bundle.putSerializable(BundleKeys.KEY_OPERATION_CODE, ConversationOperationEnum.OPS_CODE_GET_AND_JOIN_ROOM)
        prepareAndShowBottomSheetWithBundle(bundle)
    }

    private fun toggleCallHeader() {
        toggleConversationPrivacyLayout(isPublicCall)
        isPublicCall = !isPublicCall
        toggleConversationViaLinkVisibility(isPublicCall)

        enableContactForNonPublicCall()
        checkAndHandleDoneMenuItem()
        adapter?.notifyDataSetChanged()
    }

    private fun updateGroupParticipantSelection() {
        val currentItems: List<AbstractFlexibleItem<*>> = adapter?.currentItems as
            List<AbstractFlexibleItem<*>>
        var internalParticipant: Participant
        for (i in currentItems.indices) {
            if (currentItems[i] is ContactItem) {
                internalParticipant = (currentItems[i] as ContactItem).model
                if (internalParticipant.calculatedActorType == Participant.ActorType.GROUPS &&
                    internalParticipant.selected
                ) {
                    internalParticipant.selected = false
                    selectedGroupIds.remove(internalParticipant.calculatedActorId)
                }
            }
        }
    }

    private fun enableContactForNonPublicCall() {
        for (i in 0 until adapter!!.itemCount) {
            if (adapter?.getItem(i) is ContactItem) {
                val contactItem: ContactItem = adapter?.getItem(i) as ContactItem
                if ("groups" == contactItem.model.source) {
                    contactItem.isEnabled = !isPublicCall
                }
            }
        }
    }

    private fun toggleConversationPrivacyLayout(showInitialLayout: Boolean) {
        withNullableControllerViewBinding {
            if (showInitialLayout) {
                binding.conversationPrivacyToggle.initialRelativeLayout.visibility = View.VISIBLE
                binding.conversationPrivacyToggle.secondaryRelativeLayout.visibility = View.GONE
            } else {
                binding.conversationPrivacyToggle.initialRelativeLayout.visibility = View.GONE
                binding.conversationPrivacyToggle.secondaryRelativeLayout.visibility = View.VISIBLE
            }
        }
    }

    private fun toggleConversationViaLinkVisibility(isPublicCall: Boolean) {
        withNullableControllerViewBinding {
            if (isPublicCall) {
                binding.joinConversationViaLink.joinConversationViaLinkRelativeLayout.visibility = View.GONE
                updateGroupParticipantSelection()
            } else {
                binding.joinConversationViaLink.joinConversationViaLinkRelativeLayout.visibility = View.VISIBLE
            }
        }
    }

    companion object {
        const val TAG = "ContactsController"
        const val RETRIES: Long = 3
        const val CONTACTS_BATCH_SIZE: Int = 50
        const val HEADER_ELEVATION: Int = 5
    }
}
