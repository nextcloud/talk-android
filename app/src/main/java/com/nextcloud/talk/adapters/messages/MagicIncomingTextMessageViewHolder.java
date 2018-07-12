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
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.RelativeSizeSpan;
import android.view.View;
import android.widget.TextView;

import com.kevalpatel2106.emoticongifkeyboard.widget.EmoticonTextView;
import com.nextcloud.talk.R;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.chat.ChatMessage;
import com.nextcloud.talk.utils.DisplayUtils;
import com.nextcloud.talk.utils.database.user.UserUtils;
import com.nextcloud.talk.utils.emoticons.EmoticonUtils;
import com.stfalcon.chatkit.messages.MessageHolders;

import java.util.HashMap;

import javax.inject.Inject;

import autodagger.AutoInjector;
import butterknife.BindView;
import butterknife.ButterKnife;

@AutoInjector(NextcloudTalkApplication.class)
public class MagicIncomingTextMessageViewHolder
        extends MessageHolders.IncomingTextMessageViewHolder<ChatMessage> {

    @BindView(R.id.messageAuthor)
    TextView messageAuthor;

    @BindView(R.id.messageText)
    EmoticonTextView messageText;

    @Inject
    UserUtils userUtils;

    private UserEntity currentUser;
    private View itemView;

    public MagicIncomingTextMessageViewHolder(View itemView) {
        super(itemView);
        ButterKnife.bind(this, itemView);
        NextcloudTalkApplication.getSharedApplication().getComponentApplication().inject(this);

        this.itemView = itemView;
        currentUser = userUtils.getCurrentUser();
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

        HashMap<String, HashMap<String, String>> messageParameters = message.getMessageParameters();

        Context context = NextcloudTalkApplication.getSharedApplication().getApplicationContext();

        Spannable messageString = new SpannableString(message.getText());

        if (messageParameters != null && message.getMessageParameters().size() > 0) {
            for (String key : message.getMessageParameters().keySet()) {
                HashMap<String, String> individualHashMap = message.getMessageParameters().get(key);
                if (individualHashMap.get("type").equals("user")) {
                    int color;

                    if (individualHashMap.get("id").equals(currentUser.getUserId())) {
                        color = NextcloudTalkApplication.getSharedApplication().getResources().getColor(R.color
                                .nc_incoming_text_mention_you);
                    } else {
                        color = NextcloudTalkApplication.getSharedApplication().getResources().getColor(R.color
                                .nc_incoming_text_mention_others);
                    }

                    messageString = DisplayUtils.searchAndColor(messageText.getText().toString(),
                            messageString, "@" + individualHashMap.get("name"), color);
                }
            }

        } else if (EmoticonUtils.isMessageWithSingleEmoticonOnly(context, message.getText())) {
            messageString.setSpan(new RelativeSizeSpan(2.5f), 0, messageString.length(),
                    Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
            itemView.setSelected(true);
        }

        messageText.setText(messageString);
    }
}