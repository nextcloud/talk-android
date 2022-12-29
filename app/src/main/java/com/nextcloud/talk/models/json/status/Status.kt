/*
 * Nextcloud Talk application
 *
 * @author Tim Krüger
 * Copyright (C) 2021 Tim Krüger <t@timkrueger.me>
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
package com.nextcloud.talk.models.json.status

import android.os.Parcelable
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import kotlinx.parcelize.Parcelize

@Parcelize
@JsonObject
data class Status(
    @JsonField(name = ["userId"])
    var userId: String?,
    @JsonField(name = ["message"])
    var message: String?,
    /* TODO Change to enum */
    @JsonField(name = ["messageId"])
    var messageId: String?,
    @JsonField(name = ["messageIsPredefined"])
    var messageIsPredefined: Boolean,
    @JsonField(name = ["icon"])
    var icon: String?,
    @JsonField(name = ["clearAt"])
    var clearAt: Long = 0,
    /* TODO Change to enum */
    @JsonField(name = ["status"])
    var status: String = "offline",
    @JsonField(name = ["statusIsUserDefined"])
    var statusIsUserDefined: Boolean
) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this(null, null, null, false, null, 0, "offline", false)
}
