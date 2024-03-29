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
import com.nextcloud.talk.models.json.signaling.NCSignalingMessage
import kotlinx.parcelize.Parcelize

@Parcelize
@JsonObject
data class CallWebSocketMessage(
    @JsonField(name = ["recipient"])
    var recipientWebSocketMessage: ActorWebSocketMessage? = null,
    @JsonField(name = ["sender"])
    var senderWebSocketMessage: ActorWebSocketMessage? = null,
    @JsonField(name = ["data"])
    var ncSignalingMessage: NCSignalingMessage? = null
) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this(null, null, null)
}
