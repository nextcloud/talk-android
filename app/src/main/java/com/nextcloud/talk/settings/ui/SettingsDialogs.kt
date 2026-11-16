/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.settings.ui

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nextcloud.talk.R

@Composable
internal fun RemoveAccountDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.nc_settings_remove_account)) },
        text = { Text(stringResource(R.string.nc_settings_remove_confirmation)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.nc_settings_remove)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.nc_cancel)) }
        }
    )
}

@Composable
internal fun BatteryOptimizationDialog(onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.nc_ignore_battery_optimization_dialog_title)) },
        text = {
            Text(
                stringResource(
                    R.string.nc_ignore_battery_optimization_dialog_text,
                    stringResource(R.string.nc_app_name)
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.nc_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.nc_common_dismiss)) }
        }
    )
}

@Composable
internal fun NotificationTroubleshootingDialog(
    onOpenChecklist: () -> Unit,
    onOpenDontKillMyApp: () -> Unit,
    onOpenDiagnosis: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.nc_notifications_troubleshooting_dialog_title)) },
        text = { Text(stringResource(R.string.nc_notifications_troubleshooting_dialog_text)) },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onOpenDontKillMyApp) {
                    Text(stringResource(R.string.nc_diagnosis_dialog_open_dontkillmyapp_website))
                }
                TextButton(onClick = onOpenChecklist) {
                    Text(stringResource(R.string.nc_diagnosis_dialog_open_checklist))
                }
                TextButton(onClick = onOpenDiagnosis) {
                    Text(stringResource(R.string.nc_diagnosis_dialog_open_diagnosis))
                }
            }
        }
    )
}

@Composable
internal fun MediaQualityDialog(reducedQuality: Boolean, onSelect: (Boolean) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.nc_settings_media_quality_title)) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                Text(
                    text = stringResource(R.string.nc_media_quality_original_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                MediaQualityOption(
                    label = stringResource(R.string.nc_media_quality_original),
                    selected = !reducedQuality,
                    onClick = { onSelect(false) }
                )
                MediaQualityOption(
                    label = stringResource(R.string.nc_media_quality_reduced),
                    selected = reducedQuality,
                    onClick = { onSelect(true) }
                )
            }
        },
        // selecting an option applies it, so the only button cancels
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.nc_cancel)) }
        }
    )
}

@Composable
private fun MediaQualityOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

/** Asks for the phone number of the account, which the phone book integration needs. */
@Composable
internal fun PhoneNumberDialog(
    isError: Boolean,
    onPhoneNumberChange: () -> Unit,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var phoneNumber by rememberSaveable { mutableStateOf("") }
    val phoneNumberHint = stringResource(R.string.nc_settings_phone_book_integration_phone_number_dialog_edit_text_hint)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.nc_settings_phone_book_integration_phone_number_dialog_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.nc_settings_phone_book_integration_phone_number_dialog_description),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = {
                        phoneNumber = it
                        onPhoneNumberChange()
                    },
                    label = { Text(phoneNumberHint) },
                    prefix = { Text("+") },
                    singleLine = true,
                    isError = isError,
                    supportingText = if (isError) {
                        {
                            Text(
                                stringResource(R.string.nc_settings_phone_book_integration_phone_number_dialog_invalid)
                            )
                        }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(phoneNumber) }, enabled = phoneNumber.isNotBlank()) {
                Text(stringResource(R.string.nc_common_set))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.nc_common_skip)) }
        }
    )
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MediaQualityDialogPreview() {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        MediaQualityDialog(reducedQuality = true, onSelect = {}, onDismiss = {})
    }
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PhoneNumberDialogPreview() {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        PhoneNumberDialog(isError = true, onPhoneNumberChange = {}, onSubmit = {}, onDismiss = {})
    }
}
