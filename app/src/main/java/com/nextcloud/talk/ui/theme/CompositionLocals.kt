/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import com.nextcloud.talk.utils.message.MessageUtils

val LocalViewThemeUtils = staticCompositionLocalOf<ViewThemeUtils> {
    error("ViewThemeUtils not provided")
}

val LocalMessageUtils = staticCompositionLocalOf<MessageUtils> {
    error("MessageUtils not provided")
}
