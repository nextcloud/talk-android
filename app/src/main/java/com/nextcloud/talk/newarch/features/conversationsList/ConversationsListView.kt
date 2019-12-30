/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2019 Mario Danic <mario@lovelyhq.com>
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

package com.nextcloud.talk.newarch.features.conversationsList

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.*
import android.view.inputmethod.EditorInfo
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.SearchView.OnQueryTextListener
import androidx.core.view.MenuItemCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import butterknife.OnClick
import coil.ImageLoader
import coil.target.Target
import coil.transform.CircleCropTransformation
import com.afollestad.materialdialogs.LayoutMode.WRAP_CONTENT
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler
import com.bluelinelabs.conductor.changehandler.TransitionChangeHandlerCompat
import com.bluelinelabs.conductor.changehandler.VerticalChangeHandler
import com.nextcloud.talk.R
import com.nextcloud.talk.R.drawable
import com.nextcloud.talk.adapters.items.ConversationItem
import com.nextcloud.talk.controllers.ContactsController
import com.nextcloud.talk.controllers.SettingsController
import com.nextcloud.talk.controllers.bottomsheet.items.BasicListItemWithImage
import com.nextcloud.talk.controllers.bottomsheet.items.listItemsWithImage
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.newarch.conversationsList.mvp.BaseView
import com.nextcloud.talk.newarch.features.conversationsList.ConversationsListViewState.*
import com.nextcloud.talk.newarch.mvvm.ext.initRecyclerView
import com.nextcloud.talk.newarch.utils.Images
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.ConductorRemapping
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.ShareUtils
import com.nextcloud.talk.utils.animations.SharedElementTransition
import com.nextcloud.talk.utils.bundle.BundleKeys
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.FlexibleAdapter.OnItemClickListener
import eu.davidea.flexibleadapter.FlexibleAdapter.OnItemLongClickListener
import eu.davidea.flexibleadapter.common.SmoothScrollLinearLayoutManager
import eu.davidea.flexibleadapter.items.IFlexible
import kotlinx.android.synthetic.main.controller_conversations_rv.view.*
import kotlinx.android.synthetic.main.fast_scroller.view.*
import kotlinx.android.synthetic.main.view_states.view.*
import org.koin.android.ext.android.inject
import org.parceler.Parcels
import java.util.*

