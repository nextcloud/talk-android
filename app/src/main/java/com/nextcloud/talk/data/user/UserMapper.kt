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
        return if (entities == null) {
            ArrayList()
        } else {
            val users = ArrayList<User>()
            for (entity in entities) {
                users.add(toModel(entity)!!)
            }
            users
        }
    }

    fun toModel(entity: UserEntity?): User? {
        return if (entity == null) {
            null
        } else {
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
        var UserEntity: UserEntity? = null
        model.id?.let {
            UserEntity = UserEntity(it, model.userId, model.username, model.baseUrl)
        } ?: run {
            UserEntity = UserEntity(userId = model.userId, username = model.username, baseUrl = model.baseUrl)
        }

        UserEntity!!.token = model.token
        UserEntity!!.displayName = model.displayName
        UserEntity!!.pushConfigurationState = model.pushConfigurationState
        UserEntity!!.capabilities = model.capabilities
        UserEntity!!.clientCertificate = model.clientCertificate
        UserEntity!!.externalSignalingServer = model.externalSignalingServer
        UserEntity!!.current = model.current
        UserEntity!!.scheduledForDeletion = model.scheduledForDeletion

        return UserEntity!!
    }
}
