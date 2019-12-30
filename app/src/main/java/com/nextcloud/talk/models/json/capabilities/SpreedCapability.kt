/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2018 Mario Danic <mario@lovelyhq.com>
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
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import kotlinx.android.parcel.Parcelize
import lombok.Data
import org.parceler.Parcel
import java.util.*

@Parcel
@Data
@JsonObject
@Parcelize
data class SpreedCapability(
        @JsonField(name = ["features"])
        var features: List<String>? = null,
        @JsonField(name = ["config"])
        var config: HashMap<String, HashMap<String, String>>? = null
) : Parcelable {

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o !is SpreedCapability) return false
        return features == o.features &&
                config == o.config
    }

    override fun hashCode(): Int {
        return Objects.hash(features, config)
    }
}