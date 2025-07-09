/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Álvaro Brey <alvaro@alvarobrey.com>
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.polls.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import autodagger.AutoInjector
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.databinding.DialogPollResultsBinding
import com.nextcloud.talk.polls.adapters.PollResultItemClickListener
import com.nextcloud.talk.polls.adapters.PollResultsAdapter
import com.nextcloud.talk.polls.viewmodels.PollMainViewModel
import com.nextcloud.talk.polls.viewmodels.PollResultsViewModel
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class PollResultsFragment :
    Fragment(),
    PollResultItemClickListener {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    private lateinit var parentViewModel: PollMainViewModel
    lateinit var viewModel: PollResultsViewModel

    lateinit var binding: DialogPollResultsBinding

    private var adapter: PollResultsAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
        viewModel = ViewModelProvider(this, viewModelFactory)[PollResultsViewModel::class.java]
        parentViewModel = ViewModelProvider(requireParentFragment(), viewModelFactory)[PollMainViewModel::class.java]
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogPollResultsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        parentViewModel.viewState.observe(viewLifecycleOwner) { state ->
            if (state is PollMainViewModel.PollResultState) {
                initAdapter()
                viewModel.setPoll(state.poll)
                initEditButton(state.showEditButton)
                initEndPollButton(state.showEndPollButton)
            }
        }

        viewModel.items.observe(viewLifecycleOwner) {
            val adapter = PollResultsAdapter(
                parentViewModel.user,
                parentViewModel.roomToken,
                this,
                viewThemeUtils
            )
                .apply {
                    if (it != null) {
                        list = it
                    }
                }
            binding.pollResultsList.adapter = adapter
        }

        themeDialog()
    }

    private fun themeDialog() {
        viewThemeUtils.material.colorMaterialButtonPrimaryFilled(binding.editVoteButton)
        viewThemeUtils.material.colorMaterialButtonPrimaryBorderless(binding.pollResultsEndPollButton)
    }

    private fun initAdapter() {
        adapter = PollResultsAdapter(
            parentViewModel.user,
            parentViewModel.roomToken,
            this,
            viewThemeUtils
        )
        binding.pollResultsList.adapter = adapter
        binding.pollResultsList.layoutManager = LinearLayoutManager(context)
    }

    private fun initEditButton(showEditButton: Boolean) {
        if (showEditButton) {
            binding.editVoteButton.visibility = View.VISIBLE
            binding.editVoteButton.setOnClickListener {
                parentViewModel.editVotes()
            }
        } else {
            binding.editVoteButton.visibility = View.GONE
        }
    }

    private fun initEndPollButton(showEndPollButton: Boolean) {
        if (showEndPollButton) {
            binding.pollResultsEndPollButton.visibility = View.VISIBLE
            binding.pollResultsEndPollButton.setOnClickListener {
                val dialogBuilder = MaterialAlertDialogBuilder(binding.pollResultsEndPollButton.context)
                    .setTitle(R.string.polls_end_poll)
                    .setMessage(R.string.polls_end_poll_confirm)
                    .setPositiveButton(R.string.polls_end_poll) { _, _ ->
                        parentViewModel.endPoll()
                    }
                    .setNegativeButton(R.string.nc_cancel, null)

                viewThemeUtils.dialog.colorMaterialAlertDialogBackground(
                    binding.pollResultsEndPollButton.context,
                    dialogBuilder
                )

                val dialog = dialogBuilder.show()

                viewThemeUtils.platform.colorTextButtons(
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE),
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                )
            }
        } else {
            binding.pollResultsEndPollButton.visibility = View.GONE
        }
    }

    override fun onClick() {
        viewModel.toggleDetails()
    }

    companion object {
        @JvmStatic
        fun newInstance(): PollResultsFragment = PollResultsFragment()
    }
}
