/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
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
data class ThemingCapability(
    @JsonField(name = ["name"])
    var name: String? = null,
    @JsonField(name = ["url"])
    var url: String? = null,
    @JsonField(name = ["slogan"])
    var slogan: String? = null,
    @JsonField(name = ["color"])
    var color: String? = null,
    @JsonField(name = ["color-text"])
    var colorText: String? = null,
    @JsonField(name = ["color-element"])
    var colorElement: String? = null,
    @JsonField(name = ["logo"])
    var logo: String? = null,
    @JsonField(name = ["background"])
    var background: String? = null,
    @JsonField(name = ["background-plain"])
    var backgroundPlain: Boolean = false,
    @JsonField(name = ["background-default"])
    var backgroundDefault: Boolean = false
): Parcelable {

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o !is ThemingCapability) return false
        val that = o
        return backgroundPlain == that.backgroundPlain && backgroundDefault == that.backgroundDefault &&
                name == that.name &&
                url == that.url &&
                slogan == that.slogan &&
                color == that.color &&
                colorText == that.colorText &&
                colorElement == that.colorElement &&
                logo == that.logo &&
                background == that.background
    }

    override fun hashCode(): Int {
        return Objects.hash(name, url, slogan, color, colorText, colorElement, logo, background, backgroundPlain, backgroundDefault)
    }
}