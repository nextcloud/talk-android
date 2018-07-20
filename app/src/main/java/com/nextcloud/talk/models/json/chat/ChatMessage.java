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
package com.nextcloud.talk.models.json.chat;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;
import com.nextcloud.talk.R;
import com.nextcloud.talk.utils.ApiUtils;
import com.stfalcon.chatkit.commons.models.IMessage;
import com.stfalcon.chatkit.commons.models.IUser;

import org.parceler.Parcel;

import java.util.Date;
import java.util.HashMap;

import lombok.Data;

@Parcel
@Data
@JsonObject
public class ChatMessage implements IMessage {
    String baseUrl;
    @JsonField(name = "id")
    int jsonMessageId;
    @JsonField(name = "token")
    String token;
    // guests or users
    @JsonField(name = "actorType")
    String actorType;
    @JsonField(name = "actorId")
    String actorId;
    // send when crafting a message
    @JsonField(name = "actorDisplayName")
    String actorDisplayName;
    @JsonField(name = "timestamp")
    long timestamp;
    // send when crafting a message, max 1000 lines
    @JsonField(name = "message")
    String message;
    @JsonField(name = "messageParameters")
    HashMap<String, HashMap<String, String>> messageParameters;
    boolean isGrouped;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override
    public String getId() {
        return Integer.toString(jsonMessageId);
    }

    @Override
    public String getText() {
        return getParsedMessage();
    }

    @Override
    public IUser getUser() {
        return new IUser() {
            @Override
            public String getId() {
                return actorId;
            }

            @Override
            public String getName() {
                return actorDisplayName;
            }

            @Override
            public String getAvatar() {
                if (getActorType().equals("users")) {
                    return ApiUtils.getUrlForAvatarWithName(getBaseUrl(), actorId, R.dimen.avatar_size);
                } else {
                    return null;
                }
            }
        };
    }

    @Override
    public Date getCreatedAt() {
        return new Date(timestamp * 1000L);
    }

    private String getParsedMessage() {
        String message = getMessage();
        if (messageParameters != null && messageParameters.size() > 0) {
            for (String key : messageParameters.keySet()) {
                HashMap<String, String> individualHashMap = messageParameters.get(key);
                if (individualHashMap.get("type").equals("user")) {
                    message = message.replaceAll("\\{" + key + "\\}", "@" +
                            messageParameters.get(key).get("name"));
                }
            }
        }


        return message;
    }
}
