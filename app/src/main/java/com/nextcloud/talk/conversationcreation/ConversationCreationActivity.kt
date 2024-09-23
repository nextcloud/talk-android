/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Sowjanya Kota <sowjanya.kch@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:Suppress("DEPRECATION")

package com.nextcloud.talk.conversationcreation

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import autodagger.AutoInjector
import coil.compose.AsyncImage
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.BaseActivity
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.chat.ChatActivity
import com.nextcloud.talk.contacts.ContactsActivityCompose
import com.nextcloud.talk.contacts.loadImage
import com.nextcloud.talk.models.json.autocomplete.AutocompleteUser
import com.nextcloud.talk.utils.PickImage
import com.nextcloud.talk.utils.bundle.BundleKeys
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class ConversationCreationActivity : BaseActivity() {
    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    private lateinit var pickImage: PickImage
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
        val conversationCreationViewModel = ViewModelProvider(
            this,
            viewModelFactory
        )[ConversationCreationViewModel::class.java]
        val conversationUser = conversationCreationViewModel.currentUser
        pickImage = PickImage(this, conversationUser)

        setContent {
            val colorScheme = viewThemeUtils.getColorScheme(this)
            val context = LocalContext.current
            MaterialTheme(
                colorScheme = colorScheme
            ) {
                ConversationCreationScreen(conversationCreationViewModel, context, pickImage)
            }
            SetStatusBarColor()
        }
    }
}

@Composable
private fun SetStatusBarColor() {
    val view = LocalView.current
    val isDarkMod = isSystemInDarkTheme()

    DisposableEffect(isDarkMod) {
        val activity = view.context as Activity
        activity.window.statusBarColor = activity.getColor(R.color.bg_default)

        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = !isDarkMod
        }

        onDispose { }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationCreationScreen(
    conversationCreationViewModel: ConversationCreationViewModel,
    context: Context,
    pickImage: PickImage
) {
    val selectedImageUri = conversationCreationViewModel.selectedImageUri.collectAsState().value

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pickImage.onImagePickerResult(result.data) { uri ->
                conversationCreationViewModel.updateSelectedImageUri(uri)
            }
        }
    }

    val remoteFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pickImage.onSelectRemoteFilesResult(imagePickerLauncher, result.data)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pickImage.onTakePictureResult(imagePickerLauncher, result.data)
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                val selectedParticipants =
                    data?.getParcelableArrayListExtra<AutocompleteUser>("selectedParticipants")
                        ?: emptyList()
                val participants = selectedParticipants.toMutableList()
                conversationCreationViewModel.updateSelectedParticipants(participants)
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.nc_new_conversation)) },
                navigationIcon = {
                    IconButton(onClick = {
                        (context as? Activity)?.finish()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.back_button)
                        )
                    }
                }
            )
        },
        content = { paddingValues ->
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
            ) {
                DefaultUserAvatar(selectedImageUri)
                UploadAvatar(
                    pickImage = pickImage,
                    onImageSelected = { uri -> conversationCreationViewModel.updateSelectedImageUri(uri) },
                    imagePickerLauncher = imagePickerLauncher,
                    remoteFilePickerLauncher = remoteFilePickerLauncher,
                    cameraLauncher = cameraLauncher,
                    onDeleteImage = { conversationCreationViewModel.updateSelectedImageUri(null) },
                    selectedImageUri = selectedImageUri
                )

                ConversationNameAndDescription(conversationCreationViewModel)
                AddParticipants(launcher, context, conversationCreationViewModel)
                RoomCreationOptions(conversationCreationViewModel)
                CreateConversation(conversationCreationViewModel, context)
            }
        }
    )
}

