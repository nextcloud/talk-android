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
    var websiteScope: Scope?,

    @JsonField(name = ["biography"])
    var biography: String?,

    @JsonField(name = ["biographyScope"], typeConverter = ScopeConverter::class)
    var biographyScope: Scope?,

    @JsonField(name = ["fediverse"])
    var fediverse: String?,

    @JsonField(name = ["fediverseScope"], typeConverter = ScopeConverter::class)
    var fediverseScope: Scope?,

    @JsonField(name = ["headline"])
    var headline: String?,

    @JsonField(name = ["headlineScope"], typeConverter = ScopeConverter::class)
    var headlineScope: Scope?,

    @JsonField(name = ["organisation"])
    var organisation: String?,

    @JsonField(name = ["organisationScope"], typeConverter = ScopeConverter::class)
    var organisationScope: Scope?,

    @JsonField(name = ["profile_enabled"])
    var profileEnabled: String?,

    @JsonField(name = ["profile_enabledScope"], typeConverter = ScopeConverter::class)
    var profileEnabledScope: Scope?,

    @JsonField(name = ["pronouns"])
    var pronouns: String?,

    @JsonField(name = ["pronounsScope"], typeConverter = ScopeConverter::class)
    var pronounsScope: Scope?,

    @JsonField(name = ["role"])
    var role: String?,

    @JsonField(name = ["roleScope"], typeConverter = ScopeConverter::class)
    var roleScope: Scope?,

    @JsonField(name = ["bluesky"])
    var bluesky: String?,

    @JsonField(name = ["blueskyScope"], typeConverter = ScopeConverter::class)
    var blueskyScope: Scope?
) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this(
        null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null
    )

    fun getValueByField(field: ProfileActivity.Field?): String? =
        when (field) {
            ProfileActivity.Field.EMAIL -> email
            ProfileActivity.Field.DISPLAYNAME -> displayName
            ProfileActivity.Field.PHONE -> phone
            ProfileActivity.Field.ADDRESS -> address
            ProfileActivity.Field.WEBSITE -> website
            ProfileActivity.Field.TWITTER -> twitter
            ProfileActivity.Field.BIOGRAPHY -> biography
            ProfileActivity.Field.FEDIVERSE -> fediverse
            ProfileActivity.Field.HEADLINE -> headline
            ProfileActivity.Field.ORGANISATION -> organisation
            ProfileActivity.Field.PROFILE_ENABLED -> profileEnabled
            ProfileActivity.Field.PRONOUNS -> pronouns
            ProfileActivity.Field.ROLE -> role
            ProfileActivity.Field.BLUESKY -> bluesky
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
            ProfileActivity.Field.BIOGRAPHY -> biographyScope
            ProfileActivity.Field.FEDIVERSE -> fediverseScope
            ProfileActivity.Field.HEADLINE -> headlineScope
            ProfileActivity.Field.ORGANISATION -> organisationScope
            ProfileActivity.Field.PROFILE_ENABLED -> profileEnabledScope
            ProfileActivity.Field.PRONOUNS -> pronounsScope
            ProfileActivity.Field.ROLE -> roleScope
            ProfileActivity.Field.BLUESKY -> blueskyScope
            else -> null
        }
}
