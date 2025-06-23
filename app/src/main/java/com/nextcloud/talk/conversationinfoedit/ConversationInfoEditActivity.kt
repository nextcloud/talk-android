/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2023 Ezhil Shanmugham <ezhil56x.contact@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.conversationinfoedit

import android.app.Activity
import android.os.Bundle
import android.text.InputFilter
import android.text.TextUtils
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toFile
import androidx.core.view.ViewCompat
import androidx.lifecycle.ViewModelProvider
import autodagger.AutoInjector
import com.github.dhaval2404.imagepicker.ImagePicker
import com.google.android.material.snackbar.Snackbar
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.BaseActivity
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.conversationinfoedit.viewmodel.ConversationInfoEditViewModel
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.ActivityConversationInfoEditBinding
import com.nextcloud.talk.extensions.loadConversationAvatar
import com.nextcloud.talk.extensions.loadSystemAvatar
import com.nextcloud.talk.extensions.loadUserAvatar
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.capabilities.SpreedCapability
import com.nextcloud.talk.models.json.conversations.ConversationEnums
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.CapabilitiesUtil
import com.nextcloud.talk.utils.PickImage
import com.nextcloud.talk.utils.bundle.BundleKeys
import java.io.File
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class ConversationInfoEditActivity : BaseActivity() {

    private lateinit var binding: ActivityConversationInfoEditBinding

    @Inject
    lateinit var ncApi: NcApi

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    lateinit var conversationInfoEditViewModel: ConversationInfoEditViewModel

    private lateinit var roomToken: String
    private lateinit var conversationUser: User
    private lateinit var credentials: String

    private var conversation: ConversationModel? = null

    private lateinit var pickImage: PickImage

    private lateinit var spreedCapabilities: SpreedCapability

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

        binding = ActivityConversationInfoEditBinding.inflate(layoutInflater)
        setupActionBar()
        setContentView(binding.root)
        initSystemBars()

        val extras: Bundle? = intent.extras

        conversationUser = currentUserProvider.currentUser.blockingGet()

        roomToken = extras?.getString(BundleKeys.KEY_ROOM_TOKEN)!!

        conversationInfoEditViewModel =
            ViewModelProvider(this, viewModelFactory)[ConversationInfoEditViewModel::class.java]

        conversationInfoEditViewModel.getRoom(conversationUser, roomToken)

        viewThemeUtils.material.colorTextInputLayout(binding.conversationNameInputLayout)
        viewThemeUtils.material.colorTextInputLayout(binding.conversationDescriptionInputLayout)

        credentials = ApiUtils.getCredentials(conversationUser.username, conversationUser.token)!!

        pickImage = PickImage(this, conversationUser)

        val max = CapabilitiesUtil.conversationDescriptionLength(conversationUser.capabilities?.spreedCapability!!)
        binding.conversationDescription.filters = arrayOf(
            InputFilter.LengthFilter(max)
        )
        binding.conversationDescriptionInputLayout.counterMaxLength = max

        initObservers()
    }

    private fun initObservers() {
        initViewStateObserver()
        conversationInfoEditViewModel.renameRoomUiState.observe(this) { uiState ->
            when (uiState) {
                is ConversationInfoEditViewModel.RenameRoomUiState.None -> {
                }
                is ConversationInfoEditViewModel.RenameRoomUiState.Success -> {
                    if (CapabilitiesUtil.isConversationDescriptionEndpointAvailable(spreedCapabilities)) {
                        saveConversationDescription()
                    } else {
                        finish()
                    }
                }
                is ConversationInfoEditViewModel.RenameRoomUiState.Error -> {
                    Snackbar
                        .make(binding.root, context.getString(R.string.default_error_msg), Snackbar.LENGTH_LONG)
                        .show()
                    Log.e(TAG, "Error while saving conversation name", uiState.exception)
                }
            }
        }

        conversationInfoEditViewModel.setConversationDescriptionUiState.observe(this) { uiState ->
            when (uiState) {
                is ConversationInfoEditViewModel.SetConversationDescriptionUiState.None -> {
                }
                is ConversationInfoEditViewModel.SetConversationDescriptionUiState.Success -> {
                    finish()
                }
                is ConversationInfoEditViewModel.SetConversationDescriptionUiState.Error -> {
                    Snackbar
                        .make(binding.root, context.getString(R.string.default_error_msg), Snackbar.LENGTH_LONG)
                        .show()
                    Log.e(TAG, "Error while saving conversation description", uiState.exception)
                }
            }
        }
    }

    private fun initViewStateObserver() {
        conversationInfoEditViewModel.viewState.observe(this) { state ->
            when (state) {
                is ConversationInfoEditViewModel.GetRoomSuccessState -> {
                    conversation = state.conversationModel

                    spreedCapabilities = conversationUser.capabilities!!.spreedCapability!!

                    binding.conversationName.setText(conversation!!.displayName)

                    if (conversation!!.description.isNotEmpty()) {
                        binding.conversationDescription.setText(conversation!!.description)
                    }

                    if (!CapabilitiesUtil.isConversationDescriptionEndpointAvailable(spreedCapabilities)) {
                        binding.conversationDescription.isEnabled = false
                    }

                    if (conversation?.objectType == ConversationEnums.ObjectType.EVENT) {
                        binding.conversationName.isEnabled = false
                        binding.conversationDescription.isEnabled = false
                    }

                    loadConversationAvatar()
                }

                is ConversationInfoEditViewModel.GetRoomErrorState -> {
                    Snackbar.make(binding.root, R.string.nc_common_error_sorry, Snackbar.LENGTH_LONG).show()
                }

                is ConversationInfoEditViewModel.UploadAvatarSuccessState -> {
                    conversation = state.conversationModel
                    loadConversationAvatar()
                }

                is ConversationInfoEditViewModel.UploadAvatarErrorState -> {
                    Snackbar.make(binding.root, R.string.nc_common_error_sorry, Snackbar.LENGTH_LONG).show()
                }

                is ConversationInfoEditViewModel.DeleteAvatarSuccessState -> {
                    conversation = state.conversationModel
                    loadConversationAvatar()
                }

                is ConversationInfoEditViewModel.DeleteAvatarErrorState -> {
                    Snackbar.make(binding.root, R.string.nc_common_error_sorry, Snackbar.LENGTH_LONG).show()
                }

                else -> {}
            }
        }
    }

    private fun setupAvatarOptions() {
        binding.avatarUpload.setOnClickListener {
            pickImage.selectLocal(startImagePickerForResult = startImagePickerForResult)
        }

        binding.avatarChoose.setOnClickListener {
            pickImage.selectRemote(startSelectRemoteFilesIntentForResult = startSelectRemoteFilesIntentForResult)
        }

        binding.avatarCamera.setOnClickListener {
            pickImage.takePicture(startTakePictureIntentForResult = startTakePictureIntentForResult)
        }

        if (conversation?.hasCustomAvatar == true) {
            binding.avatarDelete.visibility = View.VISIBLE
            binding.avatarDelete.setOnClickListener { deleteAvatar() }
        } else {
            binding.avatarDelete.visibility = View.GONE
        }

        binding.avatarImage.let { ViewCompat.setTransitionName(it, "userAvatar.transitionTag") }

        binding.let {
            viewThemeUtils.material.themeFAB(it.avatarUpload)
            viewThemeUtils.material.themeFAB(it.avatarChoose)
            viewThemeUtils.material.themeFAB(it.avatarCamera)
            viewThemeUtils.material.themeFAB(it.avatarDelete)
        }
    }

    private fun setupActionBar() {
        setSupportActionBar(binding.conversationInfoEditToolbar)
        binding.conversationInfoEditToolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setIcon(resources!!.getColor(android.R.color.transparent, null).toDrawable())
        supportActionBar?.title = resources!!.getString(R.string.nc_conversation_menu_conversation_info)

        viewThemeUtils.material.themeToolbar(binding.conversationInfoEditToolbar)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.menu_conversation_info_edit, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        super.onPrepareOptionsMenu(menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.save) {
            if (conversation?.objectType != ConversationEnums.ObjectType.EVENT) {
                saveConversationNameAndDescription()
            }
        }
        return true
    }

    private fun saveConversationNameAndDescription() {
        val newRoomName = binding.conversationName.text.toString()
        conversationInfoEditViewModel.renameRoom(
            conversation!!.token,
            newRoomName
        )
    }

    private fun saveConversationDescription() {
        val conversationDescription = binding.conversationDescription.text.toString()
        conversationInfoEditViewModel.setConversationDescription(conversation!!.token, conversationDescription)
    }

    private fun handleResult(result: ActivityResult, onResult: (result: ActivityResult) -> Unit) {
        when (result.resultCode) {
            Activity.RESULT_OK -> onResult(result)

            ImagePicker.RESULT_ERROR -> {
                Snackbar.make(binding.root, ImagePicker.getError(result.data), Snackbar.LENGTH_SHORT).show()
            }

            else -> {
                Log.i(TAG, "Task Cancelled")
            }
        }
    }

    private fun uploadAvatar(file: File) {
        conversationInfoEditViewModel.uploadConversationAvatar(conversationUser, file, roomToken)
    }

    private fun deleteAvatar() {
        conversationInfoEditViewModel.deleteConversationAvatar(conversationUser, roomToken)
    }

    private fun loadConversationAvatar() {
        setupAvatarOptions()

        when (conversation!!.type) {
            ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL -> if (!TextUtils.isEmpty(
                    conversation!!.name
                )
            ) {
                conversation!!.name.let { binding.avatarImage.loadUserAvatar(conversationUser, it, true, false) }
            }

            ConversationEnums.ConversationType.ROOM_GROUP_CALL, ConversationEnums.ConversationType.ROOM_PUBLIC_CALL -> {
                binding.avatarImage.loadConversationAvatar(conversationUser, conversation!!, false, viewThemeUtils)
            }

            ConversationEnums.ConversationType.ROOM_SYSTEM -> {
                binding.avatarImage.loadSystemAvatar()
            }

            else -> {
                // unused atm
            }
        }
    }

    companion object {
        private val TAG = ConversationInfoEditActivity::class.simpleName
    }
}
