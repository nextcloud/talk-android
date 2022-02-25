/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Tim Krüger
 * Copyright (C) 2022 Tim Krüger <t@timkrueger.me>
 * Copyright (C) 2017-2018 Mario Danic <mario@lovelyhq.com>
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
package com.nextcloud.talk.models.json.notifications

import android.os.Parcelable
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import com.nextcloud.talk.models.json.converters.LoganSquareJodaTimeConverter
import kotlinx.android.parcel.Parcelize
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
    var messageRichParameters: HashMap<String, HashMap<String, String>>?,
    @JsonField(name = ["link"])
    var link: String?,
    @JsonField(name = ["actions"])
    var actions: List<NotificationAction>?
) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null)
}
