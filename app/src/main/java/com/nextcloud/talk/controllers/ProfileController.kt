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
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
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
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import autodagger.AutoInjector
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.VerticalChangeHandler
import com.github.dhaval2404.imagepicker.ImagePicker
import com.github.dhaval2404.imagepicker.ImagePicker.Companion.getError
import com.github.dhaval2404.imagepicker.ImagePicker.Companion.getFile
import com.github.dhaval2404.imagepicker.ImagePicker.Companion.with
import com.nextcloud.talk.R
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.components.filebrowser.controllers.BrowserController.BrowserType
import com.nextcloud.talk.components.filebrowser.controllers.BrowserForAvatarController
import com.nextcloud.talk.controllers.base.NewBaseController
import com.nextcloud.talk.controllers.util.viewBinding
import com.nextcloud.talk.databinding.ControllerProfileBinding
import com.nextcloud.talk.databinding.UserInfoDetailsTableItemBinding
import com.nextcloud.talk.models.database.CapabilitiesUtil
import com.nextcloud.talk.models.database.UserEntity
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.models.json.userprofile.Scope
import com.nextcloud.talk.models.json.userprofile.UserProfileData
import com.nextcloud.talk.models.json.userprofile.UserProfileFieldsOverall
import com.nextcloud.talk.models.json.userprofile.UserProfileOverall
import com.nextcloud.talk.ui.dialog.ScopeDialog
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_BROWSER_TYPE
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_USER_ENTITY
import com.nextcloud.talk.utils.database.user.UserUtils
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import org.parceler.Parcels
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.ArrayList
import java.util.LinkedList
import java.util.Locale
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class ProfileController : NewBaseController(R.layout.controller_profile) {
    private val binding: ControllerProfileBinding by viewBinding(ControllerProfileBinding::bind)

    @Inject
    lateinit var ncApi: NcApi

    @Inject
    lateinit var userUtils: UserUtils

    private var currentUser: UserEntity? = null
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
                binding.emptyList.root.visibility = View.GONE
                binding.userinfoList.visibility = View.VISIBLE
                if (CapabilitiesUtil.isAvatarEndpointAvailable(currentUser)) {
                    // TODO later avatar can also be checked via user fields, for now it is in Talk capability
                    binding.avatarButtons.visibility = View.VISIBLE
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

    override fun onAttach(view: View) {
        super.onAttach(view)
        adapter = UserInfoAdapter(null, activity!!.resources.getColor(R.color.colorPrimary), this)
        binding.userinfoList.adapter = adapter
        binding.userinfoList.setItemViewCacheSize(DEFAULT_CACHE_SIZE)
        currentUser = userUtils.currentUser
        val credentials = ApiUtils.getCredentials(currentUser!!.username, currentUser!!.token)
        binding.avatarUpload.setOnClickListener { sendSelectLocalFileIntent() }
        binding.avatarChoose.setOnClickListener { showBrowserScreen(BrowserType.DAV_BROWSER) }
        binding.avatarDelete.setOnClickListener {
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
                            binding.avatarImage,
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
        ViewCompat.setTransitionName(binding.avatarImage, "userAvatar.transitionTag")
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
    }

    private fun isAllEmpty(items: Array<String>): Boolean {
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
            binding.userinfoBaseurl.text = Uri.parse(currentUser!!.baseUrl).host
        }
        DisplayUtils.loadAvatarImage(currentUser, binding.avatarImage, false)
        if (!TextUtils.isEmpty(userInfo!!.displayName)) {
            binding.userinfoFullName.text = userInfo!!.displayName
        }
        binding.loadingContent.visibility = View.VISIBLE
        adapter!!.setData(createUserInfoDetails(userInfo))
        if (isAllEmpty(
                arrayOf(
                        userInfo!!.displayName!!,
                        userInfo!!.phone!!,
                        userInfo!!.email.orEmpty(),
                        userInfo!!.address!!,
                        userInfo!!.twitter!!,
                        userInfo!!.website!!
                    )
            )
        ) {
            binding.userinfoList.visibility = View.GONE
            binding.loadingContent.visibility = View.GONE
            binding.emptyList.root.visibility = View.VISIBLE
            setErrorMessageForMultiList(
                activity!!.getString(R.string.userinfo_no_info_headline),
                activity!!.getString(R.string.userinfo_no_info_text), R.drawable.ic_user
            )
        } else {
            binding.emptyList.root.visibility = View.GONE
            binding.loadingContent.visibility = View.GONE
            binding.userinfoList.visibility = View.VISIBLE
        }

        // show edit button
        if (CapabilitiesUtil.canEditScopes(currentUser)) {
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

    private fun setErrorMessageForMultiList(headline: String, message: String, @DrawableRes errorResource: Int) {
        if (activity == null) {
            return
        }
        binding.emptyList.emptyListViewHeadline.text = headline
        binding.emptyList.emptyListViewText.text = message
        binding.emptyList.emptyListIcon.setImageResource(errorResource)
        binding.emptyList.emptyListIcon.visibility = View.VISIBLE
        binding.emptyList.emptyListViewText.visibility = View.VISIBLE
        binding.userinfoList.visibility = View.GONE
        binding.loadingContent.visibility = View.GONE
    }

    private fun createUserInfoDetails(userInfo: UserProfileData?): List<UserInfoDetailsItem> {
        val result: MutableList<UserInfoDetailsItem> = LinkedList()
        result.add(
            UserInfoDetailsItem(
                R.drawable.ic_user,
                userInfo!!.displayName!!,
                resources!!.getString(R.string.user_info_displayname),
                Field.DISPLAYNAME,
                userInfo.displayNameScope
            )
        )
        result.add(
            UserInfoDetailsItem(
                R.drawable.ic_phone,
                userInfo.phone!!,
                resources!!.getString(R.string.user_info_phone),
                Field.PHONE,
                userInfo.phoneScope
            )
        )
        result.add(
            UserInfoDetailsItem(
                R.drawable.ic_email,
                userInfo.email.orEmpty(),
                resources!!.getString(R.string.user_info_email),
                Field.EMAIL,
                userInfo.emailScope
            )
        )
        result.add(
            UserInfoDetailsItem(
                R.drawable.ic_map_marker,
                userInfo.address!!,
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
                                binding.userinfoFullName.text = item.text
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
        val intent = with(activity!!)
            .galleryOnly()
            .crop()
            .cropSquare()
            .compress(MAX_SIZE)
            .maxResultSize(MAX_SIZE, MAX_SIZE)
            .prepareIntent()
        startActivityForResult(intent, 1)
    }

    private fun showBrowserScreen(browserType: BrowserType) {
        val bundle = Bundle()
        bundle.putParcelable(
            KEY_BROWSER_TYPE,
            Parcels.wrap(BrowserType::class.java, browserType)
        )
        bundle.putParcelable(
            KEY_USER_ENTITY,
            Parcels.wrap(UserEntity::class.java, currentUser)
        )
        bundle.putString(KEY_ROOM_TOKEN, "123")
        router.pushController(
            RouterTransaction.with(BrowserForAvatarController(bundle, this))
                .pushChangeHandler(VerticalChangeHandler())
                .popChangeHandler(VerticalChangeHandler())
        )
    }

    fun handleAvatar(remotePath: String?) {
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

    // only possible with API26
    private fun saveBitmapAndPassToImagePicker(bitmap: Bitmap) {
        var file: File? = null
        try {
            file = File.createTempFile(
                "avatar", "png",
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
            )
            try {
                FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, FULL_QUALITY, out) }
            } catch (e: IOException) {
                Log.e(TAG, "Error compressing bitmap", e)
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error creating temporary avatar image", e)
        }
        if (file == null) {
            // TODO exception
            return
        }
        val intent = with(activity!!)
            .fileOnly()
            .crop()
            .cropSquare()
            .compress(MAX_SIZE)
            .maxResultSize(MAX_SIZE, MAX_SIZE)
            .prepareIntent()
        intent.putExtra("extra.file", file)
        startActivityForResult(intent, REQUEST_CODE_IMAGE_PICKER)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            uploadAvatar(getFile(data))
        } else if (resultCode == ImagePicker.RESULT_ERROR) {
            Toast.makeText(activity, getError(data), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(activity, "Task Cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    private fun uploadAvatar(file: File?) {
        val builder = MultipartBody.Builder()
        builder.setType(MultipartBody.FORM)
        builder.addFormDataPart("files[]", file!!.name, RequestBody.create("image/*".toMediaTypeOrNull(), file))
        val filePart: MultipartBody.Part = MultipartBody.Part.createFormData(
            "files[]", file.name,
            RequestBody.create("image/jpg".toMediaTypeOrNull(), file)
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
                    DisplayUtils.loadAvatarImage(currentUser, binding.avatarImage, true)
                }

                override fun onError(e: Throwable) {
                    Toast.makeText(applicationContext, "Error", Toast.LENGTH_LONG).show()
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
        @field:DrawableRes @param:DrawableRes var icon: Int,
        var text: String,
        var hint: String,
        val field: Field,
        var scope: Scope?
    )

    class UserInfoAdapter(
        displayList: List<UserInfoDetailsItem>?,
        @ColorInt tintColor: Int,
        controller: ProfileController
    ) : RecyclerView.Adapter<UserInfoAdapter.ViewHolder>() {
        var displayList: List<UserInfoDetailsItem>?
        var filteredDisplayList: MutableList<UserInfoDetailsItem> = LinkedList()

        @ColorInt
        protected var mTintColor: Int
        private val controller: ProfileController

        class ViewHolder(val binding: UserInfoDetailsTableItemBinding) : RecyclerView.ViewHolder(binding.root)

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
            DrawableCompat.setTint(holder.binding.icon.drawable, mTintColor)
            if (!TextUtils.isEmpty(item.text) || controller.edit) {
                holder.binding.userInfoDetailContainer.visibility = View.VISIBLE
                if (controller.activity != null) {
                    holder.binding.userInfoEditText.setTextColor(
                        ContextCompat.getColor(
                            controller.activity!!,
                            R.color.conversation_item_header
                        )
                    )
                }
                if (controller.edit &&
                    controller.editableFields.contains(item.field.toString().toLowerCase(Locale.ROOT))
                ) {
                    holder.binding.userInfoEditText.isEnabled = true
                    holder.binding.userInfoEditText.isFocusableInTouchMode = true
                    holder.binding.userInfoEditText.isEnabled = true
                    holder.binding.userInfoEditText.isCursorVisible = true
                    holder.binding.userInfoEditText.backgroundTintList = ColorStateList.valueOf(mTintColor)
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
                    holder.binding.userInfoEditText.isEnabled = false
                    holder.binding.userInfoEditText.isFocusableInTouchMode = false
                    holder.binding.userInfoEditText.isEnabled = false
                    holder.binding.userInfoEditText.isCursorVisible = false
                    holder.binding.userInfoEditText.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
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
            holder.binding.userInfoEditText.setText(item.text)
            holder.binding.userInfoEditText.hint = item.hint
            holder.binding.userInfoEditText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                    // unused atm
                }

                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    if (controller.edit) {
                        displayList!![holder.adapterPosition].text = holder.binding.userInfoEditText.text.toString()
                    } else {
                        filteredDisplayList[holder.adapterPosition].text =
                            holder.binding.userInfoEditText.text.toString()
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

        init {
            this.displayList = displayList ?: LinkedList()
            mTintColor = tintColor
            this.controller = controller
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
        private const val DEFAULT_CACHE_SIZE: Int = 20
        private const val DEFAULT_RETRIES: Long = 3
        private const val MAX_SIZE: Int = 1024
        private const val REQUEST_CODE_IMAGE_PICKER: Int = 1
        private const val FULL_QUALITY: Int = 100
        private const val HIGH_EMPHASIS_ALPHA: Float = 0.87f
        private const val MEDIUM_EMPHASIS_ALPHA: Float = 0.6f
    }
}
