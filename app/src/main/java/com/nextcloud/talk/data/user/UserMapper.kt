/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.data.user

import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.data.user.model.UserEntity

object UserMapper {
    fun toModel(entities: List<UserEntity?>?): List<User> =
        entities?.map { user: UserEntity? ->
            toModel(user)!!
        } ?: emptyList()

    fun toModel(entity: UserEntity?): User? =
        entity?.let {
            User(
                entity.id,
                entity.userId,
                entity.username,
                entity.baseUrl!!,
                entity.token,
                entity.displayName,
                entity.pushConfigurationState,
                entity.capabilities,
                entity.serverVersion,
                entity.clientCertificate,
                entity.externalSignalingServer,
                entity.current,
                entity.scheduledForDeletion
            )
        }

    fun toEntity(model: User): UserEntity {
        val userEntity = when (val id = model.id) {
            null -> UserEntity(userId = model.userId, username = model.username, baseUrl = model.baseUrl!!)
            else -> UserEntity(id, model.userId, model.username, model.baseUrl!!)
        }
        userEntity.apply {
            token = model.token
            displayName = model.displayName
            pushConfigurationState = model.pushConfigurationState
            capabilities = model.capabilities
            serverVersion = model.serverVersion
            clientCertificate = model.clientCertificate
            externalSignalingServer = model.externalSignalingServer
            current = model.current
            scheduledForDeletion = model.scheduledForDeletion
        }
        return userEntity
    }
}
