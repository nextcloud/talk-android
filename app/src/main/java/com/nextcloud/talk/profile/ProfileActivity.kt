/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Ezhil Shanmugham <ezhil56x.contact@gmail.com>
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2022 Tim Krüger <t@timkrueger.me>
 * SPDX-FileCopyrightText: 2021 Tobias Kaminsky <tobias.kaminsky@nextcloud.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.profile

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import autodagger.AutoInjector
import com.github.dhaval2404.imagepicker.ImagePicker
import com.github.dhaval2404.imagepicker.ImagePicker.Companion.getError
import com.google.android.material.snackbar.Snackbar
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.BaseActivity
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.ActivityProfileBinding
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.models.json.userprofile.Scope
import com.nextcloud.talk.models.json.userprofile.UserProfileData
import com.nextcloud.talk.models.json.userprofile.UserProfileFieldsOverall
import com.nextcloud.talk.models.json.userprofile.UserProfileOverall
import com.nextcloud.talk.ui.dialog.ScopeDialog
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.CapabilitiesUtil
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.Mimetype.IMAGE_JPG
import com.nextcloud.talk.utils.Mimetype.IMAGE_PREFIX_GENERIC
import com.nextcloud.talk.utils.PickImage
import com.nextcloud.talk.utils.PickImage.Companion.REQUEST_PERMISSION_CAMERA
import com.nextcloud.talk.utils.SpreedFeatures
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.LinkedList
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
@Suppress("Detekt.TooManyFunctions")
class ProfileActivity : BaseActivity() {
    private lateinit var binding: ActivityProfileBinding

    @Inject
    lateinit var ncApi: NcApi

    @Inject
    lateinit var userManager: UserManager

    private var currentUser: User? = null
    private var edit = false
    private var adapter: UserInfoAdapter? = null
    private var userInfo: UserProfileData? = null
    private var editableFields = ArrayList<String>()
    private var isProfileEnabled by mutableStateOf(false)

