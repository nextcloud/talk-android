/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationinfo.ui

import android.content.res.Configuration
import android.text.format.DateFormat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.nextcloud.talk.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * Two-step picker for the lobby timer: a date first, then a time.
 *
 * @param initialEpochSeconds the current lobby timer in seconds, or 0 if none is set
 * @param onConfirm called with the chosen point in time in epoch seconds, never earlier than now
 */
@Composable
fun LobbyTimerPickerDialog(initialEpochSeconds: Long, onConfirm: (epochSeconds: Long) -> Unit, onDismiss: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val initial = remember(initialEpochSeconds) {
        if (initialEpochSeconds != 0L) {
            LocalDateTime.ofInstant(Instant.ofEpochSecond(initialEpochSeconds), zone)
        } else {
            LocalDateTime.now(zone)
        }
    }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }

    val date = selectedDate
    if (date == null) {
        LobbyDatePicker(initial.toLocalDate(), onDateSelected = { selectedDate = it }, onDismiss = onDismiss)
    } else {
        LobbyTimePicker(
            initial.toLocalTime(),
            onTimeSelected = { time ->
                val chosen = ZonedDateTime.of(date, time, zone)
                onConfirm(maxOf(chosen, ZonedDateTime.now(zone)).toEpochSecond())
            },
            onDismiss = onDismiss
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LobbyDatePicker(initialDate: LocalDate, onDateSelected: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    // The date picker works with UTC midnight millis of the selected day
    val today = remember { LocalDate.now() }
    val todayUtcMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = maxOf(initialDate, today).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= todayUtcMillis
            override fun isSelectableYear(year: Int): Boolean = year >= today.year
        }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let {
                        onDateSelected(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                },
                enabled = datePickerState.selectedDateMillis != null
            ) { Text(stringResource(R.string.nc_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.nc_cancel)) }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LobbyTimePicker(initialTime: LocalTime, onTimeSelected: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state = timePickerState) },
        confirmButton = {
            TextButton(onClick = {
                onTimeSelected(LocalTime.of(timePickerState.hour, timePickerState.minute))
            }) { Text(stringResource(R.string.nc_common_set)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.nc_cancel)) }
        }
    )
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "RTL Arabic", locale = "ar")
@Composable
private fun LobbyTimerPickerDialogPreview() {
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        LobbyTimerPickerDialog(initialEpochSeconds = 0L, onConfirm = {}, onDismiss = {})
    }
}
