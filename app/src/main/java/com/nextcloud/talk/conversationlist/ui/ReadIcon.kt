/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationlist.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.util.lerp

private const val ICON_VIEWPORT = 960f
private const val CHECKMARK_X_MIN = 553f
private const val CHECKMARK_X_MAX = 920f
private const val CHECKMARK_CENTROID_X = 722f
private const val CHECKMARK_CENTROID_Y = 640f

@Composable
internal fun ReadIcon(progress: Float, popScale: Float, color: Color, modifier: Modifier = Modifier) {
    val bubblePath = remember {
        PathParser().parsePathString(
            "M80,880L80,160Q80,127 103.5,103.5Q127,80 160,80L800,80Q833,80 856.5,103.5Q880,127 880,160" +
                "L880,440L800,440L800,160Q800,160 800,160Q800,160 800,160" +
                "L160,160Q160,160 160,160Q160,160 160,160L160,685L206,640L480,640L480,720L240,720L80,880Z"
        ).toPath()
    }
    val checkmarkPath = remember {
        PathParser().parsePathString("M694,800L553,658L609,602L694,687L864,517L920,574L694,800Z").toPath()
    }
    Canvas(modifier = modifier) {
        val s = size.width / ICON_VIEWPORT
        withTransform({ scale(s, s, Offset.Zero) }) {
            drawPath(bubblePath, color)
            if (progress < 1f) {
                val clipRight = lerp(CHECKMARK_X_MIN, CHECKMARK_X_MAX, progress)
                clipRect(0f, 0f, clipRight, ICON_VIEWPORT) {
                    drawPath(checkmarkPath, color)
                }
            } else {
                withTransform({ scale(popScale, popScale, Offset(CHECKMARK_CENTROID_X, CHECKMARK_CENTROID_Y)) }) {
                    drawPath(checkmarkPath, color)
                }
            }
        }
    }
}
