/*
 * Nextcloud Talk application
 *
 * @author Álvaro Brey
 * Copyright (C) 2022 Álvaro Brey
 * Copyright (C) 2022 Nextcloud GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.models.json.unifiedsearch

import android.os.Parcelable
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import kotlinx.android.parcel.Parcelize

@Parcelize
@JsonObject
data class UnifiedSearchEntry(
    @JsonField(name = ["thumbnailUrl"])
    var thumbnailUrl: String?,
    @JsonField(name = ["title"])
    var title: String?,
    @JsonField(name = ["subline"])
    var subline: String?,
    @JsonField(name = ["resourceUrl"])
    var resourceUrl: String?,
    @JsonField(name = ["icon"])
    var icon: String?,
    @JsonField(name = ["rounded"])
    var rounded: Boolean?,
    @JsonField(name = ["attributes"])
    var attributes: Map<String, String>?,
) : Parcelable {
    constructor() : this(null, null, null, null, null, null, null)
}
