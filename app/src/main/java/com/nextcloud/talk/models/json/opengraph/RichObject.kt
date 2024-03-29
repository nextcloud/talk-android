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
data class RichObject(
    @JsonField(name = ["id"])
    var id: String,
    @JsonField(name = ["name"])
    var name: String,
    @JsonField(name = ["description"])
    var description: String? = null,
    @JsonField(name = ["thumb"])
    var thumb: String? = null,
    @JsonField(name = ["link"])
    var link: String? = null
) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this("", "", null, null)
}
