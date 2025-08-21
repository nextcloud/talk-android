/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.dialog

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSizeIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.asFlow
import autodagger.AutoInjector
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.chat.viewmodels.ChatViewModel
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters.nextOrSame
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class DateTimeCompose(val bundle: Bundle) {
    private var timeState = mutableStateOf(LocalDateTime.ofEpochSecond(0, 0, ZoneOffset.MIN))

    init {
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
        val user = currentUserProvider.currentUser.blockingGet()
        val roomToken = bundle.getString(BundleKeys.KEY_ROOM_TOKEN)!!
        val messageId = bundle.getString(BundleKeys.KEY_MESSAGE_ID)!!
        val apiVersion = bundle.getInt(BundleKeys.KEY_CHAT_API_VERSION)
        chatViewModel.getReminder(user, roomToken, messageId, apiVersion)
    }

    @Inject
    lateinit var chatViewModel: ChatViewModel

    @Inject
    lateinit var currentUserProvider: CurrentUserProviderNew

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    @Composable
    fun GetDateTimeDialog(shouldDismiss: MutableState<Boolean>, context: Context) {
        if (shouldDismiss.value) {
            return
        }

        val colorScheme = viewThemeUtils.getColorScheme(context)
        val isCollapsed = remember { mutableStateOf(true) }

        MaterialTheme(colorScheme = colorScheme) {
            Dialog(
                onDismissRequest = {
                    shouldDismiss.value = true
                },
                properties = DialogProperties(
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true,
                    usePlatformDefaultWidth = isCollapsed.value
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
                        Header()
                        Body()
                        CollapsableDateTime(shouldDismiss, isCollapsed)
                    }
                }
            }
        }
    }

    @Composable
    private fun Submission(shouldDismiss: MutableState<Boolean>) {
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            TextButton(
                onClick = {
                    val user = currentUserProvider.currentUser.blockingGet()
                    val roomToken = bundle.getString(BundleKeys.KEY_ROOM_TOKEN)!!
                    val messageId = bundle.getString(BundleKeys.KEY_MESSAGE_ID)!!
                    val apiVersion = bundle.getInt(BundleKeys.KEY_CHAT_API_VERSION)
                    chatViewModel.deleteReminder(user, roomToken, messageId, apiVersion)
                    shouldDismiss.value = true
                },
                modifier = Modifier
                    .weight(CUBED_PADDING)
            ) {
                Text(
                    stringResource(R.string.nc_delete),
                    color = Color.Red
                )
            }

            TextButton(
                onClick = {
                    shouldDismiss.value = true
                },
                modifier = Modifier
                    .weight(CUBED_PADDING)
            ) {
                Text(stringResource(R.string.close))
            }

            TextButton(
                onClick = {
                    val user = currentUserProvider.currentUser.blockingGet()
                    val roomToken = bundle.getString(BundleKeys.KEY_ROOM_TOKEN)!!
                    val messageId = bundle.getString(BundleKeys.KEY_MESSAGE_ID)!!
                    val apiVersion = bundle.getInt(BundleKeys.KEY_CHAT_API_VERSION)
                    val offset = timeState.value.atZone(ZoneOffset.systemDefault()).offset
                    val timeVal = timeState.value.toEpochSecond(offset)
                    chatViewModel.setReminder(user, roomToken, messageId, timeVal.toInt(), apiVersion)
                    shouldDismiss.value = true
                },
                modifier = Modifier
                    .weight(CUBED_PADDING)
            ) {
                Text(stringResource(R.string.set))
            }
        }
    }

    @Composable
    private fun Body() {
        val context = LocalContext.current
        val currTime = LocalDateTime.now()

        val timeFormatter = DateTimeFormatter.ofPattern(timePattern(context))
        val dayTimeFormatter = DateTimeFormatter.ofPattern(dayTimePattern(context))

        val laterToday = LocalDateTime.now()
            .withHour(INT_18)
            .withMinute(0)
            .withSecond(0)
        val laterTodayStr = laterToday.format(timeFormatter)

        val tomorrow = LocalDateTime.now()
            .plusDays(1)
            .withHour(INT_8)
            .withMinute(0)
            .withSecond(0)
        val tomorrowStr = tomorrow.format(dayTimeFormatter)

        val thisWeekend = LocalDateTime.now()
            .with(nextOrSame(DayOfWeek.SATURDAY))
            .withHour(INT_8)
            .withMinute(0)
            .withSecond(0)
        val thisWeekendStr = thisWeekend.format(dayTimeFormatter)

        val nextWeek = LocalDateTime.now()
            .plusWeeks(1)
            .with(DayOfWeek.MONDAY)
            .withHour(INT_8)
            .withMinute(0)
            .withSecond(0)
        val nextWeekStr = nextWeek.format(dayTimeFormatter)

        if (currTime < laterToday) {
            TimeOption(
                label = stringResource(R.string.later_today),
                timeString = laterTodayStr
            ) {
                setTime(laterToday)
            }
        }

        TimeOption(
            label = stringResource(R.string.tomorrow),
            timeString = tomorrowStr
        ) {
            setTime(tomorrow)
        }

        if (currTime.dayOfWeek < DayOfWeek.FRIDAY) {
            TimeOption(
                label = stringResource(R.string.this_weekend),
                timeString = thisWeekendStr
            ) {
                setTime(thisWeekend)
            }
        }

        if (currTime.dayOfWeek != DayOfWeek.SUNDAY) {
            TimeOption(
                label = stringResource(R.string.next_week),
                timeString = nextWeekStr
            ) {
                setTime(nextWeek)
            }
        }

        HorizontalDivider()
    }

    private fun setTime(localDateTime: LocalDateTime) {
        timeState.value = localDateTime
        chatViewModel.overrideReminderState()
    }

    @Composable
    private fun Header() {
        val context = LocalContext.current
        Row(
            modifier = Modifier
                .padding(INT_8.dp)
                .fillMaxWidth()
        ) {
            Text(stringResource(R.string.nc_remind), modifier = Modifier.weight(HALF_WEIGHT))

            val reminderState = chatViewModel.getReminderExistState
                .asFlow()
                .collectAsState(ChatViewModel.GetReminderStartState)

            when (reminderState.value) {
                is ChatViewModel.GetReminderExistState -> {
                    val timeL =
                        (reminderState.value as ChatViewModel.GetReminderExistState).reminder.timestamp!!.toLong()
                    timeState.value = LocalDateTime.ofInstant(Instant.ofEpochSecond(timeL), ZoneId.systemDefault())
                }

                else -> {}
            }

            val timeText = if (timeState.value != LocalDateTime.ofEpochSecond(0, 0, ZoneOffset.MIN)) {
                timeState.value.format(DateTimeFormatter.ofPattern(fullPattern(context)))
            } else {
                ""
            }

            Text(
                timeText,
                modifier = Modifier.weight(HALF_WEIGHT)
            )
        }
        HorizontalDivider()
    }

    @SuppressLint("UnusedBoxWithConstraintsScope")
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun CollapsableDateTime(shouldDismiss: MutableState<Boolean>, isCollapsed: MutableState<Boolean>) {
        GeneralIconButton(icon = Icons.Filled.DateRange, label = "Custom") { isCollapsed.value = !isCollapsed.value }
        val scrollState = rememberScrollState()
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.verticalScroll(scrollState)
        ) {
            if (!isCollapsed.value) {
                val todayMillis = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val currentYear = LocalDate.now().year
                val selectableDates = object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= todayMillis

                    override fun isSelectableYear(year: Int): Boolean = year >= currentYear
                }

                val datePickerState = rememberDatePickerState(
                    selectableDates = selectableDates
                )
                val now = LocalDateTime.now()
                val timePickerState = rememberTimePickerState(
                    initialHour = now.hour,
                    initialMinute = now.minute,
                    is24Hour = DateFormat.is24HourFormat(LocalContext.current)
                )

                BoxWithConstraints(
                    modifier = Modifier
                        .requiredSizeIn(minWidth = 360.dp)
                ) {
                    val scale = remember(maxWidth) { if (maxWidth < 360.dp) maxWidth / 360.dp else 1f }

                    DatePicker(
                        state = datePickerState,
                        modifier = Modifier
                            .scale(scale)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                TimePicker(
                    state = timePickerState
                )

                val date = datePickerState.selectedDateMillis?.let {
                    val instant = Instant.ofEpochMilli(it)
                    LocalDateTime.ofInstant(instant, ZoneOffset.UTC) // Google sends time in UTC
                }
                if (date != null) {
                    val year = date.year
                    val month = date.month
                    val day = date.dayOfMonth
                    val hour = timePickerState.hour
                    val minute = timePickerState.minute
                    val newTime = LocalDateTime.of(year, month, day, hour, minute)
                    setTime(newTime)
                } else {
                    val newTime = LocalDate.now().atTime(timePickerState.hour, timePickerState.minute)
                    setTime(newTime)
                }
            }
            Submission(shouldDismiss)
        }
    }

    @Composable
    fun GeneralIconButton(icon: ImageVector, label: String, onClick: () -> Unit) {
        TextButton(
            onClick = onClick
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(INT_24.dp)
                )
                Spacer(modifier = Modifier.width(INT_8.dp))
                Text(text = label)
            }
        }
    }

    private fun timePattern(context: Context): String = if (DateFormat.is24HourFormat(context)) "HH:mm" else "hh:mm a"

    private fun dayTimePattern(context: Context): String =
        if (DateFormat.is24HourFormat(context)) "EEE, HH:mm" else "EEE, hh:mm a"

    private fun fullPattern(context: Context): String =
        if (DateFormat.is24HourFormat(context)) "dd MMM, HH:mm" else "dd MMM, hh:mm a"

    @Composable
    private fun TimeOption(label: String, timeString: String, onClick: () -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(INT_8.dp)
                .clickable { onClick() }
        ) {
            Text(label, modifier = Modifier.weight(HALF_WEIGHT))
            Text(timeString, modifier = Modifier.weight(HALF_WEIGHT))
        }
    }

    companion object {
        private const val HALF_WEIGHT = 0.5f
        private const val INT_8 = 8
        private const val INT_16 = 16
        private const val INT_18 = 18
        private const val INT_24 = 24
        private const val CUBED_PADDING = 0.33f
    }
}
