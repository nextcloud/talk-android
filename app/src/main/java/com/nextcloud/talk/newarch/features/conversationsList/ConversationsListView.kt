/*
 *
 *  * Nextcloud Talk application
 *  *
 *  * @author Mario Danic
 *  * Copyright (C) 2017-2020 Mario Danic <mario@lovelyhq.com>
 *  *
 *  * This program is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * at your option) any later version.
 *  *
 *  * This program is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.nextcloud.talk.newarch.features.conversationsList

import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.observe
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.archlifecycle.ControllerLifecycleOwner
import com.bluelinelabs.conductor.autodispose.ControllerScopeProvider
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler
import com.bluelinelabs.conductor.changehandler.TransitionChangeHandlerCompat
import com.bluelinelabs.conductor.changehandler.VerticalChangeHandler
import com.nextcloud.talk.R
import com.nextcloud.talk.R.drawable
import com.nextcloud.talk.controllers.SettingsController
import com.nextcloud.talk.controllers.bottomsheet.items.BasicListItemWithImage
import com.nextcloud.talk.controllers.bottomsheet.items.listItemsWithImage
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.newarch.data.presenters.AdvancedEmptyPresenter
import com.nextcloud.talk.newarch.features.contactsflow.contacts.ContactsView
import com.nextcloud.talk.newarch.features.search.DebouncingTextWatcher
import com.nextcloud.talk.newarch.local.models.toUser
import com.nextcloud.talk.newarch.mvvm.BaseView
import com.nextcloud.talk.utils.ConductorRemapping
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.animations.SharedElementTransition
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.otaliastudios.elements.Adapter
import com.otaliastudios.elements.Adapter.Companion.AUTOSCROLL_POSITION_0
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
    override val lifecycleOwner = ControllerLifecycleOwner(this)

    private lateinit var viewModel: ConversationsListViewModel
    val factory: ConversationListViewModelFactory by inject()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup
    ): View {
        viewModel = viewModelProvider(factory).get(ConversationsListViewModel::class.java)
        val view = super.onCreateView(inflater, container)

        val adapter = Adapter.builder(this)
            .addSource(ConversationsListSource(viewModel.conversationsLiveData))
            .addPresenter(ConversationPresenter(activity as Context, ::onElementClick, ::onElementLongClick))
            .addPresenter(Presenter.forLoadingIndicator(context, R.layout.loading_state))
            .addPresenter(AdvancedEmptyPresenter(context, R.layout.message_state, ::openNewConversationScreen) { view ->
                view.messageStateImageView.imageTintList = resources?.getColor(R.color.colorPrimary)?.let { ColorStateList.valueOf(it) }
            })
            .addPresenter(Presenter.forErrorIndicator(context, R.layout.message_state) { view, _ ->
                with(view) {
                    messageStateTextView.setText(R.string.nc_oops)
                    messageStateImageView.setImageDrawable(context.getDrawable(drawable.ic_announcement_white_24dp))
                    messageStateImageView.imageTintList = resources?.getColor(R.color.colorPrimary)?.let { ColorStateList.valueOf(it) }
                }
            }
            )
            .setAutoScrollMode(AUTOSCROLL_POSITION_0, true)
            .into(view.recyclerView)

        view.apply {
            recyclerView.adapter = adapter
            with(swipeRefreshLayoutView) {
                setColorSchemeResources(R.color.colorPrimary)
                setOnRefreshListener {
                    view.swipeRefreshLayoutView.isRefreshing = false
                    viewModel.loadConversations()
                }
            }
        }

        activity?.inputEditText?.addTextChangedListener(DebouncingTextWatcher(lifecycle, ::setSearchQuery))
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
            avatar.observe(this@ConversationsListView) {
                with(activity) {
                    this?.settingsButton?.imageTintList = null
                    this?.settingsButton?.setImageDrawable(it)
                }
            }

            filterLiveData.observe(this@ConversationsListView) { query ->
                if (!transitionInProgress) {
                    with(activity) {
                        this?.settingsButton?.isVisible = query.isNullOrEmpty()
                        this?.clearButton?.isVisible = !query.isNullOrEmpty()
                    }
                }
            }
        }
        return view
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        floatingActionButton?.isVisible = true
        appBar?.isVisible = true
    }

    private fun setSearchQuery(query: CharSequence?) = viewModel.filterLiveData.postValue(query)

    private fun onElementClick(page: Page, holder: Presenter.Holder, element: Element<Conversation>) {
        val conversation = element.data
        val user = viewModel.globalService.currentUserLiveData.value

        user?.let { user ->
            conversation?.let { conversation ->
                val bundle = Bundle()
                with(bundle) {
                    putParcelable(BundleKeys.KEY_USER, user.toUser())
                    putString(BundleKeys.KEY_CONVERSATION_TOKEN, conversation.token)
                    putString(BundleKeys.KEY_ROOM_ID, conversation.conversationId)
                    putParcelable(BundleKeys.KEY_ACTIVE_CONVERSATION, Parcels.wrap(conversation))
                }

                ConductorRemapping.remapChatController(
                    router, user.id, conversation.token!!,
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

                    listItemsWithImage(getConversationMenuItemsForConversation(conversation)) { _, _, item ->
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

    override fun getLayoutId(): Int = R.layout.conversations_list_view

    private fun openNewConversationScreen() =
        router.pushController(
            RouterTransaction.with(ContactsView())
                .pushChangeHandler(HorizontalChangeHandler())
                .popChangeHandler(HorizontalChangeHandler())
        )

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

        if (conversation.canLeave(viewModel.globalService.currentUserLiveData.value!!.toUser())) {
            items.add(
                BasicListItemWithImage(
                    drawable.ic_exit_to_app_black_24dp, context.getString
                (R.string.nc_leave)
                )
            )
        }

        if (conversation.canModerate(viewModel.globalService.currentUserLiveData.value!!.toUser())) {
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

    override fun onFloatingActionButtonClick() = openNewConversationScreen()

    override fun getAppBarLayoutType(): AppBarLayoutType = AppBarLayoutType.SEARCH_BAR

    override fun getTitle(): String? = resources?.getString(R.string.nc_search_conversations)

    override fun onRestoreViewState(view: View, savedViewState: Bundle) {
        super.onRestoreViewState(view, savedViewState)
        viewModel.loadConversations()
    }
}