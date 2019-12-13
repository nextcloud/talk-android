/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
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

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import butterknife.BindView
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler
import com.bluelinelabs.logansquare.LoganSquare
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.MagicCallActivity
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.controllers.base.BaseController
import com.nextcloud.talk.events.BottomSheetLockEvent
import com.nextcloud.talk.models.RetrofitBucket
import com.nextcloud.talk.models.json.capabilities.Capabilities
import com.nextcloud.talk.models.json.capabilities.CapabilitiesOverall
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.conversations.RoomOverall
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.models.json.participants.AddParticipantOverall
import com.nextcloud.talk.newarch.domain.repository.offline.UsersRepository
import com.nextcloud.talk.newarch.local.models.UserNgEntity
import com.nextcloud.talk.newarch.local.models.getCredentials
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.ConductorRemapping
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.database.user.UserUtils
import com.nextcloud.talk.utils.singletons.ApplicationWideMessageHolder
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.io.IOException
import java.util.ArrayList
import org.greenrobot.eventbus.EventBus
import org.koin.android.ext.android.inject
import org.parceler.Parcels
import retrofit2.HttpException

class OperationsMenuController(args: Bundle) : BaseController() {

    @JvmField @BindView(R.id.progress_bar)
    internal var progressBar: ProgressBar? = null

    @JvmField @BindView(R.id.result_image_view)
    internal var resultImageView: ImageView? = null

    @JvmField @BindView(R.id.result_text_view)
    internal var resultsTextView: TextView? = null

    @JvmField @BindView(R.id.ok_button)
    internal var okButton: Button? = null

    @JvmField @BindView(R.id.web_button)
    internal var webButton: Button? = null

    val ncApi: NcApi by inject()
    val usersRepository: UsersRepository by inject()

    private val operationCode: Int
    private var conversation: Conversation? = null

    private var currentUser: UserNgEntity? = null
    private val callPassword: String
    private val callUrl: String

    private var baseUrl: String? = null
    private var conversationToken: String? = null

    private var disposable: Disposable? = null

    private var conversationType: Conversation.ConversationType? = null
    private var invitedUsers: ArrayList<String>? = ArrayList()
    private var invitedGroups: ArrayList<String>? = ArrayList()

    private var serverCapabilities: Capabilities? = null
    private var credentials: String? = null
    private val conversationName: String


    init {
        this.operationCode = args.getInt(BundleKeys.KEY_OPERATION_CODE)
        if (args.containsKey(BundleKeys.KEY_ROOM)) {
            this.conversation = Parcels.unwrap(args.getParcelable(BundleKeys.KEY_ROOM))
        }

        this.callPassword = args.getString(BundleKeys.KEY_CONVERSATION_PASSWORD, "")
        this.callUrl = args.getString(BundleKeys.KEY_CALL_URL, "")

        if (args.containsKey(BundleKeys.KEY_INVITED_PARTICIPANTS)) {
            this.invitedUsers = args.getStringArrayList(BundleKeys.KEY_INVITED_PARTICIPANTS)
        }

        if (args.containsKey(BundleKeys.KEY_INVITED_GROUP)) {
            this.invitedGroups = args.getStringArrayList(BundleKeys.KEY_INVITED_GROUP)
        }

        if (args.containsKey(BundleKeys.KEY_CONVERSATION_TYPE)) {
            this.conversationType = Parcels.unwrap(args.getParcelable(BundleKeys.KEY_CONVERSATION_TYPE))
        }

        if (args.containsKey(BundleKeys.KEY_SERVER_CAPABILITIES)) {
            this.serverCapabilities = Parcels.unwrap(args.getParcelable(BundleKeys.KEY_SERVER_CAPABILITIES))
        }

        this.conversationName = args.getString(BundleKeys.KEY_CONVERSATION_NAME, "")
    }

    override fun inflateView(inflater: LayoutInflater, container: ViewGroup): View {
        return inflater.inflate(R.layout.controller_operations_menu, container, false)
    }

    override fun onViewBound(view: View) {
        super.onViewBound(view)
        processOperation()
    }

