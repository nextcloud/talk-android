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
    var attributes: Map<String, String>?
) : Parcelable {
    constructor() : this(null, null, null, null, null, null, null)
}
