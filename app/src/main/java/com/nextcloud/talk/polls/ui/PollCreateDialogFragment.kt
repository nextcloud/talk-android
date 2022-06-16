package com.nextcloud.talk.polls.ui

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import autodagger.AutoInjector
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.databinding.DialogPollCreateBinding
import com.nextcloud.talk.polls.adapters.PollCreateOptionItem
import com.nextcloud.talk.polls.adapters.PollCreateOptionsAdapter
import com.nextcloud.talk.polls.adapters.PollCreateOptionsItemClickListener
import com.nextcloud.talk.polls.viewmodels.PollCreateViewModel
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class PollCreateDialogFragment(
    private val roomToken: String
) : DialogFragment(), PollCreateOptionsItemClickListener {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    private lateinit var binding: DialogPollCreateBinding
    private lateinit var viewModel: PollCreateViewModel

    private var adapter: PollCreateOptionsAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        viewModel = ViewModelProvider(this, viewModelFactory)[PollCreateViewModel::class.java]
    }

    @SuppressLint("InflateParams")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogPollCreateBinding.inflate(LayoutInflater.from(context))

        val dialog = AlertDialog.Builder(requireContext())
            .setView(binding.root)
            .create()

        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = PollCreateOptionsAdapter(this)
        binding?.pollCreateOptionsList?.adapter = adapter
        binding?.pollCreateOptionsList?.layoutManager = LinearLayoutManager(context)

        viewModel.initialize(roomToken)

        for (i in 1..3) {
            val item = PollCreateOptionItem("a")
            adapter?.list?.add(item)
        }

        binding.pollAddOption.setOnClickListener {
            val item = PollCreateOptionItem("a")
            adapter?.list?.add(item)
            adapter?.notifyDataSetChanged()
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
                viewModel.question = question.toString()
            }
        })

        // binding.option1.addTextChangedListener(object : TextWatcher {
        //     override fun afterTextChanged(s: Editable) {
        //         // unused atm
        //     }
        //
        //     override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
        //         // unused atm
        //     }
        //
        //     override fun onTextChanged(option: CharSequence, start: Int, before: Int, count: Int) {
        //         viewModel.options = listOf(option.toString())
        //     }
        // })

        binding.pollPrivatePollCheckbox.setOnClickListener {
            viewModel.multipleAnswer = binding.pollMultipleAnswersCheckbox.isChecked
        }

        binding.pollMultipleAnswersCheckbox.setOnClickListener {
            viewModel.multipleAnswer = binding.pollMultipleAnswersCheckbox.isChecked
        }

        binding.pollCreateButton.setOnClickListener {
            viewModel.createPoll()
        }

        viewModel.viewState.observe(viewLifecycleOwner) { state ->
            when (state) {
                PollCreateViewModel.InitialState -> {}

                is PollCreateViewModel.PollCreatedState -> {
                    dismiss()
                }
            }
        }

        viewModel.initialize(roomToken)
    }

    override fun onDeleteClick(pollCreateOptionItem: PollCreateOptionItem, position: Int) {
        adapter?.list?.remove(pollCreateOptionItem)
        adapter?.notifyItemRemoved(position)
    }

    /**
     * Fragment creator
     */
    companion object {
        private val TAG = PollCreateDialogFragment::class.java.simpleName

        @JvmStatic
        fun newInstance(
            roomTokenParam: String
        ): PollCreateDialogFragment = PollCreateDialogFragment(roomTokenParam)
    }
}