    private fun processOperation() {
        currentUser = usersRepository.getActiveUser()
        val operationsObserver = OperationsObserver()

        if (!TextUtils.isEmpty(callUrl) && callUrl.contains("/call")) {
            conversationToken = callUrl.substring(callUrl.lastIndexOf("/") + 1)
            if (callUrl.contains("/index.php")) {
                baseUrl = callUrl.substring(0, callUrl.indexOf("/index.php"))
            } else {
                baseUrl = callUrl.substring(0, callUrl.indexOf("/call"))
            }
        }

        if (currentUser != null) {
            credentials = currentUser!!.getCredentials()

            if (!TextUtils.isEmpty(baseUrl) && baseUrl != currentUser!!.baseUrl) {
                credentials = null
            }

            when (operationCode) {
                2 -> ncApi.renameRoom(credentials,
                        ApiUtils.getRoom(currentUser!!.baseUrl, conversation!!.token),
                        conversation!!.name)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .retry(1)
                        .subscribe(operationsObserver)
                3 -> ncApi.makeRoomPublic(credentials,
                        ApiUtils.getUrlForRoomVisibility(currentUser!!.baseUrl, conversation!!
                                .token))
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .retry(1)
                        .subscribe(operationsObserver)
                4, 5, 6 -> {
                    var pass: String? = ""
                    if (conversation!!.password != null) {
                        pass = conversation!!.password
                    }
                    ncApi.setPassword(credentials, ApiUtils.getUrlForPassword(currentUser!!.baseUrl,
                            conversation!!.token), pass)
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .retry(1)
                            .subscribe(operationsObserver)
                }
                7 -> {
                }
                8 -> ncApi.makeRoomPrivate(credentials,
                        ApiUtils.getUrlForRoomVisibility(currentUser!!.baseUrl, conversation!!
                                .token))
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .retry(1)
                        .subscribe(operationsObserver)
                10 -> ncApi.getRoom(credentials, ApiUtils.getRoom(baseUrl, conversationToken))
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .retry(1)
                        .subscribe(object : Observer<RoomOverall> {
                            override fun onSubscribe(d: Disposable) {
                                disposable = d
                            }

                            override fun onNext(roomOverall: RoomOverall) {
                                conversation = roomOverall.ocs.data
                                fetchCapabilities(credentials)
                            }

                            override fun onError(e: Throwable) {
                                showResultImage(false, false)
                                dispose()
                            }

                            override fun onComplete() {
                                dispose()
                            }
                        })
                11 -> {
                    val retrofitBucket: RetrofitBucket
                    var isGroupCallWorkaround = false
                    var invite: String? = null

                    if (invitedGroups!!.size > 0) {
                        invite = invitedGroups!![0]
                    }

                    if (conversationType == Conversation.ConversationType.PUBLIC_CONVERSATION || !currentUser!!.hasSpreedFeatureCapability("empty-group-room")) {
                        retrofitBucket = ApiUtils.getRetrofitBucketForCreateRoom(currentUser!!.baseUrl,
                                "3", invite, conversationName)
                    } else {
                        var roomType = "2"
                        if (!currentUser!!.hasSpreedFeatureCapability("empty-group-room")) {
                            isGroupCallWorkaround = true
                            roomType = "3"
                        }

                        retrofitBucket = ApiUtils.getRetrofitBucketForCreateRoom(currentUser!!.baseUrl,
                                roomType, invite, conversationName)
                    }

                    val isGroupCallWorkaroundFinal = isGroupCallWorkaround
                    ncApi.createRoom(credentials, retrofitBucket.url, retrofitBucket.queryMap)
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .retry(1)
                            .subscribe(object : Observer<RoomOverall> {
                                override fun onSubscribe(d: Disposable) {

                                }

                                override fun onNext(roomOverall: RoomOverall) {
                                    conversation = roomOverall.ocs.data

                                    ncApi.getRoom(credentials,
                                            ApiUtils.getRoom(currentUser!!.baseUrl, conversation!!.token))
                                            .subscribeOn(Schedulers.io())
                                            .observeOn(AndroidSchedulers.mainThread())
                                            .subscribe(object : Observer<RoomOverall> {
                                                override fun onSubscribe(d: Disposable) {

                                                }

                                                override fun onNext(roomOverall: RoomOverall) {
                                                    conversation = roomOverall.ocs.data
                                                    if (conversationType == Conversation.ConversationType.PUBLIC_CONVERSATION && isGroupCallWorkaroundFinal) {
                                                        performGroupCallWorkaround(credentials)
                                                    } else {
                                                        inviteUsersToAConversation()
                                                    }
                                                }

                                                override fun onError(e: Throwable) {
                                                    showResultImage(false, false)
                                                    dispose()
                                                }

                                                override fun onComplete() {

                                                }
                                            })
                                }

                                override fun onError(e: Throwable) {
                                    showResultImage(false, false)
                                    dispose()
                                }

                                override fun onComplete() {
                                    dispose()
                                }
                            })
                }
                97, 98 -> if (operationCode == 97) {
                    ncApi.removeConversationFromFavorites(credentials,
                            ApiUtils.getUrlForConversationFavorites(currentUser!!.baseUrl,
                                    conversation!!.token))
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .retry(1)
                            .subscribe(operationsObserver)
                } else {
                    ncApi.addConversationToFavorites(credentials,
                            ApiUtils.getUrlForConversationFavorites(currentUser!!.baseUrl,
                                    conversation!!.token))
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .retry(1)
                            .subscribe(operationsObserver)
                }
                99 -> ncApi.joinRoom(credentials,
                        ApiUtils.getUrlForSettingMyselfAsActiveParticipant(baseUrl, conversationToken),
                        callPassword)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .retry(1)
                        .subscribe(operationsObserver)
                else -> {
                }
            }// Operation 7 is sharing, so we handle this differently
        }
    }

