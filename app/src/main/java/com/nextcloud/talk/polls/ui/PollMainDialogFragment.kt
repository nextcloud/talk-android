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
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.databinding.DialogPollMainBinding
import com.nextcloud.talk.polls.model.Poll
import com.nextcloud.talk.polls.viewmodels.PollMainViewModel
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class PollMainDialogFragment(
    private val pollId: String,
    private val roomToken: String,
    private val pollTitle: String
) : DialogFragment() {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    private lateinit var binding: DialogPollMainBinding
    private lateinit var viewModel: PollMainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        viewModel = ViewModelProvider(this, viewModelFactory)[PollMainViewModel::class.java]
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.viewState.observe(viewLifecycleOwner) { state ->
            when (state) {
                PollMainViewModel.InitialState -> {}

                is PollMainViewModel.PollVotedState -> {
                    if (state.poll.resultMode == Poll.RESULT_MODE_HIDDEN) {
                        showVoteFragment()
                    } else {
                        showResultsFragment()
                    }
                }

                is PollMainViewModel.PollUnvotedState -> {
                    if (state.poll.status == Poll.STATUS_CLOSED) {
                        showResultsFragment()
                    } else {
                        showVoteFragment()
                    }
                }
            }
        }

        viewModel.initialize(roomToken, pollId)
    }

    private fun showVoteFragment() {
        val contentFragment = PollVoteFragment(
            viewModel,
            roomToken,
            pollId
        )
        val transaction = childFragmentManager.beginTransaction()
        transaction.replace(binding.messagePollContentFragment.id, contentFragment)
        transaction.commit()
    }

    private fun showResultsFragment() {
        val contentFragment = PollResultsFragment(
            viewModel,
            roomToken,
            pollId
        )
        val transaction = childFragmentManager.beginTransaction()
        transaction.replace(binding.messagePollContentFragment.id, contentFragment)
        transaction.commit()
    }

    /**
     * Fragment creator
     */
    companion object {
        @JvmStatic
        fun newInstance(
            roomTokenParam: String,
            pollId: String,
            name: String
        ): PollMainDialogFragment = PollMainDialogFragment(pollId, roomTokenParam, name)
    }
}
