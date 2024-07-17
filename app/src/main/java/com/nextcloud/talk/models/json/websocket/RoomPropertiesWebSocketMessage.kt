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
import com.nextcloud.talk.models.json.conversations.ConversationEnums
import com.nextcloud.talk.models.json.converters.EnumRoomTypeConverter
import kotlinx.parcelize.Parcelize

@Parcelize
@JsonObject
data class RoomPropertiesWebSocketMessage(
    @JsonField(name = ["name"])
    var name: String? = null,
    @JsonField(name = ["type"], typeConverter = EnumRoomTypeConverter::class)
    var roomType: ConversationEnums.ConversationType? = null
) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this(null, null)
}
