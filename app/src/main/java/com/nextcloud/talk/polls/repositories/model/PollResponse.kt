/*
 * Nextcloud Talk application
 *
 * @author Marcel Hibbe
 * Copyright (C) 2022 Marcel Hibbe <dev@mhibbe.de>
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
package com.nextcloud.talk.polls.repositories.model

import android.os.Parcelable
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import kotlinx.android.parcel.Parcelize

@Parcelize
@JsonObject
data class PollResponse(
    @JsonField(name = ["id"])
    var id: Int = 0,

    @JsonField(name = ["question"])
    var question: String? = null,

    @JsonField(name = ["options"])
    var options: ArrayList<String>? = null,

    @JsonField(name = ["votes"])
    var votes: ArrayList<Int>? = null,

    @JsonField(name = ["actorType"])
    var actorType: String? = null,

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
    var details: ArrayList<PollDetails>? = null,

) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this(0, null, null, null, null, null, null, 0, 0, 0, null)
}
