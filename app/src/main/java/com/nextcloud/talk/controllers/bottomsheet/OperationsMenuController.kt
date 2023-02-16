/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Andy Scherzinger
 * Copyright (C) 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * Copyright (C) 2017 Mario Danic <mario@lovelyhq.com>
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
package com.nextcloud.talk.controllers.bottomsheet

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.View
import autodagger.AutoInjector
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler
import com.nextcloud.talk.R
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.controllers.base.BaseController
import com.nextcloud.talk.controllers.util.viewBinding
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.ControllerOperationsMenuBinding
import com.nextcloud.talk.events.ConversationsListFetchDataEvent
import com.nextcloud.talk.events.OpenConversationEvent
import com.nextcloud.talk.models.RetrofitBucket
import com.nextcloud.talk.models.json.capabilities.Capabilities
import com.nextcloud.talk.models.json.capabilities.CapabilitiesOverall
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.conversations.Conversation.ConversationType
import com.nextcloud.talk.models.json.conversations.RoomOverall
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.models.json.participants.AddParticipantOverall
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.NoSupportedApiException
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ACTIVE_CONVERSATION
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_CALL_URL
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_CONVERSATION_NAME
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_CONVERSATION_PASSWORD
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_CONVERSATION_TYPE
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_INVITED_GROUP
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_INVITED_PARTICIPANTS
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_OPERATION_CODE
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_ID
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_SERVER_CAPABILITIES
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_USER_ENTITY
import com.nextcloud.talk.utils.database.user.CapabilitiesUtilNew
import com.nextcloud.talk.utils.singletons.ApplicationWideMessageHolder
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.parceler.Parcels
import retrofit2.HttpException
import java.io.IOException
import java.util.Collections
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class OperationsMenuController(args: Bundle) : BaseController(
    R.layout.controller_operations_menu,
    args
) {
    private val binding: ControllerOperationsMenuBinding? by viewBinding(ControllerOperationsMenuBinding::bind)

    @Inject
    lateinit var ncApi: NcApi

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var eventBus: EventBus

    private val operation: ConversationOperationEnum?
    private var conversation: Conversation? = null
    private var currentUser: User? = null
    private val callPassword: String
    private val callUrl: String
    private var baseUrl: String? = null
    private var conversationToken: String? = null
    private var disposable: Disposable? = null
    private var conversationType: ConversationType? = null
    private var invitedUsers: ArrayList<String>? = ArrayList()
    private var invitedGroups: ArrayList<String>? = ArrayList()
    private var serverCapabilities: Capabilities? = null
    private var credentials: String? = null
    private val conversationName: String

    override val appBarLayoutType: AppBarLayoutType
        get() = AppBarLayoutType.SEARCH_BAR

    override fun onViewBound(view: View) {
        super.onViewBound(view)
        sharedApplication!!.componentApplication.inject(this)
        currentUser = userManager.currentUser.blockingGet()

        binding?.progressBar?.let { viewThemeUtils.platform.colorCircularProgressBar(it) }

        if (!TextUtils.isEmpty(callUrl) && callUrl.contains("/call")) {
            conversationToken = callUrl.substring(callUrl.lastIndexOf("/") + 1)
            if (callUrl.contains("/index.php")) {
                baseUrl = callUrl.substring(0, callUrl.indexOf("/index.php"))
            } else {
                baseUrl = callUrl.substring(0, callUrl.indexOf("/call"))
            }
        }
        if (!TextUtils.isEmpty(baseUrl) && baseUrl != currentUser!!.baseUrl) {
            if (serverCapabilities != null) {
                try {
                    useBundledCapabilitiesForGuest()
                } catch (e: IOException) {
                    // Fall back to fetching capabilities again
                    fetchCapabilitiesForGuest()
                }
            } else {
                fetchCapabilitiesForGuest()
            }
        } else {
            processOperation()
        }
    }

    @Throws(IOException::class)
    private fun useBundledCapabilitiesForGuest() {
        currentUser = User()
        currentUser!!.baseUrl = baseUrl
        currentUser!!.userId = "?"
        try {
            currentUser!!.capabilities = serverCapabilities
        } catch (e: IOException) {
            Log.e("OperationsMenu", "Failed to serialize capabilities")
            throw e
        }
        try {
            checkCapabilities(currentUser!!)
            processOperation()
        } catch (e: NoSupportedApiException) {
            showResultImage(everythingOK = false, isGuestSupportError = false)
            Log.d(TAG, "No supported server version found", e)
        }
    }

    private fun fetchCapabilitiesForGuest() {
        ncApi.getCapabilities(null, ApiUtils.getUrlForCapabilities(baseUrl))
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : Observer<CapabilitiesOverall> {
                override fun onSubscribe(d: Disposable) {
                    // unused atm
                }

                override fun onNext(capabilitiesOverall: CapabilitiesOverall) {
                    currentUser = User()
                    currentUser!!.baseUrl = baseUrl
                    currentUser!!.userId = "?"
                    try {
                        currentUser!!.capabilities = capabilitiesOverall.ocs!!.data!!.capabilities
                    } catch (e: IOException) {
                        Log.e("OperationsMenu", "Failed to serialize capabilities")
                    }
                    try {
                        checkCapabilities(currentUser!!)
                        processOperation()
                    } catch (e: NoSupportedApiException) {
                        showResultImage(everythingOK = false, isGuestSupportError = false)
                        Log.d(TAG, "No supported server version found", e)
                    }
                }

                override fun onError(e: Throwable) {
                    showResultImage(everythingOK = false, isGuestSupportError = false)
                    Log.e(TAG, "Error fetching capabilities for guest", e)
                }

                override fun onComplete() {
                    // unused atm
                }
            })
    }

    @Suppress("Detekt.ComplexMethod")
    private fun processOperation() {
        if (currentUser == null) {
            showResultImage(everythingOK = false, isGuestSupportError = true)
            Log.e(TAG, "Ended up in processOperation without a valid currentUser")
            return
        }
        credentials = ApiUtils.getCredentials(currentUser!!.username, currentUser!!.token)
        when (operation) {
            ConversationOperationEnum.OPS_CODE_RENAME_ROOM -> operationRenameRoom()
            ConversationOperationEnum.OPS_CODE_GET_AND_JOIN_ROOM -> operationGetAndJoinRoom()
            ConversationOperationEnum.OPS_CODE_INVITE_USERS -> operationInviteUsers()
            ConversationOperationEnum.OPS_CODE_MARK_AS_READ -> operationMarkAsRead()
            ConversationOperationEnum.OPS_CODE_MARK_AS_UNREAD -> operationMarkAsUnread()
            ConversationOperationEnum.OPS_CODE_REMOVE_FAVORITE,
            ConversationOperationEnum.OPS_CODE_ADD_FAVORITE -> operationToggleFavorite()
            ConversationOperationEnum.OPS_CODE_JOIN_ROOM -> operationJoinRoom()
            else -> {
            }
        }
    }

    private fun apiVersion(): Int {
        return ApiUtils.getConversationApiVersion(currentUser, intArrayOf(ApiUtils.APIv4, ApiUtils.APIv1))
    }

    private fun chatApiVersion(): Int {
        return ApiUtils.getChatApiVersion(currentUser, intArrayOf(ApiUtils.APIv1))
    }

    private fun operationJoinRoom() {
        ncApi.joinRoom(
            credentials,
            ApiUtils.getUrlForParticipantsActive(
                apiVersion(),
                baseUrl,
                conversationToken
            ),
            callPassword
        )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .retry(1)
            .subscribe(RoomOperationsObserver())
    }

    private fun operationMarkAsRead() {
        ncApi.setChatReadMarker(
            credentials,
            ApiUtils.getUrlForChatReadMarker(
                chatApiVersion(),
                currentUser!!.baseUrl,
                conversation!!.token
            ),
            conversation!!.lastMessage!!.jsonMessageId
        )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .retry(1)
            .subscribe(GenericOperationsObserver())
    }

    private fun operationMarkAsUnread() {
        ncApi.markRoomAsUnread(
            credentials,
            ApiUtils.getUrlForChatReadMarker(
                chatApiVersion(),
                currentUser!!.baseUrl,
                conversation!!.token
            )
        )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .retry(1)
            .subscribe(GenericOperationsObserver())
    }

    private fun operationRenameRoom() {
        ncApi.renameRoom(
            credentials,
            ApiUtils.getUrlForRoom(
                apiVersion(),
                currentUser!!.baseUrl,
                conversation!!.token
            ),
            conversation!!.name
        )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .retry(1)
            .subscribe(GenericOperationsObserver())
    }

    private fun operationToggleFavorite() {
        val genericOperationsObserver = GenericOperationsObserver()
        val apiVersion = apiVersion()
        if (operation === ConversationOperationEnum.OPS_CODE_REMOVE_FAVORITE) {
            ncApi.removeConversationFromFavorites(
                credentials,
                ApiUtils.getUrlForRoomFavorite(
                    apiVersion,
                    currentUser!!.baseUrl,
                    conversation!!.token
                )
            )
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .retry(1)
                .subscribe(genericOperationsObserver)
        } else {
            ncApi.addConversationToFavorites(
                credentials,
                ApiUtils.getUrlForRoomFavorite(
                    apiVersion,
                    currentUser!!.baseUrl,
                    conversation!!.token
                )
            )
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .retry(1)
                .subscribe(genericOperationsObserver)
        }
    }

    private fun operationInviteUsers() {
        val retrofitBucket: RetrofitBucket
        val apiVersion = apiVersion()
        var invite: String? = null
        if (invitedGroups!!.size > 0) {
            invite = invitedGroups!![0]
        }
        retrofitBucket = if (conversationType == ConversationType.ROOM_PUBLIC_CALL) {
            ApiUtils.getRetrofitBucketForCreateRoom(
                apiVersion,
                currentUser!!.baseUrl,
                "3",
                null,
                invite,
                conversationName
            )
        } else {
            ApiUtils.getRetrofitBucketForCreateRoom(
                apiVersion,
                currentUser!!.baseUrl,
                "2",
                null,
                invite,
                conversationName
            )
        }
        ncApi.createRoom(credentials, retrofitBucket.url, retrofitBucket.queryMap)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .retry(1)
            .subscribe(object : Observer<RoomOverall> {
                override fun onSubscribe(d: Disposable) {
                    // unused atm
                }

                override fun onNext(roomOverall: RoomOverall) {
                    conversation = roomOverall.ocs!!.data
                    ncApi.getRoom(
                        credentials,
                        ApiUtils.getUrlForRoom(
                            apiVersion,
                            currentUser!!.baseUrl,
                            conversation!!.token
                        )
                    )
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(object : Observer<RoomOverall> {
                            override fun onSubscribe(d: Disposable) {
                                // unused atm
                            }

                            override fun onNext(
                                roomOverall: RoomOverall
                            ) {
                                conversation = roomOverall.ocs!!.data
                                inviteUsersToAConversation()
                            }

                            override fun onError(e: Throwable) {
                                showResultImage(everythingOK = false, isGuestSupportError = false)
                                dispose()
                            }

                            override fun onComplete() {
                                // unused atm
                            }
                        })
                }

                override fun onError(e: Throwable) {
                    showResultImage(everythingOK = false, isGuestSupportError = false)
                    dispose()
                }

                override fun onComplete() {
                    dispose()
                }
            })
    }

    private fun operationGetAndJoinRoom() {
        val apiVersion = apiVersion()
        ncApi.getRoom(
            credentials,
            ApiUtils.getUrlForRoom(apiVersion, baseUrl, conversationToken)
        )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .retry(1)
            .subscribe(object : Observer<RoomOverall> {
                override fun onSubscribe(d: Disposable) {
                    disposable = d
                }

                override fun onNext(roomOverall: RoomOverall) {
                    conversation = roomOverall.ocs!!.data
                    if (conversation!!.hasPassword && conversation!!.isGuest) {
                        eventBus.post(ConversationsListFetchDataEvent())
                        val bundle = Bundle()
                        bundle.putParcelable(KEY_ROOM, Parcels.wrap(conversation))
                        bundle.putString(KEY_CALL_URL, callUrl)
                        try {
                            bundle.putParcelable(
                                KEY_SERVER_CAPABILITIES,
                                Parcels.wrap<Capabilities>(currentUser!!.capabilities)
                            )
                        } catch (e: IOException) {
                            Log.e(TAG, "Failed to parse capabilities for guest")
                            showResultImage(everythingOK = false, isGuestSupportError = false)
                        }
                        bundle.putSerializable(KEY_OPERATION_CODE, ConversationOperationEnum.OPS_CODE_JOIN_ROOM)
                        router.pushController(
                            RouterTransaction.with(EntryMenuController(bundle))
                                .pushChangeHandler(HorizontalChangeHandler())
                                .popChangeHandler(HorizontalChangeHandler())
                        )
                    } else if (conversation!!.isGuest) {
                        ncApi.joinRoom(
                            credentials,
                            ApiUtils.getUrlForParticipantsActive(
                                apiVersion,
                                baseUrl,
                                conversationToken
                            ),
                            null
                        )
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(object : Observer<RoomOverall> {
                                override fun onSubscribe(d: Disposable) {
                                    // unused atm
                                }

                                override fun onNext(roomOverall: RoomOverall) {
                                    conversation = roomOverall.ocs!!.data
                                    initiateConversation()
                                }

                                override fun onError(e: Throwable) {
                                    showResultImage(everythingOK = false, isGuestSupportError = false)
                                    dispose()
                                }

                                override fun onComplete() {
                                    // unused atm
                                }
                            })
                    } else {
                        initiateConversation()
                    }
                }

                override fun onError(e: Throwable) {
                    showResultImage(everythingOK = false, isGuestSupportError = false)
                    dispose()
                }

                override fun onComplete() {
                    dispose()
                }
            })
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    private fun showResultImage(everythingOK: Boolean, isGuestSupportError: Boolean) {
        try {
            binding?.progressBar?.visibility = View.GONE
            if (resources != null) {
                if (everythingOK) {
                    binding?.resultImageView?.setImageDrawable(
                        DisplayUtils.getTintedDrawable(
                            resources,
                            R.drawable.ic_check_circle_black_24dp,
                            R.color.nc_darkGreen
                        )
                    )
                } else {
                    binding?.resultImageView?.setImageDrawable(
                        DisplayUtils.getTintedDrawable(
                            resources,
                            R.drawable.ic_cancel_black_24dp,
                            R.color.nc_darkRed
                        )
                    )
                }
            }
            binding?.resultImageView?.visibility = View.VISIBLE
            if (everythingOK) {
                binding?.resultTextView?.setText(R.string.nc_all_ok_operation)
            } else {
                binding?.resultTextView?.setTextColor(resources!!.getColor(R.color.nc_darkRed))
                if (!isGuestSupportError) {
                    binding?.resultTextView?.setText(R.string.nc_failed_to_perform_operation)
                } else {
                    binding?.resultTextView?.setText(R.string.nc_failed_signaling_settings)
                    binding?.webButton?.setOnClickListener {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(callUrl))
                        startActivity(browserIntent)
                    }
                    binding?.webButton?.visibility = View.VISIBLE
                }
            }
            binding?.resultTextView?.visibility = View.VISIBLE
            if (everythingOK) {
                eventBus.post(ConversationsListFetchDataEvent())
            } else {
                binding?.resultImageView?.setImageDrawable(
                    DisplayUtils.getTintedDrawable(
                        resources,
                        R.drawable.ic_cancel_black_24dp,
                        R.color.nc_darkRed
                    )
                )
                binding?.okButton?.setOnClickListener { v: View? -> eventBus.post(ConversationsListFetchDataEvent()) }
                binding?.okButton?.visibility = View.VISIBLE
            }
        } catch (npe: NullPointerException) {
            Log.i(TAG, "Controller already closed", npe)
        }
    }

    private fun dispose() {
        if (disposable != null && !disposable!!.isDisposed) {
            disposable!!.dispose()
        }
        disposable = null
    }

    public override fun onDestroy() {
        super.onDestroy()
        dispose()
    }

    @kotlin.Throws(NoSupportedApiException::class)
    private fun checkCapabilities(currentUser: User) {
        ApiUtils.getConversationApiVersion(currentUser, intArrayOf(ApiUtils.APIv4, 1))
        ApiUtils.getCallApiVersion(currentUser, intArrayOf(ApiUtils.APIv4, 1))
        ApiUtils.getChatApiVersion(currentUser, intArrayOf(1))
        ApiUtils.getSignalingApiVersion(currentUser, intArrayOf(ApiUtils.APIv3, 2, 1))
    }

    private fun inviteUsersToAConversation() {
        val localInvitedUsers = invitedUsers
        val localInvitedGroups = invitedGroups
        if (localInvitedGroups!!.size > 0) {
            localInvitedGroups.removeAt(0)
        }
        val apiVersion = ApiUtils.getConversationApiVersion(currentUser, API_CONVERSATION_VERSIONS)
        if (localInvitedUsers!!.size > 0 || localInvitedGroups.size > 0 &&
            CapabilitiesUtilNew.hasSpreedFeatureCapability(currentUser, "invite-groups-and-mails")
        ) {
            addGroupsToConversation(localInvitedUsers, localInvitedGroups, apiVersion)
            addUsersToConversation(localInvitedUsers, localInvitedGroups, apiVersion)
        } else {
            initiateConversation()
        }
    }

    private fun addUsersToConversation(
        localInvitedUsers: ArrayList<String>?,
        localInvitedGroups: ArrayList<String>?,
        apiVersion: Int
    ) {
        var retrofitBucket: RetrofitBucket
        for (i in localInvitedUsers!!.indices) {
            val userId = invitedUsers!![i]
            retrofitBucket = ApiUtils.getRetrofitBucketForAddParticipant(
                apiVersion,
                currentUser!!.baseUrl,
                conversation!!.token,
                userId
            )
            ncApi.addParticipant(credentials, retrofitBucket.url, retrofitBucket.queryMap)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .retry(1)
                .subscribe(object : Observer<AddParticipantOverall> {
                    override fun onSubscribe(d: Disposable) {
                        // unused atm
                    }
                    override fun onNext(addParticipantOverall: AddParticipantOverall) {
                        // unused atm
                    }

                    override fun onError(e: Throwable) {
                        dispose()
                    }

                    override fun onComplete() {
                        Collections.synchronizedList(localInvitedUsers).remove(userId)
                        if (localInvitedGroups!!.size == 0 && localInvitedUsers.size == 0) {
                            initiateConversation()
                        }
                        dispose()
                    }
                })
        }
    }

    private fun addGroupsToConversation(
        localInvitedUsers: ArrayList<String>?,
        localInvitedGroups: ArrayList<String>?,
        apiVersion: Int
    ) {
        var retrofitBucket: RetrofitBucket
        if (localInvitedGroups!!.size > 0 &&
            CapabilitiesUtilNew.hasSpreedFeatureCapability(currentUser, "invite-groups-and-mails")
        ) {
            for (i in localInvitedGroups.indices) {
                val groupId = localInvitedGroups[i]
                retrofitBucket = ApiUtils.getRetrofitBucketForAddParticipantWithSource(
                    apiVersion,
                    currentUser!!.baseUrl,
                    conversation!!.token,
                    "groups",
                    groupId
                )
                ncApi.addParticipant(credentials, retrofitBucket.url, retrofitBucket.queryMap)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .retry(1)
                    .subscribe(object : Observer<AddParticipantOverall> {
                        override fun onSubscribe(d: Disposable) {
                            // unused atm
                        }
                        override fun onNext(addParticipantOverall: AddParticipantOverall) {
                            // unused atm
                        }

                        override fun onError(e: Throwable) {
                            dispose()
                        }

                        override fun onComplete() {
                            Collections.synchronizedList(localInvitedGroups).remove(groupId)
                            if (localInvitedGroups.size == 0 && localInvitedUsers!!.size == 0) {
                                initiateConversation()
                            }
                            dispose()
                        }
                    })
            }
        }
    }

    private fun initiateConversation() {
        eventBus.post(ConversationsListFetchDataEvent())
        val bundle = Bundle()
        bundle.putString(KEY_ROOM_TOKEN, conversation!!.token)
        bundle.putString(KEY_ROOM_ID, conversation!!.roomId)
        bundle.putString(KEY_CONVERSATION_NAME, conversation!!.displayName)
        bundle.putParcelable(KEY_USER_ENTITY, currentUser)
        bundle.putParcelable(KEY_ACTIVE_CONVERSATION, Parcels.wrap(conversation))
        bundle.putString(KEY_CONVERSATION_PASSWORD, callPassword)
        eventBus.post(OpenConversationEvent(conversation, bundle))
    }

    private fun handleObserverError(e: Throwable) {
        if (operation !== ConversationOperationEnum.OPS_CODE_JOIN_ROOM || e !is HttpException) {
            showResultImage(everythingOK = false, isGuestSupportError = false)
        } else {
            val response = e.response()
            if (response != null && response.code() == FORBIDDEN) {
                ApplicationWideMessageHolder.getInstance()
                    .setMessageType(ApplicationWideMessageHolder.MessageType.CALL_PASSWORD_WRONG)
                router.popCurrentController()
            } else {
                showResultImage(everythingOK = false, isGuestSupportError = false)
            }
        }
        dispose()
    }

    private inner class GenericOperationsObserver : Observer<GenericOverall> {
        override fun onSubscribe(d: Disposable) {
            disposable = d
        }

        override fun onNext(genericOverall: GenericOverall) {
            if (operation !== ConversationOperationEnum.OPS_CODE_JOIN_ROOM) {
                showResultImage(everythingOK = true, isGuestSupportError = false)
            } else {
                throw IllegalArgumentException("Unsupported operation code observed!")
            }
        }

        override fun onError(e: Throwable) {
            handleObserverError(e)
        }

        override fun onComplete() {
            dispose()
        }
    }

    private inner class RoomOperationsObserver : Observer<RoomOverall> {
        override fun onSubscribe(d: Disposable) {
            disposable = d
        }

        override fun onNext(roomOverall: RoomOverall) {
            conversation = roomOverall.ocs!!.data
            if (operation !== ConversationOperationEnum.OPS_CODE_JOIN_ROOM) {
                showResultImage(everythingOK = true, isGuestSupportError = false)
            } else {
                conversation = roomOverall.ocs!!.data
                initiateConversation()
            }
        }

        override fun onError(e: Throwable) {
            handleObserverError(e)
        }

        override fun onComplete() {
            dispose()
        }
    }

    companion object {
        private const val TAG = "OperationsMenu"
        private const val FORBIDDEN = 403
        private val API_CONVERSATION_VERSIONS = intArrayOf(4, 1)
    }

    init {
        operation = args.getSerializable(KEY_OPERATION_CODE) as ConversationOperationEnum?
        if (args.containsKey(KEY_ROOM)) {
            conversation = Parcels.unwrap(args.getParcelable(KEY_ROOM))
        }
        callPassword = args.getString(KEY_CONVERSATION_PASSWORD, "")
        callUrl = args.getString(KEY_CALL_URL, "")
        if (args.containsKey(KEY_INVITED_PARTICIPANTS)) {
            invitedUsers = args.getStringArrayList(KEY_INVITED_PARTICIPANTS)
        }
        if (args.containsKey(KEY_INVITED_GROUP)) {
            invitedGroups = args.getStringArrayList(KEY_INVITED_GROUP)
        }
        if (args.containsKey(KEY_CONVERSATION_TYPE)) {
            conversationType = Parcels.unwrap(args.getParcelable(KEY_CONVERSATION_TYPE))
        }
        if (args.containsKey(KEY_SERVER_CAPABILITIES)) {
            serverCapabilities = Parcels.unwrap(args.getParcelable(KEY_SERVER_CAPABILITIES))
        }
        conversationName = args.getString(KEY_CONVERSATION_NAME, "")
    }
}