class ConversationsListView : BaseView(), OnQueryTextListener,
        OnItemClickListener, OnItemLongClickListener {

    private lateinit var viewModel: ConversationsListViewModel
    val factory: ConversationListViewModelFactory by inject()
    private val imageLoader: ImageLoader by inject()
    private val viewState: MutableLiveData<ConversationsListViewState> = MutableLiveData(LOADING)

    private val recyclerViewAdapter = FlexibleAdapter(mutableListOf(), null, false)

    private var searchItem: MenuItem? = null
    private var settingsItem: MenuItem? = null
    private var searchView: SearchView? = null

    override fun onCreateOptionsMenu(
            menu: Menu,
            inflater: MenuInflater
    ) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_conversation_plus_filter, menu)
        searchItem = menu.findItem(R.id.action_search)
        initSearchView()
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        if (recyclerViewAdapter.hasFilter()) {
            searchItem?.expandActionView()
            searchView?.setQuery(viewModel.searchQuery.value, false)
            recyclerViewAdapter.filterItems()
        }

        settingsItem = menu.findItem(R.id.action_settings)
    }

    private fun loadAvatar() {
        val iconSize = settingsItem?.icon?.intrinsicHeight?.toFloat()
                ?.let {
                    DisplayUtils.convertDpToPixel(
                            it,
                            activity!!
                    )
                            .toInt()
                }

        iconSize?.let {
            val target = object : Target {
                override fun onSuccess(result: Drawable) {
                    super.onSuccess(result)
                    settingsItem?.icon = result
                }

                override fun onError(error: Drawable?) {
                    super.onError(error)
                    settingsItem?.icon = context.getDrawable(R.drawable.ic_settings_white_24dp)
                }
            }

            viewModel.currentUserLiveData.value?.let {
                val avatarRequest = Images().getRequestForUrl(
                        imageLoader, context, ApiUtils.getUrlForAvatarWithNameAndPixels(
                        it.baseUrl,
                        it.userId, iconSize
                ), it, target, this, CircleCropTransformation()
                )

                imageLoader.load(avatarRequest)
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_settings -> {
                val names = ArrayList<String>()
                names.add("userAvatar.transitionTag")
                router.pushController(
                        RouterTransaction.with(SettingsController())
                                .pushChangeHandler(
                                        TransitionChangeHandlerCompat(
                                                SharedElementTransition(names), VerticalChangeHandler()
                                        )
                                )
                                .popChangeHandler(
                                        TransitionChangeHandlerCompat(
                                                SharedElementTransition(names), VerticalChangeHandler()
                                        )
                                )
                )
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    private fun initSearchView() {
        val searchManager = activity!!.getSystemService(Context.SEARCH_SERVICE) as SearchManager
        searchView = MenuItemCompat.getActionView(searchItem) as SearchView
        searchView!!.maxWidth = Integer.MAX_VALUE
        searchView!!.inputType = InputType.TYPE_TEXT_VARIATION_FILTER
        var imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_FULLSCREEN
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appPreferences.isKeyboardIncognito) {
            imeOptions = imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        }
        searchView!!.imeOptions = imeOptions
        searchView!!.queryHint = resources?.getString(R.string.nc_search)
        searchView!!.setSearchableInfo(searchManager.getSearchableInfo(activity!!.componentName))
        searchView!!.setOnQueryTextListener(this)
    }

    override fun onRestoreViewState(view: View, savedViewState: Bundle) {
        super.onRestoreViewState(view, savedViewState)
        viewModel.loadConversations()
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        if (!viewModel.searchQuery.value.equals(query)) {
            viewModel.searchQuery.value = query
        }

        return true
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        return onQueryTextSubmit(newText)
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup
    ): View {
        setHasOptionsMenu(true)
        actionBar?.show()

        viewModel = viewModelProvider(factory).get(ConversationsListViewModel::class.java)
        viewModel.apply {
            currentUserLiveData.observe(this@ConversationsListView, Observer { value ->
                loadAvatar()
            })

            conversationsLiveData.observe(this@ConversationsListView, Observer {
                if (it.isEmpty()) {
                    if (viewState.value != LOADED_EMPTY) {
                        viewState.value = LOADED_EMPTY
                    }
                } else {
                    if (viewState.value != LOADED) {
                        viewState.value = LOADED
                    }
                }

                val newConversations = mutableListOf<ConversationItem>()
                for (conversation in it) {
                    newConversations.add(
                            ConversationItem(
                                    conversation, viewModel.currentUserLiveData.value!!,
                                    activity!!
                            )
                    )
                }

                recyclerViewAdapter.updateDataSet(
                        newConversations as
                                List<IFlexible<ViewHolder>>?, false
                )
            })

            searchQuery.observe(this@ConversationsListView, Observer {
                recyclerViewAdapter.setFilter(it)
                recyclerViewAdapter.filterItems(500)
            })
        }

        viewState.observe(this@ConversationsListView, Observer { value ->
            when (value) {
                LOADING -> {
                    view?.swipeRefreshLayoutView?.isEnabled = false
                    view?.loadingStateView?.visibility = View.VISIBLE
                    view?.stateWithMessageView?.visibility = View.GONE
                    view?.dataStateView?.visibility = View.GONE
                    view?.floatingActionButton?.visibility = View.GONE
                    searchItem?.isVisible = false
                }
                LOADED -> {
                    view?.swipeRefreshLayoutView?.isEnabled = true
                    view?.swipeRefreshLayoutView?.post {
                        view?.swipeRefreshLayoutView?.isRefreshing = false
                    }
                    view?.loadingStateView?.visibility = View.GONE
                    view?.stateWithMessageView?.visibility = View.GONE
                    view?.dataStateView?.visibility = View.VISIBLE
                    view?.floatingActionButton?.visibility = View.VISIBLE
                    searchItem?.isVisible = true
                }
                LOADED_EMPTY, FAILED -> {
                    view?.swipeRefreshLayoutView?.post {
                        view?.swipeRefreshLayoutView?.isRefreshing = false
                    }
                    view?.swipeRefreshLayoutView?.isEnabled = true
                    view?.loadingStateView?.visibility = View.GONE
                    view?.dataStateView?.visibility = View.GONE
                    searchItem?.isVisible = false

                    if (value.equals(FAILED)) {
                        view?.stateWithMessageView?.errorStateTextView?.text = viewModel.messageData
                        if (viewModel.messageData.equals(
                                        context.resources.getString(R.string.nc_no_connection_error)
                                )
                        ) {
                            view?.stateWithMessageView?.errorStateImageView?.setImageResource(
                                    drawable.ic_signal_wifi_off_white_24dp
                            )
                        } else {
                            view?.stateWithMessageView?.errorStateImageView?.setImageResource(
                                    drawable.ic_announcement_white_24dp
                            )
                        }
                        view?.floatingActionButton?.visibility = View.GONE
                    } else {
                        view?.floatingActionButton?.visibility = View.VISIBLE
                        view?.stateWithMessageView?.errorStateTextView?.text =
                                resources?.getText(R.string.nc_conversations_empty)
                        view?.stateWithMessageView?.errorStateImageView?.setImageResource(drawable.ic_logo)
                    }

                    view?.stateWithMessageView?.visibility = View.VISIBLE
                }
                else -> {
                    // We should not be here
                }
            }
        })


        return super.onCreateView(inflater, container)
    }

    override fun getLayoutId(): Int {
        return R.layout.controller_conversations_rv
    }

    @OnClick(R.id.floatingActionButton)
    fun onFloatingActionButtonClick() {
        openNewConversationScreen()
    }

    @OnClick(R.id.stateWithMessageView)
    fun onStateWithMessageViewClick() {
        if (viewState.value!! == LOADED_EMPTY) {
            openNewConversationScreen()
        }
    }

    private fun openNewConversationScreen() {
        val bundle = Bundle()
        bundle.putBoolean(BundleKeys.KEY_NEW_CONVERSATION, true)
        router.pushController(
                RouterTransaction.with(ContactsController(bundle))
                        .pushChangeHandler(HorizontalChangeHandler())
                        .popChangeHandler(HorizontalChangeHandler())
        )
    }

    private fun getShareIntentForConversation(conversation: Conversation): Intent {
        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(
                    Intent.EXTRA_SUBJECT,
                    String.format(
                            context.getString(R.string.nc_share_subject),
                            context.getString(R.string.nc_app_name)
                    )
            )

            // TODO, make sure we ask for password if needed
            putExtra(
                    Intent.EXTRA_TEXT, ShareUtils.getStringForIntent(
                    context, null, conversation
            )
            )

            type = "text/plain"
        }

        // TODO filter our own app once we're there
        return Intent.createChooser(sendIntent, context.getString(R.string.nc_share_link))
    }

    private fun getConversationMenuItemsForConversation(conversation: Conversation): MutableList<BasicListItemWithImage> {
        val items = mutableListOf<BasicListItemWithImage>()

        if (conversation.favorite) {
            items.add(
                    BasicListItemWithImage(
                            drawable.ic_star_border_black_24dp,
                            context.getString(R.string.nc_remove_from_favorites)
                    )
            )
        } else {
            items.add(
                    BasicListItemWithImage(
                            drawable.ic_star_black_24dp,
                            context.getString(R.string.nc_add_to_favorites)
                    )
            )
        }

        if (conversation.isPublic) {
            items.add(
                    (BasicListItemWithImage(
                            drawable
                                    .ic_share_black_24dp, context.getString(R.string.nc_share_link)
                    ))
            )
        }

        if (conversation.canLeave(viewModel.currentUserLiveData.value!!)) {
            items.add(
                    BasicListItemWithImage(
                            drawable.ic_exit_to_app_black_24dp, context.getString
                    (R.string.nc_leave)
                    )
            )
        }

        if (conversation.canModerate(viewModel.currentUserLiveData.value!!)) {
            items.add(
                    BasicListItemWithImage(
                            drawable.ic_delete_grey600_24dp, context.getString(
                            R.string.nc_delete_call
                    )
                    )
            )
        }

        return items
    }

    override fun getTitle(): String? {
        return resources?.getString(R.string.nc_app_name)
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        view.recyclerView.initRecyclerView(
                SmoothScrollLinearLayoutManager(view.context), recyclerViewAdapter, false
        )

        recyclerViewAdapter.fastScroller = view.fast_scroller
        recyclerViewAdapter.mItemClickListener = this
        recyclerViewAdapter.mItemLongClickListener = this

        view.swipeRefreshLayoutView.setOnRefreshListener {
            view.swipeRefreshLayoutView.isRefreshing = false
            viewModel.loadConversations()
        }

        view.swipeRefreshLayoutView.setColorSchemeResources(R.color.colorPrimary)

        view.fast_scroller.setBubbleTextCreator { position ->
            var displayName =
                    (recyclerViewAdapter.getItem(position) as ConversationItem).model.displayName

            if (displayName!!.length > 8) {
                displayName = displayName.substring(0, 4) + "..."
            }

            displayName
        }
    }

    override fun onItemLongClick(position: Int) {
        val clickedItem = recyclerViewAdapter.getItem(position)
        clickedItem?.let {
            val conversation = (it as ConversationItem).model

            activity?.let { activity ->
                MaterialDialog(activity, BottomSheet(WRAP_CONTENT)).show {
                    cornerRadius(res = R.dimen.corner_radius)
                    title(text = conversation.displayName)
                    listItemsWithImage(getConversationMenuItemsForConversation(conversation)
                    ) { dialog,
                        index, item ->

                        when (item.iconRes) {
                            drawable.ic_star_border_black_24dp -> {
                                viewModel.changeFavoriteValueForConversation(conversation, false)

                            }
                            drawable.ic_star_black_24dp -> {
                                viewModel.changeFavoriteValueForConversation(conversation, true)
                            }
                            drawable.ic_share_black_24dp -> {
                                startActivity(getShareIntentForConversation(conversation))
                            }
                            drawable.ic_exit_to_app_black_24dp -> {
                                MaterialDialog(activity).show {
                                    title(R.string.nc_leave)
                                    message(R.string.nc_leave_message)
                                    positiveButton(R.string.nc_simple_leave) { dialog ->
                                        viewModel.leaveConversation(conversation)
                                    }
                                    negativeButton(R.string.nc_cancel)
                                    icon(drawable.ic_exit_to_app_black_24dp)
                                }
                            }
                            drawable.ic_delete_grey600_24dp -> {
                                MaterialDialog(activity).show {
                                    title(R.string.nc_delete)
                                    message(text = conversation.deleteWarningMessage)
                                    positiveButton(R.string.nc_delete_call) { dialog ->
                                        viewModel.deleteConversation(conversation)
                                    }
                                    negativeButton(R.string.nc_cancel)
                                    icon(
                                            drawable = DisplayUtils.getTintedDrawable(
                                                    resources!!, drawable
                                                    .ic_delete_grey600_24dp, R.color.nc_darkRed
                                            )
                                    )
                                }
                            }
                            else -> {

                            }
                        }
                    }
                }
            }
        }
    }

    override fun onItemClick(
            view: View?,
            position: Int
    ): Boolean {
        val clickedItem = recyclerViewAdapter.getItem(position)
        if (clickedItem != null) {
            val conversation = (clickedItem as ConversationItem).model

            val bundle = Bundle()
            bundle.putParcelable(BundleKeys.KEY_USER_ENTITY, viewModel.currentUserLiveData.value)
            bundle.putString(BundleKeys.KEY_ROOM_TOKEN, conversation.token)
            bundle.putString(BundleKeys.KEY_ROOM_ID, conversation.conversationId)
            bundle.putParcelable(BundleKeys.KEY_ACTIVE_CONVERSATION, Parcels.wrap(conversation))
            ConductorRemapping.remapChatController(
                    router, viewModel.currentUserLiveData.value!!.id!!, conversation.token!!,
                    bundle, false
            )
        }

        return true
    }
}