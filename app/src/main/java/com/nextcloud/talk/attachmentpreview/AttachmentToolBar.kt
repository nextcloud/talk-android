/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.attachmentpreview

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditOff
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.CropRotate
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.nextcloud.talk.R

private const val BADGE_BORDER_DP = 1
private const val BADGE_CORNER_RADIUS_DP = 4
private const val BADGE_HORIZONTAL_PADDING_DP = 3
private const val QUALITY_TOGGLE_SIZE_DP = 48
private const val PERMISSION_ICON_SIZE_DP = 18
private const val PERMISSION_COMPACT_PADDING_DP = 15

/** Where more files can come from: the "add" button next to the caption offers these. */
internal data class AddMoreActions(
    val onPickFromGallery: () -> Unit,
    val onTakePhoto: () -> Unit,
    val onTakeVideo: () -> Unit
)

/** What the compact panel under the caption shows. */
internal data class ToolBarState(
    val showQuality: Boolean,
    val highQuality: Boolean,
    val showPermission: Boolean,
    val allowUpdate: Boolean
)

/** [onCrop]/[onDraw] are null when the current file isn't an image (no edit tools for video/documents). */
internal data class ToolBarActions(
    val onCrop: (() -> Unit)?,
    val onDraw: (() -> Unit)?,
    val onHighQualityChange: (Boolean) -> Unit,
    val onAllowUpdateChange: (Boolean) -> Unit
)

/**
 * Telegram-style pill with edit tools (images only), the SD/HD switch and the View-only/Editable choice.
 * Buttons keep their size. When the full pill is wider than its slot, the permission choice shrinks to its icon;
 * should the pill still be too wide (large font), its content scrolls.
 */
@Composable
internal fun AttachmentToolBar(state: ToolBarState, actions: ToolBarActions, modifier: Modifier = Modifier) {
    SubcomposeLayout(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(ScrimColor)
    ) { constraints ->
        val full = subcompose(PermissionStyle.LABELED) { ToolBarContent(state, actions, compactPermission = false) }
        val fullWidth = full.maxOfOrNull { it.maxIntrinsicWidth(Constraints.Infinity) } ?: 0
        val measurables = if (state.showPermission && shouldCompactPermission(fullWidth, constraints.maxWidth)) {
            subcompose(PermissionStyle.ICON_ONLY) { ToolBarContent(state, actions, compactPermission = true) }
        } else {
            full
        }
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
        layout(placeables.maxOfOrNull { it.width } ?: 0, placeables.maxOfOrNull { it.height } ?: 0) {
            placeables.forEach { it.place(0, 0) }
        }
    }
}

private enum class PermissionStyle { LABELED, ICON_ONLY }

@Composable
private fun ToolBarContent(state: ToolBarState, actions: ToolBarActions, compactPermission: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp)
    ) {
        actions.onCrop?.let { onCrop ->
            ToolIconButton(Icons.Outlined.CropRotate, R.string.nc_attachment_crop, onCrop)
        }
        actions.onDraw?.let { onDraw ->
            ToolIconButton(Icons.Outlined.Brush, R.string.nc_attachment_draw, onDraw)
        }
        if (state.showQuality) {
            QualityToggle(state.highQuality, actions.onHighQualityChange)
        }
        if (state.showPermission) {
            PermissionChoice(state.allowUpdate, compactPermission, actions.onAllowUpdateChange)
        }
    }
}

@Composable
private fun ToolIconButton(icon: ImageVector, @StringRes description: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(imageVector = icon, contentDescription = stringResource(description), tint = Color.White)
    }
}

@Composable
private fun TextBadge(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier
            .border(BADGE_BORDER_DP.dp, color, RoundedCornerShape(BADGE_CORNER_RADIUS_DP.dp))
            .padding(horizontal = BADGE_HORIZONTAL_PADDING_DP.dp)
    )
}

/** SD = compressed, HD = original; one tap flips it, the chip on the photo shows the resulting size. */
@Composable
private fun QualityToggle(highQuality: Boolean, onHighQualityChange: (Boolean) -> Unit) {
    val description = stringResource(R.string.nc_media_quality_original)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(QUALITY_TOGGLE_SIZE_DP.dp)
            .clip(CircleShape)
            .toggleable(value = highQuality, role = Role.Switch, onValueChange = onHighQualityChange)
            .semantics { contentDescription = description }
    ) {
        TextBadge(if (highQuality) "HD" else "SD", Color.White)
    }
}

@Composable
private fun PermissionChoice(allowUpdate: Boolean, compact: Boolean, onAllowUpdateChange: (Boolean) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = stringResource(
        if (allowUpdate) R.string.nc_file_permission_editable else R.string.nc_file_permission_view_only
    )

    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .clickable(role = Role.Button, onClickLabel = label) { expanded = true }
                .padding(
                    horizontal = if (compact) PERMISSION_COMPACT_PADDING_DP.dp else 10.dp,
                    vertical = if (compact) PERMISSION_COMPACT_PADDING_DP.dp else 12.dp
                )
        ) {
            Icon(
                imageVector = if (allowUpdate) Icons.Filled.Edit else Icons.Filled.EditOff,
                contentDescription = if (compact) label else null,
                tint = Color.White,
                modifier = Modifier.size(PERMISSION_ICON_SIZE_DP.dp)
            )
            if (!compact) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.nc_file_permission_view_only)) },
                leadingIcon = { Icon(Icons.Filled.EditOff, contentDescription = null) },
                trailingIcon = { if (!allowUpdate) Icon(Icons.Filled.Check, contentDescription = null) },
                onClick = {
                    onAllowUpdateChange(false)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.nc_file_permission_editable)) },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                trailingIcon = { if (allowUpdate) Icon(Icons.Filled.Check, contentDescription = null) },
                onClick = {
                    onAllowUpdateChange(true)
                    expanded = false
                }
            )
        }
    }
}

/** The labeled View-only/Editable choice is used while the full pill fits the slot; otherwise only its icon. */
internal fun shouldCompactPermission(fullWidthPx: Int, availableWidthPx: Int): Boolean = fullWidthPx > availableWidthPx
