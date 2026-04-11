/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationinfoedit.ui

import android.content.res.Configuration
import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nextcloud.talk.R
import com.nextcloud.talk.conversationinfoedit.viewmodel.ConversationInfoEditUiState
import com.nextcloud.talk.extensions.loadSystemAvatar
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.conversations.ConversationEnums
import com.nextcloud.talk.models.json.participants.Participant

private const val AVATAR_SIZE_DP = 96
private const val AVATAR_BUTTON_SIZE_DP = 40
private const val CONVERSATION_NAME_MAX_LENGTH = 255
private val avatarButtonShape = RoundedCornerShape(12.dp)

data class ConversationInfoEditCallbacks(
    val onNavigateBack: () -> Unit = {},
    val onSaveClick: () -> Unit = {},
    val onAvatarUploadClick: () -> Unit = {},
    val onAvatarChooseClick: () -> Unit = {},
    val onAvatarCameraClick: () -> Unit = {},
    val onAvatarDeleteClick: () -> Unit = {},
    val onNameChange: (String) -> Unit = {},
    val onDescriptionChange: (String) -> Unit = {}
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationInfoEditScreen(
    uiState: ConversationInfoEditUiState,
    callbacks: ConversationInfoEditCallbacks,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    Scaffold(
        topBar = { ConversationInfoEditTopBar(uiState = uiState, callbacks = callbacks) },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { paddingValues ->
        ConversationInfoEditContent(
            uiState = uiState,
            callbacks = callbacks,
            modifier = Modifier.padding(paddingValues)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationInfoEditTopBar(uiState: ConversationInfoEditUiState, callbacks: ConversationInfoEditCallbacks) {
    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.nc_conversation_menu_conversation_info),
                maxLines = 1
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurface
        ),
        navigationIcon = {
            IconButton(onClick = callbacks.onNavigateBack) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.ic_arrow_back_black_24dp),
                    contentDescription = stringResource(R.string.back_button)
                )
            }
        },
        actions = {
            if (uiState.showSaveButton) {
                IconButton(onClick = callbacks.onSaveClick) {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.ic_check),
                        contentDescription = stringResource(R.string.save)
                    )
                }
            }
        }
    )
}

