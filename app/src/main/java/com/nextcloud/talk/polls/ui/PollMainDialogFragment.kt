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
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import autodagger.AutoInjector
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.DialogPollMainBinding
import com.nextcloud.talk.polls.viewmodels.PollMainViewModel
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class PollMainDialogFragment : DialogFragment() {

    lateinit var user: User
    lateinit var roomToken: String
    private var isOwnerOrModerator: Boolean = false
    lateinit var pollId: String
    lateinit var pollTitle: String

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    private lateinit var binding: DialogPollMainBinding
    private lateinit var viewModel: PollMainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        viewModel = ViewModelProvider(this, viewModelFactory)[PollMainViewModel::class.java]

        user = arguments?.getParcelable(KEY_USER_ENTITY)!!
        roomToken = arguments?.getString(KEY_ROOM_TOKEN)!!
        isOwnerOrModerator = arguments?.getBoolean(KEY_OWNER_OR_MODERATOR)!!
        pollId = arguments?.getString(KEY_POLL_ID)!!
        pollTitle = arguments?.getString(KEY_POLL_TITLE)!!
    }

    @SuppressLint("InflateParams")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogPollMainBinding.inflate(LayoutInflater.from(context))

        val dialog = AlertDialog.Builder(requireContext())
            .setView(binding.root)
            .create()

        binding.messagePollTitle.text = pollTitle

        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.setIsOwnerOrModerator(isOwnerOrModerator)

        viewModel.viewState.observe(viewLifecycleOwner) { state ->
            when (state) {
                PollMainViewModel.InitialState -> {}
                is PollMainViewModel.PollVoteHiddenState -> {
                    binding.pollVotedHidden.visibility = View.VISIBLE
                    initVotersAmount(state.showVotersAmount, state.poll.numVoters, false)
                    showVoteScreen()
                }
                is PollMainViewModel.PollVoteState -> {
                    binding.pollVotedHidden.visibility = View.GONE
                    initVotersAmount(state.showVotersAmount, state.poll.numVoters, false)
                    showVoteScreen()
                }
                is PollMainViewModel.PollResultState -> {
                    binding.pollVotedHidden.visibility = View.GONE
                    initVotersAmount(state.showVotersAmount, state.poll.numVoters, true)
                    showResultsScreen()
                }
                else -> {}
            }
        }

        viewModel.initialize(roomToken, pollId)
    }

    private fun showVoteScreen() {
        val contentFragment = PollVoteFragment.newInstance(
            roomToken,
            pollId
        )

        val transaction = childFragmentManager.beginTransaction()
        transaction.replace(binding.messagePollContentFragment.id, contentFragment)
        transaction.commit()
    }

    private fun showResultsScreen() {
        val contentFragment = PollResultsFragment.newInstance(
            user
        )

        val transaction = childFragmentManager.beginTransaction()
        transaction.replace(binding.messagePollContentFragment.id, contentFragment)
        transaction.commit()
    }

    private fun initVotersAmount(showVotersAmount: Boolean, numVoters: Int, showResultSubtitle: Boolean) {
        if (showVotersAmount) {
            binding.pollVotesAmount.visibility = View.VISIBLE
            binding.pollVotesAmount.text = String.format(
                resources.getString(R.string.polls_amount_voters),
                numVoters
            )
        } else {
            binding.pollVotesAmount.visibility = View.GONE
        }

        if (showResultSubtitle) {
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
            val args = Bundle()
            args.putParcelable(KEY_USER_ENTITY, user)
            args.putString(KEY_ROOM_TOKEN, roomTokenParam)
            args.putBoolean(KEY_OWNER_OR_MODERATOR, isOwnerOrModerator)
            args.putString(KEY_POLL_ID, pollId)
            args.putString(KEY_POLL_TITLE, name)
            val fragment = PollMainDialogFragment()
            fragment.arguments = args
            return fragment
        }
    }
}
