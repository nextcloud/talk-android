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
import com.nextcloud.talk.models.json.converters.EnumParticipantFlagsConverter;
import com.nextcloud.talk.models.json.converters.EnumParticipantTypeConverter;

import org.parceler.Parcel;

import lombok.Data;

@Parcel
@Data
@JsonObject
public class Participant {
    @JsonField(name = "userId")
    public String userId;

    @JsonField(name = {
            "type", "participantType"
    }, typeConverter = EnumParticipantTypeConverter.class)
    public ParticipantType type;

    @JsonField(name = "name")
    public String name;

    @JsonField(name = "displayName")
    public String displayName;

    @JsonField(name = "lastPing")
    public long lastPing;

    @JsonField(name = "sessionId")
    public String sessionId;

    @JsonField(name = "conversationId")
    public long conversationId;

    @JsonField(name = { "inCall", "call" }, typeConverter = EnumParticipantFlagsConverter.class)
    public ParticipantFlags participantFlags;

    @JsonField(name = "source")
    public String source;

    public boolean selected;
    
    public enum ParticipantType {
        OWNER(1),
        MODERATOR(2),
        USER(3),
        GUEST(4),
        USER_FOLLOWING_LINK(5),
        GUEST_AS_MODERATOR(6);

        private long value;

        ParticipantType(long value) {
            this.value = value;
        }

        public static ParticipantType fromValue(long value) {
            if (value == 1) {
                return OWNER;
            } else if (value == 2) {
                return MODERATOR;
            } else if (value == 3) {
                return USER;
            } else if (value == 4) {
                return GUEST;
            } else if (value == 5) {
                return USER_FOLLOWING_LINK;
            } else if (value == 6) {
                return GUEST_AS_MODERATOR;
            } else {
                return GUEST;
            }
        }

        public long getValue() {
            return value;
        }

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
