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

import android.os.Parcelable
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonIgnore
import com.bluelinelabs.logansquare.annotation.JsonObject
import kotlinx.parcelize.Parcelize

@Parcelize
@JsonObject
data class DecryptedPushMessage(
    @JsonField(name = ["app"])
    var app: String?,

    @JsonField(name = ["type"])
    var type: String?,

    @JsonField(name = ["subject"])
    var subject: String,

    @JsonField(name = ["id"])
    var id: String?,

    @JsonField(name = ["nid"])
    var notificationId: Long?,

    @JsonField(name = ["nids"])
    var notificationIds: LongArray?,

    @JsonField(name = ["delete"])
    var delete: Boolean,

    @JsonField(name = ["delete-all"])
    var deleteAll: Boolean,

    @JsonField(name = ["delete-multiple"])
    var deleteMultiple: Boolean,

    @JsonIgnore
    var notificationUser: NotificationUser?,

    @JsonIgnore
    var text: String?,

    @JsonIgnore
    var timestamp: Long,

    @JsonIgnore
    var objectId: String?
) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this(null, null, "", null, 0, null, false, false, false, null, null, 0, null)

    @Suppress("Detekt.ComplexMethod")
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DecryptedPushMessage

        if (app != other.app) return false
        if (type != other.type) return false
        if (subject != other.subject) return false
        if (id != other.id) return false
        if (notificationId != other.notificationId) return false
        if (notificationIds != null) {
            if (other.notificationIds == null) return false
            if (!notificationIds.contentEquals(other.notificationIds)) return false
        } else if (other.notificationIds != null) return false
        if (delete != other.delete) return false
        if (deleteAll != other.deleteAll) return false
        if (deleteMultiple != other.deleteMultiple) return false
        if (notificationUser != other.notificationUser) return false
        if (text != other.text) return false
        if (timestamp != other.timestamp) return false
        if (objectId != other.objectId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = app?.hashCode() ?: 0
        result = 31 * result + (type?.hashCode() ?: 0)
        result = 31 * result + (subject?.hashCode() ?: 0)
        result = 31 * result + (id?.hashCode() ?: 0)
        result = 31 * result + (notificationId?.hashCode() ?: 0)
        result = 31 * result + (notificationIds?.contentHashCode() ?: 0)
        result = 31 * result + (delete?.hashCode() ?: 0)
        result = 31 * result + (deleteAll?.hashCode() ?: 0)
        result = 31 * result + (deleteMultiple?.hashCode() ?: 0)
        result = 31 * result + (notificationUser?.hashCode() ?: 0)
        result = 31 * result + (text?.hashCode() ?: 0)
        result = 31 * result + (timestamp?.hashCode() ?: 0)
        result = 31 * result + (objectId?.hashCode() ?: 0)
        return result
    }
}