    private fun performGroupCallWorkaround(credentials: String?) {
        ncApi.makeRoomPrivate(credentials,
                ApiUtils.getUrlForRoomVisibility(currentUser!!.baseUrl, conversation!!.token))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .retry(1)
                .subscribe(object : Observer<GenericOverall> {
                    override fun onSubscribe(d: Disposable) {

                    }

                    override fun onNext(genericOverall: GenericOverall) {
                        inviteUsersToAConversation()
                    }

                    override fun onError(e: Throwable) {
                        showResultImage(false, false)
                        dispose()
                    }

                    override fun onComplete() {
                        dispose()
                    }
                })
    }

    private fun showResultImage(everythingOK: Boolean, isGuestSupportError: Boolean) {
        progressBar!!.visibility = View.GONE

        if (everythingOK) {
            resultImageView!!.setImageDrawable(DisplayUtils.getTintedDrawable(resources!!, R.drawable
                    .ic_check_circle_black_24dp, R.color.nc_darkGreen))
        } else {
            resultImageView!!.setImageDrawable(DisplayUtils.getTintedDrawable(resources!!, R.drawable
                    .ic_cancel_black_24dp, R.color.nc_darkRed))
        }

        resultImageView!!.visibility = View.VISIBLE

        if (everythingOK) {
            resultsTextView!!.setText(R.string.nc_all_ok_operation)
        } else {
            resultsTextView!!.setTextColor(resources!!.getColor(R.color.nc_darkRed))
            if (!isGuestSupportError) {
                resultsTextView!!.setText(R.string.nc_failed_to_perform_operation)
            } else {
                resultsTextView!!.setText(R.string.nc_failed_signaling_settings)
                webButton!!.setOnClickListener { v ->
                    eventBus.post(BottomSheetLockEvent(true, 0, false, true))
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(callUrl))
                    startActivity(browserIntent)
                    BottomSheetLockEvent(true, 0, false, true)
                }
                webButton!!.visibility = View.VISIBLE
            }
        }

