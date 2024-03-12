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
import kotlinx.serialization.SerialName
import com.nextcloud.talk.models.json.converters.KotlinxJodaTimeConverter
import kotlinx.serialization.Serializable
import com.nextcloud.talk.models.json.converters.LoganSquareJodaTimeConverter
import kotlinx.parcelize.Parcelize
import org.joda.time.DateTime

@Parcelize
@Serializable
data class Notification(
    var icon: String?,
    @SerialName("notification_id")
    var notificationId: Int?,
    var app: String?,
    var user: String?,
    @Serializable(with = KotlinxJodaTimeConverter::class)
    var datetime: DateTime?,
    @SerialName("object_type")
    var objectType: String?,
    @SerialName("object_id")
    var objectId: String?,
    var subject: String?,
    var subjectRich: String?,
    var subjectRichParameters: HashMap<String, HashMap<String, String>>?,
    var message: String?,
    var messageRich: String?,
    var messageRichParameters: HashMap<String?, HashMap<String?, String?>>?,
    var link: String?,
    var actions: List<NotificationAction>?
) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null)
}
