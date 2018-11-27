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
import com.nextcloud.talk.models.json.converters.ParticipantFlagsConverter;

import org.parceler.Parcel;

import lombok.Data;

@Parcel
@Data
@JsonObject
public class Participant {
    @JsonField(name = "userId")
    String userId;

    @JsonField(name = {"type", "participantType"}, typeConverter = EnumParticipantTypeConverter.class)
    ParticipantType type;

    @JsonField(name = "name")
    String name;

    @JsonField(name = "displayName")
    String displayName;

    @JsonField(name = "lastPing")
    long lastPing;

    @JsonField(name = "sessionId")
    String sessionId;

    @JsonField(name = "roomId")
    long roomId;

    @JsonField(name = "inCall")
    boolean inCall;

    @JsonField(name = "participantFlags", typeConverter = ParticipantFlagsConverter.class)
    ParticipantFlags participantFlags;

    String source;

    public enum ParticipantType {
        DUMMY,
        OWNER,
        MODERATOR,
        USER,
        GUEST,
        USER_FOLLOWING_LINK
    }

    public enum ParticipantFlags {
        NOT_IN_CALL (0),
        IN_CALL (1),
        IN_CALL_WITH_AUDIO (3),
        IN_CALL_WITH_VIDEO (5),
        IN_CALL_WITH_AUDIO_AND_VIDEO (7);

        private int value;

        ParticipantFlags(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static ParticipantFlags fromValue(int value) {
            switch (value) {
                case 0:
                    return NOT_IN_CALL;
                case 1:
                    return IN_CALL;
                case 3:
                    return IN_CALL_WITH_AUDIO;
                case 5:
                    return IN_CALL_WITH_VIDEO;
                case 7:
                    return IN_CALL_WITH_AUDIO_AND_VIDEO;
                default:
                    return NOT_IN_CALL;
            }
        }

    }
}
