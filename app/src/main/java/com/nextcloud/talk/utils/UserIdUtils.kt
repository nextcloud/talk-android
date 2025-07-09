/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import com.nextcloud.talk.data.user.model.User

object UserIdUtils {
    const val NO_ID: Long = -1

    fun getIdForUser(user: User?): Long =
        if (user?.id != null) {
            user.id!!
        } else {
            NO_ID
        }
}
