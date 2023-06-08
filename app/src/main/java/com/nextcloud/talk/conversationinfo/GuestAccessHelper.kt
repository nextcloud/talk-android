package com.nextcloud.talk.conversationinfo

import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nextcloud.talk.R
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.ActivityConversationInfoBinding
import com.nextcloud.talk.databinding.DialogPasswordBinding
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.repositories.conversations.ConversationsRepository
import com.nextcloud.talk.utils.Mimetype
import com.nextcloud.talk.utils.ShareUtils
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers

class GuestAccessHelper(
    private val activity: ConversationInfoActivity,
    private val binding: ActivityConversationInfoBinding,
    private val conversation: Conversation,
    private val conversationUser: User
) {

    private val conversationsRepository = activity.conversationsRepository
    private val viewThemeUtils = activity.viewThemeUtils
    private val context = activity.context

    fun setupGuestAccess() {
        if (conversation.canModerate(conversationUser)) {
            binding.guestAccessView.guestAccessSettings.visibility = View.VISIBLE
        } else {
            binding.guestAccessView.guestAccessSettings.visibility = View.GONE
        }

        if (conversation.type == Conversation.ConversationType.ROOM_PUBLIC_CALL) {
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
            conversationsRepository.allowGuests(
                conversation.token!!,
                !isChecked
            ).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread()).subscribe(AllowGuestsResultObserver())
        }

        binding.guestAccessView.guestAccessSettingsPasswordProtection.setOnClickListener {
            val isChecked = binding.guestAccessView.passwordProtectionSwitch.isChecked
            binding.guestAccessView.passwordProtectionSwitch.isChecked = !isChecked
            if (isChecked) {
                conversationsRepository.password("", conversation.token!!).subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread()).subscribe(PasswordResultObserver(false))
            } else {
                showPasswordDialog()
            }
        }

        binding.guestAccessView.shareConversationButton.setOnClickListener {
            shareUrl()
        }

        binding.guestAccessView.resendInvitationsButton.setOnClickListener {
            conversationsRepository.resendInvitations(conversation.token!!).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread()).subscribe(ResendInvitationsObserver())
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
                conversationsRepository.password(password, conversation.token!!)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(PasswordResultObserver(true))
            }
            setNegativeButton(R.string.nc_cancel) { _, _ ->
                binding.guestAccessView.passwordProtectionSwitch.isChecked = false
            }
        }
        createDialog(builder)
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

    private fun shareUrl() {
        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            type = Mimetype.TEXT_PLAIN
            putExtra(
                Intent.EXTRA_SUBJECT,
                String.format(
                    activity.resources.getString(R.string.nc_share_subject),
                    activity.resources.getString(R.string.nc_app_product_name)
                )
            )

            putExtra(
                Intent.EXTRA_TEXT,
                ShareUtils.getStringForIntent(activity, conversationUser, conversation)
            )
        }

        val shareIntent = Intent.createChooser(sendIntent, null)
        activity.startActivity(shareIntent)
    }

    inner class ResendInvitationsObserver : Observer<ConversationsRepository.ResendInvitationsResult> {

        private lateinit var resendInvitationsResult: ConversationsRepository.ResendInvitationsResult

        override fun onSubscribe(d: Disposable) = Unit

        override fun onNext(t: ConversationsRepository.ResendInvitationsResult) {
            resendInvitationsResult = t
        }

        override fun onError(e: Throwable) {
            val message = context.getString(R.string.nc_guest_access_resend_invitations_failed)
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            Log.e(TAG, message, e)
        }

        override fun onComplete() {
            if (resendInvitationsResult.successful) {
                Toast.makeText(context, R.string.nc_guest_access_resend_invitations_successful, Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    inner class AllowGuestsResultObserver : Observer<ConversationsRepository.AllowGuestsResult> {

        private lateinit var allowGuestsResult: ConversationsRepository.AllowGuestsResult

        override fun onNext(t: ConversationsRepository.AllowGuestsResult) {
            allowGuestsResult = t
        }

        override fun onError(e: Throwable) {
            val message = context.getString(R.string.nc_guest_access_allow_failed)
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            Log.e(TAG, message, e)
        }

        override fun onComplete() {
            binding.guestAccessView.allowGuestsSwitch.isChecked = allowGuestsResult.allow
            if (allowGuestsResult.allow) {
                showAllOptions()
            } else {
                hideAllOptions()
            }
        }

        override fun onSubscribe(d: Disposable) = Unit
    }

    private fun showAllOptions() {
        binding.guestAccessView.guestAccessSettingsPasswordProtection.visibility = View.VISIBLE
        binding.guestAccessView.shareConversationButton.visibility = View.VISIBLE
        if (conversationUser.capabilities?.spreedCapability?.features?.contains("sip-support") == true) {
            binding.guestAccessView.resendInvitationsButton.visibility = View.VISIBLE
        }
    }

    private fun hideAllOptions() {
        binding.guestAccessView.guestAccessSettingsPasswordProtection.visibility = View.GONE
        binding.guestAccessView.shareConversationButton.visibility = View.GONE
        binding.guestAccessView.resendInvitationsButton.visibility = View.GONE
    }

    inner class PasswordResultObserver(private val setPassword: Boolean) :
        Observer<ConversationsRepository.PasswordResult> {

        private lateinit var passwordResult: ConversationsRepository.PasswordResult

        override fun onSubscribe(d: Disposable) = Unit

        override fun onNext(t: ConversationsRepository.PasswordResult) {
            passwordResult = t
        }

        override fun onError(e: Throwable) {
            val message = context.getString(R.string.nc_guest_access_password_failed)
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            Log.e(TAG, message, e)
        }

        override fun onComplete() {
            binding.guestAccessView.passwordProtectionSwitch.isChecked = passwordResult.passwordSet && setPassword
            if (passwordResult.passwordIsWeak) {
                val builder = MaterialAlertDialogBuilder(activity)
                builder.apply {
                    setTitle(R.string.nc_guest_access_password_weak_alert_title)
                    setMessage(passwordResult.message)
                    setPositiveButton("OK") { _, _ -> }
                }
                createDialog(builder)
            }
        }
    }

    companion object {
        private val TAG = GuestAccessHelper::class.simpleName
    }
}
