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
package com.nextcloud.talk.models.domain.converters

import com.bluelinelabs.logansquare.typeconverters.IntBasedTypeConverter
import com.nextcloud.talk.models.domain.NotificationLevel

class DomainEnumNotificationLevelConverter : IntBasedTypeConverter<NotificationLevel>() {
    override fun getFromInt(i: Int): NotificationLevel {
        return when (i) {
            0 -> NotificationLevel.DEFAULT
            1 -> NotificationLevel.ALWAYS
            2 -> NotificationLevel.MENTION
            3 -> NotificationLevel.NEVER
            else -> NotificationLevel.DEFAULT
        }
    }

    override fun convertToInt(`object`: NotificationLevel): Int {
        return when (`object`) {
            NotificationLevel.DEFAULT -> 0
            NotificationLevel.ALWAYS -> 1
            NotificationLevel.MENTION -> 2
            NotificationLevel.NEVER -> 3
            else -> 0
        }
    }
}
