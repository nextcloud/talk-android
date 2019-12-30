/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2019 Mario Danic <mario@lovelyhq.com>
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

package com.nextcloud.talk.newarch.local.models

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nextcloud.talk.models.ExternalSignalingServer
import com.nextcloud.talk.models.json.capabilities.Capabilities
import com.nextcloud.talk.models.json.push.PushConfigurationState
import com.nextcloud.talk.newarch.local.models.other.UserStatus
import com.nextcloud.talk.utils.ApiUtils
import kotlinx.android.parcel.Parcelize
import java.util.*

@Parcelize
@Entity(tableName = "users")
data class UserNgEntity(
        @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") var id: Long?,
        @ColumnInfo(name = "user_id") var userId: String,
        @ColumnInfo(name = "username") var username: String,
        @ColumnInfo(name = "base_url") var baseUrl: String,
        @ColumnInfo(name = "token") var token: String? = null,
        @ColumnInfo(name = "display_name") var displayName: String? = null,
        @ColumnInfo(
                name = "push_configuration"
        ) var pushConfiguration: PushConfigurationState? = null,
        @ColumnInfo(name = "capabilities") var capabilities: Capabilities? = null,
        @ColumnInfo(name = "client_auth_cert") var clientCertificate: String? = null,
        @ColumnInfo(
                name = "external_signaling"
        ) var externalSignaling: ExternalSignalingServer? = null,
        @ColumnInfo(name = "status") var status: UserStatus? = null
) : Parcelable {

    fun hasSpreedFeatureCapability(capabilityName: String): Boolean {
        return capabilities?.spreedCapability?.features?.contains(capabilityName) ?: false

    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UserNgEntity

        if (userId != other.userId) return false
        //if (username != other.username) return false
        if (baseUrl != other.baseUrl) return false
        if (token != other.token) return false
        if (displayName != other.displayName) return false
        if (pushConfiguration != other.pushConfiguration) return false
        if (capabilities != other.capabilities) return false
        if (clientCertificate != other.clientCertificate) return false
        if (externalSignaling != other.externalSignaling) return false
        if (status != other.status) return false

        return true
    }

    override fun hashCode(): Int {
        return Objects.hash(userId, username)
    }
}

fun UserNgEntity.getCredentials() = ApiUtils.getCredentials(username, token)

fun UserNgEntity.hasSpreedFeatureCapability(capabilityName: String): Boolean {
    val capabilityExists = capabilities?.spreedCapability?.features?.contains(capabilityName)
    if (capabilityExists != null) {
        return capabilityExists
    } else {
        return false
    }
}

fun UserNgEntity.getMaxMessageLength(): Int {
    val maxLength = capabilities?.spreedCapability?.config?.get("chat")
            ?.get("max-length")
    return maxLength?.toInt() ?: 1000
}
