/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.dialog

import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import autodagger.AutoInjector
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.chat.viewmodels.ChatViewModel
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.bundle.BundleKeys
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters.nextOrSame
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class DateTimeCompose(val bundle: Bundle) {
    private var timeState = mutableStateOf(LocalDateTime.now())

    init {
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
    }

    @Inject
    lateinit var chatViewModel: ChatViewModel

    @Inject
    lateinit var userManager: UserManager

    @Composable
    fun GetDateTimeDialog(shouldDismiss: MutableState<Boolean>) {
        if (shouldDismiss.value) {
            return
        }

        Dialog(
            onDismissRequest = {
                shouldDismiss.value = true
            },
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
             Surface(
                 shape = RoundedCornerShape(8.dp),
                 color = Color.White
             ) {
                 Column(
                     modifier = Modifier
                         .padding(16.dp)
                         .fillMaxWidth()
                 ) {
                     Header()
                     Body()
                     CollapsableDateTime(shouldDismiss)
                 }
             }
        }
    }

    @Composable
    private fun Submission(shouldDismiss: MutableState<Boolean>) {
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            TextButton(onClick = {
                val user = userManager.currentUser.blockingGet()
                val roomToken = bundle.getString(BundleKeys.KEY_ROOM_TOKEN)!!
                val messageId = bundle.getString(BundleKeys.KEY_MESSAGE_ID)!!
                val apiVersion = bundle.getInt(BundleKeys.KEY_CHAT_API_VERSION)
                chatViewModel.deleteReminder(user, roomToken, messageId, apiVersion)
                shouldDismiss.value = true
            },
            modifier = Modifier
                .weight(.33f)
            ) {
                Text(
                    "Delete",
                    color = Color.Red,
                )
            }

            TextButton(onClick = {
                val user = userManager.currentUser.blockingGet()
                val roomToken = bundle.getString(BundleKeys.KEY_ROOM_TOKEN)!!
                val messageId = bundle.getString(BundleKeys.KEY_MESSAGE_ID)!!
                val apiVersion = bundle.getInt(BundleKeys.KEY_CHAT_API_VERSION)
                chatViewModel.setReminder(user, roomToken, messageId, timeState.value.nano, apiVersion) // TODO verify
                shouldDismiss.value = true
            },
                modifier = Modifier
                    .weight(.33f)
            ) {
                Text("Set")
            }

            TextButton(onClick = {
                shouldDismiss.value = true
            },
                modifier = Modifier
                    .weight(.33f)
            ) {
                Text("Close")
            }
        }
    }

    @Suppress("DEPRECATION")
    @Composable
    private fun Body() {
        val currTime = LocalDateTime.now()

        val laterToday = LocalDateTime.now()
            .withHour(18)
            .withMinute(0)
            .withSecond(0)

        val tomorrow = LocalDateTime.now()
            .plusDays(1)
            .withHour(8)
            .withMinute(0)
            .withSecond(0)

        val thisWeekend = LocalDateTime.now()
            .with(nextOrSame(DayOfWeek.SATURDAY))
            .withHour(8)
            .withMinute(0)
            .withSecond(0)

        val nextWeek = LocalDateTime.now()
            .plusWeeks(1)
            .with(nextOrSame(DayOfWeek.MONDAY))
            .withHour(8)
            .withMinute(0)
            .withSecond(0)

        if (currTime < laterToday) {
            ClickableText(
                AnnotatedString("Later today"),
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth()
            ) {
                timeState.value = laterToday
            }
        }

        ClickableText(
            AnnotatedString("Tomorrow"),
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth()
        ) {
            timeState.value = tomorrow
        }

        ClickableText(
            AnnotatedString("This weekend"),
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth()
        ) {
            timeState.value = thisWeekend
        }

        ClickableText(
            AnnotatedString("Next week"),
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth()
        ) {
            timeState.value = nextWeek
        }
        HorizontalDivider()
    }

    @Composable
    private fun Header() {
        Row(
            modifier = Modifier
                .padding(8.dp)
        ) {
            Text("Remind Me Later")
            Spacer(modifier = Modifier.width(32.dp))
            // TODO this needs to get it from the server
            //  this will be tricky, need to figure this out

            Text(timeState.value.format(DateTimeFormatter.ofPattern("dd MMM, HH:mm a")))

        }
        HorizontalDivider()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun CollapsableDateTime(shouldDismiss: MutableState<Boolean>) {
        var isCollapsed by remember { mutableStateOf(true) }
        GeneralIconButton(icon = Icons.Filled.DateRange, label = "Custom") { isCollapsed = !isCollapsed }
        val scrollState = rememberScrollState()
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.verticalScroll(scrollState)
        ) {
            if (!isCollapsed) {
                val datePickerState = rememberDatePickerState()
                val timePickerState = rememberTimePickerState()
                DatePicker(
                    state = datePickerState,
                    modifier = Modifier.scale(0.9f)
                )
                TimePicker(
                    state = timePickerState,
                    modifier = Modifier.scale(0.9f)
                )

                val date = datePickerState.selectedDateMillis?.let {
                    LocalDateTime.ofEpochSecond(it / 1000, 0, ZoneOffset.UTC)
                }
                if (date != null) {
                    val year = date.year
                    val month = date.month
                    val day = date.dayOfMonth
                    val hour = timePickerState.hour
                    val minute = timePickerState.minute
                    timeState.value = LocalDateTime.of(year, month, day, hour, minute)
                } else {
                    timeState.value = LocalDate.now().atTime(timePickerState.hour, timePickerState.minute)
                }

            }
            Submission(shouldDismiss)
        }
    }

    @Composable
    fun GeneralIconButton(
        icon: ImageVector,
        label: String,
        onClick: () -> Unit
    ) {
        TextButton(
            onClick = onClick) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = label)
            }
        }
    }

    // Preview Logic
    // class DummyProvider : PreviewParameterProvider<String> {
    //     override val values: Sequence<String> = sequenceOf()
    // }
    // @Preview()
    // @PreviewParameter(DummyProvider::class)
    // @Composable
    // fun PreviewDateTimeDialog() {
    //     GetDateTimeDialog()
    // }
}
