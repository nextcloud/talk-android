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

import com.nextcloud.talk.R
import com.nextcloud.talk.models.json.capabilities.ThemingCapability
import com.nextcloud.talk.utils.ui.ColorUtil

internal class ServerThemeImpl(themingCapability: ThemingCapability?, colorUtil: ColorUtil) :
    ServerTheme {

    override val primaryColor: Int
    override val colorElement: Int
    override val colorElementBright: Int
    override val colorElementDark: Int
    override val colorText: Int

    init {
        primaryColor = colorUtil.getNullSafeColorWithFallbackRes(themingCapability?.color, R.color.colorPrimary)

        colorElement = colorUtil.getNullSafeColor(themingCapability?.colorElement, primaryColor)
        colorElementBright = colorUtil.getNullSafeColor(themingCapability?.colorElementBright, primaryColor)
        colorElementDark = colorUtil.getNullSafeColor(themingCapability?.colorElementDark, primaryColor)

        colorText = colorUtil.getTextColor(themingCapability?.colorText, primaryColor)
    }
}
