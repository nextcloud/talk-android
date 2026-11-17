/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.settings.data

import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.userprofile.UserProfileDataDto

/**
 * Server side account settings shown on the settings screen. All functions throw on network or server errors.
 */
interface SettingsRepository {
    suspend fun getUserProfile(user: User): UserProfileDataDto?

    /** Returns whether the server accepted [phoneNumber]. */
    suspend fun setPhoneNumber(user: User, phoneNumber: String): Boolean

    suspend fun setReadStatusShared(user: User, shared: Boolean)

    suspend fun setTypingStatusShared(user: User, shared: Boolean)
}
