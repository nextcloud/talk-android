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
package com.nextcloud.talk.callnotification

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModelProvider
import autodagger.AutoInjector
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.CallActivity
import com.nextcloud.talk.activities.CallBaseActivity
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.callnotification.viewmodel.CallNotificationViewModel
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.CallNotificationActivityBinding
import com.nextcloud.talk.extensions.loadUserAvatar
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.domain.ConversationType
import com.nextcloud.talk.models.json.participants.Participant
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.ConversationUtils
import com.nextcloud.talk.utils.NotificationUtils
import com.nextcloud.talk.utils.ParticipantPermissions
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_CALL_VOICE_ONLY
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_CONVERSATION_NAME
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import com.nextcloud.talk.utils.database.user.CapabilitiesUtilNew.hasSpreedFeatureCapability
import io.reactivex.disposables.Disposable
import okhttp3.Cache
import java.io.IOException
import javax.inject.Inject

@SuppressLint("LongLogTag")
@AutoInjector(NextcloudTalkApplication::class)
class CallNotificationActivity : CallBaseActivity() {
    @JvmField
    @Inject
    var ncApi: NcApi? = null

    @JvmField
    @Inject
    var cache: Cache? = null

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    lateinit var callNotificationViewModel: CallNotificationViewModel

    private val disposablesList: MutableList<Disposable> = ArrayList()
    private var originalBundle: Bundle? = null
    private var roomToken: String? = null
    private var notificationTimestamp: Int? = null
    private var userBeingCalled: User? = null
    private var credentials: String? = null
    var currentConversation: ConversationModel? = null
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

        val internalUserId = extras.getLong(BundleKeys.KEY_INTERNAL_USER_ID)
        userBeingCalled = userManager.getUserWithId(internalUserId).blockingGet()

        originalBundle = extras
        credentials = ApiUtils.getCredentials(userBeingCalled!!.username, userBeingCalled!!.token)

        callNotificationViewModel = ViewModelProvider(this, viewModelFactory)[CallNotificationViewModel::class.java]

        initObservers()

        if (userManager.setUserAsActive(userBeingCalled!!).blockingGet()) {
            setCallDescriptionText()
            callNotificationViewModel.getRoom(userBeingCalled!!, roomToken!!)
        }
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

    private fun initObservers() {
        val apiVersion = ApiUtils.getConversationApiVersion(
            userBeingCalled,
            intArrayOf(
                ApiUtils.APIv4,
                ApiUtils.APIv3,
                1
            )
        )

        callNotificationViewModel.getRoomViewState.observe(this) { state ->
            when (state) {
                is CallNotificationViewModel.GetRoomSuccessState -> {
                    currentConversation = state.conversationModel

                    binding!!.conversationNameTextView.text = currentConversation!!.displayName
                    if (currentConversation!!.type === ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL) {
                        binding!!.avatarImageView.loadUserAvatar(
                            userBeingCalled!!,
                            currentConversation!!.name!!,
                            true,
                            false
                        )
                    } else {
                        binding!!.avatarImageView.setImageResource(R.drawable.ic_circular_group)
                    }

                    val notificationHandler = Handler(Looper.getMainLooper())
                    notificationHandler.post(object : Runnable {
                        override fun run() {
                            if (NotificationUtils.isNotificationVisible(context, notificationTimestamp!!.toInt())) {
                                notificationHandler.postDelayed(this, ONE_SECOND)
                            } else {
                                finish()
                            }
                        }
                    })

                    showAnswerControls()

                    if (apiVersion >= ApiUtils.APIv3) {
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

                    initClickListeners()
                }

                is CallNotificationViewModel.GetRoomErrorState -> {
                    Toast.makeText(context, R.string.nc_common_error_sorry, Toast.LENGTH_LONG).show()
                }

                else -> {}
            }
        }
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
        if (currentConversation != null) {
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
            originalBundle!!.putBoolean(
                BundleKeys.KEY_IS_MODERATOR,
                ConversationUtils.isParticipantOwnerOrModerator(currentConversation!!)
            )

            val intent = Intent(this, CallActivity::class.java)
            intent.putExtras(originalBundle!!)
            startActivity(intent)
        } else {
            Log.w(TAG, "conversation was still null when clicked to answer call. User has to click another time.")
        }
    }

    private fun isInCallWithVideo(callFlag: Int): Boolean {
        return (callFlag and Participant.InCallFlags.WITH_VIDEO) > 0
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

    override fun updateUiForPipMode() {
        binding!!.callAnswerButtons.visibility = View.INVISIBLE
        binding!!.incomingCallRelativeLayout.visibility = View.INVISIBLE
    }

    override fun updateUiForNormalMode() {
        binding!!.callAnswerButtons.visibility = View.VISIBLE
        binding!!.incomingCallRelativeLayout.visibility = View.VISIBLE
    }

    override fun suppressFitsSystemWindows() {
        binding!!.controllerCallNotificationLayout.fitsSystemWindows = false
    }

    companion object {
        const val TAG = "CallNotificationActivity"
        const val ONE_SECOND: Long = 1000
    }
}
