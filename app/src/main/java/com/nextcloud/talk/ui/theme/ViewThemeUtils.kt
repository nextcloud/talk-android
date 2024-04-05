/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Álvaro Brey <alvaro@alvarobrey.com>
 * SPDX-FileCopyrightText: 2022 Nextcloud GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.ui.theme

import com.nextcloud.android.common.ui.theme.MaterialSchemes
import com.nextcloud.android.common.ui.theme.ViewThemeUtilsBase
import com.nextcloud.android.common.ui.theme.utils.AndroidViewThemeUtils
import com.nextcloud.android.common.ui.theme.utils.AndroidXViewThemeUtils
import com.nextcloud.android.common.ui.theme.utils.DialogViewThemeUtils
import com.nextcloud.android.common.ui.theme.utils.MaterialViewThemeUtils
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
