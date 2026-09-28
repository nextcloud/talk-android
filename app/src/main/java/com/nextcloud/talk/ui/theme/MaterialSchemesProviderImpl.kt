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
import com.nextcloud.talk.models.json.capabilities.CapabilitiesDto
import com.nextcloud.talk.users.DefaultAccountProvider
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

internal class MaterialSchemesProviderImpl @Inject constructor(
    private val defaultAccountProvider: DefaultAccountProvider,
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

    override fun getMaterialSchemesForDefaultUser(): MaterialSchemes =
        getMaterialSchemesForUser(defaultAccountProvider.getDefaultUserBlocking())

    override fun getMaterialSchemesForCapabilities(capabilities: CapabilitiesDto?): MaterialSchemes {
        val serverTheme = ServerThemeImpl(capabilities?.themingCapability, colorUtil)
        return MaterialSchemes.fromServerTheme(serverTheme)
    }

    companion object {
        const val FALLBACK_URL = "NULL"
    }
}
