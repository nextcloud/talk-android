/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.models.json.profile

import android.os.Parcelable
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import kotlinx.parcelize.Parcelize

@Parcelize
@JsonObject
data class Profile(
    @JsonField(name = ["userId"])
    var userId: String? = null,
    @JsonField(name = ["address"])
    var address: String? = null,
    @JsonField(name = ["biography"])
    var biography: Int? = null,
    @JsonField(name = ["displayname"])
    var displayName: Int? = null,
    @JsonField(name = ["headline"])
    var headline: String? = null,
    @JsonField(name = ["isUserAvatarVisible"])
    var isUserAvatarVisible: Boolean = false,
    @JsonField(name = ["organization"])
    var organization: String? = null,
    @JsonField(name = ["pronouns"])
    var pronouns: String? = null,
    @JsonField(name = ["role"])
    var role: String? = null,
    @JsonField(name = ["actions"])
    var actions: List<CoreProfileAction>? = null,
    @JsonField(name = ["timezone"])
    var timezone: String? = null,
    @JsonField(name = ["timezoneOffset"])
    var timezoneOffset: Int? = null,

) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this(null, null, null, null)
}
