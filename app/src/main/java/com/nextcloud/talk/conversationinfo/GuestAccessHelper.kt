/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2023 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-FileCopyrightText: 2022 Tim Krüger <t@timkrueger.me>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.conversationinfo

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.nextcloud.talk.R
import com.nextcloud.talk.conversationinfo.viewmodel.ConversationInfoViewModel
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.ActivityConversationInfoBinding
import com.nextcloud.talk.databinding.DialogPasswordBinding
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.capabilities.SpreedCapability
import com.nextcloud.talk.models.json.conversations.ConversationEnums
import com.nextcloud.talk.repositories.conversations.ConversationsRepository
import com.nextcloud.talk.utils.ConversationUtils
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers

class GuestAccessHelper(
    private val activity: ConversationInfoActivity,
    private val binding: ActivityConversationInfoBinding,
    private val conversation: ConversationModel,
    private val spreedCapabilities: SpreedCapability,
    private val conversationUser: User,
    private val viewModel: ConversationInfoViewModel,
    private val lifecycleOwner: LifecycleOwner
) {
    private val conversationsRepository = activity.conversationsRepository
    private val viewThemeUtils = activity.viewThemeUtils
    private val context = activity.context

    fun setupGuestAccess() {
        if (ConversationUtils.canModerate(conversation, spreedCapabilities)) {
            binding.guestAccessView.guestAccessSettings.visibility = View.VISIBLE
        } else {
            binding.guestAccessView.guestAccessSettings.visibility = View.GONE
        }

        if (conversation.type == ConversationEnums.ConversationType.ROOM_PUBLIC_CALL) {
            binding.guestAccessView.allowGuestsSwitch.isChecked = true
            showAllOptions()
            if (conversation.hasPassword) {
                binding.guestAccessView.passwordProtectionSwitch.isChecked = true
            }
        } else {
            binding.guestAccessView.allowGuestsSwitch.isChecked = false
            hideAllOptions()
        }

        binding.guestAccessView.guestAccessSettingsAllowGuest.setOnClickListener {
            val isChecked = binding.guestAccessView.allowGuestsSwitch.isChecked
            binding.guestAccessView.allowGuestsSwitch.isChecked = !isChecked
            viewModel.allowGuests(conversation.token, !isChecked)
            viewModel.allowGuestsViewState.observe(lifecycleOwner) { uiState ->
                when (uiState) {
                    is ConversationInfoViewModel.AllowGuestsUIState.Success -> {
                        binding.guestAccessView.allowGuestsSwitch.isChecked = uiState.allow
                        if (uiState.allow) {
                            showAllOptions()
                        } else {
                            hideAllOptions()
                        }
                    }
                    is ConversationInfoViewModel.AllowGuestsUIState.Error -> {
                        val exception = uiState.exception
                        val message = context.getString(R.string.nc_guest_access_allow_failed)
                        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
                        Log.e(TAG, message, exception)
                    }
                    ConversationInfoViewModel.AllowGuestsUIState.None -> {
                    }
                }
            }
        }

        binding.guestAccessView.guestAccessSettingsPasswordProtection.setOnClickListener {
            val isChecked = binding.guestAccessView.passwordProtectionSwitch.isChecked
            binding.guestAccessView.passwordProtectionSwitch.isChecked = !isChecked
            if (isChecked) {
                viewModel.setPassword("", conversation.token)
                passwordObserver()
            } else {
                showPasswordDialog()
            }
        }

        binding.guestAccessView.resendInvitationsButton.setOnClickListener {
            conversationsRepository.resendInvitations(conversation.token!!).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread()).subscribe(ResendInvitationsObserver())
        }
    }

    private fun passwordObserver() {
        viewModel.passwordViewState.observe(lifecycleOwner) { uiState ->
            when (uiState) {
                is ConversationInfoViewModel.PasswordUiState.Success -> {
                    // unused atm
                }
                is ConversationInfoViewModel.PasswordUiState.Error -> {
                    val exception = uiState.exception
                    val message = context.getString(R.string.nc_guest_access_password_failed)
                    Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
                    Log.e(TAG, message, exception)
                }
                is ConversationInfoViewModel.PasswordUiState.None -> {
                    // unused atm
                }
            }
        }
    }

    private fun showPasswordDialog() {
        val builder = MaterialAlertDialogBuilder(activity)
        builder.apply {
            val dialogPassword = DialogPasswordBinding.inflate(LayoutInflater.from(context))
            viewThemeUtils.platform.colorEditText(dialogPassword.password)
            setView(dialogPassword.root)
            setTitle(R.string.nc_guest_access_password_dialog_title)
            setPositiveButton(R.string.nc_ok) { _, _ ->
                val password = dialogPassword.password.text.toString()
                viewModel.setPassword(password, conversation.token)
            }
            setNegativeButton(R.string.nc_cancel) { _, _ ->
                binding.guestAccessView.passwordProtectionSwitch.isChecked = false
            }
        }
        createDialog(builder)
        passwordObserver()
    }

    private fun createDialog(builder: MaterialAlertDialogBuilder) {
        builder.create()
        viewThemeUtils.dialog.colorMaterialAlertDialogBackground(binding.conversationInfoName.context, builder)
        val dialog = builder.show()
        viewThemeUtils.platform.colorTextButtons(
            dialog.getButton(AlertDialog.BUTTON_POSITIVE),
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
        )
    }

    inner class ResendInvitationsObserver : Observer<ConversationsRepository.ResendInvitationsResult> {

        private lateinit var resendInvitationsResult: ConversationsRepository.ResendInvitationsResult

        override fun onSubscribe(d: Disposable) = Unit

        override fun onNext(t: ConversationsRepository.ResendInvitationsResult) {
            resendInvitationsResult = t
        }

        override fun onError(e: Throwable) {
            val message = context.getString(R.string.nc_guest_access_resend_invitations_failed)
            Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
            Log.e(TAG, message, e)
        }

        override fun onComplete() {
            if (resendInvitationsResult.successful) {
                Snackbar.make(
                    binding.root,
                    R.string.nc_guest_access_resend_invitations_successful,
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showAllOptions() {
        binding.guestAccessView.guestAccessSettingsPasswordProtection.visibility = View.VISIBLE
        if (conversationUser.capabilities?.spreedCapability?.features?.contains("sip-support") == true) {
            binding.guestAccessView.resendInvitationsButton.visibility = View.VISIBLE
        }
    }

    private fun hideAllOptions() {
        binding.guestAccessView.guestAccessSettingsPasswordProtection.visibility = View.GONE
        binding.guestAccessView.resendInvitationsButton.visibility = View.GONE
    }

    companion object {
        private val TAG = GuestAccessHelper::class.simpleName
    }
}
