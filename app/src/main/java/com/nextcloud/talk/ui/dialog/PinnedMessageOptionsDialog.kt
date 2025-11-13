/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.dialog

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nextcloud.talk.R
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable
fun GetPinnedOptionsDialog(
    shouldDismiss: MutableState<Boolean>,
    context: Context,
    viewThemeUtils: ViewThemeUtils,
    onPin: (zonedDateTime: ZonedDateTime?) -> Unit
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
                dismissOnClickOutside = true
            )
        ) {
            Surface(
                shape = RoundedCornerShape(32.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                ) {
                    PinMessageOptions(onPin)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinMessageOptions(
    onPin: (zonedDateTime: ZonedDateTime?) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDatePickerDialog by remember { mutableStateOf(false) }
    var showTimePickerDialog by remember { mutableStateOf(false) }

    var tempSelectedDate by remember { mutableStateOf<LocalDate?>(null) }

    // Helper to format dates for subtitles
    val formatter = remember { DateTimeFormatter.ofPattern("MMM d, h:mm a") }
    fun getReadableDateTime(dateTime: ZonedDateTime): String {
        return dateTime.format(formatter)
    }

    Column(modifier = modifier) {
        val pinUntil24h = ZonedDateTime.now().plusHours(24)
        TextButton(
            onClick = {
                Log.d("Julius", "Pinned: $pinUntil24h")
                onPin(pinUntil24h)
            },
            content = { Text(stringResource(R.string.pin_24hr)) }
        )

        val pinUntil7d = ZonedDateTime.now().plusDays(7)
        TextButton(
            onClick = {
                Log.d("Julius", "Pinned: $pinUntil7d")
                onPin(pinUntil7d)
            },
            content = { Text(stringResource(R.string.pin_7_days)) }
        )

        val pinUntil30d = ZonedDateTime.now().plusDays(30)
        TextButton(
            onClick = {
                Log.d("Julius", "Pinned: $pinUntil30d")
                onPin(pinUntil30d)
            },
            content = { Text(stringResource(R.string.pin_30_days)) }
        )

        TextButton(
            onClick = {
                onPin(null)
            },
            content = { Text(stringResource(R.string.pin_indefinitely)) }
        )

        HorizontalDivider()

        TextButton(
            onClick = {
                showDatePickerDialog = true // Start the custom-pick flow
            },
            content = { Text(stringResource(R.string.custom)) }
        )
    }

    val minDateTime = ZonedDateTime.now().plusMinutes(15)
    val initialDateTime = ZonedDateTime.now().plusHours(1)

    if (showDatePickerDialog) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialDateTime.toInstant().toEpochMilli(),
            // Ensure user can't select a date in the past
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val minDate = minDateTime.truncatedTo(ChronoUnit.DAYS)
                    return utcTimeMillis >= minDate.toInstant().toEpochMilli()
                }
            }
        )

        DatePickerDialog(
            onDismissRequest = {},
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            // Save the selected date and show the time picker
                            tempSelectedDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                            showTimePickerDialog = true
                        }
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = {}) { Text("Cancel") }
            },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePickerDialog) {
        val timePickerState = rememberTimePickerState(
            initialHour = initialDateTime.hour,
            initialMinute = initialDateTime.minute,
            is24Hour = false // Or true, based on system settings
        )

        TimePickerDialog(
            onDismissRequest = {},
            confirmButton = {
                TextButton(
                    onClick = {
                        val selectedTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                        val finalDateTime = ZonedDateTime.of(
                            tempSelectedDate ?: LocalDate.now(), // Fallback, though should never be null here
                            selectedTime,
                            ZoneId.systemDefault()
                        )

                        // Final validation: check if it's after the minimum time
                        if (finalDateTime.isAfter(minDateTime)) {
                            onPin(finalDateTime)
                        }
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = {}) { Text("Cancel") }
            }
        ) {
            TimePicker(state = timePickerState)
        }
    }
}

@Composable
fun TimePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(PaddingValues(all = 16.dp)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                content()

                Box(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        dismissButton()
                        confirmButton()
                    }
                }
            }
        }
    }
}

