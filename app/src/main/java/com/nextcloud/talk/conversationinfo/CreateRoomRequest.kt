/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationinfo

import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject

@JsonObject
data class CreateRoomRequest(
    @JsonField(name = ["roomType"])
    var roomType: String,
    @JsonField(name = ["roomName"])
    var roomName: String? = null,
    @JsonField(name = ["objectType"])
    var objectType: String? = null,
    @JsonField(name = ["objectId"])
    var objectId: String? = null,
    @JsonField(name = ["password"])
    var password: String? = null,
    @JsonField(name = ["readOnly"])
    var readOnly: Int,
    @JsonField(name = ["listable"])
    var listable: Int,
    @JsonField(name = ["messageExpiration"])
    var messageExpiration: Int? = null,
    @JsonField(name = ["lobbyState"])
    var lobbyState: Int? = null,
    @JsonField(name = ["lobbyTimer"])
    var lobbyTimer: Int,
    @JsonField(name = ["sipEnabled"])
    var sipEnabled: Int,
    @JsonField(name = ["permissions"])
    var permissions: Int,
    @JsonField(name = ["recordingConsent"])
    var recordingConsent: Int,
    @JsonField(name = ["mentionPermissions"])
    var mentionPermissions: Int,
    @JsonField(name = ["description"])
    var description: String? = null,
    @JsonField(name = ["emoji"])
    var emoji: String? = null,
    @JsonField(name = ["avatarColor"])
    var avatarColor: String? = null,
    @JsonField(name = ["participants"])
    var participants: Participants? = null
) {
    constructor() : this(
        0.toString(),
        "",
        "",
        "",
        "",
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        "",
        "",
        "",
        Participants()
    )
}
