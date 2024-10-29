/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2017-2019 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.adapters.messages;

import android.view.View;

import com.nextcloud.talk.chat.data.model.ChatMessage;
import com.stfalcon.chatkit.messages.MessageHolders;

public class UnreadNoticeMessageViewHolder extends MessageHolders.SystemMessageViewHolder<ChatMessage> {

    public UnreadNoticeMessageViewHolder(View itemView) {
        super(itemView);
    }

    public UnreadNoticeMessageViewHolder(View itemView, Object payload) {
        super(itemView, payload);
    }

    @Override
    public void viewDetached() {
    }

    @Override
    public void viewAttached() {
    }

    @Override
    public void viewRecycled() {

    }
}
