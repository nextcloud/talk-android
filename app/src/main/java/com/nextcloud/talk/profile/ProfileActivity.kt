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
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
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
import com.nextcloud.android.common.ui.theme.utils.ColorRole
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.BaseActivity
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.ActivityProfileBinding
import com.nextcloud.talk.databinding.UserInfoDetailsTableItemBinding
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setupActionBar()
        setContentView(binding.root)
        initSystemBars()
    }

    override fun onResume() {
        super.onResume()

        adapter = UserInfoAdapter(null, viewThemeUtils, this)
        binding.userinfoList.adapter = adapter
        binding.userinfoList.setItemViewCacheSize(DEFAULT_CACHE_SIZE)
        currentUser = currentUserProvider.currentUser.blockingGet()
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

        colorIcons()
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
                if (CapabilitiesUtil.hasSpreedFeatureCapability(
                        currentUser!!.capabilities!!.spreedCapability!!,
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

                binding.avatarButtons.visibility = View.INVISIBLE
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
        adapter!!.setData(createUserInfoDetails(userInfo))
        if (
            isAllEmpty(
                arrayOf(
                    userInfo?.displayName,
                    userInfo?.phone,
                    userInfo?.email,
                    userInfo?.address,
                    userInfo?.twitter,
                    userInfo?.website
                )
            )
        ) {
            binding.userinfoList.visibility = View.GONE
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
            binding.userinfoList.visibility = View.VISIBLE
        }

        // show edit button
        if (CapabilitiesUtil.canEditScopes(currentUser!!)) {
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
                        invalidateOptionsMenu()
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
        }
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    private fun setErrorMessageForMultiList(headline: String, message: String, @DrawableRes errorResource: Int) {
        binding.emptyList.emptyListViewHeadline.text = headline
        binding.emptyList.emptyListViewText.text = message
        binding.emptyList.emptyListIcon.setImageResource(errorResource)
        binding.emptyList.emptyListIcon.visibility = View.VISIBLE
        binding.emptyList.emptyListViewText.visibility = View.VISIBLE
        binding.userinfoList.visibility = View.GONE
        binding.loadingContent.visibility = View.GONE
    }

    @Suppress("Detekt.LongMethod")
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
                    R.drawable.ic_phone,
                    userInfo.phone,
                    resources!!.getString(R.string.user_info_phone),
                    Field.PHONE,
                    userInfo.phoneScope
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
                    DisplayUtils.beautifyURL(userInfo.website),
                    resources!!.getString(R.string.user_info_website),
                    Field.WEBSITE,
                    userInfo.websiteScope
                )
            )
            result.add(
                UserInfoDetailsItem(
                    R.drawable.ic_twitter,
                    DisplayUtils.beautifyTwitterHandle(userInfo.twitter),
                    resources!!.getString(R.string.user_info_twitter),
                    Field.TWITTER,
                    userInfo.twitterScope
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
                            Log.e(TAG, "Failed to saved: " + item.text + " as " + item.field, e)
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
            item.scope!!.name
        )
            .retry(DEFAULT_RETRIES)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : Observer<GenericOverall> {
                override fun onSubscribe(d: Disposable) {
                    // unused atm
                }

                override fun onNext(userProfileOverall: GenericOverall) {
                    Log.d(TAG, "Successfully saved: " + item.scope + " as " + item.field)
                }

                override fun onError(e: Throwable) {
                    item.scope = userInfo?.getScopeByField(item.field)
                    Log.e(TAG, "Failed to saved: " + item.scope + " as " + item.field, e)
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

        class ViewHolder(val binding: UserInfoDetailsTableItemBinding) : RecyclerView.ViewHolder(binding.root)

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
            val itemBinding =
                UserInfoDetailsTableItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(itemBinding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item: UserInfoDetailsItem = if (profileActivity.edit) {
                displayList!![position]
            } else {
                filteredDisplayList[position]
            }

            initScopeElements(item, holder)

            holder.binding.icon.setImageResource(item.icon)
            initUserInfoEditText(holder, item)

            holder.binding.icon.contentDescription = item.hint
            viewThemeUtils.platform.colorImageView(holder.binding.icon, ColorRole.PRIMARY)
            if (!TextUtils.isEmpty(item.text) || profileActivity.edit) {
                holder.binding.userInfoDetailContainer.visibility = View.VISIBLE
                profileActivity.viewThemeUtils.material.colorTextInputLayout(holder.binding.userInfoInputLayout)
                if (profileActivity.edit &&
                    profileActivity.editableFields.contains(item.field.toString().lowercase())
                ) {
                    holder.binding.userInfoEditTextEdit.isEnabled = true
                    holder.binding.userInfoEditTextEdit.isFocusableInTouchMode = true
                    holder.binding.userInfoEditTextEdit.isEnabled = true
                    holder.binding.userInfoEditTextEdit.isCursorVisible = true
                    holder.binding.scope.setOnClickListener {
                        ScopeDialog(
                            holder.binding.scope.context,
                            this,
                            item.field,
                            holder.adapterPosition
                        ).show()
                    }
                    holder.binding.scope.alpha = HIGH_EMPHASIS_ALPHA
                } else {
                    holder.binding.userInfoEditTextEdit.isEnabled = false
                    holder.binding.userInfoEditTextEdit.isFocusableInTouchMode = false
                    holder.binding.userInfoEditTextEdit.isEnabled = false
                    holder.binding.userInfoEditTextEdit.isCursorVisible = false
                    holder.binding.scope.setOnClickListener(null)
                    holder.binding.scope.alpha = MEDIUM_EMPHASIS_ALPHA
                }
            } else {
                holder.binding.userInfoDetailContainer.visibility = View.GONE
            }
        }

        private fun initUserInfoEditText(holder: ViewHolder, item: UserInfoDetailsItem) {
            holder.binding.userInfoEditTextEdit.setText(item.text)
            holder.binding.userInfoInputLayout.hint = item.hint
            holder.binding.userInfoEditTextEdit.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                    // unused atm
                }

                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    if (profileActivity.edit) {
                        displayList!![holder.adapterPosition].text = holder.binding.userInfoEditTextEdit.text.toString()
                    } else {
                        filteredDisplayList[holder.adapterPosition].text =
                            holder.binding.userInfoEditTextEdit.text.toString()
                    }
                }

                override fun afterTextChanged(s: Editable) {
                    // unused atm
                }
            })
        }

        private fun initScopeElements(item: UserInfoDetailsItem, holder: ViewHolder) {
            if (item.scope == null) {
                holder.binding.scope.visibility = View.GONE
            } else {
                holder.binding.scope.visibility = View.VISIBLE
                when (item.scope) {
                    Scope.PRIVATE -> holder.binding.scope.setImageResource(R.drawable.ic_cellphone)
                    Scope.LOCAL -> holder.binding.scope.setImageResource(R.drawable.ic_password)
                    Scope.FEDERATED -> holder.binding.scope.setImageResource(R.drawable.ic_contacts)
                    Scope.PUBLISHED -> holder.binding.scope.setImageResource(R.drawable.ic_link)
                    null -> {
                        // nothing
                    }
                }
                holder.binding.scope.contentDescription = holder.binding.scope.context.getString(
                    R.string.scope_toggle_description,
                    item.hint
                )
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
        TWITTER("twitter", "twitterScope")
    }

    companion object {
        private val TAG = ProfileActivity::class.java.simpleName
        private const val DEFAULT_CACHE_SIZE: Int = 20
        private const val DEFAULT_RETRIES: Long = 3
        private const val HIGH_EMPHASIS_ALPHA: Float = 0.87f
        private const val MEDIUM_EMPHASIS_ALPHA: Float = 0.6f
    }
}
