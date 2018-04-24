/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2018 Mario Danic <mario@lovelyhq.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.adapters.messages;

import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import com.nextcloud.talk.R;
import com.nextcloud.talk.models.json.chat.ChatMessage;
import com.stfalcon.chatkit.messages.MessageHolders;

import butterknife.BindView;
import butterknife.ButterKnife;

public class MagicIncomingTextMessageViewHolder
        extends MessageHolders.IncomingTextMessageViewHolder<ChatMessage> {

    @BindView(R.id.messageAuthor)
    TextView messageAuthor;

    public MagicIncomingTextMessageViewHolder(View itemView) {
        super(itemView);
        ButterKnife.bind(this, itemView);
    }


    @Override
    public void onBind(ChatMessage message) {
        super.onBind(message);
        String author;
        if (!TextUtils.isEmpty(author = message.getActorDisplayName())) {
            messageAuthor.setText(author);
        } else {
            messageAuthor.setText(R.string.nc_nick_guest);
        }
    }
}