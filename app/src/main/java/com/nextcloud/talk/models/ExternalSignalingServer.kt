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

package com.nextcloud.talk.models

import android.os.Parcelable
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import kotlinx.android.parcel.Parcelize
import lombok.Data
import org.parceler.Parcel
import java.util.*

@Data
@Parcel
@JsonObject
@Parcelize
data class ExternalSignalingServer(
        @JsonField(name = ["externalSignalingServer"])
        var externalSignalingServer: String? = null,
        @JsonField(name = ["externalSignalingTicket"])
        var externalSignalingTicket: String? = null
) : Parcelable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ExternalSignalingServer

        if (externalSignalingServer != other.externalSignalingServer) return false
        //if (externalSignalingTicket != other.externalSignalingTicket) return false

        return true
    }

    override fun hashCode(): Int {
        return Objects.hash(externalSignalingServer)
        /*var result = externalSignalingServer?.hashCode() ?: 0
        result = 31 * result + (externalSignalingTicket?.hashCode() ?: 0)
        return result*/
    }
}
