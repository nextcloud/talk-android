/*
 * Nextcloud Talk application
 *
 * @author Marcel Hibbe
 * @author Álvaro Brey
 * Copyright (C) 2022 Álvaro Brey
 * Copyright (C) 2022 Marcel Hibbe <dev@mhibbe.de>
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
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import autodagger.AutoInjector
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.databinding.DialogPollVoteBinding
import com.nextcloud.talk.polls.model.Poll
import com.nextcloud.talk.polls.viewmodels.PollMainViewModel
import com.nextcloud.talk.polls.viewmodels.PollVoteViewModel
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class PollVoteFragment : Fragment() {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    private lateinit var parentViewModel: PollMainViewModel
    lateinit var viewModel: PollVoteViewModel

    private lateinit var binding: DialogPollVoteBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
        viewModel = ViewModelProvider(this, viewModelFactory)[PollVoteViewModel::class.java]

        parentViewModel = ViewModelProvider(requireParentFragment(), viewModelFactory)[PollMainViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogPollVoteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        parentViewModel.viewState.observe(viewLifecycleOwner) { state ->
            if (state is PollMainViewModel.PollVoteState) {
                initPollOptions(state.poll)
                initEndPollButton(state.showEndPollButton)
                updateSubmitButton()
                updateDismissEditButton(state.showDismissEditButton)
            }
        }

        viewModel.viewState.observe(viewLifecycleOwner) { state ->
            when (state) {
                PollVoteViewModel.InitialState -> {}
                is PollVoteViewModel.PollVoteFailedState -> {
                    Log.e(TAG, "Failed to vote on poll.")
                    Toast.makeText(context, R.string.nc_common_error_sorry, Toast.LENGTH_LONG).show()
                }
                is PollVoteViewModel.PollVoteHiddenSuccessState -> {
                    Toast.makeText(context, R.string.polls_voted_hidden_success, Toast.LENGTH_LONG).show()
                    parentViewModel.dismissDialog()
                }
                is PollVoteViewModel.PollVoteSuccessState -> {
                    parentViewModel.voted()
                }
            }
        }

        viewModel.submitButtonEnabled.observe(viewLifecycleOwner) { enabled ->
            binding.pollVoteSubmitButton.isEnabled = enabled
        }

        binding.pollVoteRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            viewModel.selectOption(checkedId, true)
            updateSubmitButton()
        }

        binding.pollVoteSubmitButton.setOnClickListener {
            viewModel.vote(parentViewModel.roomToken, parentViewModel.pollId)
        }

        binding.pollVoteEditDismiss.setOnClickListener {
            parentViewModel.dismissEditVotes()
        }

        themeDialog()
    }

    private fun themeDialog() {
        viewThemeUtils.material.colorMaterialButtonPrimaryFilled(binding.pollVoteSubmitButton)
        viewThemeUtils.material.colorMaterialButtonText(binding.pollVoteEndPollButton)
        viewThemeUtils.material.colorMaterialButtonPrimaryOutlined(binding.pollVoteEditDismiss)
    }

    private fun updateDismissEditButton(showDismissEditButton: Boolean) {
        if (showDismissEditButton) {
            binding.pollVoteEditDismiss.visibility = View.VISIBLE
        } else {
            binding.pollVoteEditDismiss.visibility = View.GONE
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
                viewThemeUtils.platform.themeRadioButton(radioButton)
                makeOptionBoldIfSelfVoted(radioButton, poll, index)
                binding.pollVoteRadioGroup.addView(radioButton)

                radioButton.isChecked = viewModel.selectedOptions.contains(index) == true
            }
        } else {
            binding.voteOptionsCheckboxesWrapper.removeAllViews()

            val layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            layoutParams.marginStart = CHECKBOX_MARGIN_LEFT

            poll.options?.map { option ->
                CheckBox(context).apply {
                    text = option
                    setLayoutParams(layoutParams)
                }
            }?.forEachIndexed { index, checkBox ->
                viewThemeUtils.platform.themeCheckbox(checkBox)
                checkBox.id = index
                makeOptionBoldIfSelfVoted(checkBox, poll, index)
                binding.voteOptionsCheckboxesWrapper.addView(checkBox)

                checkBox.isChecked = viewModel.selectedOptions.contains(index) == true
                checkBox.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        if (poll.maxVotes == UNLIMITED_VOTES || viewModel.selectedOptions.size < poll.maxVotes) {
                            viewModel.selectOption(index, false)
                        } else {
                            checkBox.isChecked = false
                            Toast.makeText(context, R.string.polls_max_votes_reached, Toast.LENGTH_LONG).show()
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
        viewModel.updateSubmitButton()
    }

    private fun makeOptionBoldIfSelfVoted(button: CompoundButton, poll: Poll, index: Int) {
        if (poll.votedSelf?.contains(index) == true) {
            button.setTypeface(null, Typeface.BOLD)
        }
    }

    private fun initEndPollButton(showEndPollButton: Boolean) {
        if (showEndPollButton) {
            binding.pollVoteEndPollButton.visibility = View.VISIBLE
            binding.pollVoteEndPollButton.setOnClickListener {
                val dialogBuilder = MaterialAlertDialogBuilder(binding.pollVoteEndPollButton.context)
                    .setTitle(R.string.polls_end_poll)
                    .setMessage(R.string.polls_end_poll_confirm)
                    .setPositiveButton(R.string.polls_end_poll) { _, _ ->
                        parentViewModel.endPoll()
                    }
                    .setNegativeButton(R.string.nc_cancel, null)

                viewThemeUtils.material.colorMaterialAlertDialogBackground(
                    binding.pollVoteEndPollButton.context,
                    dialogBuilder
                )

                val dialog = dialogBuilder.show()

                viewThemeUtils.platform.colorTextButtons(
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE),
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                )
            }
        } else {
            binding.pollVoteEndPollButton.visibility = View.GONE
        }
    }

    companion object {
        private val TAG = PollVoteFragment::class.java.simpleName
        private const val UNLIMITED_VOTES = 0
        private const val CHECKBOX_MARGIN_LEFT = -18

        @JvmStatic
        fun newInstance(): PollVoteFragment {
            return PollVoteFragment()
        }
    }
}
