/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017-2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models.json.websocket

import android.os.Parcelable
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import kotlinx.parcelize.Parcelize

@Parcelize
@JsonObject
class RoomWebSocketMessage(
    @JsonField(name = ["roomid"])
    var roomId: String? = null,
    @JsonField(name = ["sessionid"])
    var sessionId: String? = null,
    @JsonField(name = ["properties"])
    var roomPropertiesWebSocketMessage: RoomPropertiesWebSocketMessage? = null,
    @JsonField(name = ["federation"])
    var roomFederationWebSocketMessage: RoomFederationWebSocketMessage? = null
) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this(null, null, null, null)
}
