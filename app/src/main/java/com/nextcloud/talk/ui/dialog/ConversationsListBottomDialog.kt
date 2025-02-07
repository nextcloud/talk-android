/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.ui.dialog

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import autodagger.AutoInjector
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.MainActivity
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.api.NcApiCoroutines
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.conversation.RenameConversationDialogFragment
import com.nextcloud.talk.conversationinfo.viewmodel.ConversationInfoViewModel
import com.nextcloud.talk.conversationlist.ConversationsListActivity
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.DialogConversationOperationsBinding
import com.nextcloud.talk.jobs.LeaveConversationWorker
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.CapabilitiesUtil
import com.nextcloud.talk.utils.ConversationUtils
import com.nextcloud.talk.utils.ShareUtils
import com.nextcloud.talk.utils.SpreedFeatures
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_INTERNAL_USER_ID
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class ConversationsListBottomDialog(
    val activity: ConversationsListActivity,
    val currentUser: User,
    val conversation: ConversationModel
) : BottomSheetDialog(activity) {

    private lateinit var binding: DialogConversationOperationsBinding

    @Inject
    lateinit var ncApi: NcApi

    @Inject
    lateinit var ncApiCoroutines: NcApiCoroutines

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    @Inject
    lateinit var conversationInfoViewModel: ConversationInfoViewModel

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var currentUserProvider: CurrentUserProviderNew

    lateinit var credentials: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication?.componentApplication?.inject(this)

        binding = DialogConversationOperationsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        viewThemeUtils.material.colorBottomSheetBackground(binding.root)
        viewThemeUtils.material.colorBottomSheetDragHandle(binding.bottomSheetDragHandle)
        initHeaderDescription()
        initItemsVisibility()
        initClickListeners()

        credentials = ApiUtils.getCredentials(currentUser.username, currentUser.token)!!
    }

    override fun onStart() {
        super.onStart()
        val bottomSheet = findViewById<View>(R.id.design_bottom_sheet)
        val behavior = BottomSheetBehavior.from(bottomSheet as View)
        behavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    private fun initHeaderDescription() {
        if (!TextUtils.isEmpty(conversation.displayName)) {
            binding.conversationOperationHeader.text = conversation.displayName
        } else if (!TextUtils.isEmpty(conversation.name)) {
            binding.conversationOperationHeader.text = conversation.name
        }
    }

    private fun initItemsVisibility() {
        val hasFavoritesCapability = CapabilitiesUtil.hasSpreedFeatureCapability(
            currentUser.capabilities?.spreedCapability!!,
            SpreedFeatures.FAVORITES
        )
        val canModerate = ConversationUtils.canModerate(conversation, currentUser.capabilities?.spreedCapability!!)

        binding.conversationRemoveFromFavorites.visibility = setVisibleIf(
            hasFavoritesCapability && conversation.favorite
        )
        binding.conversationAddToFavorites.visibility = setVisibleIf(
            hasFavoritesCapability && !conversation.favorite
        )

        binding.conversationMarkAsRead.visibility = setVisibleIf(
            conversation.unreadMessages > 0 && CapabilitiesUtil.hasSpreedFeatureCapability(
                currentUser.capabilities?.spreedCapability!!,
                SpreedFeatures.CHAT_READ_MARKER
            )
        )

        binding.conversationMarkAsUnread.visibility = setVisibleIf(
            conversation.unreadMessages <= 0 && CapabilitiesUtil.hasSpreedFeatureCapability(
                currentUser.capabilities?.spreedCapability!!,
                SpreedFeatures.CHAT_UNREAD
            )
        )

        binding.conversationOperationRename.visibility = setVisibleIf(
            ConversationUtils.isNameEditable(conversation, currentUser.capabilities!!.spreedCapability!!)
        )
        binding.conversationLinkShare.visibility = setVisibleIf(
            !ConversationUtils.isNoteToSelfConversation(conversation)
        )

        binding.conversationOperationDelete.visibility = setVisibleIf(
            canModerate
        )

        binding.conversationOperationLeave.visibility = setVisibleIf(
            conversation.canLeaveConversation &&
                // leaving is by api not possible for the last user with moderator permissions.
                // for now, hide this option for all moderators.
                !ConversationUtils.canModerate(conversation, currentUser.capabilities!!.spreedCapability!!)
        )
    }

    private fun setVisibleIf(boolean: Boolean): Int {
        return if (boolean) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun initClickListeners() {
        binding.conversationAddToFavorites.setOnClickListener {
            addConversationToFavorites()
        }

        binding.conversationRemoveFromFavorites.setOnClickListener {
            removeConversationFromFavorites()
        }

        binding.conversationMarkAsRead.setOnClickListener {
            markConversationAsRead()
        }

        binding.conversationMarkAsUnread.setOnClickListener {
            markConversationAsUnread()
        }

        binding.conversationLinkShare.setOnClickListener {
            val canGeneratePrettyURL = CapabilitiesUtil.canGeneratePrettyURL(currentUser)
            ShareUtils.shareConversationLink(
                activity,
                currentUser.baseUrl,
                conversation.token,
                conversation.name,
                canGeneratePrettyURL
            )
            dismiss()
        }

        binding.conversationArchiveText.text = if (conversation.hasArchived) {
            this.activity.resources.getString(R.string.unarchive_conversation)
        } else {
            this.activity.resources.getString(R.string.archive_conversation)
        }

        binding.conversationArchive.setOnClickListener {
            handleArchiving()
        }

        binding.conversationOperationRename.setOnClickListener {
            renameConversation()
        }

        binding.conversationOperationLeave.setOnClickListener {
            leaveConversation()
        }

        binding.conversationOperationDelete.setOnClickListener {
            deleteConversation()
        }
    }

    private fun handleArchiving() {
        val currentUser = currentUserProvider.currentUser.blockingGet()
        val token = conversation.token
        lifecycleScope.launch {
            if (conversation.hasArchived) {
                conversationInfoViewModel.unarchiveConversation(currentUser, token)
                activity.showSnackbar(
                    String.format(
                        context.resources.getString(R.string.unarchived_conversation),
                        conversation.displayName
                    )
                )
                dismiss()
            } else {
                conversationInfoViewModel.archiveConversation(currentUser, token)
                activity.showSnackbar(
                    String.format(
                        context.resources.getString(R.string.archived_conversation),
                        conversation.displayName
                    )
                )
                dismiss()
            }
        }
        activity.fetchRooms()
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    @SuppressLint("StringFormatInvalid", "TooGenericExceptionCaught")
    private fun addConversationToFavorites() {
        val apiVersion = ApiUtils.getConversationApiVersion(currentUser, intArrayOf(ApiUtils.API_V4, ApiUtils.API_V1))
        val url = ApiUtils.getUrlForRoomFavorite(apiVersion, currentUser.baseUrl!!, conversation.token)
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    ncApiCoroutines.addConversationToFavorites(credentials, url)
                }
                activity.fetchRooms()
                activity.showSnackbar(
                    String.format(
                        context.resources.getString(R.string.added_to_favorites),
                        conversation.displayName
                    )
                )
                dismiss()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    activity.showSnackbar(context.resources.getString(R.string.nc_common_error_sorry))
                    dismiss()
                }
            }
        }
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    @SuppressLint("StringFormatInvalid", "TooGenericExceptionCaught")
    private fun removeConversationFromFavorites() {
        val apiVersion = ApiUtils.getConversationApiVersion(currentUser, intArrayOf(ApiUtils.API_V4, ApiUtils.API_V1))
        val url = ApiUtils.getUrlForRoomFavorite(apiVersion, currentUser.baseUrl!!, conversation.token)
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    ncApiCoroutines.removeConversationFromFavorites(credentials, url)
                }
                activity.fetchRooms()
                activity.showSnackbar(
                    String.format(
                        context.resources.getString(R.string.removed_from_favorites),
                        conversation.displayName
                    )
                )
                dismiss()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    activity.showSnackbar(context.resources.getString(R.string.nc_common_error_sorry))
                    dismiss()
                }
            }
        }
    }

    private fun markConversationAsUnread() {
        ncApi.markRoomAsUnread(
            credentials,
            ApiUtils.getUrlForChatReadMarker(
                chatApiVersion(),
                currentUser.baseUrl!!,
                conversation.token!!
            )
        )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .retry(1)
            .subscribe(object : Observer<GenericOverall> {
                override fun onSubscribe(d: Disposable) {
                    // unused atm
                }

                @SuppressLint("StringFormatInvalid")
                override fun onNext(genericOverall: GenericOverall) {
                    activity.fetchRooms()
                    activity.showSnackbar(
                        String.format(
                            context.resources.getString(R.string.marked_as_unread),
                            conversation.displayName
                        )
                    )
                    dismiss()
                }

                override fun onError(e: Throwable) {
                    activity.showSnackbar(context.resources.getString(R.string.nc_common_error_sorry))
                    dismiss()
                }

                override fun onComplete() {
                    // unused atm
                }
            })
    }

    private fun markConversationAsRead() {
        val messageId = if (conversation.remoteServer.isNullOrEmpty()) {
            conversation.lastMessage?.id
        } else {
            null
        }

        ncApi.setChatReadMarker(
            credentials,
            ApiUtils.getUrlForChatReadMarker(
                chatApiVersion(),
                currentUser.baseUrl!!,
                conversation.token!!
            ),
            messageId?.toInt()
        )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .retry(1)
            .subscribe(object : Observer<GenericOverall> {
                override fun onSubscribe(d: Disposable) {
                    // unused atm
                }

                @SuppressLint("StringFormatInvalid")
                override fun onNext(genericOverall: GenericOverall) {
                    activity.fetchRooms()
                    activity.showSnackbar(
                        String.format(
                            context.resources.getString(R.string.marked_as_read),
                            conversation.displayName
                        )
                    )
                    dismiss()
                }

                override fun onError(e: Throwable) {
                    activity.showSnackbar(context.resources.getString(R.string.nc_common_error_sorry))
                    dismiss()
                }

                override fun onComplete() {
                    // unused atm
                }
            })
    }

    private fun renameConversation() {
        if (!TextUtils.isEmpty(conversation.token)) {
            dismiss()
            val conversationDialog = RenameConversationDialogFragment.newInstance(
                conversation.token!!,
                conversation.displayName!!
            )
            conversationDialog.show(
                activity.supportFragmentManager,
                TAG
            )
        }
    }

    @SuppressLint("StringFormatInvalid")
    private fun leaveConversation() {
        val dataBuilder = Data.Builder()
        dataBuilder.putString(KEY_ROOM_TOKEN, conversation.token)
        dataBuilder.putLong(KEY_INTERNAL_USER_ID, currentUser.id!!)
        val data = dataBuilder.build()

        val leaveConversationWorker =
            OneTimeWorkRequest.Builder(LeaveConversationWorker::class.java).setInputData(
                data
            ).build()
        WorkManager.getInstance().enqueue(leaveConversationWorker)

        WorkManager.getInstance(context).getWorkInfoByIdLiveData(leaveConversationWorker.id)
            .observeForever { workInfo: WorkInfo? ->
                if (workInfo != null) {
                    when (workInfo.state) {
                        WorkInfo.State.SUCCEEDED -> {
                            activity.showSnackbar(
                                String.format(
                                    context.resources.getString(R.string.left_conversation),
                                    conversation.displayName
                                )
                            )
                            val intent = Intent(context, MainActivity::class.java)
                            context.startActivity(intent)
                        }

                        WorkInfo.State.FAILED -> {
                            activity.showSnackbar(context.resources.getString(R.string.nc_common_error_sorry))
                        }

                        else -> {
                        }
                    }
                }
            }

        dismiss()
    }

    private fun deleteConversation() {
        if (!TextUtils.isEmpty(conversation.token)) {
            activity.showDeleteConversationDialog(conversation)
        }

        dismiss()
    }

    private fun chatApiVersion(): Int {
        return ApiUtils.getChatApiVersion(currentUser.capabilities!!.spreedCapability!!, intArrayOf(ApiUtils.API_V1))
    }

    companion object {
        val TAG = ConversationsListBottomDialog::class.simpleName
    }
}
