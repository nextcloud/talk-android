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
import androidx.compose.ui.util.lerp

private const val LEAVE_VIEWPORT = 24f
private const val LEAVE_ARROW_MAX_DX = 2f

@Composable
internal fun LeaveIcon(progress: Float, popScale: Float, color: Color, modifier: Modifier = Modifier) {
    val doorPath = remember {
        PathParser().parsePathString(
            "M19,3H5c-1.11,0 -2,0.9 -2,2v4h2V5h14v14H5v-4H3v4c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2V5c0," +
                "-1.1 -0.9,-2 -2,-2z"
        ).toPath()
    }
    val arrowPath = remember {
        PathParser().parsePathString(
            "M10.09,15.59L11.5,17l5,-5 -5,-5 -1.41,1.41L12.67,11H3v2h9.67l-2.58,2.59z"
        ).toPath()
    }
    Canvas(modifier = modifier) {
        val s = size.width / LEAVE_VIEWPORT
        withTransform({ scale(s, s, Offset.Zero) }) {
            withTransform({ scale(popScale, popScale, Offset(LEAVE_VIEWPORT / 2f, LEAVE_VIEWPORT / 2f)) }) {
                drawPath(doorPath, color)
                withTransform({ translate(left = lerp(0f, LEAVE_ARROW_MAX_DX, progress)) }) {
                    drawPath(arrowPath, color)
                }
            }
        }
    }
}
