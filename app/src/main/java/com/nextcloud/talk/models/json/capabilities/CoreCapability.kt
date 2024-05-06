/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models.json.capabilities

import android.os.Parcelable
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Parcelize
@JsonObject
@Serializable
data class CoreCapability(
    @JsonField(name = ["pollinterval"])
    var pollInterval: Int?,
    @JsonField(name = ["webdav-root"])
    var webdavRoot: String?,
    @JsonField(name = ["reference-api"])
    var referenceApi: String?,
    @JsonField(name = ["reference-regex"])
    var referenceRegex: String?,
    @JsonField(name = ["mod-rewrite-working"])
    var modRewriteWorking: Boolean?
) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this(null, null, null, null, null)
}
