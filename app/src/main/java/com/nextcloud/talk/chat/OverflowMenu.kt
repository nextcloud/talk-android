/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.nextcloud.talk.R

data class MenuItemData(val title: String, val subtitle: String? = null, val icon: Int? = null, val onClick: () -> Unit)

@Composable
fun OverflowMenu(anchor: View?, expanded: Boolean, items: List<MenuItemData>, onDismiss: () -> Unit) {
    if (!expanded) return

    val rect = anchor?.boundsInWindow()
    val xOffset = rect?.left ?: 0
    val yOffset = rect?.bottom ?: 0

    Popup(
        onDismissRequest = onDismiss,
        offset = IntOffset(xOffset, yOffset)
    ) {
        Column(
            modifier = Modifier
                .width(IntrinsicSize.Max)
                .background(
                    color = colorResource(id = R.color.bg_default),
                    shape = RoundedCornerShape(1.dp)
                )
                .shadow(
                    elevation = 1.dp,
                    shape = RoundedCornerShape(1.dp),
                    clip = false
                )
        ) {
            items.forEach { item ->
                DynamicMenuItem(
                    item.copy(
                        onClick = {
                            item.onClick()
                            onDismiss()
                        }
                    )
                )
            }
        }
    }
}

@Composable
fun DynamicMenuItem(item: MenuItemData) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClick = item.onClick,
                interactionSource = remember { MutableInteractionSource() }
            )
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        item.icon?.let { icon ->
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(8.dp))
        }

        Column {
            Text(item.title, color = MaterialTheme.colorScheme.onSurface)
            item.subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun View.boundsInWindow(): android.graphics.Rect {
    val location = IntArray(2)
    getLocationOnScreen(location)
    return android.graphics.Rect(
        location[0],
        location[1],
        location[0] + width,
        location[1] + height
    )
}

@Preview
@Composable
fun OverflowMenuPreview() {
    val items = listOf(
        MenuItemData(
            title = "first item title",
            subtitle = "first item subtitle",
            icon = R.drawable.baseline_notifications_24,
            onClick = {}
        ),
        MenuItemData(
            title = "second item title",
            subtitle = null,
            icon = R.drawable.outline_notifications_active_24,
            onClick = {}
        ),
        MenuItemData(
            title = "third item title",
            subtitle = null,
            icon = R.drawable.baseline_notifications_24,
            onClick = {}
        ),
        MenuItemData(
            title = "fourth item title",
            subtitle = null,
            icon = R.drawable.baseline_notifications_24,
            onClick = {}
        )
    )

    OverflowMenu(
        anchor = null,
        expanded = true,
        items = items,
        onDismiss = { }
    )
}
