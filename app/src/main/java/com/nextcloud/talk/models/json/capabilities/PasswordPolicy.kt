/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Sowjanya Kota <sowjanya.kch@hgmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.models.json.capabilities

import android.os.Parcelable
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import kotlinx.parcelize.Parcelize

@Parcelize
@JsonObject
data class PasswordPolicy(
    @JsonField(name = ["api"])
    var api: PasswordApi?,
    @JsonField(name = ["policies"])
    var policies: PasswordPolicies?,
    @JsonField(name = ["minLength"])
    var minLength: Int,
    @JsonField(name = ["enforceNonCommonPassword"])
    var enforceNonCommonPassword: Boolean,
    @JsonField(name = ["enforceNumericCharacters"])
    var enforceNumericCharacters: Boolean,
    @JsonField(name = ["enforceSpecialCharacters"])
    var enforceSpecialCharacters: Boolean,
    @JsonField(name = ["enforceUpperLowerCase"])
    var enforceUpperLowerCase: Boolean
) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this(null, null, 0, false, false, false, false)
}
