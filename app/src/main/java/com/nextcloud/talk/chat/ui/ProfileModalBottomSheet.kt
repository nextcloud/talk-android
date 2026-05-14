/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.net.toUri
import com.nextcloud.talk.R
import com.nextcloud.talk.api.NcApiCoroutines
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.hovercard.HoverCardAction
import com.nextcloud.talk.utils.ApiUtils

private const val TAG = "ProfileModalBottomSheet"

private val allowedAppIds = listOf("profile", "spreed", "email", "timezone")

@DrawableRes
private fun iconResForAppId(appId: String?): Int? =
    when (appId) {
        "profile" -> R.drawable.ic_user
        "email" -> R.drawable.ic_email
        "spreed" -> R.drawable.ic_talk
        "timezone" -> R.drawable.baseline_schedule_24
        else -> null
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileModalBottomSheet(
    actorId: String,
    user: User,
    ncApiCoroutines: NcApiCoroutines,
    onTalkTo: (actorId: String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        ProfileSheetContent(
            actorId = actorId,
            user = user,
            ncApiCoroutines = ncApiCoroutines,
            onTalkTo = onTalkTo,
            onDismiss = onDismiss
        )
    }
}

@Suppress("TooGenericExceptionCaught")
@Composable
internal fun ProfileSheetContent(
    actorId: String,
    user: User,
    ncApiCoroutines: NcApiCoroutines,
    onTalkTo: (actorId: String) -> Unit,
    onDismiss: () -> Unit
) {
    var displayName by remember { mutableStateOf("") }
    var actions by remember { mutableStateOf<List<HoverCardAction>>(emptyList()) }
    val context = LocalContext.current
    val credentials = remember(user) { ApiUtils.getCredentials(user.username, user.token) }

    LaunchedEffect(actorId) {
        try {
            val result = ncApiCoroutines.hoverCard(
                credentials!!,
                ApiUtils.getUrlForHoverCard(user.baseUrl!!, actorId)
            )
            displayName = result.ocs!!.data!!.displayName!!
            actions = result.ocs!!.data!!.actions!!.filter { allowedAppIds.contains(it.appId) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load hover card for $actorId", e)
            onDismiss()
        }
    }

    ProfileSheetLayout(
        displayName = displayName,
        actions = actions,
        onActionClick = { action ->
            onDismiss()
            handleAction(action, actorId, context, onTalkTo)
        }
    )
}

@Composable
internal fun ProfileSheetLayout(
    displayName: String,
    actions: List<HoverCardAction>,
    onActionClick: (HoverCardAction) -> Unit
) {
    val timezoneAction = actions.firstOrNull { it.appId == "timezone" }
    val actionItems = actions.filter { it.appId != "timezone" }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
    ) {
        if (displayName.isNotEmpty() || timezoneAction != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = dimensionResource(R.dimen.standard_dialog_padding),
                        vertical = dimensionResource(R.dimen.standard_half_padding)
                    )
            ) {
                if (displayName.isNotEmpty()) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                timezoneAction?.title?.let { timezone ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(R.drawable.baseline_schedule_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(dimensionResource(R.dimen.standard_half_padding)))
                        Text(
                            text = timezone,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        actionItems.forEach { action ->
            ProfileActionItem(action = action, onClick = { onActionClick(action) })
        }
    }
}

@Composable
private fun ProfileActionItem(action: HoverCardAction, onClick: () -> Unit) {
    val iconRes = iconResForAppId(action.appId) ?: return
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(dimensionResource(R.dimen.bottom_sheet_item_height)),
        shape = RectangleShape,
        contentPadding = PaddingValues(horizontal = dimensionResource(R.dimen.standard_dialog_padding)),
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(dimensionResource(R.dimen.standard_dialog_padding)))
        Text(
            text = action.title ?: "",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Start
        )
    }
}

private fun handleAction(
    action: HoverCardAction,
    actorId: String,
    context: Context,
    onTalkTo: (actorId: String) -> Unit
) {
    when (action.appId) {
        "profile" -> openProfile(action.hyperlink!!, context)
        "email" -> composeEmail(action.title!!, context)
        "spreed" -> onTalkTo(actorId)
    }
}

private fun openProfile(hyperlink: String, context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, hyperlink.toUri())
    context.startActivity(intent)
}

private fun composeEmail(address: String, context: Context) {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = "mailto:$address".toUri()
    }
    context.startActivity(intent)
}

private val previewActions = listOf(
    HoverCardAction("Profile", null, "https://cloud.example.com/u/alice", "profile"),
    HoverCardAction("23:27 • same time", null, null, "timezone"),
    HoverCardAction("alice@example.com", null, null, "email"),
    HoverCardAction("work@example.com", null, null, "email"),
    HoverCardAction("Talk to Alice", null, null, "spreed")
)

private val previewActionsNoTimezone = previewActions.filter { it.appId != "timezone" }

@Preview(showBackground = true, name = "Light")
@Preview(showBackground = true, name = "RTL · Arabic", locale = "ar")
@Composable
private fun PreviewProfileSheetLayout() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            ProfileSheetLayout(
                displayName = "Alice Wonderland",
                actions = previewActions,
                onActionClick = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewProfileSheetLayoutDark() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            ProfileSheetLayout(
                displayName = "Alice Wonderland",
                actions = previewActionsNoTimezone,
                onActionClick = {}
            )
        }
    }
}
