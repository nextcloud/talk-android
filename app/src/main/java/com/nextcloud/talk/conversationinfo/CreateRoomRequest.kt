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
    @JsonField(name = ["invite"])
    var invite: String? = null,
    @JsonField(name = ["source"])
    var source: String? = null,
    @JsonField(name = ["preset"])
    var preset: String? = null,
    @JsonField(name = ["objectType"])
    var objectType: String? = null,
    @JsonField(name = ["objectId"])
    var objectId: String? = null,
    @JsonField(name = ["password"])
    var password: String? = null,
    @JsonField(name = ["readOnly"])
    var readOnly: Int? = null,
    @JsonField(name = ["listable"])
    var listable: Int? = null,
    @JsonField(name = ["messageExpiration"])
    var messageExpiration: Int? = null,
    @JsonField(name = ["lobbyState"])
    var lobbyState: Int? = null,
    @JsonField(name = ["lobbyTimer"])
    var lobbyTimer: Int? = null,
    @JsonField(name = ["sipEnabled"])
    var sipEnabled: Int? = null,
    @JsonField(name = ["permissions"])
    var permissions: Int? = null,
    @JsonField(name = ["recordingConsent"])
    var recordingConsent: Int? = null,
    @JsonField(name = ["mentionPermissions"])
    var mentionPermissions: Int? = null,
    @JsonField(name = ["description"])
    var description: String? = null,
    @JsonField(name = ["emoji"])
    var emoji: String? = null,
    @JsonField(name = ["avatarColor"])
    var avatarColor: String? = null,
    @JsonField(name = ["participants"])
    var participants: Participants? = null
) {
    constructor() : this(roomType = "2")
}
