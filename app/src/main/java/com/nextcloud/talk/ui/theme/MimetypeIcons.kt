/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Tint for a mimetype icon rendered as an [androidx.compose.material3.Icon]. Monochrome placeholders get
 * the theme color, every other mimetype icon keeps its own colors ([Color.Unspecified]).
 */
@Composable
@ReadOnlyComposable
fun mimetypeIconTint(@DrawableRes drawableResourceId: Int): Color =
    if (TalkSpecificViewThemeUtils.isThemeablePlaceholder(drawableResourceId)) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.Unspecified
    }
