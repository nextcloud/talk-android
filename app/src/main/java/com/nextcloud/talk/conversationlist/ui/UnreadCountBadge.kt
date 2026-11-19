/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationlist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nextcloud.talk.R
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.conversations.ConversationEnums

private const val UNREAD_THRESHOLD = 1000
private const val UNREAD_BUBBLE_STROKE_DP = 1.5f

/** Visual emphasis of an unread counter, ordered from lowest to highest priority. */
enum class UnreadBadgeStyle {
    /** Unread messages without a mention. */
    GREY,

    /** Unread group mention (e.g. @all) without a direct mention. */
    OUTLINED,

    /** Unread direct mention, or any unread message in a one-to-one conversation. */
    FILLED
}

/** Returns the badge style for a conversation's unread counter, or null if it has no unread messages. */
fun unreadBadgeStyle(model: ConversationModel, hasDirectMentionFlag: Boolean): UnreadBadgeStyle? {
    if (model.unreadMessages <= 0) return null

    val isOneToOne = model.type == ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL
    val outlined = model.unreadMention && hasDirectMentionFlag && !model.unreadMentionDirect && !isOneToOne

    return when {
        outlined -> UnreadBadgeStyle.OUTLINED
        isOneToOne || model.unreadMention -> UnreadBadgeStyle.FILLED
        else -> UnreadBadgeStyle.GREY
    }
}

@Composable
fun UnreadCountBadge(count: Int, style: UnreadBadgeStyle, modifier: Modifier = Modifier) {
    val text = if (count >= UNREAD_THRESHOLD) {
        stringResource(R.string.tooManyUnreadMessages)
    } else {
        count.toString()
    }

    val shape = CircleShape
    val (boxModifier, textColor) = when (style) {
        UnreadBadgeStyle.FILLED ->
            Modifier.background(MaterialTheme.colorScheme.primary, shape) to MaterialTheme.colorScheme.onPrimary

        UnreadBadgeStyle.OUTLINED ->
            Modifier.border(UNREAD_BUBBLE_STROKE_DP.dp, MaterialTheme.colorScheme.primary, shape) to
                MaterialTheme.colorScheme.primary

        UnreadBadgeStyle.GREY ->
            Modifier.background(colorResource(R.color.conversation_unread_bubble), shape) to
                colorResource(R.color.conversation_unread_bubble_text)
    }

    UnreadBadgeBox(text = text, textColor = textColor, modifier = modifier.atLeastAsWideAsTall().then(boxModifier))
}

/** Makes single digit badges a circle, longer numbers stay a pill. */
private fun Modifier.atLeastAsWideAsTall(): Modifier =
    layout { measurable, constraints ->
        val height = measurable.minIntrinsicHeight(constraints.maxWidth)
        val minWidth = height.coerceIn(constraints.minWidth, constraints.maxWidth)
        val placeable = measurable.measure(constraints.copy(minWidth = minWidth))
        layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
    }

@Composable
private fun UnreadBadgeBox(text: String, textColor: Color, modifier: Modifier) {
    Box(
        modifier = modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}
