/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Sowjanya Kota<sowjanya.kch@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.adapters.messages

import android.view.View
import autodagger.AutoInjector
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.stfalcon.chatkit.messages.MessageHolders

@AutoInjector(NextcloudTalkApplication::class)
class OutgoingDeckCardViewHolder(
    outcomingView: View
) : MessageHolders.OutcomingTextMessageViewHolder<ChatMessage>(outcomingView) {
}
