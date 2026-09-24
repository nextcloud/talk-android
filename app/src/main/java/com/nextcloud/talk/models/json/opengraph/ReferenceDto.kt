/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models.json.opengraph

import android.os.Parcelable
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import kotlinx.parcelize.Parcelize

@Parcelize
@JsonObject
data class ReferenceDto(
    @JsonField(name = ["richObjectType"])
    var richObjectType: String? = null,
    @JsonField(name = ["richObject"])
    var richObject: RichObjectDto? = null,
    @JsonField(name = ["openGraphObject"])
    var openGraphObject: OpenGraphObjectDto? = null,
    @JsonField(name = ["accessible"])
    var accessible: Boolean
) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this(null, null, null, false)
}
