/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017 Mario Danic <mario@lovelyhq.com>
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
package com.nextcloud.talk.models.json.signaling.settings

import android.os.Parcelable
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import kotlinx.android.parcel.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import lombok.Data

@Data
@JsonObject
@Parcelize
@Serializable
data class IceServer @JvmOverloads constructor(
        @JvmField
        @JsonField(name = ["url"])
        @SerialName("url")
        var url: String? = null,
        @JvmField
        @JsonField(name = ["urls"])
        @SerialName("urls")
        var urls: List<String>? = null,
        @JvmField
        @JsonField(name = ["username"])
        @SerialName("username")
        var username: String? = null,
        @JvmField
        @JsonField(name = ["credential"])
        @SerialName("credential")
        var credential: String? = null
) : Parcelable