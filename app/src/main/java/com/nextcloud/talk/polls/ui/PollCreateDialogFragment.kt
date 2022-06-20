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
import androidx.lifecycle.Observer
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
        viewModel.options.observe(this, optionsObserver)
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

        binding.pollCreateOptionsList.layoutManager = LinearLayoutManager(context)

        adapter = PollCreateOptionsAdapter(this)
        binding.pollCreateOptionsList.adapter = adapter

        viewModel.initialize(roomToken)

        binding.pollAddOptionsItem.setOnClickListener {
            viewModel.addOption()
            // viewModel.options?.value?.let { it1 -> adapter?.notifyItemInserted(it1.size) }

            // viewModel.options?.value?.let { it1 -> adapter?.notifyItemChanged(it1.size) }
            // viewModel.options?.value?.let { it1 -> adapter?.notifyItemRangeInserted(it1.size, 1) }
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
                // TODO make question a livedata
                // if(question != viewmodel.question.value) viewModel.setQuestion(question)
                viewModel.question = question.toString()
            }
        })

        // viewModel.question.observe { it ->  binding.pollCreateQuestion.text = it }

        binding.pollPrivatePollCheckbox.setOnClickListener {
            // FIXME
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
    }

    override fun onRemoveOptionsItemClick(pollCreateOptionItem: PollCreateOptionItem, position: Int) {
        viewModel.removeOption(pollCreateOptionItem)
        // adapter?.notifyItemRemoved(position)

        // adapter?.notifyItemChanged(position)
        // adapter?.notifyItemRangeRemoved(position, 1)
    }

    var optionsObserver: Observer<ArrayList<PollCreateOptionItem>> =
        object : Observer<ArrayList<PollCreateOptionItem>> {
            override fun onChanged(options: ArrayList<PollCreateOptionItem>) {
                adapter?.updateOptionsList(options)
            }
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
