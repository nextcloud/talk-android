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

package com.nextcloud.talk.newarch.features.contactsflow.contacts

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.core.view.isVisible
import androidx.lifecycle.observe
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.api.load
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.archlifecycle.ControllerLifecycleOwner
import com.bluelinelabs.conductor.autodispose.ControllerScopeProvider
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler
import com.nextcloud.talk.R
import com.nextcloud.talk.controllers.ChatController
import com.nextcloud.talk.models.json.participants.Participant
import com.nextcloud.talk.newarch.data.presenters.AdvancedEmptyPresenter
import com.nextcloud.talk.newarch.features.contactsflow.ContactsViewOperationState
import com.nextcloud.talk.newarch.features.contactsflow.ParticipantElement
import com.nextcloud.talk.newarch.features.contactsflow.groupconversation.GroupConversationView
import com.nextcloud.talk.newarch.features.search.DebouncingTextWatcher
import com.nextcloud.talk.newarch.mvvm.BaseView
import com.nextcloud.talk.newarch.mvvm.ext.initRecyclerView
import com.nextcloud.talk.newarch.utils.ElementPayload
import com.nextcloud.talk.newarch.utils.px
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.otaliastudios.elements.Adapter
import com.otaliastudios.elements.Element
import com.otaliastudios.elements.Page
import com.otaliastudios.elements.Presenter
import com.uber.autodispose.lifecycle.LifecycleScopeProvider
import kotlinx.android.synthetic.main.contacts_list_view.view.*
import kotlinx.android.synthetic.main.conversations_list_view.view.recyclerView
import kotlinx.android.synthetic.main.message_state.view.*
import kotlinx.android.synthetic.main.search_layout.*
import kotlinx.android.synthetic.main.search_layout.view.*
import org.koin.android.ext.android.inject

class ContactsView(private val bundle: Bundle? = null) : BaseView() {
    override val scopeProvider: LifecycleScopeProvider<*> = ControllerScopeProvider.from(this)
    override val lifecycleOwner = ControllerLifecycleOwner(this)

    private lateinit var viewModel: ContactsViewModel
    val factory: ContactsViewModelFactory by inject()
    private lateinit var participantsAdapter: Adapter
    private lateinit var selectedParticipantsAdapter: Adapter

    private val isNewGroupConversation = bundle?.getBoolean(BundleKeys.KEY_NEW_GROUP_CONVERSATION) == true
    private val hasToken = bundle?.containsKey(BundleKeys.KEY_CONVERSATION_TOKEN) == true

    override fun getLayoutId(): Int {
        return R.layout.contacts_list_view
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup
    ): View {
        actionBar?.show()
        setHasOptionsMenu(true)

        viewModel = viewModelProvider(factory).get(ContactsViewModel::class.java)
        val view = super.onCreateView(inflater, container)

        val contactsViewSource = ContactsViewSource(data = viewModel.contactsLiveData)
        val participantsAdapterBuilder = Adapter.builder(this)
                .addSource(contactsViewSource)
                .addSource(ContactsHeaderSource(activity as Context, ParticipantElementType.PARTICIPANT_HEADER.ordinal))
                .addSource(ContactsViewFooterSource(activity as Context, ParticipantElementType.PARTICIPANT_FOOTER.ordinal))
                .addPresenter(ContactPresenter(activity as Context, ::onElementClick))
                .addPresenter(Presenter.forLoadingIndicator(activity as Context, R.layout.loading_state))
                .addPresenter(AdvancedEmptyPresenter(activity as Context, R.layout.message_state, null) { view ->
                    val layoutParams = view.messageStateImageView.layoutParams as RelativeLayout.LayoutParams
                    layoutParams.height = 128.px
                    layoutParams.width = 128.px
                    view.messageStateImageView.layoutParams = layoutParams
                    view.messageStateTextView.setText(R.string.nc_search_empty_contacts)
                    view.messageStateImageView.load(context.getDrawable(R.drawable.ic_undraw_not_found_60pq))
                    view.messageStateImageView.imageTintList = null
                })
                .addPresenter(Presenter.forErrorIndicator(activity as Context, R.layout.message_state) { view, throwable ->
                    val layoutParams = view.messageStateImageView.layoutParams as RelativeLayout.LayoutParams
                    layoutParams.height = 128.px
                    layoutParams.width = 128.px
                    view.messageStateImageView.layoutParams = layoutParams
                    view.messageStateTextView.setText(R.string.nc_oops)
                    view.messageStateImageView.load((activity as Context).getDrawable(R.drawable.ic_undraw_server_down_s4lk))
                    view.messageStateImageView.imageTintList = null
                })
                .setAutoScrollMode(Adapter.AUTOSCROLL_POSITION_0, true)

        participantsAdapter = participantsAdapterBuilder.into(view.selectedParticipantsRecyclerView)

        selectedParticipantsAdapter = Adapter.builder(this)
                .addSource(ContactsViewSource(data = viewModel.selectedParticipantsLiveData, loadingIndicatorsEnabled = false, errorIndicatorEnabled = false, emptyIndicatorEnabled = false))
                .addPresenter(ContactPresenter(activity as Context, ::onElementClick))
                .setAutoScrollMode(Adapter.AUTOSCROLL_POSITION_ANY, true)
                .into(view.selectedParticipantsRecyclerView)

        view.apply {
            recyclerView.initRecyclerView(LinearLayoutManager(activity), participantsAdapter, true)
            selectedParticipantsRecyclerView.initRecyclerView(LinearLayoutManager(activity, RecyclerView.HORIZONTAL, false), selectedParticipantsAdapter, true)
        }

        activity?.inputEditText?.addTextChangedListener(DebouncingTextWatcher(lifecycle, ::setSearchQuery))

        selectedParticipantsAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() {
                super.onChanged()
                toggleSelectedParticipantsViewVisibility()
            }

            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                super.onItemRangeRemoved(positionStart, itemCount)
                toggleSelectedParticipantsViewVisibility()
            }

