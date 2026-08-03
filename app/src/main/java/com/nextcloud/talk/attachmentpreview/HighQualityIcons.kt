/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
@file:Suppress("MagicNumber") // vector path coordinates, not meaningful named constants

package com.nextcloud.talk.attachmentpreview

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// Material Symbols "high_quality" / "high_quality_off" — not part of the material-icons-extended
// artifact this project already depends on, so vendored here instead of via Icons.Filled.*.

internal val HighQualityIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "high_quality",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    )
        .apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(14.75f, 16.5f)
                horizontalLineToRelative(1.5f)
                verticalLineTo(15f)
                horizontalLineTo(17f)
                quadToRelative(0.43f, 0f, 0.71f, -0.29f)
                reflectiveQuadTo(18f, 14f)
                verticalLineTo(10f)
                quadTo(18f, 9.57f, 17.71f, 9.29f)
                reflectiveQuadTo(17f, 9f)
                horizontalLineTo(14f)
                quadTo(13.58f, 9f, 13.29f, 9.29f)
                reflectiveQuadTo(13f, 10f)
                verticalLineToRelative(4f)
                quadToRelative(0f, 0.42f, 0.29f, 0.71f)
                reflectiveQuadTo(14f, 15f)
                horizontalLineToRelative(0.75f)
                verticalLineToRelative(1.5f)
                close()
                moveTo(6f, 15f)
                horizontalLineTo(7.5f)
                verticalLineTo(13f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(2f)
                horizontalLineTo(11f)
                verticalLineTo(9f)
                horizontalLineTo(9.5f)
                verticalLineToRelative(2.5f)
                horizontalLineToRelative(-2f)
                verticalLineTo(9f)
                horizontalLineTo(6f)
                verticalLineToRelative(6f)
                close()
                moveToRelative(8.5f, -1.5f)
                verticalLineToRelative(-3f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(3f)
                horizontalLineToRelative(-2f)
                close()
                moveTo(4f, 20f)
                quadTo(3.18f, 20f, 2.59f, 19.41f)
                reflectiveQuadTo(2f, 18f)
                verticalLineTo(6f)
                quadTo(2f, 5.18f, 2.59f, 4.59f)
                reflectiveQuadTo(4f, 4f)
                horizontalLineTo(20f)
                quadToRelative(0.83f, 0f, 1.41f, 0.59f)
                quadTo(22f, 5.18f, 22f, 6f)
                verticalLineTo(18f)
                quadToRelative(0f, 0.82f, -0.59f, 1.41f)
                reflectiveQuadTo(20f, 20f)
                horizontalLineTo(4f)
                close()
                moveTo(4f, 18f)
                horizontalLineTo(20f)
                verticalLineTo(6f)
                horizontalLineTo(4f)
                verticalLineTo(18f)
                close()
                moveToRelative(0f, 0f)
                verticalLineTo(6f)
                verticalLineTo(18f)
                close()
            }
        }
        .build()
}

internal val HighQualityOffIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "high_quality_off",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    )
        .apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(18f, 15.15f)
                lineToRelative(-1.5f, -1.5f)
                verticalLineTo(10.5f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(1.15f)
                lineTo(13f, 10.15f)
                verticalLineTo(9.92f)
                quadToRelative(0f, -0.4f, 0.29f, -0.66f)
                reflectiveQuadTo(14f, 9f)
                horizontalLineToRelative(3f)
                quadToRelative(0.43f, 0f, 0.71f, 0.29f)
                reflectiveQuadTo(18f, 10f)
                verticalLineToRelative(5.15f)
                close()
                moveTo(6f, 15f)
                verticalLineTo(9f)
                horizontalLineTo(7.5f)
                verticalLineToRelative(2.5f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(-2f)
                lineTo(11f, 11f)
                verticalLineToRelative(4f)
                horizontalLineTo(9.5f)
                verticalLineTo(13f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(2f)
                horizontalLineTo(6f)
                close()
                moveTo(4f, 20f)
                quadTo(3.18f, 20f, 2.59f, 19.41f)
                reflectiveQuadTo(2f, 18f)
                verticalLineTo(6f)
                quadTo(2f, 5.18f, 2.59f, 4.59f)
                reflectiveQuadTo(4f, 4f)
                lineTo(6f, 6f)
                horizontalLineTo(4f)
                verticalLineTo(18f)
                horizontalLineTo(15.15f)
                lineTo(1.4f, 4.2f)
                lineTo(2.8f, 2.8f)
                lineTo(21.2f, 21.2f)
                lineToRelative(-1.4f, 1.4f)
                lineTo(17.15f, 20f)
                horizontalLineTo(4f)
                close()
                moveTo(21.75f, 18.9f)
                lineTo(20f, 17.15f)
                verticalLineTo(6f)
                horizontalLineTo(8.85f)
                lineToRelative(-2f, -2f)
                horizontalLineTo(20f)
                quadToRelative(0.83f, 0f, 1.41f, 0.59f)
                quadTo(22f, 5.18f, 22f, 6f)
                verticalLineTo(17.9f)
                quadToRelative(0f, 0.28f, -0.05f, 0.53f)
                reflectiveQuadToRelative(-0.2f, 0.47f)
                close()
                moveTo(14.43f, 11.58f)
                close()
                moveTo(9.58f, 12.43f)
                close()
            }
        }
        .build()
}
