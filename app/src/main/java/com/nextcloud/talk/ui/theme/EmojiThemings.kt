/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.toArgb
import com.vanniktech.emoji.EmojiTheming

/**
 * Themes the vanniktech emoji popup from the current [MaterialTheme.colorScheme] so its category,
 * search and backspace icons stay visible in dark mode.
 * Keep in sync with the view variant [TalkSpecificViewThemeUtils.getEmojiTheming].
 */
@Composable
@ReadOnlyComposable
fun emojiTheming(): EmojiTheming =
    with(MaterialTheme.colorScheme) {
        EmojiTheming(
            backgroundColor = surface.toArgb(),
            primaryColor = onSurfaceVariant.toArgb(),
            secondaryColor = primary.toArgb(),
            dividerColor = outlineVariant.toArgb(),
            textColor = onSurface.toArgb(),
            textSecondaryColor = onSurfaceVariant.toArgb()
        )
    }
