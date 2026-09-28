/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
@file:JvmName("HostViewThemeUtils")

package com.nextcloud.talk.ui.theme

import android.content.Context
import android.content.ContextWrapper
import com.nextcloud.talk.activities.BaseActivity

/**
 * Returns the [ViewThemeUtils] of the [BaseActivity] that [context] belongs to, which is themed for the account that
 * activity was started for, or [fallback] if [context] does not belong to a [BaseActivity].
 */
fun hostViewThemeUtils(context: Context?, fallback: ViewThemeUtils): ViewThemeUtils {
    var current = context
    while (current is ContextWrapper) {
        if (current is BaseActivity) {
            return current.viewThemeUtils
        }
        current = current.baseContext
    }
    return fallback
}
