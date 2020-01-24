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

package com.nextcloud.talk.newarch.features.contactsflow

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.observe
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bluelinelabs.conductor.autodispose.ControllerScopeProvider
import com.nextcloud.talk.R
import com.nextcloud.talk.models.json.participants.Participant
import com.nextcloud.talk.newarch.mvvm.BaseView
import com.nextcloud.talk.newarch.mvvm.ext.initRecyclerView
import com.nextcloud.talk.newarch.utils.ElementPayload
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.otaliastudios.elements.Adapter
import com.otaliastudios.elements.Element
import com.otaliastudios.elements.Page
import com.otaliastudios.elements.Presenter
import com.uber.autodispose.lifecycle.LifecycleScopeProvider
import kotlinx.android.synthetic.main.contacts_list_view.view.*
import kotlinx.android.synthetic.main.conversations_list_view.view.recyclerView
import kotlinx.android.synthetic.main.message_state.view.*
import org.koin.android.ext.android.inject

class ContactsView<T : Any>(private val bundle: Bundle? = null) : BaseView() {
    override val scopeProvider: LifecycleScopeProvider<*> = ControllerScopeProvider.from(this)

    private lateinit var viewModel: ContactsViewModel
    val factory: ContactsViewModelFactory by inject()
    lateinit var participantsAdapter: Adapter
    lateinit var selectedParticipantsAdapter: Adapter
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
        viewModel.conversationToken = bundle?.getString(BundleKeys.KEY_CONVERSATION_TOKEN)
        val view = super.onCreateView(inflater, container)

        // todo - change empty state magic
        participantsAdapter = Adapter.builder(this)
                .addSource(ContactsViewSource(data = viewModel.contactsLiveData, elementType = ParticipantElementType.PARTICIPANT.ordinal))
                .addSource(ContactsHeaderSource(activity as Context, ParticipantElementType.PARTICIPANT_HEADER.ordinal))
                .addSource(ContactsFooterSource(activity as Context, ParticipantElementType.PARTICIPANT_FOOTER.ordinal))
                .addPresenter(ContactPresenter(activity as Context, ::onElementClick))
                .addPresenter(Presenter.forLoadingIndicator(activity as Context, R.layout.loading_state))
                .addPresenter(Presenter.forEmptyIndicator(activity as Context, R.layout.message_state))
                .addPresenter(Presenter.forErrorIndicator(activity as Context, R.layout.message_state) { view, throwable ->
                    view.messageStateTextView.setText(R.string.nc_oops)
                    view.messageStateImageView.setImageDrawable((activity as Context).getDrawable(R.drawable.ic_announcement_white_24dp))
                })
                .setAutoScrollMode(Adapter.AUTOSCROLL_POSITION_0, true)
                .into(view.recyclerView)

        selectedParticipantsAdapter = Adapter.builder(this)
                .addSource(ContactsViewSource(data = viewModel.selectedParticipantsLiveData, elementType = ParticipantElementType.PARTICIPANT_SELECTED.ordinal, loadingIndicatorsEnabled = false, errorIndicatorEnabled = false, emptyIndicatorEnabled = false))
                .addPresenter(ContactPresenter(activity as Context, ::onElementClick))
                .setAutoScrollMode(Adapter.AUTOSCROLL_POSITION_ANY, true)
                .into(view.selectedParticipantsRecyclerView)

        view.apply {
            recyclerView.initRecyclerView(LinearLayoutManager(activity), participantsAdapter, true)
            selectedParticipantsRecyclerView.initRecyclerView(LinearLayoutManager(activity, RecyclerView.HORIZONTAL, false), selectedParticipantsAdapter, true)
        }

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
            selectedParticipantsLiveData.observe(this@ContactsView) { participants ->
                view.selectedParticipantsRecyclerView.isVisible = participants.isNotEmpty()
                view.divider.isVisible = participants.isNotEmpty()

            }

        }

        viewModel.loadContacts()

        return view
    }

    private fun toggleSelectedParticipantsViewVisibility() {
        view?.selectedParticipantsRecyclerView?.isVisible = selectedParticipantsAdapter.itemCount > 0
        view?.divider?.isVisible = selectedParticipantsAdapter.itemCount > 0
    }

    private fun onElementClick(page: Page, holder: Presenter.Holder, element: Element<T>) {
        if (element.data is Participant?) {
            val participant = element.data as Participant?
            val isElementSelected = participant?.selected == true
            participant?.let {
                if (isElementSelected) {
                    viewModel.unselectParticipant(it)
                } else {
                    viewModel.selectParticipant(it)
                }
                it.selected = !isElementSelected
                if (element.type == ParticipantElementType.PARTICIPANT_SELECTED.ordinal) {
                    participantsAdapter.notifyItemRangeChanged(0, participantsAdapter.itemCount, ElementPayload.SELECTION_TOGGLE)
                } else {
                    participantsAdapter.notifyItemChanged(holder.adapterPosition, ElementPayload.SELECTION_TOGGLE)
                }

            }
        }
    }

    override fun getTitle(): String? {
        return resources?.getString(R.string.nc_select_contacts)
    }

}