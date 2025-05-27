/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models.json.participants

import android.os.Parcelable
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import com.nextcloud.talk.models.json.converters.EnumActorTypeConverter
import com.nextcloud.talk.models.json.converters.EnumParticipantTypeConverter
import kotlinx.parcelize.Parcelize

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

    @JsonField(name = ["internal"])
    var internal: Boolean? = null,

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

    @JsonField(name = ["invitedActorId"])
    var invitedActorId: String? = null,

    var selected: Boolean = false
) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this(
        null, null, null, null, null, null, null, null, null,
        0, null, ArrayList(0), 0, 0, null,
        null, null
    )

    /**
     * actorType is only guaranteed in APIv3+ so use calculatedActorType
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
        CIRCLES,
        FEDERATED,
        PHONES
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