@Composable
private fun ConversationInfoEditContent(
    uiState: ConversationInfoEditUiState,
    callbacks: ConversationInfoEditCallbacks,
    modifier: Modifier = Modifier
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        Row(modifier = modifier.fillMaxSize()) {
            AvatarPane(
                uiState = uiState,
                callbacks = callbacks,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            FieldsPane(
                uiState = uiState,
                callbacks = callbacks,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            ConversationAvatarImage(
                uiState = uiState,
                modifier = Modifier.size(AVATAR_SIZE_DP.dp).clip(CircleShape)
            )

            Spacer(modifier = Modifier.height(16.dp))

            AvatarButtonsRow(uiState = uiState, callbacks = callbacks)

            Spacer(modifier = Modifier.height(8.dp))

            NameField(uiState = uiState, callbacks = callbacks)

            Spacer(modifier = Modifier.height(16.dp))

            DescriptionField(uiState = uiState, callbacks = callbacks)

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AvatarPane(
    uiState: ConversationInfoEditUiState,
    callbacks: ConversationInfoEditCallbacks,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ConversationAvatarImage(
            uiState = uiState,
            modifier = Modifier.size(AVATAR_SIZE_DP.dp).clip(CircleShape)
        )

        Spacer(modifier = Modifier.height(16.dp))

        AvatarButtonsRow(uiState = uiState, callbacks = callbacks)
    }
}

@Composable
private fun FieldsPane(
    uiState: ConversationInfoEditUiState,
    callbacks: ConversationInfoEditCallbacks,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        NameField(uiState = uiState, callbacks = callbacks)

        Spacer(modifier = Modifier.height(16.dp))

        DescriptionField(uiState = uiState, callbacks = callbacks)

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun NameField(uiState: ConversationInfoEditUiState, callbacks: ConversationInfoEditCallbacks) {
    OutlinedTextField(
        value = uiState.conversationName,
        onValueChange = { value ->
            if (value.length <= CONVERSATION_NAME_MAX_LENGTH) callbacks.onNameChange(value)
        },
        label = { Text(stringResource(R.string.nc_call_name)) },
        enabled = uiState.nameEnabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    )
}

@Composable
private fun DescriptionField(uiState: ConversationInfoEditUiState, callbacks: ConversationInfoEditCallbacks) {
    OutlinedTextField(
        value = uiState.conversationDescription,
        onValueChange = { value ->
            if (value.length <= uiState.descriptionMaxLength) callbacks.onDescriptionChange(value)
        },
        label = { Text(stringResource(R.string.nc_conversation_description)) },
        enabled = uiState.descriptionEnabled,
        minLines = 1,
        maxLines = 8,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Default),
        supportingText = {
            Text(
                text = "${uiState.conversationDescription.length}/${uiState.descriptionMaxLength}",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End
            )
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    )
}

@Composable
private fun AvatarButtonsRow(uiState: ConversationInfoEditUiState, callbacks: ConversationInfoEditCallbacks) {
    val convType = uiState.conversation?.type
    val showButtons = convType != ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL &&
        convType != ConversationEnums.ConversationType.ROOM_SYSTEM &&
        uiState.conversation != null

    if (!showButtons) return

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalIconButton(
            onClick = callbacks.onAvatarUploadClick,
            enabled = uiState.avatarButtonsEnabled,
            modifier = Modifier.size(AVATAR_BUTTON_SIZE_DP.dp),
            shape = avatarButtonShape
        ) {
            Icon(
                painter = painterResource(R.drawable.upload),
                contentDescription = stringResource(R.string.upload_new_avatar_from_device)
            )
        }
        FilledTonalIconButton(
            onClick = callbacks.onAvatarChooseClick,
            enabled = uiState.avatarButtonsEnabled,
            modifier = Modifier.size(AVATAR_BUTTON_SIZE_DP.dp),
            shape = avatarButtonShape
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_folder),
                contentDescription = stringResource(R.string.choose_avatar_from_cloud)
            )
        }
        FilledTonalIconButton(
            onClick = callbacks.onAvatarCameraClick,
            enabled = uiState.avatarButtonsEnabled,
            modifier = Modifier.size(AVATAR_BUTTON_SIZE_DP.dp),
            shape = avatarButtonShape
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_baseline_photo_camera_24),
                contentDescription = stringResource(R.string.set_avatar_from_camera)
            )
        }
        if (uiState.conversation.hasCustomAvatar) {
            FilledTonalIconButton(
                onClick = callbacks.onAvatarDeleteClick,
                enabled = uiState.avatarButtonsEnabled,
                modifier = Modifier.size(AVATAR_BUTTON_SIZE_DP.dp),
                shape = avatarButtonShape
            ) {
                Icon(
                    painter = painterResource(R.drawable.trashbin),
                    contentDescription = stringResource(R.string.delete_avatar)
                )
            }
        }
    }
}

@Suppress("CyclomaticComplexMethod")
@Composable
private fun ConversationAvatarImage(uiState: ConversationInfoEditUiState, modifier: Modifier = Modifier) {
    val conversation = uiState.conversation

    if (conversation == null || uiState.conversationUser == null) {
        Image(
            painter = painterResource(R.drawable.account_circle_96dp),
            contentDescription = stringResource(R.string.avatar),
            modifier = modifier
        )
        return
    }

    val isInPreview = LocalInspectionMode.current
    val isDark = LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES
    val params = AvatarImageParams(
        isDark = isDark,
        credentials = uiState.avatarCredentials,
        avatarUrl = uiState.avatarUrl,
        avatarUrlDark = uiState.avatarUrlDark
    )

    when (conversation.type) {
        ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL ->
            OneToOneAvatarImage(params, modifier)

        ConversationEnums.ConversationType.ROOM_GROUP_CALL,
        ConversationEnums.ConversationType.ROOM_PUBLIC_CALL ->
            GroupAvatarImage(params, modifier)

        ConversationEnums.ConversationType.ROOM_SYSTEM ->
            SystemAvatarImage(isInPreview = isInPreview, modifier = modifier)

        else ->
            Image(
                painter = painterResource(R.drawable.account_circle_96dp),
                contentDescription = stringResource(R.string.avatar),
                modifier = modifier
            )
    }
}

