/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2020 Mario Danic <mario@lovelyhq.com>
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

package com.nextcloud.talk.data.user.model

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nextcloud.talk.models.json.capabilities.Capabilities
import com.nextcloud.talk.models.json.push.PushConfigurationState
import com.nextcloud.talk.models.json.signaling.settings.SignalingSettings
import com.nextcloud.talk.utils.ApiUtils
import kotlinx.android.parcel.Parcelize
import kotlinx.serialization.Serializable
import java.lang.Boolean.FALSE

@Parcelize
@Serializable
@Entity(tableName = "users")
data class UserNgEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") var id: Long = 0,
    @ColumnInfo(name = "user_id") var userId: String,
    @ColumnInfo(name = "username") var username: String,
    @ColumnInfo(name = "base_url") var baseUrl: String,
    @ColumnInfo(name = "token") var token: String? = null,
    @ColumnInfo(name = "display_name") var displayName: String? = null,
    @ColumnInfo(name = "push_configuration_state") var pushConfiguration: PushConfigurationState? = null,
    @ColumnInfo(name = "capabilities") var capabilities: Capabilities? = null,
    @ColumnInfo(name = "client_certificate") var clientCertificate: String? = null,
    @ColumnInfo(name = "external_signaling_server") var externalSignalingServer: SignalingSettings? = null,
    @ColumnInfo(name = "current") var current: Boolean = FALSE,
    @ColumnInfo(name = "scheduled_for_deletion") var scheduledForDeletion: Boolean = FALSE,
) : Parcelable {

    fun hasSpreedFeatureCapability(capabilityName: String): Boolean {
        return capabilities?.spreedCapability?.features?.contains(capabilityName) ?: false

    }
}

fun UserNgEntity.canUserCreateGroupConversations(): Boolean {
    val canCreateValue = capabilities?.spreedCapability?.config?.get("conversations")?.get("can-create")
    canCreateValue?.let {
        return it.toBoolean()
    }
    return true
}

fun UserNgEntity.toUser(): User {
    return User(this.id, this.userId, this.username, this.baseUrl, this.token, this.displayName, this
        .pushConfiguration, this.capabilities, this.clientCertificate, this.externalSignalingServer, this.current,
        this.scheduledForDeletion)
}

fun UserNgEntity.getCredentials(): String = ApiUtils.getCredentials(username, token)
