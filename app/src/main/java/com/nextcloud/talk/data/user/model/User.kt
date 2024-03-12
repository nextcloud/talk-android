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
import com.nextcloud.talk.models.ExternalSignalingServer
import com.nextcloud.talk.models.json.capabilities.Capabilities
import com.nextcloud.talk.models.json.capabilities.ServerVersion
import com.nextcloud.talk.models.json.push.PushConfigurationState
import com.nextcloud.talk.utils.ApiUtils
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import java.lang.Boolean.FALSE

@Parcelize
@Serializable
data class User(
    var id: Long? = null,
    var userId: String? = null,
    var username: String? = null,
    var baseUrl: String? = null,
    var token: String? = null,
    var displayName: String? = null,
    var pushConfigurationState: PushConfigurationState? = null,
    var capabilities: Capabilities? = null,
    var serverVersion: ServerVersion? = null,
    var clientCertificate: String? = null,
    var externalSignalingServer: ExternalSignalingServer? = null,
    var current: Boolean = FALSE,
    var scheduledForDeletion: Boolean = FALSE
) : Parcelable {

    fun getCredentials(): String = ApiUtils.getCredentials(username, token)!!

    fun hasSpreedFeatureCapability(capabilityName: String): Boolean {
        return capabilities?.spreedCapability?.features?.contains(capabilityName) ?: false
    }
}
