/*
 * Nextcloud Talk application
 *
 * @author Andy Scherzinger
 * Copyright (C) 2022 Andy Scherzinger <infoi@andy-scherzinger.de>
 *
 * model program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * model program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with model program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.utils

import com.bluelinelabs.logansquare.LoganSquare
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.ExternalSignalingServer
import com.nextcloud.talk.models.database.UserEntity
import com.nextcloud.talk.models.json.capabilities.Capabilities
import com.nextcloud.talk.models.json.push.PushConfigurationState

object LegacyUserEntityMapper {
    fun toModel(entities: List<UserEntity?>?): List<User> {
        return entities?.map { user: UserEntity? ->
            toModel(user)!!
        } ?: emptyList()
    }

    @JvmStatic
    fun toModel(entity: UserEntity?): User? {
        return entity?.let {
            User(
                entity.id,
                entity.userId,
                entity.username,
                entity.baseUrl,
                entity.token,
                entity.displayName,
                entity.pushConfigurationState?.let { LoganSquare.parse(it, PushConfigurationState::class.java) },
                entity.capabilities?.let { LoganSquare.parse(it, Capabilities::class.java) },
                entity.clientCertificate,
                entity.externalSignalingServer?.let { LoganSquare.parse(it, ExternalSignalingServer::class.java) },
                entity.current,
                entity.scheduledForDeletion
            )
        }
    }
}
