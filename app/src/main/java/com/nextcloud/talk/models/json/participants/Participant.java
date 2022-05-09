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
package com.nextcloud.talk.models.json.participants;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;
import com.nextcloud.talk.models.json.converters.EnumActorTypeConverter;
import com.nextcloud.talk.models.json.converters.EnumParticipantTypeConverter;
import com.nextcloud.talk.models.json.converters.ObjectParcelConverter;

import org.parceler.Parcel;
import org.parceler.ParcelPropertyConverter;

import java.util.Arrays;

@Parcel
@JsonObject
public class Participant {
    @JsonField(name = "attendeeId")
    public Long attendeeId;

    @JsonField(name = "actorType", typeConverter = EnumActorTypeConverter.class)
    public ActorType actorType;

    @JsonField(name = "actorId")
    public String actorId;

    @JsonField(name = "attendeePin")
    public String attendeePin;

    @Deprecated
    @JsonField(name = "userId")
    public String userId;

    @JsonField(name = {"type", "participantType"}, typeConverter = EnumParticipantTypeConverter.class)
    public ParticipantType type;

    @Deprecated
    @JsonField(name = "name")
    public String name;

    @JsonField(name = "displayName")
    public String displayName;

    @JsonField(name = "lastPing")
    public long lastPing;

    @Deprecated
    @JsonField(name = "sessionId")
    public String sessionId;

    @JsonField(name = "sessionIds")
    public String[] sessionIds;

    @Deprecated
    @JsonField(name = "roomId")
    public long roomId;

    @ParcelPropertyConverter(ObjectParcelConverter.class)
    @JsonField(name = "inCall")
    public Object inCall;

    @JsonField(name = "status")
    public String status;

    @JsonField(name = "statusIcon")
    public String statusIcon;

    @JsonField(name = "statusMessage")
    public String statusMessage;

    @JsonField(name = "permissions")
    public int permissions;

    @JsonField(name = "attendeePermissions")
    public int attendeePermissions;

    public String source;

    public boolean selected;

    public Long getAttendeeId() {
        return attendeeId;
    }

    public ActorType getActorType() {
        if (this.actorType == null) {
            if (this.userId != null) {
                return ActorType.USERS;
            } else {
                return ActorType.GUESTS;
            }
        }
        return actorType;
    }

    public String getActorId() {
        if (this.actorId == null) {
            return this.userId;
        }
        return actorId;
    }

    public String getAttendeePin() {
        return attendeePin;
    }

    public ParticipantType getType() {
        return this.type;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public long getLastPing() {
        return this.lastPing;
    }

    @Deprecated
    public String getSessionId() {
        return this.sessionId;
    }

    public String[] getSessionIds() {
        return sessionIds;
    }

    public Long getInCall() {
        if (inCall instanceof Long) {
            return (Long) this.inCall;
        }

        if (this.inCall instanceof Boolean) {
            if ((boolean) inCall) {
                return 1L;
            } else {
                return 0L;
            }
        }
        return 0L;
    }

    public String getSource() {
        return this.source;
    }

    public boolean isSelected() {
        return this.selected;
    }

    public void setAttendeeId(Long attendeeId) {
        this.attendeeId = attendeeId;
    }

    public void setActorType(ActorType actorType) {
        this.actorType = actorType;
    }

    public void setActorId(String actorId) {
        this.actorId = actorId;
    }

    public void setAttendeePin(String attendeePin) {
        this.attendeePin = attendeePin;
    }

    public void setType(ParticipantType type) {
        this.type = type;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setLastPing(long lastPing) {
        this.lastPing = lastPing;
    }

    @Deprecated
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public void setInCall(Object inCall) {
        this.inCall = inCall;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public void setSessionIds(String[] sessionIds) {
        this.sessionIds = sessionIds;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Participant that = (Participant) o;

        if (lastPing != that.lastPing) {
            return false;
        }
        if (roomId != that.roomId) {
            return false;
        }
        if (selected != that.selected) {
            return false;
        }
        if (!attendeeId.equals(that.attendeeId)) {
            return false;
        }
        if (!actorType.equals(that.actorType)) {
            return false;
        }
        if (!actorId.equals(that.actorId)) {
            return false;
        }
        if (!attendeePin.equals(that.attendeePin)) {
            return false;
        }
        if (!userId.equals(that.userId)) {
            return false;
        }
        if (type != that.type) {
            return false;
        }
        if (!name.equals(that.name)) {
            return false;
        }
        if (displayName != null ? !displayName.equals(that.displayName) : that.displayName != null) {
            return false;
        }
        if (!sessionId.equals(that.sessionId)) {
            return false;
        }
        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        if (!Arrays.equals(sessionIds, that.sessionIds)) {
            return false;
        }
        if (inCall != null ? !inCall.equals(that.inCall) : that.inCall != null) {
            return false;
        }
        return source != null ? source.equals(that.source) : that.source == null;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof Participant;
    }

    @Override
    public int hashCode() {
        int result = (attendeeId != null ? attendeeId.hashCode() : 0);
        result = 31 * result + (actorType != null ? actorType.hashCode() : 0);
        result = 31 * result + (actorId != null ? actorId.hashCode() : 0);
        result = 31 * result + (attendeePin != null ? attendeePin.hashCode() : 0);
        result = 31 * result + (userId != null ? userId.hashCode() : 0);
        result = 31 * result + (type != null ? type.hashCode() : 0);
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (displayName != null ? displayName.hashCode() : 0);
        result = 31 * result + (int) (lastPing ^ (lastPing >>> 32));
        result = 31 * result + (sessionId != null ? sessionId.hashCode() : 0);
        result = 31 * result + Arrays.hashCode(sessionIds);
        result = 31 * result + (int) (roomId ^ (roomId >>> 32));
        result = 31 * result + (inCall != null ? inCall.hashCode() : 0);
        result = 31 * result + (source != null ? source.hashCode() : 0);
        result = 31 * result + (selected ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Participant{" +
                "attendeeId=" + attendeeId +
                ", actorType='" + actorType + '\'' +
                ", actorId='" + actorId + '\'' +
                ", attendeePin='" + attendeePin + '\'' +
                ", userId='" + userId + '\'' +
                ", type=" + type +
                ", name='" + name + '\'' +
                ", displayName='" + displayName + '\'' +
                ", lastPing=" + lastPing +
                ", sessionId='" + sessionId + '\'' +
                ", sessionIds=" + Arrays.toString(sessionIds) +
                ", roomId=" + roomId +
                ", inCall=" + inCall +
                ", source='" + source + '\'' +
                ", selected=" + selected +
                '}';
    }

    public enum ActorType {
        DUMMY,
        EMAILS,
        GROUPS,
        GUESTS,
        USERS,
        CIRCLES,
    }

    public enum ParticipantType {
        DUMMY,
        OWNER,
        MODERATOR,
        USER,
        GUEST,
        USER_FOLLOWING_LINK,
        GUEST_MODERATOR
    }

    public static class InCallFlags {
        public static final int DISCONNECTED = 0;
        public static final int IN_CALL = 1;
        public static final int WITH_AUDIO = 2;
        public static final int WITH_VIDEO = 4;
        public static final int WITH_PHONE = 8;
    }
}
