/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models.json.userprofile

import android.os.Parcelable
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import com.nextcloud.talk.models.json.converters.ScopeConverter
import com.nextcloud.talk.profile.ProfileActivity
import kotlinx.parcelize.Parcelize

@Parcelize
@JsonObject
data class UserProfileData(
    @JsonField(name = ["display-name"])
    var displayName: String?,

    @JsonField(name = ["displaynameScope"], typeConverter = ScopeConverter::class)
    var displayNameScope: Scope?,

    @JsonField(name = ["displayname"])
    var displayNameAlt: String?,

    @JsonField(name = ["id"])
    var userId: String?,

    @JsonField(name = ["phone"])
    var phone: String?,

    @JsonField(name = ["phoneScope"], typeConverter = ScopeConverter::class)
    var phoneScope: Scope?,

    @JsonField(name = ["email"])
    var email: String?,

    @JsonField(name = ["emailScope"], typeConverter = ScopeConverter::class)
    var emailScope: Scope?,

    @JsonField(name = ["address"])
    var address: String?,

    @JsonField(name = ["addressScope"], typeConverter = ScopeConverter::class)
    var addressScope: Scope?,

    @JsonField(name = ["twitter"])
    var twitter: String?,

    @JsonField(name = ["twitterScope"], typeConverter = ScopeConverter::class)
    var twitterScope: Scope?,

    @JsonField(name = ["website"])
    var website: String?,

    @JsonField(name = ["websiteScope"], typeConverter = ScopeConverter::class)
    var websiteScope: Scope?
) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this(null, null, null, null, null, null, null, null, null, null, null, null, null, null)

    fun getValueByField(field: ProfileActivity.Field?): String? =
        when (field) {
            ProfileActivity.Field.EMAIL -> email
            ProfileActivity.Field.DISPLAYNAME -> displayName
            ProfileActivity.Field.PHONE -> phone
            ProfileActivity.Field.ADDRESS -> address
            ProfileActivity.Field.WEBSITE -> website
            ProfileActivity.Field.TWITTER -> twitter
            else -> ""
        }

    fun getScopeByField(field: ProfileActivity.Field?): Scope? =
        when (field) {
            ProfileActivity.Field.EMAIL -> emailScope
            ProfileActivity.Field.DISPLAYNAME -> displayNameScope
            ProfileActivity.Field.PHONE -> phoneScope
            ProfileActivity.Field.ADDRESS -> addressScope
            ProfileActivity.Field.WEBSITE -> websiteScope
            ProfileActivity.Field.TWITTER -> twitterScope
            else -> null
        }
}
