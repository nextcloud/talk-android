/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.models.json.upcomingEvents

import android.os.Parcelable
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import kotlinx.parcelize.Parcelize

@Parcelize
@JsonObject
data class UpcomingEvent(
    @JsonField(name = ["uri"])
    var uri: String,
    @JsonField(name = ["recurrenceId"])
    var recurrenceId: Long?,
    @JsonField(name = ["calendarUri"])
    var calendarUri: String,
    @JsonField(name = ["start"])
    var start: Long?,
    @JsonField(name = ["summary"])
    var summary: String?,
    @JsonField(name = ["location"])
    var location: String?,
    @JsonField(name = ["calendarAppUrl"])
    var calendarAppUrl: String?
) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this("", null, "", null, null, null, null)
}
