/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.adapters.messages

import android.view.View
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import autodagger.AutoInjector
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.databinding.ItemTemporaryMessageBinding
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.DisplayUtils
import com.stfalcon.chatkit.messages.MessagesListAdapter
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class TemporaryMessageViewHolder(outgoingView: View, payload: Any) :
    MessagesListAdapter.OutcomingMessageViewHolder<ChatMessage>(outgoingView) {

    private val binding: ItemTemporaryMessageBinding = ItemTemporaryMessageBinding.bind(outgoingView)

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    override fun onBind(message: ChatMessage?) {
        super.onBind(message)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        val bgBubbleColor = bubble.resources.getColor(R.color.bg_message_list_incoming_bubble, null)
        val layout = R.drawable.shape_outcoming_message
        val bubbleDrawable = DisplayUtils.getMessageSelector(
            bgBubbleColor,
            ResourcesCompat.getColor(bubble.resources, R.color.transparent, null),
            bgBubbleColor,
            layout
        )
        ViewCompat.setBackground(bubble, bubbleDrawable)

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
}
