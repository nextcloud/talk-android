/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.account.data.model

data class LoginResponse(val token: String, val pollUrl: String, val loginUrl: String)

data class LoginCompletion(val status: Int, val server: String, val loginName: String, val appPassword: String)
