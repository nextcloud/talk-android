/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.ui.theme

import com.nextcloud.android.common.ui.color.ColorUtil
import com.nextcloud.android.common.ui.theme.utils.AndroidViewThemeUtils
import com.nextcloud.android.common.ui.theme.utils.AndroidXViewThemeUtils
import com.nextcloud.android.common.ui.theme.utils.DialogViewThemeUtils
import com.nextcloud.android.common.ui.theme.utils.MaterialViewThemeUtils
import com.nextcloud.talk.data.user.model.User
import javax.inject.Inject

/**
 * Creates [ViewThemeUtils] themed with the server colors of a given account.
 */
class ViewThemeUtilsFactory @Inject constructor(
    private val schemesProvider: MaterialSchemesProvider,
    private val colorUtil: ColorUtil
) {
    fun forUser(user: User): ViewThemeUtils {
        val schemes = schemesProvider.getMaterialSchemesForUser(user)
        val android = AndroidViewThemeUtils(schemes, colorUtil)
        val material = MaterialViewThemeUtils(schemes, colorUtil)
        val androidx = AndroidXViewThemeUtils(schemes, android)
        val talk = TalkSpecificViewThemeUtils(schemes, androidx)
        val dialog = DialogViewThemeUtils(schemes)
        return ViewThemeUtils(schemes, android, material, androidx, talk, dialog)
    }
}