    private lateinit var pickImage: PickImage

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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_EDIT_MODE, edit)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        edit = savedInstanceState?.getBoolean(KEY_EDIT_MODE) ?: false
        setupActionBar()
        setContentView(binding.root)
        initSystemBars()
        val colorScheme = viewThemeUtils.getColorScheme(this)
        binding.profileSettingEnabledProfile.apply {
            setContent {
                MaterialTheme(colorScheme = colorScheme) {
                    ProfileEnabledCard(
                        isEnabled = isProfileEnabled,
                        onCheckedChange = { isProfileEnabled = it }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        adapter = UserInfoAdapter(null, viewThemeUtils, this)
        binding.userinfoList.adapter = adapter
        binding.userinfoList.setItemViewCacheSize(DEFAULT_CACHE_SIZE)
        currentUser = currentUserProviderOld.currentUser.blockingGet()
        val credentials = ApiUtils.getCredentials(currentUser!!.username, currentUser!!.token)

        pickImage = PickImage(this, currentUser)
        binding.avatarUpload.setOnClickListener {
            pickImage.selectLocal(startImagePickerForResult = startImagePickerForResult)
        }
        binding.avatarChoose.setOnClickListener {
            pickImage.selectRemote(startSelectRemoteFilesIntentForResult = startSelectRemoteFilesIntentForResult)
        }
        binding.avatarCamera.setOnClickListener {
            pickImage.takePicture(startTakePictureIntentForResult = startTakePictureIntentForResult)
        }
        binding.avatarDelete.setOnClickListener {
            ncApi.deleteAvatar(
                credentials,
                ApiUtils.getUrlForTempAvatar(currentUser!!.baseUrl!!)
            )
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(object : Observer<GenericOverall> {
                    override fun onSubscribe(d: Disposable) {
                        // unused atm
                    }

                    override fun onNext(genericOverall: GenericOverall) {
                        DisplayUtils.loadAvatarImage(
                            currentUser,
                            binding.avatarImage,
                            true
                        )
                    }

                    override fun onError(e: Throwable) {
                        Log.e(TAG, "Failed to delete avatar", e)
                    }

                    override fun onComplete() {
                        // unused atm
                    }
                })
        }
        binding.avatarImage.let { ViewCompat.setTransitionName(it, "userAvatar.transitionTag") }

        if (CapabilitiesUtil.canEditScopes(currentUser!!)) {
            ncApi.getEditableUserProfileFields(
                credentials,
                ApiUtils.getUrlForUserFields(currentUser!!.baseUrl!!)
            )
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(object : Observer<UserProfileFieldsOverall> {
                    override fun onSubscribe(d: Disposable) {
                        // unused atm
                    }

                    override fun onNext(userProfileFieldsOverall: UserProfileFieldsOverall) {
                        editableFields = userProfileFieldsOverall.ocs!!.data!!
                        invalidateOptionsMenu()
                        fetchUserProfile(credentials)
                    }

                    override fun onError(e: Throwable) {
                        Log.e(TAG, "Error loading editable user profile from server", e)
                        edit = false
                        fetchUserProfile(credentials)
                    }

                    override fun onComplete() {
                        // unused atm
                    }
                })
        } else {
            fetchUserProfile(credentials)
        }

        colorIcons()
    }

    private fun fetchUserProfile(credentials: String?) {
        ncApi.getUserProfile(credentials, ApiUtils.getUrlForUserProfile(currentUser!!.baseUrl!!))
            .retry(DEFAULT_RETRIES)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : Observer<UserProfileOverall> {
                override fun onSubscribe(d: Disposable) {
                    // unused atm
                }

                override fun onNext(userProfileOverall: UserProfileOverall) {
                    userInfo = userProfileOverall.ocs!!.data
                    showUserProfile()
                }

                override fun onError(e: Throwable) {
                    setErrorMessageForMultiList(
                        getString(R.string.userinfo_no_info_headline),
                        getString(R.string.userinfo_error_text),
                        R.drawable.ic_list_empty_error
                    )
                }

                override fun onComplete() {
                    // unused atm
                }
            })
    }

    private fun setupActionBar() {
        setSupportActionBar(binding.profileToolbar)
        binding.profileToolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setIcon(resources!!.getColor(android.R.color.transparent, null).toDrawable())
        supportActionBar?.title = context.getString(R.string.nc_profile_personal_info_title)
        viewThemeUtils.material.themeToolbar(binding.profileToolbar)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.menu_profile, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        super.onPrepareOptionsMenu(menu)
        menu.findItem(R.id.edit).isVisible = editableFields.size > 0
        if (edit) {
            menu.findItem(R.id.edit).setTitle(R.string.save)
            menu.findItem(R.id.edit).icon = ContextCompat.getDrawable(this, R.drawable.ic_check)
        } else {
            menu.findItem(R.id.edit).setTitle(R.string.edit)
            menu.findItem(R.id.edit).icon = ContextCompat.getDrawable(this, R.drawable.ic_edit)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.edit) {
            if (edit) {
                save()
            }
            edit = !edit
            if (edit) {
                item.setTitle(R.string.save)
                item.icon = ContextCompat.getDrawable(this, R.drawable.ic_check)
                binding.emptyList.root.visibility = View.GONE
                binding.userinfoList.visibility = View.VISIBLE
                binding.profileSettingEnabledProfile.visibility = View.VISIBLE
                if (CapabilitiesUtil.hasSpreedFeatureCapability(
                        currentUser?.capabilities?.spreedCapability,
                        SpreedFeatures.TEMP_USER_AVATAR_API
                    )
                ) {
                    // TODO later avatar can also be checked via user fields, for now it is in Talk capability
                    binding.avatarButtons.visibility = View.VISIBLE
                }
                ncApi.getEditableUserProfileFields(
                    ApiUtils.getCredentials(currentUser!!.username, currentUser!!.token),
                    ApiUtils.getUrlForUserFields(currentUser!!.baseUrl!!)
                )
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(object : Observer<UserProfileFieldsOverall> {
                        override fun onSubscribe(d: Disposable) {
                            // unused atm
                        }

                        override fun onNext(userProfileFieldsOverall: UserProfileFieldsOverall) {
                            editableFields = userProfileFieldsOverall.ocs!!.data!!
                            adapter!!.notifyDataSetChanged()
                        }

                        override fun onError(e: Throwable) {
                            Log.e(TAG, "Error loading editable user profile from server", e)
                            edit = false
                        }

                        override fun onComplete() {
                            // unused atm
                        }
                    })
            } else {
                item.setTitle(R.string.edit)
                item.icon = ContextCompat.getDrawable(this, R.drawable.ic_edit)

                binding.avatarButtons.visibility = View.GONE
                binding.profileSettingEnabledProfile.visibility = View.GONE
                if (adapter!!.filteredDisplayList.isEmpty()) {
                    binding.emptyList.root.visibility = View.VISIBLE
                    binding.userinfoList.visibility = View.GONE
                }
            }
            adapter!!.notifyDataSetChanged()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun colorIcons() {
        binding.let {
            viewThemeUtils.material.themeFAB(it.avatarChoose)
            viewThemeUtils.material.themeFAB(it.avatarCamera)
            viewThemeUtils.material.themeFAB(it.avatarUpload)
            viewThemeUtils.material.themeFAB(it.avatarDelete)
        }
    }

    private fun isAllEmpty(items: Array<String?>): Boolean {
        for (item in items) {
            if (!TextUtils.isEmpty(item)) {
                return false
            }
        }

        return true
    }

    private fun showUserProfile() {
        if (currentUser!!.baseUrl != null) {
            binding.userinfoBaseurl.text = currentUser!!.baseUrl!!.toUri().host
        }
        DisplayUtils.loadAvatarImage(currentUser, binding.avatarImage, false)
        if (!TextUtils.isEmpty(userInfo?.displayName)) {
            binding.userinfoFullName.text = userInfo?.displayName
        }
        binding.loadingContent.visibility = View.VISIBLE

        isProfileEnabled = "1" == userInfo?.profileEnabled

        adapter!!.setData(createUserInfoDetails(userInfo))
        if (
            isAllEmpty(
                arrayOf(
                    userInfo?.displayName,
                    userInfo?.phone,
                    userInfo?.email,
                    userInfo?.address,
                    userInfo?.twitter,
                    userInfo?.website,
                    userInfo?.biography,
                    userInfo?.bluesky,
                    userInfo?.fediverse,
                    userInfo?.headline,
                    userInfo?.organisation,
                    userInfo?.pronouns,
                    userInfo?.role
                )
            )
        ) {
            binding.userinfoList.visibility = View.GONE
            binding.profileSettingEnabledProfile.visibility = if (edit) View.VISIBLE else View.GONE
            binding.loadingContent.visibility = View.GONE
            binding.emptyList.root.visibility = View.VISIBLE
            setErrorMessageForMultiList(
                getString(R.string.userinfo_no_info_headline),
                getString(R.string.userinfo_no_info_text),
                R.drawable.ic_user
            )
        } else {
            binding.emptyList.root.visibility = View.GONE
            binding.loadingContent.visibility = View.GONE
            binding.profileSettingEnabledProfile.visibility = if (edit) {
                View.VISIBLE
            } else {
                View.GONE
            }
            binding.userinfoList.visibility = View.VISIBLE
        }
        binding.avatarButtons.visibility = if (edit &&
            CapabilitiesUtil.hasSpreedFeatureCapability(
                currentUser?.capabilities?.spreedCapability,
                SpreedFeatures.TEMP_USER_AVATAR_API
            )
        ) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    private fun setErrorMessageForMultiList(headline: String, message: String, @DrawableRes errorResource: Int) {
        binding.emptyList.emptyListViewHeadline.text = headline
        binding.emptyList.emptyListViewText.text = message
        binding.emptyList.emptyListIcon.setImageResource(errorResource)
        binding.emptyList.emptyListIcon.visibility = View.VISIBLE
        binding.emptyList.emptyListViewText.visibility = View.VISIBLE
        binding.profileSettingEnabledProfile.visibility = View.GONE
        binding.userinfoList.visibility = View.GONE
        binding.loadingContent.visibility = View.GONE
    }

    private fun createUserInfoDetails(userInfo: UserProfileData?): List<UserInfoDetailsItem> {
        val result: MutableList<UserInfoDetailsItem> = LinkedList()

        if (userInfo != null) {
            result.add(
                UserInfoDetailsItem(
                    R.drawable.ic_user,
                    userInfo.displayName,
                    resources!!.getString(R.string.user_info_displayname),
                    Field.DISPLAYNAME,
                    userInfo.displayNameScope
                )
            )
            result.add(
                UserInfoDetailsItem(
                    R.drawable.ic_record_voice_over_24px,
                    userInfo.pronouns,
                    resources!!.getString(R.string.user_info_pronouns),
                    Field.PRONOUNS,
                    userInfo.pronounsScope
                )
            )
            result.add(
                UserInfoDetailsItem(
                    R.drawable.ic_email,
                    userInfo.email,
                    resources!!.getString(R.string.user_info_email),
                    Field.EMAIL,
                    userInfo.emailScope
                )
            )
            result.add(
                UserInfoDetailsItem(
                    R.drawable.ic_phone,
                    userInfo.phone,
                    resources!!.getString(R.string.user_info_phone),
                    Field.PHONE,
                    userInfo.phoneScope
                )
            )
            result.add(
                UserInfoDetailsItem(
                    R.drawable.ic_map_marker,
                    userInfo.address,
                    resources!!.getString(R.string.user_info_address),
                    Field.ADDRESS,
                    userInfo.addressScope
                )
            )
            result.add(
                UserInfoDetailsItem(
                    R.drawable.ic_web,
                    userInfo.website,
                    resources!!.getString(R.string.user_info_website),
                    Field.WEBSITE,
                    userInfo.websiteScope
                )
            )
            result.add(
                UserInfoDetailsItem(
                    R.drawable.ic_twitter,
                    userInfo.twitter,
                    resources!!.getString(R.string.user_info_twitter),
                    Field.TWITTER,
                    userInfo.twitterScope
                )
            )
            result.add(
                UserInfoDetailsItem(
                    R.drawable.ic_cloud_24px,
                    userInfo.bluesky,
                    resources!!.getString(R.string.user_info_bluesky),
                    Field.BLUESKY,
                    userInfo.blueskyScope
                )
            )
            result.add(
                UserInfoDetailsItem(
                    R.drawable.ic_fediverse_24px,
                    userInfo.fediverse,
                    resources!!.getString(R.string.user_info_fediverse),
                    Field.FEDIVERSE,
                    userInfo.fediverseScope
                )
            )
            result.add(
                UserInfoDetailsItem(
                    R.drawable.ic_home_work_24px,
                    userInfo.organisation,
                    resources!!.getString(R.string.user_info_organisation),
                    Field.ORGANISATION,
                    userInfo.organisationScope
                )
            )
            result.add(
                UserInfoDetailsItem(
                    R.drawable.ic_work_24px,
                    userInfo.role,
                    resources!!.getString(R.string.user_info_role),
                    Field.ROLE,
                    userInfo.roleScope
                )
            )
            result.add(
                UserInfoDetailsItem(
                    R.drawable.ic_info_24px,
                    userInfo.headline,
                    resources!!.getString(R.string.user_info_headline),
                    Field.HEADLINE,
                    userInfo.headlineScope
                )
            )
            result.add(
                UserInfoDetailsItem(
                    R.drawable.ic_article_24px,
                    userInfo.biography,
                    resources!!.getString(R.string.user_info_biography),
                    Field.BIOGRAPHY,
                    userInfo.biographyScope
                )
            )
        }
        return result
    }

    private fun save() {
        for (item in adapter!!.displayList!!) {
            // Text
            if (item.text != userInfo?.getValueByField(item.field)) {
                val credentials = ApiUtils.getCredentials(currentUser!!.username, currentUser!!.token)
                ncApi.setUserData(
                    credentials,
                    ApiUtils.getUrlForUserData(currentUser!!.baseUrl!!, currentUser!!.userId!!),
                    item.field.fieldName,
                    item.text
                )
                    .retry(DEFAULT_RETRIES)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(object : Observer<GenericOverall> {
                        override fun onSubscribe(d: Disposable) {
                            // unused atm
                        }

                        override fun onNext(userProfileOverall: GenericOverall) {
                            Log.d(TAG, "Successfully saved: " + item.text + " as " + item.field)
                            if (item.field == Field.DISPLAYNAME) {
                                binding?.userinfoFullName?.text = item.text
                            }
                        }

                        override fun onError(e: Throwable) {
                            item.text = userInfo?.getValueByField(item.field)
                            Snackbar.make(
                                binding.root,
                                String.format(
                                    resources!!.getString(R.string.failed_to_save),
                                    item.field
                                ),
                                Snackbar.LENGTH_LONG
                            ).show()
                            adapter!!.updateFilteredList()
                            adapter!!.notifyDataSetChanged()
                            Log.e(TAG, "Failed to save: " + item.text + " as " + item.field, e)
                        }

                        override fun onComplete() {
                            // unused atm
                        }
                    })
            }

            // Scope
            if (item.scope != userInfo?.getScopeByField(item.field)) {
                saveScope(item, userInfo)
            }
            adapter!!.updateFilteredList()
        }

        // Profile enabled
        val originalProfileEnabled = "1" == userInfo?.profileEnabled
        if (isProfileEnabled != originalProfileEnabled) {
            val newValue = if (isProfileEnabled) "1" else "0"
            val credentials = ApiUtils.getCredentials(currentUser!!.username, currentUser!!.token)
            ncApi.setUserData(
                credentials,
                ApiUtils.getUrlForUserData(currentUser!!.baseUrl!!, currentUser!!.userId!!),
                Field.PROFILE_ENABLED.fieldName,
                newValue
            )
                .retry(DEFAULT_RETRIES)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(object : Observer<GenericOverall> {
                    override fun onSubscribe(d: Disposable) {
                        // unused atm
                    }

                    override fun onNext(genericOverall: GenericOverall) {
                        Log.d(TAG, "Successfully saved: $newValue as ${Field.PROFILE_ENABLED.fieldName}")
                        userInfo?.profileEnabled = newValue
                    }

                    override fun onError(e: Throwable) {
                        isProfileEnabled = originalProfileEnabled
                        Snackbar.make(
                            binding.root,
                            String.format(
                                resources!!.getString(R.string.failed_to_save),
                                Field.PROFILE_ENABLED.fieldName
                            ),
                            Snackbar.LENGTH_LONG
                        ).show()
                        Log.e(TAG, "Failed to save: $newValue as ${Field.PROFILE_ENABLED.fieldName}", e)
                    }

                    override fun onComplete() {
                        // unused atm
                    }
                })
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSION_CAMERA) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pickImage.takePicture(startTakePictureIntentForResult = startTakePictureIntentForResult)
            } else {
                Snackbar
                    .make(binding.root, context.getString(R.string.take_photo_permission), Snackbar.LENGTH_LONG)
                    .show()
            }
        }
    }

    private fun handleResult(result: ActivityResult, onResult: (result: ActivityResult) -> Unit) {
        when (result.resultCode) {
            Activity.RESULT_OK -> onResult(result)

            ImagePicker.RESULT_ERROR -> {
                Snackbar.make(binding.root, getError(result.data), Snackbar.LENGTH_SHORT).show()
            }

            else -> {
                Log.i(TAG, "Task Cancelled")
            }
        }
    }

    private fun uploadAvatar(file: File?) {
        val builder = MultipartBody.Builder()
        builder.setType(MultipartBody.FORM)
        builder.addFormDataPart(
            "files[]",
            file!!.name,
            file.asRequestBody(IMAGE_PREFIX_GENERIC.toMediaTypeOrNull())
        )
        val filePart: MultipartBody.Part = MultipartBody.Part.createFormData(
            "files[]",
            file.name,
            file.asRequestBody(IMAGE_JPG.toMediaTypeOrNull())
        )

        // upload file
        ncApi.uploadAvatar(
            ApiUtils.getCredentials(currentUser!!.username, currentUser!!.token),
            ApiUtils.getUrlForTempAvatar(currentUser!!.baseUrl!!),
            filePart
        )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : Observer<GenericOverall> {
                override fun onSubscribe(d: Disposable) {
                    // unused atm
                }

                override fun onNext(genericOverall: GenericOverall) {
                    DisplayUtils.loadAvatarImage(currentUser, binding?.avatarImage, true)
                }

                override fun onError(e: Throwable) {
                    Snackbar.make(
                        binding.root,
                        context.getString(R.string.default_error_msg),
                        Snackbar
                            .LENGTH_LONG
                    ).show()
                    Log.e(TAG, "Error uploading avatar", e)
                }

                override fun onComplete() {
                    // unused atm
                }
            })
    }

    fun saveScope(item: UserInfoDetailsItem, userInfo: UserProfileData?) {
        val credentials = ApiUtils.getCredentials(currentUser!!.username, currentUser!!.token)
        ncApi.setUserData(
            credentials,
            ApiUtils.getUrlForUserData(currentUser!!.baseUrl!!, currentUser!!.userId!!),
            item.field.scopeName,
            item.scope!!.id
        )
            .retry(DEFAULT_RETRIES)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : Observer<GenericOverall> {
                override fun onSubscribe(d: Disposable) {
                    // unused atm
                }

                override fun onNext(userProfileOverall: GenericOverall) {
                    Log.d(TAG, "Successfully saved: " + item.scope!!.id + " as " + item.field.scopeName)
                }

                override fun onError(e: Throwable) {
                    item.scope = userInfo?.getScopeByField(item.field)
                    Log.e(TAG, "Failed to saved: " + item.scope!!.id + " as " + item.field.scopeName, e)
                }

                override fun onComplete() {
                    // unused atm
                }
            })
    }

    class UserInfoDetailsItem(
        @field:DrawableRes @param:DrawableRes
        var icon: Int,
        var text: String?,
        var hint: String,
        val field: Field,
        var scope: Scope?
    )

    class UserInfoAdapter(
        displayList: List<UserInfoDetailsItem>?,
        private val viewThemeUtils: ViewThemeUtils,
        private val profileActivity: ProfileActivity
    ) : RecyclerView.Adapter<UserInfoAdapter.ViewHolder>() {
        var displayList: List<UserInfoDetailsItem>?
        var filteredDisplayList: MutableList<UserInfoDetailsItem> = LinkedList()

        class ViewHolder(val composeView: ComposeView) : RecyclerView.ViewHolder(composeView)

        init {
            this.displayList = displayList ?: LinkedList()
        }

        fun setData(displayList: List<UserInfoDetailsItem>) {
            this.displayList = displayList
            updateFilteredList()
            notifyDataSetChanged()
        }

        fun updateFilteredList() {
            filteredDisplayList.clear()
            if (displayList != null) {
                for (item in displayList!!) {
                    if (!TextUtils.isEmpty(item.text)) {
                        filteredDisplayList.add(item)
                    }
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val composeView = ComposeView(parent.context).apply {
                layoutParams =
                    ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            }
            return ViewHolder(composeView)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item: UserInfoDetailsItem = if (profileActivity.edit) {
                displayList!![position]
            } else {
                filteredDisplayList[position]
            }
            val colorScheme = viewThemeUtils.getColorScheme(profileActivity)
            val itemPosition = when (position) {
                0 -> UserInfoDetailItemPosition.FIRST
                filteredDisplayList.size - 1 -> UserInfoDetailItemPosition.LAST
                else -> UserInfoDetailItemPosition.MIDDLE
            }

            if (profileActivity.edit) {
                val itemData = UserInfoDetailItemData(
                    icon = item.icon,
                    text = item.text.orEmpty(),
                    hint = item.hint,
                    scope = item.scope
                )
                val listeners = UserInfoDetailListeners(
                    onTextChange = { newText ->
                        if (profileActivity.edit) {
                            displayList!![position].text = newText
                        } else {
                            filteredDisplayList[position].text = newText
                        }
                    },
                    onScopeClick = { ScopeDialog(profileActivity, this, item.field, position).show() }
                )
                holder.composeView.setContent {
                    MaterialTheme(colorScheme = colorScheme) {
                        UserInfoDetailItemEditable(
                            data = itemData,
                            listeners = listeners,
                            position = itemPosition,
                            enabled = profileActivity.editableFields.contains(item.field.toString().lowercase()),
                            multiLine = item.field == Field.BIOGRAPHY
                        )
                    }
                }
            } else {
                val displayText = when (item.field) {
                    Field.WEBSITE -> DisplayUtils.beautifyURL(item.text)
                    Field.TWITTER -> DisplayUtils.beautifyTwitterHandle(item.text)
                    else -> item.text.orEmpty()
                }
                holder.composeView.setContent {
                    MaterialTheme(colorScheme = colorScheme) {
                        UserInfoDetailItemViewOnly(
                            userInfo = UserInfoDetailItemData(
                                icon = item.icon,
                                text = displayText,
                                hint = item.hint,
                                scope = item.scope
                            ),
                            position = itemPosition,
                            ellipsize = Field.EMAIL == item.field
                        )
                    }
                }
            }
        }

        override fun getItemCount(): Int =
            if (profileActivity.edit) {
                displayList!!.size
            } else {
                filteredDisplayList.size
            }

        fun updateScope(position: Int, scope: Scope?) {
            displayList!![position].scope = scope
            notifyDataSetChanged()
        }
    }

    enum class Field(val fieldName: String, val scopeName: String) {
        EMAIL("email", "emailScope"),
        DISPLAYNAME("displayname", "displaynameScope"),
        PHONE("phone", "phoneScope"),
        ADDRESS("address", "addressScope"),
        WEBSITE("website", "websiteScope"),
        TWITTER("twitter", "twitterScope"),
        BIOGRAPHY("biography", "biographyScope"),
        FEDIVERSE("fediverse", "fediverseScope"),
        HEADLINE("headline", "headlineScope"),
        ORGANISATION("organisation", "organisationScope"),
        PROFILE_ENABLED("profile_enabled", "profile_enabledScope"),
        PRONOUNS("pronouns", "pronounsScope"),
        ROLE("role", "roleScope"),
        BLUESKY("bluesky", "blueskyScope")
    }

    companion object {
        private val TAG = ProfileActivity::class.java.simpleName
        private const val DEFAULT_CACHE_SIZE: Int = 20
        private const val DEFAULT_RETRIES: Long = 3
        private const val KEY_EDIT_MODE = "edit_mode"
    }
}
