/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:Suppress("MagicNumber")

package com.nextcloud.talk.ui.chat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

private val searchHighlightBaseColor = Color(0xFFFFB300)
private const val SEARCH_HIGHLIGHT_ALPHA = 0.35f

val searchHighlightColor: Color = searchHighlightBaseColor.copy(alpha = SEARCH_HIGHLIGHT_ALPHA)
val searchHighlightColorArgb: Int = searchHighlightColor.toArgb()
