/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models.json.signaling.settings

import android.os.Parcelable
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Parcelize
@JsonObject
@Serializable
data class SignalingSettings(
    @JsonField(name = ["stunservers"])
    var stunServers: List<IceServer>? = null,
    @JsonField(name = ["turnservers"])
    var turnServers: List<IceServer>? = null,
    @JsonField(name = ["server"])
    var externalSignalingServer: String? = null,
    @JsonField(name = ["ticket"])
    var externalSignalingTicket: String? = null
) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this(null, null, null)
}
