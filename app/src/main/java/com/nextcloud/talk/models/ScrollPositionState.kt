/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.models

import android.os.Parcelable
import androidx.room.TypeConverter
import com.bluelinelabs.logansquare.LoganSquare
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Parcelize
@JsonObject
@Serializable
data class ScrollPositionState(
    @JsonField(name = ["position"]) var position: Int = 0,
    @JsonField(name = ["offset"]) var offset: Int = 0
) : Parcelable {
    constructor() : this(0, 0)
}

class ScrollPositionStateConverter {

    @TypeConverter
    fun fromStateToString(state: ScrollPositionState?): String =
        if (state == null) {
            ""
        } else {
            LoganSquare.serialize(state)
        }

    @TypeConverter
    fun fromStringToState(value: String): ScrollPositionState? =
        if (value.isBlank()) {
            null
        } else {
            LoganSquare.parse(value, ScrollPositionState::class.java)
        }
}
