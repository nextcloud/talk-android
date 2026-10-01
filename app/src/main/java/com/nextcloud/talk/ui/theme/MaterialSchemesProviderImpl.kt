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
import com.nextcloud.talk.models.json.capabilities.ThemingCapabilityDto
import com.nextcloud.talk.users.DefaultAccountProvider
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

internal class MaterialSchemesProviderImpl @Inject constructor(
    private val defaultAccountProvider: DefaultAccountProvider,
    private val colorUtil: ColorUtil
) : MaterialSchemesProvider {

    // Keyed by the theming capability instead of the server: accounts on the same server can have different colors
    // (e.g. a personal primary color), and changed colors are used after the next capabilities sync.
    private val themeCache: ConcurrentHashMap<ThemeKey, MaterialSchemes> = ConcurrentHashMap()

    override fun getMaterialSchemesForUser(user: User?): MaterialSchemes {
        val capabilities = user?.capabilities
        // A copy, so a later change of the capability object cannot change the key of a cached entry.
        val key = ThemeKey(capabilities?.themingCapability?.copy())
        return themeCache.getOrPut(key) { getMaterialSchemesForCapabilities(capabilities) }
    }

    override fun getMaterialSchemesForDefaultUser(): MaterialSchemes =
        getMaterialSchemesForUser(defaultAccountProvider.getDefaultUserBlocking())

    override fun getMaterialSchemesForCapabilities(capabilities: CapabilitiesDto?): MaterialSchemes {
        val serverTheme = ServerThemeImpl(capabilities?.themingCapability, colorUtil)
        return MaterialSchemes.fromServerTheme(serverTheme)
    }

    private data class ThemeKey(val themingCapability: ThemingCapabilityDto?)
}
