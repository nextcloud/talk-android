/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:Suppress("TooManyFunctions", "MatchingDeclarationName")

package com.nextcloud.talk.conversationinfo.ui

import android.content.res.Configuration
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.transform.CircleCropTransformation
import com.nextcloud.talk.R
import com.nextcloud.talk.conversationinfo.ConversationInfoUiState
import com.nextcloud.talk.conversationinfo.model.ParticipantModel
import com.nextcloud.talk.models.json.participants.Participant
import com.nextcloud.talk.models.json.status.StatusType
import com.nextcloud.talk.ui.StatusDrawable
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.DisplayUtils

data class ConversationInfoScreenCallbacks(
    val onNavigateBack: () -> Unit = {},
    val onEditConversation: () -> Unit = {},
    val onMessageNotificationLevelClick: () -> Unit = {},
    val onCallNotificationsClick: () -> Unit = {},
    val onImportantConversationClick: () -> Unit = {},
    val onSensitiveConversationClick: () -> Unit = {},
    val onLobbyClick: () -> Unit = {},
    val onLobbyTimerClick: () -> Unit = {},
    val onAllowGuestsClick: () -> Unit = {},
    val onPasswordProtectionClick: () -> Unit = {},
    val onResendInvitationsClick: () -> Unit = {},
    val onSharedItemsClick: () -> Unit = {},
    val onThreadsClick: () -> Unit = {},
    val onRecordingConsentClick: () -> Unit = {},
    val onMessageExpirationClick: () -> Unit = {},
    val onShareConversationClick: () -> Unit = {},
    val onLockConversationClick: () -> Unit = {},
    val onParticipantClick: (ParticipantModel) -> Unit = {},
    val onAddParticipantsClick: () -> Unit = {},
    val onStartGroupChatClick: () -> Unit = {},
    val onListBansClick: () -> Unit = {},
    val onArchiveClick: () -> Unit = {},
    val onLeaveConversationClick: () -> Unit = {},
    val onClearHistoryClick: () -> Unit = {},
    val onDeleteConversationClick: () -> Unit = {}
)

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod")
@Composable
fun ConversationInfoScreen(
    state: ConversationInfoUiState,
    callbacks: ConversationInfoScreenCallbacks = ConversationInfoScreenCallbacks()
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.nc_conversation_menu_conversation_info),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = callbacks.onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.back_button)
                        )
                    }
                },
                actions = {
                    if (state.showEditButton) {
                        IconButton(onClick = callbacks.onEditConversation) {
                            Icon(
                                painter = painterResource(R.drawable.ic_pencil_grey600_24dp),
                                contentDescription = stringResource(R.string.edit)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = WindowInsets.navigationBars.asPaddingValues()
            ) {
                item { ConversationInfoHeader(state) }

                if (state.description.isNotEmpty()) {
                    item { ConversationDescriptionSection(state.description) }
                }

                if (state.upcomingEventSummary != null || state.upcomingEventTime != null) {
                    item { UpcomingEventCard(state) }
                }

                item { NotificationSettingsSection(state, callbacks) }

                if (state.showWebinarSettings) {
                    item { WebinarSettingsSection(state, callbacks) }
                }

                if (state.showGuestAccess) {
                    item { GuestAccessSection(state, callbacks) }
                }

                if (state.showSharedItems) {
                    item { SharedItemsSection(state, callbacks) }
                }

                item { RecordingConsentSection(state, callbacks) }

                item { ConversationSettingsSection(state, callbacks) }

                if (state.showLockConversation) {
                    item { LockConversationRow(state, callbacks) }
                }

                if (state.showParticipants) {
                    item { ParticipantsSectionHeader(state, callbacks) }
                    items(
                        items = state.participants,
                        key = { p ->
                            "${p.participant.calculatedActorType}#${p.participant.calculatedActorId}"
                        }
                    ) { participant ->
                        ParticipantItemRow(
                            model = participant,
                            baseUrl = state.serverBaseUrl,
                            credentials = state.credentials,
                            conversationToken = state.conversationToken,
                            onItemClick = callbacks.onParticipantClick
                        )
                    }
                }

                if (state.showListBans) {
                    item {
                        ClickableIconRow(
                            title = stringResource(R.string.show_banned_participants),
                            iconRes = R.drawable.baseline_block_24,
                            onClick = callbacks.onListBansClick
                        )
                    }
                }

                item { DangerZoneSection(state, callbacks) }
            }
        }
    }
}

