/*
 * Nextcloud Talk application
 *
 * @author Marcel Hibbe
 * Copyright (C) 2024 Marcel Hibbe <dev@mhibbe.de>
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
package com.nextcloud.talk.models.json.invitation

import android.os.Parcelable
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import kotlinx.parcelize.Parcelize

@Parcelize
@JsonObject
data class Invitation(
    @JsonField(name = ["id"])
    var id: Int = 0,
    @JsonField(name = ["state"])
    var state: Int = 0,
    @JsonField(name = ["localCloudId"])
    var localCloudId: String? = null,
    @JsonField(name = ["localToken"])
    var localToken: String? = null,
    @JsonField(name = ["remoteAttendeeId"])
    var remoteAttendeeId: Int = 0,
    @JsonField(name = ["remoteServerUrl"])
    var remoteServerUrl: String? = null,
    @JsonField(name = ["remoteToken"])
    var remoteToken: String? = null,
    @JsonField(name = ["roomName"])
    var roomName: String? = null,
    @JsonField(name = ["userId"])
    var userId: String? = null,
    @JsonField(name = ["inviterCloudId"])
    var inviterCloudId: String? = null,
    @JsonField(name = ["inviterDisplayName"])
    var inviterDisplayName: String? = null

) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this(0, 0, null, null, 0, null, null, null, null, null, null)
}
