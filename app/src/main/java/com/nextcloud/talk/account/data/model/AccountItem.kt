/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Sowjanya Kota <sowjanya.kch@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.account.data.model

import com.nextcloud.talk.data.user.model.User

data class AccountItem(val user: User, val userId: String?, val pendingInvitation: Int)
