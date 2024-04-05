/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Tim Krüger <t@timkrueger.me>
 * SPDX-FileCopyrightText: 2017-2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models.json.notifications

import android.os.Parcelable
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import com.nextcloud.talk.models.json.converters.LoganSquareJodaTimeConverter
import kotlinx.parcelize.Parcelize
import org.joda.time.DateTime

@Parcelize
@JsonObject
data class Notification(
    @JsonField(name = ["icon"])
    var icon: String?,
    @JsonField(name = ["notification_id"])
    var notificationId: Int?,
    @JsonField(name = ["app"])
    var app: String?,
    @JsonField(name = ["user"])
    var user: String?,
    @JsonField(name = ["datetime"], typeConverter = LoganSquareJodaTimeConverter::class)
    var datetime: DateTime?,
    @JsonField(name = ["object_type"])
    var objectType: String?,
    @JsonField(name = ["object_id"])
    var objectId: String?,
    @JsonField(name = ["subject"])
    var subject: String?,
    @JsonField(name = ["subjectRich"])
    var subjectRich: String?,
    @JsonField(name = ["subjectRichParameters"])
    var subjectRichParameters: HashMap<String, HashMap<String, String>>?,
    @JsonField(name = ["message"])
    var message: String?,
    @JsonField(name = ["messageRich"])
    var messageRich: String?,
    @JsonField(name = ["messageRichParameters"])
    var messageRichParameters: HashMap<String?, HashMap<String?, String?>>?,
    @JsonField(name = ["link"])
    var link: String?,
    @JsonField(name = ["actions"])
    var actions: List<NotificationAction>?
) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null)
}
