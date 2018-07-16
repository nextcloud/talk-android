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

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;
import com.nextcloud.talk.models.json.converters.EnumParticipantTypeConverter;
import com.nextcloud.talk.models.json.converters.EnumRoomTypeConverter;
import com.nextcloud.talk.models.json.participants.Participant;

import org.parceler.Parcel;

import java.util.List;

import lombok.Data;

@Parcel
@Data
@JsonObject
public class Room {
    @JsonField(name = "id")
    public String roomId;
    @JsonField(name = "token")
    public String token;
    @JsonField(name = "name")
    public String name;
    @JsonField(name = "displayName")
    public String displayName;
    @JsonField(name = "type", typeConverter = EnumRoomTypeConverter.class)
    public RoomType type;
    @JsonField(name = "count")
    public long count;
    @JsonField(name = "lastPing")
    public long lastPing;
    @JsonField(name = "numGuests")
    public long numberOfGuests;
    @JsonField(name = "guestList")
    public List<Participant> guestList;
    @JsonField(name = "participants")
    public List<Participant> participants;
    @JsonField(name = "participantType", typeConverter = EnumParticipantTypeConverter.class)
    public Participant.ParticipantType participantType;
    @JsonField(name = "hasPassword")
    public boolean hasPassword;
    @JsonField(name = "sessionId")
    public String sessionId;
    public String password;
    @JsonField(name = "isFavorite")
    public boolean isFavorite;

    public boolean isPublic() {
        return (RoomType.ROOM_PUBLIC_CALL.equals(type));
    }

    public boolean isGuest() {
        return (Participant.ParticipantType.GUEST.equals(participantType) ||
                Participant.ParticipantType.USER_FOLLOWING_LINK.equals(participantType));
    }

    public boolean canModerate() {
        return (Participant.ParticipantType.OWNER.equals(participantType)
                || Participant.ParticipantType.MODERATOR.equals(participantType));
    }

    public boolean isNameEditable() {
        return (canModerate() && !RoomType.ROOM_TYPE_ONE_TO_ONE_CALL.equals(type));
    }

    public boolean isDeletable() {
        return (canModerate() && ((participants != null && participants.size() > 2) || numberOfGuests > 0));
    }

    @Parcel
    public enum RoomType {
        DUMMY,
        ROOM_TYPE_ONE_TO_ONE_CALL,
        ROOM_GROUP_CALL,
        ROOM_PUBLIC_CALL
    }

}
