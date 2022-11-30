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

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.text.Spannable;
import android.text.SpannableString;
import android.view.View;
import android.view.ViewGroup;

import com.nextcloud.talk.R;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.models.json.chat.ChatMessage;
import com.nextcloud.talk.utils.DisplayUtils;
import com.nextcloud.talk.utils.preferences.AppPreferences;
import com.stfalcon.chatkit.messages.MessageHolders;

import java.util.Map;

import javax.inject.Inject;

import androidx.core.view.ViewCompat;
import autodagger.AutoInjector;

import static com.nextcloud.talk.ui.recyclerview.MessageSwipeCallback.REPLYABLE_VIEW_TAG;

@AutoInjector(NextcloudTalkApplication.class)
public class SystemMessageViewHolder extends MessageHolders.IncomingTextMessageViewHolder<ChatMessage> {

    @Inject
    AppPreferences appPreferences;

    @Inject
    Context context;

    protected ViewGroup background;

    public SystemMessageViewHolder(View itemView) {
        super(itemView);
        NextcloudTalkApplication.Companion.getSharedApplication().getComponentApplication().inject(this);
        background = itemView.findViewById(R.id.container);
    }

    @Override
    public void onBind(ChatMessage message) {
        super.onBind(message);

        Resources resources = itemView.getResources();
        int pressedColor;
        int mentionColor;

        pressedColor = resources.getColor(R.color.bg_message_list_incoming_bubble);
        mentionColor = resources.getColor(R.color.textColorMaxContrast);

        Drawable bubbleDrawable = DisplayUtils.getMessageSelector(resources.getColor(R.color.transparent),
                                                                  resources.getColor(R.color.transparent),
                                                                  pressedColor,
                                                                  R.drawable.shape_grouped_incoming_message);
        ViewCompat.setBackground(background, bubbleDrawable);

        Spannable messageString = new SpannableString(message.getText());

        if (message.getMessageParameters() != null && message.getMessageParameters().size() > 0) {
            for (String key : message.getMessageParameters().keySet()) {
                Map<String, String> individualMap = message.getMessageParameters().get(key);

                if (individualMap != null && individualMap.containsKey("name")) {
                    String searchText;
                    if ("user".equals(individualMap.get("type")) ||
                        "guest".equals(individualMap.get("type")) ||
                        "call".equals(individualMap.get("type"))
                    ) {
                        searchText = "@" + individualMap.get("name");
                    } else {
                        searchText = individualMap.get("name");
                    }
                    messageString = DisplayUtils.searchAndColor(messageString, searchText, mentionColor);
                }
            }
        }

        text.setText(messageString);

        itemView.setTag(REPLYABLE_VIEW_TAG, message.getReplyable());
    }
}
