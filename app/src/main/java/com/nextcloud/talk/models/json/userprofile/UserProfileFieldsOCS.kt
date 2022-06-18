/*
 * Nextcloud Talk application
 *
 * @author Tobias Kaminsky
 * @author Andy Scherzinger
 * Copyright (C) 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * Copyright (C) 2021 Tobias Kaminsky <tobias.kaminsky@nextcloud.com>
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
package com.nextcloud.talk.models.json.userprofile

import android.os.Parcelable
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import com.nextcloud.talk.models.json.generic.GenericMeta
import kotlinx.android.parcel.Parcelize
import java.util.ArrayList

@Parcelize
@JsonObject
data class UserProfileFieldsOCS(
    @JsonField(name = ["meta"])
    var meta: GenericMeta?,
    @JsonField(name = ["data"])
    var data: ArrayList<String>? = null
) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this(null, null)
}
