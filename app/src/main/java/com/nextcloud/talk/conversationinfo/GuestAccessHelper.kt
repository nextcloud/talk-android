/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2023 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-FileCopyrightText: 2022 Tim Krüger <t@timkrueger.me>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.conversationinfo

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context.CLIPBOARD_SERVICE
import android.util.Log
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.nextcloud.talk.R
import com.nextcloud.talk.conversationinfo.viewmodel.ConversationInfoViewModel
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.ActivityConversationInfoBinding
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.capabilities.SpreedCapability
import com.nextcloud.talk.models.json.conversations.ConversationEnums
import com.nextcloud.talk.repositories.conversations.ConversationsRepository
import com.nextcloud.talk.utils.ApiUtils
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
    private val context = activity.context

    private var shouldCopyPasswordAfterSet: Boolean = false
    private var lastSetPassword: String = ""
    private var passwordValidationState by mutableStateOf<ConversationInfoViewModel.SecurePasswordViewState>(
        ConversationInfoViewModel.SecurePasswordViewState.None
    )

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

                ConversationInfoViewModel.AllowGuestsUIState.None -> Unit
            }
        }

        viewModel.securePasswordViewState.observe(lifecycleOwner) { uiState ->
            passwordValidationState = uiState
        }

        passwordObserver()

        binding.guestAccessView.guestAccessSettingsAllowGuest.setOnClickListener {
            val isChecked = binding.guestAccessView.allowGuestsSwitch.isChecked
            binding.guestAccessView.allowGuestsSwitch.isChecked = !isChecked
            viewModel.allowGuests(conversationUser, conversation.token, !isChecked)
        }

        binding.guestAccessView.guestAccessSettingsPasswordProtection.setOnClickListener {
            val isChecked = binding.guestAccessView.passwordProtectionSwitch.isChecked
            binding.guestAccessView.passwordProtectionSwitch.isChecked = !isChecked
            if (isChecked) {
                val apiVersion = ApiUtils.getConversationApiVersion(
                    conversationUser,
                    intArrayOf(ApiUtils.API_V4, ApiUtils.API_V1)
                )
                val url = ApiUtils.getUrlForRoomPassword(
                    apiVersion,
                    conversationUser.baseUrl!!,
                    conversation.token
                )
                viewModel.setPassword(user = conversationUser, url = url, password = "")
            } else {
                showPasswordDialog()
            }
        }

        binding.guestAccessView.resendInvitationsButton.setOnClickListener {
            val apiVersion = ApiUtils.getConversationApiVersion(conversationUser, intArrayOf(ApiUtils.API_V4))
            val url = ApiUtils.getUrlForParticipantsResendInvitations(
                apiVersion,
                conversationUser.baseUrl!!,
                conversation.token
            )

            conversationsRepository.resendInvitations(user = conversationUser, url = url)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(ResendInvitationsObserver())
        }
    }

    private fun passwordObserver() {
        viewModel.passwordViewState.observe(lifecycleOwner) { uiState ->
            when (uiState) {
                is ConversationInfoViewModel.PasswordUiState.Success -> {
                    if (shouldCopyPasswordAfterSet && lastSetPassword.isNotEmpty()) {
                        val clipboardManager = activity.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                        val clipData = ClipData.newPlainText("Guest access password", lastSetPassword)
                        clipboardManager.setPrimaryClip(clipData)
                    }
                    shouldCopyPasswordAfterSet = false
                    lastSetPassword = ""
                }

                is ConversationInfoViewModel.PasswordUiState.Error -> {
                    val exception = uiState.exception
                    val message = context.getString(R.string.nc_guest_access_password_failed)
                    Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
                    Log.e(TAG, message, exception)
                }

                is ConversationInfoViewModel.PasswordUiState.None -> Unit
            }
        }
    }

    private fun showPasswordDialog() {
        val apiVersion = ApiUtils.getConversationApiVersion(
            conversationUser,
            intArrayOf(ApiUtils.API_V4, ApiUtils.API_V1)
        )
        val url = ApiUtils.getUrlForRoomPassword(apiVersion, conversationUser.baseUrl!!, conversation.token)

        val validPasswordUrl = conversationUser?.capabilities?.passwordCapability?.api?.validatePasswordApi ?: ""
        passwordValidationState = ConversationInfoViewModel.SecurePasswordViewState.None

        val composeView = ComposeView(activity)
        var materialDialog: AlertDialog? = null
        val credentials = ApiUtils.getCredentials(conversationUser.username, conversationUser.token)
        composeView.setContent {
            GuestAccessPasswordDialog(
                validationState = passwordValidationState,
                onPasswordChanged = { password ->
                    viewModel.securePassword(credentials!!, validPasswordUrl, password)
                },
                onDismiss = {
                    binding.guestAccessView.passwordProtectionSwitch.isChecked = false
                    materialDialog?.dismiss()
                },
                onSave = { password, copyAfterSave ->
                    shouldCopyPasswordAfterSet = copyAfterSave
                    lastSetPassword = password
                    viewModel.setPassword(user = conversationUser, url = url, password = password)
                    materialDialog?.dismiss()
                }
            )
        }

        val builder = MaterialAlertDialogBuilder(activity)
            .setView(composeView)
            .setCancelable(true)

        materialDialog = builder.show()
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

@Composable
@Suppress("LongMethod")
private fun GuestAccessPasswordDialog(
    validationState: ConversationInfoViewModel.SecurePasswordViewState,
    onPasswordChanged: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (password: String, copyAfterSave: Boolean) -> Unit
) {
    var password by rememberSaveable { mutableStateOf("") }
    val secureText = stringResource(R.string.nc_password_secure)
    val warningMessage = passwordWarningMessage(validationState, secureText)
    val isPasswordValid =
        password.isNotBlank() && warningMessage == secureText

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(id = R.string.nc_guest_access_password_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        onPasswordChanged(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = {
                        Text(text = stringResource(id = R.string.nc_guest_access_password_dialog_hint))
                    },
                    supportingText = {
                        warningMessage?.let {
                            Text(
                                text = it,
                                color = if (!isPasswordValid) {
                                    colorResource(R.color.nc_darkRed)
                                } else {
                                    colorResource(R.color.nc_darkGreen)
                                }
                            )
                        }
                    }
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { onSave(password, true) },
                    enabled = isPasswordValid
                ) {
                    Text(text = stringResource(R.string.nc_copy_password))
                }
                TextButton(
                    onClick = { onSave(password, false) },
                    enabled = isPasswordValid
                ) {
                    Text(text = stringResource(R.string.save))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.nc_cancel))
            }
        }
    )
}

@Composable
private fun passwordWarningMessage(
    validationState: ConversationInfoViewModel.SecurePasswordViewState,
    secureText: String
): String? =
    when (validationState) {
        is ConversationInfoViewModel.SecurePasswordViewState.Success -> {
            validationState.result.passed?.let { passed ->
                if (passed) secureText else validationState.result.reason
            }
        }

        is ConversationInfoViewModel.SecurePasswordViewState.Error -> {
            stringResource(R.string.nc_common_error_sorry)
        }

        ConversationInfoViewModel.SecurePasswordViewState.None -> ""
    }
