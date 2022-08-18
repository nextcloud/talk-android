/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Andy Scherzinger
 * @author Marcel Hibbe
 * Copyright (C) 2021 Andy Scherzinger (info@andy-scherzinger.de)
 * Copyright (C) 2017-2020 Mario Danic (mario@lovelyhq.com)
 * Copyright (C) 2022 Marcel Hibbe (dev@mhibbe.de)
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

import android.animation.AnimatorInflater
import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.text.InputType
import android.text.TextUtils
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.core.view.MenuItemCompat
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.RecyclerView
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import autodagger.AutoInjector
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler
import com.bluelinelabs.conductor.changehandler.VerticalChangeHandler
import com.facebook.common.executors.UiThreadImmediateExecutorService
import com.facebook.common.references.CloseableReference
import com.facebook.datasource.DataSource
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.imagepipeline.datasource.BaseBitmapDataSubscriber
import com.facebook.imagepipeline.image.CloseableImage
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.MainActivity
import com.nextcloud.talk.adapters.items.ConversationItem
import com.nextcloud.talk.adapters.items.GenericTextHeaderItem
import com.nextcloud.talk.adapters.items.LoadMoreResultsItem
import com.nextcloud.talk.adapters.items.MessageResultItem
import com.nextcloud.talk.adapters.items.MessagesTextHeaderItem
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.controllers.base.NewBaseController
import com.nextcloud.talk.controllers.util.viewBinding
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.ControllerConversationsRvBinding
import com.nextcloud.talk.events.ConversationsListFetchDataEvent
import com.nextcloud.talk.events.EventStatus
import com.nextcloud.talk.interfaces.ConversationMenuInterface
import com.nextcloud.talk.jobs.AccountRemovalWorker
import com.nextcloud.talk.jobs.ContactAddressBookWorker.Companion.run
import com.nextcloud.talk.jobs.DeleteConversationWorker
import com.nextcloud.talk.jobs.UploadAndShareFilesWorker
import com.nextcloud.talk.jobs.UploadAndShareFilesWorker.Companion.isStoragePermissionGranted
import com.nextcloud.talk.jobs.UploadAndShareFilesWorker.Companion.requestStoragePermission
import com.nextcloud.talk.messagesearch.MessageSearchHelper
import com.nextcloud.talk.messagesearch.MessageSearchHelper.MessageSearchResults
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.conversations.RoomsOverall
import com.nextcloud.talk.models.json.status.Status
import com.nextcloud.talk.models.json.statuses.StatusesOverall
import com.nextcloud.talk.repositories.unifiedsearch.UnifiedSearchRepository
import com.nextcloud.talk.ui.dialog.ChooseAccountDialogFragment
import com.nextcloud.talk.ui.dialog.ConversationsListBottomDialog
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.AttendeePermissionsUtil
import com.nextcloud.talk.utils.ClosedInterfaceImpl
import com.nextcloud.talk.utils.ConductorRemapping.remapChatController
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.Mimetype
import com.nextcloud.talk.utils.UriUtils.Companion.getFileName
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ACTIVE_CONVERSATION
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_FORWARD_HIDE_SOURCE_ROOM
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_FORWARD_MSG_FLAG
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_FORWARD_MSG_TEXT
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_INTERNAL_USER_ID
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_NEW_CONVERSATION
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_ID
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_SHARED_TEXT
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_USER_ENTITY
import com.nextcloud.talk.utils.database.user.CapabilitiesUtilNew.getAttachmentFolder
import com.nextcloud.talk.utils.database.user.CapabilitiesUtilNew.hasSpreedFeatureCapability
import com.nextcloud.talk.utils.database.user.CapabilitiesUtilNew.isServerEOL
import com.nextcloud.talk.utils.database.user.CapabilitiesUtilNew.isUnifiedSearchAvailable
import com.nextcloud.talk.utils.database.user.CapabilitiesUtilNew.isUserStatusAvailable
import com.nextcloud.talk.utils.rx.SearchViewObservable.Companion.observeSearchView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.common.SmoothScrollLinearLayoutManager
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.apache.commons.lang3.builder.CompareToBuilder
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.parceler.Parcels
import retrofit2.HttpException
import java.util.Collections
import java.util.Objects
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class ConversationsListController(bundle: Bundle) :
    NewBaseController(R.layout.controller_conversations_rv, bundle),
    FlexibleAdapter.OnItemClickListener,
    FlexibleAdapter.OnItemLongClickListener,
    ConversationMenuInterface {
    private val bundle: Bundle

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var eventBus: EventBus

    @Inject
    lateinit var ncApi: NcApi

    @Inject
    lateinit var unifiedSearchRepository: UnifiedSearchRepository

    private val binding: ControllerConversationsRvBinding by viewBinding(ControllerConversationsRvBinding::bind)

    override val title: String
        get() = resources!!.getString(R.string.nc_app_product_name)

    override val appBarLayoutType: AppBarLayoutType
        get() = AppBarLayoutType.SEARCH_BAR

    private var currentUser: User? = null
    private var roomsQueryDisposable: Disposable? = null
    private var openConversationsQueryDisposable: Disposable? = null
    private var adapter: FlexibleAdapter<AbstractFlexibleItem<*>>? = null
    private var conversationItems: MutableList<AbstractFlexibleItem<*>> = ArrayList()
    private var conversationItemsWithHeader: MutableList<AbstractFlexibleItem<*>> = ArrayList()
    private val searchableConversationItems: MutableList<AbstractFlexibleItem<*>> = ArrayList()
    private var searchItem: MenuItem? = null
    private var searchView: SearchView? = null
    private var searchQuery: String? = null
    private var credentials: String? = null
    private var adapterWasNull = true
    private var isRefreshing = false
    private var conversationMenuBundle: Bundle? = null
    private var showShareToScreen = false
    private var filesToShare: ArrayList<String>? = null
    private var selectedConversation: Conversation? = null
    private var textToPaste: String? = ""
    private var selectedMessageId: String? = null
    private var forwardMessage: Boolean
    private var nextUnreadConversationScrollPosition = 0
    private var layoutManager: SmoothScrollLinearLayoutManager? = null
    private val callHeaderItems = HashMap<String, GenericTextHeaderItem>()
    private var conversationsListBottomDialog: ConversationsListBottomDialog? = null
    private val userStatuses = HashMap<String?, Status>()
    private var searchHelper: MessageSearchHelper? = null
    private var searchViewDisposable: Disposable? = null

    override fun onViewBound(view: View) {
        super.onViewBound(view)
        sharedApplication!!.componentApplication.inject(this)
        actionBar?.show()
        if (adapter == null) {
            adapter = FlexibleAdapter(conversationItems, activity, true)
        } else {
            binding.loadingContent.visibility = View.GONE
        }
        adapter!!.addListener(this)
        prepareViews()
    }

    private fun loadUserAvatar(button: MaterialButton) {
        if (activity != null) {
            val imageRequest = DisplayUtils.getImageRequestForUrl(
                ApiUtils.getUrlForAvatar(
                    currentUser!!.baseUrl,
                    currentUser!!.userId,
                    true
                ),
                currentUser
            )
            val imagePipeline = Fresco.getImagePipeline()
            val dataSource = imagePipeline.fetchDecodedImage(imageRequest, null)
            dataSource.subscribe(
                object : BaseBitmapDataSubscriber() {
                    override fun onNewResultImpl(bitmap: Bitmap?) {
                        if (bitmap != null && resources != null) {
                            val roundedBitmapDrawable = RoundedBitmapDrawableFactory.create(
                                resources!!,
                                bitmap
                            )
                            roundedBitmapDrawable.isCircular = true
                            roundedBitmapDrawable.setAntiAlias(true)
                            button.icon = roundedBitmapDrawable
                        }
                    }

                    override fun onFailureImpl(dataSource: DataSource<CloseableReference<CloseableImage?>>) {
                        if (resources != null) {
                            button.icon = ResourcesCompat.getDrawable(resources!!, R.drawable.ic_user, null)
                        }
                    }
                },
                UiThreadImmediateExecutorService.getInstance()
            )
        }
    }

    override fun onAttach(view: View) {
        Log.d(
            TAG,
            "onAttach: Controller: " + System.identityHashCode(this) +
                " Activity: " + System.identityHashCode(activity)
        )
        super.onAttach(view)
        ClosedInterfaceImpl().setUpPushTokenRegistration()
        if (!eventBus.isRegistered(this)) {
            eventBus.register(this)
        }
        currentUser = userManager.currentUser.blockingGet()
        if (currentUser != null) {
            if (isServerEOL(currentUser!!)) {
                showServerEOLDialog()
                return
            }
            if (isUnifiedSearchAvailable(currentUser!!)) {
                searchHelper = MessageSearchHelper(unifiedSearchRepository)
            }
            credentials = ApiUtils.getCredentials(currentUser!!.username, currentUser!!.token)
            if (activity != null && activity is MainActivity) {
                loadUserAvatar((activity as MainActivity?)!!.binding.switchAccountButton)
                viewThemeUtils.colorMaterialTextButton((activity as MainActivity?)!!.binding.switchAccountButton)
            }
            fetchData()
        }
    }

    override fun onDetach(view: View) {
        Log.d(
            TAG,
            "onDetach: Controller: " + System.identityHashCode(this) +
                " Activity: " + System.identityHashCode(activity)
        )
        super.onDetach(view)
        eventBus.unregister(this)
    }

    private fun initSearchView() {
        if (activity != null) {
            val searchManager = activity!!.getSystemService(Context.SEARCH_SERVICE) as SearchManager
            if (searchItem != null) {
                searchView = MenuItemCompat.getActionView(searchItem) as SearchView
                viewThemeUtils.themeSearchView(searchView!!)
                searchView!!.maxWidth = Int.MAX_VALUE
                searchView!!.inputType = InputType.TYPE_TEXT_VARIATION_FILTER
                var imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_FULLSCREEN
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appPreferences.isKeyboardIncognito) {
                    imeOptions = imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                }
                searchView!!.imeOptions = imeOptions
                searchView!!.queryHint = searchHint
                if (searchManager != null) {
                    searchView!!.setSearchableInfo(searchManager.getSearchableInfo(activity!!.componentName))
                }
                searchViewDisposable = observeSearchView(searchView!!)
                    .debounce { query: String? ->
                        if (TextUtils.isEmpty(query)) {
                            return@debounce Observable.empty<Long>()
                        } else {
                            return@debounce Observable.timer(
                                SEARCH_DEBOUNCE_INTERVAL_MS.toLong(),
                                TimeUnit.MILLISECONDS
                            )
                        }
                    }
                    .distinctUntilChanged()
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe { newText: String? -> onQueryTextChange(newText) }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_conversation_plus_filter, menu)
        searchItem = menu.findItem(R.id.action_search)
        initSearchView()
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        searchView = MenuItemCompat.getActionView(searchItem) as SearchView
        showShareToScreen = !showShareToScreen && hasActivityActionSendIntent()
        if (showShareToScreen) {
            hideSearchBar()
            actionBar?.setTitle(R.string.send_to_three_dots)
        } else if (forwardMessage) {
            hideSearchBar()
            actionBar?.setTitle(R.string.nc_forward_to_three_dots)
        } else {
            val activity = activity as MainActivity?
            searchItem!!.isVisible = conversationItems.size > 0
            if (activity != null) {
                if (adapter!!.hasFilter()) {
                    showSearchView(activity, searchView, searchItem)
                    searchView!!.setQuery(adapter!!.getFilter(String::class.java), false)
                }
                activity.binding.searchText.setOnClickListener { v: View? ->
                    showSearchView(activity, searchView, searchItem)
                    viewThemeUtils.themeStatusBar(activity, searchView!!)
                }
            }
            searchView!!.setOnCloseListener {
                if (TextUtils.isEmpty(searchView!!.query.toString())) {
                    searchView!!.onActionViewCollapsed()
                    if (activity != null) {
                        viewThemeUtils.resetStatusBar(activity, searchView!!)
                    }
                } else {
                    searchView!!.post { searchView!!.setQuery(TAG, true) }
                }
                true
            }
            searchItem!!.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
                override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                    adapter!!.setHeadersShown(true)
                    adapter!!.updateDataSet(searchableConversationItems, false)
                    adapter!!.showAllHeaders()
                    if (binding.swipeRefreshLayoutView != null) {
                        binding.swipeRefreshLayoutView!!.isEnabled = false
                    }
                    return true
                }

                override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                    adapter!!.setHeadersShown(false)
                    adapter!!.updateDataSet(conversationItems, false)
                    adapter!!.hideAllHeaders()
                    if (searchHelper != null) {
                        // cancel any pending searches
                        searchHelper!!.cancelSearch()
                        binding.swipeRefreshLayoutView!!.isRefreshing = false
                    }
                    if (binding.swipeRefreshLayoutView != null) {
                        binding.swipeRefreshLayoutView!!.isEnabled = true
                    }
                    searchView!!.onActionViewCollapsed()
                    val activity = getActivity() as MainActivity?
                    if (activity != null) {
                        activity.binding.appBar.stateListAnimator = AnimatorInflater.loadStateListAnimator(
                            activity.binding.appBar.context,
                            R.animator.appbar_elevation_off
                        )
                        activity.binding.toolbar.visibility = View.GONE
                        activity.binding.searchToolbar.visibility = View.VISIBLE
                        if (resources != null) {
                            viewThemeUtils.resetStatusBar(activity, activity.binding.searchToolbar)
                        }
                    }
                    val layoutManager = binding.recyclerView!!.layoutManager as SmoothScrollLinearLayoutManager?
                    layoutManager?.scrollToPositionWithOffset(0, 0)
                    return true
                }
            })
        }
    }

    private fun hasActivityActionSendIntent(): Boolean {
        return if (activity != null) {
            Intent.ACTION_SEND == activity!!.intent.action || Intent.ACTION_SEND_MULTIPLE == activity!!.intent.action
        } else false
    }

    override fun showSearchOrToolbar() {
        if (TextUtils.isEmpty(searchQuery)) {
            super.showSearchOrToolbar()
        }
    }

    private fun showSearchView(activity: MainActivity, searchView: SearchView?, searchItem: MenuItem?) {
        activity.binding.appBar.stateListAnimator = AnimatorInflater.loadStateListAnimator(
            activity.binding.appBar.context,
            R.animator.appbar_elevation_on
        )
        activity.binding.toolbar.visibility = View.VISIBLE
        activity.binding.searchToolbar.visibility = View.GONE
        searchItem!!.expandActionView()
    }

    @SuppressLint("LongLogTag")
    fun fetchData() {
        if (isUserStatusAvailable(userManager.currentUser.blockingGet())) {
            fetchUserStatusesAndRooms()
        } else {
            fetchRooms()
        }
    }

    private fun fetchUserStatusesAndRooms() {
        ncApi.getUserStatuses(credentials, ApiUtils.getUrlForUserStatuses(currentUser!!.baseUrl))
            .subscribe(object : Observer<StatusesOverall> {
                override fun onSubscribe(d: Disposable) {}
                override fun onNext(statusesOverall: StatusesOverall) {
                    for (status in statusesOverall.ocs!!.data!!) {
                        userStatuses[status.userId] = status
                    }
                    fetchRooms()
                }

                override fun onError(e: Throwable) {
                    Log.e(TAG, "failed to fetch user statuses", e)
                    fetchRooms()
                }

                override fun onComplete() {}
            })
    }

    private fun fetchRooms() {
        dispose(null)
        isRefreshing = true
        conversationItems = ArrayList()
        conversationItemsWithHeader = ArrayList()
        val apiVersion = ApiUtils.getConversationApiVersion(currentUser, intArrayOf(ApiUtils.APIv4, ApiUtils.APIv3, 1))
        val startNanoTime = System.nanoTime()
        Log.d(TAG, "fetchData - getRooms - calling: $startNanoTime")
        roomsQueryDisposable = ncApi.getRooms(
            credentials,
            ApiUtils.getUrlForRooms(
                apiVersion,
                currentUser!!.baseUrl
            )
        )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ (ocs): RoomsOverall ->
                Log.d(TAG, "fetchData - getRooms - got response: $startNanoTime")

                // This is invoked asynchronously, when server returns a response the view might have been
                // unbound in the meantime. Check if the view is still there.
                // FIXME - does it make sense to update internal data structures even when view has been unbound?
                if (view == null) {
                    Log.d(TAG, "fetchData - getRooms - view is not bound: $startNanoTime")
                    return@subscribe
                }
                if (adapterWasNull) {
                    adapterWasNull = false
                    binding.loadingContent!!.visibility = View.GONE
                }
                if (ocs!!.data!!.size > 0) {
                    if (binding.emptyLayout!!.visibility != View.GONE) {
                        binding.emptyLayout!!.visibility = View.GONE
                    }
                    if (binding.swipeRefreshLayoutView!!.visibility != View.VISIBLE) {
                        binding.swipeRefreshLayoutView!!.visibility = View.VISIBLE
                    }
                } else {
                    if (binding.emptyLayout!!.visibility != View.VISIBLE) {
                        binding.emptyLayout!!.visibility = View.VISIBLE
                    }
                    if (binding.swipeRefreshLayoutView!!.visibility != View.GONE) {
                        binding.swipeRefreshLayoutView!!.visibility = View.GONE
                    }
                }
                for (conversation in ocs.data!!) {
                    if (bundle.containsKey(KEY_FORWARD_HIDE_SOURCE_ROOM) && conversation.roomId == bundle.getString(
                            KEY_FORWARD_HIDE_SOURCE_ROOM
                        )
                    ) {
                        continue
                    }
                    var headerTitle: String
                    headerTitle = resources!!.getString(R.string.conversations)
                    var genericTextHeaderItem: GenericTextHeaderItem
                    if (!callHeaderItems.containsKey(headerTitle)) {
                        genericTextHeaderItem = GenericTextHeaderItem(headerTitle, viewThemeUtils)
                        callHeaderItems[headerTitle] = genericTextHeaderItem
                    }
                    if (activity != null) {
                        val conversationItem = ConversationItem(
                            conversation,
                            currentUser,
                            activity,
                            userStatuses[conversation.name],
                            viewThemeUtils
                        )
                        conversationItems.add(conversationItem)
                        val conversationItemWithHeader = ConversationItem(
                            conversation,
                            currentUser,
                            activity,
                            callHeaderItems[headerTitle],
                            userStatuses[conversation.name],
                            viewThemeUtils
                        )
                        conversationItemsWithHeader.add(conversationItemWithHeader)
                    }
                }
                sortConversations(conversationItems)
                sortConversations(conversationItemsWithHeader)
                adapter!!.updateDataSet(conversationItems, false)
                Handler().postDelayed({ checkToShowUnreadBubble() }, UNREAD_BUBBLE_DELAY.toLong())
                fetchOpenConversations(apiVersion)
                if (binding.swipeRefreshLayoutView != null) {
                    binding.swipeRefreshLayoutView!!.isRefreshing = false
                }
            }, { throwable: Throwable ->
                handleHttpExceptions(throwable)
                if (binding.swipeRefreshLayoutView != null) {
                    binding.swipeRefreshLayoutView!!.isRefreshing = false
                }
                dispose(roomsQueryDisposable)
            }) {
                dispose(roomsQueryDisposable)
                if (binding.swipeRefreshLayoutView != null) {
                    binding.swipeRefreshLayoutView!!.isRefreshing = false
                }
                isRefreshing = false
            }
    }

    private fun sortConversations(conversationItems: List<AbstractFlexibleItem<*>>) {
        Collections.sort(conversationItems) { o1: AbstractFlexibleItem<*>, o2: AbstractFlexibleItem<*> ->
            val (_, _, _, _, _, _, _, _, _, _, _, _, _, favorite, lastActivity) = (o1 as ConversationItem).model
            val (_, _, _, _, _, _, _, _, _, _, _, _, _, favorite1, lastActivity1) = (o2 as ConversationItem).model
            CompareToBuilder()
                .append(favorite1, favorite)
                .append(lastActivity1, lastActivity)
                .toComparison()
        }
    }

    private fun fetchOpenConversations(apiVersion: Int) {
        searchableConversationItems.clear()
        searchableConversationItems.addAll(conversationItemsWithHeader)
        if (hasSpreedFeatureCapability(currentUser, "listable-rooms")) {
            val openConversationItems: MutableList<AbstractFlexibleItem<*>> = ArrayList()
            openConversationsQueryDisposable = ncApi.getOpenConversations(
                credentials,
                ApiUtils.getUrlForOpenConversations(apiVersion, currentUser!!.baseUrl)
            )
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ (ocs): RoomsOverall ->
                    for (conversation in ocs!!.data!!) {
                        val headerTitle = resources!!.getString(R.string.openConversations)
                        var genericTextHeaderItem: GenericTextHeaderItem
                        if (!callHeaderItems.containsKey(headerTitle)) {
                            genericTextHeaderItem = GenericTextHeaderItem(headerTitle, viewThemeUtils)
                            callHeaderItems[headerTitle] = genericTextHeaderItem
                        }
                        val conversationItem = ConversationItem(
                            conversation,
                            currentUser,
                            activity,
                            callHeaderItems[headerTitle],
                            userStatuses[conversation.name],
                            viewThemeUtils
                        )
                        openConversationItems.add(conversationItem)
                    }
                    searchableConversationItems.addAll(openConversationItems)
                }, { throwable: Throwable ->
                    Log.e(TAG, "fetchData - getRooms - ERROR", throwable)
                    handleHttpExceptions(throwable)
                    dispose(openConversationsQueryDisposable)
                }) { dispose(openConversationsQueryDisposable) }
        } else {
            Log.d(TAG, "no open conversations fetched because of missing capability")
        }
    }

    private fun handleHttpExceptions(throwable: Throwable) {
        if (throwable is HttpException) {
            when (throwable.code()) {
                401 -> if (parentController != null && parentController!!.router != null) {
                    Log.d(TAG, "Starting reauth webview via getParentController()")
                    parentController!!.router.pushController(
                        RouterTransaction.with(
                            WebViewLoginController(
                                currentUser!!.baseUrl,
                                true
                            )
                        )
                            .pushChangeHandler(VerticalChangeHandler())
                            .popChangeHandler(VerticalChangeHandler())
                    )
                } else {
                    Log.d(TAG, "Starting reauth webview via ConversationsListController")
                    showUnauthorizedDialog()
                }
                else -> {}
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun prepareViews() {
        layoutManager = SmoothScrollLinearLayoutManager(Objects.requireNonNull(activity))
        binding.recyclerView!!.layoutManager = layoutManager
        binding.recyclerView!!.setHasFixedSize(true)
        binding.recyclerView!!.adapter = adapter
        binding.recyclerView!!.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    checkToShowUnreadBubble()
                }
            }
        })
        binding.recyclerView!!.setOnTouchListener { v: View, event: MotionEvent? ->
            if (isAttached && (!isBeingDestroyed || !isDestroyed)) {
                val imm = activity!!.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(v.windowToken, 0)
            }
            false
        }
        binding.swipeRefreshLayoutView!!.setOnRefreshListener { fetchData() }
        viewThemeUtils.themeSwipeRefreshLayout(binding.swipeRefreshLayoutView!!)
        binding.emptyLayout!!.setOnClickListener { v: View? -> showNewConversationsScreen() }
        binding.floatingActionButton!!.setOnClickListener { v: View? ->
            run(context)
            showNewConversationsScreen()
        }
        viewThemeUtils.themeFAB(binding.floatingActionButton!!)
        if (activity != null && activity is MainActivity) {
            val activity = activity as MainActivity?
            activity!!.binding.switchAccountButton.setOnClickListener { v: View? ->
                if (resources != null && resources!!.getBoolean(R.bool.multiaccount_support)) {
                    val newFragment: DialogFragment = ChooseAccountDialogFragment.newInstance()
                    newFragment.show(
                        (getActivity() as MainActivity?)!!.supportFragmentManager,
                        "ChooseAccountDialogFragment"
                    )
                } else {
                    router.pushController(
                        RouterTransaction.with(SettingsController())
                            .pushChangeHandler(HorizontalChangeHandler())
                            .popChangeHandler(HorizontalChangeHandler())
                    )
                }
            }
        }
        binding.newMentionPopupBubble!!.hide()
        binding.newMentionPopupBubble!!.setPopupBubbleListener {
            binding.recyclerView!!.smoothScrollToPosition(
                nextUnreadConversationScrollPosition
            )
        }
        viewThemeUtils.colorMaterialButtonPrimaryFilled(binding.newMentionPopupBubble!!)
    }

    private fun checkToShowUnreadBubble() {
        try {
            val lastVisibleItem = layoutManager!!.findLastCompletelyVisibleItemPosition()
            for (flexItem in conversationItems) {
                val conversation: Conversation = (flexItem as ConversationItem).model
                val position = adapter!!.getGlobalPositionOf(flexItem)
                if ((
                    conversation.unreadMention ||
                        conversation.unreadMessages > 0 &&
                        conversation.type === Conversation.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL
                    ) && position > lastVisibleItem
                ) {
                    nextUnreadConversationScrollPosition = position
                    if (!binding.newMentionPopupBubble!!.isShown) {
                        binding.newMentionPopupBubble!!.show()
                    }
                    return
                }
            }
            nextUnreadConversationScrollPosition = 0
            binding.newMentionPopupBubble!!.hide()
        } catch (e: NullPointerException) {
            Log.d(
                TAG,
                "A NPE was caught when trying to show the unread popup bubble. This might happen when the " +
                    "user already left the conversations-list screen so the popup bubble is not available anymore.",
                e
            )
        }
    }

    private fun showNewConversationsScreen() {
        val bundle = Bundle()
        bundle.putBoolean(KEY_NEW_CONVERSATION, true)
        router.pushController(
            RouterTransaction.with(ContactsController(bundle))
                .pushChangeHandler(HorizontalChangeHandler())
                .popChangeHandler(HorizontalChangeHandler())
        )
    }

    private fun dispose(disposable: Disposable?) {
        var disposable = disposable
        if (disposable != null && !disposable.isDisposed) {
            disposable.dispose()
            disposable = null
        } else if (disposable == null && roomsQueryDisposable != null && !roomsQueryDisposable!!.isDisposed) {
            roomsQueryDisposable!!.dispose()
            roomsQueryDisposable = null
        } else if (disposable == null &&
            openConversationsQueryDisposable != null &&
            !openConversationsQueryDisposable!!.isDisposed
        ) {
            openConversationsQueryDisposable!!.dispose()
            openConversationsQueryDisposable = null
        }
    }

    public override fun onSaveViewState(view: View, outState: Bundle) {
        if (searchView != null && !TextUtils.isEmpty(searchView!!.query)) {
            outState.putString(KEY_SEARCH_QUERY, searchView!!.query.toString())
        }
        super.onSaveViewState(view, outState)
    }

    public override fun onRestoreViewState(view: View, savedViewState: Bundle) {
        super.onRestoreViewState(view, savedViewState)
        if (savedViewState.containsKey(KEY_SEARCH_QUERY)) {
            searchQuery = savedViewState.getString(KEY_SEARCH_QUERY, "")
        }
    }

    public override fun onDestroy() {
        super.onDestroy()
        dispose(null)
        if (searchViewDisposable != null && !searchViewDisposable!!.isDisposed) {
            searchViewDisposable!!.dispose()
        }
    }

    fun onQueryTextChange(newText: String?) {
        if (!TextUtils.isEmpty(searchQuery)) {
            val filter = searchQuery
            searchQuery = ""
            performFilterAndSearch(filter)
        } else if (adapter!!.hasNewFilter(newText)) {
            performFilterAndSearch(newText)
        }
    }

    private fun performFilterAndSearch(filter: String?) {
        if (filter!!.length >= SEARCH_MIN_CHARS) {
            clearMessageSearchResults()
            adapter!!.setFilter(filter)
            adapter!!.filterItems()
            if (isUnifiedSearchAvailable(currentUser!!)) {
                startMessageSearch(filter)
            }
        } else {
            resetSearchResults()
        }
    }

    private fun resetSearchResults() {
        clearMessageSearchResults()
        adapter!!.setFilter("")
        adapter!!.filterItems()
    }

    private fun clearMessageSearchResults() {
        val firstHeader = adapter!!.getSectionHeader(0)
        if (firstHeader != null && firstHeader.itemViewType == MessagesTextHeaderItem.VIEW_TYPE) {
            adapter!!.removeSection(firstHeader)
        } else {
            adapter!!.removeItemsOfType(MessageResultItem.VIEW_TYPE)
        }
        adapter!!.removeItemsOfType(LoadMoreResultsItem.VIEW_TYPE)
    }

    @SuppressLint("CheckResult") // handled by helper
    private fun startMessageSearch(search: String?) {
        if (binding.swipeRefreshLayoutView != null) {
            binding.swipeRefreshLayoutView!!.isRefreshing = true
        }
        searchHelper?.startMessageSearch(search!!)
            ?.subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe({ results: MessageSearchResults -> onMessageSearchResult(results) }) { throwable: Throwable ->
                onMessageSearchError(
                    throwable
                )
            }
    }

    @SuppressLint("CheckResult") // handled by helper
    private fun loadMoreMessages() {
        binding.swipeRefreshLayoutView!!.isRefreshing = true
        val observable = searchHelper!!.loadMore()
        observable?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe({ results: MessageSearchResults -> onMessageSearchResult(results) }) { throwable: Throwable ->
                onMessageSearchError(
                    throwable
                )
            }
    }

    override fun onItemClick(view: View, position: Int): Boolean {
        val item = adapter!!.getItem(position)
        if (item != null) {
            when (item.itemViewType) {
                MessageResultItem.VIEW_TYPE -> {
                    val messageItem: MessageResultItem = item as MessageResultItem
                    val conversationToken = messageItem.messageEntry.conversationToken
                    selectedMessageId = messageItem.messageEntry.messageId
                    showConversationByToken(conversationToken)
                }
                LoadMoreResultsItem.VIEW_TYPE -> {
                    loadMoreMessages()
                }
                ConversationItem.VIEW_TYPE -> {
                    showConversation((Objects.requireNonNull(item) as ConversationItem).model)
                }
            }
        }
        return true
    }

    private fun showConversationByToken(conversationToken: String) {
        for (absItem in conversationItems) {
            val conversationItem = absItem as ConversationItem
            if (conversationItem.model.token == conversationToken) {
                val conversation = conversationItem.model
                showConversation(conversation)
            }
        }
    }

    private fun showConversation(conversation: Conversation?) {
        selectedConversation = conversation
        if (selectedConversation != null && activity != null) {
            val hasChatPermission = AttendeePermissionsUtil(selectedConversation!!.permissions).hasChatPermission(
                currentUser!!
            )
            if (showShareToScreen) {
                if (hasChatPermission && !isReadOnlyConversation(selectedConversation!!)) {
                    handleSharedData()
                    showShareToScreen = false
                } else {
                    Toast.makeText(context, R.string.send_to_forbidden, Toast.LENGTH_LONG).show()
                }
            } else if (forwardMessage) {
                if (hasChatPermission && !isReadOnlyConversation(selectedConversation!!)) {
                    openConversation(bundle.getString(KEY_FORWARD_MSG_TEXT))
                    forwardMessage = false
                } else {
                    Toast.makeText(context, R.string.send_to_forbidden, Toast.LENGTH_LONG).show()
                }
            } else {
                openConversation()
            }
        }
    }

    private fun isReadOnlyConversation(conversation: Conversation): Boolean {
        return conversation.conversationReadOnlyState ===
            Conversation.ConversationReadOnlyState.CONVERSATION_READ_ONLY
    }

    private fun handleSharedData() {
        collectDataFromIntent()
        if (!textToPaste!!.isEmpty()) {
            openConversation(textToPaste)
        } else if (filesToShare != null && !filesToShare!!.isEmpty()) {
            showSendFilesConfirmDialog()
        } else {
            Toast.makeText(context, context.resources.getString(R.string.nc_common_error_sorry), Toast.LENGTH_LONG)
                .show()
        }
    }

    private fun showSendFilesConfirmDialog() {
        if (isStoragePermissionGranted(context)) {
            val fileNamesWithLineBreaks = StringBuilder("\n")
            for (file in filesToShare!!) {
                val filename = getFileName(Uri.parse(file), context)
                fileNamesWithLineBreaks.append(filename).append("\n")
            }
            val confirmationQuestion: String
            confirmationQuestion = if (filesToShare!!.size == 1) {
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
            val dialogBuilder = MaterialAlertDialogBuilder(binding.floatingActionButton!!.context)
                .setIcon(viewThemeUtils.colorMaterialAlertDialogIcon(context, R.drawable.upload))
                .setTitle(confirmationQuestion)
                .setMessage(fileNamesWithLineBreaks.toString())
                .setPositiveButton(R.string.nc_yes) { dialog: DialogInterface?, which: Int ->
                    upload()
                    openConversation()
                }
                .setNegativeButton(R.string.nc_no) { dialog: DialogInterface?, which: Int ->
                    Log.d(TAG, "sharing files aborted, going back to share-to screen")
                    showShareToScreen = true
                }
            viewThemeUtils.colorMaterialAlertDialogBackground(binding.floatingActionButton!!.context, dialogBuilder)
            val dialog = dialogBuilder.show()
            viewThemeUtils.colorTextButtons(
                dialog.getButton(AlertDialog.BUTTON_POSITIVE),
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            )
        } else {
            requestStoragePermission(this@ConversationsListController)
        }
    }

    override fun onItemLongClick(position: Int) {
        if (showShareToScreen) {
            Log.d(TAG, "sharing to multiple rooms not yet implemented. onItemLongClick is ignored.")
        } else {
            val clickedItem: Any? = adapter!!.getItem(position)
            if (clickedItem != null) {
                val conversation = (clickedItem as ConversationItem).model
                conversationsListBottomDialog = ConversationsListBottomDialog(
                    activity!!,
                    this,
                    userManager.currentUser.blockingGet(),
                    conversation
                )
                conversationsListBottomDialog!!.show()
            }
        }
    }

    private fun collectDataFromIntent() {
        filesToShare = ArrayList()
        if (activity != null && activity!!.intent != null) {
            val intent = activity!!.intent
            if (Intent.ACTION_SEND == intent.action || Intent.ACTION_SEND_MULTIPLE == intent.action) {
                try {
                    val mimeType = intent.type
                    if (Mimetype.TEXT_PLAIN == mimeType && intent.getStringExtra(Intent.EXTRA_TEXT) != null) {
                        // Share from Google Chrome sets text/plain MIME type, but also provides a content:// URI
                        // with a *screenshot* of the current page in getClipData().
                        // Here we assume that when sharing a web page the user would prefer to send the URL
                        // of the current page rather than a screenshot.
                        textToPaste = intent.getStringExtra(Intent.EXTRA_TEXT)
                    } else {
                        if (intent.clipData != null) {
                            for (i in 0 until intent.clipData!!.itemCount) {
                                val item = intent.clipData!!.getItemAt(i)
                                if (item.uri != null) {
                                    filesToShare!!.add(item.uri.toString())
                                } else if (item.text != null) {
                                    textToPaste = item.text.toString()
                                    break
                                } else {
                                    Log.w(TAG, "datatype not yet implemented for share-to")
                                }
                            }
                        } else {
                            filesToShare!!.add(intent.data.toString())
                        }
                    }
                    if (filesToShare!!.isEmpty() && textToPaste!!.isEmpty()) {
                        Toast.makeText(
                            context,
                            context.resources.getString(R.string.nc_common_error_sorry),
                            Toast.LENGTH_LONG
                        ).show()
                        Log.e(TAG, "failed to get data from intent")
                    }
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        context.resources.getString(R.string.nc_common_error_sorry),
                        Toast.LENGTH_LONG
                    ).show()
                    Log.e(TAG, "Something went wrong when extracting data from intent")
                }
            }
        }
    }

    private fun upload() {
        if (selectedConversation == null) {
            Toast.makeText(
                context,
                context.resources.getString(R.string.nc_common_error_sorry),
                Toast.LENGTH_LONG
            ).show()
            Log.e(TAG, "not able to upload any files because conversation was null.")
            return
        }
        try {
            var filesToShareArray: Array<String?>? = arrayOfNulls(filesToShare!!.size)
            filesToShareArray = filesToShare!!.toArray(filesToShareArray)
            val data = Data.Builder()
                .putStringArray(UploadAndShareFilesWorker.DEVICE_SOURCEFILES, filesToShareArray)
                .putString(
                    UploadAndShareFilesWorker.NC_TARGETPATH,
                    getAttachmentFolder(currentUser!!)
                )
                .putString(UploadAndShareFilesWorker.ROOM_TOKEN, selectedConversation!!.token)
                .build()
            val uploadWorker = OneTimeWorkRequest.Builder(UploadAndShareFilesWorker::class.java)
                .setInputData(data)
                .build()
            WorkManager.getInstance().enqueue(uploadWorker)
            Toast.makeText(
                context,
                context.resources.getString(R.string.nc_upload_in_progess),
                Toast.LENGTH_LONG
            ).show()
        } catch (e: IllegalArgumentException) {
            Toast.makeText(context, context.resources.getString(R.string.nc_upload_failed), Toast.LENGTH_LONG).show()
            Log.e(TAG, "Something went wrong when trying to upload file", e)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == UploadAndShareFilesWorker.REQUEST_PERMISSION &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "upload starting after permissions were granted")
            showSendFilesConfirmDialog()
        } else {
            Toast.makeText(context, context.getString(R.string.read_storage_no_permission), Toast.LENGTH_LONG).show()
        }
    }

    private fun openConversation(textToPaste: String? = "") {
        val bundle = Bundle()
        bundle.putParcelable(KEY_USER_ENTITY, currentUser)
        bundle.putParcelable(KEY_ACTIVE_CONVERSATION, Parcels.wrap(selectedConversation))
        bundle.putString(KEY_ROOM_TOKEN, selectedConversation!!.token)
        bundle.putString(KEY_ROOM_ID, selectedConversation!!.roomId)
        bundle.putString(KEY_SHARED_TEXT, textToPaste)
        if (selectedMessageId != null) {
            bundle.putString(BundleKeys.KEY_MESSAGE_ID, selectedMessageId)
            selectedMessageId = null
        }
        remapChatController(
            router,
            currentUser!!.id!!,
            selectedConversation!!.token!!,
            bundle,
            false
        )
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.BACKGROUND)
    fun onMessageEvent(eventStatus: EventStatus) {
        if (currentUser != null && eventStatus.userId == currentUser!!.id) {
            when (eventStatus.eventType) {
                EventStatus.EventType.CONVERSATION_UPDATE -> if (eventStatus.isAllGood && !isRefreshing) {
                    fetchData()
                }
                else -> {}
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(conversationsListFetchDataEvent: ConversationsListFetchDataEvent?) {
        fetchData()
        Handler().postDelayed({
            if (conversationsListBottomDialog!!.isShowing) {
                conversationsListBottomDialog!!.dismiss()
            }
        }, 2500)
    }

    override fun showDeleteConversationDialog(bundle: Bundle) {
        conversationMenuBundle = bundle
        if (activity != null &&
            conversationMenuBundle != null &&
            currentUser != null &&
            conversationMenuBundle!!.getLong(KEY_INTERNAL_USER_ID) == currentUser!!.id
        ) {
            val conversation = Parcels.unwrap<Conversation>(conversationMenuBundle!!.getParcelable(KEY_ROOM))
            if (conversation != null) {
                val dialogBuilder = MaterialAlertDialogBuilder(binding.floatingActionButton!!.context)
                    .setIcon(viewThemeUtils.colorMaterialAlertDialogIcon(context, R.drawable.ic_delete_black_24dp))
                    .setTitle(R.string.nc_delete_call)
                    .setMessage(R.string.nc_delete_conversation_more)
                    .setPositiveButton(R.string.nc_delete) { dialog: DialogInterface?, which: Int ->
                        val data = Data.Builder()
                        data.putLong(
                            KEY_INTERNAL_USER_ID,
                            conversationMenuBundle!!.getLong(KEY_INTERNAL_USER_ID)
                        )
                        data.putString(KEY_ROOM_TOKEN, conversation.token)
                        conversationMenuBundle = null
                        deleteConversation(data.build())
                    }
                    .setNegativeButton(R.string.nc_cancel) { dialog: DialogInterface?, which: Int ->
                        conversationMenuBundle = null
                    }
                viewThemeUtils.colorMaterialAlertDialogBackground(binding.floatingActionButton!!.context, dialogBuilder)
                val dialog = dialogBuilder.show()
                viewThemeUtils.colorTextButtons(
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE),
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                )
            }
        }
    }

    private fun showUnauthorizedDialog() {
        if (activity != null) {
            val dialogBuilder = MaterialAlertDialogBuilder(binding.floatingActionButton!!.context)
                .setIcon(viewThemeUtils.colorMaterialAlertDialogIcon(context, R.drawable.ic_delete_black_24dp))
                .setTitle(R.string.nc_dialog_invalid_password)
                .setMessage(R.string.nc_dialog_reauth_or_delete)
                .setCancelable(false)
                .setPositiveButton(R.string.nc_delete) { dialog: DialogInterface?, which: Int ->
                    val otherUserExists = userManager
                        .scheduleUserForDeletionWithId(currentUser!!.id!!)
                        .blockingGet()
                    val accountRemovalWork = OneTimeWorkRequest.Builder(AccountRemovalWorker::class.java).build()
                    WorkManager.getInstance().enqueue(accountRemovalWork)
                    if (otherUserExists && view != null) {
                        onViewBound(view!!)
                        onAttach(view!!)
                    } else if (!otherUserExists) {
                        router.setRoot(
                            RouterTransaction.with(
                                ServerSelectionController()
                            )
                                .pushChangeHandler(VerticalChangeHandler())
                                .popChangeHandler(VerticalChangeHandler())
                        )
                    }
                }
                .setNegativeButton(R.string.nc_settings_reauthorize) { dialog: DialogInterface?, which: Int ->
                    router.pushController(
                        RouterTransaction.with(
                            WebViewLoginController(currentUser!!.baseUrl, true)
                        )
                            .pushChangeHandler(VerticalChangeHandler())
                            .popChangeHandler(VerticalChangeHandler())
                    )
                }
            viewThemeUtils.colorMaterialAlertDialogBackground(binding.floatingActionButton!!.context, dialogBuilder)
            val dialog = dialogBuilder.show()
            viewThemeUtils.colorTextButtons(
                dialog.getButton(AlertDialog.BUTTON_POSITIVE),
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            )
        }
    }

    private fun showServerEOLDialog() {
        val dialogBuilder = MaterialAlertDialogBuilder(binding.floatingActionButton!!.context)
            .setIcon(viewThemeUtils.colorMaterialAlertDialogIcon(context, R.drawable.ic_warning_white))
            .setTitle(R.string.nc_settings_server_eol_title)
            .setMessage(R.string.nc_settings_server_eol)
            .setCancelable(false)
            .setPositiveButton(R.string.nc_settings_remove_account) { dialog: DialogInterface?, which: Int ->
                val otherUserExists = userManager
                    .scheduleUserForDeletionWithId(currentUser!!.id!!)
                    .blockingGet()
                val accountRemovalWork = OneTimeWorkRequest.Builder(AccountRemovalWorker::class.java).build()
                WorkManager.getInstance().enqueue(accountRemovalWork)
                if (otherUserExists && view != null) {
                    onViewBound(view!!)
                    onAttach(view!!)
                } else if (!otherUserExists) {
                    router.setRoot(
                        RouterTransaction.with(
                            ServerSelectionController()
                        )
                            .pushChangeHandler(VerticalChangeHandler())
                            .popChangeHandler(VerticalChangeHandler())
                    )
                }
            }
            .setNegativeButton(R.string.nc_cancel) { dialog: DialogInterface?, which: Int ->
                if (userManager.users.blockingGet().size > 0) {
                    router.pushController(RouterTransaction.with(SwitchAccountController()))
                } else {
                    activity!!.finishAffinity()
                    activity!!.finish()
                }
            }
        viewThemeUtils.colorMaterialAlertDialogBackground(binding.floatingActionButton!!.context, dialogBuilder)
        val dialog = dialogBuilder.show()
        viewThemeUtils.colorTextButtons(
            dialog.getButton(AlertDialog.BUTTON_POSITIVE),
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
        )
    }

    private fun deleteConversation(data: Data) {
        val deleteConversationWorker =
            OneTimeWorkRequest.Builder(DeleteConversationWorker::class.java).setInputData(data).build()
        WorkManager.getInstance().enqueue(deleteConversationWorker)
    }

    fun onMessageSearchResult(results: MessageSearchResults) {
        if (searchView!!.query.length > 0) {
            clearMessageSearchResults()
            val entries = results.messages
            if (entries.size > 0) {
                val adapterItems: MutableList<AbstractFlexibleItem<*>> = ArrayList(entries.size + 1)
                for (i in entries.indices) {
                    val showHeader = i == 0
                    adapterItems.add(
                        MessageResultItem(
                            context,
                            currentUser!!,
                            entries[i],
                            showHeader,
                            viewThemeUtils
                        )
                    )
                }
                if (results.hasMore) {
                    adapterItems.add(LoadMoreResultsItem)
                }
                adapter!!.addItems(0, adapterItems)
                binding.recyclerView!!.scrollToPosition(0)
            }
        }
        if (binding.swipeRefreshLayoutView != null) {
            binding.swipeRefreshLayoutView!!.isRefreshing = false
        }
    }

    fun onMessageSearchError(throwable: Throwable) {
        handleHttpExceptions(throwable)
        if (binding.swipeRefreshLayoutView != null) {
            binding.swipeRefreshLayoutView!!.isRefreshing = false
        }
    }

    companion object {
        const val TAG = "ConvListController"
        const val UNREAD_BUBBLE_DELAY = 2500
        private const val KEY_SEARCH_QUERY = "ContactsController.searchQuery"
        const val SEARCH_DEBOUNCE_INTERVAL_MS = 300
        const val SEARCH_MIN_CHARS = 2
    }

    init {
        setHasOptionsMenu(true)
        forwardMessage = bundle.getBoolean(KEY_FORWARD_MSG_FLAG)
        this.bundle = bundle
    }
}
