/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models.json.invitation

import android.os.Parcelable
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import kotlinx.parcelize.Parcelize

@Parcelize
@JsonObject
data class Invitation(
    @JsonField(name = ["id"])
    var id: Int = 0,
    @JsonField(name = ["state"])
    var state: Int = 0,
    @JsonField(name = ["localCloudId"])
    var localCloudId: String? = null,
    @JsonField(name = ["localToken"])
    var localToken: String? = null,
    @JsonField(name = ["remoteAttendeeId"])
    var remoteAttendeeId: Int = 0,
    @JsonField(name = ["remoteServerUrl"])
    var remoteServerUrl: String? = null,
    @JsonField(name = ["remoteToken"])
    var remoteToken: String? = null,
    @JsonField(name = ["roomName"])
    var roomName: String? = null,
    @JsonField(name = ["userId"])
    var userId: String? = null,
    @JsonField(name = ["inviterCloudId"])
    var inviterCloudId: String? = null,
    @JsonField(name = ["inviterDisplayName"])
    var inviterDisplayName: String? = null

) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this(0, 0, null, null, 0, null, null, null, null, null, null)
}
