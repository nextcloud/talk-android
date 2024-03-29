/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models.json.unifiedsearch

import android.os.Parcelable
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import com.nextcloud.talk.models.json.generic.GenericMeta
import kotlinx.parcelize.Parcelize

@Parcelize
@JsonObject
data class UnifiedSearchOCS(
    @JsonField(name = ["meta"])
    var meta: GenericMeta?,
    @JsonField(name = ["data"])
    var data: UnifiedSearchResponseData?
) : Parcelable {
    // Empty constructor needed for JsonObject
    constructor() : this(null, null)
}
