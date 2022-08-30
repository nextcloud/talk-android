/*
 * Nextcloud Talk application
 *
 * @author Álvaro Brey
 * Copyright (C) 2022 Álvaro Brey
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
import com.nextcloud.android.common.ui.theme.ViewThemeUtilsBase
import com.nextcloud.talk.ui.theme.viewthemeutils.AndroidViewThemeUtils
import com.nextcloud.talk.ui.theme.viewthemeutils.AndroidXViewThemeUtils
import com.nextcloud.talk.ui.theme.viewthemeutils.DialogViewThemeUtils
import com.nextcloud.talk.ui.theme.viewthemeutils.MaterialViewThemeUtils
import com.nextcloud.talk.ui.theme.viewthemeutils.TalkSpecificViewThemeUtils
import javax.inject.Inject

@Suppress("TooManyFunctions")
class ViewThemeUtils @Inject constructor(
    schemes: MaterialSchemes,
    @JvmField
    val platform: AndroidViewThemeUtils,
    @JvmField
    val material: MaterialViewThemeUtils,
    @JvmField
    val androidx: AndroidXViewThemeUtils,
    @JvmField
    val talk: TalkSpecificViewThemeUtils,
    @JvmField
    val dialog: DialogViewThemeUtils
) : ViewThemeUtilsBase(schemes)
