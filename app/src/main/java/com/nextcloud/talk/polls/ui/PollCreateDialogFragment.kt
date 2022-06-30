package com.nextcloud.talk.polls.ui

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import autodagger.AutoInjector
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.databinding.DialogPollCreateBinding
import com.nextcloud.talk.polls.adapters.PollCreateOptionItem
import com.nextcloud.talk.polls.adapters.PollCreateOptionsAdapter
import com.nextcloud.talk.polls.adapters.PollCreateOptionsItemListener
import com.nextcloud.talk.polls.viewmodels.PollCreateViewModel
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class PollCreateDialogFragment() : DialogFragment(), PollCreateOptionsItemListener {

    lateinit var roomToken: String

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    private lateinit var binding: DialogPollCreateBinding
    private lateinit var viewModel: PollCreateViewModel

    private var adapter: PollCreateOptionsAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        viewModel = ViewModelProvider(this, viewModelFactory)[PollCreateViewModel::class.java]

        roomToken = arguments?.getString(KEY_ROOM_TOKEN)!!
    }

    @SuppressLint("InflateParams")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogPollCreateBinding.inflate(LayoutInflater.from(context))

        return AlertDialog.Builder(requireContext())
            .setView(binding.root)
            .create()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.options.observe(viewLifecycleOwner) { options -> adapter?.updateOptionsList(options) }
        viewModel.question.observe(viewLifecycleOwner) { binding.pollCreateQuestion.setText(it) }
        viewModel.privatePoll.observe(viewLifecycleOwner) { binding.pollPrivatePollCheckbox.isChecked = it }
        viewModel.multipleAnswer.observe(viewLifecycleOwner) { binding.pollMultipleAnswersCheckbox.isChecked = it }

        binding.pollCreateOptionsList.layoutManager = LinearLayoutManager(context)

        adapter = PollCreateOptionsAdapter(this)
        binding.pollCreateOptionsList.adapter = adapter

        viewModel.initialize(roomToken)

        setupListeners()
        setupStateObserver()
    }

    private fun setupListeners() {
        binding.pollAddOptionsItem.setOnClickListener {
            viewModel.addOption()
            adapter?.itemCount?.minus(1)?.let { binding.pollCreateOptionsList.scrollToPosition(it) }
        }

        binding.pollDismiss.setOnClickListener {
            dismiss()
        }

        binding.pollCreateQuestion.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                // unused atm
            }

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                // unused atm
            }

            override fun onTextChanged(question: CharSequence, start: Int, before: Int, count: Int) {
                if (question.toString() != viewModel.question.value) {
                    viewModel.setQuestion(question.toString())
                    binding.pollCreateQuestion.setSelection(binding.pollCreateQuestion.length())
                }
            }
        })

        binding.pollPrivatePollCheckbox.setOnClickListener {
            viewModel.setPrivatePoll(binding.pollPrivatePollCheckbox.isChecked)
        }

        binding.pollMultipleAnswersCheckbox.setOnClickListener {
            viewModel.setMultipleAnswer(binding.pollMultipleAnswersCheckbox.isChecked)
        }

        binding.pollCreateButton.setOnClickListener {
            viewModel.createPoll()
        }
    }

    private fun setupStateObserver() {
        viewModel.viewState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is PollCreateViewModel.PollCreatedState -> dismiss()
                is PollCreateViewModel.PollCreationFailedState -> showError()
                is PollCreateViewModel.PollCreationState -> updateButtons(state)
            }
        }
    }

    private fun updateButtons(state: PollCreateViewModel.PollCreationState) {
        binding.pollAddOptionsItem.isEnabled = state.enableAddOptionButton
        binding.pollCreateButton.isEnabled = state.enableCreatePollButton
    }

    private fun showError() {
        dismiss()
        Log.e(TAG, "Failed to create poll")
        Toast.makeText(context, R.string.nc_common_error_sorry, Toast.LENGTH_LONG).show()
    }

    override fun onRemoveOptionsItemClick(pollCreateOptionItem: PollCreateOptionItem, position: Int) {
        viewModel.removeOption(pollCreateOptionItem)
    }

    override fun onOptionsItemTextChanged(pollCreateOptionItem: PollCreateOptionItem) {
        viewModel.optionsItemTextChanged()
    }

    /**
     * Fragment creator
     */
    companion object {
        private val TAG = PollCreateDialogFragment::class.java.simpleName
        private const val KEY_ROOM_TOKEN = "keyRoomToken"

        @JvmStatic
        fun newInstance(roomTokenParam: String): PollCreateDialogFragment {
            val args = Bundle()
            args.putString(KEY_ROOM_TOKEN, roomTokenParam)
            val fragment = PollCreateDialogFragment()
            fragment.arguments = args
            return fragment
        }
    }
}
