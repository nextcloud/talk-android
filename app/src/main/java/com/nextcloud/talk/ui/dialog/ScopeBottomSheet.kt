/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.ui.dialog

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nextcloud.talk.R
import com.nextcloud.talk.models.json.userprofile.Scope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScopeModalBottomSheet(showPrivate: Boolean = true, onScopeSelected: (Scope) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        ScopeBottomSheetContent(
            showPrivate = showPrivate,
            onScopeSelected = { scope ->
                onScopeSelected(scope)
                onDismiss()
            }
        )
    }
}

@Composable
fun ScopeBottomSheetContent(
    modifier: Modifier = Modifier,
    showPrivate: Boolean = true,
    onScopeSelected: (Scope) -> Unit
) {
    Column(modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 8.dp)) {
        if (showPrivate) {
            ScopeOption(
                iconRes = R.drawable.ic_cellphone,
                title = stringResource(R.string.scope_private_title),
                description = stringResource(R.string.scope_private_description),
                onClick = { onScopeSelected(Scope.PRIVATE) }
            )
        }
        ScopeOption(
            iconRes = R.drawable.ic_password,
            title = stringResource(R.string.scope_local_title),
            description = stringResource(R.string.scope_local_description),
            onClick = { onScopeSelected(Scope.LOCAL) }
        )
        ScopeOption(
            iconRes = R.drawable.ic_contacts,
            title = stringResource(R.string.scope_federated_title),
            description = stringResource(R.string.scope_federated_description),
            onClick = { onScopeSelected(Scope.FEDERATED) }
        )
        ScopeOption(
            iconRes = R.drawable.ic_link,
            title = stringResource(R.string.scope_published_title),
            description = stringResource(R.string.scope_published_description),
            onClick = { onScopeSelected(Scope.PUBLISHED) }
        )
    }
}

@Composable
private fun ScopeOption(
    iconRes: Int,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = stringResource(R.string.lock_symbol),
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "RTL · Arabic", locale = "ar")
@Composable
private fun PreviewScopeBottomSheet() {
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        ModalBottomSheet(
            onDismissRequest = {},
            sheetState = rememberModalBottomSheetState()
        ) {
            ScopeBottomSheetContent(onScopeSelected = {})
        }
    }
}
