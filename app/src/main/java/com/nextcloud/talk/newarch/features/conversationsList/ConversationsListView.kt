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
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.SearchView.OnQueryTextListener
import androidx.core.view.MenuItemCompat
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import butterknife.OnClick
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler
import com.bluelinelabs.conductor.changehandler.TransitionChangeHandlerCompat
import com.bluelinelabs.conductor.changehandler.VerticalChangeHandler
import com.nextcloud.talk.R
import com.nextcloud.talk.adapters.items.ConversationItem
import com.nextcloud.talk.controllers.ContactsController
import com.nextcloud.talk.controllers.SettingsController
import com.nextcloud.talk.controllers.bottomsheet.CallMenuController.MenuType
import com.nextcloud.talk.controllers.bottomsheet.CallMenuController.MenuType.REGULAR
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.newarch.conversationsList.mvp.BaseView
import com.nextcloud.talk.newarch.mvvm.ViewState.FAILED
import com.nextcloud.talk.newarch.mvvm.ViewState.LOADED
import com.nextcloud.talk.newarch.mvvm.ViewState.LOADED_EMPTY
import com.nextcloud.talk.newarch.mvvm.ViewState.LOADING
import com.nextcloud.talk.newarch.mvvm.ext.initRecyclerView
import com.nextcloud.talk.utils.ConductorRemapping
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.animations.SharedElementTransition
import com.nextcloud.talk.utils.bundle.BundleKeys
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.FlexibleAdapter.OnItemClickListener
import eu.davidea.flexibleadapter.FlexibleAdapter.OnItemLongClickListener
import eu.davidea.flexibleadapter.common.SmoothScrollLinearLayoutManager
import eu.davidea.flexibleadapter.items.IFlexible
import kotlinx.android.synthetic.main.controller_conversations_rv.view.dataStateView
import kotlinx.android.synthetic.main.controller_conversations_rv.view.floatingActionButton
import kotlinx.android.synthetic.main.controller_conversations_rv.view.recyclerView
import kotlinx.android.synthetic.main.controller_conversations_rv.view.swipeRefreshLayoutView
import kotlinx.android.synthetic.main.fast_scroller.view.fast_scroller
import kotlinx.android.synthetic.main.view_states.view.errorStateImageView
import kotlinx.android.synthetic.main.view_states.view.errorStateTextView
import kotlinx.android.synthetic.main.view_states.view.loadingStateView
import kotlinx.android.synthetic.main.view_states.view.stateWithMessageView
import org.koin.android.ext.android.inject
import org.parceler.Parcels
import java.util.ArrayList

class ConversationsListView : BaseView(), OnQueryTextListener,
    OnItemClickListener, OnItemLongClickListener {

  lateinit var viewModel: ConversationsListViewModel
  val factory: ConversationListViewModelFactory by inject()

  private val recyclerViewAdapter = FlexibleAdapter(mutableListOf())

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
    val iconSize = settingsItem?.icon?.intrinsicHeight?.toFloat()
        ?.let {
          DisplayUtils.convertDpToPixel(
              it,
              activity
          )
              .toInt()
        }

    iconSize?.let { viewModel.loadAvatar(it) }
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

    viewModel = viewModelProvider(factory).get(ConversationsListViewModel::class.java)
    viewModel.apply {
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
            view?.floatingActionButton?.visibility = View.GONE
            searchItem?.isVisible = true
          }
          LOADED_EMPTY, FAILED -> {
            view?.swipeRefreshLayoutView?.post {
              view?.swipeRefreshLayoutView?.isRefreshing = false
            }
            view?.swipeRefreshLayoutView?.isEnabled = true
            view?.loadingStateView?.visibility = View.GONE
            view?.dataStateView?.visibility = View.GONE
            view?.floatingActionButton?.visibility = View.GONE
            searchItem?.isVisible = false

            if (value.equals(FAILED)) {
              view?.stateWithMessageView?.errorStateTextView?.text = messageData
              view?.stateWithMessageView?.errorStateImageView?.setImageResource(
                  R.drawable.ic_announcement_white_24dp
              )
            } else {
              view?.stateWithMessageView?.errorStateTextView?.text =
                resources?.getText(R.string.nc_conversations_empty)
              view?.stateWithMessageView?.errorStateImageView?.setImageResource(R.drawable.ic_logo)
            }

            view?.stateWithMessageView?.visibility = View.VISIBLE
          }
          else -> {
            // We should not be here
          }
        }
      })

      conversationsListData.observe(this@ConversationsListView, Observer {
        val newConversations = mutableListOf<ConversationItem>()
        for (conversation in it) {
          newConversations.add(ConversationItem(conversation, viewModel.currentUser, activity))
        }

        recyclerViewAdapter.updateDataSet(newConversations as List<IFlexible<ViewHolder>>?)
      })

      searchQuery.observe(this@ConversationsListView, Observer {
        recyclerViewAdapter.setFilter(it)
        recyclerViewAdapter.filterItems(500)
      })


      currentUserAvatar.observe(this@ConversationsListView, Observer {
        settingsItem?.icon = it
      })
    }

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
    if (viewModel.viewState.equals(LOADED_EMPTY)) {
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

  override fun getTitle(): String? {
    return resources?.getString(R.string.nc_app_name)
  }

  override fun onAttach(view: View) {
    super.onAttach(view)
    view.recyclerView.initRecyclerView(
        SmoothScrollLinearLayoutManager(view.context), recyclerViewAdapter
    )

    view.swipeRefreshLayoutView.setOnRefreshListener { viewModel.loadConversations() }
    view.swipeRefreshLayoutView.setColorSchemeResources(R.color.colorPrimary)

    recyclerViewAdapter.fastScroller = view.fast_scroller
    recyclerViewAdapter.mItemClickListener = this
    recyclerViewAdapter.mItemLongClickListener = this

    view.fast_scroller.setBubbleTextCreator { position ->
      var displayName =
        (recyclerViewAdapter.getItem(position) as ConversationItem).model.displayName

      if (displayName.length > 8) {
        displayName = displayName.substring(0, 4) + "..."
      }

      displayName
    }

    viewModel.loadConversations()
  }

  override fun onItemLongClick(position: Int) {
    val clickedItem = recyclerViewAdapter.getItem(position)
    if (clickedItem != null) {
      val conversation = (clickedItem as ConversationItem).model
      val bundle = Bundle()
      bundle.putParcelable(BundleKeys.KEY_ROOM, Parcels.wrap<Conversation>(conversation))
      bundle.putParcelable(BundleKeys.KEY_MENU_TYPE, Parcels.wrap<MenuType>(REGULAR))
      //prepareAndShowBottomSheetWithBundle(bundle, true)
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
      bundle.putParcelable(BundleKeys.KEY_USER_ENTITY, viewModel.currentUser)
      bundle.putString(BundleKeys.KEY_ROOM_TOKEN, conversation.token)
      bundle.putString(BundleKeys.KEY_ROOM_ID, conversation.roomId)
      bundle.putParcelable(BundleKeys.KEY_ACTIVE_CONVERSATION, Parcels.wrap(conversation))
      ConductorRemapping.remapChatController(
          router, viewModel.currentUser.id, conversation.token,
          bundle, false
      )
    }

    return true
  }
}