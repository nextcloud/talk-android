/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2017-2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.callnotification

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import autodagger.AutoInjector
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.CallActivity
import com.nextcloud.talk.activities.CallBaseActivity
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.CallNotificationActivityBinding
import com.nextcloud.talk.extensions.loadUserAvatar
import com.nextcloud.talk.jobs.CallPushPayload
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.CapabilitiesUtil
import com.nextcloud.talk.utils.CapabilitiesUtil.hasSpreedFeatureCapability
import com.nextcloud.talk.utils.NotificationUtils
import com.nextcloud.talk.utils.SpreedFeatures
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_CALL_VOICE_ONLY
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_ONE_TO_ONE
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
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

    private var roomToken: String? = null
    private var notificationTimestamp: Int? = null
    private var displayName: String? = null
    private var callFlag: Int = 0
    private var isOneToOneCall: Boolean = true
    private var conversationName: String? = null
    private lateinit var userBeingCalled: User
    private var leavingScreen = false
    private var handler: Handler? = null
    private var binding: CallNotificationActivityBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedApplication!!.componentApplication.inject(this)
        userBeingCalled = setUpBoundUserOrFinish() ?: return
        binding = CallNotificationActivityBinding.inflate(layoutInflater)
        setContentView(binding!!.root)
        hideNavigationIfNoPipAvailable()

        handleExtras()

        showCallData()
        initClickListeners()
        setupNotificationCanceledRoutine()
    }

    private fun handleExtras() {
        val extras = intent.extras!!
        roomToken = extras.getString(KEY_ROOM_TOKEN, "")
        notificationTimestamp = extras.getInt(BundleKeys.KEY_NOTIFICATION_TIMESTAMP)
        displayName = extras.getString(BundleKeys.KEY_CONVERSATION_DISPLAY_NAME, "")
        callFlag = extras.getInt(BundleKeys.KEY_CALL_FLAG)
        isOneToOneCall = extras.getBoolean(KEY_ROOM_ONE_TO_ONE)
        conversationName = extras.getString(BundleKeys.KEY_CONVERSATION_NAME, "")
    }

    private fun showCallData() {
        setupCallTypeDescription()
        setupCallAnswerButtons()
        binding!!.conversationNameTextView.text = displayName
        setupAvatar(isOneToOneCall, conversationName)
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

    private fun callType() = CallPushPayload.callTypeOf(callFlag, hasCallFlagsCapability())

    private fun setupCallTypeDescription() {
        val textId = when (callType()) {
            CallPushPayload.CallType.VIDEO -> R.string.nc_call_video
            CallPushPayload.CallType.VOICE -> R.string.nc_call_voice
            CallPushPayload.CallType.UNKNOWN -> R.string.nc_call_unknown
        }
        binding!!.incomingCallVoiceOrVideoTextView.text = String.format(
            resources.getString(textId),
            resources.getString(R.string.nc_app_product_name)
        )
    }

    private fun setupCallAnswerButtons() {
        val callType = callType()
        binding!!.callAnswerVoiceOnlyView.visibility = if (callType.showsVoiceButton) View.VISIBLE else View.GONE
        binding!!.callAnswerCameraView.visibility = if (callType.showsVideoButton) View.VISIBLE else View.GONE
    }

    private fun setupNotificationCanceledRoutine() {
        val notificationHandler = Handler(Looper.getMainLooper())
        notificationHandler.post(object : Runnable {
            override fun run() {
                val extras = NotificationUtils.getActiveNotificationExtras(context, notificationTimestamp!!)
                if (extras == null) {
                    finish()
                } else {
                    refreshCallData(extras)
                    notificationHandler.postDelayed(this, ONE_SECOND)
                }
            }
        })
    }

    /**
     * The notification is shown again with the data of the server once it answers, but this screen may be open
     * already. The notification is the only carrier of that data, so the screen follows it.
     */
    private fun refreshCallData(notificationExtras: Bundle) {
        if (!CallPushPayload.isCallDataChanged(intent.extras, notificationExtras)) {
            return
        }
        intent.putExtras(CallPushPayload.pickCallData(notificationExtras))
        handleExtras()
        showCallData()
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
        finish()
    }

    private fun proceedToCall() {
        if (CapabilitiesUtil.isCallEndToEndEncryptionEnabled(userBeingCalled.capabilities?.spreedCapability)) {
            Toast.makeText(context, R.string.nc_call_e2ee_not_supported, Toast.LENGTH_LONG).show()
            hangup()
            return
        }
        val callIntent = Intent(this, CallActivity::class.java)
        intent.putExtra(KEY_ROOM_ONE_TO_ONE, isOneToOneCall)
        callIntent.putExtras(intent.extras!!)
        startActivity(callIntent)
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
        super.onDestroy()
    }

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
        binding!!.callNotificationLayout.fitsSystemWindows = false
    }

    companion object {
        private val TAG = CallNotificationActivity::class.simpleName
        const val ONE_SECOND: Long = 1000
    }
}
