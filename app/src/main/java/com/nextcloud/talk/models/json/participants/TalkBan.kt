/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.models.json.participants

import android.os.Parcelable
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import kotlinx.parcelize.Parcelize

@Parcelize
@JsonObject
data class TalkBan(
    @JsonField(name = ["id"])
    var id: String?,
    @JsonField(name = ["actorType"])
    var actorType: String?,
    @JsonField(name = ["actorId"])
    var actorId: String?,
    @JsonField(name = ["bannedType"])
    var bannedType: String?,
    @JsonField(name = ["bannedId"])
    var bannedId: String?,
    @JsonField(name = ["bannedTime"])
    var bannedTime: Int?,
    @JsonField(name = ["internalNote"])
    var internalNote: String?
) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this(null, null, null, null, null, null, null)
}
