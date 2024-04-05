/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models.json.reactions

import android.os.Parcelable
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import com.nextcloud.talk.models.json.converters.EnumReactionActorTypeConverter
import kotlinx.parcelize.Parcelize

@Parcelize
@JsonObject
data class ReactionVoter(
    @JsonField(name = ["actorType"], typeConverter = EnumReactionActorTypeConverter::class)
    var actorType: ReactionActorType?,
    @JsonField(name = ["actorId"])
    var actorId: String?,
    @JsonField(name = ["actorDisplayName"])
    var actorDisplayName: String?,
    @JsonField(name = ["timestamp"])
    var timestamp: Long = 0
) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this(null, null, null, 0)

    enum class ReactionActorType {
        DUMMY,
        GUESTS,
        USERS
    }
}
