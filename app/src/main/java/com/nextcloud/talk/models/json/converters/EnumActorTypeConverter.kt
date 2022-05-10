/*
 * Nextcloud Talk application
 *
 * @author Joas Schilling
 * @author Andy Scherzinger
 * Copyright (C) 2021 Joas Schilling <coding@schilljs.com>
 * Copyright (C) 2021 Andy Scherzinger <info@andy-scherzinger.de>
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

package com.nextcloud.talk.models.json.converters

import com.bluelinelabs.logansquare.typeconverters.StringBasedTypeConverter
import com.nextcloud.talk.models.json.participants.Participant
import com.nextcloud.talk.models.json.participants.Participant.ActorType.CIRCLES
import com.nextcloud.talk.models.json.participants.Participant.ActorType.DUMMY
import com.nextcloud.talk.models.json.participants.Participant.ActorType.EMAILS
import com.nextcloud.talk.models.json.participants.Participant.ActorType.GROUPS
import com.nextcloud.talk.models.json.participants.Participant.ActorType.GUESTS
import com.nextcloud.talk.models.json.participants.Participant.ActorType.USERS

class EnumActorTypeConverter : StringBasedTypeConverter<Participant.ActorType>() {
    override fun getFromString(string: String?): Participant.ActorType {
        return when (string) {
            "emails" -> EMAILS
            "groups" -> GROUPS
            "guests" -> GUESTS
            "users" -> USERS
            "circles" -> CIRCLES
            else -> DUMMY
        }
    }

    override fun convertToString(`object`: Participant.ActorType?): String {

        if (`object` == null) {
            return ""
        }

        return when (`object`) {
            EMAILS -> "emails"
            GROUPS -> "groups"
            GUESTS -> "guests"
            USERS -> "users"
            CIRCLES -> "circles"
            else -> ""
        }
    }
}
