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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.parcelize.Parcelize

@Parcelize
@Serializable
data class PollResponse(
    @SerialName("id")
    var id: String,

    @SerialName("question")
    var question: String? = null,

    @SerialName("options")
    var options: ArrayList<String>? = null,

    @SerialName("votes")
    var votes: Map<String, Int>? = null,

    @SerialName("actorType")
    var actorType: String? = null,

    @SerialName("actorId")
    var actorId: String? = null,

    @SerialName("actorDisplayName")
    var actorDisplayName: String? = null,

    @SerialName("status")
    var status: Int = 0,

    @SerialName("resultMode")
    var resultMode: Int = 0,

    @SerialName("maxVotes")
    var maxVotes: Int = 0,

    @SerialName("votedSelf")
    var votedSelf: ArrayList<Int>? = null,

    @SerialName("numVoters")
    var numVoters: Int = 0,

    @SerialName("details")
    var details: ArrayList<PollDetailsResponse>? = null
) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this("id", null, null, null, null, null, null, 0, 0, 0, null, 0, null)
}
