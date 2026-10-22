/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nextcloud.talk.chat.ui.model.ChatMessageUi
import com.nextcloud.talk.chat.ui.model.MessageTypeContent
import com.nextcloud.talk.chat.viewmodels.isSelectableMessageId

internal const val TAG_MESSAGE_ROW = "chat_message_row"
internal const val TAG_SELECTION_INDICATOR = "chat_message_selection_indicator"

private const val SELECTED_ROW_TINT_ALPHA = 0.12f

/**
 * Selection state of one chat row. [active] is true while any message of the chat is selected; [selectable] tells
 * whether this row can be selected at all; [onToggle] adds the row to the selection or removes it.
 */
internal data class MessageRowSelection(
    val active: Boolean = false,
    val checked: Boolean = false,
    val selectable: Boolean = false,
    val onToggle: () -> Unit = {}
)

internal fun ChatMessageUi.isSelectable(): Boolean =
    content != MessageTypeContent.SystemMessage && !isDeleted && isSelectableMessageId(id)

/**
 * The full-width frame of a chat row. A tap or a long press on the row outside of the bubble ends up here: the tap
 * calls [onTap] and the long press starts the selection. While a selection is active a tap or a long press anywhere
 * on the row toggles it, the content of the bubble is not reachable then. A row that cannot be selected opens the
 * menu with [onOpenMenu] on a long press, like a tap on the bubble does.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MessageRowFrame(
    selection: MessageRowSelection,
    onTap: () -> Unit,
    onOpenMenu: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    val toggle = { if (selection.selectable) selection.onToggle() }
    val interactionSource = remember { MutableInteractionSource() }
    val tint = if (selection.checked) {
        Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = SELECTED_ROW_TINT_ALPHA))
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TAG_MESSAGE_ROW)
            .then(tint)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = { if (selection.active) toggle() else onTap() },
                onDoubleClick = { if (selection.active) toggle() else onOpenMenu() },
                onLongClick = { if (selection.active || selection.selectable) toggle() else onOpenMenu() }
            )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selection.active) {
                SelectionIndicator(selection)
            }
            Box(modifier = Modifier.weight(1f)) {
                content()
                if (selection.active) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = toggle,
                                onLongClick = toggle
                            )
                    )
                }
            }
        }
    }
}

/**
 * Makes the bubble of a message open the message menu on a tap and on a long press, so that the row around it, which
 * selects the message on a long press, never sees a gesture that started on the bubble.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.openMenuOnClick(messageId: Int): Modifier {
    val openMenu = LocalMessageLongClickHandler.current
    return combinedClickable(
        onClick = { openMenu(messageId) },
        onLongClick = { openMenu(messageId) }
    )
}

@Composable
private fun SelectionIndicator(selection: MessageRowSelection) {
    val primary = MaterialTheme.colorScheme.primary
    val base = Modifier
        .padding(start = 12.dp)
        .size(24.dp)
        .testTag(TAG_SELECTION_INDICATOR)
        .semantics { selected = selection.checked }
        .clip(CircleShape)
    if (!selection.selectable) {
        Box(modifier = Modifier.padding(start = 12.dp).size(24.dp))
        return
    }
    Box(
        modifier = if (selection.checked) {
            base.background(primary)
        } else {
            base.border(BorderStroke(2.dp, MaterialTheme.colorScheme.onSurfaceVariant), CircleShape)
        },
        contentAlignment = Alignment.Center
    ) {
        if (selection.checked) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
