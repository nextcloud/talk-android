/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022-2024 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2022-2023 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2023 Tobias Kaminsky <tobias@kaminsky.me>
 * SPDX-FileCopyrightText: 2023 Ezhil Shanmugham <ezhil56x.contact@gmail.com>
 * SPDX-FileCopyrightText: 2022 Álvaro Brey <alvaro.brey@nextcloud.com>
 * SPDX-FileCopyrightText: 2017-2020 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.conversationlist

import android.Manifest
import android.animation.AnimatorInflater
import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.text.InputType
import android.text.TextUtils
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuItemCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import autodagger.AutoInjector
import coil.imageLoader
import coil.request.ImageRequest
import coil.target.Target
import coil.transform.CircleCropTransformation
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.badge.BadgeUtils
import com.google.android.material.badge.ExperimentalBadgeUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.nextcloud.android.common.ui.theme.utils.ColorRole
import com.nextcloud.talk.R
import com.nextcloud.talk.account.ServerSelectionActivity
import com.nextcloud.talk.account.WebViewLoginActivity
import com.nextcloud.talk.activities.BaseActivity
import com.nextcloud.talk.activities.CallActivity
import com.nextcloud.talk.activities.MainActivity
import com.nextcloud.talk.adapters.items.ConversationItem
import com.nextcloud.talk.adapters.items.GenericTextHeaderItem
import com.nextcloud.talk.adapters.items.LoadMoreResultsItem
import com.nextcloud.talk.adapters.items.MessageResultItem
import com.nextcloud.talk.adapters.items.MessagesTextHeaderItem
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.arbitrarystorage.ArbitraryStorageManager
import com.nextcloud.talk.chat.ChatActivity
import com.nextcloud.talk.contacts.ContactsActivity
import com.nextcloud.talk.conversationlist.viewmodels.ConversationsListViewModel
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.ActivityConversationsBinding
import com.nextcloud.talk.events.ConversationsListFetchDataEvent
import com.nextcloud.talk.events.EventStatus
import com.nextcloud.talk.invitation.InvitationsActivity
import com.nextcloud.talk.jobs.AccountRemovalWorker
import com.nextcloud.talk.jobs.ContactAddressBookWorker.Companion.run
import com.nextcloud.talk.jobs.DeleteConversationWorker
import com.nextcloud.talk.jobs.UploadAndShareFilesWorker
import com.nextcloud.talk.messagesearch.MessageSearchHelper
import com.nextcloud.talk.messagesearch.MessageSearchHelper.MessageSearchResults
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.conversations.RoomsOverall
import com.nextcloud.talk.repositories.unifiedsearch.UnifiedSearchRepository
import com.nextcloud.talk.settings.SettingsActivity
import com.nextcloud.talk.ui.dialog.ChooseAccountDialogFragment
import com.nextcloud.talk.ui.dialog.ChooseAccountShareToDialogFragment
import com.nextcloud.talk.ui.dialog.ConversationsListBottomDialog
import com.nextcloud.talk.ui.dialog.FilterConversationFragment
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.BrandingUtils
import com.nextcloud.talk.utils.CapabilitiesUtil.hasSpreedFeatureCapability
import com.nextcloud.talk.utils.CapabilitiesUtil.isServerEOL
import com.nextcloud.talk.utils.CapabilitiesUtil.isUnifiedSearchAvailable
import com.nextcloud.talk.utils.CapabilitiesUtil.isUserStatusAvailable
import com.nextcloud.talk.utils.ClosedInterfaceImpl
import com.nextcloud.talk.utils.FileUtils
import com.nextcloud.talk.utils.Mimetype
import com.nextcloud.talk.utils.ParticipantPermissions
import com.nextcloud.talk.utils.SpreedFeatures
import com.nextcloud.talk.utils.UserIdUtils
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.bundle.BundleKeys.ADD_ADDITIONAL_ACCOUNT
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_FORWARD_HIDE_SOURCE_ROOM
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_FORWARD_MSG_FLAG
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_FORWARD_MSG_TEXT
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_INTERNAL_USER_ID
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_NEW_CONVERSATION
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_ID
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_SHARED_TEXT
import com.nextcloud.talk.utils.permissions.PlatformPermissionUtil
import com.nextcloud.talk.utils.power.PowerManagerUtils
import com.nextcloud.talk.utils.rx.SearchViewObservable.Companion.observeSearchView
import com.nextcloud.talk.utils.singletons.ApplicationWideCurrentRoomHolder
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.common.SmoothScrollLinearLayoutManager
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import org.apache.commons.lang3.builder.CompareToBuilder
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import retrofit2.HttpException
import java.util.Objects
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class ConversationsListActivity :
    BaseActivity(),
    FlexibleAdapter.OnItemClickListener,
    FlexibleAdapter.OnItemLongClickListener {

    private lateinit var binding: ActivityConversationsBinding

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var ncApi: NcApi

    @Inject
    lateinit var unifiedSearchRepository: UnifiedSearchRepository

    @Inject
    lateinit var platformPermissionUtil: PlatformPermissionUtil

    @Inject
    lateinit var arbitraryStorageManager: ArbitraryStorageManager

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    lateinit var conversationsListViewModel: ConversationsListViewModel

    override val appBarLayoutType: AppBarLayoutType
        get() = AppBarLayoutType.SEARCH_BAR

    private var currentUser: User? = null
    private var roomsQueryDisposable: Disposable? = null
    private var openConversationsQueryDisposable: Disposable? = null
    private var adapter: FlexibleAdapter<AbstractFlexibleItem<*>>? = null
    private var conversationItems: MutableList<AbstractFlexibleItem<*>> = ArrayList()
    private var conversationItemsWithHeader: MutableList<AbstractFlexibleItem<*>> = ArrayList()
    private val searchableConversationItems: MutableList<AbstractFlexibleItem<*>> = ArrayList()
    private var filterableConversationItems: MutableList<AbstractFlexibleItem<*>> = ArrayList()
    private var searchItem: MenuItem? = null
    private var chooseAccountItem: MenuItem? = null
    private var searchView: SearchView? = null
    private var searchQuery: String? = null
    private var credentials: String? = null
    private var adapterWasNull = true
    private var isRefreshing = false
    private var showShareToScreen = false
    private var filesToShare: ArrayList<String>? = null
    private var selectedConversation: Conversation? = null
    private var textToPaste: String? = ""
    private var selectedMessageId: String? = null
    private var forwardMessage: Boolean = false
    private var nextUnreadConversationScrollPosition = 0
    private var layoutManager: SmoothScrollLinearLayoutManager? = null
    private val callHeaderItems = HashMap<String, GenericTextHeaderItem>()
    private var conversationsListBottomDialog: ConversationsListBottomDialog? = null
    private var searchHelper: MessageSearchHelper? = null
    private var searchViewDisposable: Disposable? = null
    private var filterState =
        mutableMapOf(
            FilterConversationFragment.MENTION to false,
            FilterConversationFragment.UNREAD to false
        )
    val searchBehaviorSubject = BehaviorSubject.createDefault(false)
    private lateinit var accountIconBadge: BadgeDrawable

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

        conversationsListViewModel = ViewModelProvider(this, viewModelFactory)[ConversationsListViewModel::class.java]

        binding = ActivityConversationsBinding.inflate(layoutInflater)
        setupActionBar()
        setContentView(binding.root)
        setupSystemColors()
        viewThemeUtils.material.themeCardView(binding.searchToolbar)
        viewThemeUtils.material.themeSearchBarText(binding.searchText)

        forwardMessage = intent.getBooleanExtra(KEY_FORWARD_MSG_FLAG, false)
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        initObservers()
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        // handle notification permission on API level >= 33
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !platformPermissionUtil.isPostNotificationsPermissionGranted() &&
            ClosedInterfaceImpl().isGooglePlayServicesAvailable
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_POST_NOTIFICATIONS_PERMISSION
            )
        }
    }

    override fun onResume() {
        super.onResume()

        // actionBar?.show()
        if (adapter == null) {
            adapter = FlexibleAdapter(conversationItems, this, true)
        } else {
            binding?.loadingContent?.visibility = View.GONE
        }
        adapter!!.addListener(this)
        prepareViews()

        showShareToScreen = hasActivityActionSendIntent()

        if (!eventBus.isRegistered(this)) {
            eventBus.register(this)
        }
        currentUser = userManager.currentUser.blockingGet()
        if (currentUser != null) {
            if (isServerEOL(currentUser!!.serverVersion?.major)) {
                showServerEOLDialog()
                return
            }
            if (isUnifiedSearchAvailable(currentUser!!.capabilities!!.spreedCapability!!)) {
                searchHelper = MessageSearchHelper(unifiedSearchRepository)
            }
            credentials = ApiUtils.getCredentials(currentUser!!.username, currentUser!!.token)

            loadUserAvatar(binding.switchAccountButton)
            viewThemeUtils.material.colorMaterialTextButton(binding.switchAccountButton)
            viewThemeUtils.material.themeCardView(binding.conversationListHintInclude.hintLayoutCardview)
            searchBehaviorSubject.onNext(false)
            fetchRooms()
            fetchPendingInvitations()
        } else {
            Log.e(TAG, "userManager.currentUser.blockingGet() returned null")
            Snackbar.make(binding.root, R.string.nc_common_error_sorry, Snackbar.LENGTH_LONG).show()
        }

        showSearchOrToolbar()
    }

    private fun initObservers() {
        conversationsListViewModel.getFederationInvitationsViewState.observe(this) { state ->
            when (state) {
                is ConversationsListViewModel.GetFederationInvitationsStartState -> {
                    binding.conversationListHintInclude.conversationListHintLayout.visibility = View.GONE
                }

                is ConversationsListViewModel.GetFederationInvitationsSuccessState -> {
                    if (state.showInvitationsHint) {
                        binding.conversationListHintInclude.conversationListHintLayout.visibility = View.VISIBLE
                    } else {
                        binding.conversationListHintInclude.conversationListHintLayout.visibility = View.GONE
                    }
                }

                is ConversationsListViewModel.GetFederationInvitationsErrorState -> {
                    Snackbar.make(binding.root, R.string.get_invitations_error, Snackbar.LENGTH_LONG).show()
                }

                else -> {}
            }
        }

        conversationsListViewModel.showBadgeViewState.observe(this) { state ->
            when (state) {
                is ConversationsListViewModel.ShowBadgeStartState -> {
                    showAccountIconBadge(false)
                }

                is ConversationsListViewModel.ShowBadgeSuccessState -> {
                    showAccountIconBadge(state.showBadge)
                }

                is ConversationsListViewModel.ShowBadgeErrorState -> {
                    Snackbar.make(binding.root, R.string.nc_common_error_sorry, Snackbar.LENGTH_LONG).show()
                }

                else -> {}
            }
        }
    }

    fun filterConversation() {
        val accountId = UserIdUtils.getIdForUser(userManager.currentUser.blockingGet())
        filterState[FilterConversationFragment.UNREAD] = (
            arbitraryStorageManager.getStorageSetting(
                accountId,
                FilterConversationFragment.UNREAD,
                ""
            ).blockingGet()?.value ?: ""
            ) == "true"

        filterState[FilterConversationFragment.MENTION] = (
            arbitraryStorageManager.getStorageSetting(
                accountId,
                FilterConversationFragment.MENTION,
                ""
            ).blockingGet()?.value ?: ""
            ) == "true"

        val newItems: MutableList<AbstractFlexibleItem<*>> = ArrayList()
        if (filterState[FilterConversationFragment.UNREAD] == false &&
            filterState[FilterConversationFragment.MENTION] == false
        ) {
            adapter!!.updateDataSet(conversationItems, true)
        } else {
            val items = conversationItems
            for (i in items) {
                val conversation = (i as ConversationItem).model
                if (filter(conversation)) {
                    newItems.add(i)
                }
            }
            adapter!!.updateDataSet(newItems, true)
            setFilterableItems(newItems)
        }

        updateFilterConversationButtonColor()
    }

    private fun filter(conversation: Conversation): Boolean {
        var result = true
        for ((k, v) in filterState) {
            if (v) {
                when (k) {
                    FilterConversationFragment.MENTION -> result = (result && conversation.unreadMention) ||
                        (
                            result &&
                                (
                                    conversation.type == Conversation.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL ||
                                        conversation.type == Conversation.ConversationType.FORMER_ONE_TO_ONE
                                    ) &&
                                (conversation.unreadMessages > 0)
                            )

                    FilterConversationFragment.UNREAD -> result = result && (conversation.unreadMessages > 0)
                }
            }
        }

        return result
    }

    private fun setupActionBar() {
        setSupportActionBar(binding.conversationListToolbar)
        binding.conversationListToolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setIcon(ColorDrawable(resources!!.getColor(R.color.transparent, null)))
        supportActionBar?.title = resources!!.getString(R.string.nc_app_product_name)
        viewThemeUtils.material.themeToolbar(binding.conversationListToolbar)
    }

    private fun loadUserAvatar(target: Target) {
        if (currentUser != null) {
            val url = ApiUtils.getUrlForAvatar(
                currentUser!!.baseUrl!!,
                currentUser!!.userId,
                true
            )

            val credentials = ApiUtils.getCredentials(currentUser!!.username, currentUser!!.token)

            context.imageLoader.enqueue(
                ImageRequest.Builder(context)
                    .data(url)
                    .addHeader("Authorization", credentials!!)
                    .placeholder(R.drawable.ic_user)
                    .transformations(CircleCropTransformation())
                    .crossfade(true)
                    .target(target)
                    .build()
            )
        } else {
            Log.e(TAG, "currentUser was null in loadUserAvatar")
            Snackbar.make(binding.root, R.string.nc_common_error_sorry, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun loadUserAvatar(button: MaterialButton) {
        val target = object : Target {
            override fun onStart(placeholder: Drawable?) {
                button.icon = placeholder
            }

            override fun onSuccess(result: Drawable) {
                button.icon = result
            }
        }

        loadUserAvatar(target)
    }

    private fun loadUserAvatar(menuItem: MenuItem) {
        val target = object : Target {
            override fun onStart(placeholder: Drawable?) {
                menuItem.icon = placeholder
            }

            override fun onSuccess(result: Drawable) {
                menuItem.icon = result
            }
        }
        loadUserAvatar(target)
    }

    private fun initSearchView() {
        val searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager?
        if (searchItem != null) {
            searchView = MenuItemCompat.getActionView(searchItem) as SearchView
            viewThemeUtils.talk.themeSearchView(searchView!!)
            searchView!!.maxWidth = Int.MAX_VALUE
            searchView!!.inputType = InputType.TYPE_TEXT_VARIATION_FILTER
            var imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_FULLSCREEN
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appPreferences.isKeyboardIncognito) {
                imeOptions = imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            }
            searchView!!.imeOptions = imeOptions
            searchView!!.queryHint = getString(R.string.appbar_search_in, getString(R.string.nc_app_product_name))
            if (searchManager != null) {
                searchView!!.setSearchableInfo(searchManager.getSearchableInfo(componentName))
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)

        menuInflater.inflate(R.menu.menu_conversation_plus_filter, menu)
        searchItem = menu.findItem(R.id.action_search)
        chooseAccountItem = menu.findItem(R.id.action_choose_account)
        loadUserAvatar(chooseAccountItem!!)

        chooseAccountItem?.setOnMenuItemClickListener {
            if (resources != null && resources!!.getBoolean(R.bool.multiaccount_support)) {
                val newFragment: DialogFragment = ChooseAccountShareToDialogFragment.newInstance()
                newFragment.show(
                    supportFragmentManager,
                    ChooseAccountShareToDialogFragment.TAG
                )
            }
            true
        }
        initSearchView()
        return true
    }

    @OptIn(ExperimentalBadgeUtils::class)
    fun showAccountIconBadge(showBadge: Boolean) {
        if (!::accountIconBadge.isInitialized) {
            accountIconBadge = BadgeDrawable.create(binding.switchAccountButton.context)
            accountIconBadge.verticalOffset = BADGE_OFFSET
            accountIconBadge.horizontalOffset = BADGE_OFFSET
            accountIconBadge.backgroundColor = resources.getColor(R.color.badge_color, null)
        }

        if (showBadge) {
            BadgeUtils.attachBadgeDrawable(accountIconBadge, binding.switchAccountButton)
        } else {
            BadgeUtils.detachBadgeDrawable(accountIconBadge, binding.switchAccountButton)
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        super.onPrepareOptionsMenu(menu)

        searchView = MenuItemCompat.getActionView(searchItem) as SearchView

        val moreAccountsAvailable = userManager.users.blockingGet().size > 1
        menu.findItem(R.id.action_choose_account).isVisible = showShareToScreen && moreAccountsAvailable

        if (showShareToScreen) {
            hideSearchBar()
            supportActionBar?.setTitle(R.string.send_to_three_dots)
        } else if (forwardMessage) {
            hideSearchBar()
            supportActionBar?.setTitle(R.string.nc_forward_to_three_dots)
        } else {
            searchItem!!.isVisible = conversationItems.size > 0
            if (adapter!!.hasFilter()) {
                showSearchView(searchView, searchItem)
                searchView!!.setQuery(adapter!!.getFilter(String::class.java), false)
            }
            binding.searchText.setOnClickListener {
                showSearchView(searchView, searchItem)
                viewThemeUtils.platform.themeStatusBar(this)
            }
            searchView!!.setOnCloseListener {
                if (TextUtils.isEmpty(searchView!!.query.toString())) {
                    searchView!!.onActionViewCollapsed()
                    viewThemeUtils.platform.resetStatusBar(this)
                } else {
                    searchView!!.post { searchView!!.setQuery(TAG, true) }
                }
                true
            }
            searchItem!!.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
                override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                    adapter!!.setHeadersShown(true)
                    if (!filterState.containsValue(true)) filterableConversationItems = searchableConversationItems
                    adapter!!.updateDataSet(filterableConversationItems, false)
                    adapter!!.showAllHeaders()
                    binding?.swipeRefreshLayoutView?.isEnabled = false
                    searchBehaviorSubject.onNext(true)
                    return true
                }

                override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                    adapter!!.setHeadersShown(false)
                    if (!filterState.containsValue(true)) filterableConversationItems = conversationItemsWithHeader
                    adapter!!.updateDataSet(filterableConversationItems, false)
                    adapter!!.hideAllHeaders()
                    if (searchHelper != null) {
                        // cancel any pending searches
                        searchHelper!!.cancelSearch()
                        binding?.swipeRefreshLayoutView?.isRefreshing = false
                        searchBehaviorSubject.onNext(false)
                    }
                    binding?.swipeRefreshLayoutView?.isEnabled = true
                    searchView!!.onActionViewCollapsed()

                    binding.conversationListAppbar.stateListAnimator = AnimatorInflater.loadStateListAnimator(
                        binding.conversationListAppbar.context,
                        R.animator.appbar_elevation_off
                    )
                    binding.conversationListToolbar.visibility = View.GONE
                    binding.searchToolbar.visibility = View.VISIBLE
                    if (resources != null) {
                        viewThemeUtils.platform.resetStatusBar(this@ConversationsListActivity)
                    }

                    val layoutManager = binding?.recyclerView?.layoutManager as SmoothScrollLinearLayoutManager?
                    layoutManager?.scrollToPositionWithOffset(0, 0)
                    return true
                }
            })
        }
        return true
    }

    private fun showSearchOrToolbar() {
        if (TextUtils.isEmpty(searchQuery)) {
            if (appBarLayoutType == AppBarLayoutType.SEARCH_BAR) {
                showSearchBar()
            } else {
                showToolbar()
            }
            colorizeStatusBar()
            colorizeNavigationBar()
        }
    }

    private fun showSearchBar() {
        val layoutParams = binding.searchToolbar.layoutParams as AppBarLayout.LayoutParams
        binding.searchToolbar.visibility = View.VISIBLE
        binding.searchText.hint = getString(R.string.appbar_search_in, getString(R.string.nc_app_product_name))
        binding.conversationListToolbar.visibility = View.GONE
        // layoutParams.setScrollFlags(AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL | AppBarLayout
        // .LayoutParams.SCROLL_FLAG_SNAP | AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS);
        layoutParams.scrollFlags = 0
        binding.conversationListAppbar.stateListAnimator = AnimatorInflater.loadStateListAnimator(
            binding.conversationListAppbar.context,
            R.animator.appbar_elevation_off
        )
        binding.searchToolbar.layoutParams = layoutParams
    }

    private fun showToolbar() {
        val layoutParams = binding.searchToolbar.layoutParams as AppBarLayout.LayoutParams
        binding.searchToolbar.visibility = View.GONE
        binding.conversationListToolbar.visibility = View.VISIBLE
        viewThemeUtils.material.colorToolbarOverflowIcon(binding.conversationListToolbar)
        layoutParams.scrollFlags = 0
        binding.conversationListAppbar.stateListAnimator = AnimatorInflater.loadStateListAnimator(
            binding.conversationListAppbar.context,
            R.animator.appbar_elevation_on
        )
        binding.conversationListToolbar.layoutParams = layoutParams
    }

    private fun hideSearchBar() {
        val layoutParams = binding.searchToolbar.layoutParams as AppBarLayout.LayoutParams
        binding.searchToolbar.visibility = View.GONE
        binding.conversationListToolbar.visibility = View.VISIBLE
        layoutParams.scrollFlags = 0
        binding.conversationListAppbar.stateListAnimator = AnimatorInflater.loadStateListAnimator(
            binding.conversationListAppbar.context,
            R.animator.appbar_elevation_on
        )
    }

    private fun hasActivityActionSendIntent(): Boolean {
        return Intent.ACTION_SEND == intent.action || Intent.ACTION_SEND_MULTIPLE == intent.action
    }

    private fun showSearchView(searchView: SearchView?, searchItem: MenuItem?) {
        binding.conversationListAppbar.stateListAnimator = AnimatorInflater.loadStateListAnimator(
            binding.conversationListAppbar.context,
            R.animator.appbar_elevation_on
        )
        binding.conversationListToolbar.visibility = View.VISIBLE
        binding.searchToolbar.visibility = View.GONE
        searchItem!!.expandActionView()
    }

    fun showSnackbar(text: String) {
        Snackbar.make(binding.root, text, Snackbar.LENGTH_LONG).show()
    }

    fun fetchRooms() {
        val includeStatus = isUserStatusAvailable(userManager.currentUser.blockingGet())

        // checks internet connection before fetching rooms
        if (isNetworkAvailable(context)) {
            Log.d(TAG, "Internet connection available")
            dispose(null)
            isRefreshing = true
            conversationItems = ArrayList()
            conversationItemsWithHeader = ArrayList()
            val apiVersion = ApiUtils.getConversationApiVersion(
                currentUser!!,
                intArrayOf(ApiUtils.API_V4, ApiUtils.API_V3, 1)
            )
            val startNanoTime = System.nanoTime()
            Log.d(TAG, "fetchData - getRooms - calling: $startNanoTime")
            roomsQueryDisposable = ncApi.getRooms(
                credentials,
                ApiUtils.getUrlForRooms(
                    apiVersion,
                    currentUser!!.baseUrl
                ),
                includeStatus
            )
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ (ocs): RoomsOverall ->
                    Log.d(TAG, "fetchData - getRooms - got response: $startNanoTime")

                    // This is invoked asynchronously, when server returns a response the view might have been
                    // unbound in the meantime. Check if the view is still there.
                    // FIXME - does it make sense to update internal data structures even when view has been unbound?
                    // if (view == null) {
                    //     Log.d(TAG, "fetchData - getRooms - view is not bound: $startNanoTime")
                    //     return@subscribe
                    // }

                    if (adapterWasNull) {
                        adapterWasNull = false
                        binding?.loadingContent?.visibility = View.GONE
                    }
                    initOverallLayout(ocs!!.data!!.isNotEmpty())
                    for (conversation in ocs.data!!) {
                        addToConversationItems(conversation)
                    }
                    sortConversations(conversationItems)
                    sortConversations(conversationItemsWithHeader)
                    if (!filterState.containsValue(true)) filterableConversationItems = conversationItems
                    filterConversation()
                    adapter!!.updateDataSet(filterableConversationItems, false)
                    Handler().postDelayed({ checkToShowUnreadBubble() }, UNREAD_BUBBLE_DELAY.toLong())
                    fetchOpenConversations(apiVersion)
                    binding?.swipeRefreshLayoutView?.isRefreshing = false
                }, { throwable: Throwable ->
                    handleHttpExceptions(throwable)
                    binding?.swipeRefreshLayoutView?.isRefreshing = false
                    dispose(roomsQueryDisposable)
                }) {
                    dispose(roomsQueryDisposable)
                    binding?.swipeRefreshLayoutView?.isRefreshing = false
                    isRefreshing = false
                }
        } else {
            Log.d(TAG, "No internet connection detected")
            showNetworkErrorDialog()
        }
    }

    private fun fetchPendingInvitations() {
        if (hasSpreedFeatureCapability(currentUser!!.capabilities!!.spreedCapability!!, SpreedFeatures.FEDERATION_V1)) {
            binding.conversationListHintInclude.conversationListHintLayout.setOnClickListener {
                val intent = Intent(this, InvitationsActivity::class.java)
                startActivity(intent)
            }
            conversationsListViewModel.getFederationInvitations()
        }
    }

    private fun initOverallLayout(isConversationListNotEmpty: Boolean) {
        if (isConversationListNotEmpty) {
            if (binding?.emptyLayout?.visibility != View.GONE) {
                binding?.emptyLayout?.visibility = View.GONE
            }
            if (binding?.swipeRefreshLayoutView?.visibility != View.VISIBLE) {
                binding?.swipeRefreshLayoutView?.visibility = View.VISIBLE
            }
        } else {
            if (binding?.emptyLayout?.visibility != View.VISIBLE) {
                binding?.emptyLayout?.visibility = View.VISIBLE
            }
            if (binding?.swipeRefreshLayoutView?.visibility != View.GONE) {
                binding?.swipeRefreshLayoutView?.visibility = View.GONE
            }
        }
    }

    private fun addToConversationItems(conversation: Conversation) {
        if (intent.getStringExtra(KEY_FORWARD_HIDE_SOURCE_ROOM) != null &&
            intent.getStringExtra(KEY_FORWARD_HIDE_SOURCE_ROOM) == conversation.roomId
        ) {
            return
        }

        if (conversation.objectType == Conversation.ObjectType.ROOM &&
            conversation.lobbyState == Conversation.LobbyState.LOBBY_STATE_MODERATORS_ONLY
        ) {
            return
        }

        val headerTitle: String = resources!!.getString(R.string.conversations)
        val genericTextHeaderItem: GenericTextHeaderItem
        if (!callHeaderItems.containsKey(headerTitle)) {
            genericTextHeaderItem = GenericTextHeaderItem(headerTitle, viewThemeUtils)
            callHeaderItems[headerTitle] = genericTextHeaderItem
        }

        val conversationItem = ConversationItem(
            conversation,
            currentUser!!,
            this,
            viewThemeUtils
        )
        conversationItems.add(conversationItem)
        val conversationItemWithHeader = ConversationItem(
            conversation,
            currentUser!!,
            this,
            callHeaderItems[headerTitle],
            viewThemeUtils
        )
        conversationItemsWithHeader.add(conversationItemWithHeader)
    }

    private fun showErrorDialog() {
        binding.floatingActionButton.let {
            val dialogBuilder = MaterialAlertDialogBuilder(it.context)
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
                    val newFragment: DialogFragment = ChooseAccountDialogFragment.newInstance()
                    newFragment.show(supportFragmentManager, ChooseAccountDialogFragment.TAG)
                }
            }

            if (resources!!.getBoolean(R.bool.multiaccount_support)) {
                dialogBuilder.setNeutralButton(R.string.nc_account_chooser_add_account) { _, _ ->
                    val intent = Intent(this, ServerSelectionActivity::class.java)
                    intent.putExtra(ADD_ADDITIONAL_ACCOUNT, true)
                    startActivity(intent)
                }
            }

            viewThemeUtils.dialog.colorMaterialAlertDialogBackground(it.context, dialogBuilder)
            val dialog = dialogBuilder.show()
            viewThemeUtils.platform.colorTextButtons(
                dialog.getButton(AlertDialog.BUTTON_POSITIVE),
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE),
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            )
        }
    }

    private fun showNetworkErrorDialog() {
        binding.floatingActionButton.let {
            val dialogBuilder = MaterialAlertDialogBuilder(it.context)
                .setIcon(
                    viewThemeUtils.dialog.colorMaterialAlertDialogIcon(
                        context,
                        R.drawable.ic_baseline_error_outline_24dp
                    )
                )
                .setTitle(R.string.nc_check_your_internet)
                .setCancelable(false)
                .setNegativeButton(R.string.close, null)
                .setNeutralButton(R.string.nc_refresh) { _, _ ->
                    fetchRooms()
                    fetchPendingInvitations()
                }

            viewThemeUtils.dialog.colorMaterialAlertDialogBackground(it.context, dialogBuilder)
            val dialog = dialogBuilder.show()
            viewThemeUtils.platform.colorTextButtons(
                dialog.getButton(AlertDialog.BUTTON_POSITIVE),
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE),
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            )
        }
    }

    @Suppress("ReturnCount")
    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private fun sortConversations(conversationItems: MutableList<AbstractFlexibleItem<*>>) {
        conversationItems.sortWith { o1: AbstractFlexibleItem<*>, o2: AbstractFlexibleItem<*> ->
            val conversation1 = (o1 as ConversationItem).model
            val conversation2 = (o2 as ConversationItem).model
            CompareToBuilder()
                .append(conversation2.favorite, conversation1.favorite)
                .append(conversation2.lastActivity, conversation1.lastActivity)
                .toComparison()
        }
    }

    private fun fetchOpenConversations(apiVersion: Int) {
        searchableConversationItems.clear()
        searchableConversationItems.addAll(conversationItemsWithHeader)
        if (hasSpreedFeatureCapability(
                currentUser!!.capabilities!!.spreedCapability!!,
                SpreedFeatures.LISTABLE_ROOMS
            )
        ) {
            val openConversationItems: MutableList<AbstractFlexibleItem<*>> = ArrayList()
            openConversationsQueryDisposable = ncApi.getOpenConversations(
                credentials,
                ApiUtils.getUrlForOpenConversations(apiVersion, currentUser!!.baseUrl!!)
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
                            currentUser!!,
                            this,
                            callHeaderItems[headerTitle],
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

    @SuppressLint("ClickableViewAccessibility")
    private fun prepareViews() {
        hideLogoForBrandedClients()

        layoutManager = SmoothScrollLinearLayoutManager(this)
        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.adapter = adapter
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val isSearchActive = searchBehaviorSubject.value
                    if (!isSearchActive!!) {
                        checkToShowUnreadBubble()
                    }
                }
            }
        })
        binding?.recyclerView?.setOnTouchListener { v: View, _: MotionEvent? ->
            if (!isDestroyed) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(v.windowToken, 0)
            }
            false
        }
        binding?.swipeRefreshLayoutView?.setOnRefreshListener {
            fetchRooms()
            fetchPendingInvitations()
        }
        binding?.swipeRefreshLayoutView?.let { viewThemeUtils.androidx.themeSwipeRefreshLayout(it) }
        binding?.emptyLayout?.setOnClickListener { showNewConversationsScreen() }
        binding?.floatingActionButton?.setOnClickListener {
            run(context)
            showNewConversationsScreen()
        }
        binding?.floatingActionButton?.let { viewThemeUtils.material.themeFAB(it) }

        binding.switchAccountButton.setOnClickListener {
            if (resources != null && resources!!.getBoolean(R.bool.multiaccount_support)) {
                val newFragment: DialogFragment = ChooseAccountDialogFragment.newInstance()
                newFragment.show(supportFragmentManager, ChooseAccountDialogFragment.TAG)
            } else {
                val intent = Intent(context, SettingsActivity::class.java)
                startActivity(intent)
            }
        }

        updateFilterConversationButtonColor()

        binding.filterConversationsButton.setOnClickListener {
            val newFragment = FilterConversationFragment.newInstance(filterState)
            newFragment.show(supportFragmentManager, FilterConversationFragment.TAG)
        }

        binding?.newMentionPopupBubble?.hide()
        binding?.newMentionPopupBubble?.setPopupBubbleListener {
            binding?.recyclerView?.smoothScrollToPosition(
                nextUnreadConversationScrollPosition
            )
        }
        binding?.newMentionPopupBubble?.let { viewThemeUtils.material.colorMaterialButtonPrimaryFilled(it) }
    }

    private fun hideLogoForBrandedClients() {
        if (!BrandingUtils.isOriginalNextcloudClient(applicationContext)) {
            binding.emptyListIcon.visibility = View.GONE
        }
    }

    @SuppressLint("CheckResult")
    @Suppress("Detekt.TooGenericExceptionCaught")
    private fun checkToShowUnreadBubble() {
        searchBehaviorSubject.subscribe { value ->
            if (value) {
                nextUnreadConversationScrollPosition = 0
                binding.newMentionPopupBubble.hide()
            } else {
                try {
                    val lastVisibleItem = layoutManager!!.findLastCompletelyVisibleItemPosition()
                    for (flexItem in conversationItems) {
                        val conversation: Conversation = (flexItem as ConversationItem).model
                        val position = adapter!!.getGlobalPositionOf(flexItem)
                        if (hasUnreadItems(conversation) && position > lastVisibleItem) {
                            nextUnreadConversationScrollPosition = position
                            if (!binding?.newMentionPopupBubble?.isShown!!) {
                                binding?.newMentionPopupBubble?.show()
                            }
                            return@subscribe
                        }
                        nextUnreadConversationScrollPosition = 0
                        binding?.newMentionPopupBubble?.hide()
                    }
                } catch (e: NullPointerException) {
                    Log.d(
                        TAG,
                        "A NPE was caught when trying to show the unread popup bubble. This might happen when the " +
                            "user already left the conversations-list screen so the popup bubble is not available " +
                            "anymore.",
                        e
                    )
                }
            }
        }
    }

    private fun hasUnreadItems(conversation: Conversation) =
        conversation.unreadMention ||
            conversation.unreadMessages > 0 &&
            conversation.type === Conversation.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL

    private fun showNewConversationsScreen() {
        val intent = Intent(context, ContactsActivity::class.java)
        intent.putExtra(KEY_NEW_CONVERSATION, true)
        startActivity(intent)
    }

    private fun dispose(disposable: Disposable?) {
        if (disposable != null && !disposable.isDisposed) {
            disposable.dispose()
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

    override fun onSaveInstanceState(bundle: Bundle) {
        super.onSaveInstanceState(bundle)

        if (searchView != null && !TextUtils.isEmpty(searchView!!.query)) {
            bundle.putString(KEY_SEARCH_QUERY, searchView!!.query.toString())
        }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)

        if (savedInstanceState.containsKey(KEY_SEARCH_QUERY)) {
            searchQuery = savedInstanceState.getString(KEY_SEARCH_QUERY, "")
        }
    }

    public override fun onDestroy() {
        super.onDestroy()
        dispose(null)
        if (searchViewDisposable != null && !searchViewDisposable!!.isDisposed) {
            searchViewDisposable!!.dispose()
        }
    }

    private fun onQueryTextChange(newText: String?) {
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
            if (isUnifiedSearchAvailable(currentUser!!.capabilities!!.spreedCapability!!)) {
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
        binding?.swipeRefreshLayoutView?.isRefreshing = true
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
        binding.swipeRefreshLayoutView.isRefreshing = true
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
                    handleConversation((Objects.requireNonNull(item) as ConversationItem).model)
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
                handleConversation(conversation)
            }
        }
    }

    @Suppress("Detekt.ComplexMethod")
    private fun handleConversation(conversation: Conversation?) {
        selectedConversation = conversation
        if (selectedConversation != null) {
            val hasChatPermission = ParticipantPermissions(
                currentUser!!.capabilities!!.spreedCapability!!,
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
                    Snackbar.make(binding.root, R.string.send_to_forbidden, Snackbar.LENGTH_LONG).show()
                }
            } else if (forwardMessage) {
                if (hasChatPermission && !isReadOnlyConversation(selectedConversation!!)) {
                    openConversation(intent.getStringExtra(KEY_FORWARD_MSG_TEXT))
                    forwardMessage = false
                } else {
                    Snackbar.make(binding.root, R.string.send_to_forbidden, Snackbar.LENGTH_LONG).show()
                }
            } else {
                openConversation()
            }
        }
    }

    private fun shouldShowLobby(conversation: Conversation): Boolean {
        val participantPermissions = ParticipantPermissions(
            currentUser!!.capabilities?.spreedCapability!!,
            conversation
        )
        return conversation.lobbyState == Conversation.LobbyState.LOBBY_STATE_MODERATORS_ONLY &&
            !conversation.canModerate(currentUser!!) &&
            !participantPermissions.canIgnoreLobby()
    }

    private fun isReadOnlyConversation(conversation: Conversation): Boolean {
        return conversation.conversationReadOnlyState ===
            Conversation.ConversationReadOnlyState.CONVERSATION_READ_ONLY
    }

    private fun handleSharedData() {
        collectDataFromIntent()
        if (textToPaste!!.isNotEmpty()) {
            openConversation(textToPaste)
        } else if (filesToShare != null && filesToShare!!.isNotEmpty()) {
            showSendFilesConfirmDialog()
        } else {
            Snackbar
                .make(binding.root, context.resources.getString(R.string.nc_common_error_sorry), Snackbar.LENGTH_LONG)
                .show()
        }
    }

    private fun showSendFilesConfirmDialog() {
        if (platformPermissionUtil.isFilesPermissionGranted()) {
            val fileNamesWithLineBreaks = StringBuilder("\n")
            for (file in filesToShare!!) {
                val filename = FileUtils.getFileName(Uri.parse(file), context)
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
            binding.floatingActionButton.let {
                val dialogBuilder = MaterialAlertDialogBuilder(it.context)
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
                    .colorMaterialAlertDialogBackground(it.context, dialogBuilder)
                val dialog = dialogBuilder.show()
                viewThemeUtils.platform.colorTextButtons(
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE),
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                )
            }
        } else {
            UploadAndShareFilesWorker.requestStoragePermission(this)
        }
    }

    private fun clearIntentAction() {
        intent.action = ""
    }

    override fun onItemLongClick(position: Int) {
        if (showShareToScreen) {
            Log.d(TAG, "sharing to multiple rooms not yet implemented. onItemLongClick is ignored.")
        } else {
            val clickedItem: Any? = adapter!!.getItem(position)
            if (clickedItem != null && clickedItem is ConversationItem) {
                val conversation = clickedItem.model
                conversationsListBottomDialog = ConversationsListBottomDialog(
                    this,
                    userManager.currentUser.blockingGet(),
                    conversation
                )
                conversationsListBottomDialog!!.show()
            }
        }
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    private fun collectDataFromIntent() {
        filesToShare = ArrayList()
        if (intent != null) {
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
                        Snackbar.make(
                            binding.root,
                            context.resources.getString(R.string.nc_common_error_sorry),
                            Snackbar.LENGTH_LONG
                        ).show()
                        Log.e(TAG, "failed to get data from intent")
                    }
                } catch (e: Exception) {
                    Snackbar.make(
                        binding.root,
                        context.resources.getString(R.string.nc_common_error_sorry),
                        Snackbar.LENGTH_LONG
                    ).show()
                    Log.e(TAG, "Something went wrong when extracting data from intent")
                }
            }
        }
    }

    private fun upload() {
        if (selectedConversation == null) {
            Snackbar.make(
                binding.root,
                context.resources.getString(R.string.nc_common_error_sorry),
                Snackbar.LENGTH_LONG
            ).show()
            Log.e(TAG, "not able to upload any files because conversation was null.")
            return
        }
        try {
            filesToShare?.forEach {
                UploadAndShareFilesWorker.upload(
                    it,
                    selectedConversation!!.token!!,
                    selectedConversation!!.displayName!!,
                    null
                )
            }
        } catch (e: IllegalArgumentException) {
            Snackbar.make(binding.root, context.resources.getString(R.string.nc_upload_failed), Snackbar.LENGTH_LONG)
                .show()
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
                    Snackbar.make(
                        binding.root,
                        context.getString(R.string.read_storage_no_permission),
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }

            REQUEST_POST_NOTIFICATIONS_PERMISSION -> {
                // whenever user allowed notifications, also check to ignore battery optimization
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
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

    private fun openConversation(textToPaste: String? = "") {
        if (CallActivity.active &&
            selectedConversation!!.token != ApplicationWideCurrentRoomHolder.getInstance().currentRoomToken
        ) {
            Snackbar.make(
                binding.root,
                context.getString(R.string.restrict_join_other_room_while_call),
                Snackbar.LENGTH_LONG
            ).show()
            return
        }

        val bundle = Bundle()
        bundle.putString(KEY_ROOM_TOKEN, selectedConversation!!.token)
        bundle.putString(KEY_ROOM_ID, selectedConversation!!.roomId)
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
                EventStatus.EventType.CONVERSATION_UPDATE -> if (eventStatus.isAllGood && !isRefreshing) {
                    fetchRooms()
                }

                else -> {}
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(conversationsListFetchDataEvent: ConversationsListFetchDataEvent?) {
        fetchRooms()
        Handler().postDelayed({
            if (conversationsListBottomDialog!!.isShowing) {
                conversationsListBottomDialog!!.dismiss()
            }
        }, BOTTOM_SHEET_DELAY)
    }

    fun showDeleteConversationDialog(conversation: Conversation) {
        binding.floatingActionButton.let {
            val dialogBuilder = MaterialAlertDialogBuilder(it.context)
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
                .colorMaterialAlertDialogBackground(it.context, dialogBuilder)
            val dialog = dialogBuilder.show()
            viewThemeUtils.platform.colorTextButtons(
                dialog.getButton(AlertDialog.BUTTON_POSITIVE),
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            )
        }
    }

    private fun showUnauthorizedDialog() {
        binding.floatingActionButton.let {
            val dialogBuilder = MaterialAlertDialogBuilder(it.context)
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
                    val intent = Intent(context, WebViewLoginActivity::class.java)
                    val bundle = Bundle()
                    bundle.putString(BundleKeys.KEY_BASE_URL, currentUser!!.baseUrl!!)
                    bundle.putBoolean(BundleKeys.KEY_REAUTHORIZE_ACCOUNT, true)
                    intent.putExtras(bundle)
                    startActivity(intent)
                }

            viewThemeUtils.dialog.colorMaterialAlertDialogBackground(it.context, dialogBuilder)
            val dialog = dialogBuilder.show()
            viewThemeUtils.platform.colorTextButtons(
                dialog.getButton(AlertDialog.BUTTON_POSITIVE),
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            )
        }
    }

    @SuppressLint("CheckResult")
    private fun deleteUserAndRestartApp() {
        userManager.scheduleUserForDeletionWithId(currentUser!!.id!!).blockingGet()
        val accountRemovalWork = OneTimeWorkRequest.Builder(AccountRemovalWorker::class.java).build()
        WorkManager.getInstance(applicationContext).enqueue(accountRemovalWork)

        WorkManager.getInstance(context).getWorkInfoByIdLiveData(accountRemovalWork.id)
            .observeForever { workInfo: WorkInfo ->

                when (workInfo.state) {
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
        binding.floatingActionButton.let {
            val dialogBuilder = MaterialAlertDialogBuilder(it.context)
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
                            Intent(Intent.ACTION_VIEW, Uri.parse(CLIENT_UPGRADE_MARKET_LINK + packageName))
                        )
                    } catch (e: ActivityNotFoundException) {
                        startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(CLIENT_UPGRADE_GPLAY_LINK + packageName))
                        )
                    }
                }

            if (resources!!.getBoolean(R.bool.multiaccount_support) && userManager.users.blockingGet().size > 1) {
                dialogBuilder.setNegativeButton(R.string.nc_switch_account) { _, _ ->
                    val newFragment: DialogFragment = ChooseAccountDialogFragment.newInstance()
                    newFragment.show(supportFragmentManager, ChooseAccountDialogFragment.TAG)
                }
            }

            if (resources!!.getBoolean(R.bool.multiaccount_support)) {
                dialogBuilder.setNeutralButton(R.string.nc_account_chooser_add_account) { _, _ ->
                    val intent = Intent(this, ServerSelectionActivity::class.java)
                    intent.putExtra(ADD_ADDITIONAL_ACCOUNT, true)
                    startActivity(intent)
                }
            }

            viewThemeUtils.dialog.colorMaterialAlertDialogBackground(it.context, dialogBuilder)
            val dialog = dialogBuilder.show()
            viewThemeUtils.platform.colorTextButtons(
                dialog.getButton(AlertDialog.BUTTON_POSITIVE),
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE),
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            )
        }
    }

    private fun showServiceUnavailableDialog(httpException: HttpException) {
        if (httpException.response()?.headers()?.get(MAINTENANCE_MODE_HEADER_KEY) == "1") {
            binding.floatingActionButton.let {
                val dialogBuilder = MaterialAlertDialogBuilder(it.context)
                    .setIcon(
                        viewThemeUtils.dialog.colorMaterialAlertDialogIcon(
                            context,
                            R.drawable.ic_info_white_24dp
                        )
                    )
                    .setTitle(R.string.nc_dialog_maintenance_mode)
                    .setMessage(R.string.nc_dialog_maintenance_mode_description)
                    .setCancelable(false)
                    .setNegativeButton(R.string.nc_settings_remove_account) { _, _ ->
                        deleteUserAndRestartApp()
                    }

                if (resources!!.getBoolean(R.bool.multiaccount_support) && userManager.users.blockingGet().size > 1) {
                    dialogBuilder.setPositiveButton(R.string.nc_switch_account) { _, _ ->
                        val newFragment: DialogFragment = ChooseAccountDialogFragment.newInstance()
                        newFragment.show(supportFragmentManager, ChooseAccountDialogFragment.TAG)
                    }
                }

                if (resources!!.getBoolean(R.bool.multiaccount_support)) {
                    dialogBuilder.setNeutralButton(R.string.nc_account_chooser_add_account) { _, _ ->
                        val intent = Intent(this, ServerSelectionActivity::class.java)
                        intent.putExtra(ADD_ADDITIONAL_ACCOUNT, true)
                        startActivity(intent)
                    }
                }

                viewThemeUtils.dialog.colorMaterialAlertDialogBackground(it.context, dialogBuilder)
                val dialog = dialogBuilder.show()
                viewThemeUtils.platform.colorTextButtons(
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE),
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE),
                    dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                )
            }
        } else {
            showErrorDialog()
        }
    }

    private fun showServerEOLDialog() {
        binding.floatingActionButton.let {
            val dialogBuilder = MaterialAlertDialogBuilder(it.context)
                .setIcon(viewThemeUtils.dialog.colorMaterialAlertDialogIcon(context, R.drawable.ic_warning_white))
                .setTitle(R.string.nc_settings_server_eol_title)
                .setMessage(R.string.nc_settings_server_eol)
                .setCancelable(false)
                .setPositiveButton(R.string.nc_settings_remove_account) { _, _ ->
                    deleteUserAndRestartApp()
                }

            if (resources!!.getBoolean(R.bool.multiaccount_support) && userManager.users.blockingGet().size > 1) {
                dialogBuilder.setNegativeButton(R.string.nc_switch_account) { _, _ ->
                    val newFragment: DialogFragment = ChooseAccountDialogFragment.newInstance()
                    newFragment.show(supportFragmentManager, ChooseAccountDialogFragment.TAG)
                }
            }

            if (resources!!.getBoolean(R.bool.multiaccount_support)) {
                dialogBuilder.setNeutralButton(R.string.nc_account_chooser_add_account) { _, _ ->
                    val intent = Intent(this, ServerSelectionActivity::class.java)
                    intent.putExtra(ADD_ADDITIONAL_ACCOUNT, true)
                    startActivity(intent)
                }
            }

            viewThemeUtils.dialog.colorMaterialAlertDialogBackground(it.context, dialogBuilder)
            val dialog = dialogBuilder.show()
            viewThemeUtils.platform.colorTextButtons(
                dialog.getButton(AlertDialog.BUTTON_POSITIVE),
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE),
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            )
        }
    }

    private fun deleteConversation(conversation: Conversation) {
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

    private fun onMessageSearchResult(results: MessageSearchResults) {
        if (searchView!!.query.isNotEmpty()) {
            clearMessageSearchResults()
            val entries = results.messages
            if (entries.isNotEmpty()) {
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
                // add unified search result at the end of the list
                adapter!!.addItems(adapter!!.mainItemCount + adapter!!.scrollableHeaders.size, adapterItems)
                binding?.recyclerView?.scrollToPosition(0)
            }
        }
        binding?.swipeRefreshLayoutView?.isRefreshing = false
    }

    private fun onMessageSearchError(throwable: Throwable) {
        handleHttpExceptions(throwable)
        binding?.swipeRefreshLayoutView?.isRefreshing = false
        showErrorDialog()
    }

    fun updateFilterState(mention: Boolean, unread: Boolean) {
        filterState[FilterConversationFragment.MENTION] = mention
        filterState[FilterConversationFragment.UNREAD] = unread
    }

    fun setFilterableItems(items: MutableList<AbstractFlexibleItem<*>>) {
        filterableConversationItems = items
    }

    fun updateFilterConversationButtonColor() {
        if (filterState.containsValue(true)) {
            binding.filterConversationsButton.let { viewThemeUtils.platform.colorImageView(it, ColorRole.PRIMARY) }
        } else {
            binding.filterConversationsButton.let {
                viewThemeUtils.platform.colorImageView(
                    it,
                    ColorRole.ON_SURFACE_VARIANT
                )
            }
        }
    }

    companion object {
        private val TAG = ConversationsListActivity::class.java.simpleName
        const val UNREAD_BUBBLE_DELAY = 2500
        const val BOTTOM_SHEET_DELAY: Long = 2500
        private const val KEY_SEARCH_QUERY = "ConversationsListActivity.searchQuery"
        const val SEARCH_DEBOUNCE_INTERVAL_MS = 300
        const val SEARCH_MIN_CHARS = 2
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_CLIENT_UPGRADE_REQUIRED = 426
        const val CLIENT_UPGRADE_MARKET_LINK = "market://details?id="
        const val CLIENT_UPGRADE_GPLAY_LINK = "https://play.google.com/store/apps/details?id="
        const val HTTP_SERVICE_UNAVAILABLE = 503
        const val MAINTENANCE_MODE_HEADER_KEY = "X-Nextcloud-Maintenance-Mode"
        const val REQUEST_POST_NOTIFICATIONS_PERMISSION = 111
        const val BADGE_OFFSET = 35
    }
}
