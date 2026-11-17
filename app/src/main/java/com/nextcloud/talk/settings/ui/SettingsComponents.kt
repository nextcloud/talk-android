/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.settings.ui

import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private const val DISABLED_ALPHA = 0.38f
private const val HIGHLIGHT_ALPHA = 0.24f
private const val HIGHLIGHT_START_DELAY_MS = 1000L
private const val HIGHLIGHT_BLINK_DURATION_MS = 250
private const val HIGHLIGHT_BLINK_COUNT = 3

@Composable
internal fun SettingsSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}

/**
 * A setting with a [title] and an optional [summary]. Clickable if [onClick] is given and it is [enabled]. With
 * [highlight], it blinks to draw attention to it.
 */
@Suppress("LongParameterList")
@Composable
internal fun SettingsItem(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    summaryColor: Color = Color.Unspecified,
    enabled: Boolean = true,
    highlight: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .blinkingHighlight(highlight)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        if (summary != null) {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = if (summaryColor == Color.Unspecified) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    summaryColor
                }
            )
        }
    }
}

/** A setting that is turned on and off by clicking anywhere on it. */
@Suppress("LongParameterList")
@Composable
internal fun SettingsSwitchItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    supportingText: String? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** A choice of one of [options], given as pairs of the stored value and its label. */
@Suppress("LongParameterList")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsDropdown(
    label: String,
    options: List<Pair<String, String>>,
    selectedValue: String,
    onValueSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { expanded = it },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        OutlinedTextField(
            value = options.firstOrNull { it.first == selectedValue }?.second.orEmpty(),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled)
        )
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            options.forEach { (value, optionLabel) ->
                DropdownMenuItem(
                    text = { Text(optionLabel) },
                    onClick = {
                        expanded = false
                        onValueSelected(value)
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}

/** Blinks the background a few times when [enabled], like a repeated ripple. */
@Composable
private fun Modifier.blinkingHighlight(enabled: Boolean): Modifier {
    if (!enabled) return this
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(HIGHLIGHT_START_DELAY_MS)
        repeat(HIGHLIGHT_BLINK_COUNT) {
            alpha.animateTo(HIGHLIGHT_ALPHA, tween(HIGHLIGHT_BLINK_DURATION_MS))
            alpha.animateTo(0f, tween(HIGHLIGHT_BLINK_DURATION_MS))
        }
    }
    return background(MaterialTheme.colorScheme.onSurface.copy(alpha = alpha.value))
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "RTL Arabic", locale = "ar")
@Composable
private fun SettingsComponentsPreview() {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface {
            Column {
                SettingsSectionHeader(title = "Privacy")
                SettingsItem(title = "Plain setting", summary = "Some detail text", onClick = {})
                SettingsItem(title = "Disabled", summary = "Not available", enabled = false, onClick = {})
                SettingsSwitchItem(title = "Toggle", summary = "Turned on", checked = true, onCheckedChange = {})
                SettingsSwitchItem(
                    title = "Unavailable toggle",
                    checked = false,
                    enabled = false,
                    supportingText = "Requires something",
                    onCheckedChange = {}
                )
                SettingsDropdown(
                    label = "Theme",
                    options = listOf("light" to "Light", "dark" to "Dark"),
                    selectedValue = "dark",
                    onValueSelected = {}
                )
            }
        }
    }
}
