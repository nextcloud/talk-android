/*
 * Nextcloud Talk application
 *
 * @author Álvaro Brey
 * @author Andy Scherzinger
 * Copyright (C) 2022 Álvaro Brey
 * Copyright (C) 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * Copyright (C) 2022 Nextcloud GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.ui.theme

import android.annotation.SuppressLint
import android.content.Context
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.capabilities.Capabilities
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

internal class ServerThemeProviderImpl @Inject constructor(
    private val context: Context,
    private val userProvider: CurrentUserProviderNew
) : ServerThemeProvider {

    val themeCache: ConcurrentHashMap<String, ServerTheme> = ConcurrentHashMap()

    // TODO move this logic to currentUserProvider or something
    private var _currentUser: User? = null
    private val currentUser: User?
        @SuppressLint("CheckResult")
        get() {
            return when (_currentUser) {
                null -> {
                    // immediately get a result synchronously
                    _currentUser = userProvider.currentUser.blockingGet()
                    // start observable for auto-updates
                    userProvider.currentUserObservable.subscribe { _currentUser = it }
                    _currentUser
                }
                else -> {
                    _currentUser
                }
            }
        }

    override fun getServerThemeForUser(user: User?): ServerTheme {
        val url: String = if (user?.baseUrl != null) {
            user.baseUrl!!
        } else {
            FALLBACK_URL
        }

        if (!themeCache.containsKey(url)) {
            themeCache[url] = getServerThemeForCapabilities(user?.capabilities)
        }

        return themeCache[url]!!
    }

    override fun getServerThemeForCurrentUser(): ServerTheme {
        return getServerThemeForUser(currentUser)
    }

    override fun getServerThemeForCapabilities(capabilities: Capabilities?): ServerTheme {
        return ServerThemeImpl(context, capabilities?.themingCapability)
    }

    companion object {
        const val FALLBACK_URL = "NULL"
    }
}