        resultsTextView!!.visibility = View.VISIBLE
        if (everythingOK) {
            eventBus.post(BottomSheetLockEvent(true, 2500, true, true))
        } else {
            resultImageView!!.setImageDrawable(DisplayUtils.getTintedDrawable(resources!!, R.drawable
                    .ic_cancel_black_24dp, R.color.nc_darkRed))
            okButton!!.setOnClickListener { v -> eventBus.post(BottomSheetLockEvent(true, 0, operationCode != 99 && operationCode != 10, true)) }
            okButton!!.visibility = View.VISIBLE
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

    private fun fetchCapabilities(credentials: String?) {
        ncApi.getCapabilities(credentials, ApiUtils.getUrlForCapabilities(baseUrl))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(object : Observer<CapabilitiesOverall> {
                    override fun onSubscribe(d: Disposable) {

                    }

                    override fun onNext(capabilitiesOverall: CapabilitiesOverall) {
                        val hasChatV2Capability = capabilitiesOverall.ocs?.data?.capabilities?.spreedCapability?.features?.contains("chat-v2") == true
                        val hasGuestSignaling = capabilitiesOverall.ocs?.data?.capabilities?.spreedCapability?.features?.contains("guest-signaling") == true

                        if (hasChatV2Capability) {
                            if (conversation!!.hasPassword && conversation!!.isGuest) {
                                eventBus.post(BottomSheetLockEvent(true, 0,
                                        true, false))
                                val bundle = Bundle()
                                bundle.putParcelable(BundleKeys.KEY_ROOM, Parcels.wrap(conversation))
                                bundle.putString(BundleKeys.KEY_CALL_URL, callUrl)
                                bundle.putParcelable(BundleKeys.KEY_SERVER_CAPABILITIES,
                                        Parcels.wrap(capabilitiesOverall.ocs.data.capabilities))
                                bundle.putInt(BundleKeys.KEY_OPERATION_CODE, 99)
                                router.pushController(RouterTransaction.with(EntryMenuController(bundle))
                                        .pushChangeHandler(HorizontalChangeHandler())
                                        .popChangeHandler(HorizontalChangeHandler()))
                            } else {
                                initiateConversation(false, capabilitiesOverall.ocs.data.capabilities)
                            }

                        } else if (hasGuestSignaling) {
                            initiateCall()
                        } else {
                            showResultImage(false, true)

                        }
                    }

                    override fun onError(e: Throwable) {
                        showResultImage(false, false)
                    }

                    override fun onComplete() {

                    }
                })
    }

    private fun inviteUsersToAConversation() {
        var retrofitBucket: RetrofitBucket
        val localInvitedUsers = invitedUsers
        val localInvitedGroups = invitedGroups
        if (localInvitedGroups!!.size > 0) {
            localInvitedGroups.removeAt(0)
        }

        if (localInvitedUsers!!.size > 0 || localInvitedGroups.size > 0 && currentUser!!.hasSpreedFeatureCapability("invite-groups-and-mails")) {
            if (localInvitedGroups.size > 0 && currentUser!!.hasSpreedFeatureCapability(
                            "invite-groups-and-mails")) {
                for (i in localInvitedGroups.indices) {
                    val groupId = localInvitedGroups[i]
                    retrofitBucket = ApiUtils.getRetrofitBucketForAddGroupParticipant(currentUser!!.baseUrl,
                            conversation!!.token,
                            groupId)

                    ncApi.addParticipant(credentials, retrofitBucket.url, retrofitBucket.queryMap)
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .retry(1)
                            .subscribe(object : Observer<AddParticipantOverall> {
                                override fun onSubscribe(d: Disposable) {

                                }

                                override fun onNext(addParticipantOverall: AddParticipantOverall) {}

                                override fun onError(e: Throwable) {
                                    dispose()
                                }

                                override fun onComplete() {
                                    synchronized(localInvitedGroups) {
                                        localInvitedGroups.remove(groupId)
                                    }

                                    if (localInvitedGroups.size == 0 && localInvitedUsers.size == 0) {
                                        initiateConversation(true, null)
                                    }
                                    dispose()
                                }
                            })
                }
            }

            for (i in localInvitedUsers.indices) {
                val userId = invitedUsers!![i]
                retrofitBucket = ApiUtils.getRetrofitBucketForAddParticipant(currentUser!!.baseUrl,
                        conversation!!.token,
                        userId)

                ncApi.addParticipant(credentials, retrofitBucket.url, retrofitBucket.queryMap)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .retry(1)
                        .subscribe(object : Observer<AddParticipantOverall> {
                            override fun onSubscribe(d: Disposable) {

                            }

                            override fun onNext(addParticipantOverall: AddParticipantOverall) {}

                            override fun onError(e: Throwable) {
                                dispose()
                            }

                            override fun onComplete() {
                                synchronized(localInvitedUsers) {
                                    localInvitedUsers.remove(userId)
                                }

                                if (localInvitedGroups.size == 0 && localInvitedUsers.size == 0) {
                                    initiateConversation(true, null)
                                }
                                dispose()
                            }
                        })
            }
        } else {
            if (!currentUser!!.hasSpreedFeatureCapability("chat-v2")) {
                showResultImage(true, false)
            } else {
                initiateConversation(true, null)
            }
        }
    }

    private fun initiateConversation(dismissView: Boolean, capabilities: Capabilities?) {
        val bundle = Bundle()
        var isGuestUser = false
        val hasChatCapability: Boolean

        if (baseUrl != null && baseUrl != currentUser!!.baseUrl) {
            isGuestUser = true
            hasChatCapability = capabilities?.spreedCapability?.features?.contains("chat-v2") == true
        } else {
            hasChatCapability = currentUser!!.hasSpreedFeatureCapability("chat-v2")
        }

        if (hasChatCapability) {
            eventBus.post(BottomSheetLockEvent(true, 0,
                    true, true, dismissView))

            val conversationIntent = Intent(activity, MagicCallActivity::class.java)
            bundle.putString(BundleKeys.KEY_ROOM_TOKEN, conversation!!.token)
            bundle.putString(BundleKeys.KEY_ROOM_ID, conversation!!.conversationId)
            bundle.putString(BundleKeys.KEY_CONVERSATION_NAME,
                    conversation!!.displayName)
            val conversationUser: UserNgEntity
            if (isGuestUser) {
                conversationUser = UserNgEntity(-1, "?", "?", baseUrl!!)
                conversationUser.capabilities = capabilities!!
            } else {
                conversationUser = currentUser!!
            }

            bundle.putParcelable(BundleKeys.KEY_USER_ENTITY, conversationUser)
            bundle.putParcelable(BundleKeys.KEY_ACTIVE_CONVERSATION,
                    Parcels.wrap(conversation))
            bundle.putString(BundleKeys.KEY_CONVERSATION_PASSWORD, callPassword)

            conversationIntent.putExtras(bundle)

            if (parentController != null) {
                ConductorRemapping.remapChatController(parentController!!.router,
                        conversationUser.id!!,
                        conversation!!.token!!, bundle, true)
            }
        } else {
            initiateCall()
        }
    }

    private fun initiateCall() {
        eventBus.post(BottomSheetLockEvent(true, 0, true, true))
        val bundle = Bundle()
        bundle.putString(BundleKeys.KEY_ROOM_TOKEN, conversation!!.token)
        bundle.putParcelable(BundleKeys.KEY_USER_ENTITY, currentUser)
        if (baseUrl != null && baseUrl != currentUser!!.baseUrl) {
            bundle.putString(BundleKeys.KEY_MODIFIED_BASE_URL, baseUrl)
        }
        bundle.putParcelable(BundleKeys.KEY_ACTIVE_CONVERSATION,
                Parcels.wrap(conversation))

        if (activity != null) {

            val callIntent = Intent(activity, MagicCallActivity::class.java)
            callIntent.putExtras(bundle)

            val imm = activity!!.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
            imm?.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0)

            Handler().postDelayed({ parentController!!.router.popCurrentController() },
                    100)
            startActivity(callIntent)
        }
    }

    private inner class OperationsObserver : Observer<Any> {

        override fun onSubscribe(d: Disposable) {
            disposable = d
        }

        override fun onNext(o: Any) {
            if (operationCode != 99) {
                showResultImage(true, false)
            } else {
                val roomOverall = o as RoomOverall
                conversation = roomOverall.ocs.data
                initiateConversation(true, serverCapabilities)
            }
        }

        override fun onError(e: Throwable) {
            if (operationCode != 99 || e !is HttpException) {
                showResultImage(false, false)
            } else {
                if (e.response()!!.code() == 403) {
                    eventBus.post(BottomSheetLockEvent(true, 0, false,
                            false))
                    ApplicationWideMessageHolder.getInstance()
                            .setMessageType(ApplicationWideMessageHolder.MessageType.CALL_PASSWORD_WRONG)
                    router.popCurrentController()
                } else {
                    showResultImage(false, false)
                }
            }
            dispose()
        }

        override fun onComplete() {
            dispose()
        }
    }
}
