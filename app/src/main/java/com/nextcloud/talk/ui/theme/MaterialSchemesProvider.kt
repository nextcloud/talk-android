/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Álvaro Brey <alvaro@alvarobrey.com>
 * SPDX-FileCopyrightText: 2022 Nextcloud GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.ui.theme

import com.nextcloud.android.common.ui.theme.MaterialSchemes
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.capabilities.Capabilities

interface MaterialSchemesProvider {
    fun getMaterialSchemesForUser(user: User?): MaterialSchemes
    fun getMaterialSchemesForCapabilities(capabilities: Capabilities?): MaterialSchemes
    fun getMaterialSchemesForCurrentUser(): MaterialSchemes
}
