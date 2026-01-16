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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
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

const val VAL_24 = 24L
const val VAL_7 = 7L
const val VAL_30 = 30L

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
                    PinMessageOptions(shouldDismiss, onPin)
                }
            }
        }
    }
}

@Suppress("LongMethod")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinMessageOptions(
    shouldDismiss: MutableState<Boolean>,
    onPin: (zonedDateTime: ZonedDateTime?) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDateTimePickerDialog by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    var tempSelectedDate by remember { mutableStateOf<LocalDate?>(null) }

    // Helper to format dates for subtitles
    val formatter = remember { DateTimeFormatter.ofPattern("MMM d, h:mm a") }
    fun getReadableDateTime(dateTime: ZonedDateTime): String = dateTime.format(formatter)

    Column(modifier = modifier) {
        val pinUntil24h = ZonedDateTime.now().plusHours(VAL_24)
        TextButton(
            onClick = {
                onPin(pinUntil24h)
            },
            content = { Text(stringResource(R.string.pin_24hr)) }
        )

        val pinUntil7d = ZonedDateTime.now().plusDays(VAL_7)
        TextButton(
            onClick = {
                onPin(pinUntil7d)
            },
            content = { Text(stringResource(R.string.pin_7_days)) }
        )

        val pinUntil30d = ZonedDateTime.now().plusDays(VAL_30)
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
            content = { Text(stringResource(R.string.until_unpin)) }
        )

        HorizontalDivider()

        TextButton(
            onClick = {
                showDateTimePickerDialog = true
            },
            content = { Text(stringResource(R.string.custom)) }
        )
    }

    val minDateTime = ZonedDateTime.now().plusMinutes(1)
    val initialDateTime = ZonedDateTime.now().plusHours(1)

    if (showDateTimePickerDialog) {
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

        val timePickerState = rememberTimePickerState(
            initialHour = initialDateTime.hour,
            initialMinute = initialDateTime.minute,
            is24Hour = false // Or true, based on system settings
        )

        DatePickerDialog(
            onDismissRequest = {
                shouldDismiss.value = true
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (showTimePicker) {
                            val selectedTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                            val finalDateTime = ZonedDateTime.of(
                                tempSelectedDate ?: LocalDate.now(), // Fallback, though should never be null here
                                selectedTime,
                                ZoneId.systemDefault()
                            )

                            if (finalDateTime.isAfter(minDateTime)) {
                                onPin(finalDateTime)
                            }
                        } else {
                            datePickerState.selectedDateMillis?.let { millis ->
                                // This is NOT redundant
                                tempSelectedDate = Instant.ofEpochMilli(millis)
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDate()

                                showTimePicker = true
                            }
                        }
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = {
                    shouldDismiss.value = true
                }) { Text("Cancel") }
            },
            modifier = Modifier.background(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(PaddingValues(all = 16.dp)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (showTimePicker) {
                    TimePicker(
                        state = timePickerState,
                        modifier = Modifier
                            .offset(16.dp, 16.dp)
                    )
                } else {
                    DatePicker(state = datePickerState)
                }
            }
        }
    }
}
