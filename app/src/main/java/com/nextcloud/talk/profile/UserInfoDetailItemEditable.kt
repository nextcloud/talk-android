/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.profile

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nextcloud.talk.R
import com.nextcloud.talk.models.json.userprofile.Scope

private const val ENABLED_ALPHA = 1f
private const val DISABLED_ALPHA = 0.38f

@Composable
fun UserInfoDetailItemEditable(
    data: UserInfoDetailItemData,
    listeners: UserInfoDetailListeners,
    position: UserInfoDetailItemPosition,
    enabled: Boolean = true,
    multiLine: Boolean = false
) {
    val topPadding = if (position == UserInfoDetailItemPosition.FIRST) 0.dp else 8.dp
    val bottomPadding = if (position == UserInfoDetailItemPosition.LAST) 16.dp else 8.dp
    var textValue by remember(data.text) { mutableStateOf(data.text) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .padding(top = topPadding, bottom = bottomPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.width(16.dp))
        Icon(
            painter = painterResource(id = data.icon),
            contentDescription = data.hint,
            modifier = Modifier
                .padding(top = 8.dp)
                .size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(16.dp))
        OutlinedTextField(
            value = textValue,
            onValueChange = { newText ->
                textValue = newText
                listeners.onTextChange(newText)
            },
            modifier = Modifier.weight(1f),
            label = { Text(data.hint) },
            enabled = enabled,
            singleLine = !multiLine,
            keyboardOptions = KeyboardOptions(imeAction = if (multiLine) ImeAction.Default else ImeAction.Next),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                cursorColor = MaterialTheme.colorScheme.primary
            )
        )
        if (data.scope != null) {
            ScopeIconButton(data.scope, data.hint, enabled, listeners.onScopeClick)
        }
    }
}

@Composable
private fun ScopeIconButton(scope: Scope, hint: String, enabled: Boolean, onScopeClick: (() -> Unit)?) {
    val scopeIconRes = when (scope) {
        Scope.PRIVATE -> R.drawable.ic_cellphone
        Scope.LOCAL -> R.drawable.ic_password
        Scope.FEDERATED -> R.drawable.ic_contacts
        Scope.PUBLISHED -> R.drawable.ic_link
    }
    IconButton(
        onClick = { onScopeClick?.invoke() },
        modifier = Modifier
            .size(48.dp)
            .padding(top = 8.dp)
            .alpha(if (enabled) ENABLED_ALPHA else DISABLED_ALPHA),
        enabled = enabled
    ) {
        Icon(
            painter = painterResource(id = scopeIconRes),
            contentDescription = stringResource(R.string.scope_toggle_description, hint),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private val previewListeners = UserInfoDetailListeners(onTextChange = {}, onScopeClick = {})

@Preview(name = "Edit", showBackground = true)
@Preview(name = "Dark · Edit", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewEdit() {
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        Surface {
            Column {
                UserInfoDetailItemEditable(
                    data = UserInfoDetailItemData(
                        R.drawable.ic_user,
                        "John Doe",
                        stringResource(R.string.user_info_displayname),
                        Scope.PRIVATE
                    ),
                    listeners = previewListeners,
                    position = UserInfoDetailItemPosition.FIRST,
                    enabled = false
                )
                UserInfoDetailItemEditable(
                    data = UserInfoDetailItemData(
                        R.drawable.ic_phone,
                        "+49 123 456 789 12",
                        stringResource(R.string.user_info_phone),
                        Scope.LOCAL
                    ),
                    listeners = previewListeners,
                    position = UserInfoDetailItemPosition.MIDDLE
                )
                UserInfoDetailItemEditable(
                    data = UserInfoDetailItemData(
                        R.drawable.ic_email,
                        "john@example.com",
                        stringResource(R.string.user_info_email),
                        Scope.FEDERATED
                    ),
                    listeners = previewListeners,
                    position = UserInfoDetailItemPosition.MIDDLE
                )
            }
        }
    }
}

@Preview(name = "RTL · Arabic · Edit", showBackground = true, locale = "ar")
@Composable
private fun PreviewEditRtl() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        Surface {
            Column {
                UserInfoDetailItemEditable(
                    data = UserInfoDetailItemData(
                        R.drawable.ic_user,
                        "جون دو",
                        stringResource(R.string.user_info_displayname),
                        Scope.PRIVATE
                    ),
                    listeners = previewListeners,
                    position = UserInfoDetailItemPosition.FIRST,
                    enabled = false
                )
                UserInfoDetailItemEditable(
                    data = UserInfoDetailItemData(
                        R.drawable.ic_map_marker,
                        "برلين، ألمانيا",
                        stringResource(R.string.user_info_address),
                        Scope.PUBLISHED
                    ),
                    listeners = previewListeners,
                    position = UserInfoDetailItemPosition.MIDDLE
                )
                UserInfoDetailItemEditable(
                    data = UserInfoDetailItemData(
                        R.drawable.ic_web,
                        "nextcloud.com",
                        stringResource(R.string.user_info_website),
                        Scope.PRIVATE
                    ),
                    listeners = previewListeners,
                    position = UserInfoDetailItemPosition.MIDDLE
                )
                UserInfoDetailItemEditable(
                    data = UserInfoDetailItemData(
                        R.drawable.ic_twitter,
                        "@nextcloud",
                        stringResource(R.string.user_info_twitter),
                        Scope.LOCAL
                    ),
                    listeners = previewListeners,
                    position = UserInfoDetailItemPosition.LAST
                )
            }
        }
    }
}
