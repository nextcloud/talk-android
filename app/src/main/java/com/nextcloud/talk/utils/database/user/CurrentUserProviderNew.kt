/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Álvaro Brey <alvaro@alvarobrey.com>
 * SPDX-FileCopyrightText: 2022 Nextcloud GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils.database.user

import com.nextcloud.talk.data.user.model.User
import io.reactivex.Maybe

interface CurrentUserProviderNew {
    val currentUser: Maybe<User>
}
