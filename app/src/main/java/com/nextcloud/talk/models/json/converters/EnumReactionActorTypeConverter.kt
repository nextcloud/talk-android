/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models.json.converters

import com.bluelinelabs.logansquare.typeconverters.StringBasedTypeConverter
import com.nextcloud.talk.models.json.reactions.ReactionVoterDto.ReactionActorType.DUMMY
import com.nextcloud.talk.models.json.reactions.ReactionVoterDto.ReactionActorType.GUESTS
import com.nextcloud.talk.models.json.reactions.ReactionVoterDto.ReactionActorType.USERS
import com.nextcloud.talk.models.json.reactions.ReactionVoterDto

class EnumReactionActorTypeConverter : StringBasedTypeConverter<ReactionVoterDto.ReactionActorType>() {
    override fun getFromString(string: String): ReactionVoterDto.ReactionActorType =
        when (string) {
            "guests" -> GUESTS
            "users" -> USERS
            else -> DUMMY
        }

    override fun convertToString(`object`: ReactionVoterDto.ReactionActorType?): String {
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
