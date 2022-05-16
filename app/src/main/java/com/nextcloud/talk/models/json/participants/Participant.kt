/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Andy Scherzinger
 * Copyright (C) 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * Copyright (C) 2017 Mario Danic (mario@lovelyhq.com)
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
package com.nextcloud.talk.models.json.participants

import android.os.Parcel
import android.os.Parcelable
import android.util.Log
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import com.nextcloud.talk.models.json.converters.EnumActorTypeConverter
import com.nextcloud.talk.models.json.converters.EnumParticipantTypeConverter
import com.nextcloud.talk.models.json.signaling.DataChannelMessage
import kotlinx.android.parcel.Parceler
import kotlinx.android.parcel.Parcelize
import kotlinx.android.parcel.WriteWith
import java.util.ArrayList

@Parcelize
@JsonObject
data class Participant(
    @JsonField(name = ["attendeeId"])
    var attendeeId: Long? = null,

    @JsonField(name = ["actorType"], typeConverter = EnumActorTypeConverter::class)
    var actorType: ActorType? = null,

    @JsonField(name = ["actorId"])
    var actorId: String? = null,

    @JsonField(name = ["attendeePin"])
    var attendeePin: String? = null,

    @Deprecated("")
    @JsonField(name = ["userId"])
    var userId: String? = null,

    @JsonField(name = ["type", "participantType"], typeConverter = EnumParticipantTypeConverter::class)
    var type: ParticipantType? = null,

    @Deprecated("")
    @JsonField(name = ["name"])
    var name: String? = null,

    @JsonField(name = ["displayName"])
    var displayName: String? = null,

    @JsonField(name = ["lastPing"])
    var lastPing: Long = 0,

    @Deprecated("")
    @JsonField(name = ["sessionId"])
    var sessionId: String? = null,

    @JsonField(name = ["sessionIds"])
    var sessionIds: ArrayList<String> = ArrayList(0),

    @Deprecated("")
    @JsonField(name = ["roomId"])
    var roomId: Long = 0,

    @JsonField(name = ["inCall"])
    var inCall: Long = 0,

    @JsonField(name = ["status"])
    var status: String? = null,

    @JsonField(name = ["statusIcon"])
    var statusIcon: String? = null,

    @JsonField(name = ["statusMessage"])
    var statusMessage: String? = null,

    var source: String? = null,

    var selected: Boolean = false
) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this(
        null, null, null, null, null, null, null, null,
        0, null, ArrayList(0), 0, 0, null,
        null, null
    )

    /**
     * actorType is only guaranteed in APIv3+ so use calculatedActorId
     *
     * https://github.com/nextcloud/spreed/blob/stable21/lib/Controller/RoomController.php#L1145-L1148
     */
    val calculatedActorType: ActorType
        get() = if (actorType == null) {
            if (userId != null) {
                ActorType.USERS
            } else {
                ActorType.GUESTS
            }
        } else actorType!!

    /**
     * actorId is only guaranteed in APIv3+ so use calculatedActorId.
     */
    val calculatedActorId: String?
        get() = if (actorId == null) {
            userId
        } else actorId

    enum class ActorType {
        DUMMY, EMAILS, GROUPS, GUESTS, USERS, CIRCLES
    }

    enum class ParticipantType {
        DUMMY, OWNER, MODERATOR, USER, GUEST, USER_FOLLOWING_LINK, GUEST_MODERATOR
    }

    object InCallFlags {
        const val DISCONNECTED = 0
        const val IN_CALL = 1
        const val WITH_AUDIO = 2
        const val WITH_VIDEO = 4
        const val WITH_PHONE = 8
    }
}
