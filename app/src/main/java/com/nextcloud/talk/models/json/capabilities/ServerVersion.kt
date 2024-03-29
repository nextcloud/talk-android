/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models.json.capabilities

import android.os.Parcelable
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import kotlinx.parcelize.Parcelize

@Parcelize
@JsonObject
data class ServerVersion(
    @JsonField(name = ["major"])
    var major: Int = 0,
    @JsonField(name = ["minor"])
    var minor: Int = 0,
    @JsonField(name = ["micro"])
    var micro: Int = 0,
    @JsonField(name = ["string"])
    var versionString: String? = null
) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this(0, 0, 0, null)
}
