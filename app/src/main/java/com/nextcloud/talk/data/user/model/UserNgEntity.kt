/*
 * Nextcloud Talk application
 *
 * @author Andy Scherzinger
 * @author Mario Danic
 * Copyright (C) 2022 Andy Scherzinger <infoi@andy-scherzinger.de>
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
import com.nextcloud.talk.models.ExternalSignalingServer
import com.nextcloud.talk.models.json.capabilities.Capabilities
import com.nextcloud.talk.models.json.push.PushConfigurationState
import com.nextcloud.talk.utils.ApiUtils
import kotlinx.android.parcel.Parcelize
import kotlinx.serialization.Serializable
import java.lang.Boolean.FALSE

@Parcelize
@Serializable
@Entity(tableName = "User")
data class UserNgEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") var id: Long = 0,
    @ColumnInfo(name = "userId") var userId: String? = null,
    @ColumnInfo(name = "username") var username: String? = null,
    @ColumnInfo(name = "baseUrl") var baseUrl: String? = null,
    @ColumnInfo(name = "token") var token: String? = null,
    @ColumnInfo(name = "displayName") var displayName: String? = null,
    @ColumnInfo(name = "pushConfigurationState") var pushConfigurationState: PushConfigurationState? = null,
    @ColumnInfo(name = "capabilities") var capabilities: Capabilities? = null,
    @ColumnInfo(name = "clientCertificate") var clientCertificate: String? = null,
    @ColumnInfo(name = "externalSignalingServer") var externalSignalingServer: ExternalSignalingServer? = null,
    @ColumnInfo(name = "current") var current: Boolean = FALSE,
    @ColumnInfo(name = "scheduledForDeletion") var scheduledForDeletion: Boolean = FALSE,
) : Parcelable {

    fun hasSpreedFeatureCapability(capabilityName: String): Boolean {
        return capabilities?.spreedCapability?.features?.contains(capabilityName) ?: false
    }

    fun canUserCreateGroupConversations(): Boolean {
        val canCreateValue = capabilities?.spreedCapability?.config?.get("conversations")?.get("can-create")
        canCreateValue?.let {
            return it.toBoolean()
        }
        return true
    }

    fun getCredentials(): String = ApiUtils.getCredentials(username, token)
}
