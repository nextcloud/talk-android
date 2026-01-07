/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.dialog

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import autodagger.AutoInjector
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.models.json.chat.ChatMessageJson
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class ScheduledMessagesCompose(
    private val messages: List<ChatMessageJson>,
    private val onReschedule: (ChatMessageJson) -> Unit,
    private val onSendNow: (ChatMessageJson) -> Unit,
    private val onEdit: (ChatMessageJson) -> Unit,
    private val onDelete: (ChatMessageJson) -> Unit
) {
    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    init {
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
    }

    @Composable
    fun GetScheduledMessagesDialog(shouldDismiss: MutableState<Boolean>, context: Context) {
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
                Surface(
                    shape = RoundedCornerShape(INT_8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(INT_16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.scheduled_messages),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { shouldDismiss.value = true }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_baseline_close_24),
                                    contentDescription = stringResource(R.string.close)
                                )
                            }
                        }
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(INT_8.dp))
                        LazyColumn {
                            items(messages, key = { it.id }) { message ->
                                ScheduledMessageRow(
                                    message = message,
                                    onReschedule = onReschedule,
                                    onSendNow = onSendNow,
                                    onEdit = onEdit,
                                    onDelete = onDelete
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ScheduledMessageRow(
        message: ChatMessageJson,
        onReschedule: (ChatMessageJson) -> Unit,
        onSendNow: (ChatMessageJson) -> Unit,
        onEdit: (ChatMessageJson) -> Unit,
        onDelete: (ChatMessageJson) -> Unit
    ) {
        val menuExpanded = remember { mutableStateOf(false) }
        val context = LocalContext.current
        val initials = message.actorDisplayName
            ?.split(" ")
            ?.filter { it.isNotBlank() }
            ?.take(2)
            ?.joinToString("") { it.first().uppercase() }
            .orEmpty()
            .ifEmpty { "?" }

        val timeFormatter = DateTimeFormatter.ofPattern(fullPattern(context))
        val scheduledTime = Instant.ofEpochSecond(message.timestamp)
            .atZone(ZoneId.systemDefault())
            .format(timeFormatter)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(vertical = INT_8.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = initials,
                modifier = Modifier
                    .size(INT_40.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                    .padding(INT_8.dp),
                maxLines = 1
            )
            Spacer(modifier = Modifier.width(INT_8.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = message.message.orEmpty(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = scheduledTime,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            IconButton(onClick = { menuExpanded.value = true }) {
                Icon(
                    painter = painterResource(R.drawable.ic_more_horiz_black_24dp),
                    contentDescription = stringResource(R.string.nc_common_more_options)
                )
            }
            DropdownMenu(
                expanded = menuExpanded.value,
                onDismissRequest = { menuExpanded.value = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.reschedule)) },
                    onClick = {
                        menuExpanded.value = false
                        onReschedule(message)
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.send_now)) },
                    onClick = {
                        menuExpanded.value = false
                        onSendNow(message)
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.edit)) },
                    onClick = {
                        menuExpanded.value = false
                        onEdit(message)
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.nc_delete)) },
                    onClick = {
                        menuExpanded.value = false
                        onDelete(message)
                    }
                )
            }
        }
    }

    private fun fullPattern(context: Context): String =
        if (DateFormat.is24HourFormat(context)) "dd MMM, HH:mm" else "dd MMM, hh:mm a"

    companion object {
        private const val INT_8 = 8
        private const val INT_16 = 16
        private const val INT_40 = 40
    }
}
