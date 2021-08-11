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

package com.moyn.talk.adapters.messages;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.text.Spannable;
import android.text.SpannableString;
import android.view.View;

import com.moyn.talk.R;
import com.moyn.talk.application.NextcloudTalkApplication;
import com.moyn.talk.models.json.chat.ChatMessage;
import com.moyn.talk.utils.DisplayUtils;
import com.moyn.talk.utils.preferences.AppPreferences;
import com.stfalcon.chatkit.messages.MessageHolders;

import java.util.Map;

import javax.inject.Inject;

import androidx.core.view.ViewCompat;
import autodagger.AutoInjector;

import static com.moyn.talk.ui.recyclerview.MessageSwipeCallback.REPLYABLE_VIEW_TAG;

@AutoInjector(NextcloudTalkApplication.class)
public class MagicSystemMessageViewHolder extends MessageHolders.IncomingTextMessageViewHolder<ChatMessage> {

    @Inject
    AppPreferences appPreferences;

    @Inject
    Context context;

    public MagicSystemMessageViewHolder(View itemView) {
        super(itemView);
        NextcloudTalkApplication.Companion.getSharedApplication().getComponentApplication().inject(this);
    }
    
    @Override
    public void onBind(ChatMessage message) {
        super.onBind(message);

        Resources resources = itemView.getResources();
        int normalColor = resources.getColor(R.color.bg_message_list_incoming_bubble);
        int pressedColor;
        int mentionColor;

        pressedColor = normalColor;
        mentionColor = resources.getColor(R.color.textColorMaxContrast);

        Drawable bubbleDrawable = DisplayUtils.getMessageSelector(normalColor,
                                resources.getColor(R.color.transparent), pressedColor,
                                R.drawable.shape_grouped_incoming_message);
        ViewCompat.setBackground(bubble, bubbleDrawable);

        Spannable messageString = new SpannableString(message.getText());

        if (message.messageParameters != null && message.messageParameters.size() > 0) {
            for (String key : message.messageParameters.keySet()) {
                Map<String, String> individualMap = message.messageParameters.get(key);
                if (individualMap != null &&
                        ("user".equals(individualMap.get("type")) ||
                                "guest".equals(individualMap.get("type")) ||
                                "call".equals(individualMap.get("type"))
                        )) {
                    messageString = DisplayUtils.searchAndColor(
                            messageString, "@" + individualMap.get("name"), mentionColor);
                }
            }
        }

        text.setText(messageString);

        itemView.setTag(REPLYABLE_VIEW_TAG, message.isReplyable());
    }
}
