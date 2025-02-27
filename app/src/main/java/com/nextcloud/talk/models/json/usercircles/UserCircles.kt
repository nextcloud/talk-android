/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.models.json.usercircles

import android.os.Parcelable
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import kotlinx.parcelize.Parcelize

@Parcelize
@JsonObject
data class UserCircles(
    @JsonField(name = ["id"])
    var id: String? = null,
    @JsonField(name = ["name"])
    var name: String? = null,
    @JsonField(name = ["displayName"])
    var displayName: String? = null,
    @JsonField(name = ["sanitizedName"])
    var sanitizedName: String? = null,
    @JsonField(name = ["source"])
    var source: Int = 0,
    @JsonField(name = ["population"])
    var population: Int = 0,
    @JsonField(name = ["config"])
    var config: Int = 0,
    @JsonField(name = ["description"])
    var description: String? = null,
    @JsonField(name = ["url"])
    var url: String? = null,
    @JsonField(name = ["creation"])
    var creation: Int = 0,
    @JsonField(name = ["initiator"])
    var initiator: String? = null

) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this(null, null, null, null, 0, 0, 0, null, null, 0, null)
}
