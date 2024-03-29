/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.conversation

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Parcelable
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
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import autodagger.AutoInjector
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.nextcloud.android.common.ui.theme.utils.ColorRole
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.chat.ChatActivity
import com.nextcloud.talk.conversation.viewmodel.ConversationViewModel
import com.nextcloud.talk.databinding.DialogCreateConversationBinding
import com.nextcloud.talk.jobs.AddParticipantsToConversation
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import com.vanniktech.emoji.EmojiPopup
import org.greenrobot.eventbus.EventBus
import org.parceler.Parcels
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class CreateConversationDialogFragment : DialogFragment() {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    @Inject
    lateinit var eventBus: EventBus

    @Inject
    lateinit var currentUserProvider: CurrentUserProviderNew

    private lateinit var binding: DialogCreateConversationBinding
    private lateinit var viewModel: ConversationViewModel

    private var emojiPopup: EmojiPopup? = null

    private var conversationType: Conversation.ConversationType? = null
    private var usersToInvite: ArrayList<String> = ArrayList()
    private var groupsToInvite: ArrayList<String> = ArrayList()
    private var emailsToInvite: ArrayList<String> = ArrayList()
    private var circlesToInvite: ArrayList<String> = ArrayList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        viewModel = ViewModelProvider(this, viewModelFactory)[ConversationViewModel::class.java]

        if (arguments?.containsKey(USERS_TO_INVITE) == true) {
            usersToInvite = arguments?.getStringArrayList(USERS_TO_INVITE)!!
        }
        if (arguments?.containsKey(GROUPS_TO_INVITE) == true) {
            groupsToInvite = arguments?.getStringArrayList(GROUPS_TO_INVITE)!!
        }
        if (arguments?.containsKey(EMAILS_TO_INVITE) == true) {
            emailsToInvite = arguments?.getStringArrayList(EMAILS_TO_INVITE)!!
        }
        if (arguments?.containsKey(CIRCLES_TO_INVITE) == true) {
            circlesToInvite = arguments?.getStringArrayList(CIRCLES_TO_INVITE)!!
        }
        if (arguments?.containsKey(KEY_CONVERSATION_TYPE) == true) {
            conversationType = Parcels.unwrap(arguments?.getParcelable(KEY_CONVERSATION_TYPE))
        }
    }

    @SuppressLint("InflateParams")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogCreateConversationBinding.inflate(LayoutInflater.from(context))

        val dialogBuilder = MaterialAlertDialogBuilder(binding.root.context)
            .setTitle(resources.getString(R.string.create_conversation))
            // listener is null for now to avoid closing after button was clicked.
            // listener is set later in onStart
            .setPositiveButton(R.string.nc_common_create, null)
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

        val positiveButton = (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE)
        positiveButton.isEnabled = false
        positiveButton.setOnClickListener {
            viewModel.createConversation(
                binding.textEdit.text.toString(),
                conversationType
            )
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
                    if (!positiveButton.isEnabled) {
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
                is ConversationViewModel.InitialState -> {}
                is ConversationViewModel.CreatingState -> {}
                is ConversationViewModel.CreatingSuccessState -> addParticipants(state.roomToken)
                is ConversationViewModel.CreatingFailedState -> {
                    Log.e(TAG, "Failed to create conversation")
                    showError()
                }
                else -> {}
            }
        }
    }

    private fun addParticipants(roomToken: String) {
        val data = Data.Builder()
        data.putLong(BundleKeys.KEY_INTERNAL_USER_ID, currentUserProvider.currentUser.blockingGet().id!!)
        data.putString(BundleKeys.KEY_TOKEN, roomToken)
        data.putStringArray(BundleKeys.KEY_SELECTED_USERS, usersToInvite.toTypedArray())
        data.putStringArray(BundleKeys.KEY_SELECTED_GROUPS, groupsToInvite.toTypedArray())
        data.putStringArray(BundleKeys.KEY_SELECTED_EMAILS, emailsToInvite.toTypedArray())
        data.putStringArray(BundleKeys.KEY_SELECTED_CIRCLES, circlesToInvite.toTypedArray())

        val addParticipantsToConversationWorker: OneTimeWorkRequest = OneTimeWorkRequest.Builder(
            AddParticipantsToConversation::class.java
        )
            .setInputData(data.build())
            .build()

        WorkManager.getInstance(requireContext()).enqueue(addParticipantsToConversationWorker)

        WorkManager.getInstance(requireContext()).getWorkInfoByIdLiveData(addParticipantsToConversationWorker.id)
            .observeForever { workInfo: WorkInfo? ->
                if (workInfo != null) {
                    when (workInfo.state) {
                        WorkInfo.State.RUNNING -> {
                            Log.d(TAG, "running AddParticipantsToConversation")
                        }

                        WorkInfo.State.SUCCEEDED -> {
                            Log.d(TAG, "success AddParticipantsToConversation")
                            initiateConversation(roomToken)
                        }

                        WorkInfo.State.FAILED -> {
                            Log.e(TAG, "failed to AddParticipantsToConversation")
                            showError()
                        }

                        else -> {
                        }
                    }
                }
            }
    }

    private fun initiateConversation(roomToken: String) {
        val bundle = Bundle()
        bundle.putString(BundleKeys.KEY_ROOM_TOKEN, roomToken)

        val chatIntent = Intent(context, ChatActivity::class.java)
        chatIntent.putExtras(bundle)
        chatIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(chatIntent)

        dismiss()
    }

    private fun showError() {
        dismiss()
        Snackbar.make(binding.root, R.string.nc_common_error_sorry, Snackbar.LENGTH_LONG).show()
    }

    /**
     * Fragment creator
     */
    companion object {
        private val TAG = CreateConversationDialogFragment::class.java.simpleName
        private const val USERS_TO_INVITE = "usersToInvite"
        private const val GROUPS_TO_INVITE = "groupsToInvite"
        private const val EMAILS_TO_INVITE = "emailsToInvite"
        private const val CIRCLES_TO_INVITE = "circlesToInvite"
        private const val KEY_CONVERSATION_TYPE = "keyConversationType"

        @JvmStatic
        fun newInstance(
            usersToInvite: ArrayList<String>?,
            groupsToInvite: ArrayList<String>?,
            emailsToInvite: ArrayList<String>?,
            circlesToInvite: ArrayList<String>?,
            conversationType: Parcelable
        ): CreateConversationDialogFragment {
            val args = Bundle()
            args.putStringArrayList(USERS_TO_INVITE, usersToInvite)
            args.putStringArrayList(GROUPS_TO_INVITE, groupsToInvite)
            args.putStringArrayList(EMAILS_TO_INVITE, emailsToInvite)
            args.putStringArrayList(CIRCLES_TO_INVITE, circlesToInvite)
            args.putParcelable(KEY_CONVERSATION_TYPE, conversationType)
            val fragment = CreateConversationDialogFragment()
            fragment.arguments = args
            return fragment
        }
    }
}
