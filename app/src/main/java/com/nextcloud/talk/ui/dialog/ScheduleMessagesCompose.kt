/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.dialog

import android.annotation.SuppressLint
import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import autodagger.AutoInjector
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.ui.theme.ViewThemeUtils
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
class ScheduleMessageCompose(private val onSchedule: (LocalDateTime) -> Unit) {
    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    init {
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
    }

    @Composable
    fun GetScheduleDialog(shouldDismiss: MutableState<Boolean>, context: Context) {
        if (shouldDismiss.value) {
            return
        }

        val colorScheme = viewThemeUtils.getColorScheme(context)
        val showCustomPicker = remember { mutableStateOf(false) }

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
                        Header()
                        Body(
                            onSchedule = { time ->
                                onSchedule(time)
                                shouldDismiss.value = true
                            }
                        )
                        HorizontalDivider()
                        CustomTimePicker(showCustomPicker, shouldDismiss, onSchedule)
                    }
                }
            }
        }
    }

    @Composable
    private fun Header() {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(bottom = INT_8.dp)
                .fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                modifier = Modifier.size(INT_24.dp)
            )
            Spacer(modifier = Modifier.width(INT_8.dp))
            Text(stringResource(R.string.nc_send_later))
        }
        HorizontalDivider()
    }

    @Composable
    private fun Body(onSchedule: (LocalDateTime) -> Unit) {
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
                onSchedule(laterToday)
            }
        }

        TimeOption(
            label = stringResource(R.string.tomorrow),
            timeString = tomorrowStr
        ) {
            onSchedule(tomorrow)
        }

        if (currTime.dayOfWeek < DayOfWeek.FRIDAY) {
            TimeOption(
                label = stringResource(R.string.this_weekend),
                timeString = thisWeekendStr
            ) {
                onSchedule(thisWeekend)
            }
        }

        if (currTime.dayOfWeek != DayOfWeek.SUNDAY) {
            TimeOption(
                label = stringResource(R.string.next_week),
                timeString = nextWeekStr
            ) {
                onSchedule(nextWeek)
            }
        }
    }

    @SuppressLint("UnusedBoxWithConstraintsScope")
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun CustomTimePicker(
        showCustomPicker: MutableState<Boolean>,
        shouldDismiss: MutableState<Boolean>,
        onSchedule: (LocalDateTime) -> Unit
    ) {
        val scrollState = rememberScrollState()
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.verticalScroll(scrollState)
        ) {
            GeneralIconButton(icon = Icons.Filled.DateRange, label = stringResource(R.string.choose_a_time)) {
                showCustomPicker.value = !showCustomPicker.value
            }
            if (showCustomPicker.value) {
                val todayMillis = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val currentYear = LocalDate.now().year
                val selectableDates = object : androidx.compose.material3.SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= todayMillis

                    override fun isSelectableYear(year: Int): Boolean = year >= currentYear
                }

                val datePickerState = rememberDatePickerState(selectableDates = selectableDates)
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
                            .padding(bottom = INT_8.dp)
                            .scale(scale),
                        colors = DatePickerDefaults.colors(
                            containerColor = colorResource(R.color.bg_default)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                TimePicker(
                    state = timePickerState
                )

                val date = datePickerState.selectedDateMillis?.let {
                    val instant = Instant.ofEpochMilli(it)
                    LocalDateTime.ofInstant(instant, ZoneOffset.UTC)
                }
                val updatedTime = if (date != null) {
                    LocalDateTime.of(date.year, date.month, date.dayOfMonth, timePickerState.hour, timePickerState.minute)
                } else {
                    LocalDate.now().atTime(timePickerState.hour, timePickerState.minute)
                }

                TextButton(
                    onClick = {
                        onSchedule(updatedTime)
                        shouldDismiss.value = true
                    }
                ) {
                    Text(stringResource(R.string.send_at_custom_time))
                }
            }
        }
    }

    @Composable
    private fun GeneralIconButton(icon: ImageVector, label: String, onClick: () -> Unit) {
        TextButton(
            onClick = onClick
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
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
    }
}
