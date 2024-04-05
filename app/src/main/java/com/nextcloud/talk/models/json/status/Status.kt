/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2021 Tim Krüger <t@timkrueger.me>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models.json.status

import android.os.Parcelable
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import kotlinx.parcelize.Parcelize

@Parcelize
@JsonObject
data class Status(
    @JsonField(name = ["userId"])
    var userId: String?,
    @JsonField(name = ["message"])
    var message: String?,
    /* TODO Change to enum */
    @JsonField(name = ["messageId"])
    var messageId: String?,
    @JsonField(name = ["messageIsPredefined"])
    var messageIsPredefined: Boolean,
    @JsonField(name = ["icon"])
    var icon: String?,
    @JsonField(name = ["clearAt"])
    var clearAt: Long = 0,
    /* TODO Change to enum */
    @JsonField(name = ["status"])
    var status: String = "offline",
    @JsonField(name = ["statusIsUserDefined"])
    var statusIsUserDefined: Boolean
) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this(null, null, null, false, null, 0, "offline", false)
}
