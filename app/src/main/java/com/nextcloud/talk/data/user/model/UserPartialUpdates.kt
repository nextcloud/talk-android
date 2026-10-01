/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.data.user.model

import androidx.room.ColumnInfo
import com.nextcloud.talk.models.ExternalSignalingServer
import com.nextcloud.talk.models.json.capabilities.CapabilitiesDto
import com.nextcloud.talk.models.json.capabilities.ServerVersionDto

// Partial entities of UserEntity for Room's @Update(entity = UserEntity::class): each writes only its own columns
// of the user with the given id.

data class UserCapabilitiesUpdate(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "capabilities") val capabilities: CapabilitiesDto?,
    @ColumnInfo(name = "serverVersion") val serverVersion: ServerVersionDto?
)

data class UserExternalSignalingServerUpdate(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "externalSignalingServer") val externalSignalingServer: ExternalSignalingServer?
)

data class UserDisplayNameUpdate(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "displayName") val displayName: String?
)

data class UserClientCertificateUpdate(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "clientCertificate") val clientCertificate: String?
)

data class UserCredentialsUpdate(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "token") val token: String?,
    @ColumnInfo(name = "clientCertificate") val clientCertificate: String?
)
