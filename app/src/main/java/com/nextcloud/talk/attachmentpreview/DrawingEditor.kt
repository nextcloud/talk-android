/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.attachmentpreview

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nextcloud.talk.R

private const val BRUSH_WIDTH_FRACTION = 0.012f
private const val SWATCH_SIZE_DP = 32
private const val SWATCH_SELECTED_BORDER_DP = 3
private const val COLOR_RED = 0xFFE53935
private const val COLOR_YELLOW = 0xFFFDD835
private const val COLOR_GREEN = 0xFF43A047
private const val COLOR_BLUE = 0xFF1E88E5
private val brushSwatches = listOf(
    Swatch(Color.White, R.string.nc_attachment_color_white),
    Swatch(Color.Black, R.string.nc_attachment_color_black),
    Swatch(Color(COLOR_RED), R.string.nc_attachment_color_red),
    Swatch(Color(COLOR_YELLOW), R.string.nc_attachment_color_yellow),
    Swatch(Color(COLOR_GREEN), R.string.nc_attachment_color_green),
    Swatch(Color(COLOR_BLUE), R.string.nc_attachment_color_blue)
)

private data class Swatch(val color: Color, @StringRes val name: Int)

/**
 * Full-screen brush editor over an image. Strokes are kept in image-relative coordinates, so the
 * result doesn't depend on the screen size; they live in [session], owned by the view model.
 * [aspectRatio] is the image's displayed width / height.
 */
@Composable
internal fun DrawingEditor(
    imageUri: String,
    aspectRatio: Float,
    session: DrawingSession,
    onCancel: () -> Unit,
    onDone: () -> Unit
) {
    BackHandler(onBack = onCancel)

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
            val heightPx = with(LocalDensity.current) { maxHeight.toPx() }
            val rect = remember(widthPx, heightPx, aspectRatio) { fitRect(widthPx, heightPx, aspectRatio) }

            AsyncImage(
                model = imageUri,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
            DrawingSurface(rect = rect, session = session)
        }

        EditorTopBar(
            canUndo = session.strokes.isNotEmpty(),
            onCancel = onCancel,
            onUndo = session::undo,
            onDone = onDone,
            modifier = Modifier.align(Alignment.TopCenter)
        )
        ColorRow(
            selectedArgb = session.brushColorArgb,
            onSelect = { session.brushColorArgb = it.toArgb() },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun DrawingSurface(rect: FitRect, session: DrawingSession) {
    val live = remember { mutableStateListOf<NormalizedPoint>() }

    fun commit() {
        if (live.isNotEmpty()) {
            session.add(DrawStroke(session.brushColorArgb, BRUSH_WIDTH_FRACTION, live.toList()))
            live.clear()
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(rect) {
                detectDragGestures(
                    onDragStart = { start ->
                        live.clear()
                        live.add(toNormalized(start.x, start.y, rect))
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        live.add(toNormalized(change.position.x, change.position.y, rect))
                    },
                    onDragEnd = ::commit,
                    onDragCancel = ::commit
                )
            }
    ) {
        session.strokes.forEach { drawStroke(it.colorArgb, it.widthFraction, it.points, rect) }
        drawStroke(session.brushColorArgb, BRUSH_WIDTH_FRACTION, live.toList(), rect)
    }
}

private fun DrawScope.drawStroke(colorArgb: Int, widthFraction: Float, points: List<NormalizedPoint>, rect: FitRect) {
    if (points.isEmpty()) return
    val color = Color(colorArgb)
    val width = (widthFraction * rect.width).coerceAtLeast(1f)
    fun NormalizedPoint.toOffset() = Offset(rect.left + x * rect.width, rect.top + y * rect.height)
    if (points.size == 1) {
        drawCircle(color, radius = width / 2f, center = points.first().toOffset())
        return
    }
    val path = Path().apply {
        val first = points.first().toOffset()
        moveTo(first.x, first.y)
        points.drop(1).forEach { point -> point.toOffset().let { lineTo(it.x, it.y) } }
    }
    drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

@Composable
private fun EditorTopBar(
    canUndo: Boolean,
    onCancel: () -> Unit,
    onUndo: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .background(ScrimColor)
            .statusBarsPadding()
            .padding(horizontal = 4.dp)
    ) {
        IconButton(onClick = onCancel) {
            Icon(Icons.Filled.Close, stringResource(R.string.nc_common_dismiss), tint = Color.White)
        }
        Row {
            IconButton(onClick = onUndo, enabled = canUndo) {
                Icon(
                    Icons.AutoMirrored.Filled.Undo,
                    stringResource(R.string.nc_attachment_undo),
                    tint = Color.White.copy(alpha = if (canUndo) 1f else 0.4f)
                )
            }
            IconButton(onClick = onDone) {
                Icon(Icons.Filled.Check, stringResource(R.string.save), tint = Color.White)
            }
        }
    }
}

@Composable
private fun ColorRow(selectedArgb: Int, onSelect: (Color) -> Unit, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(ScrimColor)
            .navigationBarsPadding()
            .padding(vertical = 12.dp)
    ) {
        brushSwatches.forEach { swatch ->
            val isSelected = swatch.color.toArgb() == selectedArgb
            val borderColor = if (isSelected) Color.White else Color.Gray
            val borderWidth = if (isSelected) SWATCH_SELECTED_BORDER_DP else 1
            val name = stringResource(swatch.name)
            Box(
                modifier = Modifier
                    .size(SWATCH_SIZE_DP.dp)
                    .clip(CircleShape)
                    .background(swatch.color)
                    .border(borderWidth.dp, borderColor, CircleShape)
                    .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(swatch.color) }
                    .semantics { contentDescription = name }
            )
        }
    }
}
