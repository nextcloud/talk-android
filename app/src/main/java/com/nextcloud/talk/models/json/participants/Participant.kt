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
package com.nextcloud.talk.models.json.participants

import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonIgnore
import com.bluelinelabs.logansquare.annotation.JsonObject
import com.nextcloud.talk.models.json.converters.EnumParticipantFlagsConverter
import com.nextcloud.talk.models.json.converters.EnumParticipantTypeConverter
import kotlinx.serialization.Serializable
import lombok.Data
import org.parceler.Parcel
import java.util.*

@Parcel
@Data
@JsonObject
@Serializable
class Participant {
    @JvmField
    @JsonField(name = ["userId"])
    var userId: String? = null

    @JvmField
    @JsonField(name = ["type", "participantType"], typeConverter = EnumParticipantTypeConverter::class)
    var type: ParticipantType? = null

    @JvmField
    @JsonField(name = ["name"])
    var name: String? = null

    @JvmField
    @JsonField(name = ["displayName"])
    var displayName: String? = null

    /*@JsonField(name = "lastPing")
    public long lastPing;*/
    @JvmField
    @JsonField(name = ["sessionId"])
    var sessionId: String? = null

    @JvmField
    @JsonField(name = ["conversationId"])
    var conversationId: Long = 0

    @JvmField
    @JsonField(name = ["inCall", "call"], typeConverter = EnumParticipantFlagsConverter::class)
    var participantFlags: ParticipantFlags? = null

    @JvmField
    @JsonField(name = ["source"])
    var source: String? = null

    @JvmField
    @JsonIgnore
    var selected = false

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o !is Participant) return false
        return conversationId == o.conversationId && selected == o.selected &&
                userId == o.userId && type == o.type &&
                name == o.name &&
                displayName == o.displayName &&
                sessionId == o.sessionId && participantFlags == o.participantFlags &&
                source == o.source
    }

    override fun hashCode(): Int {
        return Objects.hash(userId, type, name, displayName, sessionId, conversationId, participantFlags, source, selected)
    }

    enum class ParticipantType(val value: Int) {
        OWNER(1), MODERATOR(2), USER(3), GUEST(4), USER_FOLLOWING_LINK(5), GUEST_AS_MODERATOR(6);

        companion object {
            fun fromValue(value: Int): ParticipantType {
                return if (value == 1) {
                    OWNER
                } else if (value == 2) {
                    MODERATOR
                } else if (value == 3) {
                    USER
                } else if (value == 4) {
                    GUEST
                } else if (value == 5) {
                    USER_FOLLOWING_LINK
                } else if (value == 6) {
                    GUEST_AS_MODERATOR
                } else {
                    GUEST
                }
            }
        }

    }

    enum class ParticipantFlags(val value: Int) {
        NOT_IN_CALL(0), IN_CALL(1), IN_CALL_WITH_AUDIO(3), IN_CALL_WITH_VIDEO(5), IN_CALL_WITH_AUDIO_AND_VIDEO(7);

        companion object {
            fun fromValue(value: Int): ParticipantFlags {
                return if (value == 0) {
                    NOT_IN_CALL
                } else if (value == 1) {
                    IN_CALL
                } else if (value == 3) {
                    IN_CALL_WITH_AUDIO
                } else if (value == 5) {
                    IN_CALL_WITH_VIDEO
                } else if (value == 7) {
                    IN_CALL_WITH_AUDIO_AND_VIDEO
                } else {
                    NOT_IN_CALL
                }
            }
        }

    }
}