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
import com.nextcloud.talk.models.json.converters.EnumParticipantTypeConverter;
import com.nextcloud.talk.models.json.converters.ObjectParcelConverter;

import org.parceler.Parcel;
import org.parceler.ParcelPropertyConverter;

@Parcel
@JsonObject
public class Participant {
    @JsonField(name = "userId")
    public String userId;

    @JsonField(name = {"type", "participantType"}, typeConverter = EnumParticipantTypeConverter.class)
    public ParticipantType type;

    @JsonField(name = "name")
    public String name;

    @JsonField(name = "displayName")
    public String displayName;

    @JsonField(name = "lastPing")
    public long lastPing;

    @JsonField(name = "sessionId")
    public String sessionId;

    @JsonField(name = "roomId")
    public long roomId;

    @ParcelPropertyConverter(ObjectParcelConverter.class)
    @JsonField(name = "inCall")
    public Object inCall;
    public String source;

    public boolean selected;

    public Participant() {
    }


    public ParticipantFlags getParticipantFlags() {
        ParticipantFlags participantFlags = ParticipantFlags.NOT_IN_CALL;
        if (inCall != null) {
            if (inCall instanceof Long) {
                participantFlags = ParticipantFlags.fromValue((Long) inCall);
            } else if (inCall instanceof Boolean) {
                if ((boolean) inCall) {
                    participantFlags = ParticipantFlags.IN_CALL;
                } else {
                    participantFlags = ParticipantFlags.NOT_IN_CALL;
                }
            }
        }

        return participantFlags;
    }

    public String getUserId() {
        return this.userId;
    }

    public ParticipantType getType() {
        return this.type;
    }

    public String getName() {
        return this.name;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public long getLastPing() {
        return this.lastPing;
    }

    public String getSessionId() {
        return this.sessionId;
    }

    public long getRoomId() {
        return this.roomId;
    }

    public Object getInCall() {
        return this.inCall;
    }

    public String getSource() {
        return this.source;
    }

    public boolean isSelected() {
        return this.selected;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setType(ParticipantType type) {
        this.type = type;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setLastPing(long lastPing) {
        this.lastPing = lastPing;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public void setRoomId(long roomId) {
        this.roomId = roomId;
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

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof Participant)) return false;
        final Participant other = (Participant) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$userId = this.getUserId();
        final Object other$userId = other.getUserId();
        if (this$userId == null ? other$userId != null : !this$userId.equals(other$userId)) return false;
        final Object this$type = this.getType();
        final Object other$type = other.getType();
        if (this$type == null ? other$type != null : !this$type.equals(other$type)) return false;
        final Object this$name = this.getName();
        final Object other$name = other.getName();
        if (this$name == null ? other$name != null : !this$name.equals(other$name)) return false;
        final Object this$displayName = this.getDisplayName();
        final Object other$displayName = other.getDisplayName();
        if (this$displayName == null ? other$displayName != null : !this$displayName.equals(other$displayName))
            return false;
        if (this.getLastPing() != other.getLastPing()) return false;
        final Object this$sessionId = this.getSessionId();
        final Object other$sessionId = other.getSessionId();
        if (this$sessionId == null ? other$sessionId != null : !this$sessionId.equals(other$sessionId)) return false;
        if (this.getRoomId() != other.getRoomId()) return false;
        final Object this$inCall = this.getInCall();
        final Object other$inCall = other.getInCall();
        if (this$inCall == null ? other$inCall != null : !this$inCall.equals(other$inCall)) return false;
        final Object this$source = this.getSource();
        final Object other$source = other.getSource();
        if (this$source == null ? other$source != null : !this$source.equals(other$source)) return false;
        if (this.isSelected() != other.isSelected()) return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof Participant;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $userId = this.getUserId();
        result = result * PRIME + ($userId == null ? 43 : $userId.hashCode());
        final Object $type = this.getType();
        result = result * PRIME + ($type == null ? 43 : $type.hashCode());
        final Object $name = this.getName();
        result = result * PRIME + ($name == null ? 43 : $name.hashCode());
        final Object $displayName = this.getDisplayName();
        result = result * PRIME + ($displayName == null ? 43 : $displayName.hashCode());
        final long $lastPing = this.getLastPing();
        result = result * PRIME + (int) ($lastPing >>> 32 ^ $lastPing);
        final Object $sessionId = this.getSessionId();
        result = result * PRIME + ($sessionId == null ? 43 : $sessionId.hashCode());
        final long $roomId = this.getRoomId();
        result = result * PRIME + (int) ($roomId >>> 32 ^ $roomId);
        final Object $inCall = this.getInCall();
        result = result * PRIME + ($inCall == null ? 43 : $inCall.hashCode());
        final Object $source = this.getSource();
        result = result * PRIME + ($source == null ? 43 : $source.hashCode());
        result = result * PRIME + (this.isSelected() ? 79 : 97);
        return result;
    }

    public String toString() {
        return "Participant(userId=" + this.getUserId() + ", type=" + this.getType() + ", name=" + this.getName() + ", displayName=" + this.getDisplayName() + ", lastPing=" + this.getLastPing() + ", sessionId=" + this.getSessionId() + ", roomId=" + this.getRoomId() + ", inCall=" + this.getInCall() + ", source=" + this.getSource() + ", selected=" + this.isSelected() + ")";
    }

    public enum ParticipantType {
        DUMMY,
        OWNER,
        MODERATOR,
        USER,
        GUEST,
        USER_FOLLOWING_LINK
    }

    public enum ParticipantFlags {
        NOT_IN_CALL(0),
        IN_CALL(1),
        IN_CALL_WITH_AUDIO(3),
        IN_CALL_WITH_VIDEO(5),
        IN_CALL_WITH_AUDIO_AND_VIDEO(7);

        private long value;

        ParticipantFlags(long value) {
            this.value = value;
        }

        public static ParticipantFlags fromValue(long value) {
            if (value == 0) {
                return NOT_IN_CALL;
            } else if (value == 1) {
                return IN_CALL;
            } else if (value == 3) {
                return IN_CALL_WITH_AUDIO;
            } else if (value == 5) {
                return IN_CALL_WITH_VIDEO;
            } else if (value == 7) {
                return IN_CALL_WITH_AUDIO_AND_VIDEO;
            } else {
                return NOT_IN_CALL;
            }
        }

        public long getValue() {
            return value;
        }

    }
}
