/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017 Mario Danic (mario@lovelyhq.com)
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
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.text.InputType
import android.util.Log
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.ProgressBar
import android.widget.RelativeLayout
import androidx.appcompat.widget.SearchView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.MenuItemCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import butterknife.BindView
import butterknife.OnClick
import butterknife.Optional
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.VerticalChangeHandler
import com.bluelinelabs.logansquare.LoganSquare
import com.kennyc.bottomsheet.BottomSheet
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.MagicCallActivity
import com.nextcloud.talk.adapters.items.GenericTextHeaderItem
import com.nextcloud.talk.adapters.items.UserItem
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.controllers.base.BaseController
import com.nextcloud.talk.controllers.bottomsheet.EntryMenuController
import com.nextcloud.talk.controllers.bottomsheet.OperationsMenuController
import com.nextcloud.talk.events.BottomSheetLockEvent
import com.nextcloud.talk.jobs.AddParticipantsToConversation
import com.nextcloud.talk.models.RetrofitBucket
import com.nextcloud.talk.models.json.autocomplete.AutocompleteOverall
import com.nextcloud.talk.models.json.autocomplete.AutocompleteUser
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.conversations.RoomOverall
import com.nextcloud.talk.models.json.participants.Participant
import com.nextcloud.talk.newarch.domain.repository.offline.UsersRepository
import com.nextcloud.talk.newarch.local.models.UserNgEntity
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.ConductorRemapping
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.uber.autodispose.AutoDispose
import eu.davidea.fastscroller.FastScroller
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.SelectableAdapter
import eu.davidea.flexibleadapter.common.SmoothScrollLinearLayoutManager
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.koin.android.ext.android.inject
import org.parceler.Parcels
import java.util.ArrayList
import java.util.HashMap
import java.util.HashSet
import kotlin.Comparator
import kotlin.String

