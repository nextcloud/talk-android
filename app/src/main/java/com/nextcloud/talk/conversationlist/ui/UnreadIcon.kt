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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser

private const val ICON_VIEWPORT = 960f
private const val DOT_CENTER_X = 760f
private const val DOT_CENTER_Y = 120f

@Composable
internal fun UnreadIcon(progress: Float, popScale: Float, color: Color, modifier: Modifier = Modifier) {
    val bubblePath = remember {
        PathParser().parsePathString(
            "M80,880L80,160Q80,127 103.5,103.5Q127,80 160,80L564,80Q560,100 560,120Q560,140 564,160" +
                "L160,160Q160,160 160,160Q160,160 160,160L160,685L206,640L800,640Q800,640 800,640Q800,640 800,640" +
                "L800,316Q823,311 843,302.5Q863,294 880,280L880,640Q880,673 856.5,696.5Q833,720 800,720L240,720L80,880Z"
        ).toPath()
    }
    val dotPath = remember {
        PathParser().parsePathString(
            "M760,240Q710,240 675,205Q640,170 640,120Q640,70 675,35Q710,0 760,0" +
                "Q810,0 845,35Q880,70 880,120Q880,170 845,205Q810,240 760,240Z"
        ).toPath()
    }
    Canvas(modifier = modifier) {
        val s = size.width / ICON_VIEWPORT
        withTransform({ scale(s, s, Offset.Zero) }) {
            drawPath(bubblePath, color)
            withTransform({ scale(progress * popScale, progress * popScale, Offset(DOT_CENTER_X, DOT_CENTER_Y)) }) {
                drawPath(dotPath, color)
            }
        }
    }
}