@Composable
fun DefaultUserAvatar(selectedImageUri: Uri?) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        if (selectedImageUri != null) {
            AsyncImage(
                model = selectedImageUri,
                contentDescription = stringResource(id = R.string.user_avatar),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(84.dp)
                    .padding(top = 8.dp)
                    .clip(CircleShape)
            )
        } else {
            AsyncImage(
                model = R.drawable.ic_circular_group,
                contentDescription = stringResource(id = R.string.user_avatar),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(84.dp)
                    .padding(top = 8.dp)
                    .clip(CircleShape)
            )
        }
    }
}

@Composable
fun UploadAvatar(
    pickImage: PickImage,
    onImageSelected: (Uri) -> Unit,
    imagePickerLauncher: ManagedActivityResultLauncher<Intent, ActivityResult>,
    remoteFilePickerLauncher: ManagedActivityResultLauncher<Intent, ActivityResult>,
    cameraLauncher: ManagedActivityResultLauncher<Intent, ActivityResult>,
    onDeleteImage: () -> Unit,
    selectedImageUri: Uri?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        IconButton(
            onClick = {
                pickImage.takePicture(cameraLauncher)
            }
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_baseline_photo_camera_24),
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
        }

        IconButton(onClick = {
            pickImage.selectLocal(imagePickerLauncher)
        }) {
            Icon(
                painter = painterResource(id = R.drawable.upload),
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
        }
        IconButton(
            onClick = {
                pickImage.selectRemote(remoteFilePickerLauncher)
            }
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_mimetype_folder),
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
        }

        if (selectedImageUri != null) {
            IconButton(onClick = {
                onDeleteImage()
            }) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_delete_grey600_24dp),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun ConversationNameAndDescription(conversationCreationViewModel: ConversationCreationViewModel) {
    val conversationRoomName = conversationCreationViewModel.roomName.collectAsState()
    val conversationDescription = conversationCreationViewModel.conversationDescription.collectAsState()
    OutlinedTextField(
        value = conversationRoomName.value,
        onValueChange = {
            conversationCreationViewModel.updateRoomName(it)
        },
        label = { Text(text = stringResource(id = R.string.nc_call_name)) },
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp)
            .fillMaxWidth(),
        singleLine = true
    )
    OutlinedTextField(
        value = conversationDescription.value,
        onValueChange = {
            conversationCreationViewModel.updateConversationDescription(it)
        },
        label = { Text(text = stringResource(id = R.string.nc_conversation_description)) },
        modifier = Modifier
            .padding(top = 8.dp, start = 16.dp, end = 16.dp)
            .fillMaxWidth()
    )
}

@SuppressLint("SuspiciousIndentation")
@Composable
fun AddParticipants(
    launcher: ManagedActivityResultLauncher<Intent, ActivityResult>,
    context: Context,
    conversationCreationViewModel: ConversationCreationViewModel
) {
    val participants = conversationCreationViewModel.selectedParticipants.collectAsState().value

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp)
    ) {
        Row {
            Text(
                text = stringResource(id = R.string.nc_participants).uppercase(),
                fontSize = 14.sp,
                modifier = Modifier.padding(start = 0.dp, bottom = 16.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            if (participants.isNotEmpty()) {
                Text(
                    text = stringResource(id = R.string.nc_edit),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .padding(start = 16.dp, bottom = 16.dp)
                        .clickable {
                            val intent = Intent(context, ContactsActivityCompose::class.java)
                            intent.putParcelableArrayListExtra(
                                "selectedParticipants",
                                participants as ArrayList<AutocompleteUser>
                            )
                            intent.putExtra("isAddParticipants", true)
                            intent.putExtra("isAddParticipantsEdit", true)
                            launcher.launch(intent)
                        },
                    textAlign = TextAlign.Right
                )
            }
        }
        participants.toSet().forEach { participant ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val imageUri = participant.id?.let { conversationCreationViewModel.getImageUri(it, true) }
                val errorPlaceholderImage: Int = R.drawable.account_circle_96dp
                val loadedImage = loadImage(imageUri, context, errorPlaceholderImage)
                AsyncImage(
                    model = loadedImage,
                    contentDescription = stringResource(id = R.string.user_avatar),
                    modifier = Modifier.size(width = 32.dp, height = 32.dp)
                )
                participant.label?.let {
                    Text(
                        text = it,
                        modifier = Modifier.padding(all = 16.dp),
                        fontSize = 15.sp
                    )
                }
            }
            HorizontalDivider(thickness = 0.1.dp, color = Color.Black)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    val intent = Intent(context, ContactsActivityCompose::class.java)
                    intent.putExtra("isAddParticipants", true)
                    launcher.launch(intent)
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (participants.isEmpty()) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_account_plus),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = stringResource(id = R.string.nc_add_participants),
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
        }
    }
}

