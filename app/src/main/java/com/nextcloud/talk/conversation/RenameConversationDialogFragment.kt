/*
 * Nextcloud Talk application
 *
 * @author Marcel Hibbe
 * Copyright (C) 2023 Marcel Hibbe <dev@mhibbe.de>
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

package com.nextcloud.talk.conversation

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import autodagger.AutoInjector
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.conversation.viewmodel.RenameConversationViewModel
import com.nextcloud.talk.conversationlist.ConversationsListActivity
import com.nextcloud.talk.databinding.DialogRenameConversationBinding
import com.nextcloud.talk.events.ConversationsListFetchDataEvent
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import org.greenrobot.eventbus.EventBus
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class RenameConversationDialogFragment : DialogFragment() {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    @Inject
    lateinit var eventBus: EventBus

    private lateinit var binding: DialogRenameConversationBinding
    private lateinit var viewModel: RenameConversationViewModel
    private var isEmojiPickerVisible = false
    private var roomToken = ""
    private var initialName = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        viewModel = ViewModelProvider(this, viewModelFactory)[RenameConversationViewModel::class.java]
        roomToken = arguments?.getString(KEY_ROOM_TOKEN)!!
        initialName = arguments?.getString(INITIAL_NAME)!!
    }

    @SuppressLint("InflateParams")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogRenameConversationBinding.inflate(LayoutInflater.from(context))

        val dialogBuilder = MaterialAlertDialogBuilder(binding.root.context)
            .setTitle(resources.getString(R.string.nc_rename))
            // listener is null for now to avoid closing after button was clicked.
            // listener is set later in onStart
            .setPositiveButton(R.string.nc_rename_confirm, null)
            .setNegativeButton(R.string.nc_common_dismiss, null)
            .setView(binding.root)
        viewThemeUtils.dialog.colorMaterialAlertDialogBackground(binding.root.context, dialogBuilder)

        return dialogBuilder.create()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        setupStateObserver()
    }

    override fun onStart() {
        super.onStart()
        binding.textEdit.setText(initialName)

        val positiveButton = (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE)
        positiveButton.isEnabled = false
        positiveButton.setOnClickListener {
            viewModel.renameConversation(roomToken, binding.textEdit.text.toString())
        }

        themeDialog()
    }

    private fun themeDialog() {
        viewThemeUtils.platform.themeDialog(binding.root)
        viewThemeUtils.platform.colorTextButtons((dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE))
        viewThemeUtils.platform.colorTextButtons((dialog as AlertDialog).getButton(AlertDialog.BUTTON_NEGATIVE))
        viewThemeUtils.material.colorTextInputLayout(binding.textInputLayout)
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.textEdit.windowToken, 0)
    }

    private fun KeyboardToggle() {
        binding.emojiPicker.visibility = View.GONE
        isEmojiPickerVisible = false
    }

    private fun setupEmojiPopup() {
        if (!isEmojiPickerVisible) {
            binding.emojiPicker.visibility = View.VISIBLE
            isEmojiPickerVisible = true
            hideKeyboard()
        } else {
            binding.emojiPicker.visibility = View.GONE
            isEmojiPickerVisible = false
        }
        binding.emojiPicker.setOnEmojiPickedListener() {
            binding.textEdit.editableText?.append(it.emoji)
        }
    }

    private fun setupListeners() {
        binding.smileyButton.setOnClickListener { setupEmojiPopup() }
        binding.textEdit.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                KeyboardToggle()
            }
        }
        binding.textEdit.setOnClickListener { KeyboardToggle() }
        binding.textEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                // unused atm
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                // unused atm
            }

            override fun afterTextChanged(s: Editable) {
                val positiveButton = (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE)

                if (!TextUtils.isEmpty(s)) {
                    if (initialName == s.toString()) {
                        positiveButton.isEnabled = false
                    } else if (!positiveButton.isEnabled) {
                        positiveButton.isEnabled = true
                    }
                } else {
                    if (positiveButton.isEnabled) {
                        positiveButton.isEnabled = false
                    }
                }
            }
        })
    }

    private fun setupStateObserver() {
        viewModel.viewState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is RenameConversationViewModel.InitialState -> {}
                is RenameConversationViewModel.RenamingState -> {}
                is RenameConversationViewModel.RenamingSuccessState -> handleSuccess()
                is RenameConversationViewModel.RenamingFailedState -> showError()
                else -> {}
            }
        }
    }

    private fun handleSuccess() {
        eventBus.post(ConversationsListFetchDataEvent())

        context?.resources?.let {
            String.format(
                it.getString(R.string.renamed_conversation),
                initialName
            )
        }?.let {
            (activity as ConversationsListActivity?)?.showSnackbar(
                it
            )
        }

        dismiss()
    }

    private fun showError() {
        dismiss()
        Log.e(TAG, "Failed to rename conversation")
        Snackbar.make(binding.root, R.string.nc_common_error_sorry, Snackbar.LENGTH_LONG).show()
    }

    override fun onResume() {
        super.onResume()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    /**
     * Fragment creator
     */
    companion object {
        private val TAG = RenameConversationDialogFragment::class.java.simpleName
        private const val KEY_ROOM_TOKEN = "keyRoomToken"
        private const val INITIAL_NAME = "initialName"

        @JvmStatic
        fun newInstance(roomTokenParam: String, initialName: String): RenameConversationDialogFragment {
            val args = Bundle()
            args.putString(KEY_ROOM_TOKEN, roomTokenParam)
            args.putString(INITIAL_NAME, initialName)
            val fragment = RenameConversationDialogFragment()
            fragment.arguments = args
            return fragment
        }
    }
}
