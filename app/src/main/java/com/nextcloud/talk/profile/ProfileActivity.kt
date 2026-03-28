/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2021-2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.profile

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toFile
import androidx.core.net.toUri
import autodagger.AutoInjector
import com.github.dhaval2404.imagepicker.ImagePicker
import com.github.dhaval2404.imagepicker.ImagePicker.Companion.getError
import com.google.android.material.snackbar.Snackbar
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.BaseActivity
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.components.ColoredStatusBar
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.models.json.userprofile.Scope
import com.nextcloud.talk.models.json.userprofile.UserProfileData
import com.nextcloud.talk.models.json.userprofile.UserProfileFieldsOverall
import com.nextcloud.talk.models.json.userprofile.UserProfileOverall
import com.nextcloud.talk.ui.dialog.ScopeModalBottomSheet
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.CapabilitiesUtil
import com.nextcloud.talk.utils.Mimetype.IMAGE_JPG
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
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
@Suppress("Detekt.TooManyFunctions")
class ProfileActivity : BaseActivity() {

    @Inject
    lateinit var ncApi: NcApi

    @Inject
    lateinit var userManager: UserManager

    private var currentUser: User? = null
    private var userInfo: UserProfileData? = null
    private var editableFields = ArrayList<String>()

    /** Single source of truth that drives the Compose UI. */
    private var profileUiState by mutableStateOf(ProfileUiState())

    private data class ScopeSheetRequest(val position: Int, val field: Field)
    private var scopeSheetRequest by mutableStateOf<ScopeSheetRequest?>(null)

    private val profileItems: MutableList<UserInfoDetailsItem> = mutableListOf()

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
        outState.putBoolean(KEY_EDIT_MODE, profileUiState.isEditMode)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        val restoredEdit = savedInstanceState?.getBoolean(KEY_EDIT_MODE) ?: false
        profileUiState = profileUiState.copy(isEditMode = restoredEdit)