private data class AvatarImageParams(
    val isDark: Boolean,
    val credentials: String,
    val avatarUrl: String,
    val avatarUrlDark: String
)

@Composable
private fun OneToOneAvatarImage(params: AvatarImageParams, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val url = if (params.isDark) params.avatarUrlDark else params.avatarUrl
    val request = remember(url, params.credentials) {
        ImageRequest.Builder(context)
            .data(url)
            .addHeader("Authorization", params.credentials)
            .crossfade(true)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = stringResource(R.string.avatar),
        contentScale = ContentScale.Crop,
        placeholder = painterResource(R.drawable.account_circle_96dp),
        error = painterResource(R.drawable.account_circle_96dp),
        modifier = modifier
    )
}

@Composable
private fun GroupAvatarImage(params: AvatarImageParams, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val url = if (params.isDark) params.avatarUrlDark else params.avatarUrl
    val request = remember(url, params.credentials) {
        ImageRequest.Builder(context)
            .data(url)
            .addHeader("Authorization", params.credentials)
            .crossfade(true)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = stringResource(R.string.avatar),
        contentScale = ContentScale.Crop,
        placeholder = painterResource(R.drawable.ic_circular_group),
        error = painterResource(R.drawable.ic_circular_group),
        modifier = modifier
    )
}

@Composable
private fun SystemAvatarImage(isInPreview: Boolean, modifier: Modifier = Modifier) {
    if (isInPreview) {
        Icon(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = stringResource(R.string.avatar),
            modifier = modifier
        )
    } else {
        AndroidView(
            factory = { ctx -> ImageView(ctx).apply { loadSystemAvatar() } },
            modifier = modifier
        )
    }
}

@Preview(name = "Light", showSystemUi = true)
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showSystemUi = true)
@Preview(name = "RTL Arabic", locale = "ar", showSystemUi = true)
@Preview(name = "Landscape", widthDp = 891, heightDp = 411)
@Composable
private fun ConversationInfoEditScreenPreview() {
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        ConversationInfoEditScreen(
            uiState = ConversationInfoEditUiState(
                conversationName = "My Conversation",
                conversationDescription = "A great conversation about everything",
                nameEnabled = true,
                descriptionEnabled = true,
                descriptionMaxLength = ConversationInfoEditUiState.DESCRIPTION_MAX_LENGTH_DEFAULT,
                conversation = previewConversation,
                avatarButtonsEnabled = true
            ),
            callbacks = ConversationInfoEditCallbacks()
        )
    }
}

private val previewConversation = ConversationModel(
    internalId = "1@preview",
    accountId = 1L,
    token = "preview",
    name = "My Conversation",
    displayName = "My Conversation",
    description = "A great conversation about everything",
    type = ConversationEnums.ConversationType.ROOM_GROUP_CALL,
    participantType = Participant.ParticipantType.OWNER,
    sessionId = "",
    actorId = "",
    actorType = "",
    objectType = ConversationEnums.ObjectType.DEFAULT,
    notificationLevel = ConversationEnums.NotificationLevel.DEFAULT,
    conversationReadOnlyState = ConversationEnums.ConversationReadOnlyState.CONVERSATION_READ_WRITE,
    lobbyState = ConversationEnums.LobbyState.LOBBY_STATE_ALL_PARTICIPANTS,
    lobbyTimer = 0L,
    canLeaveConversation = true,
    canDeleteConversation = false,
    unreadMentionDirect = false,
    notificationCalls = 1,
    avatarVersion = "",
    hasCustomAvatar = false,
    callStartTime = 0L
)
