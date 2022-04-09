/*
 *
 *   Nextcloud Talk application
 *
 *   @author Marcel Hibbe
 *   Copyright (C) 2022 Marcel Hibbe <dev@mhibbe.de>
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
package com.nextcloud.talk.models.json.reactions

import android.os.Parcelable
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import com.nextcloud.talk.models.json.converters.EnumReactionActorTypeConverter
import kotlinx.android.parcel.Parcelize

@Parcelize
@JsonObject
data class ReactionVoter(
    @JsonField(name = ["actorType"], typeConverter = EnumReactionActorTypeConverter::class)
    var actorType: ReactionActorType?,
    @JsonField(name = ["actorId"])
    var actorId: String?,
    @JsonField(name = ["actorDisplayName"])
    var actorDisplayName: String?,
    @JsonField(name = ["timestamp"])
    var timestamp: Long = 0
) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this(null, null, null, 0)

    enum class ReactionActorType {
        DUMMY, GUESTS, USERS
    }
}
