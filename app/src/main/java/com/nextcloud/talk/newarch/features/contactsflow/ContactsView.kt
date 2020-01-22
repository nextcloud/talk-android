package com.nextcloud.talk.newarch.features.contactsflow

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.bluelinelabs.conductor.autodispose.ControllerScopeProvider
import com.nextcloud.talk.R
import com.nextcloud.talk.models.json.participants.Participant
import com.nextcloud.talk.newarch.conversationsList.mvp.BaseView
import com.nextcloud.talk.newarch.mvvm.ext.initRecyclerView
import com.nextcloud.talk.newarch.utils.ElementPayload
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.otaliastudios.elements.Adapter
import com.otaliastudios.elements.Element
import com.otaliastudios.elements.Page
import com.otaliastudios.elements.Presenter
import com.uber.autodispose.lifecycle.LifecycleScopeProvider
import kotlinx.android.synthetic.main.conversations_list_view.view.*
import kotlinx.android.synthetic.main.message_state.view.*
import org.koin.android.ext.android.inject

class ContactsView<T : Any>(private val bundle: Bundle? = null) : BaseView() {
    override val scopeProvider: LifecycleScopeProvider<*> = ControllerScopeProvider.from(this)

    private lateinit var viewModel: ContactsViewModel
    val factory: ContactsViewModelFactory by inject()
    lateinit var adapter: Adapter
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
        adapter = Adapter.builder(this)
                .addSource(ContactsViewSource(viewModel.contactsLiveData, ParticipantElementType.PARTICIPANT.ordinal))
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

        view.apply {
            recyclerView.initRecyclerView(LinearLayoutManager(activity), adapter, true)
        }

        viewModel.loadContacts()

        return view
    }

    private fun onElementClick(page: Page, holder: Presenter.Holder, element: Element<T>) {
        if (element.data is Participant?) {
            val participant = element.data as Participant?
            val isElementSelected = participant?.selected == true
            participant?.selected = !isElementSelected
            adapter.notifyItemChanged(holder.adapterPosition, ElementPayload.SELECTION_TOGGLE)
        }
    }

    override fun getTitle(): String? {
        return resources?.getString(R.string.nc_select_contacts)
    }

}