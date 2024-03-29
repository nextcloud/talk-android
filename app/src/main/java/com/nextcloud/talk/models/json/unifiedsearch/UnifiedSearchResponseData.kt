/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Álvaro Brey <alvaro@alvarobrey.com>
 * SPDX-FileCopyrightText: 2022 Nextcloud GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models.json.unifiedsearch

import android.os.Parcelable
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import kotlinx.parcelize.Parcelize

@Parcelize
@JsonObject
data class UnifiedSearchResponseData(
    @JsonField(name = ["name"])
    var name: String?,
    @JsonField(name = ["isPaginated"])
    var paginated: Boolean?,
    @JsonField(name = ["entries"])
    var entries: List<UnifiedSearchEntry>?,
    @JsonField(name = ["cursor"])
    var cursor: Int?
) : Parcelable {
    // empty constructor needed for JsonObject
    constructor() : this(null, null, null, null)
}
