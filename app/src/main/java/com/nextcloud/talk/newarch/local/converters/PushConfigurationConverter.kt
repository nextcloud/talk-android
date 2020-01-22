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

package com.nextcloud.talk.newarch.local.converters

import androidx.room.TypeConverter
import com.nextcloud.talk.models.json.push.PushConfiguration
import com.nextcloud.talk.newarch.utils.MagicJson
import kotlinx.serialization.json.Json

class PushConfigurationConverter {
    val json = Json(MagicJson.customJsonConfiguration)

    @TypeConverter
    fun fromPushConfigurationToString(pushConfiguration: PushConfiguration?): String {

        return if (pushConfiguration == null) {
            ""
        } else {
            json.stringify(PushConfiguration.serializer(), pushConfiguration)
        }
    }

    @TypeConverter
    fun fromStringToPushConfiguration(value: String): PushConfiguration? {
        if (value.isNullOrBlank()) {
            return null
        }

        return json.parse(PushConfiguration.serializer(), value)
    }
}
