/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Marcel Hibbe
 * Copyright (C) 2022 Marcel Hibbe <dev@mhibbe.de>
 * Copyright (C) 2017-2018 Mario Danic <mario@lovelyhq.com>
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
package com.nextcloud.talk.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationManagerCompat
import autodagger.AutoInjector
import com.nextcloud.talk.R
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.CallNotificationActivityBinding
import com.nextcloud.talk.extensions.loadAvatar
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.conversations.RoomOverall
import com.nextcloud.talk.models.json.participants.Participant
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.NotificationUtils
import com.nextcloud.talk.utils.ParticipantPermissions
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_CALL_VOICE_ONLY
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_CONVERSATION_NAME
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_USER_ENTITY
import com.nextcloud.talk.utils.database.user.CapabilitiesUtilNew.hasSpreedFeatureCapability
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.call_item.*
import okhttp3.Cache
import org.parceler.Parcels
import java.io.IOException
import javax.inject.Inject
import kotlin.concurrent.thread

@SuppressLint("LongLogTag")
@AutoInjector(NextcloudTalkApplication::class)
class CallNotificationActivity : CallBaseActivity() {
    @JvmField
    @Inject
    var ncApi: NcApi? = null

    @JvmField
    @Inject
    var cache: Cache? = null

    private val disposablesList: MutableList<Disposable> = ArrayList()
    private var originalBundle: Bundle? = null
    private var roomToken: String? = null
    private var notificationTimestamp: Int? = null
    private var userBeingCalled: User? = null
    private var credentials: String? = null
    private var currentConversation: Conversation? = null
    private var leavingScreen = false
    private var handler: Handler? = null
    private var binding: CallNotificationActivityBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate")
        super.onCreate(savedInstanceState)
        sharedApplication!!.componentApplication.inject(this)
        binding = CallNotificationActivityBinding.inflate(layoutInflater)
        setContentView(binding!!.root)
        hideNavigationIfNoPipAvailable()
        val extras = intent.extras
        roomToken = extras!!.getString(KEY_ROOM_TOKEN, "")
        notificationTimestamp = extras.getInt(BundleKeys.KEY_NOTIFICATION_TIMESTAMP)
        currentConversation = Parcels.unwrap(extras.getParcelable(KEY_ROOM))
        userBeingCalled = extras.getParcelable(KEY_USER_ENTITY)
        originalBundle = extras
        credentials = ApiUtils.getCredentials(userBeingCalled!!.username, userBeingCalled!!.token)
        setCallDescriptionText()
        if (currentConversation == null) {
            handleFromNotification()
        } else {
            setUpAfterConversationIsKnown()
        }
        initClickListeners()
    }

    override fun onStart() {
        super.onStart()
        if (handler == null) {
            handler = Handler()
            try {
                cache!!.evictAll()
            } catch (e: IOException) {
                Log.e(TAG, "Failed to evict cache")
            }
        }
    }

    private fun initClickListeners() {
        binding!!.callAnswerVoiceOnlyView.setOnClickListener {
            Log.d(TAG, "accept call (voice only)")
            originalBundle!!.putBoolean(KEY_CALL_VOICE_ONLY, true)
            proceedToCall()
        }
        binding!!.callAnswerCameraView.setOnClickListener {
            Log.d(TAG, "accept call (with video)")
            originalBundle!!.putBoolean(KEY_CALL_VOICE_ONLY, false)
            proceedToCall()
        }
        binding!!.hangupButton.setOnClickListener { hangup() }
    }

    private fun setCallDescriptionText() {
        val callDescriptionWithoutTypeInfo = String.format(
            resources.getString(R.string.nc_call_unknown),
            resources.getString(R.string.nc_app_product_name)
        )
        binding!!.incomingCallVoiceOrVideoTextView.text = callDescriptionWithoutTypeInfo
    }

    private fun showAnswerControls() {
        binding!!.callAnswerCameraView.visibility = View.VISIBLE
        binding!!.callAnswerVoiceOnlyView.visibility = View.VISIBLE
    }

    private fun hangup() {
        leavingScreen = true
        dispose()
        finish()
    }

    private fun proceedToCall() {
        originalBundle!!.putString(KEY_ROOM_TOKEN, currentConversation!!.token)
        originalBundle!!.putString(KEY_CONVERSATION_NAME, currentConversation!!.displayName)

        val participantPermission = ParticipantPermissions(
            userBeingCalled!!,
            currentConversation!!
        )
        originalBundle!!.putBoolean(
            BundleKeys.KEY_PARTICIPANT_PERMISSION_CAN_PUBLISH_AUDIO,
            participantPermission.canPublishAudio()
        )
        originalBundle!!.putBoolean(
            BundleKeys.KEY_PARTICIPANT_PERMISSION_CAN_PUBLISH_VIDEO,
            participantPermission.canPublishVideo()
        )

        val intent = Intent(this, CallActivity::class.java)
        intent.putExtras(originalBundle!!)
        startActivity(intent)
    }

    @Suppress("MagicNumber")
    private fun handleFromNotification() {
        val apiVersion = ApiUtils.getConversationApiVersion(
            userBeingCalled,
            intArrayOf(
                ApiUtils.APIv4,
                ApiUtils.APIv3, 1
            )
        )
        ncApi!!.getRoom(credentials, ApiUtils.getUrlForRoom(apiVersion, userBeingCalled!!.baseUrl, roomToken))
            .subscribeOn(Schedulers.io())
            .retry(GET_ROOM_RETRY_COUNT)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : Observer<RoomOverall> {
                override fun onSubscribe(d: Disposable) {
                    disposablesList.add(d)
                }

                override fun onNext(roomOverall: RoomOverall) {
                    currentConversation = roomOverall.ocs!!.data
                    setUpAfterConversationIsKnown()
                    if (apiVersion >= 3) {
                        val hasCallFlags = hasSpreedFeatureCapability(
                            userBeingCalled,
                            "conversation-call-flags"
                        )
                        if (hasCallFlags) {
                            if (isInCallWithVideo(currentConversation!!.callFlag)) {
                                binding!!.incomingCallVoiceOrVideoTextView.text = String.format(
                                    resources.getString(R.string.nc_call_video),
                                    resources.getString(R.string.nc_app_product_name)
                                )
                            } else {
                                binding!!.incomingCallVoiceOrVideoTextView.text = String.format(
                                    resources.getString(R.string.nc_call_voice),
                                    resources.getString(R.string.nc_app_product_name)
                                )
                            }
                        }
                    }
                }

                override fun onError(e: Throwable) {
                    Log.e(TAG, e.message, e)
                }

                override fun onComplete() {
                    // unused atm
                }
            })
    }

    private fun isInCallWithVideo(callFlag: Int): Boolean {
        return (callFlag and Participant.InCallFlags.WITH_VIDEO) > 0
    }

    private fun setUpAfterConversationIsKnown() {
        binding!!.conversationNameTextView.text = currentConversation!!.displayName
        if (currentConversation!!.type === Conversation.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL) {
            avatarImageView.loadAvatar(userBeingCalled!!, currentConversation!!.name!!)
        } else {
            binding!!.avatarImageView.setImageResource(R.drawable.ic_circular_group)
        }

        thread(start = true) {
            var isNotificationOpen = true
            while (isNotificationOpen) {
                Thread.sleep(1000)
                if (!NotificationUtils.isNotificationVisible(context, notificationTimestamp!!.toInt())) {
                    isNotificationOpen = false
                    finish()
                }
            }
        }

        showAnswerControls()
    }

    override fun onStop() {
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.cancel(notificationTimestamp!!)
        super.onStop()
    }

    public override fun onDestroy() {
        leavingScreen = true
        if (handler != null) {
            handler!!.removeCallbacksAndMessages(null)
            handler = null
        }
        dispose()
        super.onDestroy()
    }

    private fun dispose() {
        for (disposable in disposablesList) {
            if (!disposable.isDisposed) {
                disposable.dispose()
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode = isInPictureInPictureMode
        if (isInPictureInPictureMode) {
            updateUiForPipMode()
        } else {
            updateUiForNormalMode()
        }
    }

    public override fun updateUiForPipMode() {
        binding!!.callAnswerButtons.visibility = View.INVISIBLE
        binding!!.incomingCallRelativeLayout.visibility = View.INVISIBLE
    }

    public override fun updateUiForNormalMode() {
        binding!!.callAnswerButtons.visibility = View.VISIBLE
        binding!!.incomingCallRelativeLayout.visibility = View.VISIBLE
    }

    public override fun suppressFitsSystemWindows() {
        binding!!.controllerCallNotificationLayout.fitsSystemWindows = false
    }

    companion object {
        const val TAG = "CallNotificationActivity"
        const val GET_ROOM_RETRY_COUNT: Long = 3
    }
}
