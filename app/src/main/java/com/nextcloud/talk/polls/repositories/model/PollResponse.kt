/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.polls.repositories.model

import android.os.Parcelable
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import com.nextcloud.talk.models.json.converters.EnumActorTypeConverter
import com.nextcloud.talk.models.json.participants.Participant
import kotlinx.parcelize.Parcelize

@Parcelize
@JsonObject
data class PollResponse(
    @JsonField(name = ["id"])
    var id: String,

    @JsonField(name = ["question"])
    var question: String? = null,

    @JsonField(name = ["options"])
    var options: ArrayList<String>? = null,

    @JsonField(name = ["votes"])
    var votes: Map<String, Int>? = null,

    @JsonField(name = ["actorType"], typeConverter = EnumActorTypeConverter::class)
    var actorType: Participant.ActorType? = null,

    @JsonField(name = ["actorId"])
    var actorId: String? = null,

    @JsonField(name = ["actorDisplayName"])
    var actorDisplayName: String? = null,

    @JsonField(name = ["status"])
    var status: Int = 0,

    @JsonField(name = ["resultMode"])
    var resultMode: Int = 0,

    @JsonField(name = ["maxVotes"])
    var maxVotes: Int = 0,

    @JsonField(name = ["votedSelf"])
    var votedSelf: ArrayList<Int>? = null,

    @JsonField(name = ["numVoters"])
    var numVoters: Int = 0,

    @JsonField(name = ["details"])
    var details: ArrayList<PollDetailsResponse>? = null
) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this("id", null, null, null, null, null, null, 0, 0, 0, null, 0, null)
}
