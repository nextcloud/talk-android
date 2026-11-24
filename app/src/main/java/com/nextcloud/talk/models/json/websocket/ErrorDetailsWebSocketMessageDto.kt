/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models.json.websocket

import android.os.Parcelable
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import kotlinx.parcelize.Parcelize

/**
 * Details of a signaling error. For "already_joined" the server names the room the session is already in.
 */
@Parcelize
@JsonObject
data class ErrorDetailsWebSocketMessageDto(
    @JsonField(name = ["room"])
    var room: RoomWebSocketMessageDto? = null
) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this(null)
}
