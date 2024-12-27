/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.ui.dialog

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import autodagger.AutoInjector
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.chat.ChatActivity
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.data.network.NetworkMonitor
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.DialogMessageActionsBinding
import com.nextcloud.talk.databinding.DialogTempMessageActionsBinding
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.DateUtils
import kotlinx.coroutines.launch
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class TempMessageActionsDialog(
    private val chatActivity: ChatActivity,
    private val message: ChatMessage,
    private val user: User?,
    private val currentConversation: ConversationModel?
) : BottomSheetDialog(chatActivity) {

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    @Inject
    lateinit var dateUtils: DateUtils

    @Inject
    lateinit var networkMonitor: NetworkMonitor

    private lateinit var binding: DialogTempMessageActionsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication?.componentApplication?.inject(this)

        binding = DialogTempMessageActionsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        viewThemeUtils.material.colorBottomSheetBackground(binding.root)
        viewThemeUtils.material.colorBottomSheetDragHandle(binding.bottomSheetDragHandle)

        initMenuItemCopy(!message.isDeleted)
        val apiVersion = ApiUtils.getConversationApiVersion(user!!, intArrayOf(ApiUtils.API_V4, ApiUtils.API_V3, 1))


        initMenuItems()
    }

    private fun initMenuItems() {
        this.lifecycleScope.launch {
            initResendMessage(true)
            initMenuEditMessage(true)
            initMenuDeleteMessage(true)
        }
    }

    override fun onStart() {
        super.onStart()
        val bottomSheet = findViewById<View>(R.id.design_bottom_sheet)
        val behavior = BottomSheetBehavior.from(bottomSheet as View)
        behavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    private fun initResendMessage(visible: Boolean) {
        if (visible) {
            binding.menuResendMessage.setOnClickListener {


                chatActivity.chatViewModel.resendMessage(
                    chatActivity.conversationUser!!.getCredentials(),
                    ApiUtils.getUrlForChat(
                        chatActivity.chatApiVersion,
                        chatActivity.conversationUser!!.baseUrl!!,
                        chatActivity.roomToken
                    ),
                    message
                )

                dismiss()
            }
        }
        binding.menuResendMessage.visibility = getVisibility(visible)
    }

    private fun initMenuDeleteMessage(visible: Boolean) {
        if (visible) {
            binding.menuDeleteMessage.setOnClickListener {
                chatActivity.chatViewModel.deleteTempMessage(message)
                dismiss()
            }
        }
        binding.menuDeleteMessage.visibility = getVisibility(visible)
    }

    private fun initMenuEditMessage(visible: Boolean) {
        binding.menuEditMessage.setOnClickListener {
            chatActivity.messageInputViewModel.edit(message)
            dismiss()
        }

        binding.menuEditMessage.visibility = getVisibility(visible)
    }

    private fun initMenuItemCopy(visible: Boolean) {
        if (visible) {
            binding.menuCopyMessage.setOnClickListener {
                chatActivity.copyMessage(message)
                dismiss()
            }
        }

        binding.menuCopyMessage.visibility = getVisibility(visible)
    }

    private fun getVisibility(visible: Boolean): Int {
        return if (visible) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    companion object {
        private val TAG = TempMessageActionsDialog::class.java.simpleName
    }
}
