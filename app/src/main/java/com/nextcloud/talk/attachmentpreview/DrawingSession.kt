/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.attachmentpreview

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

private const val DEFAULT_BRUSH_COLOR_ARGB = 0xFFFFFFFF

/** Strokes and brush colour of the drawing being made, held by the view model across rotation. */
internal class DrawingSession {
    var strokes by mutableStateOf<List<DrawStroke>>(emptyList())
        private set

    var brushColorArgb by mutableStateOf(DEFAULT_BRUSH_COLOR_ARGB.toInt())

    fun add(stroke: DrawStroke) {
        strokes = strokes + stroke
    }

    fun undo() {
        strokes = undoLast(strokes)
    }

    fun clear() {
        strokes = emptyList()
    }
}
