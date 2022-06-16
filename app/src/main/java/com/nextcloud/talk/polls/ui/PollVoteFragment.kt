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
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
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
            if (state is PollMainViewModel.PollUnvotedState) {
                val poll = state.poll
                binding.radioGroup.removeAllViews()
                poll.options?.map { option ->
                    RadioButton(context).apply { text = option }
                }?.forEachIndexed { index, radioButton ->
                    radioButton.id = index
                    binding.radioGroup.addView(radioButton)
                }
            } else if (state is PollMainViewModel.PollVotedState && state.poll.resultMode == Poll.RESULT_MODE_HIDDEN) {
                Log.d(TAG, "show vote screen also for resultMode hidden poll when already voted")
                // TODO: other text for submit button
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

        binding.radioGroup.setOnCheckedChangeListener { group, checkedId ->
            // todo set selected in viewmodel.
            Log.d("bb", "click")
        }
        // todo observe viewmodel checked, set view checked with it

        binding.submitVote.setOnClickListener {
            viewModel.vote(roomToken, pollId, binding.radioGroup.checkedRadioButtonId)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private val TAG = PollVoteFragment::class.java.simpleName
    }
}
