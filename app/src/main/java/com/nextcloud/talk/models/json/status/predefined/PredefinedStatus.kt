/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models.json.status.predefined

import android.os.Parcelable
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import com.nextcloud.talk.models.json.status.ClearAt
import kotlinx.parcelize.Parcelize

@Parcelize
@JsonObject
data class PredefinedStatus(
    @JsonField(name = ["id"])
    var id: String,
    @JsonField(name = ["icon"])
    var icon: String?,
    @JsonField(name = ["message"])
    var message: String,
    @JsonField(name = ["clearAt"])
    var clearAt: ClearAt?
) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this("id", "icon", "message", null)
}
