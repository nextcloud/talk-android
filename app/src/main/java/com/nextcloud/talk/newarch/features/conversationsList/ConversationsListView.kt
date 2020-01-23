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

package com.nextcloud.talk.newarch.features.conversationslist

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.observe
import androidx.recyclerview.widget.LinearLayoutManager
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.bluelinelabs.conductor.ControllerChangeHandler
import com.bluelinelabs.conductor.ControllerChangeType
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.autodispose.ControllerScopeProvider
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler
import com.bluelinelabs.conductor.changehandler.TransitionChangeHandlerCompat
import com.bluelinelabs.conductor.changehandler.VerticalChangeHandler
import com.nextcloud.talk.R
import com.nextcloud.talk.R.drawable
import com.nextcloud.talk.controllers.ContactsController
import com.nextcloud.talk.controllers.SettingsController
import com.nextcloud.talk.controllers.bottomsheet.items.BasicListItemWithImage
import com.nextcloud.talk.controllers.bottomsheet.items.listItemsWithImage
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.newarch.mvvm.BaseView
import com.nextcloud.talk.newarch.data.presenters.AdvancedEmptyPresenter
import com.nextcloud.talk.newarch.features.search.DebouncingTextWatcher
import com.nextcloud.talk.newarch.mvvm.ext.initRecyclerView
import com.nextcloud.talk.utils.ConductorRemapping
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.animations.SharedElementTransition
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.otaliastudios.elements.Adapter
import com.otaliastudios.elements.Element
import com.otaliastudios.elements.Page
import com.otaliastudios.elements.Presenter
import com.uber.autodispose.lifecycle.LifecycleScopeProvider
import kotlinx.android.synthetic.main.conversations_list_view.view.*
import kotlinx.android.synthetic.main.message_state.view.*
import kotlinx.android.synthetic.main.search_layout.*
import org.koin.android.ext.android.inject
import org.parceler.Parcels

class ConversationsListView : BaseView() {

    override val scopeProvider: LifecycleScopeProvider<*> = ControllerScopeProvider.from(this)

    private lateinit var viewModel: ConversationsListViewModel
    val factory: ConversationListViewModelFactory by inject()

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup
    ): View {
        actionBar?.show()

        viewModel = viewModelProvider(factory).get(ConversationsListViewModel::class.java)
        val view = super.onCreateView(inflater, container)

        val adapter = Adapter.builder(this)
                .addSource(ConversationsListSource(viewModel.conversationsLiveData))
                .addPresenter(ConversationPresenter(activity as Context, ::onElementClick, ::onElementLongClick))
                .addPresenter(Presenter.forLoadingIndicator(activity as Context, R.layout.loading_state))
                .addPresenter(AdvancedEmptyPresenter(activity as Context, R.layout.message_state, ::openNewConversationScreen))
                .addPresenter(Presenter.forErrorIndicator(activity as Context, R.layout.message_state) { view, throwable ->
                    view.messageStateTextView.setText(R.string.nc_oops)
                    view.messageStateImageView.setImageDrawable((activity as Context).getDrawable(drawable.ic_announcement_white_24dp))
                })
                .setAutoScrollMode(Adapter.AUTOSCROLL_POSITION_0, true)
                .into(view.recyclerView)


        view.apply {
            recyclerView.initRecyclerView(LinearLayoutManager(activity), adapter, true)
            swipeRefreshLayoutView.setOnRefreshListener {
                view.swipeRefreshLayoutView.isRefreshing = false
                viewModel.loadConversations()
            }

            swipeRefreshLayoutView.setColorSchemeResources(R.color.colorPrimary)
        }

        activity?.inputEditText?.addTextChangedListener(DebouncingTextWatcher(lifecycle, ::setSearchQuery))
        activity?.clearButton?.setOnClickListener {
            activity?.inputEditText?.text = null
        }
        activity?.settingsButton?.setOnClickListener {
            val settingsTransitionName = "userAvatar.transitionTag"
            router.pushController(
                    RouterTransaction.with(SettingsController())
                            .pushChangeHandler(
                                    TransitionChangeHandlerCompat(
                                            SharedElementTransition(arrayListOf(settingsTransitionName)), VerticalChangeHandler()
                                    )
                            )
                            .popChangeHandler(
                                    TransitionChangeHandlerCompat(
                                            SharedElementTransition(arrayListOf(settingsTransitionName)), VerticalChangeHandler()
                                    )
                            )
            )

        }

        viewModel.apply {
            avatar.observe(this@ConversationsListView) { avatar ->
                activity?.settingsButton?.imageTintList = null
                activity?.settingsButton?.setImageDrawable(avatar)
            }

            filterLiveData.observe(this@ConversationsListView) { query ->
                activity?.settingsButton?.isVisible = query.isNullOrEmpty()
                activity?.clearButton?.isVisible = !query.isNullOrEmpty()
            }
        }
        return view
    }

    override fun onChangeStarted(changeHandler: ControllerChangeHandler, changeType: ControllerChangeType) {
        actionBar?.setIcon(null)
        super.onChangeStarted(changeHandler, changeType)
    }

    private fun setSearchQuery(query: CharSequence?) {
        viewModel.filterLiveData.value = query
    }

    private fun onElementClick(page: Page, holder: Presenter.Holder, element: Element<Conversation>) {
        val conversation = element.data
        val user = viewModel.globalService.currentUserLiveData.value

        user?.let { user ->
            conversation?.let { conversation ->
                val bundle = Bundle()
                bundle.putParcelable(BundleKeys.KEY_USER_ENTITY, user)
                bundle.putString(BundleKeys.KEY_CONVERSATION_TOKEN, conversation.token)
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
        return R.layout.conversations_list_view
    }

    private fun openNewConversationScreen() {
        val bundle = Bundle()
        bundle.putBoolean(BundleKeys.KEY_NEW_CONVERSATION, true)
        router.pushController(
                RouterTransaction.with(ContactsController(bundle))
                        .pushChangeHandler(HorizontalChangeHandler())
                        .popChangeHandler(HorizontalChangeHandler()))
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

    override fun getIsUsingSearchLayout(): Boolean {
        return true
    }

    override fun getSearchHint(): String? {
        return resources?.getString(R.string.nc_search_conversations)
    }

    override fun getTitle(): String? {
        return resources?.getString(R.string.nc_app_name)
    }

    override fun onRestoreViewState(view: View, savedViewState: Bundle) {
        super.onRestoreViewState(view, savedViewState)
        viewModel.loadConversations()
    }
}