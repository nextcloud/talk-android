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

@Data
@Parcel
@JsonObject
@Parcelize
data class Capabilities(
        @JsonField(name = ["spreed"])
        var spreedCapability: SpreedCapability? = null,
        @JsonField(name = ["notifications"])
        var notificationsCapability: NotificationsCapability? = null,
        @JsonField(name = ["theming"])
        var themingCapability: ThemingCapability? = null
) : Parcelable {
    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o !is Capabilities) return false

        return (spreedCapability == o.spreedCapability &&
                notificationsCapability == o.notificationsCapability &&
                themingCapability == o.themingCapability)
    }

    override fun hashCode(): Int {
        return Objects.hash(spreedCapability, notificationsCapability, themingCapability)
    }
}