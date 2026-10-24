/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.account.data

import com.nextcloud.talk.account.data.model.LoginCompletion
import com.nextcloud.talk.account.data.model.LoginResponse

/**
 * A login via the browser (login flow v2) that was started but not finished yet.
 */
data class PendingBrowserLogin(
    val response: LoginResponse,
    val baseUrl: String,
    val reAuth: Boolean,
    val accountToReauthorize: Long?,
    val startedAtMillis: Long,
    /** The confirmed login. The server hands it out once, so it is kept until the login screen has shown it. */
    val completion: LoginCompletion? = null
)

/**
 * Keeps the unfinished browser login in memory only (the response holds the login token, so it must not be written to
 * preferences or the database). It outlives [com.nextcloud.talk.account.BrowserLoginActivity], which the system
 * destroys when the app is opened again by the launcher icon while the browser is in front. The login is then
 * continued by polling the saved response, instead of being lost.
 *
 * The server hands out the confirmed login only once. It is saved here as soon as it arrives, and the login stays
 * pending until the login screen has shown the result, so a screen destroyed before that doesn't lose it.
 */
object PendingBrowserLoginStore {
    /** The login flow v2 lives 20 minutes on the server. */
    const val LIFETIME_MILLIS = 20 * 60 * 1000L

    @Volatile
    var clock: () -> Long = System::currentTimeMillis

    @Volatile
    private var pending: PendingBrowserLogin? = null

    fun save(response: LoginResponse, baseUrl: String, reAuth: Boolean, accountToReauthorize: Long?) {
        pending = PendingBrowserLogin(response, baseUrl, reAuth, accountToReauthorize, clock())
    }

    /** The unfinished login, or null if there is none or it expired. */
    fun active(): PendingBrowserLogin? {
        val current = pending
        if (current != null && clock() - current.startedAtMillis >= LIFETIME_MILLIS) {
            clear()
            return null
        }
        return current
    }

    /** Keeps the confirmed login of [response] for a new view model. Ignored if that login is not pending anymore. */
    fun complete(response: LoginResponse, completion: LoginCompletion) {
        val current = pending
        if (current != null && current.response == response) {
            pending = current.copy(completion = completion)
        }
    }

    fun clear() {
        pending = null
    }
}
