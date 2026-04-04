/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chat

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.nextcloud.talk.chat.ui.model.ChatMessageUi
import com.nextcloud.talk.chat.ui.model.MessageReactionUi
import com.nextcloud.talk.chat.ui.model.MessageStatusIcon
import com.nextcloud.talk.chat.ui.model.MessageTypeContent
import com.nextcloud.talk.ui.theme.LocalViewThemeUtils
import com.nextcloud.talk.utils.preview.ComposePreviewUtils
import java.time.LocalDate

internal val previewReactions = listOf(
    MessageReactionUi(emoji = "👍", amount = 1, isSelfReaction = true),
    MessageReactionUi(emoji = "❤️", amount = 1, isSelfReaction = false)
)

@Preview(showBackground = true, name = "Light")
@Preview(
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
    name = "Dark"
)
@Preview(showBackground = true, locale = "ar", name = "RTL Arabic")
internal annotation class ChatMessagePreviews

@Composable
internal fun PreviewContainer(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val previewUtils = remember { ComposePreviewUtils.getInstance(context) }
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()

    CompositionLocalProvider(
        LocalViewThemeUtils provides previewUtils.viewThemeUtils
    ) {
        MaterialTheme(colorScheme = colorScheme) {
            content()
        }
    }
}

internal fun createBaseMessage(content: MessageTypeContent?): ChatMessageUi =
    ChatMessageUi(
        id = 1,
        message = "Sample message text",
        plainMessage = "Sample message text",
        renderMarkdown = false,
        actorDisplayName = "John Doe",
        isThread = false,
        threadTitle = "",
        threadReplies = 0,
        incoming = true,
        isDeleted = false,
        avatarUrl = null,
        statusIcon = MessageStatusIcon.SENT,
        timestamp = System.currentTimeMillis() / 1000,
        date = LocalDate.now(),
        content = content,
        reactions = previewReactions
    )

internal fun createBaseMessageWithoutCaption(content: MessageTypeContent?): ChatMessageUi =
    ChatMessageUi(
        id = 1,
        message = "",
        plainMessage = "",
        renderMarkdown = false,
        actorDisplayName = "John Doe",
        isThread = false,
        threadTitle = "",
        threadReplies = 0,
        incoming = true,
        isDeleted = false,
        avatarUrl = null,
        statusIcon = MessageStatusIcon.SENT,
        timestamp = System.currentTimeMillis() / 1000,
        date = LocalDate.now(),
        content = content,
        reactions = previewReactions
    )

internal fun createLongBaseMessage(content: MessageTypeContent?): ChatMessageUi =
    ChatMessageUi(
        id = 1,
        message = "Sample message text that is very very very very very long",
        plainMessage = "Sample message text that is very very very very very long",
        renderMarkdown = false,
        actorDisplayName = "John Doe",
        isThread = false,
        threadTitle = "",
        threadReplies = 0,
        incoming = true,
        isDeleted = false,
        avatarUrl = null,
        statusIcon = MessageStatusIcon.SENT,
        timestamp = System.currentTimeMillis() / 1000,
        date = LocalDate.now(),
        content = content,
        reactions = previewReactions
    )
