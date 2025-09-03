/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.adapters.messages

import android.view.View
import com.nextcloud.talk.R
import com.nextcloud.talk.chat.ChatActivity
import com.nextcloud.talk.databinding.ReactionsInsideMessageBinding
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.databinding.ItemThreadTitleBinding

class Thread {

    fun showThreadPreview(
        chatActivity: ChatActivity,
        message: ChatMessage,
        threadBinding: ItemThreadTitleBinding,
        reactionsBinding: ReactionsInsideMessageBinding,
        openThread: (message: ChatMessage) -> Unit
    ) {
        val isFirstMessageOfThreadInNormalChat = chatActivity.conversationThreadId == null && message.isThread
        if (isFirstMessageOfThreadInNormalChat) {
            threadBinding.threadTitleLayout.visibility = View.VISIBLE

            threadBinding.threadTitleLayout.findViewById<androidx.emoji2.widget.EmojiTextView>(R.id.threadTitle).text =
                message.threadTitle

            reactionsBinding.threadButton.visibility = View.VISIBLE

            reactionsBinding.threadButton.setContent {
                ThreadButtonComposable(
                    message.threadReplies ?: 0,
                    onButtonClick = { openThread(message) }
                )
            }
        } else {
            threadBinding.threadTitleLayout.visibility = View.GONE
            reactionsBinding.threadButton.visibility = View.GONE
        }
    }
}
