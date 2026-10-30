/*
 * Nextcloud Talk - Android Client
 *
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
    val completion: LoginCompletion? = null,
    val completedAtMillis: Long = 0L
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
    private fun PendingBrowserLogin.lastChangeMillis() = if (completion == null) startedAtMillis else completedAtMillis

    private fun PendingBrowserLogin.lifetimeMillis() =
        if (completion == null) LIFETIME_MILLIS else COMPLETED_LIFETIME_MILLIS

    /** The login flow v2 lives 20 minutes on the server. */
    const val LIFETIME_MILLIS = 20 * 60 * 1000L

    /**
     * How long a confirmed login is kept for a screen that did not show it yet. The server deleted its token already,
     * so the 20 minutes of the flow don't apply anymore. Counted from the confirmation.
     */
    const val COMPLETED_LIFETIME_MILLIS = 60 * 60 * 1000L

    @Volatile
    var clock: () -> Long = System::currentTimeMillis

    // The callers are the main thread (save, cancel, show) and the polling on the IO dispatcher (complete), and each
    // of them reads and then writes, so every access is synchronized.
    private var pending: PendingBrowserLogin? = null

    @Synchronized
    fun save(response: LoginResponse, baseUrl: String, reAuth: Boolean, accountToReauthorize: Long?) {
        pending = PendingBrowserLogin(response, baseUrl, reAuth, accountToReauthorize, clock())
    }

    /** The unfinished login, or null if there is none or it expired. */
    @Synchronized
    fun active(): PendingBrowserLogin? {
        val current = pending
        val isExpired = current != null && clock() - current.lastChangeMillis() >= current.lifetimeMillis()
        if (isExpired) pending = null
        return current.takeUnless { isExpired }
    }

    /** Keeps the confirmed login of [response] for a new view model. Ignored if that login is not pending anymore. */
    @Synchronized
    fun complete(response: LoginResponse, completion: LoginCompletion) {
        val current = pending
        if (current != null && current.response == response) {
            pending = current.copy(completion = completion, completedAtMillis = clock())
        }
    }

    /** Ends the pending login of [response]. A newer login of another response is kept. */
    @Synchronized
    fun clearIf(response: LoginResponse) {
        if (pending?.response == response) pending = null
    }

    @Synchronized
    fun clear() {
        pending = null
    }
}
