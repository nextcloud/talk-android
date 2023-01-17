/*
 * Nextcloud Talk application
 *
 * @author Tobias Kaminsky
 * @author Andy Scherzinger
 * Copyright (C) 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * Copyright (C) 2021 Tobias Kaminsky <tobias.kaminsky@nextcloud.com>
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
package com.nextcloud.talk.controllers

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.core.net.toFile
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import autodagger.AutoInjector
import com.github.dhaval2404.imagepicker.ImagePicker
import com.github.dhaval2404.imagepicker.ImagePicker.Companion.getError
import com.github.dhaval2404.imagepicker.ImagePicker.Companion.with
import com.github.dhaval2404.imagepicker.constant.ImageProvider
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.TakePhotoActivity
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.controllers.base.BaseController
import com.nextcloud.talk.controllers.util.viewBinding
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.ControllerProfileBinding
import com.nextcloud.talk.databinding.UserInfoDetailsTableItemBinding
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.models.json.userprofile.Scope
import com.nextcloud.talk.models.json.userprofile.UserProfileData
import com.nextcloud.talk.models.json.userprofile.UserProfileFieldsOverall
import com.nextcloud.talk.models.json.userprofile.UserProfileOverall
import com.nextcloud.talk.remotefilebrowser.activities.RemoteFileBrowserActivity
import com.nextcloud.talk.ui.dialog.ScopeDialog
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.FileUtils
import com.nextcloud.talk.utils.Mimetype.IMAGE_JPG
import com.nextcloud.talk.utils.Mimetype.IMAGE_PREFIX
import com.nextcloud.talk.utils.Mimetype.IMAGE_PREFIX_GENERIC
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_MIME_TYPE_FILTER
import com.nextcloud.talk.utils.database.user.CapabilitiesUtilNew
import com.nextcloud.talk.utils.permissions.PlatformPermissionUtil
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.LinkedList
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
@Suppress("Detekt.TooManyFunctions")
class ProfileController : BaseController(R.layout.controller_profile) {
    private val binding: ControllerProfileBinding? by viewBinding(ControllerProfileBinding::bind)

    @Inject
    lateinit var ncApi: NcApi

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var permissionUtil: PlatformPermissionUtil

    private var currentUser: User? = null
    private var edit = false
    private var adapter: UserInfoAdapter? = null
    private var userInfo: UserProfileData? = null
    private var editableFields = ArrayList<String>()

    override val title: String
        get() =
            resources!!.getString(R.string.nc_profile_personal_info_title)

    override fun onViewBound(view: View) {
        super.onViewBound(view)
        sharedApplication!!.componentApplication.inject(this)
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_profile, menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        menu.findItem(R.id.edit).isVisible = editableFields.size > 0
        if (edit) {
            menu.findItem(R.id.edit).setTitle(R.string.save)
        } else {
            menu.findItem(R.id.edit).setTitle(R.string.edit)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.edit) {
            if (edit) {
                save()
            }
            edit = !edit
            if (edit) {
                item.setTitle(R.string.save)
                binding?.emptyList?.root?.visibility = View.GONE
                binding?.userinfoList?.visibility = View.VISIBLE
                if (CapabilitiesUtilNew.isAvatarEndpointAvailable(currentUser!!)) {
                    // TODO later avatar can also be checked via user fields, for now it is in Talk capability
                    binding?.avatarButtons?.visibility = View.VISIBLE
                }
                ncApi.getEditableUserProfileFields(
                    ApiUtils.getCredentials(currentUser!!.username, currentUser!!.token),
                    ApiUtils.getUrlForUserFields(currentUser!!.baseUrl)
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
                binding?.avatarButtons?.visibility = View.INVISIBLE
                if (adapter!!.filteredDisplayList.isEmpty()) {
                    binding?.emptyList?.root?.visibility = View.VISIBLE
                    binding?.userinfoList?.visibility = View.GONE
                }
            }
            adapter!!.notifyDataSetChanged()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        adapter = UserInfoAdapter(null, viewThemeUtils, this)
        binding?.userinfoList?.adapter = adapter
        binding?.userinfoList?.setItemViewCacheSize(DEFAULT_CACHE_SIZE)
        currentUser = userManager.currentUser.blockingGet()
        val credentials = ApiUtils.getCredentials(currentUser!!.username, currentUser!!.token)
        binding?.avatarUpload?.setOnClickListener { sendSelectLocalFileIntent() }
        binding?.avatarChoose?.setOnClickListener { showBrowserScreen() }
        binding?.avatarCamera?.setOnClickListener { checkPermissionAndTakePicture() }
        binding?.avatarDelete?.setOnClickListener {
            ncApi.deleteAvatar(
                credentials,
                ApiUtils.getUrlForTempAvatar(currentUser!!.baseUrl)
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
                            binding?.avatarImage,
                            true
                        )
                    }

                    override fun onError(e: Throwable) {
                        Toast.makeText(applicationContext, "Error", Toast.LENGTH_LONG).show()
                    }

                    override fun onComplete() {
                        // unused atm
                    }
                })
        }
        binding?.avatarImage?.let { ViewCompat.setTransitionName(it, "userAvatar.transitionTag") }
        ncApi.getUserProfile(credentials, ApiUtils.getUrlForUserProfile(currentUser!!.baseUrl))
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
                        activity!!.getString(R.string.userinfo_no_info_headline),
                        activity!!.getString(R.string.userinfo_error_text),
                        R.drawable.ic_list_empty_error
                    )
                }

                override fun onComplete() {
                    // unused atm
                }
            })

        colorIcons()
    }

    private fun colorIcons() {
        binding?.let {
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
        if (activity == null) {
            return
        }
        if (currentUser!!.baseUrl != null) {
            binding?.userinfoBaseurl?.text = Uri.parse(currentUser!!.baseUrl).host
        }
        DisplayUtils.loadAvatarImage(currentUser, binding?.avatarImage, false)
        if (!TextUtils.isEmpty(userInfo?.displayName)) {
            binding?.userinfoFullName?.text = userInfo?.displayName
        }
        binding?.loadingContent?.visibility = View.VISIBLE
        adapter!!.setData(createUserInfoDetails(userInfo))
        if (isAllEmpty(
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
            binding?.userinfoList?.visibility = View.GONE
            binding?.loadingContent?.visibility = View.GONE
            binding?.emptyList?.root?.visibility = View.VISIBLE
            setErrorMessageForMultiList(
                activity!!.getString(R.string.userinfo_no_info_headline),
                activity!!.getString(R.string.userinfo_no_info_text),
                R.drawable.ic_user
            )
        } else {
            binding?.emptyList?.root?.visibility = View.GONE
            binding?.loadingContent?.visibility = View.GONE
            binding?.userinfoList?.visibility = View.VISIBLE
        }

        // show edit button
        if (CapabilitiesUtilNew.canEditScopes(currentUser!!)) {
            ncApi.getEditableUserProfileFields(
                ApiUtils.getCredentials(currentUser!!.username, currentUser!!.token),
                ApiUtils.getUrlForUserFields(currentUser!!.baseUrl)
            )
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(object : Observer<UserProfileFieldsOverall> {
                    override fun onSubscribe(d: Disposable) {
                        // unused atm
                    }

                    override fun onNext(userProfileFieldsOverall: UserProfileFieldsOverall) {
                        editableFields = userProfileFieldsOverall.ocs!!.data!!
                        activity!!.invalidateOptionsMenu()
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
        if (activity == null) {
            return
        }

        binding?.emptyList?.emptyListViewHeadline?.text = headline
        binding?.emptyList?.emptyListViewText?.text = message
        binding?.emptyList?.emptyListIcon?.setImageResource(errorResource)
        binding?.emptyList?.emptyListIcon?.visibility = View.VISIBLE
        binding?.emptyList?.emptyListViewText?.visibility = View.VISIBLE
        binding?.userinfoList?.visibility = View.GONE
        binding?.loadingContent?.visibility = View.GONE
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
            if (item.text != userInfo!!.getValueByField(item.field)) {
                val credentials = ApiUtils.getCredentials(currentUser!!.username, currentUser!!.token)
                ncApi.setUserData(
                    credentials,
                    ApiUtils.getUrlForUserData(currentUser!!.baseUrl, currentUser!!.userId),
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
                            item.text = userInfo!!.getValueByField(item.field)!!
                            Toast.makeText(
                                applicationContext,
                                String.format(
                                    resources!!.getString(R.string.failed_to_save),
                                    item.field
                                ),
                                Toast.LENGTH_LONG
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
            if (item.scope != userInfo!!.getScopeByField(item.field)) {
                saveScope(item, userInfo)
            }
            adapter!!.updateFilteredList()
        }
    }

    private fun sendSelectLocalFileIntent() {
        with(activity!!)
            .provider(ImageProvider.GALLERY)
            .crop()
            .cropSquare()
            .compress(MAX_SIZE)
            .maxResultSize(MAX_SIZE, MAX_SIZE)
            .createIntent { intent -> startActivityForResult(intent, REQUEST_CODE_IMAGE_PICKER) }
    }

    private fun showBrowserScreen() {
        val bundle = Bundle()
        bundle.putString(KEY_MIME_TYPE_FILTER, IMAGE_PREFIX)

        val avatarIntent = Intent(activity, RemoteFileBrowserActivity::class.java)
        avatarIntent.putExtras(bundle)

        startActivityForResult(avatarIntent, REQUEST_CODE_SELECT_REMOTE_FILES)
    }

    private fun checkPermissionAndTakePicture() {
        if (permissionUtil.isCameraPermissionGranted()) {
            takePictureForAvatar()
        } else {
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), REQUEST_PERMISSION_CAMERA)
        }
    }

    private fun takePictureForAvatar() {
        startActivityForResult(TakePhotoActivity.createIntent(context), REQUEST_CODE_TAKE_PICTURE)
    }

    private fun handleAvatar(remotePath: String?) {
        val uri = currentUser!!.baseUrl + "/index.php/apps/files/api/v1/thumbnail/512/512/" +
            Uri.encode(remotePath, "/")
        val downloadCall = ncApi.downloadResizedImage(
            ApiUtils.getCredentials(currentUser!!.username, currentUser!!.token),
            uri
        )
        downloadCall.enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                saveBitmapAndPassToImagePicker(BitmapFactory.decodeStream(response.body()!!.byteStream()))
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                // unused atm
            }
        })
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSION_CAMERA) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                takePictureForAvatar()
            } else {
                Toast
                    .makeText(context, context.getString(R.string.take_photo_permission), Toast.LENGTH_LONG)
                    .show()
            }
        }
    }

    // only possible with API26
    private fun saveBitmapAndPassToImagePicker(bitmap: Bitmap) {
        val file: File = saveBitmapToTempFile(bitmap) ?: return
        openImageWithPicker(file)
    }

    private fun saveBitmapToTempFile(bitmap: Bitmap): File? {
        try {
            val file = createTempFileForAvatar()
            try {
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, FULL_QUALITY, out)
                }
                return file
            } catch (e: IOException) {
                Log.e(TAG, "Error compressing bitmap", e)
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error creating temporary avatar image", e)
        }
        return null
    }

    private fun createTempFileForAvatar(): File {
        FileUtils.removeTempCacheFile(
            this.context,
            AVATAR_PATH
        )
        return FileUtils.getTempCacheFile(
            context,
            AVATAR_PATH
        )
    }

    private fun openImageWithPicker(file: File) {
        with(activity!!)
            .provider(ImageProvider.URI)
            .crop()
            .cropSquare()
            .compress(MAX_SIZE)
            .maxResultSize(MAX_SIZE, MAX_SIZE)
            .setUri(Uri.fromFile(file))
            .createIntent { intent -> startActivityForResult(intent, REQUEST_CODE_IMAGE_PICKER) }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == REQUEST_CODE_IMAGE_PICKER) {
                val uri: Uri = data?.data!!
                uploadAvatar(uri.toFile())
            } else if (requestCode == REQUEST_CODE_SELECT_REMOTE_FILES) {
                val pathList = data?.getStringArrayListExtra(RemoteFileBrowserActivity.EXTRA_SELECTED_PATHS)
                if (pathList?.size!! >= 1) {
                    handleAvatar(pathList[0])
                }
            } else if (requestCode == REQUEST_CODE_TAKE_PICTURE) {
                data?.data?.path?.let {
                    openImageWithPicker(File(it))
                }
            } else {
                Log.w(TAG, "Unknown intent request code")
            }
        } else if (resultCode == ImagePicker.RESULT_ERROR) {
            Toast.makeText(activity, getError(data), Toast.LENGTH_SHORT).show()
        } else {
            Log.i(TAG, "Task Cancelled")
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
            ApiUtils.getUrlForTempAvatar(currentUser!!.baseUrl),
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
                    Toast.makeText(
                        applicationContext,
                        context.getString(R.string.default_error_msg),
                        Toast
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
            ApiUtils.getUrlForUserData(currentUser!!.baseUrl, currentUser!!.userId),
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
                    item.scope = userInfo!!.getScopeByField(item.field)
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
        private val controller: ProfileController
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
            val item: UserInfoDetailsItem = if (controller.edit) {
                displayList!![position]
            } else {
                filteredDisplayList[position]
            }

            initScopeElements(item, holder)

            holder.binding.icon.setImageResource(item.icon)
            initUserInfoEditText(holder, item)

            holder.binding.icon.contentDescription = item.hint
            viewThemeUtils.platform.colorImageView(holder.binding.icon)
            if (!TextUtils.isEmpty(item.text) || controller.edit) {
                holder.binding.userInfoDetailContainer.visibility = View.VISIBLE
                controller.viewThemeUtils.material.colorTextInputLayout(holder.binding.userInfoInputLayout)
                if (controller.edit &&
                    controller.editableFields.contains(item.field.toString().lowercase())
                ) {
                    holder.binding.userInfoEditTextEdit.isEnabled = true
                    holder.binding.userInfoEditTextEdit.isFocusableInTouchMode = true
                    holder.binding.userInfoEditTextEdit.isEnabled = true
                    holder.binding.userInfoEditTextEdit.isCursorVisible = true
                    holder.binding.scope.setOnClickListener {
                        ScopeDialog(
                            controller.activity!!,
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

        private fun initUserInfoEditText(
            holder: ViewHolder,
            item: UserInfoDetailsItem
        ) {
            holder.binding.userInfoEditTextEdit.setText(item.text)
            holder.binding.userInfoInputLayout.hint = item.hint
            holder.binding.userInfoEditTextEdit.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                    // unused atm
                }

                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    if (controller.edit) {
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

        private fun initScopeElements(
            item: UserInfoDetailsItem,
            holder: ViewHolder
        ) {
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
                holder.binding.scope.contentDescription = controller.activity!!.resources.getString(
                    R.string.scope_toggle_description,
                    item.hint
                )
            }
        }

        override fun getItemCount(): Int {
            return if (controller.edit) {
                displayList!!.size
            } else {
                filteredDisplayList.size
            }
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
        TWITTER("twitter", "twitterScope");
    }

    companion object {
        private const val TAG: String = "ProfileController"
        private const val AVATAR_PATH = "photos/avatar.png"
        private const val REQUEST_CODE_SELECT_REMOTE_FILES = 22
        private const val DEFAULT_CACHE_SIZE: Int = 20
        private const val DEFAULT_RETRIES: Long = 3
        private const val MAX_SIZE: Int = 1024
        private const val REQUEST_CODE_IMAGE_PICKER: Int = 1
        private const val REQUEST_CODE_TAKE_PICTURE: Int = 2
        private const val REQUEST_PERMISSION_CAMERA: Int = 1
        private const val FULL_QUALITY: Int = 100
        private const val HIGH_EMPHASIS_ALPHA: Float = 0.87f
        private const val MEDIUM_EMPHASIS_ALPHA: Float = 0.6f
    }
}
