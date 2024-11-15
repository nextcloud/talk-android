/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.conversation

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import autodagger.AutoInjector
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.nextcloud.android.common.ui.theme.utils.ColorRole
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.conversationinfoedit.viewmodel.ConversationInfoEditViewModel
import com.nextcloud.talk.conversationlist.ConversationsListActivity
import com.nextcloud.talk.databinding.DialogRenameConversationBinding
import com.nextcloud.talk.events.ConversationsListFetchDataEvent
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.vanniktech.emoji.EmojiPopup
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
    private lateinit var viewModel: ConversationInfoEditViewModel

    private var emojiPopup: EmojiPopup? = null

    private var roomToken = ""
    private var initialName = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        viewModel = ViewModelProvider(this, viewModelFactory)[ConversationInfoEditViewModel::class.java]
        roomToken = arguments?.getString(KEY_ROOM_TOKEN)!!
        initialName = arguments?.getString(INITIAL_NAME)!!
    }

    @SuppressLint("InflateParams")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogRenameConversationBinding.inflate(layoutInflater)

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

        setupEmojiPopup()
    }

    override fun onStart() {
        super.onStart()
        binding.textEdit.setText(initialName)

        val positiveButton = (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE)
        positiveButton.isEnabled = false
        positiveButton.setOnClickListener {
            viewModel.renameRoom(roomToken, binding.textEdit.text.toString())
        }

        themeDialog()
    }

    private fun themeDialog() {
        viewThemeUtils.platform.themeDialog(binding.root)
        viewThemeUtils.platform.colorTextButtons((dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE))
        viewThemeUtils.platform.colorTextButtons((dialog as AlertDialog).getButton(AlertDialog.BUTTON_NEGATIVE))
        viewThemeUtils.material.colorTextInputLayout(binding.textInputLayout)
    }

    private fun setupEmojiPopup() {
        emojiPopup = binding.let {
            EmojiPopup(
                rootView = requireView(),
                editText = it.textEdit,
                onEmojiPopupShownListener = {
                    viewThemeUtils.platform.colorImageView(it.smileyButton, ColorRole.PRIMARY)
                },
                onEmojiPopupDismissListener = {
                    it.smileyButton.imageTintList = ColorStateList.valueOf(
                        ResourcesCompat.getColor(
                            resources,
                            R.color.medium_emphasis_text,
                            context?.theme
                        )
                    )
                },
                onEmojiClickListener = {
                    binding.textEdit.editableText?.append(" ")
                }
            )
        }
    }

    private fun setupListeners() {
        binding.smileyButton.setOnClickListener { emojiPopup?.toggle() }
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
        viewModel.renameRoomUiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is ConversationInfoEditViewModel.RenameRoomUiState.None ->{

                }
                is ConversationInfoEditViewModel.RenameRoomUiState.Success ->{
                    handleSuccess()
                }
                is ConversationInfoEditViewModel.RenameRoomUiState.Error ->{
                    showError()
                }
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
