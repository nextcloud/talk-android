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

package com.nextcloud.talk.data.user

import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.data.user.model.UserEntity

object UserMapper {
    fun toModel(entities: List<UserEntity?>?): List<User> {
        return entities?.map { user: UserEntity? ->
            toModel(user)!!
        } ?: emptyList()
    }

    fun toModel(entity: UserEntity?): User? {
        return entity?.let {
            User(
                entity.id,
                entity.userId,
                entity.username,
                entity.baseUrl,
                entity.token,
                entity.displayName,
                entity.pushConfigurationState,
                entity.capabilities,
                entity.clientCertificate,
                entity.externalSignalingServer,
                entity.current,
                entity.scheduledForDeletion
            )
        }
    }

    fun toEntity(model: User): UserEntity {
        val userEntity = when (val id = model.id) {
            null -> UserEntity(userId = model.userId, username = model.username, baseUrl = model.baseUrl)
            else -> UserEntity(id, model.userId, model.username, model.baseUrl)
        }
        userEntity.apply {
            token = model.token
            displayName = model.displayName
            pushConfigurationState = model.pushConfigurationState
            capabilities = model.capabilities
            clientCertificate = model.clientCertificate
            externalSignalingServer = model.externalSignalingServer
            current = model.current
            scheduledForDeletion = model.scheduledForDeletion
        }
        return userEntity
    }
}
