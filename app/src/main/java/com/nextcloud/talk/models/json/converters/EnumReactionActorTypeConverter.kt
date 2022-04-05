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

package com.nextcloud.talk.models.json.converters

import com.bluelinelabs.logansquare.typeconverters.StringBasedTypeConverter
import com.nextcloud.talk.models.json.reactions.ReactionVoter.ReactionActorType.DUMMY
import com.nextcloud.talk.models.json.reactions.ReactionVoter.ReactionActorType.GUESTS
import com.nextcloud.talk.models.json.reactions.ReactionVoter.ReactionActorType.USERS
import com.nextcloud.talk.models.json.reactions.ReactionVoter

class EnumReactionActorTypeConverter : StringBasedTypeConverter<ReactionVoter.ReactionActorType>() {
    override fun getFromString(string: String): ReactionVoter.ReactionActorType {
        return when (string) {
            "guests" -> GUESTS
            "users" -> USERS
            else -> DUMMY
        }
    }

    override fun convertToString(`object`: ReactionVoter.ReactionActorType?): String {

        if (`object` == null) {
            return ""
        }

        return when (`object`) {
            GUESTS -> "guests"
            USERS -> "users"
            else -> ""
        }
    }
}