            override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
                super.onItemRangeMoved(fromPosition, toPosition, itemCount)
                toggleSelectedParticipantsViewVisibility()
            }

            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                super.onItemRangeInserted(positionStart, itemCount)
                toggleSelectedParticipantsViewVisibility()
            }

            override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
                super.onItemRangeChanged(positionStart, itemCount)
                toggleSelectedParticipantsViewVisibility()
            }

            override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) {
                super.onItemRangeChanged(positionStart, itemCount, payload)
                toggleSelectedParticipantsViewVisibility()
            }
        })

        viewModel.apply {
            filterLiveData.observe(this@ContactsView) { query ->
                if (!transitionInProgress) {
                    activity?.clearButton?.isVisible = !query.isNullOrEmpty()
                }
            }

            selectedParticipantsLiveData.observe(this@ContactsView) { participants ->
                floatingActionButton?.isVisible = participants.isNotEmpty()

            }

            operationState.observe(this@ContactsView) { operationState ->
                when (operationState.operationState) {
                    ContactsViewOperationState.OK -> {
                        val bundle = Bundle()
                        if (!hasToken || isNewGroupConversation) {
                            bundle.putString(BundleKeys.KEY_CONVERSATION_TOKEN, operationState.conversationToken)
                            router.replaceTopController(RouterTransaction.with(ChatController(bundle))
                                    .popChangeHandler(HorizontalChangeHandler())
                                    .pushChangeHandler(HorizontalChangeHandler()))
                        } else {
                            // we added the participants - go back to conversations info
                            router.popCurrentController()
                        }
                    }
                    ContactsViewOperationState.PROCESSING -> {
                        searchLayout?.searchProgressBar?.isVisible = true
                        floatingActionButton?.isVisible = false
                    }
                    ContactsViewOperationState.CONVERSATION_CREATION_FAILED -> {
                        // dunno what to do yet, an error message somewhere
                        searchLayout?.searchProgressBar?.isVisible = false
                    }
                    ContactsViewOperationState.LOADING_FAILED -> {
                        searchLayout?.searchProgressBar?.isVisible = false
                        contactsViewSource.postError(Exception(operationState.errorMessage))
                    }
                    else -> {
                        // do nothing, we're waiting
                    }
                }
            }
        }

        viewModel.initialize(bundle?.getString(BundleKeys.KEY_CONVERSATION_TOKEN), bundle?.containsKey(BundleKeys.KEY_CONVERSATION_NAME) == true)

        return view
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        floatingActionButton?.isVisible = selectedParticipantsAdapter.itemCount > 0
    }

    private fun toggleSelectedParticipantsViewVisibility() {
        view?.selectedParticipantsRecyclerView?.isVisible = selectedParticipantsAdapter.itemCount > 0
        view?.divider?.isVisible = selectedParticipantsAdapter.itemCount > 0
    }

    private fun onElementClick(page: Page, holder: Presenter.Holder, element: Element<Any>) {
        if (element.type == ParticipantElementType.PARTICIPANT.ordinal || element.type == ParticipantElementType.PARTICIPANT_SELECTED.ordinal) {
            val participantElement = element.data as ParticipantElement
            val participant = participantElement.data as Participant

            if (isNewGroupConversation || hasToken) {
                if (participant.selected) {
                    viewModel.unselectParticipant(participant)
                } else {
                    viewModel.selectParticipant(participant)
                }
                participant.selected = !participant.selected
                if (element.type == ParticipantElementType.PARTICIPANT_SELECTED.ordinal) {
                    participantsAdapter.notifyItemRangeChanged(0, participantsAdapter.itemCount, ElementPayload.SELECTION_TOGGLE)
                } else {
                    participantsAdapter.notifyItemChanged(holder.adapterPosition, ElementPayload.SELECTION_TOGGLE)
                }
            } else {
                viewModel.createConversation(1, participant.userId)
            }
        } else if (element.type == ParticipantElementType.PARTICIPANT_NEW_GROUP.ordinal) {
            router.replaceTopController(RouterTransaction.with(GroupConversationView())
                    .popChangeHandler(HorizontalChangeHandler())
                    .pushChangeHandler(HorizontalChangeHandler()))
        } else if (element.type == ParticipantElementType.PARTICIPANT_JOIN_VIA_LINK.ordinal) {

        }
    }

    private fun setSearchQuery(query: CharSequence?) {
        viewModel.setSearchQuery(query)
    }

    override fun onFloatingActionButtonClick() {
        if (hasToken) {
            val conversationToken = bundle?.getString(BundleKeys.KEY_CONVERSATION_TOKEN)
            conversationToken?.let { conversationToken ->
                viewModel.selectedParticipantsLiveData.value?.let { participantElements ->
                    viewModel.addParticipants(conversationToken, participantElements.map { it.data as Participant })
                }
            }
        }
    }

    override fun getAppBarLayoutType(): AppBarLayoutType {
        return AppBarLayoutType.SEARCH_BAR
    }

    override fun getFloatingActionButtonDrawableRes(): Int {
        return R.drawable.ic_arrow_forward_white_24px
    }

    override fun getTitle(): String? {
        return when {
            isNewGroupConversation -> {
                resources?.getString(R.string.nc_select_contacts)
            }
            hasToken -> {
                resources?.getString(R.string.nc_select_new_contacts)
            }
            else -> {
                resources?.getString(R.string.nc_select_contact)
            }
        }
    }

}