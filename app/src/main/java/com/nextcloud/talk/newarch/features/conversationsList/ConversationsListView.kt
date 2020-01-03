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

import android.os.Bundle
import android.view.*
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.observe
import butterknife.OnClick
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.autodispose.ControllerScopeProvider
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler
import com.bluelinelabs.conductor.changehandler.TransitionChangeHandlerCompat
import com.bluelinelabs.conductor.changehandler.VerticalChangeHandler
import com.nextcloud.talk.R
import com.nextcloud.talk.R.drawable
import com.nextcloud.talk.adapters.ConversationsPresenter
import com.nextcloud.talk.controllers.ContactsController
import com.nextcloud.talk.controllers.SettingsController
import com.nextcloud.talk.controllers.bottomsheet.items.BasicListItemWithImage
import com.nextcloud.talk.controllers.bottomsheet.items.listItemsWithImage
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.newarch.conversationsList.mvp.BaseView
import com.nextcloud.talk.newarch.mvvm.ext.initRecyclerView
import com.nextcloud.talk.utils.ConductorRemapping
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.animations.SharedElementTransition
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.otaliastudios.elements.*
import com.uber.autodispose.lifecycle.LifecycleScopeProvider
import eu.davidea.flexibleadapter.common.SmoothScrollLinearLayoutManager
import kotlinx.android.synthetic.main.controller_conversations_rv.view.*
import kotlinx.android.synthetic.main.message_state.view.*
import org.koin.android.ext.android.inject
import org.parceler.Parcels
import java.util.*

class ConversationsListView : BaseView() {

    override val scopeProvider: LifecycleScopeProvider<*> = ControllerScopeProvider.from(this)

    private lateinit var viewModel: ConversationsListViewModel
    val factory: ConversationListViewModelFactory by inject()

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
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        settingsItem = menu.findItem(R.id.action_settings)
        settingsItem?.actionView?.transitionName = "userAvatar.transitionTag"
        viewModel.loadAvatar()
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

    /*private fun initSearchView() {
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
    }*/

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup
    ): View {
        setHasOptionsMenu(true)
        actionBar?.show()

        viewModel = viewModelProvider(factory).get(ConversationsListViewModel::class.java)
        val view = super.onCreateView(inflater, container)

        val adapter = Adapter.builder(this)
                .addSource(Source.fromLiveData(viewModel.conversationsLiveData))
                .addPresenter(ConversationsPresenter(context, ::onElementClick, ::onElementLongClick))
                .addPresenter(Presenter.forLoadingIndicator(context, R.layout.loading_state))
                .addPresenter(Presenter.forEmptyIndicator(context, R.layout.message_state))
                .addPresenter(Presenter.forErrorIndicator(context, R.layout.message_state) { view, throwable ->
                    view.messageStateTextView.setText(R.string.nc_oops)
                    view.messageStateImageView.setImageDrawable(context.getDrawable(drawable.ic_announcement_white_24dp))
                })
                .into(view.recyclerView)

        view.apply {
            recyclerView.initRecyclerView(SmoothScrollLinearLayoutManager(activity), adapter, false)

            swipeRefreshLayoutView.setOnRefreshListener {
                view.swipeRefreshLayoutView.isRefreshing = false
                viewModel.loadConversations()
            }

            swipeRefreshLayoutView.setColorSchemeResources(R.color.colorPrimary)
        }

        viewModel.avatar.observe(this@ConversationsListView) { avatar ->
            settingsItem?.icon = avatar
        }

        return view
    }

    private fun onElementClick(page: Page, holder: Presenter.Holder, element: Element<Conversation>) {
        val conversation = element.data
        val user = viewModel.globalService.currentUserLiveData.value

        user?.let { user ->
            conversation?.let { conversation ->
                val bundle = Bundle()
                bundle.putParcelable(BundleKeys.KEY_USER_ENTITY, user)
                bundle.putString(BundleKeys.KEY_ROOM_TOKEN, conversation.token)
                bundle.putString(BundleKeys.KEY_ROOM_ID, conversation.conversationId)
                bundle.putParcelable(BundleKeys.KEY_ACTIVE_CONVERSATION, Parcels.wrap(conversation))
                ConductorRemapping.remapChatController(
                        router, user.id!!, conversation.token!!,
                        bundle, false
                )
            }

        }
    }

    private fun onElementLongClick(page: Page, holder: Presenter.Holder, element: Element<Conversation>) {
        val conversation = element.data

        conversation?.let { conversation ->
            activity?.let { activity ->
                MaterialDialog(activity, BottomSheet(LayoutMode.WRAP_CONTENT)).show {
                    cornerRadius(res = R.dimen.corner_radius)
                    title(text = conversation.displayName)

                    listItemsWithImage(getConversationMenuItemsForConversation(conversation)) { dialog, index, item ->
                        when (item.iconRes) {
                            drawable.ic_star_border_black_24dp -> {
                                viewModel.changeFavoriteValueForConversation(conversation, false)

                            }
                            drawable.ic_star_black_24dp -> {
                                viewModel.changeFavoriteValueForConversation(conversation, true)
                            }
                            drawable.ic_exit_to_app_black_24dp -> {
                                MaterialDialog(activity).show {
                                    title(R.string.nc_leave)
                                    message(R.string.nc_leave_message)
                                    positiveButton(R.string.nc_simple_leave) {
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

    override fun getLayoutId(): Int {
        return R.layout.controller_conversations_rv
    }

    @OnClick(R.id.floatingActionButton)
    fun onFloatingActionButtonClick() {
        val bundle = Bundle()
        bundle.putBoolean(BundleKeys.KEY_NEW_CONVERSATION, true)
        router.pushController(
                RouterTransaction.with(ContactsController(bundle))
                        .pushChangeHandler(HorizontalChangeHandler())
                        .popChangeHandler(HorizontalChangeHandler())
        )
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

        if (conversation.canLeave(viewModel.globalService.currentUserLiveData.value!!)) {
            items.add(
                    BasicListItemWithImage(
                            drawable.ic_exit_to_app_black_24dp, context.getString
                    (R.string.nc_leave)
                    )
            )
        }

        if (conversation.canModerate(viewModel.globalService.currentUserLiveData.value!!)) {
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

    override fun onRestoreViewState(view: View, savedViewState: Bundle) {
        super.onRestoreViewState(view, savedViewState)
        viewModel.loadConversations()
    }
}