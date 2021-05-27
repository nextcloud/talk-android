/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Marcel Hibbe
 * Copyright (C) 2017-2018 Mario Danic <mario@lovelyhq.com>
 * Copyright (C) 2021 Marcel Hibbe <dev@mhibbe.de>
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

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.nextcloud.talk.R;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.models.json.chat.ChatMessage;
import com.stfalcon.chatkit.messages.MessageHolders;

import javax.inject.Inject;

import autodagger.AutoInjector;
import butterknife.BindView;
import butterknife.ButterKnife;
import okhttp3.OkHttpClient;

@AutoInjector(NextcloudTalkApplication.class)
public class LocationMessageViewHolder extends MessageHolders.IncomingTextMessageViewHolder<ChatMessage> {

    private static String TAG = "LocationMessageViewHolder";

    @BindView(R.id.locationText)
    TextView messageText;

    View progressBar;

    @Inject
    Context context;

    @Inject
    OkHttpClient okHttpClient;

    public LocationMessageViewHolder(View itemView) {
        super(itemView);
        ButterKnife.bind(this, itemView);
        progressBar = itemView.findViewById(R.id.progress_bar);
        NextcloudTalkApplication.Companion.getSharedApplication().getComponentApplication().inject(this);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBind(ChatMessage message) {
        super.onBind(message);

        if (message.getMessageType() == ChatMessage.MessageType.SINGLE_NC_GEOLOCATION_MESSAGE) {
            Log.d(TAG, "handle geolocation here");
            messageText.setText("geolocation...");
        }

//        text.setText("bbbbbb");
    }
}