        val colorScheme = viewThemeUtils.getColorScheme(this)
        setContent {
            MaterialTheme(colorScheme = colorScheme) {
                ColoredStatusBar()
                ProfileScreen(
                    state = profileUiState,
                    callbacks = ProfileCallbacks(
                        onNavigateBack = { onBackPressedDispatcher.onBackPressed() },
                        onEditSave = ::handleEditSave,
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
                        onAvatarDeleteClick = ::deleteAvatar,
                        onProfileEnabledChange = { enabled ->
                            profileUiState = profileUiState.copy(isProfileEnabled = enabled)
                        },
                        onTextChange = { position, newText ->
                            profileItems.getOrNull(position)?.text = newText
                        },
                        onScopeClick = { position, field ->
                            scopeSheetRequest = ScopeSheetRequest(position, field)
                        }
                    )
                )
                scopeSheetRequest?.let { req ->
                    ScopeModalBottomSheet(
                        showPrivate = req.field != Field.DISPLAYNAME && req.field != Field.EMAIL,
                        onScopeSelected = { scope -> updateItemScope(req.position, scope) },
                        onDismiss = { scopeSheetRequest = null }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        currentUser = currentUserProviderOld.currentUser.blockingGet()
        val credentials = ApiUtils.getCredentials(currentUser!!.username, currentUser!!.token)

        pickImage = PickImage(this, currentUser)

        if (CapabilitiesUtil.canEditScopes(currentUser!!)) {
            ncApi.getEditableUserProfileFields(
                credentials,
                ApiUtils.getUrlForUserFields(currentUser!!.baseUrl!!)
            )
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(object : Observer<UserProfileFieldsOverall> {
                    override fun onSubscribe(d: Disposable) = Unit

                    override fun onNext(userProfileFieldsOverall: UserProfileFieldsOverall) {
                        editableFields = userProfileFieldsOverall.ocs!!.data!!
                        profileUiState = profileUiState.copy(editableFields = editableFields.toList())
                        fetchUserProfile(credentials)
                    }

                    override fun onError(e: Throwable) {
                        Log.e(TAG, "Error loading editable user profile from server", e)
                        fetchUserProfile(credentials)
                    }

                    override fun onComplete() = Unit
                })
        } else {
            fetchUserProfile(credentials)
        }
    }

    private fun fetchUserProfile(credentials: String?) {
        ncApi.getUserProfile(credentials, ApiUtils.getUrlForUserProfile(currentUser!!.baseUrl!!))
            .retry(DEFAULT_RETRIES)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : Observer<UserProfileOverall> {
                override fun onSubscribe(d: Disposable) = Unit

                override fun onNext(userProfileOverall: UserProfileOverall) {
                    userInfo = userProfileOverall.ocs!!.data
                    showUserProfile()
                }

                override fun onError(e: Throwable) {
                    setErrorState(
                        getString(R.string.userinfo_no_info_headline),
                        getString(R.string.userinfo_error_text),
                        R.drawable.ic_list_empty_error
                    )
                }

                override fun onComplete() = Unit
            })
    }

    private fun handleEditSave() {
        val currentlyEditing = profileUiState.isEditMode
        if (currentlyEditing) {
            save()
        }

        val enteringEdit = !currentlyEditing
        if (enteringEdit) {
            profileUiState = profileUiState.copy(
                isEditMode = true,
                contentState = ProfileContentState.ShowList,
                showProfileEnabledCard = true,
                showAvatarButtons = CapabilitiesUtil.hasSpreedFeatureCapability(
                    currentUser?.capabilities?.spreedCapability,
                    SpreedFeatures.TEMP_USER_AVATAR_API
                )
            )
            loadEditableFields()
        } else {
            val hasVisibleItems = profileItems.any { !it.text.isNullOrEmpty() }
            profileUiState = profileUiState.copy(
                isEditMode = false,
                showAvatarButtons = false,
                showProfileEnabledCard = false,
                contentState = if (hasVisibleItems) {
                    ProfileContentState.ShowList
                } else {
                    ProfileContentState.Empty(
                        headline = getString(R.string.userinfo_no_info_headline),
                        message = getString(R.string.userinfo_no_info_text),
                        iconRes = R.drawable.ic_user
                    )
                }
            )
        }
    }

    private fun loadEditableFields() {
        ncApi.getEditableUserProfileFields(
            ApiUtils.getCredentials(currentUser!!.username, currentUser!!.token),
            ApiUtils.getUrlForUserFields(currentUser!!.baseUrl!!)
        )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : Observer<UserProfileFieldsOverall> {
                override fun onSubscribe(d: Disposable) = Unit

                override fun onNext(userProfileFieldsOverall: UserProfileFieldsOverall) {
                    editableFields = userProfileFieldsOverall.ocs!!.data!!
                    profileUiState = profileUiState.copy(editableFields = editableFields.toList())
                }

                override fun onError(e: Throwable) {
                    Log.e(TAG, "Error loading editable user profile from server", e)
                    profileUiState = profileUiState.copy(isEditMode = false)
                }

                override fun onComplete() = Unit
            })
    }

    private fun isAllEmpty(items: Array<String?>): Boolean = items.all { it.isNullOrEmpty() }

    private fun showUserProfile() {
        val baseUrl = currentUser!!.baseUrl?.toUri()?.host.orEmpty()
        val displayName = userInfo?.displayName.orEmpty()
        val isProfileEnabled = "1" == userInfo?.profileEnabled

        val allItems = createUserInfoDetails(userInfo)
        profileItems.clear()
        profileItems.addAll(allItems)

        setupUserProfileVisibilities(displayName, baseUrl, currentUser, isProfileEnabled, allItems)
    }

    private fun setupUserProfileVisibilities(
        displayName: String,
        baseUrl: String,
        currentUser: User?,
        isProfileEnabled: Boolean,
        allItems: List<UserInfoDetailsItem>
    ) {
        val hasContent = hasProfileContent()
        val showEditControls = profileUiState.isEditMode
        profileUiState = profileUiState.copy(
            displayName = displayName,
            baseUrl = baseUrl,
            currentUser = currentUser,
            isProfileEnabled = isProfileEnabled,
            showProfileEnabledCard = showEditControls,
            showAvatarButtons = showEditControls &&
                CapabilitiesUtil.hasSpreedFeatureCapability(
                    currentUser?.capabilities?.spreedCapability,
                    SpreedFeatures.TEMP_USER_AVATAR_API
                ),
            items = allItems,
            filteredItems = profileItems.filter { !it.text.isNullOrEmpty() },
            contentState = when {
                hasContent -> ProfileContentState.ShowList
                showEditControls -> ProfileContentState.ShowList
                else -> ProfileContentState.Empty(
                    headline = getString(R.string.userinfo_no_info_headline),
                    message = getString(R.string.userinfo_no_info_text),
                    iconRes = R.drawable.ic_user
                )
            }
        )
    }

    private fun hasProfileContent(): Boolean =
        !isAllEmpty(
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

    private fun setErrorState(headline: String, message: String, @DrawableRes iconRes: Int) {
        profileUiState = profileUiState.copy(
            contentState = ProfileContentState.Empty(headline, message, iconRes),
            showProfileEnabledCard = false,
            showAvatarButtons = false
        )
    }

    private fun createUserInfoDetails(userInfo: UserProfileData?): List<UserInfoDetailsItem> {
        if (userInfo == null) return emptyList()
        return createIdentityItems(userInfo) +
            createContactItems(userInfo) +
            createSocialItems(userInfo) +
            createProfessionalItems(userInfo)
    }

    private fun createIdentityItems(userInfo: UserProfileData): List<UserInfoDetailsItem> =
        listOf(
            UserInfoDetailsItem(
                R.drawable.ic_user,
                userInfo.displayName,
                resources!!.getString(R.string.user_info_displayname),
                Field.DISPLAYNAME,
                userInfo.displayNameScope
            ),
            UserInfoDetailsItem(
                R.drawable.ic_record_voice_over_24px,
                userInfo.pronouns,
                resources!!.getString(R.string.user_info_pronouns),
                Field.PRONOUNS,
                userInfo.pronounsScope
            )
        )

    private fun createContactItems(userInfo: UserProfileData): List<UserInfoDetailsItem> =
        listOf(
            UserInfoDetailsItem(
                R.drawable.ic_email,
                userInfo.email,
                resources!!.getString(R.string.user_info_email),
                Field.EMAIL,
                userInfo.emailScope
            ),
            UserInfoDetailsItem(
                R.drawable.ic_phone,
                userInfo.phone,
                resources!!.getString(R.string.user_info_phone),
                Field.PHONE,
                userInfo.phoneScope
            ),
            UserInfoDetailsItem(
                R.drawable.ic_map_marker,
                userInfo.address,
                resources!!.getString(R.string.user_info_address),
                Field.ADDRESS,
                userInfo.addressScope
            ),
            UserInfoDetailsItem(
                R.drawable.ic_web,
                userInfo.website,
                resources!!.getString(R.string.user_info_website),
                Field.WEBSITE,
                userInfo.websiteScope
            )
        )

    private fun createSocialItems(userInfo: UserProfileData): List<UserInfoDetailsItem> =
        listOf(
            UserInfoDetailsItem(
                R.drawable.ic_twitter,
                userInfo.twitter,
                resources!!.getString(R.string.user_info_twitter),
                Field.TWITTER,
                userInfo.twitterScope
            ),
            UserInfoDetailsItem(
                R.drawable.ic_cloud_24px,
                userInfo.bluesky,
                resources!!.getString(R.string.user_info_bluesky),
                Field.BLUESKY,
                userInfo.blueskyScope
            ),
            UserInfoDetailsItem(
                R.drawable.ic_fediverse_24px,
                userInfo.fediverse,
                resources!!.getString(R.string.user_info_fediverse),
                Field.FEDIVERSE,
                userInfo.fediverseScope
            )
        )

    private fun createProfessionalItems(userInfo: UserProfileData): List<UserInfoDetailsItem> =
        listOf(
            UserInfoDetailsItem(
                R.drawable.ic_home_work_24px,
                userInfo.organisation,
                resources!!.getString(R.string.user_info_organisation),
                Field.ORGANISATION,
                userInfo.organisationScope
            ),
            UserInfoDetailsItem(
                R.drawable.ic_work_24px,
                userInfo.role,
                resources!!.getString(R.string.user_info_role),
                Field.ROLE,
                userInfo.roleScope
            ),
            UserInfoDetailsItem(
                R.drawable.ic_info_24px,
                userInfo.headline,
                resources!!.getString(R.string.user_info_headline),
                Field.HEADLINE,
                userInfo.headlineScope
            ),
            UserInfoDetailsItem(
                R.drawable.ic_article_24px,
                userInfo.biography,
                resources!!.getString(R.string.user_info_biography),
                Field.BIOGRAPHY,
                userInfo.biographyScope
            )
        )

    private fun save() {
        val credentials = ApiUtils.getCredentials(currentUser!!.username, currentUser!!.token)

        for (item in profileItems) {
            if (item.text != userInfo?.getValueByField(item.field)) {
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
                        override fun onSubscribe(d: Disposable) = Unit

                        override fun onNext(userProfileOverall: GenericOverall) {
                            Log.d(TAG, "Successfully saved: ${item.text} as ${item.field}")
                            if (item.field == Field.DISPLAYNAME) {
                                profileUiState = profileUiState.copy(displayName = item.text.orEmpty())
                            }
                        }

                        override fun onError(e: Throwable) {
                            item.text = userInfo?.getValueByField(item.field)
                            Snackbar.make(
                                window.decorView,
                                String.format(resources!!.getString(R.string.failed_to_save), item.field),
                                Snackbar.LENGTH_LONG
                            ).show()
                            syncFilteredItems()
                            Log.e(TAG, "Failed to save: ${item.text} as ${item.field}", e)
                        }

                        override fun onComplete() = Unit
                    })
            }

            if (item.scope != userInfo?.getScopeByField(item.field)) {
                saveScope(item, userInfo)
            }
            syncFilteredItems()
        }

        // Profile enabled
        val originalProfileEnabled = "1" == userInfo?.profileEnabled
        if (profileUiState.isProfileEnabled != originalProfileEnabled) {
            val newValue = if (profileUiState.isProfileEnabled) "1" else "0"
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
                    override fun onSubscribe(d: Disposable) = Unit

                    override fun onNext(genericOverall: GenericOverall) {
                        Log.d(TAG, "Successfully saved: $newValue as ${Field.PROFILE_ENABLED.fieldName}")
                        userInfo?.profileEnabled = newValue
                    }

                    override fun onError(e: Throwable) {
                        profileUiState = profileUiState.copy(isProfileEnabled = originalProfileEnabled)
                        Snackbar.make(
                            window.decorView,
                            String.format(
                                resources!!.getString(R.string.failed_to_save),
                                Field.PROFILE_ENABLED.fieldName
                            ),
                            Snackbar.LENGTH_LONG
                        ).show()
                        Log.e(TAG, "Failed to save: $newValue as ${Field.PROFILE_ENABLED.fieldName}", e)
                    }

                    override fun onComplete() = Unit
                })
        }
    }

    private fun deleteAvatar() {
        val credentials = ApiUtils.getCredentials(currentUser!!.username, currentUser!!.token)
        ncApi.deleteAvatar(credentials, ApiUtils.getUrlForTempAvatar(currentUser!!.baseUrl!!))
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : Observer<GenericOverall> {
                override fun onSubscribe(d: Disposable) = Unit

                override fun onNext(genericOverall: GenericOverall) {
                    profileUiState = profileUiState.copy(
                        avatarRefreshKey = profileUiState.avatarRefreshKey + 1,
                        avatarIsDeleted = true
                    )
                }

                override fun onError(e: Throwable) {
                    Log.e(TAG, "Failed to delete avatar", e)
                }

                override fun onComplete() = Unit
            })
    }

    private fun uploadAvatar(file: File?) {
        val filePart = MultipartBody.Part.createFormData(
            "files[]",
            file!!.name,
            file.asRequestBody(IMAGE_JPG.toMediaTypeOrNull())
        )

        ncApi.uploadAvatar(
            ApiUtils.getCredentials(currentUser!!.username, currentUser!!.token),
            ApiUtils.getUrlForTempAvatar(currentUser!!.baseUrl!!),
            filePart
        )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : Observer<GenericOverall> {
                override fun onSubscribe(d: Disposable) = Unit

                override fun onNext(genericOverall: GenericOverall) {
                    profileUiState = profileUiState.copy(
                        avatarRefreshKey = profileUiState.avatarRefreshKey + 1,
                        avatarIsDeleted = false
                    )
                }

                override fun onError(e: Throwable) {
                    Snackbar.make(
                        window.decorView,
                        context.getString(R.string.default_error_msg),
                        Snackbar.LENGTH_LONG
                    ).show()
                    Log.e(TAG, "Error uploading avatar", e)
                }

                override fun onComplete() = Unit
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
                override fun onSubscribe(d: Disposable) = Unit

                override fun onNext(userProfileOverall: GenericOverall) {
                    Log.d(TAG, "Successfully saved: ${item.scope!!.id} as ${item.field.scopeName}")
                }

                override fun onError(e: Throwable) {
                    item.scope = userInfo?.getScopeByField(item.field)
                    Log.e(TAG, "Failed to save: ${item.scope!!.id} as ${item.field.scopeName}", e)
                }

                override fun onComplete() = Unit
            })
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSION_CAMERA) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pickImage.takePicture(startTakePictureIntentForResult = startTakePictureIntentForResult)
            } else {
                Snackbar.make(window.decorView, context.getString(R.string.take_photo_permission), Snackbar.LENGTH_LONG)
                    .show()
            }
        }
    }

    private fun handleResult(result: ActivityResult, onResult: (result: ActivityResult) -> Unit) {
        when (result.resultCode) {
            Activity.RESULT_OK -> onResult(result)
            ImagePicker.RESULT_ERROR -> {
                Snackbar.make(window.decorView, getError(result.data), Snackbar.LENGTH_SHORT).show()
            }

            else -> Log.i(TAG, "Task Cancelled")
        }
    }

    class UserInfoDetailsItem(
        @field:DrawableRes @param:DrawableRes
        var icon: Int,
        var text: String?,
        var hint: String,
        val field: Field,
        var scope: Scope?
    )

    private fun updateItemScope(position: Int, scope: Scope?) {
        val old = profileItems[position]
        profileItems[position] = UserInfoDetailsItem(old.icon, old.text, old.hint, old.field, scope)
        syncFilteredItems()
    }

    private fun syncFilteredItems() {
        profileUiState = profileUiState.copy(
            items = profileItems.toList(),
            filteredItems = profileItems.filter { !it.text.isNullOrEmpty() }
        )
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
        private const val DEFAULT_RETRIES: Long = 3
        private const val KEY_EDIT_MODE = "edit_mode"
    }
}
