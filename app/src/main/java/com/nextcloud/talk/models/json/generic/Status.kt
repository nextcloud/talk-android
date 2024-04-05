/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models.json.generic

import android.os.Parcelable
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import kotlinx.parcelize.Parcelize

@Parcelize
@JsonObject
data class Status(
    @JsonField(name = ["installed"])
    var installed: Boolean = false,

    @JsonField(name = ["maintenance"])
    var maintenance: Boolean = false,

    @JsonField(name = ["upgrade"])
    var needsUpgrade: Boolean = false,

    @JsonField(name = ["version"])
    var version: String? = null,

    @JsonField(name = ["versionstring"])
    var versionString: String? = null,

    @JsonField(name = ["edition"])
    var edition: String? = null,

    @JsonField(name = ["productname"])
    var productName: String? = null
) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this(false, false, false, null, null, null, null)
}
