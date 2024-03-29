/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2021 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2021 Joas Schilling <coding@schilljs.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
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
