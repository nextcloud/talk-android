/*
 * Nextcloud Talk application
 *
 * @author Marcel Hibbe
 * @author Ezhil Shanmugham
 * Copyright (C) 2023 Marcel Hibbe (dev@mhibbe.de)
 * Copyright (C) 2023 Ezhil Shanmugham <ezhil56x.contact@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.conversationinfoedit

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.core.net.toFile
import androidx.core.view.ViewCompat
import androidx.lifecycle.ViewModelProvider
import autodagger.AutoInjector
import com.github.dhaval2404.imagepicker.ImagePicker
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
import com.nextcloud.talk.models.domain.ConversationType
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.PickImage
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.database.user.CapabilitiesUtilNew
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.io.File
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class ConversationInfoEditActivity :
    BaseActivity() {

    private lateinit var binding: ActivityConversationInfoEditBinding

    @Inject
    lateinit var ncApi: NcApi

    @Inject
    lateinit var currentUserProvider: CurrentUserProviderNew

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    lateinit var conversationInfoEditViewModel: ConversationInfoEditViewModel

    private lateinit var roomToken: String
    private lateinit var conversationUser: User
    private lateinit var credentials: String

    private var conversation: ConversationModel? = null

    private lateinit var pickImage: PickImage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        binding = ActivityConversationInfoEditBinding.inflate(layoutInflater)
        setupActionBar()
        setContentView(binding.root)
        setupSystemColors()

        val extras: Bundle? = intent.extras

        conversationUser = currentUserProvider.currentUser.blockingGet()

        roomToken = extras?.getString(BundleKeys.KEY_ROOM_TOKEN)!!

        conversationInfoEditViewModel =
            ViewModelProvider(this, viewModelFactory)[ConversationInfoEditViewModel::class.java]

        conversationInfoEditViewModel.getRoom(conversationUser, roomToken)

        viewThemeUtils.material.colorTextInputLayout(binding.conversationNameInputLayout)
        viewThemeUtils.material.colorTextInputLayout(binding.conversationDescriptionInputLayout)

        credentials = ApiUtils.getCredentials(conversationUser.username, conversationUser.token)

        pickImage = PickImage(this, conversationUser)

        initObservers()
    }

    override fun onResume() {
        super.onResume()
    }

    private fun initObservers() {
        conversationInfoEditViewModel.viewState.observe(this) { state ->
            when (state) {
                is ConversationInfoEditViewModel.GetRoomSuccessState -> {
                    conversation = state.conversationModel

                    binding.conversationName.setText(conversation!!.displayName)

                    if (conversation!!.description != null && conversation!!.description!!.isNotEmpty()) {
                        binding.conversationDescription.setText(conversation!!.description)
                    }

                    if (!CapabilitiesUtilNew.isConversationDescriptionEndpointAvailable(conversationUser)) {
                        binding.conversationDescription.isEnabled = false
                    }

                    loadConversationAvatar()
                }

                is ConversationInfoEditViewModel.GetRoomErrorState -> {
                    Toast.makeText(context, R.string.nc_common_error_sorry, Toast.LENGTH_LONG).show()
                }

                is ConversationInfoEditViewModel.UploadAvatarSuccessState -> {
                    conversation = state.conversationModel
                    loadConversationAvatar()
                }

                is ConversationInfoEditViewModel.UploadAvatarErrorState -> {
                    Toast.makeText(context, R.string.nc_common_error_sorry, Toast.LENGTH_LONG).show()
                }

                is ConversationInfoEditViewModel.DeleteAvatarSuccessState -> {
                    conversation = state.conversationModel
                    loadConversationAvatar()
                }

                is ConversationInfoEditViewModel.DeleteAvatarErrorState -> {
                    Toast.makeText(context, R.string.nc_common_error_sorry, Toast.LENGTH_LONG).show()
                }

                else -> {}
            }
        }
    }

    private fun setupAvatarOptions() {
        binding.avatarUpload.setOnClickListener { pickImage.selectLocal() }
        binding.avatarChoose.setOnClickListener { pickImage.selectRemote() }
        binding.avatarCamera.setOnClickListener { pickImage.takePicture() }
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
        supportActionBar?.setIcon(ColorDrawable(resources!!.getColor(android.R.color.transparent, null)))
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
            saveConversationNameAndDescription()
        }
        return true
    }

    private fun saveConversationNameAndDescription() {
        val apiVersion =
            ApiUtils.getConversationApiVersion(conversationUser, intArrayOf(ApiUtils.APIv4, ApiUtils.APIv1))

        ncApi.renameRoom(
            credentials,
            ApiUtils.getUrlForRoom(
                apiVersion,
                conversationUser.baseUrl,
                conversation!!.token
            ),
            binding.conversationName.text.toString()
        )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .retry(1)
            .subscribe(object : Observer<GenericOverall> {
                override fun onSubscribe(d: Disposable) {
                    // unused atm
                }

                override fun onNext(genericOverall: GenericOverall) {
                    if (CapabilitiesUtilNew.isConversationDescriptionEndpointAvailable(conversationUser)) {
                        saveConversationDescription()
                    } else {
                        finish()
                    }
                }

                override fun onError(e: Throwable) {
                    Toast.makeText(
                        applicationContext,
                        context.getString(R.string.default_error_msg),
                        Toast.LENGTH_LONG
                    ).show()
                    Log.e(TAG, "Error while saving conversation name", e)
                }

                override fun onComplete() {
                    // unused atm
                }
            })
    }

    fun saveConversationDescription() {
        val apiVersion =
            ApiUtils.getConversationApiVersion(conversationUser, intArrayOf(ApiUtils.APIv4, ApiUtils.APIv1))

        ncApi.setConversationDescription(
            credentials,
            ApiUtils.getUrlForConversationDescription(
                apiVersion,
                conversationUser.baseUrl,
                conversation!!.token
            ),
            binding.conversationDescription.text.toString()
        )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .retry(1)
            .subscribe(object : Observer<GenericOverall> {
                override fun onSubscribe(d: Disposable) {
                    // unused atm
                }

                override fun onNext(genericOverall: GenericOverall) {
                    finish()
                }

                override fun onError(e: Throwable) {
                    Toast.makeText(
                        applicationContext,
                        context.getString(R.string.default_error_msg),
                        Toast.LENGTH_LONG
                    ).show()
                    Log.e(TAG, "Error while saving conversation description", e)
                }

                override fun onComplete() {
                    // unused atm
                }
            })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (resultCode) {
            Activity.RESULT_OK -> {
                pickImage.handleActivityResult(
                    requestCode,
                    resultCode,
                    data
                ) { uploadAvatar(it.toFile()) }
            }

            ImagePicker.RESULT_ERROR -> {
                Toast.makeText(this, ImagePicker.getError(data), Toast.LENGTH_SHORT).show()
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
            ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL -> if (!TextUtils.isEmpty(conversation!!.name)) {
                conversation!!.name?.let { binding.avatarImage.loadUserAvatar(conversationUser, it, true, false) }
            }

            ConversationType.ROOM_GROUP_CALL, ConversationType.ROOM_PUBLIC_CALL -> {
                binding.avatarImage.loadConversationAvatar(conversationUser, conversation!!, false, viewThemeUtils)
            }

            ConversationType.ROOM_SYSTEM -> {
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
