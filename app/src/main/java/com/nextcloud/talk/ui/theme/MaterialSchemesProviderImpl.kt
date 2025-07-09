/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2022 Álvaro Brey <alvaro@alvarobrey.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.ui.theme

import com.nextcloud.android.common.ui.color.ColorUtil
import com.nextcloud.android.common.ui.theme.MaterialSchemes
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.capabilities.Capabilities
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

internal class MaterialSchemesProviderImpl @Inject constructor(
    private val userProvider: CurrentUserProviderNew,
    private val colorUtil: ColorUtil
) : MaterialSchemesProvider {

    private val themeCache: ConcurrentHashMap<String, MaterialSchemes> = ConcurrentHashMap()

    override fun getMaterialSchemesForUser(user: User?): MaterialSchemes {
        val url: String = if (user?.baseUrl != null) {
            user.baseUrl!!
        } else {
            FALLBACK_URL
        }

        if (!themeCache.containsKey(url)) {
            themeCache[url] = getMaterialSchemesForCapabilities(user?.capabilities)
        }

        return themeCache[url]!!
    }

    override fun getMaterialSchemesForCurrentUser(): MaterialSchemes =
        getMaterialSchemesForUser(userProvider.currentUser.blockingGet())

    override fun getMaterialSchemesForCapabilities(capabilities: Capabilities?): MaterialSchemes {
        val serverTheme = ServerThemeImpl(capabilities?.themingCapability, colorUtil)
        return MaterialSchemes.fromServerTheme(serverTheme)
    }

    companion object {
        const val FALLBACK_URL = "NULL"
    }
}
