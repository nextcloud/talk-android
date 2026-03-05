/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.profile

import androidx.annotation.DrawableRes
import com.nextcloud.talk.models.json.userprofile.Scope

data class UserInfoDetailItemData(
    @param:DrawableRes val icon: Int,
    val text: String,
    val hint: String,
    val scope: Scope?
)

data class UserInfoDetailListeners(val onTextChange: (String) -> Unit, val onScopeClick: (() -> Unit)?)
