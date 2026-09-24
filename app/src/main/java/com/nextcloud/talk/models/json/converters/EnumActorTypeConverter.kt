/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2021 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2021 Joas Schilling <coding@schilljs.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models.json.converters

import com.bluelinelabs.logansquare.typeconverters.StringBasedTypeConverter
import com.nextcloud.talk.models.json.participants.ParticipantDto
import com.nextcloud.talk.models.json.participants.ParticipantDto.ActorType.CIRCLES
import com.nextcloud.talk.models.json.participants.ParticipantDto.ActorType.DUMMY
import com.nextcloud.talk.models.json.participants.ParticipantDto.ActorType.EMAILS
import com.nextcloud.talk.models.json.participants.ParticipantDto.ActorType.FEDERATED
import com.nextcloud.talk.models.json.participants.ParticipantDto.ActorType.GROUPS
import com.nextcloud.talk.models.json.participants.ParticipantDto.ActorType.GUESTS
import com.nextcloud.talk.models.json.participants.ParticipantDto.ActorType.USERS
import com.nextcloud.talk.models.json.participants.ParticipantDto.ActorType.PHONES

class EnumActorTypeConverter : StringBasedTypeConverter<ParticipantDto.ActorType>() {
    override fun getFromString(string: String?): ParticipantDto.ActorType =
        when (string) {
            "emails" -> EMAILS
            "groups" -> GROUPS
            "guests" -> GUESTS
            "users" -> USERS
            "circles" -> CIRCLES
            "federated_users" -> FEDERATED
            "phones" -> PHONES
            else -> DUMMY
        }

    override fun convertToString(`object`: ParticipantDto.ActorType?): String {
        if (`object` == null) {
            return ""
        }

        return when (`object`) {
            EMAILS -> "emails"
            GROUPS -> "groups"
            GUESTS -> "guests"
            USERS -> "users"
            CIRCLES -> "circles"
            FEDERATED -> "federated_users"
            PHONES -> "phones"
            else -> ""
        }
    }
}
