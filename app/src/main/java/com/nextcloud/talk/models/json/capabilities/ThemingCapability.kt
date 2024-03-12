/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Tim Krüger
 * Copyright (C) 2022 Tim Krüger <t@timkrueger.me>
 * Copyright (C) 2017-2019 Mario Danic <mario@lovelyhq.com>
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
package com.nextcloud.talk.models.json.capabilities

import android.os.Parcelable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.parcelize.Parcelize

@Parcelize
@Serializable
data class ThemingCapability(
    var name: String?,
    var url: String?,
    var slogan: String?,
    var color: String?,
    @SerialName("color-text")
    var colorText: String?,
    @SerialName("color-element")
    var colorElement: String?,
    @SerialName("color-element-bright")
    var colorElementBright: String?,
    @SerialName("color-element-dark")
    var colorElementDark: String?,
    var logo: String?,
    @SerialName("background")
    var background: String?,
    @SerialName("background-plain")
    var backgroundPlain: Boolean?,
    @SerialName("background-default")
    var backgroundDefault: Boolean?
) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this(null, null, null, null, null, null, null, null, null, null, null, null)
}