@Composable
fun RoomCreationOptions(conversationCreationViewModel: ConversationCreationViewModel) {
    val isGuestsAllowed = conversationCreationViewModel.isGuestsAllowed.value
    val isConversationAvailableForRegisteredUsers = conversationCreationViewModel
        .isConversationAvailableForRegisteredUsers.value
    val isOpenForGuestAppUsers = conversationCreationViewModel.openForGuestAppUsers.value

    val isPasswordSet = conversationCreationViewModel.isPasswordEnabled.value

    Text(
        text = stringResource(id = R.string.nc_new_conversation_visibility).uppercase(),
        fontSize = 14.sp,
        modifier = Modifier.padding(top = 24.dp, start = 16.dp, end = 16.dp)
    )
    ConversationOptions(
        icon = R.drawable.ic_avatar_link,
        text = R.string.nc_guest_access_allow_title,
        switch = {
            Switch(
                checked = isGuestsAllowed,
                onCheckedChange = {
                    conversationCreationViewModel.isGuestsAllowed.value = it
                }
            )
        },
        conversationCreationViewModel = conversationCreationViewModel
    )

    if (isGuestsAllowed && !isPasswordSet) {
        ConversationOptions(
            icon = R.drawable.ic_lock_grey600_24px,
            text = R.string.nc_set_password,
            conversationCreationViewModel = conversationCreationViewModel
        )
    }

    if (isGuestsAllowed && isPasswordSet) {
        ConversationOptions(
            icon = R.drawable.ic_lock_grey600_24px,
            text = R.string.nc_change_password,
            conversationCreationViewModel = conversationCreationViewModel
        )
    }

    ConversationOptions(
        icon = R.drawable.baseline_format_list_bulleted_24,
        text = R.string.nc_open_conversation_to_registered_users,
        switch = {
            Switch(
                checked = isConversationAvailableForRegisteredUsers,
                onCheckedChange = {
                    conversationCreationViewModel.isConversationAvailableForRegisteredUsers.value = it
                }
            )
        },
        conversationCreationViewModel = conversationCreationViewModel
    )

    if (isConversationAvailableForRegisteredUsers) {
        ConversationOptions(
            text = R.string.nc_open_to_guest_app_users,
            switch = {
                Switch(
                    checked = isOpenForGuestAppUsers,
                    onCheckedChange = {
                        conversationCreationViewModel.openForGuestAppUsers.value = it
                    }
                )
            },
            conversationCreationViewModel = conversationCreationViewModel
        )
    }
}

@Composable
fun ConversationOptions(
    icon: Int? = null,
    text: Int,
    switch: @Composable (() -> Unit)? = null,
    conversationCreationViewModel: ConversationCreationViewModel
) {
    var showPasswordDialog by rememberSaveable { mutableStateOf(false) }
    var showPasswordChangeDialog by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
            .then(
                if (!conversationCreationViewModel.isPasswordEnabled.value) {
                    Modifier.clickable {
                        showPasswordDialog = true
                    }
                } else if (conversationCreationViewModel.isPasswordEnabled.value) {
                    Modifier.clickable {
                        showPasswordChangeDialog = true
                    }
                } else {
                    Modifier
                }
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
        } else {
            Spacer(modifier = Modifier.width(40.dp))
        }
        Text(
            text = stringResource(id = text),
            modifier = Modifier.weight(1f)
        )
        if (switch != null) {
            switch()
        }
        if (showPasswordDialog) {
            ShowPasswordDialog(
                onDismiss = { showPasswordDialog = false },
                conversationCreationViewModel = conversationCreationViewModel
            )
        }
        if (showPasswordChangeDialog) {
            ShowChangePassword(
                onDismiss = {
                    showPasswordChangeDialog = false
                },
                conversationCreationViewModel = conversationCreationViewModel
            )
        }
    }
}

