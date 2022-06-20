/*
 * Nextcloud Talk application
 *
 * @author Andy Scherzinger
 * Copyright (C) 2022 Andy Scherzinger <info@andy-scherzinger.de>
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

package com.nextcloud.talk.data.source.local.converters

import androidx.room.TypeConverter
import com.nextcloud.talk.models.ExternalSignalingServer

class ExternalSignalingServerConverter {
    val json = JsonConfiguration.customJsonConfiguration

    @TypeConverter
    fun fromExternalSignalingServerToString(externalSignalingServer: ExternalSignalingServer?): String {
        return if (externalSignalingServer == null) {
            ""
        } else {
            json.encodeToString(ExternalSignalingServer.serializer(), externalSignalingServer)
        }
    }

    @TypeConverter
    fun fromStringToExternalSignalingServer(value: String): ExternalSignalingServer? {
        if (value.isBlank()) {
            return null
        }

        return json.decodeFromString(ExternalSignalingServer.serializer(), value)
    }
}
