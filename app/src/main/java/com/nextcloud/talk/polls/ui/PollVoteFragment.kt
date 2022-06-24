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

import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.RadioButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import autodagger.AutoInjector
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.databinding.DialogPollVoteBinding
import com.nextcloud.talk.polls.model.Poll
import com.nextcloud.talk.polls.viewmodels.PollMainViewModel
import com.nextcloud.talk.polls.viewmodels.PollVoteViewModel
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class PollVoteFragment(
    private val parentViewModel: PollMainViewModel,
    private val roomToken: String,
    private val pollId: String
) : Fragment() {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    lateinit var viewModel: PollVoteViewModel

    var _binding: DialogPollVoteBinding? = null
    val binding: DialogPollVoteBinding
        get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
        viewModel = ViewModelProvider(this, viewModelFactory)[PollVoteViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogPollVoteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        parentViewModel.viewState.observe(viewLifecycleOwner) { state ->
            if (state is PollMainViewModel.PollVoteState) {
                initPollOptions(state.poll)
                initCloseButton(state.showCloseButton)
                updateSubmitButton()
            } else if (state is PollMainViewModel.PollVoteHiddenState) {
                initPollOptions(state.poll)
                initCloseButton(state.showCloseButton)
                updateSubmitButton()
            }
        }

        viewModel.viewState.observe(viewLifecycleOwner) { state ->
            when (state) {
                PollVoteViewModel.InitialState -> {}
                is PollVoteViewModel.PollVoteFailedState -> {
                    Log.d(TAG, "fail")
                }
                is PollVoteViewModel.PollVoteSuccessState -> {
                    parentViewModel.voted()
                }
            }
        }

        binding.pollVoteRadioGroup.setOnCheckedChangeListener { group, checkedId ->
            viewModel.selectOption(checkedId, true)
            updateSubmitButton()
        }

        binding.pollVoteSubmitButton.setOnClickListener {
            viewModel.vote(roomToken, pollId)
        }
    }

    private fun initPollOptions(poll: Poll) {
        poll.votedSelf?.let { viewModel.initVotedOptions(it as ArrayList<Int>) }

        if (poll.maxVotes == 1) {
            binding.pollVoteRadioGroup.removeAllViews()
            poll.options?.map { option ->
                RadioButton(context).apply { text = option }
            }?.forEachIndexed { index, radioButton ->
                radioButton.id = index
                makeOptionBoldIfSelfVoted(radioButton, poll, index)
                binding.pollVoteRadioGroup.addView(radioButton)

                radioButton.isChecked = viewModel.selectedOptions.contains(index) == true
            }
        } else {
            binding.voteOptionsCheckboxesWrapper.removeAllViews()
            poll.options?.map { option ->
                CheckBox(context).apply { text = option }
            }?.forEachIndexed { index, checkBox ->
                checkBox.id = index
                makeOptionBoldIfSelfVoted(checkBox, poll, index)
                binding.voteOptionsCheckboxesWrapper.addView(checkBox)

                checkBox.isChecked = viewModel.selectedOptions.contains(index) == true
                checkBox.setOnCheckedChangeListener { buttonView, isChecked ->
                    if (isChecked) {
                        if (poll.maxVotes == UNLIMITED_VOTES || viewModel.selectedOptions.size < poll.maxVotes) {
                            viewModel.selectOption(index, false)
                        } else {
                            checkBox.isChecked = false
                            Toast.makeText(context, "max votes reached", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        viewModel.deSelectOption(index)
                    }
                    updateSubmitButton()
                }
            }
        }
    }

    private fun updateSubmitButton() {
        binding.pollVoteSubmitButton.isEnabled =
            areSelectedOptionsDifferentToVotedOptions() && viewModel.selectedOptions.isNotEmpty()
    }

    private fun areSelectedOptionsDifferentToVotedOptions(): Boolean {
        return !(viewModel.votedOptions.containsAll(viewModel.selectedOptions) &&
            viewModel.selectedOptions.containsAll(viewModel.votedOptions))
    }

    private fun makeOptionBoldIfSelfVoted(button: CompoundButton, poll: Poll, index: Int) {
        if (poll.votedSelf?.contains(index) == true) {
            button.setTypeface(null, Typeface.BOLD)
        }
    }

    private fun initCloseButton(showCloseButton: Boolean) {
        if (showCloseButton) {
            _binding?.pollVoteClosePollButton?.visibility = View.VISIBLE
            _binding?.pollVoteClosePollButton?.setOnClickListener {
                parentViewModel.closePoll()
            }
        } else {
            _binding?.pollVoteClosePollButton?.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private val TAG = PollVoteFragment::class.java.simpleName
        private const val UNLIMITED_VOTES = 0
    }
}
