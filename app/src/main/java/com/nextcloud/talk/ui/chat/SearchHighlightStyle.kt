/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:Suppress("MagicNumber")

package com.nextcloud.talk.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle

private val searchHighlightBaseColor = Color(0xFFFFB300)
private const val SEARCH_HIGHLIGHT_ALPHA = 0.35f

val searchHighlightColor: Color = searchHighlightBaseColor.copy(alpha = SEARCH_HIGHLIGHT_ALPHA)
val searchHighlightColorArgb: Int = searchHighlightColor.toArgb()

internal fun AnnotatedString.withSearchHighlight(
    searchTerm: String?,
    highlightColor: Color = searchHighlightColor
): AnnotatedString {
    val term = searchTerm?.trim()?.lowercase().orEmpty()
    if (term.isEmpty()) {
        return this
    }

    val lowerSource = text.lowercase()
    val builder = AnnotatedString.Builder(this)
    var startIndex = 0
    var matchIndex = lowerSource.indexOf(term, startIndex)
    while (matchIndex != -1) {
        builder.addStyle(
            SpanStyle(background = highlightColor),
            matchIndex,
            matchIndex + term.length
        )
        startIndex = matchIndex + term.length
        matchIndex = lowerSource.indexOf(term, startIndex)
    }
    return builder.toAnnotatedString()
}

internal fun String.withSearchHighlight(searchTerm: String?): AnnotatedString =
    AnnotatedString(this).withSearchHighlight(searchTerm)

@Composable
internal fun rememberSearchHighlightedText(text: String, searchTerm: String?): AnnotatedString =
    remember(text, searchTerm) {
        text.withSearchHighlight(searchTerm)
    }
