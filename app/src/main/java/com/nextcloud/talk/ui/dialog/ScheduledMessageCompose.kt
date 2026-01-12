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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nextcloud.talk.R
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import java.text.DateFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters.nextOrSame
import java.util.Date
import java.util.Locale

class ScheduledMessageCompose(private val viewThemeUtils: ViewThemeUtils) {
    private data class ScheduleOption(val label: String, val timeSeconds: Long)

    @Composable
    fun ScheduleDialog(shouldDismiss: MutableState<Boolean>, context: Context, onTimeSelected: (Long) -> Unit) {
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
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.nc_send_later),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        ScheduleOptions(onTimeSelected, shouldDismiss)
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(12.dp))
                        CustomDateTimePicker(onTimeSelected, shouldDismiss)
                    }
                }
            }
        }
    }

    @Composable
    private fun ScheduleOptions(onTimeSelected: (Long) -> Unit, shouldDismiss: MutableState<Boolean>) {
        val context = LocalContext.current
        val formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        val options = listOf(
            ScheduleOption(
                context.getString(R.string.nc_schedule_tomorrow, formatter.format(tomorrow().toDate())),
                tomorrow().atZone(ZoneId.systemDefault()).toEpochSecond()
            ),
            ScheduleOption(
                context.getString(R.string.nc_schedule_this_weekend, formatter.format(thisWeekend().toDate())),
                thisWeekend().atZone(ZoneId.systemDefault()).toEpochSecond()
            ),
            ScheduleOption(
                context.getString(R.string.nc_schedule_next_week, formatter.format(nextWeek().toDate())),
                nextWeek().atZone(ZoneId.systemDefault()).toEpochSecond()
            )
        )

        options.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onTimeSelected(option.timeSeconds)
                        shouldDismiss.value = true
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun CustomDateTimePicker(onTimeSelected: (Long) -> Unit, shouldDismiss: MutableState<Boolean>) {
        val now = LocalDateTime.now()
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = System.currentTimeMillis(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val today = LocalDate.now(ZoneId.systemDefault())
                    val selectedDate = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                    return !selectedDate.isBefore(today)
                }
            }
        )
        val timePickerState = rememberTimePickerState(
            initialHour = now.hour,
            initialMinute = now.minute
        )
        val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.getDefault())
        val selectedMillis = datePickerState.selectedDateMillis ?: System.currentTimeMillis()
        val selectedDate = Instant.ofEpochMilli(selectedMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        val selectedDateTime = selectedDate
            .atTime(timePickerState.hour, timePickerState.minute)
        val displayText = selectedDateTime.format(formatter)

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = Icons.Filled.DateRange, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(R.string.nc_schedule_custom_time))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = displayText, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(8.dp))
        DatePicker(
            state = datePickerState,
            showModeToggle = false,
            colors = DatePickerDefaults.colors()
        )
        Spacer(modifier = Modifier.height(8.dp))
        TimePicker(state = timePickerState)
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                onTimeSelected(selectedDateTime.atZone(ZoneId.systemDefault()).toEpochSecond())
                shouldDismiss.value = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.nc_send_at_custom_time))
        }
    }

    private fun tomorrow(): LocalDateTime =
        LocalDateTime.now()
            .plusDays(1)
            .withHour(DEFAULT_HOUR)
            .withMinute(DEFAULT_MINUTE)
            .withSecond(0)
            .withNano(0)

    private fun thisWeekend(): LocalDateTime =
        LocalDateTime.now()
            .with(nextOrSame(DayOfWeek.SATURDAY))
            .withHour(DEFAULT_HOUR)
            .withMinute(DEFAULT_MINUTE)
            .withSecond(0)
            .withNano(0)

    private fun nextWeek(): LocalDateTime =
        LocalDateTime.now()
            .plusWeeks(1)
            .with(DayOfWeek.MONDAY)
            .withHour(DEFAULT_HOUR)
            .withMinute(DEFAULT_MINUTE)
            .withSecond(0)
            .withNano(0)

    private fun LocalDateTime.toDate(): Date =
        Date.from(atZone(ZoneId.systemDefault()).toInstant())

    companion object {
        private const val DEFAULT_HOUR = 8
        private const val DEFAULT_MINUTE = 0
        private const val MILLIS_IN_SECOND = 1000L
    }
}
