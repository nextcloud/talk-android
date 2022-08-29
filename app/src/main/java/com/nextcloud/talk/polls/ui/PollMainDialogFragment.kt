/*
 * Nextcloud Talk application
 *
 * @author Marcel Hibbe
 * Copyright (C) 2022 Marcel Hibbe <dev@mhibbe.de>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class PollMainDialogFragment : DialogFragment() {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    private lateinit var binding: DialogPollMainBinding
    private lateinit var viewModel: PollMainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        viewModel = ViewModelProvider(this, viewModelFactory)[PollMainViewModel::class.java]

        val user: User = arguments?.getParcelable(KEY_USER_ENTITY)!!
        val roomToken = arguments?.getString(KEY_ROOM_TOKEN)!!
        val isOwnerOrModerator = arguments?.getBoolean(KEY_OWNER_OR_MODERATOR)!!
        val pollId = arguments?.getString(KEY_POLL_ID)!!
        val pollTitle = arguments?.getString(KEY_POLL_TITLE)!!

        viewModel.setData(user, roomToken, isOwnerOrModerator, pollId, pollTitle)
    }

    @SuppressLint("InflateParams")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogPollMainBinding.inflate(LayoutInflater.from(context))

        val dialogBuilder = MaterialAlertDialogBuilder(binding.root.context).setView(binding.root)

        viewThemeUtils.material.colorMaterialAlertDialogBackground(binding.root.context, dialogBuilder)

        val dialog = dialogBuilder.create()

        binding.messagePollTitle.text = viewModel.pollTitle
        viewThemeUtils.colorDialogHeadline(binding.messagePollTitle)
        viewThemeUtils.colorDialogIcon(binding.messagePollIcon)

        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return binding.root
    }

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
        binding.root.post {
            run() {
                val fragmentHeight = binding.messagePollContentFragment.measuredHeight

                val contentFragment = PollLoadingFragment.newInstance(fragmentHeight)
                val transaction = childFragmentManager.beginTransaction()
                transaction.replace(binding.messagePollContentFragment.id, contentFragment)
                transaction.commit()
            }
        }
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
            viewThemeUtils.colorDialogSupportingText(binding.pollVotesAmount)
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
            viewThemeUtils.colorDialogSupportingText(binding.pollResultsSubtitle)
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
