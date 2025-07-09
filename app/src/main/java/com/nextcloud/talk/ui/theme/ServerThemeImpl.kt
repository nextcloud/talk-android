/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2022 Álvaro Brey <alvaro@alvarobrey.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.ui.theme

import com.nextcloud.android.common.ui.theme.ServerTheme
import com.nextcloud.talk.R
import com.nextcloud.talk.models.json.capabilities.ThemingCapability
import com.nextcloud.android.common.ui.color.ColorUtil

internal class ServerThemeImpl(themingCapability: ThemingCapability?, colorUtil: ColorUtil) : ServerTheme {

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
