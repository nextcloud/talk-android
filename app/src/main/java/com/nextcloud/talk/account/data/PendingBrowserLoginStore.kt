/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.account.data

import com.nextcloud.talk.account.data.model.LoginResponse

/**
 * A login via the browser (login flow v2) that was started but not finished yet.
 */
data class PendingBrowserLogin(
    val response: LoginResponse,
    val reAuth: Boolean,
    val accountToReauthorize: Long?,
    val startedAtMillis: Long
)

/**
 * Keeps the unfinished browser login in memory only (the response holds the login token, so it must not be written to
 * preferences or the database). It outlives [com.nextcloud.talk.account.BrowserLoginActivity], which the system
 * destroys when the app is opened again by the launcher icon while the browser is in front. The login is then
 * continued by polling the saved response, instead of being lost.
 */
object PendingBrowserLoginStore {
    /** The login flow v2 lives 20 minutes on the server. */
    const val LIFETIME_MILLIS = 20 * 60 * 1000L

    @Volatile
    var clock: () -> Long = System::currentTimeMillis

    @Volatile
    private var pending: PendingBrowserLogin? = null

    fun save(response: LoginResponse, reAuth: Boolean, accountToReauthorize: Long?) {
        pending = PendingBrowserLogin(response, reAuth, accountToReauthorize, clock())
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

    fun clear() {
        pending = null
    }
}
