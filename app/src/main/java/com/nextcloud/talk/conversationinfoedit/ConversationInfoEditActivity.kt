/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2023 Ezhil Shanmugham <ezhil56x.contact@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.conversationinfoedit

import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toFile
import androidx.lifecycle.ViewModelProvider
import autodagger.AutoInjector
import com.github.dhaval2404.imagepicker.ImagePicker
import com.google.android.material.snackbar.Snackbar
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.BaseActivity
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.components.ColoredStatusBar
import com.nextcloud.talk.conversationinfoedit.ui.ConversationInfoEditCallbacks
import com.nextcloud.talk.conversationinfoedit.ui.ConversationInfoEditScreen
import com.nextcloud.talk.conversationinfoedit.ui.ConversationInfoEditUiState
import com.nextcloud.talk.conversationinfoedit.viewmodel.ConversationInfoEditViewModel
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.capabilities.SpreedCapability
import com.nextcloud.talk.models.json.conversations.ConversationEnums
import com.nextcloud.talk.utils.CapabilitiesUtil
import com.nextcloud.talk.utils.PickImage
import com.nextcloud.talk.utils.bundle.BundleKeys
import java.io.File
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class ConversationInfoEditActivity : BaseActivity() {

    @Inject
    lateinit var ncApi: NcApi

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    lateinit var conversationInfoEditViewModel: ConversationInfoEditViewModel

    private lateinit var roomToken: String
    private lateinit var conversationUser: User
    private lateinit var credentials: String

    private lateinit var pickImage: PickImage

    private lateinit var spreedCapabilities: SpreedCapability

    private var uiState by mutableStateOf(ConversationInfoEditUiState())
    private val snackbarHostState = SnackbarHostState()

    private val startImagePickerForResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            handleResult(it) { result ->
                pickImage.onImagePickerResult(result.data) { uri ->
                    uploadAvatar(uri.toFile())
                }
            }
        }

    private val startSelectRemoteFilesIntentForResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        handleResult(it) { result ->
            pickImage.onSelectRemoteFilesResult(startImagePickerForResult, result.data)
        }
    }

    private val startTakePictureIntentForResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        handleResult(it) { result ->
            pickImage.onTakePictureResult(startImagePickerForResult, result.data)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        val extras: Bundle? = intent.extras

        conversationUser = currentUserProviderOld.currentUser.blockingGet()
        roomToken = extras?.getString(BundleKeys.KEY_ROOM_TOKEN)!!
        credentials = com.nextcloud.talk.utils.ApiUtils.getCredentials(
            conversationUser.username,
            conversationUser.token
        )!!

        pickImage = PickImage(this, conversationUser)

        conversationInfoEditViewModel =
            ViewModelProvider(this, viewModelFactory)[ConversationInfoEditViewModel::class.java]

        conversationInfoEditViewModel.getRoom(conversationUser, roomToken)

        val colorScheme = viewThemeUtils.getColorScheme(this)

        setContent {
            MaterialTheme(colorScheme = colorScheme) {
                ColoredStatusBar()
                ConversationInfoEditScreen(
                    uiState = uiState,
                    callbacks = ConversationInfoEditCallbacks(
                        onNavigateBack = { onBackPressedDispatcher.onBackPressed() },
                        onSaveClick = {
                            if (uiState.conversation?.objectType != ConversationEnums.ObjectType.EVENT) {
                                saveConversationNameAndDescription()
                            }
                        },
                        onAvatarUploadClick = {
                            pickImage.selectLocal(startImagePickerForResult = startImagePickerForResult)
                        },
                        onAvatarChooseClick = {
                            pickImage.selectRemote(
                                startSelectRemoteFilesIntentForResult = startSelectRemoteFilesIntentForResult
                            )
                        },
                        onAvatarCameraClick = {
                            pickImage.takePicture(startTakePictureIntentForResult = startTakePictureIntentForResult)
                        },
                        onAvatarDeleteClick = { deleteAvatar() },
                        onNameChange = { uiState = uiState.copy(conversationName = it) },
                        onDescriptionChange = { uiState = uiState.copy(conversationDescription = it) }
                    ),
                    snackbarHostState = snackbarHostState
                )
            }
        }

        initObservers()
    }

    private fun initObservers() {
        initViewStateObserver()
        initRenameRoomObserver()
        initSetDescriptionObserver()
    }

    private fun initViewStateObserver() {
        conversationInfoEditViewModel.viewState.observe(this) { state ->
            when (state) {
                is ConversationInfoEditViewModel.GetRoomSuccessState -> handleRoomLoaded(state.conversationModel)
                is ConversationInfoEditViewModel.GetRoomErrorState ->
                    showSnackbar(getString(R.string.nc_common_error_sorry))
                is ConversationInfoEditViewModel.UploadAvatarSuccessState ->
                    uiState = uiState.copy(
                        conversation = state.conversationModel,
                        avatarRefreshKey = uiState.avatarRefreshKey + 1
                    )
                is ConversationInfoEditViewModel.UploadAvatarErrorState ->
                    showSnackbar(getString(R.string.nc_common_error_sorry))
                is ConversationInfoEditViewModel.DeleteAvatarSuccessState ->
                    uiState = uiState.copy(
                        conversation = state.conversationModel,
                        avatarRefreshKey = uiState.avatarRefreshKey + 1
                    )
                is ConversationInfoEditViewModel.DeleteAvatarErrorState ->
                    showSnackbar(getString(R.string.nc_common_error_sorry))
                else -> {}
            }
        }
    }

    private fun handleRoomLoaded(conversation: com.nextcloud.talk.models.domain.ConversationModel) {
        spreedCapabilities = conversationUser.capabilities!!.spreedCapability!!
        val descriptionEndpointAvailable =
            CapabilitiesUtil.isConversationDescriptionEndpointAvailable(spreedCapabilities)
        val descriptionMaxLength = CapabilitiesUtil.conversationDescriptionLength(spreedCapabilities)
        val isEvent = conversation.objectType == ConversationEnums.ObjectType.EVENT

        uiState = uiState.copy(
            conversationName = conversation.displayName,
            conversationDescription = conversation.description.takeIf { it.isNotEmpty() }.orEmpty(),
            conversation = conversation,
            conversationUser = conversationUser,
            nameEnabled = !isEvent,
            descriptionEnabled = descriptionEndpointAvailable && !isEvent,
            descriptionMaxLength = descriptionMaxLength,
            isDescriptionEndpointAvailable = descriptionEndpointAvailable
        )
    }

    private fun initRenameRoomObserver() {
        conversationInfoEditViewModel.renameRoomUiState.observe(this) { uiStateVm ->
            when (uiStateVm) {
                is ConversationInfoEditViewModel.RenameRoomUiState.None -> {}
                is ConversationInfoEditViewModel.RenameRoomUiState.Success -> {
                    if (uiState.isDescriptionEndpointAvailable) saveConversationDescription() else finish()
                }
                is ConversationInfoEditViewModel.RenameRoomUiState.Error -> {
                    showSnackbar(getString(R.string.default_error_msg))
                    Log.e(TAG, "Error while saving conversation name", uiStateVm.exception)
                }
            }
        }
    }

    private fun initSetDescriptionObserver() {
        conversationInfoEditViewModel.setConversationDescriptionUiState.observe(this) { uiStateVm ->
            when (uiStateVm) {
                is ConversationInfoEditViewModel.SetConversationDescriptionUiState.None -> {}
                is ConversationInfoEditViewModel.SetConversationDescriptionUiState.Success -> finish()
                is ConversationInfoEditViewModel.SetConversationDescriptionUiState.Error -> {
                    showSnackbar(getString(R.string.default_error_msg))
                    Log.e(TAG, "Error while saving conversation description", uiStateVm.exception)
                }
            }
        }
    }

    private fun saveConversationNameAndDescription() {
        conversationInfoEditViewModel.renameRoom(
            uiState.conversation!!.token,
            uiState.conversationName
        )
    }

    private fun saveConversationDescription() {
        conversationInfoEditViewModel.setConversationDescription(
            uiState.conversation!!.token,
            uiState.conversationDescription
        )
    }

    private fun handleResult(result: ActivityResult, onResult: (result: ActivityResult) -> Unit) {
        when (result.resultCode) {
            RESULT_OK -> onResult(result)
            ImagePicker.RESULT_ERROR -> showSnackbar(ImagePicker.getError(result.data))
            else -> Log.i(TAG, "Task Cancelled")
        }
    }

    private fun uploadAvatar(file: File) {
        conversationInfoEditViewModel.uploadConversationAvatar(conversationUser, file, roomToken)
    }

    private fun deleteAvatar() {
        conversationInfoEditViewModel.deleteConversationAvatar(conversationUser, roomToken)
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(window.decorView, message, Snackbar.LENGTH_LONG).show()
    }

    companion object {
        private val TAG = ConversationInfoEditActivity::class.simpleName
    }
}
