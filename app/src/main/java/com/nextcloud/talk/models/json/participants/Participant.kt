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

import android.os.Parcelable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.nextcloud.talk.models.json.converters.EnumActorTypeConverter
import com.nextcloud.talk.models.json.converters.EnumParticipantTypeConverter
import kotlinx.parcelize.Parcelize

@Parcelize
@Serializable
data class Participant(
    var attendeeId: Long? = null,

    @SerialName("actorType"], typeConverter = EnumActorTypeConverter::class)
    var actorType: ActorType? = null,

    var actorId: String? = null,

    var attendeePin: String? = null,

    @Deprecated("")
    var userId: String? = null,

    var internal: Boolean? = null,

    @SerialName("type", "participantType"], typeConverter = EnumParticipantTypeConverter::class)
    var type: ParticipantType? = null,

    @Deprecated("")
    var name: String? = null,

    var displayName: String? = null,

    var lastPing: Long = 0,

    @Deprecated("")
    var sessionId: String? = null,

    var sessionIds: ArrayList<String> = ArrayList(0),

    @Deprecated("")
    var roomId: Long = 0,
    var inCall: Long = 0,
    var status: String? = null,
    var statusIcon: String? = null,
    var statusMessage: String? = null,
    var source: String? = null,
    var selected: Boolean = false
) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this(
        null, null, null, null, null, null, null, null, null,
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
        } else {
            actorType!!
        }

    /**
     * actorId is only guaranteed in APIv3+ so use calculatedActorId.
     */
    val calculatedActorId: String?
        get() = if (actorId == null) {
            userId
        } else {
            actorId
        }

    enum class ActorType {
        DUMMY,
        EMAILS,
        GROUPS,
        GUESTS,
        USERS,
        CIRCLES
    }

    enum class ParticipantType {
        DUMMY,
        OWNER,
        MODERATOR,
        USER,
        GUEST,
        USER_FOLLOWING_LINK,
        GUEST_MODERATOR
    }

    object InCallFlags {
        const val DISCONNECTED = 0
        const val IN_CALL = 1
        const val WITH_AUDIO = 2
        const val WITH_VIDEO = 4
        const val WITH_PHONE = 8
    }
}
