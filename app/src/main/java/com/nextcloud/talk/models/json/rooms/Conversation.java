/*
 *
 *   Nextcloud Talk application
 *
 *   @author Mario Danic
 *   Copyright (C) 2017 Mario Danic (mario@lovelyhq.com)
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.nextcloud.talk.models.json.rooms;

import android.content.res.Resources;
import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;
import com.nextcloud.talk.R;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.chat.ChatMessage;
import com.nextcloud.talk.models.json.converters.EnumNotificationLevelConverter;
import com.nextcloud.talk.models.json.converters.EnumParticipantTypeConverter;
import com.nextcloud.talk.models.json.converters.EnumReadOnlyConversationConverter;
import com.nextcloud.talk.models.json.converters.EnumRoomTypeConverter;
import com.nextcloud.talk.models.json.participants.Participant;
import lombok.Data;
import org.parceler.Parcel;

import java.util.HashMap;

@Parcel
@Data
@JsonObject
public class Conversation {
    @JsonField(name = "id")
    public String roomId;
    @JsonField(name = "token")
    public String token;
    @JsonField(name = "name")
    public String name;
    @JsonField(name = "displayName")
    public String displayName;
    @JsonField(name = "type", typeConverter = EnumRoomTypeConverter.class)
    public ConversationType type;
    @JsonField(name = "count")
    public long count;
    @JsonField(name = "lastPing")
    public long lastPing;
    @JsonField(name = "numGuests")
    public long numberOfGuests;
    @JsonField(name = "guestList")
    public HashMap<String, HashMap<String, Object>> guestList;
    @JsonField(name = "participants")
    public HashMap<String, HashMap<String, Object>> participants;
    @JsonField(name = "participantType", typeConverter = EnumParticipantTypeConverter.class)
    public Participant.ParticipantType participantType;
    @JsonField(name = "hasPassword")
    public boolean hasPassword;
    @JsonField(name = "sessionId")
    public String sessionId;
    public String password;
    @JsonField(name = "isFavorite")
    public boolean isFavorite;
    @JsonField(name = "lastActivity")
    public long lastActivity;
    @JsonField(name = "unreadMessages")
    public int unreadMessages;
    @JsonField(name = "unreadMention")
    public boolean unreadMention;
    @JsonField(name = "lastMessage")
    public ChatMessage lastMessage;
    @JsonField(name = "objectType")
    String objectType;
    @JsonField(name = "notificationLevel", typeConverter = EnumNotificationLevelConverter.class)
    NotificationLevel notificationLevel;
    @JsonField(name = "readOnly", typeConverter = EnumReadOnlyConversationConverter.class)
    ConversationReadOnlyState conversationReadOnlyState;


    public boolean isPublic() {
        return (ConversationType.ROOM_PUBLIC_CALL.equals(type));
    }

    public boolean isGuest() {
        return (Participant.ParticipantType.GUEST.equals(participantType) ||
                Participant.ParticipantType.USER_FOLLOWING_LINK.equals(participantType));
    }


    private boolean isLockedOneToOne(UserEntity conversationUser) {
        return (getType() == ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL && conversationUser.hasSpreedCapabilityWithName("locked-one-to-one-rooms"));
    }

    public boolean canModerate(UserEntity conversationUser) {
        return ((Participant.ParticipantType.OWNER.equals(participantType)
                || Participant.ParticipantType.MODERATOR.equals(participantType)) && !isLockedOneToOne(conversationUser));
    }

    public boolean isNameEditable(UserEntity conversationUser) {
        return (canModerate(conversationUser) && !ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL.equals(type));
    }

    public boolean canLeave(UserEntity conversationUser) {
        return !canModerate(conversationUser) || (getType() != ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL && getParticipants().size() > 1);

    }

    public String getDeleteWarningMessage() {
        Resources resources = NextcloudTalkApplication.getSharedApplication().getResources();
        if (getType() == ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL) {
            return String.format(resources.getString(R.string.nc_delete_conversation_one2one),
                    getDisplayName());
        } else if (getParticipants().size() > 1) {
            return resources.getString(R.string.nc_delete_conversation_more);
        }

        return resources.getString(R.string.nc_delete_conversation_default);
    }

    public enum NotificationLevel {
        DEFAULT,
        ALWAYS,
        MENTION,
        NEVER
    }

    public enum ConversationReadOnlyState {
        CONVERSATION_READ_WRITE,
        CONVERSATION_READ_ONLY
    }

    @Parcel
    public enum ConversationType {
        DUMMY,
        ROOM_TYPE_ONE_TO_ONE_CALL,
        ROOM_GROUP_CALL,
        ROOM_PUBLIC_CALL,
        ROOM_SYSTEM
    }

}
