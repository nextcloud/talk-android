/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Tim Krüger <t@timkrueger.me>
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
data class ThemingCapability(
    @JsonField(name = ["name"])
    var name: String?,
    @JsonField(name = ["url"])
    var url: String?,
    @JsonField(name = ["slogan"])
    var slogan: String?,
    @JsonField(name = ["color"])
    var color: String?,
    @JsonField(name = ["color-text"])
    var colorText: String?,
    @JsonField(name = ["color-element"])
    var colorElement: String?,
    @JsonField(name = ["color-element-bright"])
    var colorElementBright: String?,
    @JsonField(name = ["color-element-dark"])
    var colorElementDark: String?,
    @JsonField(name = ["logo"])
    var logo: String?,
    @JsonField(name = ["background"])
    var background: String?,
    @JsonField(name = ["background-plain"])
    var backgroundPlain: Boolean?,
    @JsonField(name = ["background-default"])
    var backgroundDefault: Boolean?
) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this(null, null, null, null, null, null, null, null, null, null, null, null)
}
