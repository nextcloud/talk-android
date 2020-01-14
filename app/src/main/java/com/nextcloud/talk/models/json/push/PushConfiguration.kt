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
import com.bluelinelabs.logansquare.annotation.JsonObject
import kotlinx.android.parcel.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import lombok.Data
import org.parceler.Parcel

@Parcel
@Data
@JsonObject
@Parcelize
@Serializable
data class PushConfiguration(
        @SerialName("pushToken")
        var pushToken: String? = null,
        @SerialName("deviceIdentifier")
        var deviceIdentifier: String? = null,
        @SerialName("deviceIdentifierSignature")
        var deviceIdentifierSignature: String? = null,
        @SerialName("userPublicKey")
        var userPublicKey: String? = null,
        @SerialName("state")
        var pushConfigurationStateWrapper: PushConfigurationStateWrapper? = null

) : Parcelable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PushConfiguration

        if (pushToken != other.pushToken) return false
        if (deviceIdentifier != other.deviceIdentifier) return false
        if (deviceIdentifierSignature != other.deviceIdentifierSignature) return false
        if (userPublicKey != other.userPublicKey) return false
        if (pushConfigurationStateWrapper != other.pushConfigurationStateWrapper) return false

        return true
    }

    override fun hashCode(): Int {
        var result = pushToken?.hashCode() ?: 0
        result = 31 * result + (deviceIdentifier?.hashCode() ?: 0)
        result = 31 * result + (deviceIdentifierSignature?.hashCode() ?: 0)
        result = 31 * result + (userPublicKey?.hashCode() ?: 0)
        result = 31 * result + pushConfigurationStateWrapper.hashCode()
        return result
    }
}

enum class PushConfigurationState {
    PENDING,
    SERVER_REGISTRATION_DONE,
    PROXY_REGISTRATION_DONE,
    FAILED_WITH_SERVER_REGISTRATION,
    FAILED_WITH_PROXY_REGISTRATION,
    PENDING_UNREGISTRATION,
    SERVER_UNREGISTRATION_DONE,
    PROXY_UNREGISTRATION_DONE
}

@Serializable
@Parcelize
data class PushConfigurationStateWrapper(
        @SerialName("pushConfigurationState")
        var pushConfigurationState: PushConfigurationState,
        @SerialName("reason")
        var reason: Int?
): Parcelable