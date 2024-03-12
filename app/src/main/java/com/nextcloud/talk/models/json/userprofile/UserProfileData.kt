/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Andy Scherzinger
 * Copyright (C) 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * Copyright (C) 2017 Mario Danic <mario@lovelyhq.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.nextcloud.talk.models.json.userprofile

import android.os.Parcelable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.nextcloud.talk.models.json.converters.ScopeConverter
import com.nextcloud.talk.profile.ProfileActivity
import kotlinx.parcelize.Parcelize

@Parcelize
@Serializable
data class UserProfileData(
    @SerialName("display-name")
    var displayName: String?,

    @SerialName("displaynameScope", typeConverter = ScopeConverter::class)
    var displayNameScope: Scope?,

    @SerialName("displayname")
    var displayNameAlt: String?,

    @SerialName("id")
    var userId: String?,

    var phone: String?,

    @SerialName("phoneScope", typeConverter = ScopeConverter::class)
    var phoneScope: Scope?,

    var email: String?,

    @SerialName("emailScope", typeConverter = ScopeConverter::class)
    var emailScope: Scope?,

    var address: String?,

    @SerialName("addressScope", typeConverter = ScopeConverter::class)
    var addressScope: Scope?,

    var twitter: String?,

    @SerialName("twitterScope", typeConverter = ScopeConverter::class)
    var twitterScope: Scope?,

    var website: String?,

    @SerialName("websiteScope", typeConverter = ScopeConverter::class)
    var websiteScope: Scope?
) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this(null, null, null, null, null, null, null, null, null, null, null, null, null, null)

    fun getValueByField(field: ProfileActivity.Field?): String? {
        return when (field) {
            ProfileActivity.Field.EMAIL -> email
            ProfileActivity.Field.DISPLAYNAME -> displayName
            ProfileActivity.Field.PHONE -> phone
            ProfileActivity.Field.ADDRESS -> address
            ProfileActivity.Field.WEBSITE -> website
            ProfileActivity.Field.TWITTER -> twitter
            else -> ""
        }
    }

    fun getScopeByField(field: ProfileActivity.Field?): Scope? {
        return when (field) {
            ProfileActivity.Field.EMAIL -> emailScope
            ProfileActivity.Field.DISPLAYNAME -> displayNameScope
            ProfileActivity.Field.PHONE -> phoneScope
            ProfileActivity.Field.ADDRESS -> addressScope
            ProfileActivity.Field.WEBSITE -> websiteScope
            ProfileActivity.Field.TWITTER -> twitterScope
            else -> null
        }
    }
}
