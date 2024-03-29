/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models.json.signaling

import android.os.Parcelable
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import kotlinx.parcelize.Parcelize

@Parcelize
@JsonObject
data class NCSignalingMessage(
    @JsonField(name = ["from"])
    var from: String? = null,
    @JsonField(name = ["to"])
    var to: String? = null,
    @JsonField(name = ["type"])
    var type: String? = null,
    @JsonField(name = ["payload"])
    var payload: NCMessagePayload? = null,
    @JsonField(name = ["roomType"])
    var roomType: String? = null,
    @JsonField(name = ["sid"])
    var sid: String? = null,
    @JsonField(name = ["prefix"])
    var prefix: String? = null
) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this(null, null, null, null, null, null, null)
}