class ContactsController : BaseController,
        SearchView.OnQueryTextListener,
        FlexibleAdapter.OnItemClickListener,
        FastScroller.OnScrollStateChangeListener {

    val usersRepository: UsersRepository by inject()
    val ncApi: NcApi by inject()
    @JvmField
    @BindView(R.id.initial_relative_layout)
    var initialRelativeLayout: RelativeLayout? = null
    @JvmField
    @BindView(R.id.secondary_relative_layout)
    var secondaryRelativeLayout: RelativeLayout? = null
    @JvmField
    @BindView(R.id.progressBar)
    var progressBar: ProgressBar? = null
    @JvmField
    @BindView(R.id.recyclerView)
    var recyclerView: RecyclerView? = null

    @JvmField
    @BindView(R.id.swipe_refresh_layout)
    var swipeRefreshLayout: SwipeRefreshLayout? = null

    @JvmField
    @BindView(R.id.fast_scroller)
    var fastScroller: FastScroller? = null

    @JvmField
    @BindView(R.id.call_header_layout)
    var conversationPrivacyToogleLayout: RelativeLayout? = null

    @JvmField
    @BindView(R.id.joinConversationViaLinkRelativeLayout)
    var joinConversationViaLinkLayout: RelativeLayout? = null

    @JvmField
    @BindView(R.id.generic_rv_layout)
    var genericRvLayout: CoordinatorLayout? = null

    private var credentials: String? = null
    private var currentUser: UserNgEntity? = null
    private var adapter: FlexibleAdapter<AbstractFlexibleItem<*>>? = null
    private var contactItems: MutableList<AbstractFlexibleItem<*>>? = null
    private var bottomSheet: BottomSheet? = null
    private var bottomSheetView: View? = null

    private var layoutManager: SmoothScrollLinearLayoutManager? = null

    private var searchItem: MenuItem? = null
    private var searchView: SearchView? = null

    private var isNewConversationView: Boolean = false
    private var isPublicCall: Boolean = false

    private var userHeaderItems = HashMap<String, GenericTextHeaderItem>()

    private var alreadyFetching = false

    private var doneMenuItem: MenuItem? = null

    private val selectedUserIds: MutableSet<String> = mutableSetOf()
    private val selectedGroupIds: MutableSet<String> = mutableSetOf()
    private var conversationToken: String? = null

    constructor() : super() {
        setHasOptionsMenu(true)
    }

    constructor(args: Bundle) : super() {
        setHasOptionsMenu(true)
        if (args.containsKey(BundleKeys.KEY_NEW_CONVERSATION)) {
            isNewConversationView = true
        } else {
            isNewConversationView = false
            conversationToken = args.getString(BundleKeys.KEY_TOKEN)
        }
    }

    override fun inflateView(
            inflater: LayoutInflater,
            container: ViewGroup
    ): View {
        return inflater.inflate(R.layout.controller_contacts_rv, container, false)
    }

    override fun onDetach(view: View) {
        eventBus.unregister(this)
        super.onDetach(view)
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        eventBus.register(this)

        if (isNewConversationView) {
            toggleNewCallHeaderVisibility(!isPublicCall)
        } else {
            joinConversationViaLinkLayout!!.visibility = View.GONE
            conversationPrivacyToogleLayout!!.visibility = View.GONE
        }
    }

    override fun onViewBound(view: View) {
        super.onViewBound(view)


        GlobalScope.launch {
            currentUser = usersRepository.getActiveUser()

            if (currentUser != null) {
                credentials = ApiUtils.getCredentials(currentUser!!.username, currentUser!!.token)
            }

            if (adapter == null) {
                contactItems = ArrayList()
                adapter = FlexibleAdapter(contactItems, activity, false)

                if (currentUser != null) {
                    fetchData(true)
                }
            }

            setupAdapter()
            withContext(Dispatchers.Main) {
                prepareViews()
            }
        }
    }

    private fun setupAdapter() {
        adapter!!.setNotifyChangeOfUnfilteredItems(true)
                .mode = SelectableAdapter.Mode.MULTI

        adapter!!.addListener(this)
    }

    private fun selectionDone() {
        if (isNewConversationView) {
            if (!isPublicCall && selectedGroupIds.size + selectedUserIds.size == 1) {
                val userId: String
                var roomType = "1"

                if (selectedGroupIds.size == 1) {
                    roomType = "2"
                    userId = selectedGroupIds.iterator()
                            .next()
                } else {
                    userId = selectedUserIds.iterator()
                            .next()
                }

                val retrofitBucket = ApiUtils.getRetrofitBucketForCreateRoom(
                        currentUser!!.baseUrl, roomType,
                        userId, null
                )
                ncApi.createRoom(
                        credentials,
                        retrofitBucket.url, retrofitBucket.queryMap
                )
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .`as`(AutoDispose.autoDisposable(scopeProvider))
                        .subscribe(object : Observer<RoomOverall> {
                            override fun onSubscribe(d: Disposable) {

                            }

                            override fun onNext(roomOverall: RoomOverall) {
                                val conversationIntent = Intent(activity, MagicCallActivity::class.java)
                                val bundle = Bundle()
                                bundle.putParcelable(BundleKeys.KEY_USER_ENTITY, currentUser)
                                bundle.putString(
                                        BundleKeys.KEY_ROOM_TOKEN,
                                        roomOverall.ocs.data.token
                                )
                                bundle.putString(
                                        BundleKeys.KEY_ROOM_ID,
                                        roomOverall.ocs.data.conversationId
                                )

                                if (currentUser!!.hasSpreedFeatureCapability("chat-v2")) {
                                    ncApi.getRoom(
                                            credentials,
                                            ApiUtils.getRoom(
                                                    currentUser!!.baseUrl,
                                                    roomOverall.ocs.data.token
                                            )
                                    )
                                            .subscribeOn(Schedulers.io())
                                            .observeOn(AndroidSchedulers.mainThread())
                                            .`as`(AutoDispose.autoDisposable(scopeProvider))
                                            .subscribe(object : Observer<RoomOverall> {

                                                override fun onSubscribe(d: Disposable) {

                                                }

                                                override fun onNext(roomOverall: RoomOverall) {
                                                    bundle.putParcelable(
                                                            BundleKeys.KEY_ACTIVE_CONVERSATION,
                                                            Parcels.wrap(roomOverall.ocs.data)
                                                    )

                                                    ConductorRemapping.remapChatController(
                                                            router,
                                                            currentUser!!.id!!,
                                                            roomOverall.ocs.data.token!!, bundle, true
                                                    )
                                                }

                                                override fun onError(e: Throwable) {

                                                }

                                                override fun onComplete() {

                                                }
                                            })
                                } else {
                                    conversationIntent.putExtras(bundle)
                                    startActivity(conversationIntent)
                                    Handler().postDelayed({
                                        if (!isDestroyed && !isBeingDestroyed) {
                                            router.popCurrentController()
                                        }
                                    }, 100)
                                }
                            }

                            override fun onError(e: Throwable) {

                            }

                            override fun onComplete() {}
                        })
            } else {

                val bundle = Bundle()
                val roomType: Conversation.ConversationType
                if (isPublicCall) {
                    roomType = Conversation.ConversationType.PUBLIC_CONVERSATION
                } else {
                    roomType = Conversation.ConversationType.GROUP_CONVERSATION
                }

                val userIdsArray = ArrayList(selectedUserIds)
                val groupIdsArray = ArrayList(selectedGroupIds)

                bundle.putParcelable(
                        BundleKeys.KEY_CONVERSATION_TYPE,
                        Parcels.wrap(roomType)
                )
                bundle.putStringArrayList(BundleKeys.KEY_INVITED_PARTICIPANTS, userIdsArray)
                bundle.putStringArrayList(BundleKeys.KEY_INVITED_GROUP, groupIdsArray)
                bundle.putInt(BundleKeys.KEY_OPERATION_CODE, 11)
                prepareAndShowBottomSheetWithBundle(bundle, true)
            }
        } else {
            val userIdsArray = selectedUserIds.toTypedArray()
            val groupIdsArray = selectedGroupIds.toTypedArray()

            val data = Data.Builder()
            data.putLong(BundleKeys.KEY_INTERNAL_USER_ID, currentUser!!.id!!)
            data.putString(BundleKeys.KEY_TOKEN, conversationToken)
            data.putStringArray(BundleKeys.KEY_SELECTED_USERS, userIdsArray)
            data.putStringArray(BundleKeys.KEY_SELECTED_GROUPS, groupIdsArray)

            val addParticipantsToConversationWorker =
                    OneTimeWorkRequest.Builder(AddParticipantsToConversation::class.java)
                            .setInputData(
                                    data.build()
                            )
                            .build()
            WorkManager.getInstance()
                    .enqueue(addParticipantsToConversationWorker)

            router.popCurrentController()
        }
    }

    private fun initSearchView() {
        if (activity != null) {
            val searchManager = activity!!.getSystemService(Context.SEARCH_SERVICE) as SearchManager
            if (searchItem != null) {
                searchView = MenuItemCompat.getActionView(searchItem!!) as SearchView
                searchView!!.maxWidth = Integer.MAX_VALUE
                searchView!!.inputType = InputType.TYPE_TEXT_VARIATION_FILTER
                var imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_FULLSCREEN
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appPreferences.isKeyboardIncognito) {
                    imeOptions = imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                }
                searchView!!.imeOptions = imeOptions
                searchView!!.queryHint = resources!!.getString(R.string.nc_search)
                if (searchManager != null) {
                    searchView!!.setSearchableInfo(
                            searchManager.getSearchableInfo(activity!!.componentName)
                    )
                }
                searchView!!.setOnQueryTextListener(this)
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                router.popCurrentController()
                return true
            }
            R.id.contacts_selection_done -> {
                selectionDone()
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateOptionsMenu(
            menu: Menu,
            inflater: MenuInflater
    ) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_contacts, menu)
        searchItem = menu.findItem(R.id.action_search)
        doneMenuItem = menu.findItem(R.id.contacts_selection_done)

        initSearchView()
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        checkAndHandleDoneMenuItem()
        if (adapter!!.hasFilter()) {
            searchItem!!.expandActionView()
            searchView!!.setQuery(adapter!!.getFilter(String::class.java) as CharSequence?, false)
        }
    }

    private fun fetchData(startFromScratch: Boolean) {
        alreadyFetching = true
        val autocompleteUsersHashSet = HashSet<AutocompleteUser>()

        userHeaderItems = HashMap()

        val query = adapter!!.getFilter(String::class.java)

        val retrofitBucket: RetrofitBucket = ApiUtils.getRetrofitBucketForContactsSearchFor14(currentUser!!.baseUrl, query)
        val modifiedQueryMap = HashMap<String, Any>(retrofitBucket.queryMap)
        modifiedQueryMap["limit"] = 50

        var shareTypesList: MutableList<String> = mutableListOf()
        // user
        shareTypesList.add("0")
        // group
        shareTypesList.add("1")
        // remote/circles
        shareTypesList.add("7");

        conversationToken?.let {
            modifiedQueryMap["itemId"] = it
            // emails
            shareTypesList.add("4")
        }

        modifiedQueryMap["shareTypes[]"] = shareTypesList

        ncApi.getContactsWithSearchParam(
                credentials,
                retrofitBucket.url, shareTypesList, modifiedQueryMap
        )
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .retry(3)
                .`as`(AutoDispose.autoDisposable(scopeProvider))
                .subscribe(object : Observer<ResponseBody> {
                    override fun onSubscribe(d: Disposable) {}

                    override fun onNext(responseBody: ResponseBody) {
                        var participant: Participant

                        val newUserItemList = ArrayList<AbstractFlexibleItem<*>>()

                        try {
                            val autocompleteOverall =
                                    LoganSquare.parse(responseBody.string(), AutocompleteOverall::class.java)
                            autocompleteUsersHashSet.addAll(autocompleteOverall.ocs.data)

                            for (autocompleteUser in autocompleteUsersHashSet) {
                                participant = Participant()
                                participant.userId = autocompleteUser.id
                                participant.displayName = autocompleteUser.label
                                participant.source = autocompleteUser.source

                                var headerTitle: String?


                                if (autocompleteUser.source == "groups") {
                                    headerTitle = resources?.getString(R.string.nc_groups)
                                } else if (autocompleteUser.source == "users") {
                                    headerTitle = resources?.getString(R.string.nc_contacts)
                                } else if (autocompleteUser.source == "emails") {
                                    headerTitle = resources?.getString(R.string.nc_emails)
                                } else {
                                    headerTitle = ""
                                }
                                
                                headerTitle as String
                                
                                val genericTextHeaderItem: GenericTextHeaderItem
                                if (!userHeaderItems.containsKey(headerTitle)) {
                                    genericTextHeaderItem = GenericTextHeaderItem(headerTitle)
                                    userHeaderItems[headerTitle] = genericTextHeaderItem
                                }

                                val newContactItem = UserItem(
                                        participant, currentUser!!,
                                        userHeaderItems[headerTitle], activity!!
                                )

                                if (!contactItems!!.contains(newContactItem)) {
                                    newUserItemList.add(newContactItem)
                                }
                            }
                        } catch (exception: Exception) {
                            Log.e(TAG, "Parsing response body failed while getting contacts")
                        }

                        userHeaderItems = HashMap()
                        contactItems!!.addAll(newUserItemList)

                        newUserItemList.sortWith(Comparator { o1, o2 ->
                            val firstName: String
                            val secondName: String

                            if (o1 is UserItem) {
                                firstName = o1.model.displayName
                            } else {
                                firstName = (o1 as GenericTextHeaderItem).model
                            }

                            if (o2 is UserItem) {
                                secondName = o2.model.displayName
                            } else {
                                secondName = (o2 as GenericTextHeaderItem).model
                            }

                            if (o1 is UserItem && o2 is UserItem) {
                                if ("groups" == o1.model.source && "groups" == o2.model.source) {
                                    firstName.compareTo(secondName, ignoreCase = true)
                                } else if ("groups" == o1.model.source) {
                                    -1
                                } else if ("groups" == o2.model.source) {
                                    1
                                }
                            }

                            firstName.compareTo(secondName, ignoreCase = true)
                        })

                        contactItems!!.sortWith(Comparator { o1, o2 ->
                            val firstName: String
                            val secondName: String

                            if (o1 is UserItem) {
                                firstName = o1.model.displayName
                            } else {
                                firstName = (o1 as GenericTextHeaderItem).model
                            }

                            if (o2 is UserItem) {
                                secondName = o2.model.displayName
                            } else {
                                secondName = (o2 as GenericTextHeaderItem).model
                            }

                            if (o1 is UserItem && o2 is UserItem) {
                                if ("groups" == o1.model.source && "groups" == o2.model.source) {
                                    firstName.compareTo(secondName, ignoreCase = true)
                                } else if ("groups" == o1.model.source) {
                                    -1
                                } else if ("groups" == o2.model.source) {
                                    1
                                }
                            }

                            firstName.compareTo(secondName, ignoreCase = true)
                        })

                        if (newUserItemList.size > 0) {
                            adapter!!.updateDataSet(newUserItemList)
                        } else {
                            adapter!!.filterItems()
                        }

                        if (swipeRefreshLayout != null) {
                            swipeRefreshLayout!!.isRefreshing = false
                        }
                    }

                    override fun onError(e: Throwable) {
                        if (swipeRefreshLayout != null) {
                            swipeRefreshLayout!!.isRefreshing = false
                        }
                    }

                    override fun onComplete() {
                        if (swipeRefreshLayout != null) {
                            swipeRefreshLayout!!.isRefreshing = false
                        }
                        alreadyFetching = false

                        disengageProgressBar()
                    }
                })
    }

    private fun prepareViews() {
        layoutManager = SmoothScrollLinearLayoutManager(activity!!)
        recyclerView?.layoutManager = layoutManager
        recyclerView?.setHasFixedSize(true)
        recyclerView?.adapter = adapter

        adapter!!.setStickyHeaderElevation(5)
                .setUnlinkAllItemsOnRemoveHeaders(true)
                .setDisplayHeadersAtStartUp(true)
                .setStickyHeaders(true)

        swipeRefreshLayout!!.setOnRefreshListener { fetchData(true) }
        swipeRefreshLayout!!.setColorSchemeResources(R.color.colorPrimary)

        fastScroller!!.addOnScrollStateChangeListener(this)
        adapter!!.fastScroller = fastScroller
        fastScroller!!.setBubbleTextCreator { position ->
            val abstractFlexibleItem = adapter!!.getItem(position)
            if (abstractFlexibleItem is UserItem) {
                (adapter!!.getItem(position) as UserItem).header!!.model
            } else if (abstractFlexibleItem is GenericTextHeaderItem) {
                (adapter!!.getItem(position) as GenericTextHeaderItem).model
            } else {
                ""
            }
        }

        disengageProgressBar()
    }

    private fun disengageProgressBar() {
        if (!alreadyFetching) {
            progressBar!!.visibility = View.GONE
            genericRvLayout!!.visibility = View.VISIBLE

            if (isNewConversationView) {
                conversationPrivacyToogleLayout!!.visibility = View.VISIBLE
                joinConversationViaLinkLayout!!.visibility = View.VISIBLE
            }
        }
    }

    public override fun onSaveViewState(
            view: View,
            outState: Bundle
    ) {
        adapter!!.onSaveInstanceState(outState)
        super.onSaveViewState(view, outState)
    }

    public override fun onRestoreViewState(
            view: View,
            savedViewState: Bundle
    ) {
        super.onRestoreViewState(view, savedViewState)
        if (adapter != null) {
            adapter!!.onRestoreInstanceState(savedViewState)
        }
    }

    override fun onQueryTextChange(newText: String): Boolean {
        if (newText != "" && adapter!!.hasNewFilter(newText)) {
            adapter!!.setFilter(newText)
            fetchData(true)
        } else if (newText == "") {
            adapter!!.setFilter("")
            adapter!!.updateDataSet(contactItems)
        }

        if (swipeRefreshLayout != null) {
            swipeRefreshLayout!!.isEnabled = !adapter!!.hasFilter()
        }

        return true
    }

    override fun onQueryTextSubmit(query: String): Boolean {
        return onQueryTextChange(query)
    }

    private fun checkAndHandleDoneMenuItem() {
        if (adapter != null && doneMenuItem != null) {
            doneMenuItem!!.isVisible = selectedGroupIds.size + selectedUserIds.size > 0 || isPublicCall
        } else if (doneMenuItem != null) {
            doneMenuItem!!.isVisible = false
        }
    }

    override fun getTitle(): String? {
        return resources?.getString(R.string.nc_select_contacts)
    }

    override fun onFastScrollerStateChange(scrolling: Boolean) {
        swipeRefreshLayout!!.isEnabled = !scrolling
    }

    private fun prepareAndShowBottomSheetWithBundle(
            bundle: Bundle,
            showEntrySheet: Boolean
    ) {
        if (bottomSheetView == null) {
            bottomSheetView = activity!!.layoutInflater.inflate(R.layout.bottom_sheet, null, false)
        }

        if (bottomSheet == null) {
            bottomSheet = BottomSheet.Builder(activity!!)
                    .setView(bottomSheetView)
                    .create()
        }

        if (showEntrySheet) {
            getChildRouter((bottomSheetView as ViewGroup?)!!).setRoot(
                    RouterTransaction.with(EntryMenuController(bundle))
                            .popChangeHandler(VerticalChangeHandler())
                            .pushChangeHandler(VerticalChangeHandler())
            )
        } else {
            getChildRouter((bottomSheetView as ViewGroup?)!!).setRoot(
                    RouterTransaction.with(OperationsMenuController(bundle))
                            .popChangeHandler(VerticalChangeHandler())
                            .pushChangeHandler(VerticalChangeHandler())
            )
        }

        bottomSheet!!.setOnShowListener { dialog ->
            if (showEntrySheet) {
                //KeyboardUtils(activity!!, bottomSheet!!.layout, true)
            } else {
                eventBus.post(
                        BottomSheetLockEvent(
                                false, 0,
                                false, false
                        )
                )
            }
        }

        bottomSheet!!.setOnDismissListener { dialog ->
            actionBar!!.setDisplayHomeAsUpEnabled(
                    router.backstackSize > 1
            )
        }

        bottomSheet!!.show()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(bottomSheetLockEvent: BottomSheetLockEvent) {

        if (bottomSheet != null) {
            if (!bottomSheetLockEvent.cancelable) {
                bottomSheet!!.setCancelable(bottomSheetLockEvent.cancelable)
            } else {
                bottomSheet!!.setCancelable(bottomSheetLockEvent.cancelable)
                if (bottomSheet!!.isShowing && bottomSheetLockEvent.cancel) {
                    Handler().postDelayed({
                        bottomSheet!!.setOnCancelListener(null)
                        bottomSheet!!.cancel()
                    }, bottomSheetLockEvent.delay.toLong())
                }
            }
        }
    }

    override fun onItemClick(
            view: View,
            position: Int
    ): Boolean {
        val participant = (adapter!!.getItem(position) as UserItem).model
        participant.selected = !participant.selected

        if ("groups" == participant.source) {
            if (participant.selected) {
                selectedGroupIds.add(participant.userId)
            } else {
                selectedGroupIds.remove(participant.userId)
            }
        } else {
            if (participant.selected) {
                selectedUserIds.add(participant.userId)
            } else {
                selectedUserIds.remove(participant.userId)
            }
        }

        if (currentUser!!.hasSpreedFeatureCapability("last-room-activity")
                && !currentUser!!.hasSpreedFeatureCapability("invite-groups-and-mails") &&
                "groups" == (adapter!!.getItem(position) as UserItem).model.source &&
                participant.selected &&
                adapter!!.selectedItemCount > 1
        ) {
            val currentItems = adapter!!.currentItems
            var internalParticipant: Participant
            for (i in currentItems.indices) {
                if (currentItems[i] is UserItem) {
                    internalParticipant = (currentItems[i] as UserItem).model
                    if (internalParticipant.userId == participant.userId
                            &&
                            "groups" == internalParticipant.source
                            && internalParticipant.selected
                    ) {
                        internalParticipant.selected = false
                        selectedGroupIds.remove(internalParticipant.userId)
                    }
                }
            }

            adapter!!.notifyDataSetChanged()
            checkAndHandleDoneMenuItem()
        }
        return true
    }

    @Optional
    @OnClick(R.id.joinConversationViaLinkRelativeLayout)
    internal fun joinConversationViaLink() {
        val bundle = Bundle()
        bundle.putInt(BundleKeys.KEY_OPERATION_CODE, 10)

        prepareAndShowBottomSheetWithBundle(bundle, true)
    }

    @Optional
    @OnClick(R.id.call_header_layout)
    internal fun toggleCallHeader() {
        toggleNewCallHeaderVisibility(isPublicCall)
        isPublicCall = !isPublicCall

        if (isPublicCall) {
            joinConversationViaLinkLayout!!.visibility = View.GONE
        } else {
            joinConversationViaLinkLayout!!.visibility = View.VISIBLE
        }

        if (isPublicCall) {
            val currentItems = adapter!!.currentItems
            var internalParticipant: Participant
            for (i in currentItems.indices) {
                if (currentItems[i] is UserItem) {
                    internalParticipant = (currentItems[i] as UserItem).model
                    if ("groups" == internalParticipant.source && internalParticipant.selected) {
                        internalParticipant.selected = false
                        selectedGroupIds.remove(internalParticipant.userId)
                    }
                }
            }
        }

        for (i in 0 until adapter!!.itemCount) {
            if (adapter!!.getItem(i) is UserItem) {
                val userItem = adapter!!.getItem(i) as UserItem?
                if ("groups" == userItem!!.model.source) {
                    userItem.isEnabled = !isPublicCall
                }
            }
        }

        checkAndHandleDoneMenuItem()
        adapter!!.notifyDataSetChanged()
    }

    private fun toggleNewCallHeaderVisibility(showInitialLayout: Boolean) {
        if (showInitialLayout) {
            initialRelativeLayout!!.visibility = View.VISIBLE
            secondaryRelativeLayout!!.visibility = View.GONE
        } else {
            initialRelativeLayout!!.visibility = View.GONE
            secondaryRelativeLayout!!.visibility = View.VISIBLE
        }
    }

    companion object {

        val TAG = "ContactsController"
    }
}