@Composable
private fun ConversationInfoHeader(state: ConversationInfoUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HeaderAvatar(avatarUrl = state.avatarUrl)
        Spacer(modifier = Modifier.height(8.dp))
        HeaderUserInfo(state = state)
    }
}

@Composable
private fun HeaderAvatar(avatarUrl: String?) {
    if (LocalInspectionMode.current) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.account_circle_48dp),
                contentDescription = stringResource(R.string.avatar),
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        AsyncImage(
            model = avatarUrl,
            contentDescription = stringResource(R.string.avatar),
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
            error = painterResource(R.drawable.account_circle_48dp),
            placeholder = painterResource(R.drawable.account_circle_48dp)
        )
    }
}

@Composable
private fun HeaderUserInfo(state: ConversationInfoUiState) {
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = state.displayName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        if (state.pronouns.isNotEmpty()) {
            Text(
                text = " ${state.pronouns}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
    }
    if (state.professionCompany.isNotEmpty()) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = state.professionCompany,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
    }
    if (state.localTimeLocation.isNotEmpty()) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = state.localTimeLocation,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ConversationDescriptionSection(description: String) {
    Text(
        text = description,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun UpcomingEventCard(state: ConversationInfoUiState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_event_24px),
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Column {
                if (state.upcomingEventSummary != null) {
                    Text(
                        text = state.upcomingEventSummary,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (state.upcomingEventTime != null) {
                    Text(
                        text = state.upcomingEventTime,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String? = null,
    @DrawableRes iconRes: Int? = null,
    checked: Boolean? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (checked != null) {
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}

@Composable
private fun ClickableIconRow(
    title: String,
    iconRes: Int,
    onClick: () -> Unit,
    titleColor: Color = Color.Unspecified,
    iconTint: Color = Color.Unspecified
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = if (iconTint != Color.Unspecified) iconTint else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.size(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (titleColor != Color.Unspecified) titleColor else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun NotificationSettingsSection(state: ConversationInfoUiState, callbacks: ConversationInfoScreenCallbacks) {
    SectionHeader(title = stringResource(R.string.nc_notification_settings))
    if (state.showImportantConversation) {
        SettingsRow(
            title = stringResource(R.string.nc_important_conversation),
            subtitle = stringResource(R.string.nc_important_conversation_desc),
            checked = state.importantConversation,
            onClick = callbacks.onImportantConversationClick
        )
    }
    SettingsRow(
        title = stringResource(R.string.nc_plain_old_messages),
        subtitle = state.notificationLevel.ifEmpty { null },
        onClick = callbacks.onMessageNotificationLevelClick
    )
    if (state.showCallNotifications) {
        SettingsRow(
            title = stringResource(R.string.nc_call_notifications),
            checked = state.callNotificationsEnabled,
            onClick = callbacks.onCallNotificationsClick
        )
    }
    if (state.showSensitiveConversation) {
        SettingsRow(
            title = stringResource(R.string.nc_sensitive_conversation),
            subtitle = stringResource(R.string.nc_sensitive_conversation_hint),
            checked = state.sensitiveConversation,
            onClick = callbacks.onSensitiveConversationClick
        )
    }
}

@Composable
private fun WebinarSettingsSection(state: ConversationInfoUiState, callbacks: ConversationInfoScreenCallbacks) {
    SectionHeader(title = stringResource(R.string.nc_webinar))
    SettingsRow(
        title = stringResource(R.string.nc_lobby),
        iconRes = R.drawable.ic_room_service_black_24dp,
        checked = state.lobbyEnabled,
        onClick = callbacks.onLobbyClick
    )
    if (state.showLobbyTimer) {
        SettingsRow(
            title = stringResource(R.string.nc_start_time),
            subtitle = state.lobbyTimerLabel.ifEmpty { stringResource(R.string.nc_manual) },
            iconRes = R.drawable.ic_timer_black_24dp,
            onClick = callbacks.onLobbyTimerClick
        )
    }
}

@Composable
private fun GuestAccessSection(state: ConversationInfoUiState, callbacks: ConversationInfoScreenCallbacks) {
    SectionHeader(title = stringResource(R.string.nc_guest_access))
    SettingsRow(
        title = stringResource(R.string.nc_guest_access_allow_title),
        subtitle = stringResource(R.string.nc_guest_access_allow_summary),
        checked = state.guestsAllowed,
        onClick = callbacks.onAllowGuestsClick
    )
    if (state.showPasswordProtection) {
        SettingsRow(
            title = stringResource(R.string.nc_guest_access_password_title),
            subtitle = stringResource(R.string.nc_guest_access_password_summary),
            checked = state.hasPassword,
            onClick = callbacks.onPasswordProtectionClick
        )
    }
    if (state.showResendInvitations) {
        ClickableIconRow(
            title = stringResource(R.string.nc_guest_access_resend_invitations),
            iconRes = R.drawable.ic_email,
            onClick = callbacks.onResendInvitationsClick
        )
    }
}

@Composable
private fun SharedItemsSection(state: ConversationInfoUiState, callbacks: ConversationInfoScreenCallbacks) {
    SectionHeader(title = stringResource(R.string.nc_shared_items))
    ClickableIconRow(
        title = stringResource(R.string.nc_shared_items_description),
        iconRes = R.drawable.ic_folder_multiple_image,
        onClick = callbacks.onSharedItemsClick
    )
    if (state.showThreadsButton) {
        ClickableIconRow(
            title = stringResource(R.string.recent_threads),
            iconRes = R.drawable.outline_forum_24,
            onClick = callbacks.onThreadsClick
        )
    }
}

@Composable
private fun RecordingConsentSection(state: ConversationInfoUiState, callbacks: ConversationInfoScreenCallbacks) {
    if (!state.showRecordingConsent) return
    SectionHeader(title = stringResource(R.string.recording_settings_title))
    if (state.showRecordingConsentSwitch) {
        SettingsRow(
            title = stringResource(R.string.recording_consent_for_conversation_title),
            subtitle = stringResource(R.string.recording_consent_for_conversation_description),
            checked = state.recordingConsentForConversation,
            onClick = if (state.recordingConsentEnabled) callbacks.onRecordingConsentClick else null
        )
    }
    if (state.showRecordingConsentAll) {
        Text(
            text = stringResource(R.string.recording_consent_all),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun ConversationSettingsSection(state: ConversationInfoUiState, callbacks: ConversationInfoScreenCallbacks) {
    SectionHeader(title = stringResource(R.string.nc_conversation_settings))
    if (state.showMessageExpiration) {
        SettingsRow(
            title = stringResource(R.string.nc_expire_messages),
            subtitle = state.messageExpirationLabel.ifEmpty { null },
            onClick = callbacks.onMessageExpirationClick
        )
        Text(
            text = stringResource(R.string.nc_expire_messages_explanation),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
    }
    if (state.showShareConversationButton) {
        ClickableIconRow(
            title = stringResource(R.string.nc_guest_access_share_link),
            iconRes = R.drawable.ic_share_variant,
            onClick = callbacks.onShareConversationClick
        )
    }
}

@Composable
private fun LockConversationRow(state: ConversationInfoUiState, callbacks: ConversationInfoScreenCallbacks) {
    SettingsRow(
        title = stringResource(R.string.lock_conversation),
        iconRes = R.drawable.ic_lock_white_24px,
        checked = state.isConversationLocked,
        onClick = callbacks.onLockConversationClick
    )
}

@Composable
private fun ParticipantsSectionHeader(state: ConversationInfoUiState, callbacks: ConversationInfoScreenCallbacks) {
    SectionHeader(title = stringResource(R.string.nc_participants))
    if (state.showAddParticipants) {
        ClickableIconRow(
            title = stringResource(R.string.nc_participants_add),
            iconRes = R.drawable.ic_account_plus,
            onClick = callbacks.onAddParticipantsClick
        )
    }
    if (state.showStartGroupChat) {
        ClickableIconRow(
            title = stringResource(R.string.nc_start_group_chat),
            iconRes = R.drawable.ic_people_group_black_24px,
            onClick = callbacks.onStartGroupChatClick
        )
    }
}

private sealed class ParticipantAvatarContent {
    data class Url(val url: String) : ParticipantAvatarContent()
    data class Res(@DrawableRes val resId: Int) : ParticipantAvatarContent()

    /** Renders the icon on a themed surfaceVariant background circle (equivalent to themePlaceholderAvatar). */
    data class ThemedRes(@DrawableRes val resId: Int) : ParticipantAvatarContent()
    data class FirstLetter(val letter: String) : ParticipantAvatarContent()
}

private fun buildParticipantAvatarContent(
    participant: Participant,
    baseUrl: String,
    conversationToken: String,
    isDark: Boolean
): ParticipantAvatarContent =
    when (participant.calculatedActorType) {
        Participant.ActorType.USERS ->
            ParticipantAvatarContent.Url(
                ApiUtils.getUrlForAvatar(baseUrl, participant.calculatedActorId, true, isDark)
            )
        Participant.ActorType.FEDERATED ->
            ParticipantAvatarContent.Url(
                ApiUtils.getUrlForFederatedAvatar(
                    baseUrl,
                    conversationToken,
                    participant.actorId ?: "",
                    if (isDark) 1 else 0,
                    true
                )
            )
        Participant.ActorType.GROUPS -> ParticipantAvatarContent.ThemedRes(R.drawable.ic_avatar_group_small)
        Participant.ActorType.CIRCLES -> ParticipantAvatarContent.ThemedRes(R.drawable.ic_avatar_team_small)
        Participant.ActorType.PHONES -> ParticipantAvatarContent.ThemedRes(R.drawable.ic_phone_small)
        Participant.ActorType.GUESTS, Participant.ActorType.EMAILS -> {
            val name = participant.displayName
            if (!name.isNullOrBlank()) {
                ParticipantAvatarContent.FirstLetter(name.trimStart().first().uppercase())
            } else {
                ParticipantAvatarContent.Res(R.drawable.account_circle_48dp)
            }
        }
        else -> ParticipantAvatarContent.Res(R.drawable.account_circle_48dp)
    }

@Composable
@Suppress("LongMethod")
private fun ParticipantAvatarImage(
    participant: Participant,
    baseUrl: String,
    credentials: String,
    conversationToken: String,
    modifier: Modifier = Modifier
) {
    val isInPreview = LocalInspectionMode.current
    val context = LocalContext.current
    val isDark = LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES

    if (isInPreview) {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.account_circle_48dp),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxSize()
            )
        }
        return
    }

    val avatarContent = remember(participant.calculatedActorType, participant.calculatedActorId, isDark) {
        buildParticipantAvatarContent(participant, baseUrl, conversationToken, isDark)
    }

    when (avatarContent) {
        is ParticipantAvatarContent.Url -> {
            val request = remember(avatarContent.url, credentials) {
                ImageRequest.Builder(context)
                    .data(avatarContent.url)
                    .addHeader("Authorization", credentials)
                    .crossfade(true)
                    .transformations(CircleCropTransformation())
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = stringResource(R.string.avatar),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(R.drawable.account_circle_48dp),
                error = painterResource(R.drawable.account_circle_48dp),
                modifier = modifier.clip(CircleShape)
            )
        }
        is ParticipantAvatarContent.Res -> {
            AsyncImage(
                model = avatarContent.resId,
                contentDescription = stringResource(R.string.avatar),
                contentScale = ContentScale.Crop,
                modifier = modifier.clip(CircleShape)
            )
        }
        is ParticipantAvatarContent.ThemedRes -> {
            Box(
                modifier = modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(avatarContent.resId),
                    contentDescription = stringResource(R.string.avatar),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        is ParticipantAvatarContent.FirstLetter -> {
            Box(
                modifier = modifier
                    .clip(CircleShape)
                    .background(colorResource(R.color.grey_600)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = avatarContent.letter,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private const val PARTICIPANT_STATUS_SIZE_DP = 18f
private const val PARTICIPANT_OFFLINE_ALPHA = 0.38f
private const val PARTICIPANT_STATUS_EMOJI_SCALE = 0.8f

@Composable
private fun ParticipantStatusOverlay(status: String?, modifier: Modifier = Modifier) {
    if (!status.isNullOrEmpty()) {
        if (LocalInspectionMode.current) {
            val drawableRes = when (status) {
                StatusType.ONLINE.string -> R.drawable.online_status
                StatusType.AWAY.string -> R.drawable.ic_user_status_away
                StatusType.BUSY.string -> R.drawable.ic_user_status_busy
                StatusType.DND.string -> R.drawable.ic_user_status_dnd
                else -> null
            } ?: return
            Icon(
                painter = painterResource(drawableRes),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = modifier.clipToBounds()
            )
        } else {
            val context = LocalContext.current
            val surfaceArgb = MaterialTheme.colorScheme.surface.toArgb()
            AndroidView(
                factory = { ctx ->
                    ImageView(ctx).apply {
                        val radiusPx = DisplayUtils.convertDpToPixel(PARTICIPANT_STATUS_SIZE_DP / 2f, ctx)
                        setImageDrawable(StatusDrawable(status, "", radiusPx, surfaceArgb, ctx))
                    }
                },
                update = { imageView ->
                    val radiusPx = DisplayUtils.convertDpToPixel(PARTICIPANT_STATUS_SIZE_DP / 2f, context)
                    imageView.setImageDrawable(StatusDrawable(status, "", radiusPx, surfaceArgb, context))
                },
                modifier = modifier.clipToBounds()
            )
        }
    }
}

@Composable
private fun participantRoleLabel(participant: Participant): String =
    when (participant.type) {
        Participant.ParticipantType.OWNER,
        Participant.ParticipantType.MODERATOR,
        Participant.ParticipantType.GUEST_MODERATOR -> stringResource(R.string.nc_moderator)
        Participant.ParticipantType.USER -> when (participant.calculatedActorType) {
            Participant.ActorType.GROUPS -> stringResource(R.string.nc_group)
            Participant.ActorType.CIRCLES -> stringResource(R.string.nc_team)
            else -> ""
        }
        Participant.ParticipantType.GUEST -> stringResource(R.string.nc_guest)
        Participant.ParticipantType.USER_FOLLOWING_LINK -> stringResource(R.string.nc_following_link)
        else -> ""
    }

@Composable
private fun participantEffectiveStatus(participant: Participant): String {
    val explicit = participant.statusMessage
    if (!explicit.isNullOrEmpty()) return explicit
    return when (participant.status) {
        StatusType.DND.string -> stringResource(R.string.dnd)
        StatusType.BUSY.string -> stringResource(R.string.busy)
        StatusType.AWAY.string -> stringResource(R.string.away)
        else -> ""
    }
}

@Composable
private fun ParticipantNameRow(displayName: String, roleLabel: String, nameColor: Color) {
    Row {
        Text(
            text = displayName,
            style = MaterialTheme.typography.bodyLarge,
            color = nameColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f, fill = false)
                .alignByBaseline()
        )
        if (roleLabel.isNotEmpty()) {
            Text(
                text = " ($roleLabel)",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alignByBaseline()
            )
        }
    }
}

@Composable
private fun ParticipantStatusRow(statusEmoji: String?, statusText: String) {
    if (statusEmoji == null && statusText.isEmpty()) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (statusEmoji != null) {
            val fontSize = with(LocalDensity.current) {
                (PARTICIPANT_STATUS_SIZE_DP * PARTICIPANT_STATUS_EMOJI_SCALE).dp.toSp()
            }
            Text(
                text = statusEmoji,
                fontSize = fontSize,
                lineHeight = fontSize,
                maxLines = 1
            )
            if (statusText.isNotEmpty()) {
                Spacer(modifier = Modifier.width(4.dp))
            }
        }
        if (statusText.isNotEmpty()) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
@Suppress("LongMethod")
private fun ParticipantItemRow(
    model: ParticipantModel,
    baseUrl: String,
    credentials: String,
    conversationToken: String,
    onItemClick: (ParticipantModel) -> Unit
) {
    val participant = model.participant
    val nameColor = if (model.isOnline) {
        colorResource(R.color.high_emphasis_text)
    } else {
        colorResource(R.color.medium_emphasis_text)
    }
    val displayName = if (!participant.displayName.isNullOrBlank()) {
        participant.displayName!!
    } else {
        stringResource(R.string.nc_guest)
    }
    val roleLabel = participantRoleLabel(participant)
    val statusText = participantEffectiveStatus(participant)
    val statusEmoji = participant.statusIcon?.takeIf { it.isNotEmpty() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onItemClick(model) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(40.dp)) {
            ParticipantAvatarImage(
                participant = participant,
                baseUrl = baseUrl,
                credentials = credentials,
                conversationToken = conversationToken,
                modifier = Modifier
                    .size(40.dp)
                    .alpha(if (model.isOnline) 1f else PARTICIPANT_OFFLINE_ALPHA)
            )
            ParticipantStatusOverlay(
                status = participant.status,
                modifier = Modifier
                    .size(PARTICIPANT_STATUS_SIZE_DP.dp)
                    .align(Alignment.BottomEnd)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            ParticipantNameRow(displayName = displayName, roleLabel = roleLabel, nameColor = nameColor)
            ParticipantStatusRow(statusEmoji = statusEmoji, statusText = statusText)
        }
        val inCallIconRes = when {
            participant.inCall and Participant.InCallFlags.WITH_PHONE.toLong() > 0L ->
                R.drawable.ic_call_grey_600_24dp
            participant.inCall and Participant.InCallFlags.WITH_VIDEO.toLong() > 0L ->
                R.drawable.ic_videocam_grey_600_24dp
            participant.inCall > Participant.InCallFlags.DISCONNECTED.toLong() ->
                R.drawable.ic_mic_grey_600_24dp
            else -> null
        }
        if (inCallIconRes != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                painter = painterResource(inCallIconRes),
                contentDescription = displayName,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
@Suppress("LongMethod")
private fun DangerZoneSection(state: ConversationInfoUiState, callbacks: ConversationInfoScreenCallbacks) {
    val errorColor = MaterialTheme.colorScheme.error
    Text(
        text = stringResource(R.string.danger_zone),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = errorColor,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )

    if (state.showArchiveConversation) {
        val archiveTitle = if (state.isArchived) {
            stringResource(R.string.unarchive_conversation)
        } else {
            stringResource(R.string.archive_conversation)
        }
        val archiveIcon = if (state.isArchived) R.drawable.ic_unarchive_24px else R.drawable.outline_archive_24
        ClickableIconRow(
            title = archiveTitle,
            iconRes = archiveIcon,
            onClick = callbacks.onArchiveClick
        )
        Text(
            text = if (state.isArchived) {
                stringResource(R.string.unarchive_hint)
            } else {
                stringResource(R.string.archive_hint)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
    }

    if (state.canLeave) {
        ClickableIconRow(
            title = stringResource(R.string.nc_leave),
            iconRes = R.drawable.ic_exit_to_app_black_24dp,
            iconTint = errorColor,
            titleColor = errorColor,
            onClick = callbacks.onLeaveConversationClick
        )
    }

    if (state.showClearHistory) {
        ClickableIconRow(
            title = stringResource(R.string.nc_clear_history),
            iconRes = R.drawable.ic_delete_black_24dp,
            iconTint = errorColor,
            titleColor = errorColor,
            onClick = callbacks.onClearHistoryClick
        )
    }

    if (state.canDelete) {
        ClickableIconRow(
            title = stringResource(R.string.nc_delete_call),
            iconRes = R.drawable.ic_delete_black_24dp,
            iconTint = errorColor,
            titleColor = errorColor,
            onClick = callbacks.onDeleteConversationClick
        )
    }
}

@Suppress("LongMethod")
private fun previewState(): ConversationInfoUiState {
    val alice = ParticipantModel(
        participant = Participant(
            actorType = Participant.ActorType.USERS,
            actorId = "alice",
            displayName = "Alice Johnson",
            type = Participant.ParticipantType.OWNER,
            status = StatusType.ONLINE.string
        ),
        isOnline = true
    )
    val bob = ParticipantModel(
        participant = Participant(
            actorType = Participant.ActorType.USERS,
            actorId = "bob",
            displayName = "Bob Smith",
            type = Participant.ParticipantType.MODERATOR,
            status = StatusType.AWAY.string,
            statusMessage = "In a meeting",
            inCall = Participant.InCallFlags.WITH_VIDEO.toLong()
        ),
        isOnline = true
    )
    val carol = ParticipantModel(
        participant = Participant(
            actorType = Participant.ActorType.GROUPS,
            actorId = "dev-team",
            displayName = "Dev Team",
            type = Participant.ParticipantType.USER
        ),
        isOnline = false
    )
    val dave = ParticipantModel(
        participant = Participant(
            actorType = Participant.ActorType.CIRCLES,
            actorId = "eng-circle",
            displayName = "Engineering Circle",
            type = Participant.ParticipantType.USER
        ),
        isOnline = false
    )
    return ConversationInfoUiState(
        isLoading = false,
        displayName = "Jane Doe",
        pronouns = "she/her",
        professionCompany = "Marketing Manager @ Nextcloud GmbH",
        localTimeLocation = "10:03 PM · London",
        description = "This is a sample conversation description.",
        upcomingEventSummary = "Mgmt Coordination Call",
        upcomingEventTime = "Apr 15, 2026, 2:00 PM",
        notificationLevel = "Always",
        callNotificationsEnabled = true,
        showCallNotifications = true,
        importantConversation = false,
        showImportantConversation = true,
        sensitiveConversation = false,
        showSensitiveConversation = true,
        lobbyEnabled = true,
        showWebinarSettings = true,
        lobbyTimerLabel = "Apr 15, 2026, 10:00 AM",
        showLobbyTimer = true,
        guestsAllowed = true,
        showGuestAccess = true,
        hasPassword = false,
        showPasswordProtection = true,
        showResendInvitations = true,
        showSharedItems = true,
        showThreadsButton = true,
        showRecordingConsent = true,
        recordingConsentForConversation = false,
        showRecordingConsentSwitch = true,
        messageExpirationLabel = "1 week",
        showMessageExpiration = true,
        showShareConversationButton = true,
        isConversationLocked = false,
        showLockConversation = true,
        showParticipants = true,
        participants = listOf(alice, bob, carol, dave),
        showAddParticipants = true,
        showListBans = true,
        showArchiveConversation = true,
        isArchived = false,
        canLeave = true,
        canDelete = true,
        showClearHistory = true
    )
}

@Composable
private fun PreviewWrapper(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface {
            Column {
                content()
            }
        }
    }
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "RTL Arabic", locale = "ar")
@Composable
private fun SectionHeaderPreview() {
    PreviewWrapper {
        SectionHeader(title = "Notification Settings")
    }
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "RTL Arabic", locale = "ar")
@Composable
private fun SettingsRowPreview() {
    PreviewWrapper {
        SettingsRow(title = "Plain setting", onClick = {})
        SettingsRow(title = "With subtitle", subtitle = "Some detail text", onClick = {})
        SettingsRow(title = "Toggle on", checked = true, onClick = {})
        SettingsRow(title = "Toggle off", checked = false, onClick = {})
        SettingsRow(
            title = "With icon and toggle",
            iconRes = R.drawable.ic_room_service_black_24dp,
            checked = true,
            onClick = {}
        )
    }
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "RTL Arabic", locale = "ar")
@Composable
private fun ClickableIconRowPreview() {
    PreviewWrapper {
        ClickableIconRow(
            title = "Share conversation",
            iconRes = R.drawable.ic_share_variant,
            onClick = {}
        )
        ClickableIconRow(
            title = "Delete conversation",
            iconRes = R.drawable.ic_delete_black_24dp,
            iconTint = MaterialTheme.colorScheme.error,
            titleColor = MaterialTheme.colorScheme.error,
            onClick = {}
        )
    }
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "RTL Arabic", locale = "ar")
@Composable
private fun ConversationInfoHeaderPreview() {
    PreviewWrapper {
        ConversationInfoHeader(state = previewState())
    }
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "RTL Arabic", locale = "ar")
@Composable
private fun UpcomingEventCardPreview() {
    PreviewWrapper {
        UpcomingEventCard(state = previewState())
    }
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "RTL Arabic", locale = "ar")
@Composable
private fun ConversationDescriptionSectionPreview() {
    PreviewWrapper {
        ConversationDescriptionSection(description = "This is a sample conversation description.")
    }
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "RTL Arabic", locale = "ar")
@Composable
private fun NotificationSettingsSectionPreview() {
    PreviewWrapper {
        NotificationSettingsSection(
            state = previewState(),
            callbacks = ConversationInfoScreenCallbacks()
        )
    }
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "RTL Arabic", locale = "ar")
@Composable
private fun WebinarSettingsSectionPreview() {
    PreviewWrapper {
        WebinarSettingsSection(
            state = previewState(),
            callbacks = ConversationInfoScreenCallbacks()
        )
    }
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "RTL Arabic", locale = "ar")
@Composable
private fun GuestAccessSectionPreview() {
    PreviewWrapper {
        GuestAccessSection(
            state = previewState(),
            callbacks = ConversationInfoScreenCallbacks()
        )
    }
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "RTL Arabic", locale = "ar")
@Composable
private fun SharedItemsSectionPreview() {
    PreviewWrapper {
        SharedItemsSection(
            state = previewState(),
            callbacks = ConversationInfoScreenCallbacks()
        )
    }
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "RTL Arabic", locale = "ar")
@Composable
private fun RecordingConsentSectionPreview() {
    PreviewWrapper {
        RecordingConsentSection(
            state = previewState(),
            callbacks = ConversationInfoScreenCallbacks()
        )
    }
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "RTL Arabic", locale = "ar")
@Composable
private fun ConversationSettingsSectionPreview() {
    PreviewWrapper {
        ConversationSettingsSection(
            state = previewState(),
            callbacks = ConversationInfoScreenCallbacks()
        )
    }
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "RTL Arabic", locale = "ar")
@Composable
private fun LockConversationRowPreview() {
    PreviewWrapper {
        LockConversationRow(
            state = previewState(),
            callbacks = ConversationInfoScreenCallbacks()
        )
    }
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "RTL Arabic", locale = "ar")
@Composable
private fun ParticipantsSectionHeaderPreview() {
    PreviewWrapper {
        ParticipantsSectionHeader(
            state = previewState(),
            callbacks = ConversationInfoScreenCallbacks()
        )
    }
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "RTL Arabic", locale = "ar")
@Composable
private fun ParticipantItemRowPreview() {
    val state = previewState()
    PreviewWrapper {
        state.participants.forEach { participant ->
            ParticipantItemRow(
                model = participant,
                baseUrl = state.serverBaseUrl,
                credentials = state.credentials,
                conversationToken = state.conversationToken,
                onItemClick = {}
            )
        }
    }
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "RTL Arabic", locale = "ar")
@Composable
private fun DangerZoneSectionPreview() {
    PreviewWrapper {
        DangerZoneSection(
            state = previewState(),
            callbacks = ConversationInfoScreenCallbacks()
        )
    }
}
