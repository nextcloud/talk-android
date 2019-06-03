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
import com.bluelinelabs.logansquare.annotation.JsonIgnore;
import com.bluelinelabs.logansquare.annotation.JsonObject;
import com.nextcloud.talk.models.json.converters.EnumParticipantTypeConverter;
import com.nextcloud.talk.models.json.converters.ObjectParcelConverter;
import lombok.Data;
import org.parceler.Parcel;
import org.parceler.ParcelPropertyConverter;

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

    @ParcelPropertyConverter(ObjectParcelConverter.class)
    @JsonField(name = "inCall")
    Object inCall;
    String source;

    boolean selected;


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
