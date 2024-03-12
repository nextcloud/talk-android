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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.parcelize.Parcelize

@Parcelize
@Serializable
data class Status(
    @SerialName("userId")
    var userId: String?,
    @SerialName("message")
    var message: String?,
    /* TODO Change to enum */
    @SerialName("messageId")
    var messageId: String?,
    @SerialName("messageIsPredefined")
    var messageIsPredefined: Boolean,
    @SerialName("icon")
    var icon: String?,
    @SerialName("clearAt")
    var clearAt: Long = 0,
    /* TODO Change to enum */
    @SerialName("status")
    var status: String = "offline",
    @SerialName("statusIsUserDefined")
    var statusIsUserDefined: Boolean
) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this(null, null, null, false, null, 0, "offline", false)
}
