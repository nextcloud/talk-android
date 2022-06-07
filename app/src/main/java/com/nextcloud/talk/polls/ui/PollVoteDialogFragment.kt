package com.nextcloud.talk.polls.ui

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import autodagger.AutoInjector
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.databinding.DialogPollVoteBinding
import com.nextcloud.talk.models.database.UserEntity
import com.nextcloud.talk.polls.viewmodels.PollViewModel

var user: UserEntity? = null
var pollId: Int? = null
var roomToken: String? = null
var pollTitle: String? = null

@AutoInjector(NextcloudTalkApplication::class)
class PollVoteDialogFragment : DialogFragment() {

    private lateinit var binding: DialogPollVoteBinding
    private lateinit var viewModel: PollViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
    }

    @SuppressLint("InflateParams")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogPollVoteBinding.inflate(LayoutInflater.from(context))

        return AlertDialog.Builder(requireContext())
            .setView(binding.root)
            .create()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return binding.root
    }

    @SuppressLint("DefaultLocale")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.messagePollTitle.text = pollTitle

        viewModel.viewState.observe(this) { state ->
            // when (state) {
            // }
        }

        viewModel.initialize(user!!, roomToken!!, pollId!!)
    }

    /**
     * Fragment creator
     */
    companion object {
        @JvmStatic
        fun newInstance(
            userEntity: UserEntity,
            roomTokenParam: String,
            id: Int,
            name: String
        ): PollVoteDialogFragment {
            user = userEntity   // TODO replace with "putParcelable" like in SetStatusDialogFragment???
            roomToken = roomTokenParam
            pollId = id
            pollTitle = name

            val dialogFragment = PollVoteDialogFragment()
            return dialogFragment
        }
    }
}
