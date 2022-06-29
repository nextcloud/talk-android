/*
 * Nextcloud Talk application
 *
 * @author Álvaro Brey
 * Copyright (C) 2022 Álvaro Brey
 * Copyright (C) 2022 Nextcloud GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.polls.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import autodagger.AutoInjector
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.databinding.DialogPollResultsBinding
import com.nextcloud.talk.models.database.UserEntity
import com.nextcloud.talk.polls.adapters.PollResultHeaderItem
import com.nextcloud.talk.polls.adapters.PollResultItemClickListener
import com.nextcloud.talk.polls.adapters.PollResultsAdapter
import com.nextcloud.talk.polls.viewmodels.PollMainViewModel
import com.nextcloud.talk.polls.viewmodels.PollResultsViewModel
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class PollResultsFragment(
    private val user: UserEntity,
    private val parentViewModel: PollMainViewModel,
    private val roomToken: String,
    private val pollId: String
) : Fragment(), PollResultItemClickListener {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    lateinit var viewModel: PollResultsViewModel

    var _binding: DialogPollResultsBinding? = null
    val binding: DialogPollResultsBinding
        get() = _binding!!

    private var adapter: PollResultsAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
        viewModel = ViewModelProvider(this, viewModelFactory)[PollResultsViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogPollResultsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        parentViewModel.viewState.observe(viewLifecycleOwner) { state ->
            if (state is PollMainViewModel.PollResultState) {
                initAdapter()
                viewModel.setPoll(state.poll)
                initEditButton(state.showEditButton)
                initCloseButton(state.showCloseButton)
            }
        }

        viewModel.items.observe(viewLifecycleOwner) {
            val adapter = PollResultsAdapter(user, this).apply {
                list = it
            }
            binding.pollResultsList.adapter = adapter
        }
    }

    private fun initAdapter() {
        adapter = PollResultsAdapter(user, this)
        _binding?.pollResultsList?.adapter = adapter
        _binding?.pollResultsList?.layoutManager = LinearLayoutManager(context)
    }

    private fun initEditButton(showEditButton: Boolean) {
        if (showEditButton) {
            _binding?.editVoteButton?.visibility = View.VISIBLE
            _binding?.editVoteButton?.setOnClickListener {
                parentViewModel.edit()
            }
        } else {
            _binding?.editVoteButton?.visibility = View.GONE
        }
    }

    private fun initCloseButton(showCloseButton: Boolean) {
        if (showCloseButton) {
            _binding?.pollResultsClosePollButton?.visibility = View.VISIBLE
            _binding?.pollResultsClosePollButton?.setOnClickListener {
                parentViewModel.closePoll()
            }
        } else {
            _binding?.pollResultsClosePollButton?.visibility = View.GONE
        }
    }

    override fun onClick(pollResultHeaderItem: PollResultHeaderItem) {
        viewModel.filterItems()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private val TAG = PollResultsFragment::class.java.simpleName
    }
}
