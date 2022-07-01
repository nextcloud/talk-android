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
import com.nextcloud.talk.databinding.DialogPollMainBinding
import com.nextcloud.talk.models.database.UserEntity
import com.nextcloud.talk.polls.model.Poll
import com.nextcloud.talk.polls.viewmodels.PollMainViewModel
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class PollMainDialogFragment : DialogFragment() {

    lateinit var user: UserEntity
    lateinit var roomToken: String
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
        viewModel.viewState.observe(viewLifecycleOwner) { state ->
            when (state) {
                PollMainViewModel.InitialState -> {}
                is PollMainViewModel.PollVoteHiddenState -> {
                    binding.pollDetailsText.visibility = View.VISIBLE
                    binding.pollDetailsText.text = context?.resources?.getString(R.string.polls_private_voted)
                    showVoteScreen()
                }
                is PollMainViewModel.PollVoteState -> {
                    binding.pollDetailsText.visibility = View.GONE
                    showVoteScreen()
                }
                is PollMainViewModel.PollResultState -> showResultsScreen(state.poll)
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

    private fun showResultsScreen(poll: Poll) {
        initVotesAmount(poll.totalVotes)

        val contentFragment = PollResultsFragment.newInstance(
            user
        )

        val transaction = childFragmentManager.beginTransaction()
        transaction.replace(binding.messagePollContentFragment.id, contentFragment)
        transaction.commit()
    }

    private fun initVotesAmount(totalVotes: Int) {
        binding.pollDetailsText.visibility = View.VISIBLE
        binding.pollDetailsText.text = String.format(
            resources.getString(R.string.polls_amount_voters),
            totalVotes
        )
    }

    /**
     * Fragment creator
     */
    companion object {
        private const val KEY_USER_ENTITY = "keyUserEntity"
        private const val KEY_ROOM_TOKEN = "keyRoomToken"
        private const val KEY_POLL_ID = "keyPollId"
        private const val KEY_POLL_TITLE = "keyPollTitle"

        @JvmStatic
        fun newInstance(
            user: UserEntity,
            roomTokenParam: String,
            pollId: String,
            name: String
        ): PollMainDialogFragment {
            val args = Bundle()
            args.putParcelable(KEY_USER_ENTITY, user)
            args.putString(KEY_ROOM_TOKEN, roomTokenParam)
            args.putString(KEY_POLL_ID, pollId)
            args.putString(KEY_POLL_TITLE, name)
            val fragment = PollMainDialogFragment()
            fragment.arguments = args
            return fragment
        }
    }
}
