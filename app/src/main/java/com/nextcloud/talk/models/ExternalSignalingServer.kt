/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017-2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models

import android.os.Parcelable
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import com.nextcloud.talk.models.json.signaling.settings.FederationSettings
import kotlinx.parcelize.Parcelize

@Parcelize
@JsonObject
data class ExternalSignalingServer(
    @JsonField(name = ["externalSignalingServer"])
    var externalSignalingServer: String? = null,
    @JsonField(name = ["externalSignalingTicket"])
    var externalSignalingTicket: String? = null,
    @JsonField(name = ["federation"])
    var federation: FederationSettings? = null
) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this(null, null, null)
}
