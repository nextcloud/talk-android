/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.appconfig

import android.content.RestrictionsManager
import android.content.Context
import androidx.annotation.VisibleForTesting
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppConfigManager @Inject constructor(private val context: Context) {

    fun getServerBaseUrl(): String? = getString(KEY_SERVER_BASE_URL)?.takeIf { it.isNotBlank() }

    fun getUsername(): String? = getString(KEY_USERNAME)?.takeIf { it.isNotBlank() }

    fun getAppPassword(): String? = getString(KEY_APP_PASSWORD)?.takeIf { it.isNotBlank() }

    @VisibleForTesting
    internal fun getString(key: String): String? {
        val restrictionsManager = context.getSystemService(Context.RESTRICTIONS_SERVICE) as? RestrictionsManager
            ?: return null
        val restrictions = restrictionsManager.applicationRestrictions
        return restrictions?.getString(key)
    }

    companion object {
        const val KEY_SERVER_BASE_URL = "nextcloud_url"
        const val KEY_USERNAME = "nextcloud_username"
        const val KEY_APP_PASSWORD = "nextcloud_password"
    }
}
