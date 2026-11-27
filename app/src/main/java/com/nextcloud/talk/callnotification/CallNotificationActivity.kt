/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2017-2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.callnotification

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import autodagger.AutoInjector
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.CallActivity
import com.nextcloud.talk.activities.CallBaseActivity
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.call.TelecomManager
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.CallNotificationActivityBinding
import com.nextcloud.talk.extensions.loadUserAvatar
import com.nextcloud.talk.models.json.participants.ParticipantDto
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.CapabilitiesUtil.hasSpreedFeatureCapability
import com.nextcloud.talk.utils.NotificationUtils
import com.nextcloud.talk.utils.SpreedFeatures
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_CALL_VOICE_ONLY
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_ONE_TO_ONE
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import com.nextcloud.talk.receivers.DeclineCallReceiver
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
    lateinit var telecomManager: TelecomManager
    private var roomToken: String? = null
    private var notificationTimestamp: Int? = null
    private var displayName: String? = null
    private var callFlag: Int = 0
    private var isOneToOneCall: Boolean = true
    private var conversationName: String? = null
    private lateinit var userBeingCalled: User
    private var isCacheEvicted = false
    private var leavingScreen = false
    private var wasLastShownInPip = false
    private val handler = Handler(Looper.getMainLooper())
    private var binding: CallNotificationActivityBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedApplication!!.componentApplication.inject(this)
        userBeingCalled = setUpBoundUserOrFinish() ?: return
        binding = CallNotificationActivityBinding.inflate(layoutInflater)
        setContentView(binding!!.root)
        hideNavigationIfNoPipAvailable()

        handleExtras()

        setupCallTypeDescription()
        setupCallAnswerButtons()
        binding!!.conversationNameTextView.text = displayName
        setupAvatar(isOneToOneCall, conversationName)
        initClickListeners()
        setupNotificationCanceledRoutine()
        // a window recreated for another call while in picture in picture gets no mode change
        if (isInPictureInPictureMode) {
            isInPipMode = true
            wasLastShownInPip = true
            updateUiForPipMode()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val newNotificationId = intent.extras?.let { notificationIdOrNull(it) } ?: return
        // a window still open for an earlier call is set up again for the new one
        if (newNotificationId != notificationTimestamp) {
            Log.d(TAG, "onNewIntent: another incoming call, recreating the window")
            handler.removeCallbacksAndMessages(null)
            setIntent(intent)
            recreate()
        }
    }

    private fun handleExtras() {
        val extras = intent.extras!!
        roomToken = extras.getString(KEY_ROOM_TOKEN, "")
        notificationTimestamp = notificationIdOrNull(extras)
        displayName = extras.getString(BundleKeys.KEY_CONVERSATION_DISPLAY_NAME, "")
        callFlag = extras.getInt(BundleKeys.KEY_CALL_FLAG)
        isOneToOneCall = extras.getBoolean(KEY_ROOM_ONE_TO_ONE)
        conversationName = extras.getString(BundleKeys.KEY_CONVERSATION_NAME, "")
    }

    private fun setupAvatar(isOneToOneCall: Boolean, conversationName: String?) {
        if (isOneToOneCall) {
            binding!!.avatarImageView.loadUserAvatar(
                userBeingCalled,
                conversationName!!,
                true,
                false
            )
        } else {
            binding!!.avatarImageView.setImageResource(R.drawable.ic_circular_group)
        }
    }

    private fun hasCallFlagsCapability(): Boolean {
        val apiVersion = ApiUtils.getConversationApiVersion(
            userBeingCalled,
            intArrayOf(
                ApiUtils.API_V4,
                ApiUtils.API_V3,
                1
            )
        )

        return apiVersion >= ApiUtils.API_V3 &&
            hasSpreedFeatureCapability(
                userBeingCalled.capabilities?.spreedCapability,
                SpreedFeatures.CONVERSATION_CALL_FLAGS
            )
    }

    private fun setupCallTypeDescription() {
        if (hasCallFlagsCapability()) {
            if (isInCallWithVideo(callFlag)) {
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
        } else {
            val callDescriptionWithoutTypeInfo = String.format(
                resources.getString(R.string.nc_call_unknown),
                resources.getString(R.string.nc_app_product_name)
            )
            binding!!.incomingCallVoiceOrVideoTextView.text = callDescriptionWithoutTypeInfo
        }
    }

    private fun setupCallAnswerButtons() {
        if (hasCallFlagsCapability()) {
            if (isInCallWithVideo(callFlag)) {
                binding!!.callAnswerVoiceOnlyView.visibility = View.GONE
            } else {
                binding!!.callAnswerCameraView.visibility = View.GONE
            }
        }
    }

    private fun setupNotificationCanceledRoutine() {
        val notificationId = notificationTimestamp
        if (notificationId == null) {
            // no notification backs this window, so there is no call to show
            finish()
            return
        }
        handler.post(object : Runnable {
            override fun run() {
                if (NotificationUtils.isNotificationVisible(context, notificationId)) {
                    handler.postDelayed(this, ONE_SECOND)
                } else {
                    if (!leavingScreen) {
                        telecomManager.endRingingCall(roomToken)
                    }
                    finish()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        if (!isInPictureInPictureMode) {
            wasLastShownInPip = false
        }
    }

    override fun onStart() {
        super.onStart()
        if (!isCacheEvicted) {
            isCacheEvicted = true
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
            intent.putExtra(KEY_CALL_VOICE_ONLY, true)
            proceedToCall()
        }
        binding!!.callAnswerCameraView.setOnClickListener {
            Log.d(TAG, "accept call (with video)")
            intent.putExtra(KEY_CALL_VOICE_ONLY, false)
            proceedToCall()
        }
        binding!!.hangupButton.setOnClickListener { hangup() }
    }

    private fun hangup() {
        leavingScreen = true
        val declineIntent = Intent(this, DeclineCallReceiver::class.java).apply {
            putExtra(BundleKeys.KEY_NOTIFICATION_TIMESTAMP, notificationTimestamp)
            putExtra(KEY_ROOM_TOKEN, roomToken)
        }
        sendBroadcast(declineIntent)
        finish()
    }

    private fun proceedToCall() {
        // onStop() cancels the notification; the canceled-routine must not read that as the ring being withdrawn
        leavingScreen = true
        val callIntent = Intent(this, CallActivity::class.java)
        intent.putExtra(KEY_ROOM_ONE_TO_ONE, isOneToOneCall)
        callIntent.putExtras(intent.extras!!)
        startActivity(callIntent)
        // stops the ringing, the notification check then closes this window
        dismissCallNotification()
    }

    private fun dismissCallNotification() {
        notificationTimestamp?.let { NotificationManagerCompat.from(context).cancel(it) }
    }

    private fun isInCallWithVideo(callFlag: Int): Boolean = (callFlag and ParticipantDto.InCallFlags.WITH_VIDEO) > 0

    public override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        // closing the picture in picture window finishes the window on older Android versions
        if (isFinishing && wasLastShownInPip) {
            dismissCallNotification()
        }
        super.onDestroy()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode = isInPictureInPictureMode
        if (isInPictureInPictureMode) {
            wasLastShownInPip = true
            updateUiForPipMode()
        } else {
            updateUiForNormalMode()
            handler.postDelayed({ hangupIfPipWindowWasClosed() }, ONE_SECOND)
        }
    }

    /**
     * Closing the picture in picture window only stops the window, while expanding it starts the window again. The
     * start may come after the mode change, so it is checked later. While the device is locked the window can not
     * have been closed, it waits behind the lock screen to be shown.
     */
    private fun hangupIfPipWindowWasClosed() {
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (!isInPictureInPictureMode &&
            !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) &&
            !keyguardManager.isKeyguardLocked
        ) {
            Log.d(TAG, "picture in picture window was closed")
            hangup()
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
        binding!!.callNotificationLayout.fitsSystemWindows = false
    }

    companion object {
        private val TAG = CallNotificationActivity::class.simpleName
        const val ONE_SECOND: Long = 1000

        /**
         * The call notification id is the push message timestamp cut to an Int, so 0 and negative values are valid
         * ids. Only a missing extra means that no notification backs the window.
         */
        @VisibleForTesting
        fun notificationIdOrNull(extras: Bundle): Int? =
            if (extras.containsKey(BundleKeys.KEY_NOTIFICATION_TIMESTAMP)) {
                extras.getInt(BundleKeys.KEY_NOTIFICATION_TIMESTAMP)
            } else {
                null
            }
    }
}
