/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Álvaro Brey <alvaro@alvarobrey.com>
 * SPDX-FileCopyrightText: 2022 Nextcloud GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils.database.user

import com.nextcloud.talk.data.user.model.User
import kotlinx.coroutines.flow.Flow

interface CurrentUserProvider {
    val currentUserFlow: Flow<User>
    suspend fun getCurrentUser(timeout: Long = 5000L): Result<User>
}