@Composable
fun ShowChangePassword(onDismiss: () -> Unit, conversationCreationViewModel: ConversationCreationViewModel) {
    var changedPassword by rememberSaveable { mutableStateOf("") }
    Dialog(onDismissRequest = {
        onDismiss()
    }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(375.dp)
                .padding(16.dp)
                .background(color = colorResource(id = R.color.appbar)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = stringResource(id = R.string.nc_change_password))
                OutlinedTextField(
                    value = changedPassword,
                    onValueChange = {
                        changedPassword = it
                    },
                    label = { Text(text = stringResource(id = R.string.nc_set_new_password)) },
                    singleLine = true
                )
                if (changedPassword.isNotEmpty() && changedPassword.isNotBlank()) {
                    TextButton(
                        onClick = {
                            conversationCreationViewModel.updatePassword(changedPassword)
                            conversationCreationViewModel.isPasswordEnabled.value = true
                            onDismiss()
                        },
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Text(text = stringResource(id = R.string.nc_change_password))
                    }
                }
                TextButton(
                    onClick = {
                        conversationCreationViewModel.isPasswordEnabled.value = true
                        onDismiss()
                    },
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(text = stringResource(id = R.string.nc_remove_password))
                }
                TextButton(
                    onClick = {
                        conversationCreationViewModel.isPasswordEnabled.value = true
                        onDismiss()
                    },
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(text = stringResource(id = R.string.nc_cancel))
                }
            }
        }
    }
}

@Composable
fun ShowPasswordDialog(onDismiss: () -> Unit, conversationCreationViewModel: ConversationCreationViewModel) {
    var password by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        containerColor = colorResource(id = R.color.dialog_background),
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    if (password.isNotEmpty() && password.isNotBlank()) {
                        conversationCreationViewModel.updatePassword(password)
                        conversationCreationViewModel.isPasswordEnabled(true)
                    }
                }
            ) {
                Text(text = stringResource(id = R.string.save))
            }
        },
        title = { Text(text = stringResource(id = R.string.nc_set_password)) },
        text = {
            TextField(
                value = password,
                onValueChange = {
                    password = it
                },
                label = { Text(text = stringResource(id = R.string.nc_guest_access_password_dialog_hint)) }
            )
        },
        dismissButton = {
            Button(onClick = { onDismiss() }) {
                Text(text = stringResource(id = R.string.nc_cancel))
            }
        }
    )
}

@Composable
fun CreateConversation(conversationCreationViewModel: ConversationCreationViewModel, context: Context) {
    val selectedParticipants by conversationCreationViewModel.selectedParticipants.collectAsState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(all = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = {
                conversationCreationViewModel.createRoomAndAddParticipants(
                    roomType = CompanionClass.ROOM_TYPE_GROUP,
                    conversationName = conversationCreationViewModel.roomName.value,
                    participants = selectedParticipants.toSet()
                ) { roomToken ->
                    val bundle = Bundle()
                    bundle.putString(BundleKeys.KEY_ROOM_TOKEN, roomToken)
                    val chatIntent = Intent(context, ChatActivity::class.java)
                    chatIntent.putExtras(bundle)
                    chatIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    context.startActivity(chatIntent)
                }
            }
        ) {
            Text(text = stringResource(id = R.string.create_conversation))
        }
    }
}

class CompanionClass {
    companion object {
        internal val TAG = ConversationCreationActivity::class.simpleName
        internal const val ROOM_TYPE_GROUP = "2"
    }
}
