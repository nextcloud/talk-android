/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.models.json.capabilities

import android.os.Parcelable
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import kotlinx.parcelize.Parcelize

@Parcelize
@JsonObject
data class PasswordAccount(
    @JsonField(name = ["minLength"])
    var minLength: Int,
    @JsonField(name = ["enforceHaveIBeenPwned"])
    var enforceHaveIBeenPwned: Boolean,
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
    constructor() : this(0, false, false, false, false, false)
}
