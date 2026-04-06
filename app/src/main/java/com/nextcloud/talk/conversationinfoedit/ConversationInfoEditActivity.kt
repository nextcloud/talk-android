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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toFile
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import autodagger.AutoInjector
import com.github.dhaval2404.imagepicker.ImagePicker
import com.nextcloud.talk.activities.BaseActivity
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.components.ColoredStatusBar
import com.nextcloud.talk.conversationinfoedit.ui.ConversationInfoEditCallbacks
import com.nextcloud.talk.conversationinfoedit.ui.ConversationInfoEditScreen
import com.nextcloud.talk.conversationinfoedit.viewmodel.ConversationInfoEditViewModel
import com.nextcloud.talk.utils.PickImage
import com.nextcloud.talk.utils.bundle.BundleKeys
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

@AutoInjector(NextcloudTalkApplication::class)
class ConversationInfoEditActivity : BaseActivity() {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    lateinit var conversationInfoEditViewModel: ConversationInfoEditViewModel

    private lateinit var pickImage: PickImage

    private val startImagePickerForResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            handleResult(it) { result ->
                pickImage.onImagePickerResult(result.data) { uri ->
                    conversationInfoEditViewModel.uploadAvatar(uri.toFile())
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

        val roomToken = intent.extras?.getString(BundleKeys.KEY_ROOM_TOKEN)!!

        conversationInfoEditViewModel =
            ViewModelProvider(this, viewModelFactory)[ConversationInfoEditViewModel::class.java]

        conversationInfoEditViewModel.initialize(roomToken)

        lifecycleScope.launch {
            val user = conversationInfoEditViewModel.uiState
                .mapNotNull { it.conversationUser }
                .first()
            pickImage = PickImage(this@ConversationInfoEditActivity, user)
        }

        setupCompose()
    }

    private fun setupCompose() {
        val colorScheme = viewThemeUtils.getColorScheme(this)
        setContent {
            val uiState by conversationInfoEditViewModel.uiState.collectAsStateWithLifecycle()
            val snackbarHostState = remember { SnackbarHostState() }
            val context = LocalContext.current

            LaunchedEffect(uiState.userMessage) {
                val msgRes = uiState.userMessage
                if (msgRes != null) {
                    snackbarHostState.showSnackbar(context.getString(msgRes))
                    conversationInfoEditViewModel.messageShown()
                }
            }

            LaunchedEffect(uiState.navigateBack) {
                if (uiState.navigateBack) {
                    conversationInfoEditViewModel.resetNavigateBack()
                    setResult(RESULT_OK)
                    onBackPressedDispatcher.onBackPressed()
                }
            }

            MaterialTheme(colorScheme = colorScheme) {
                ColoredStatusBar()
                ConversationInfoEditScreen(
                    uiState = uiState,
                    callbacks = ConversationInfoEditCallbacks(
                        onNavigateBack = { onBackPressedDispatcher.onBackPressed() },
                        onSaveClick = { conversationInfoEditViewModel.saveNameAndDescription() },
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
                        onAvatarDeleteClick = { conversationInfoEditViewModel.deleteAvatar() },
                        onNameChange = { conversationInfoEditViewModel.updateConversationName(it) },
                        onDescriptionChange = { conversationInfoEditViewModel.updateConversationDescription(it) }
                    ),
                    snackbarHostState = snackbarHostState
                )
            }
        }
    }

    private fun handleResult(result: ActivityResult, onResult: (result: ActivityResult) -> Unit) {
        when (result.resultCode) {
            RESULT_OK -> onResult(result)
            ImagePicker.RESULT_ERROR -> Log.e(TAG, "Image picker error: ${ImagePicker.getError(result.data)}")
            else -> Log.i(TAG, "Task Cancelled")
        }
    }

    companion object {
        private val TAG = ConversationInfoEditActivity::class.simpleName
    }
}
