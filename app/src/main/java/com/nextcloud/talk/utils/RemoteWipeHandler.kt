/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.utils

import android.util.Log
import com.nextcloud.talk.api.NcApiCoroutines
import com.nextcloud.talk.data.user.model.User
import java.io.IOException
import javax.inject.Inject

/**
 * Asks the server whether it requested a remote wipe of an account.
 *
 * A wipe makes the server reject the credentials of the account, but rejected credentials do not mean that a wipe
 * was requested, e.g. a password change in an external user backend causes that too. So the server is asked.
 */
class RemoteWipeHandler @Inject constructor(private val ncApiCoroutines: NcApiCoroutines) {

    /**
     * Whether the server requested a wipe of [user]. Removing the account and reporting the wipe is up to the caller.
     */
    suspend fun isWipeRequested(user: User): Boolean {
        val baseUrl = user.baseUrl
        val token = user.token
        return baseUrl != null && token != null && askServerAboutWipe(baseUrl, token)
    }

    private suspend fun askServerAboutWipe(baseUrl: String, token: String): Boolean {
        val wipeRequested = try {
            val response = ncApiCoroutines.checkRemoteWipe(ApiUtils.getUrlForRemoteWipeCheck(baseUrl), token)
            Log.d(TAG, "Wipe check at $baseUrl answered with ${response.code()}")
            response.isSuccessful && response.body()?.wipe == true
        } catch (e: IOException) {
            Log.e(TAG, "Failed to check remote wipe status", e)
            false
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Failed to check remote wipe status", e)
            false
        }

        Log.d(TAG, "Wipe requested by $baseUrl: $wipeRequested")
        return wipeRequested
    }

    companion object {
        private const val TAG = "RemoteWipeHandler"
    }
}
