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

import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.TextView;

import com.nextcloud.talk.R;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.models.json.chat.ChatMessage;
import com.stfalcon.chatkit.messages.MessageHolders;
import com.stfalcon.chatkit.utils.RoundedImageView;

import butterknife.BindView;
import butterknife.ButterKnife;

public class MagicPreviewMessageViewHolder extends MessageHolders.IncomingImageMessageViewHolder<ChatMessage> {

    @BindView(R.id.messageText)
    TextView messageText;

    public MagicPreviewMessageViewHolder(View itemView) {
        super(itemView);
        ButterKnife.bind(this, itemView);
    }

    @Override
    public void onBind(ChatMessage message) {
        super.onBind(message);

        if (userAvatar != null) {
            if (message.isGrouped) {
                userAvatar.setVisibility(View.INVISIBLE);
                ((RoundedImageView) image).setCorners(R.dimen.message_bubble_corners_radius, R.dimen.message_bubble_corners_radius, 0, 0);
            } else {
                userAvatar.setVisibility(View.VISIBLE);
            }
        }


        if (message.getUser().getId().equals(message.activeUserId)) {
            time.setTextColor(NextcloudTalkApplication.getSharedApplication().getResources().getColor(R.color.white60));
            if (!message.isGrouped) {
                ((RoundedImageView) image).setCorners(R.dimen.message_bubble_corners_radius, 0, 0, 0);
            }
        } else {
            time.setTextColor(NextcloudTalkApplication.getSharedApplication().getResources().getColor(R.color.warm_grey_four));
            if (!message.isGrouped) {
                ((RoundedImageView) image).setCorners(0, R.dimen.message_bubble_corners_radius, 0, 0);
            }
        }


        messageText.setText(message.getSelectedIndividualHashMap().get("name"));
        image.setOnClickListener(v -> {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(message.getSelectedIndividualHashMap().get("link")));
            browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            NextcloudTalkApplication.getSharedApplication().getApplicationContext().startActivity(browserIntent);
        });
    }
}
