/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.dialog

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nextcloud.talk.R
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import java.text.DateFormat
import java.util.Date

class ScheduledMessagesListCompose(private val viewThemeUtils: ViewThemeUtils) {
    @Composable
    fun ScheduledMessagesDialog(
        shouldDismiss: MutableState<Boolean>,
        context: Context,
        messages: List<ChatMessage>,
        onMessageActions: (ChatMessage) -> Unit
    ) {
        if (shouldDismiss.value) {
            return
        }

        val colorScheme = viewThemeUtils.getColorScheme(context)
        MaterialTheme(colorScheme = colorScheme) {
            Dialog(
                onDismissRequest = {
                    shouldDismiss.value = true
                },
                properties = DialogProperties(
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true,
                    usePlatformDefaultWidth = true
                )
            ) {
                Surface(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.nc_scheduled_messages),
                            style = MaterialTheme.typography.titleMedium
                        )
                        messages.forEachIndexed { index, message ->
                            if (index > 0) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                            }
                            ScheduledMessageRow(
                                message = message,
                                onMessageActions = onMessageActions
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ScheduledMessageRow(
        message: ChatMessage,
        onMessageActions: (ChatMessage) -> Unit
    ) {
        val formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        val scheduledAt = message.sendAt?.toLong() ?: 0L
        val scheduledText = if (scheduledAt > 0) {
            formatter.format(Date(scheduledAt * MILLIS_IN_SECOND))
        } else {
            stringResource(R.string.nc_message_scheduled)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onMessageActions(message) }
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = scheduledText,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.nc_message_scheduled_for, scheduledText),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    companion object {
        private const val MILLIS_IN_SECOND = 1000L
    }
}
