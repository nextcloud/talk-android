/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
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
package com.nextcloud.talk.models.json.push

import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonIgnore
import com.bluelinelabs.logansquare.annotation.JsonObject
import lombok.Data
import org.parceler.Parcel

@Data
@Parcel
@JsonObject
class DecryptedPushMessage {
    @JvmField
    @JsonField(name = ["app"])
    var app: String? = null
    @JvmField
    @JsonField(name = ["type"])
    var type: String? = null
    @JvmField
    @JsonField(name = ["subject"])
    var subject: String? = null
    @JvmField
    @JsonField(name = ["id"])
    var id: String? = null
    @JvmField
    @JsonField(name = ["nid"])
    var notificationId: Long? = null
    @JvmField
    @JsonField(name = ["delete"])
    var delete = false
    @JvmField
    @JsonField(name = ["delete-all"])
    var deleteAll = false
    @JvmField
    @JsonIgnore
    var notificationUser: NotificationUser? = null
    @JvmField
    @JsonIgnore
    var text: String? = null
    @JvmField
    @JsonIgnore
    var timestamp: Long = 0
}