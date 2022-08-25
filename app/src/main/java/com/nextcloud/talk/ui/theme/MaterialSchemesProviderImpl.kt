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

import com.nextcloud.android.common.ui.theme.MaterialSchemes
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.capabilities.Capabilities
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import com.nextcloud.talk.utils.ui.ColorUtil
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

    override fun getMaterialSchemesForCurrentUser(): MaterialSchemes {
        return getMaterialSchemesForUser(userProvider.currentUser.blockingGet())
    }

    override fun getMaterialSchemesForCapabilities(capabilities: Capabilities?): MaterialSchemes {
        return MaterialSchemesImpl(ServerThemeImpl(capabilities?.themingCapability, colorUtil))
    }

    companion object {
        const val FALLBACK_URL = "NULL"
    }
}
