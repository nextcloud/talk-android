/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Julius Linus <julius.linus@nextcloud.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.adapters.messages

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import autodagger.AutoInjector
import coil.Coil.imageLoader
import coil.request.ImageRequest
import coil.target.Target
import coil.transform.CircleCropTransformation
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.databinding.CallStartedMessageBinding
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ApiUtils
import com.stfalcon.chatkit.messages.MessageHolders
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class CallStartedViewHolder(incomingView: View, payload: Any) :
    MessageHolders.BaseIncomingMessageViewHolder<ChatMessage>(incomingView, payload) {
    private val binding: CallStartedMessageBinding = CallStartedMessageBinding.bind(incomingView)

    @Inject
    lateinit var context: Context

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    private lateinit var messageInterface: CallStartedMessageInterface

    override fun onBind(message: ChatMessage) {
        super.onBind(message)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
        themeBackground()
        setUpAvatarProfile(message)
        binding.callAuthorChip.text = message.actorDisplayName
        binding.joinVideoCall.setOnClickListener { messageInterface.joinVideoCall() }
        binding.joinAudioCall.setOnClickListener { messageInterface.joinAudioCall() }
    }

    private fun themeBackground() {
        binding.callStartedBackground.apply {
            viewThemeUtils.talk.themeOutgoingMessageBubble(this, grouped = true, false)
        }

        binding.callAuthorChip.apply {
            viewThemeUtils.material.colorChipBackground(this)
        }
    }

    private fun setUpAvatarProfile(message: ChatMessage) {
        val user = userManager.currentUser.blockingGet()
        val url: String = if (message.actorType == "guests" || message.actorType == "guest") {
            ApiUtils.getUrlForGuestAvatar(
                user!!.baseUrl!!,
                message.actorDisplayName,
                true
            )
        } else {
            ApiUtils.getUrlForAvatar(user!!.baseUrl!!, message.actorDisplayName, false)
        }

        val imageRequest: ImageRequest = ImageRequest.Builder(context)
            .data(url)
            .crossfade(true)
            .transformations(CircleCropTransformation())
            .target(object : Target {
                override fun onStart(placeholder: Drawable?) {
                    // unused atm
                }

                override fun onError(error: Drawable?) {
                    // unused atm
                }

                override fun onSuccess(result: Drawable) {
                    binding.callAuthorChip.chipIcon = result
                }
            })
            .build()

        imageLoader(context).enqueue(imageRequest)
    }

    fun assignCallStartedMessageInterface(inf: CallStartedMessageInterface) {
        messageInterface = inf
    }

    override fun viewDetached() {
        // unused atm
    }

    override fun viewAttached() {
        // unused atm
    }

    override fun viewRecycled() {
        // unused atm
    }

    companion object {
        val TAG: String? = CallStartedViewHolder::class.simpleName
    }
}
