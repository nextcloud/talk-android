/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Sowjanya Kota <sowjanya.kch@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.models.json.userAbsence

import android.os.Parcelable
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import kotlinx.parcelize.Parcelize

@Parcelize
@JsonObject
data class UserAbsenceData(
    @JsonField(name = ["id"])
    var id: String,
    @JsonField(name = ["userId"])
    var userId: String,
    @JsonField(name = ["startDate"])
    var startDate: Int,
    @JsonField(name = ["endDate"])
    var endDate: Int,
    @JsonField(name = ["shortMessage"])
    var shortMessage: String,
    @JsonField(name = ["message"])
    var message: String,
    @JsonField(name = ["replacementUserId"])
    var replacementUserId: String?,
    @JsonField(name = ["replacementUserDisplayName"])
    var replacementUserDisplayName: String?
) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() :
        this("", "", 0, 0, "", "", null, null)
}
