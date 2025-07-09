/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.polls.ui

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import autodagger.AutoInjector
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.DialogPollMainBinding
import com.nextcloud.talk.polls.viewmodels.PollMainViewModel
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class PollMainDialogFragment : DialogFragment() {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    var currentUserProvider: CurrentUserProviderNew? = null
        @Inject set

    private lateinit var binding: DialogPollMainBinding
    private lateinit var viewModel: PollMainViewModel

    lateinit var user: User

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        viewModel = ViewModelProvider(this, viewModelFactory)[PollMainViewModel::class.java]

        user = currentUserProvider?.currentUser?.blockingGet()!!

        val roomToken = arguments?.getString(KEY_ROOM_TOKEN)!!
        val isOwnerOrModerator = arguments?.getBoolean(KEY_OWNER_OR_MODERATOR)!!
        val pollId = arguments?.getString(KEY_POLL_ID)!!
        val pollTitle = arguments?.getString(KEY_POLL_TITLE)!!

        viewModel.setData(user, roomToken, isOwnerOrModerator, pollId, pollTitle)
    }

    @SuppressLint("InflateParams")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogPollMainBinding.inflate(layoutInflater)

        val dialogBuilder = MaterialAlertDialogBuilder(binding.root.context).setView(binding.root)

        viewThemeUtils.dialog.colorMaterialAlertDialogBackground(binding.root.context, dialogBuilder)

        val dialog = dialogBuilder.create()

        binding.messagePollTitle.text = viewModel.pollTitle
        viewThemeUtils.dialog.colorDialogHeadline(binding.messagePollTitle)
        viewThemeUtils.dialog.colorDialogIcon(binding.messagePollIcon)

        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        binding.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.viewState.observe(viewLifecycleOwner) { state ->
            when (state) {
                PollMainViewModel.InitialState -> {}
                is PollMainViewModel.PollVoteState -> {
                    initVotersAmount(state.showVotersAmount, state.poll.numVoters, false)
                    showVoteScreen()
                }

                is PollMainViewModel.PollResultState -> {
                    initVotersAmount(state.showVotersAmount, state.poll.numVoters, true)
                    showResultsScreen()
                }

                is PollMainViewModel.LoadingState -> {
                    showLoadingScreen()
                }

                is PollMainViewModel.DismissDialogState -> {
                    dismiss()
                }

                else -> {}
            }
        }
    }

    private fun showLoadingScreen() {
        val contentFragment = PollLoadingFragment.newInstance()
        val transaction = childFragmentManager.beginTransaction()
        transaction.replace(binding.messagePollContentFragment.id, contentFragment)
        transaction.commit()
    }

    private fun showVoteScreen() {
        val contentFragment = PollVoteFragment.newInstance()

        val transaction = childFragmentManager.beginTransaction()
        transaction.replace(binding.messagePollContentFragment.id, contentFragment)
        transaction.commit()
    }

    private fun showResultsScreen() {
        val contentFragment = PollResultsFragment.newInstance()

        val transaction = childFragmentManager.beginTransaction()
        transaction.replace(binding.messagePollContentFragment.id, contentFragment)
        transaction.commit()
    }

    private fun initVotersAmount(showVotersAmount: Boolean, numVoters: Int, showResultSubtitle: Boolean) {
        if (showVotersAmount) {
            viewThemeUtils.dialog.colorDialogSupportingText(binding.pollVotesAmount)
            binding.pollVotesAmount.visibility = View.VISIBLE
            binding.pollVotesAmount.text = resources.getQuantityString(
                R.plurals.polls_amount_voters,
                numVoters,
                numVoters
            )
        } else {
            binding.pollVotesAmount.visibility = View.GONE
        }

        if (showResultSubtitle) {
            viewThemeUtils.dialog.colorDialogSupportingText(binding.pollResultsSubtitle)
            binding.pollResultsSubtitle.visibility = View.VISIBLE
            binding.pollResultsSubtitleSeperator.visibility = View.VISIBLE
        } else {
            binding.pollResultsSubtitle.visibility = View.GONE
            binding.pollResultsSubtitleSeperator.visibility = View.GONE
        }
    }

    /**
     * Fragment creator
     */
    companion object {
        private const val KEY_USER_ENTITY = "keyUserEntity"
        private const val KEY_ROOM_TOKEN = "keyRoomToken"
        private const val KEY_OWNER_OR_MODERATOR = "keyIsOwnerOrModerator"
        private const val KEY_POLL_ID = "keyPollId"
        private const val KEY_POLL_TITLE = "keyPollTitle"

        @JvmStatic
        fun newInstance(
            user: User,
            roomTokenParam: String,
            isOwnerOrModerator: Boolean,
            pollId: String,
            name: String
        ): PollMainDialogFragment {
            val args = bundleOf(
                KEY_USER_ENTITY to user,
                KEY_ROOM_TOKEN to roomTokenParam,
                KEY_OWNER_OR_MODERATOR to isOwnerOrModerator,
                KEY_POLL_ID to pollId,
                KEY_POLL_TITLE to name
            )

            val fragment = PollMainDialogFragment()
            fragment.arguments = args
            return fragment
        }
    }
}
