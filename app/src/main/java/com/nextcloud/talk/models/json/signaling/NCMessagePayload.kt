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
data class NCMessagePayload(
    @JsonField(name = ["type"])
    var type: String? = null,
    @JsonField(name = ["sdp"])
    var sdp: String? = null,
    @JsonField(name = ["nick"])
    var nick: String? = null,
    @JsonField(name = ["candidate"])
    var iceCandidate: NCIceCandidate? = null,
    @JsonField(name = ["name"])
    var name: String? = null,
    @JsonField(name = ["state"])
    var state: Boolean? = null,
    @JsonField(name = ["timestamp"])
    var timestamp: Long? = null,
    @JsonField(name = ["reaction"])
    var reaction: String? = null
) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this(null, null, null, null, null, null, null, null)
}
