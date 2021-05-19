/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
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
package com.nextcloud.talk.models.database

import android.os.Parcelable
import android.util.Log
import com.bluelinelabs.logansquare.LoganSquare
import com.nextcloud.talk.models.json.capabilities.Capabilities
import io.requery.Entity
import io.requery.Generated
import io.requery.Key
import io.requery.Persistable
import java.io.IOException
import java.io.Serializable

@Entity
interface User : Parcelable, Persistable, Serializable {
    @get:Generated
    @get:Key
    val id: Long
    val userId: String?
    val username: String?
    val baseUrl: String?
    val token: String?
    val displayName: String?
    val pushConfigurationState: String?
    var capabilities: String?
    val clientCertificate: String?
    val externalSignalingServer: String?
    val current: Boolean
    val scheduledForDeletion: Boolean

    companion object {
        const val TAG = "UserEntity"
    }
}