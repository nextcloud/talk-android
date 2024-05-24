/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2022 Tim Krüger <t@timkrueger.me>
 * SPDX-FileCopyrightText: 2017-2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.activities

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.text.format.DateUtils
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewGroup
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.widget.AdapterView
import android.widget.FrameLayout
import android.widget.RelativeLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.drawable.DrawableCompat
import androidx.lifecycle.ViewModelProvider
import autodagger.AutoInjector
import com.bluelinelabs.logansquare.LoganSquare
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.nextcloud.talk.R
import com.nextcloud.talk.adapters.ParticipantDisplayItem
import com.nextcloud.talk.adapters.ParticipantsAdapter
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.call.CallParticipant
import com.nextcloud.talk.call.CallParticipantList
import com.nextcloud.talk.call.CallParticipantModel
import com.nextcloud.talk.call.ReactionAnimator
import com.nextcloud.talk.chat.ChatActivity
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.CallActivityBinding
import com.nextcloud.talk.events.ConfigurationChangeEvent
import com.nextcloud.talk.events.NetworkEvent
import com.nextcloud.talk.events.ProximitySensorEvent
import com.nextcloud.talk.events.WebSocketCommunicationEvent
import com.nextcloud.talk.models.ExternalSignalingServer
import com.nextcloud.talk.models.json.capabilities.CapabilitiesOverall
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.conversations.RoomOverall
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.models.json.participants.Participant
import com.nextcloud.talk.models.json.signaling.DataChannelMessage
import com.nextcloud.talk.models.json.signaling.NCSignalingMessage
import com.nextcloud.talk.models.json.signaling.Signaling
import com.nextcloud.talk.models.json.signaling.SignalingOverall
import com.nextcloud.talk.models.json.signaling.settings.SignalingSettingsOverall
import com.nextcloud.talk.raisehand.viewmodel.RaiseHandViewModel
import com.nextcloud.talk.raisehand.viewmodel.RaiseHandViewModel.LoweredHandState
import com.nextcloud.talk.raisehand.viewmodel.RaiseHandViewModel.RaisedHandState
import com.nextcloud.talk.signaling.SignalingMessageReceiver
import com.nextcloud.talk.signaling.SignalingMessageReceiver.CallParticipantMessageListener
import com.nextcloud.talk.signaling.SignalingMessageReceiver.LocalParticipantMessageListener
import com.nextcloud.talk.signaling.SignalingMessageReceiver.OfferMessageListener
import com.nextcloud.talk.signaling.SignalingMessageSender
import com.nextcloud.talk.ui.dialog.AudioOutputDialog
import com.nextcloud.talk.ui.dialog.MoreCallActionsDialog
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.SpreedFeatures
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.NotificationUtils.cancelExistingNotificationsForRoom
import com.nextcloud.talk.utils.NotificationUtils.getCallRingtoneUri
import com.nextcloud.talk.utils.ReceiverFlag
import com.nextcloud.talk.utils.VibrationUtils.vibrateShort
import com.nextcloud.talk.utils.animations.PulseAnimation
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_CALL_VOICE_ONLY
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_CALL_WITHOUT_NOTIFICATION
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_CONVERSATION_NAME
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_CONVERSATION_PASSWORD
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_FROM_NOTIFICATION_START_CALL
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_IS_BREAKOUT_ROOM
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_IS_MODERATOR
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_MODIFIED_BASE_URL
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_PARTICIPANT_PERMISSION_CAN_PUBLISH_AUDIO
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_PARTICIPANT_PERMISSION_CAN_PUBLISH_VIDEO
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_RECORDING_STATE
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_ID
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_START_CALL_AFTER_ROOM_SWITCH
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_SWITCH_TO_ROOM
import com.nextcloud.talk.utils.CapabilitiesUtil
import com.nextcloud.talk.utils.CapabilitiesUtil.hasSpreedFeatureCapability
import com.nextcloud.talk.utils.CapabilitiesUtil.isCallRecordingAvailable
import com.nextcloud.talk.utils.permissions.PlatformPermissionUtil
import com.nextcloud.talk.utils.power.PowerManagerUtils
import com.nextcloud.talk.utils.registerPermissionHandlerBroadcastReceiver
import com.nextcloud.talk.utils.singletons.ApplicationWideCurrentRoomHolder
import com.nextcloud.talk.viewmodels.CallRecordingViewModel
import com.nextcloud.talk.viewmodels.CallRecordingViewModel.RecordingConfirmStopState
import com.nextcloud.talk.viewmodels.CallRecordingViewModel.RecordingErrorState
import com.nextcloud.talk.viewmodels.CallRecordingViewModel.RecordingStartedState
import com.nextcloud.talk.viewmodels.CallRecordingViewModel.RecordingStartingState
import com.nextcloud.talk.webrtc.WebRTCUtils
import com.nextcloud.talk.webrtc.PeerConnectionWrapper
import com.nextcloud.talk.webrtc.PeerConnectionWrapper.PeerConnectionObserver
import com.nextcloud.talk.webrtc.WebRtcAudioManager
import com.nextcloud.talk.webrtc.WebRtcAudioManager.AudioDevice
import com.nextcloud.talk.webrtc.WebSocketConnectionHelper
import com.nextcloud.talk.webrtc.WebSocketInstance
import com.wooplr.spotlight.SpotlightView
import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import okhttp3.Cache
import org.apache.commons.lang3.StringEscapeUtils
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera1Enumerator
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraEnumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.CameraVideoCapturer.CameraSwitchHandler
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.Logging
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnection.IceConnectionState
import org.webrtc.PeerConnectionFactory
import org.webrtc.RendererCommon
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.io.IOException
import java.util.Objects
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt

@AutoInjector(NextcloudTalkApplication::class)
@Suppress("TooManyFunctions")
class CallActivity : CallBaseActivity() {
    @JvmField
    @Inject
    var ncApi: NcApi? = null

    @JvmField
    @Inject
    var userManager: UserManager? = null

    @JvmField
    @Inject
    var cache: Cache? = null

    @JvmField
    @Inject
    var permissionUtil: PlatformPermissionUtil? = null

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    var audioManager: WebRtcAudioManager? = null
    var callRecordingViewModel: CallRecordingViewModel? = null
    var raiseHandViewModel: RaiseHandViewModel? = null
    private var mReceiver: BroadcastReceiver? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var audioConstraints: MediaConstraints? = null
    private var videoConstraints: MediaConstraints? = null
    private var sdpConstraints: MediaConstraints? = null
    private var sdpConstraintsForMCU: MediaConstraints? = null
    private var videoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var videoCapturer: VideoCapturer? = null
    private var rootEglBase: EglBase? = null
    private var signalingDisposable: Disposable? = null
    private var iceServers: MutableList<PeerConnection.IceServer>? = null
    private var cameraEnumerator: CameraEnumerator? = null
    private var roomToken: String? = null
    lateinit var conversationUser: User
    private var conversationName: String? = null
    private var callSession: String? = null
    private var localStream: MediaStream? = null
    private var credentials: String? = null
    private val peerConnectionWrapperList: MutableList<PeerConnectionWrapper> = ArrayList()
    private var videoOn = false
    private var microphoneOn = false
    private var isVoiceOnlyCall = false
    private var isCallWithoutNotification = false
    private var isIncomingCallFromNotification = false
    private val callControlHandler = Handler()
    private val callInfosHandler = Handler()
    private val cameraSwitchHandler = Handler()

    private val callTimeHandler = Handler(Looper.getMainLooper())

    // push to talk
    private var isPushToTalkActive = false
    private var pulseAnimation: PulseAnimation? = null
    private var baseUrl: String? = null
    private var roomId: String? = null
    private var spotlightView: SpotlightView? = null
    private val internalSignalingMessageReceiver = InternalSignalingMessageReceiver()
    private var signalingMessageReceiver: SignalingMessageReceiver? = null
    private val internalSignalingMessageSender = InternalSignalingMessageSender()
    private var signalingMessageSender: SignalingMessageSender? = null
    private val offerAnswerNickProviders: MutableMap<String?, OfferAnswerNickProvider?> = HashMap()
    private val callParticipantMessageListeners: MutableMap<String?, CallParticipantMessageListener> = HashMap()
    private val selfPeerConnectionObserver: PeerConnectionObserver = CallActivitySelfPeerConnectionObserver()
    private var callParticipants: MutableMap<String?, CallParticipant?> = HashMap()
    private val screenParticipantDisplayItemManagers: MutableMap<String?, ScreenParticipantDisplayItemManager> =
        HashMap()
    private val screenParticipantDisplayItemManagersHandler = Handler(Looper.getMainLooper())
    private val callParticipantEventDisplayers: MutableMap<String?, CallParticipantEventDisplayer> = HashMap()
    private val callParticipantEventDisplayersHandler = Handler(Looper.getMainLooper())
    private val callParticipantListObserver: CallParticipantList.Observer = object : CallParticipantList.Observer {
        override fun onCallParticipantsChanged(
            joined: Collection<Participant>,
            updated: Collection<Participant>,
            left: Collection<Participant>,
            unchanged: Collection<Participant>
        ) {
            handleCallParticipantsChanged(joined, updated, left, unchanged)
        }

        override fun onCallEndedForAll() {
            Log.d(TAG, "A moderator ended the call for all.")
            hangup(true)
        }
    }
    private var callParticipantList: CallParticipantList? = null
    private var switchToRoomToken = ""
    private var isBreakoutRoom = false
    private val localParticipantMessageListener = LocalParticipantMessageListener { token ->
        switchToRoomToken = token
        hangup(true)
    }
    private val offerMessageListener = OfferMessageListener { sessionId, roomType, sdp, nick ->
        getOrCreatePeerConnectionWrapperForSessionIdAndType(
            sessionId,
            roomType,
            false
        )
    }
    private var externalSignalingServer: ExternalSignalingServer? = null
    private var webSocketClient: WebSocketInstance? = null
    private var webSocketConnectionHelper: WebSocketConnectionHelper? = null
    private var hasMCU = false
    private var hasExternalSignalingServer = false
    private var conversationPassword: String? = null
    private var powerManagerUtils: PowerManagerUtils? = null
    private var handler: Handler? = null
    private var currentCallStatus: CallStatus? = null
    private var mediaPlayer: MediaPlayer? = null
    private var participantDisplayItems: MutableMap<String, ParticipantDisplayItem?>? = null
    private var participantsAdapter: ParticipantsAdapter? = null
    private var binding: CallActivityBinding? = null
    private var audioOutputDialog: AudioOutputDialog? = null
    private var moreCallActionsDialog: MoreCallActionsDialog? = null
    private var elapsedSeconds: Long = 0

    private var requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionMap: Map<String, Boolean> ->
        val rationaleList: MutableList<String> = ArrayList()
        val audioPermission = permissionMap[Manifest.permission.RECORD_AUDIO]
        if (audioPermission != null) {
            if (java.lang.Boolean.TRUE == audioPermission) {
                if (!microphoneOn) {
                    onMicrophoneClick()
                }
            } else {
                rationaleList.add(resources.getString(R.string.nc_microphone_permission_hint))
            }
        }
        val cameraPermission = permissionMap[Manifest.permission.CAMERA]
        if (cameraPermission != null) {
            if (java.lang.Boolean.TRUE == cameraPermission) {
                if (!videoOn) {
                    onCameraClick()
                }
                if (cameraEnumerator!!.deviceNames.isEmpty()) {
                    binding!!.cameraButton.visibility = View.GONE
                }
                if (cameraEnumerator!!.deviceNames.size > 1) {
                    binding!!.switchSelfVideoButton.visibility = View.VISIBLE
                }
            } else {
                rationaleList.add(resources.getString(R.string.nc_camera_permission_hint))
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val bluetoothPermission = permissionMap[Manifest.permission.BLUETOOTH_CONNECT]
            if (bluetoothPermission != null) {
                if (java.lang.Boolean.TRUE == bluetoothPermission) {
                    enableBluetoothManager()
                } else {
                    // Only ask for bluetooth when already asking to grant microphone or camera access. Asking
                    // for bluetooth solely is not important enough here and would most likely annoy the user.
                    if (rationaleList.isNotEmpty()) {
                        rationaleList.add(resources.getString(R.string.nc_bluetooth_permission_hint))
                    }
                }
            }
        }
        if (rationaleList.isNotEmpty()) {
            showRationaleDialogForSettings(rationaleList)
        }
    }
    private var canPublishAudioStream = false
    private var canPublishVideoStream = false
    private var isModerator = false
    private var reactionAnimator: ReactionAnimator? = null
    private var othersInCall = false

    private lateinit var micInputAudioRecorder: AudioRecord
    private var micInputAudioRecordThread: Thread? = null
    private var isMicInputAudioThreadRunning: Boolean = false
    private val bufferSize = AudioRecord.getMinBufferSize(
        SAMPLE_RATE,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    private var recordingConsentGiven = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate")
        super.onCreate(savedInstanceState)
        sharedApplication!!.componentApplication.inject(this)
        binding = CallActivityBinding.inflate(layoutInflater)
        setContentView(binding!!.root)
        hideNavigationIfNoPipAvailable()
        conversationUser = currentUserProvider.currentUser.blockingGet()
        val extras = intent.extras
        roomId = extras!!.getString(KEY_ROOM_ID, "")
        roomToken = extras.getString(KEY_ROOM_TOKEN, "")
        conversationPassword = extras.getString(KEY_CONVERSATION_PASSWORD, "")
        conversationName = extras.getString(KEY_CONVERSATION_NAME, "")
        isVoiceOnlyCall = extras.getBoolean(KEY_CALL_VOICE_ONLY, false)
        isCallWithoutNotification = extras.getBoolean(KEY_CALL_WITHOUT_NOTIFICATION, false)
        canPublishAudioStream = extras.getBoolean(KEY_PARTICIPANT_PERMISSION_CAN_PUBLISH_AUDIO)
        canPublishVideoStream = extras.getBoolean(KEY_PARTICIPANT_PERMISSION_CAN_PUBLISH_VIDEO)
        isModerator = extras.getBoolean(KEY_IS_MODERATOR, false)
        if (extras.containsKey(KEY_FROM_NOTIFICATION_START_CALL)) {
            isIncomingCallFromNotification = extras.getBoolean(KEY_FROM_NOTIFICATION_START_CALL)
        }
        if (extras.containsKey(KEY_IS_BREAKOUT_ROOM)) {
            isBreakoutRoom = extras.getBoolean(KEY_IS_BREAKOUT_ROOM)
        }
        credentials = ApiUtils.getCredentials(conversationUser!!.username, conversationUser!!.token)
        baseUrl = extras.getString(KEY_MODIFIED_BASE_URL, "")
        if (TextUtils.isEmpty(baseUrl)) {
            baseUrl = conversationUser!!.baseUrl
        }
        powerManagerUtils = PowerManagerUtils()
        if ("resume".equals(extras.getString("state", ""), ignoreCase = true)) {
            setCallState(CallStatus.IN_CONVERSATION)
        } else {
            setCallState(CallStatus.CONNECTING)
        }
        raiseHandViewModel = ViewModelProvider(this, viewModelFactory).get(RaiseHandViewModel::class.java)
        raiseHandViewModel!!.setData(roomToken!!, isBreakoutRoom)
        raiseHandViewModel!!.viewState.observe(this) { viewState: RaiseHandViewModel.ViewState? ->
            var raised = false
            if (viewState is RaisedHandState) {
                binding!!.lowerHandButton.visibility = View.VISIBLE
                raised = true
            } else if (viewState is LoweredHandState) {
                binding!!.lowerHandButton.visibility = View.GONE
                raised = false
            }
            if (isConnectionEstablished) {
                for (peerConnectionWrapper in peerConnectionWrapperList) {
                    peerConnectionWrapper.raiseHand(raised)
                }
            }
        }
        callRecordingViewModel = ViewModelProvider(this, viewModelFactory).get(
            CallRecordingViewModel::class.java
        )
        callRecordingViewModel!!.setData(roomToken!!)
        callRecordingViewModel!!.setRecordingState(extras.getInt(KEY_RECORDING_STATE))
        callRecordingViewModel!!.viewState.observe(this) { viewState: CallRecordingViewModel.ViewState? ->
            if (viewState is RecordingStartedState) {
                binding!!.callRecordingIndicator.setImageResource(R.drawable.record_stop)
                binding!!.callRecordingIndicator.visibility = View.VISIBLE
                if (viewState.showStartedInfo) {
                    vibrateShort(context)
                    Snackbar.make(
                        binding!!.root,
                        context.resources.getString(R.string.record_active_info),
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            } else if (viewState is RecordingStartingState) {
                if (isAllowedToStartOrStopRecording) {
                    binding!!.callRecordingIndicator.setImageResource(R.drawable.record_starting)
                    binding!!.callRecordingIndicator.visibility = View.VISIBLE
                } else {
                    binding!!.callRecordingIndicator.visibility = View.GONE
                }
            } else if (viewState is RecordingConfirmStopState) {
                if (isAllowedToStartOrStopRecording) {
                    val dialogBuilder = MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.record_stop_confirm_title)
                        .setMessage(R.string.record_stop_confirm_message)
                        .setPositiveButton(R.string.record_stop_description) { _: DialogInterface?, _: Int ->
                            callRecordingViewModel!!.stopRecording()
                        }
                        .setNegativeButton(R.string.nc_common_dismiss) { _: DialogInterface?, _: Int ->
                            callRecordingViewModel!!.dismissStopRecording()
                        }
                    viewThemeUtils.dialog.colorMaterialAlertDialogBackground(this, dialogBuilder)
                    val dialog = dialogBuilder.show()
                    viewThemeUtils.platform.colorTextButtons(
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE),
                        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                    )
                } else {
                    Log.e(TAG, "Being in RecordingConfirmStopState as non moderator. This should not happen!")
                }
            } else if (viewState is RecordingErrorState) {
                if (isAllowedToStartOrStopRecording) {
                    Snackbar.make(
                        binding!!.root,
                        context.resources.getString(R.string.record_failed_info),
                        Snackbar.LENGTH_LONG
                    ).show()
                }
                binding!!.callRecordingIndicator.visibility = View.GONE
            } else {
                binding!!.callRecordingIndicator.visibility = View.GONE
            }
        }
        initClickListeners()
        binding!!.microphoneButton.setOnTouchListener(MicrophoneButtonTouchListener())
        pulseAnimation = PulseAnimation.create().with(binding!!.microphoneButton)
            .setDuration(310)
            .setRepeatCount(PulseAnimation.INFINITE)
            .setRepeatMode(PulseAnimation.REVERSE)
        basicInitialization()
        callParticipants = HashMap()
        participantDisplayItems = HashMap()
        initViews()
        updateSelfVideoViewPosition()
        reactionAnimator = ReactionAnimator(context, binding!!.reactionAnimationWrapper, viewThemeUtils)

        checkRecordingConsentAndInitiateCall()
    }

    private fun checkRecordingConsentAndInitiateCall() {
        fun askForRecordingConsent() {
            val materialAlertDialogBuilder = MaterialAlertDialogBuilder(this)
                .setTitle(R.string.recording_consent_title)
                .setMessage(R.string.recording_consent_description)
                .setCancelable(false)
                .setPositiveButton(R.string.nc_yes) { _, _ ->
                    recordingConsentGiven = true
                    initiateCall()
                }
                .setNegativeButton(R.string.nc_no) { _, _ ->
                    recordingConsentGiven = false
                    hangup(true)
                }

            viewThemeUtils.dialog.colorMaterialAlertDialogBackground(this, materialAlertDialogBuilder)
            val dialog = materialAlertDialogBuilder.show()
            viewThemeUtils.platform.colorTextButtons(
                dialog.getButton(AlertDialog.BUTTON_POSITIVE),
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            )
        }

        when (CapabilitiesUtil.getRecordingConsentType(conversationUser!!.capabilities!!.spreedCapability!!)) {
            CapabilitiesUtil.RECORDING_CONSENT_NOT_REQUIRED -> initiateCall()
            CapabilitiesUtil.RECORDING_CONSENT_REQUIRED -> askForRecordingConsent()
            CapabilitiesUtil.RECORDING_CONSENT_DEPEND_ON_CONVERSATION -> {
                val getRoomApiVersion = ApiUtils.getConversationApiVersion(
                    conversationUser!!,
                    intArrayOf(ApiUtils.API_V4, 1)
                )
                ncApi!!.getRoom(credentials, ApiUtils.getUrlForRoom(getRoomApiVersion, baseUrl, roomToken))
                    .retry(API_RETRIES)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(object : Observer<RoomOverall> {
                        override fun onSubscribe(d: Disposable) {
                            // unused atm
                        }

                        override fun onNext(roomOverall: RoomOverall) {
                            val conversation = roomOverall.ocs!!.data
                            if (conversation?.recordingConsentRequired == 1) {
                                askForRecordingConsent()
                            } else {
                                initiateCall()
                            }
                        }

                        override fun onError(e: Throwable) {
                            Log.e(TAG, "Failed to get room", e)
                            Snackbar.make(binding!!.root, R.string.nc_common_error_sorry, Snackbar.LENGTH_LONG).show()
                        }

                        override fun onComplete() {
                            // unused atm
                        }
                    })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasSpreedFeatureCapability(
                conversationUser.capabilities!!.spreedCapability!!,
                SpreedFeatures.RECORDING_V1
            ) &&
            othersInCall &&
            elapsedSeconds.toInt() >= CALL_TIME_ONE_HOUR
        ) {
            showCallRunningSinceOneHourOrMoreInfo()
        }
    }

    fun sendReaction(emoji: String?) {
        addReactionForAnimation(emoji, conversationUser!!.displayName)
        if (isConnectionEstablished) {
            for (peerConnectionWrapper in peerConnectionWrapperList) {
                peerConnectionWrapper.sendReaction(emoji)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        active = true
        initFeaturesVisibility()
        try {
            cache!!.evictAll()
        } catch (e: IOException) {
            Log.e(TAG, "Failed to evict cache")
        }
    }

    override fun onStop() {
        super.onStop()
        active = false

        if (isMicInputAudioThreadRunning) {
            stopMicInputDetection()
        }
    }

    private fun stopMicInputDetection() {
        if (micInputAudioRecordThread != null) {
            micInputAudioRecorder.stop()
            micInputAudioRecorder.release()
            isMicInputAudioThreadRunning = false
            micInputAudioRecordThread = null
        }
    }

    private fun enableBluetoothManager() {
        if (audioManager != null) {
            audioManager!!.startBluetoothManager()
        }
    }

    private fun initFeaturesVisibility() {
        if (isAllowedToStartOrStopRecording || isAllowedToRaiseHand) {
            binding!!.moreCallActions.visibility = View.VISIBLE
        } else {
            binding!!.moreCallActions.visibility = View.GONE
        }
    }

    private fun initClickListeners() {
        binding!!.pictureInPictureButton.setOnClickListener { enterPipMode() }

        binding!!.audioOutputButton.setOnClickListener {
            audioOutputDialog = AudioOutputDialog(this)
            audioOutputDialog!!.show()
        }

        binding!!.moreCallActions.setOnClickListener {
            moreCallActionsDialog = MoreCallActionsDialog(this)
            moreCallActionsDialog!!.show()
        }

        if (canPublishAudioStream) {
            binding!!.microphoneButton.setOnClickListener { onMicrophoneClick() }
            binding!!.microphoneButton.setOnLongClickListener {
                if (!microphoneOn) {
                    callControlHandler.removeCallbacksAndMessages(null)
                    callInfosHandler.removeCallbacksAndMessages(null)
                    cameraSwitchHandler.removeCallbacksAndMessages(null)
                    isPushToTalkActive = true
                    binding!!.callControls.visibility = View.VISIBLE
                    if (!isVoiceOnlyCall) {
                        binding!!.switchSelfVideoButton.visibility = View.VISIBLE
                    }
                }
                onMicrophoneClick()
                true
            }
        } else {
            binding!!.microphoneButton.setOnClickListener {
                Snackbar.make(
                    binding!!.root,
                    R.string.nc_not_allowed_to_activate_audio,
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }

        if (canPublishVideoStream) {
            binding!!.cameraButton.setOnClickListener { onCameraClick() }
        } else {
            binding!!.cameraButton.setOnClickListener {
                Snackbar.make(
                    binding!!.root,
                    R.string.nc_not_allowed_to_activate_video,
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }
        binding!!.hangupButton.setOnClickListener { hangup(true) }
        binding!!.switchSelfVideoButton.setOnClickListener { switchCamera() }
        binding!!.gridview.onItemClickListener =
            AdapterView.OnItemClickListener { _: AdapterView<*>?, _: View?, _: Int, _: Long ->
                animateCallControls(
                    true,
                    0
                )
            }
        binding!!.callStates.callStateRelativeLayout.setOnClickListener {
            if (currentCallStatus === CallStatus.CALLING_TIMEOUT) {
                setCallState(CallStatus.RECONNECTING)
                hangupNetworkCalls(false)
            }
        }
        binding!!.callRecordingIndicator.setOnClickListener {
            if (isAllowedToStartOrStopRecording) {
                if (callRecordingViewModel!!.viewState.value is RecordingStartingState) {
                    if (moreCallActionsDialog == null) {
                        moreCallActionsDialog = MoreCallActionsDialog(this)
                    }
                    moreCallActionsDialog!!.show()
                } else {
                    callRecordingViewModel!!.clickRecordButton()
                }
            } else {
                Snackbar.make(
                    binding!!.root,
                    context.resources.getString(R.string.record_active_info),
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
        binding!!.lowerHandButton.setOnClickListener { l: View? -> raiseHandViewModel!!.lowerHand() }
    }

    private fun createCameraEnumerator() {
        var camera2EnumeratorIsSupported = false
        try {
            camera2EnumeratorIsSupported = Camera2Enumerator.isSupported(this)
        } catch (t: Throwable) {
            Log.w(TAG, "Camera2Enumerator threw an error", t)
        }
        cameraEnumerator = if (camera2EnumeratorIsSupported) {
            Camera2Enumerator(this)
        } else {
            Camera1Enumerator(WebRTCUtils.shouldEnableVideoHardwareAcceleration())
        }
    }

    private fun basicInitialization() {
        rootEglBase = EglBase.create()
        createCameraEnumerator()

        // Create a new PeerConnectionFactory instance.
        val options = PeerConnectionFactory.Options()
        val defaultVideoEncoderFactory = DefaultVideoEncoderFactory(
            rootEglBase!!.eglBaseContext,
            true,
            true
        )
        val defaultVideoDecoderFactory = DefaultVideoDecoderFactory(
            rootEglBase!!.eglBaseContext
        )
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setVideoEncoderFactory(defaultVideoEncoderFactory)
            .setVideoDecoderFactory(defaultVideoDecoderFactory)
            .createPeerConnectionFactory()

        // Create MediaConstraints - Will be useful for specifying video and audio constraints.
        audioConstraints = MediaConstraints()
        videoConstraints = MediaConstraints()
        localStream = peerConnectionFactory!!.createLocalMediaStream("NCMS")

        // Create and audio manager that will take care of audio routing,
        // audio modes, audio device enumeration etc.
        audioManager = WebRtcAudioManager.create(applicationContext, isVoiceOnlyCall)
        // Store existing audio settings and change audio mode to
        // MODE_IN_COMMUNICATION for best possible VoIP performance.
        Log.d(TAG, "Starting the audio manager...")
        audioManager!!.start { currentDevice: AudioDevice, availableDevices: Set<AudioDevice> ->
            onAudioManagerDevicesChanged(
                currentDevice,
                availableDevices
            )
        }
        if (isVoiceOnlyCall) {
            setAudioOutputChannel(AudioDevice.EARPIECE)
        } else {
            setAudioOutputChannel(AudioDevice.SPEAKER_PHONE)
        }
        iceServers = ArrayList()

        // create sdpConstraints
        sdpConstraints = MediaConstraints()
        sdpConstraintsForMCU = MediaConstraints()
        sdpConstraints!!.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        var offerToReceiveVideoString = "true"
        if (isVoiceOnlyCall) {
            offerToReceiveVideoString = "false"
        }
        sdpConstraints!!.mandatory.add(
            MediaConstraints.KeyValuePair("OfferToReceiveVideo", offerToReceiveVideoString)
        )
        sdpConstraintsForMCU!!.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
        sdpConstraintsForMCU!!.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        sdpConstraintsForMCU!!.optional.add(MediaConstraints.KeyValuePair("internalSctpDataChannels", "true"))
        sdpConstraintsForMCU!!.optional.add(MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"))
        sdpConstraints!!.optional.add(MediaConstraints.KeyValuePair("internalSctpDataChannels", "true"))
        sdpConstraints!!.optional.add(MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"))
        if (!isVoiceOnlyCall) {
            cameraInitialization()
        }
        microphoneInitialization()
    }

    fun setAudioOutputChannel(selectedAudioDevice: AudioDevice?) {
        if (audioManager != null) {
            audioManager!!.selectAudioDevice(selectedAudioDevice)
            updateAudioOutputButton(audioManager!!.currentAudioDevice)
        }
    }

    private fun updateAudioOutputButton(activeAudioDevice: AudioDevice) {
        when (activeAudioDevice) {
            AudioDevice.BLUETOOTH -> binding!!.audioOutputButton.setImageResource(
                R.drawable.ic_baseline_bluetooth_audio_24
            )

            AudioDevice.SPEAKER_PHONE -> binding!!.audioOutputButton.setImageResource(
                R.drawable.ic_volume_up_white_24dp
            )

            AudioDevice.EARPIECE -> binding!!.audioOutputButton.setImageResource(
                R.drawable.ic_baseline_phone_in_talk_24
            )

            AudioDevice.WIRED_HEADSET -> binding!!.audioOutputButton.setImageResource(
                R.drawable.ic_baseline_headset_mic_24
            )

            else -> Log.e(TAG, "Icon for audio output not available")
        }
        DrawableCompat.setTint(binding!!.audioOutputButton.drawable, Color.WHITE)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initViews() {
        Log.d(TAG, "initViews")
        binding!!.callInfosLinearLayout.visibility = View.VISIBLE
        binding!!.selfVideoViewWrapper.visibility = View.VISIBLE
        if (!isPipModePossible) {
            binding!!.pictureInPictureButton.visibility = View.GONE
        }
        if (isVoiceOnlyCall) {
            binding!!.switchSelfVideoButton.visibility = View.GONE
            binding!!.cameraButton.visibility = View.GONE
            binding!!.selfVideoRenderer.visibility = View.GONE
            val params = RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.addRule(RelativeLayout.BELOW, R.id.callInfosLinearLayout)
            val callControlsHeight =
                applicationContext.resources.getDimension(R.dimen.call_controls_height).roundToInt()
            params.setMargins(0, 0, 0, callControlsHeight)
            binding!!.gridview.layoutParams = params
        } else {
            val params = RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 0, 0, 0)
            binding!!.gridview.layoutParams = params
            if (cameraEnumerator!!.deviceNames.size < 2) {
                binding!!.switchSelfVideoButton.visibility = View.GONE
            }
            initSelfVideoView()
        }
        binding!!.gridview.setOnTouchListener { _, me ->
            val action = me.actionMasked
            if (action == MotionEvent.ACTION_DOWN) {
                animateCallControls(true, 0)
            }
            false
        }
        binding!!.conversationRelativeLayout.setOnTouchListener { _, me ->
            val action = me.actionMasked
            if (action == MotionEvent.ACTION_DOWN) {
                animateCallControls(true, 0)
            }
            false
        }
        animateCallControls(true, 0)
        initGridAdapter()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initSelfVideoView() {
        try {
            binding!!.selfVideoRenderer.init(rootEglBase!!.eglBaseContext, null)
        } catch (e: IllegalStateException) {
            Log.d(TAG, "selfVideoRenderer already initialized", e)
        }
        binding!!.selfVideoRenderer.setZOrderMediaOverlay(true)
        // disabled because it causes some devices to crash
        binding!!.selfVideoRenderer.setEnableHardwareScaler(false)
        binding!!.selfVideoRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
        binding!!.selfVideoRenderer.setOnTouchListener(SelfVideoTouchListener())
    }

    private fun initGridAdapter() {
        Log.d(TAG, "initGridAdapter")
        val columns: Int
        val participantsInGrid = participantDisplayItems!!.size
        columns = if (resources != null &&
            resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        ) {
            if (participantsInGrid > 2) {
                2
            } else {
                1
            }
        } else {
            if (participantsInGrid > 2) {
                3
            } else if (participantsInGrid > 1) {
                2
            } else {
                1
            }
        }
        binding!!.gridview.numColumns = columns
        binding!!.conversationRelativeLayout
            .viewTreeObserver
            .addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    binding!!.conversationRelativeLayout.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    val height = binding!!.conversationRelativeLayout.measuredHeight
                    binding!!.gridview.minimumHeight = height
                }
            })
        binding!!.callInfosLinearLayout
            .viewTreeObserver
            .addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    binding!!.callInfosLinearLayout.viewTreeObserver.removeOnGlobalLayoutListener(this)
                }
            })
        if (participantsAdapter != null) {
            participantsAdapter!!.destroy()
        }
        participantsAdapter = ParticipantsAdapter(
            this,
            participantDisplayItems,
            binding!!.conversationRelativeLayout,
            binding!!.callInfosLinearLayout,
            columns,
            isVoiceOnlyCall
        )
        binding!!.gridview.adapter = participantsAdapter
        if (isInPipMode) {
            updateUiForPipMode()
        }
    }

    private fun checkDevicePermissions() {
        val permissionsToRequest: MutableList<String> = ArrayList()
        val rationaleList: MutableList<String> = ArrayList()
        if (permissionUtil!!.isMicrophonePermissionGranted()) {
            if (!microphoneOn) {
                onMicrophoneClick()
            }
        } else if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
            rationaleList.add(resources.getString(R.string.nc_microphone_permission_hint))
        } else {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }

        if (!isVoiceOnlyCall) {
            if (permissionUtil!!.isCameraPermissionGranted()) {
                if (!videoOn) {
                    onCameraClick()
                }
                if (cameraEnumerator!!.deviceNames.isEmpty()) {
                    binding!!.cameraButton.visibility = View.GONE
                }
                if (cameraEnumerator!!.deviceNames.size > 1) {
                    binding!!.switchSelfVideoButton.visibility = View.VISIBLE
                }
            } else if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                permissionsToRequest.add(Manifest.permission.CAMERA)
                rationaleList.add(resources.getString(R.string.nc_camera_permission_hint))
            } else {
                permissionsToRequest.add(Manifest.permission.CAMERA)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (permissionUtil!!.isBluetoothPermissionGranted()) {
                enableBluetoothManager()
            } else if (shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_CONNECT)) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
                rationaleList.add(resources.getString(R.string.nc_bluetooth_permission_hint))
            } else {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
        if (permissionsToRequest.isNotEmpty()) {
            if (rationaleList.isNotEmpty()) {
                showRationaleDialog(permissionsToRequest, rationaleList)
            } else {
                requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
            }
        }

        if (!isConnectionEstablished) {
            fetchSignalingSettings()
        }
    }

    private fun showRationaleDialog(permissionToRequest: String, rationale: String) {
        val rationaleList: MutableList<String> = ArrayList()
        val permissionsToRequest: MutableList<String> = ArrayList()
        rationaleList.add(rationale)
        permissionsToRequest.add(permissionToRequest)
        showRationaleDialog(permissionsToRequest, rationaleList)
    }

    private fun showRationaleDialog(permissionsToRequest: List<String>, rationaleList: List<String>) {
        val rationalesWithLineBreaks = StringBuilder()
        for (rationale in rationaleList) {
            rationalesWithLineBreaks.append(rationale).append("\n\n")
        }
        val dialogBuilder = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.nc_permissions_rationale_dialog_title)
            .setMessage(rationalesWithLineBreaks)
            .setPositiveButton(R.string.nc_permissions_ask) { _, _ ->
                requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
            }
            .setNegativeButton(R.string.nc_common_dismiss, null)
        viewThemeUtils.dialog.colorMaterialAlertDialogBackground(this, dialogBuilder)
        dialogBuilder.show()
    }

    private fun showRationaleDialogForSettings(rationaleList: List<String>) {
        val rationalesWithLineBreaks = StringBuilder()
        rationalesWithLineBreaks.append(resources.getString(R.string.nc_permissions_denied))
        rationalesWithLineBreaks.append('\n')
        rationalesWithLineBreaks.append(resources.getString(R.string.nc_permissions_settings_hint))
        rationalesWithLineBreaks.append("\n\n")
        for (rationale in rationaleList) {
            rationalesWithLineBreaks.append(rationale).append("\n\n")
        }
        val dialogBuilder = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.nc_permissions_rationale_dialog_title)
            .setMessage(rationalesWithLineBreaks)
            .setPositiveButton(R.string.nc_permissions_settings) { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.fromParts("package", packageName, null)
                startActivity(intent)
            }
            .setNegativeButton(R.string.nc_common_dismiss, null)
        viewThemeUtils.dialog.colorMaterialAlertDialogBackground(this, dialogBuilder)
        dialogBuilder.show()
    }

    private val isConnectionEstablished: Boolean
        get() = currentCallStatus === CallStatus.JOINED || currentCallStatus === CallStatus.IN_CONVERSATION

    private fun onAudioManagerDevicesChanged(currentDevice: AudioDevice, availableDevices: Set<AudioDevice>) {
        Log.d(TAG, "onAudioManagerDevicesChanged: $availableDevices, currentDevice: $currentDevice")
        val shouldDisableProximityLock =
            currentDevice == AudioDevice.WIRED_HEADSET ||
                currentDevice == AudioDevice.SPEAKER_PHONE ||
                currentDevice == AudioDevice.BLUETOOTH
        if (shouldDisableProximityLock) {
            powerManagerUtils!!.updatePhoneState(PowerManagerUtils.PhoneState.WITHOUT_PROXIMITY_SENSOR_LOCK)
        } else {
            powerManagerUtils!!.updatePhoneState(PowerManagerUtils.PhoneState.WITH_PROXIMITY_SENSOR_LOCK)
        }
        if (audioOutputDialog != null) {
            audioOutputDialog!!.updateOutputDeviceList()
        }
        updateAudioOutputButton(currentDevice)
    }

    private fun cameraInitialization() {
        videoCapturer = createCameraCapturer(cameraEnumerator)

        // Create a VideoSource instance
        if (videoCapturer != null) {
            val surfaceTextureHelper = SurfaceTextureHelper.create(
                "CaptureThread",
                rootEglBase!!.eglBaseContext
            )
            videoSource = peerConnectionFactory!!.createVideoSource(false)
            videoCapturer!!.initialize(surfaceTextureHelper, applicationContext, videoSource!!.capturerObserver)
        }
        localVideoTrack = peerConnectionFactory!!.createVideoTrack("NCv0", videoSource)
        localStream!!.addTrack(localVideoTrack)
        localVideoTrack!!.setEnabled(false)
        localVideoTrack!!.addSink(binding!!.selfVideoRenderer)
    }

    private fun microphoneInitialization() {
        startMicInputDetection()

        // create an AudioSource instance
        audioSource = peerConnectionFactory!!.createAudioSource(audioConstraints)
        localAudioTrack = peerConnectionFactory!!.createAudioTrack("NCa0", audioSource)
        localAudioTrack!!.setEnabled(false)
        localStream!!.addTrack(localAudioTrack)
    }

    @SuppressLint("MissingPermission")
    private fun startMicInputDetection() {
        if (permissionUtil!!.isMicrophonePermissionGranted() && micInputAudioRecordThread == null) {
            var isSpeakingLongTerm = false
            micInputAudioRecorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            isMicInputAudioThreadRunning = true
            micInputAudioRecorder.startRecording()
            micInputAudioRecordThread = Thread(
                Runnable {
                    while (isMicInputAudioThreadRunning) {
                        val byteArr = ByteArray(bufferSize / 2)
                        micInputAudioRecorder.read(byteArr, 0, byteArr.size)
                        val isCurrentlySpeaking = abs(byteArr[0].toDouble()) > MICROPHONE_VALUE_THRESHOLD

                        if (microphoneOn && isCurrentlySpeaking && !isSpeakingLongTerm) {
                            isSpeakingLongTerm = true
                            sendIsSpeakingMessage(true)
                        } else if (!isCurrentlySpeaking && isSpeakingLongTerm) {
                            isSpeakingLongTerm = false
                            sendIsSpeakingMessage(false)
                        }
                        Thread.sleep(MICROPHONE_VALUE_SLEEP)
                    }
                }
            )
            micInputAudioRecordThread!!.start()
        }
    }

    @Suppress("Detekt.NestedBlockDepth")
    private fun sendIsSpeakingMessage(isSpeaking: Boolean) {
        val isSpeakingMessage: String =
            if (isSpeaking) SIGNALING_MESSAGE_SPEAKING_STARTED else SIGNALING_MESSAGE_SPEAKING_STOPPED

        if (isConnectionEstablished && othersInCall) {
            if (!hasMCU) {
                for (peerConnectionWrapper in peerConnectionWrapperList) {
                    peerConnectionWrapper.sendChannelData(DataChannelMessage(isSpeakingMessage))
                }
            } else {
                for (peerConnectionWrapper in peerConnectionWrapperList) {
                    if (peerConnectionWrapper.sessionId == webSocketClient!!.sessionId) {
                        peerConnectionWrapper.sendChannelData(DataChannelMessage(isSpeakingMessage))
                        break
                    }
                }
            }
        }
    }

    private fun createCameraCapturer(enumerator: CameraEnumerator?): VideoCapturer? {
        val deviceNames = enumerator!!.deviceNames

        // First, try to find front facing camera
        Logging.d(TAG, "Looking for front facing cameras.")
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating front facing camera capturer.")
                val videoCapturer: VideoCapturer? = enumerator.createCapturer(deviceName, null)
                if (videoCapturer != null) {
                    binding!!.selfVideoRenderer.setMirror(true)
                    return videoCapturer
                }
            }
        }

        // Front facing camera not found, try something else
        Logging.d(TAG, "Looking for other cameras.")
        for (deviceName in deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating other camera capturer.")
                val videoCapturer: VideoCapturer? = enumerator.createCapturer(deviceName, null)
                if (videoCapturer != null) {
                    binding!!.selfVideoRenderer.setMirror(false)
                    return videoCapturer
                }
            }
        }
        return null
    }

    fun onMicrophoneClick() {
        if (!canPublishAudioStream) {
            microphoneOn = false
            binding!!.microphoneButton.setImageResource(R.drawable.ic_mic_off_white_24px)
            toggleMedia(false, false)
        }
        if (!canPublishAudioStream) {
            // In the case no audio stream will be published it's not needed to check microphone permissions
            return
        }
        if (permissionUtil!!.isMicrophonePermissionGranted()) {
            if (!appPreferences.pushToTalkIntroShown) {
                spotlightView = getSpotlightView()
                appPreferences.pushToTalkIntroShown = true
            }
            if (!isPushToTalkActive) {
                microphoneOn = !microphoneOn
                if (microphoneOn) {
                    binding!!.microphoneButton.setImageResource(R.drawable.ic_mic_white_24px)
                    updatePictureInPictureActions(
                        R.drawable.ic_mic_white_24px,
                        resources.getString(R.string.nc_pip_microphone_mute),
                        MICROPHONE_PIP_REQUEST_MUTE
                    )
                } else {
                    binding!!.microphoneButton.setImageResource(R.drawable.ic_mic_off_white_24px)
                    updatePictureInPictureActions(
                        R.drawable.ic_mic_off_white_24px,
                        resources.getString(R.string.nc_pip_microphone_unmute),
                        MICROPHONE_PIP_REQUEST_UNMUTE
                    )
                }
                toggleMedia(microphoneOn, false)
            } else {
                binding!!.microphoneButton.setImageResource(R.drawable.ic_mic_white_24px)
                pulseAnimation!!.start()
                toggleMedia(true, false)
            }
        } else if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            showRationaleDialog(
                Manifest.permission.RECORD_AUDIO,
                resources.getString(R.string.nc_microphone_permission_hint)
            )
        } else {
            requestPermissionLauncher.launch(PERMISSIONS_MICROPHONE)
        }
    }

    private fun getSpotlightView(): SpotlightView? {
        val builder = SpotlightView.Builder(this)
            .introAnimationDuration(300)
            .enableRevealAnimation(true)
            .performClick(false)
            .fadeinTextDuration(400)
            .headingTvSize(20)
            .headingTvText(resources.getString(R.string.nc_push_to_talk))
            .subHeadingTvColor(resources.getColor(R.color.bg_default, null))
            .subHeadingTvSize(16)
            .subHeadingTvText(resources.getString(R.string.nc_push_to_talk_desc))
            .maskColor(Color.parseColor("#dc000000"))
            .target(binding!!.microphoneButton)
            .lineAnimDuration(400)
            .enableDismissAfterShown(true)
            .dismissOnBackPress(true)
            .usageId("pushToTalk")

        return viewThemeUtils.talk.themeSpotlightView(context, builder).show()
    }

    private fun onCameraClick() {
        if (!canPublishVideoStream) {
            videoOn = false
            binding!!.cameraButton.setImageResource(R.drawable.ic_videocam_off_white_24px)
            binding!!.switchSelfVideoButton.visibility = View.GONE
            return
        }
        if (permissionUtil!!.isCameraPermissionGranted()) {
            videoOn = !videoOn
            if (videoOn) {
                binding!!.cameraButton.setImageResource(R.drawable.ic_videocam_white_24px)
                if (cameraEnumerator!!.deviceNames.size > 1) {
                    binding!!.switchSelfVideoButton.visibility = View.VISIBLE
                }
            } else {
                binding!!.cameraButton.setImageResource(R.drawable.ic_videocam_off_white_24px)
                binding!!.switchSelfVideoButton.visibility = View.GONE
            }
            toggleMedia(videoOn, true)
        } else if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            showRationaleDialog(
                Manifest.permission.CAMERA,
                resources.getString(R.string.nc_camera_permission_hint)
            )
        } else {
            requestPermissionLauncher.launch(PERMISSIONS_CAMERA)
        }
    }

    fun switchCamera() {
        val cameraVideoCapturer = videoCapturer as CameraVideoCapturer?
        cameraVideoCapturer?.switchCamera(object : CameraSwitchHandler {
            override fun onCameraSwitchDone(currentCameraIsFront: Boolean) {
                binding!!.selfVideoRenderer.setMirror(currentCameraIsFront)
            }

            override fun onCameraSwitchError(s: String) {
                Log.e(TAG, "Error while switching camera: $s")
            }
        })
    }

    private fun toggleMedia(enable: Boolean, video: Boolean) {
        var message: String
        if (video) {
            message = SIGNALING_MESSAGE_VIDEO_OFF
            if (enable) {
                binding!!.cameraButton.alpha = OPACITY_ENABLED
                message = SIGNALING_MESSAGE_VIDEO_ON
                startVideoCapture()
            } else {
                binding!!.cameraButton.alpha = OPACITY_DISABLED
                if (videoCapturer != null) {
                    try {
                        videoCapturer!!.stopCapture()
                    } catch (e: InterruptedException) {
                        Log.d(TAG, "Failed to stop capturing video while sensor is near the ear")
                    }
                }
            }
            if (localStream != null && localStream!!.videoTracks.size > 0) {
                localStream!!.videoTracks[0].setEnabled(enable)
            }
            if (enable) {
                binding!!.selfVideoRenderer.visibility = View.VISIBLE
            } else {
                binding!!.selfVideoRenderer.visibility = View.INVISIBLE
            }
        } else {
            message = SIGNALING_MESSAGE_AUDIO_OFF
            if (enable) {
                message = SIGNALING_MESSAGE_AUDIO_ON
                binding!!.microphoneButton.alpha = OPACITY_ENABLED
            } else {
                binding!!.microphoneButton.alpha = OPACITY_DISABLED
            }
            if (localStream != null && localStream!!.audioTracks.size > 0) {
                localStream!!.audioTracks[0].setEnabled(enable)
            }
        }
        if (isConnectionEstablished) {
            if (!hasMCU) {
                for (peerConnectionWrapper in peerConnectionWrapperList) {
                    peerConnectionWrapper.sendChannelData(DataChannelMessage(message))
                }
            } else {
                for (peerConnectionWrapper in peerConnectionWrapperList) {
                    if (peerConnectionWrapper.sessionId == webSocketClient!!.sessionId) {
                        peerConnectionWrapper.sendChannelData(DataChannelMessage(message))
                        break
                    }
                }
            }
        }
    }

    fun clickRaiseOrLowerHandButton() {
        raiseHandViewModel!!.clickHandButton()
    }

    private fun animateCallControls(show: Boolean, startDelay: Long) {
        if (isVoiceOnlyCall) {
            if (spotlightView != null && spotlightView!!.visibility != View.GONE) {
                spotlightView!!.visibility = View.GONE
            }
        } else if (!isPushToTalkActive) {
            val alpha: Float
            val duration: Long
            if (show) {
                callControlHandler.removeCallbacksAndMessages(null)
                callInfosHandler.removeCallbacksAndMessages(null)
                cameraSwitchHandler.removeCallbacksAndMessages(null)
                alpha = OPACITY_ENABLED
                duration = SECOND_IN_MILLIES
                if (binding!!.callControls.visibility != View.VISIBLE) {
                    binding!!.callControls.alpha = OPACITY_INVISIBLE
                    binding!!.callControls.visibility = View.VISIBLE
                    binding!!.callInfosLinearLayout.alpha = OPACITY_INVISIBLE
                    binding!!.callInfosLinearLayout.visibility = View.VISIBLE
                    binding!!.switchSelfVideoButton.alpha = OPACITY_INVISIBLE
                    if (videoOn) {
                        binding!!.switchSelfVideoButton.visibility = View.VISIBLE
                    }
                } else {
                    callControlHandler.postDelayed({ animateCallControls(false, 0) }, 5000)
                    return
                }
            } else {
                alpha = OPACITY_INVISIBLE
                duration = SECOND_IN_MILLIES
            }
            binding!!.callControls.isEnabled = false
            binding!!.callControls.animate()
                .translationY(0f)
                .alpha(alpha)
                .setDuration(duration)
                .setStartDelay(startDelay)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        super.onAnimationEnd(animation)
                        if (!show) {
                            binding!!.callControls.visibility = View.GONE
                            if (spotlightView != null && spotlightView!!.visibility != View.GONE) {
                                spotlightView!!.visibility = View.GONE
                            }
                        } else {
                            callControlHandler.postDelayed({
                                if (!isPushToTalkActive) {
                                    animateCallControls(false, 0)
                                }
                            }, 7500)
                        }
                        binding!!.callControls.isEnabled = true
                    }
                })
            binding!!.callInfosLinearLayout.isEnabled = false
            binding!!.callInfosLinearLayout.animate()
                .translationY(0f)
                .alpha(alpha)
                .setDuration(duration)
                .setStartDelay(startDelay)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        super.onAnimationEnd(animation)
                        if (!show) {
                            binding!!.callInfosLinearLayout.visibility = View.GONE
                        } else {
                            callInfosHandler.postDelayed({
                                if (!isPushToTalkActive) {
                                    animateCallControls(false, 0)
                                }
                            }, 7500)
                        }
                        binding!!.callInfosLinearLayout.isEnabled = true
                    }
                })
            binding!!.switchSelfVideoButton.isEnabled = false
            binding!!.switchSelfVideoButton.animate()
                .translationY(0f)
                .alpha(alpha)
                .setDuration(duration)
                .setStartDelay(startDelay)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        super.onAnimationEnd(animation)
                        if (!show) {
                            binding!!.switchSelfVideoButton.visibility = View.GONE
                        }
                        binding!!.switchSelfVideoButton.isEnabled = true
                    }
                })
        }
    }

    public override fun onDestroy() {
        if (signalingMessageReceiver != null) {
            signalingMessageReceiver!!.removeListener(localParticipantMessageListener)
            signalingMessageReceiver!!.removeListener(offerMessageListener)
        }
        if (localStream != null) {
            localStream!!.dispose()
            localStream = null
            Log.d(TAG, "Disposed localStream")
        } else {
            Log.d(TAG, "localStream is null")
        }
        if (currentCallStatus !== CallStatus.LEAVING) {
            hangup(true)
        }
        powerManagerUtils!!.updatePhoneState(PowerManagerUtils.PhoneState.IDLE)
        super.onDestroy()
    }

    private fun fetchSignalingSettings() {
        Log.d(TAG, "fetchSignalingSettings")
        val apiVersion = ApiUtils.getSignalingApiVersion(conversationUser, intArrayOf(ApiUtils.API_V3, 2, 1))
        ncApi!!.getSignalingSettings(credentials, ApiUtils.getUrlForSignalingSettings(apiVersion, baseUrl))
            .subscribeOn(Schedulers.io())
            .retry(API_RETRIES)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : Observer<SignalingSettingsOverall> {
                override fun onSubscribe(d: Disposable) {
                    // unused atm
                }

                override fun onNext(signalingSettingsOverall: SignalingSettingsOverall) {
                    if (signalingSettingsOverall.ocs != null &&
                        signalingSettingsOverall.ocs!!.settings != null
                    ) {
                        externalSignalingServer = ExternalSignalingServer()
                        if (!TextUtils.isEmpty(signalingSettingsOverall.ocs!!.settings!!.externalSignalingServer) &&
                            !TextUtils.isEmpty(signalingSettingsOverall.ocs!!.settings!!.externalSignalingTicket)
                        ) {
                            externalSignalingServer = ExternalSignalingServer()
                            externalSignalingServer!!.externalSignalingServer =
                                signalingSettingsOverall.ocs!!.settings!!.externalSignalingServer
                            externalSignalingServer!!.externalSignalingTicket =
                                signalingSettingsOverall.ocs!!.settings!!.externalSignalingTicket
                            hasExternalSignalingServer = true
                        } else {
                            hasExternalSignalingServer = false
                        }
                        Log.d(TAG, "   hasExternalSignalingServer: $hasExternalSignalingServer")

                        if ("?" != conversationUser!!.userId && conversationUser!!.id != null) {
                            Log.d(
                                TAG,
                                "Update externalSignalingServer for: " + conversationUser!!.id +
                                    " / " + conversationUser!!.userId
                            )
                            userManager!!.updateExternalSignalingServer(
                                conversationUser!!.id!!,
                                externalSignalingServer!!
                            )
                                .subscribeOn(Schedulers.io())
                                .subscribe()
                        } else {
                            conversationUser!!.externalSignalingServer = externalSignalingServer
                        }

                        addIceServers(signalingSettingsOverall, apiVersion)
                    }
                    checkCapabilities()
                }

                override fun onError(e: Throwable) {
                    Log.e(TAG, e.message, e)
                }

                override fun onComplete() {
                    // unused atm
                }
            })
    }

    private fun addIceServers(signalingSettingsOverall: SignalingSettingsOverall, apiVersion: Int) {
        if (signalingSettingsOverall.ocs!!.settings!!.stunServers != null) {
            val stunServers = signalingSettingsOverall.ocs!!.settings!!.stunServers
            if (apiVersion == ApiUtils.API_V3) {
                for ((_, urls) in stunServers!!) {
                    if (urls != null) {
                        for (url in urls) {
                            Log.d(TAG, "   STUN server url: $url")
                            iceServers!!.add(PeerConnection.IceServer(url))
                        }
                    }
                }
            } else {
                if (signalingSettingsOverall.ocs!!.settings!!.stunServers != null) {
                    for ((url) in stunServers!!) {
                        Log.d(TAG, "   STUN server url: $url")
                        iceServers!!.add(PeerConnection.IceServer(url))
                    }
                }
            }
        }

        if (signalingSettingsOverall.ocs!!.settings!!.turnServers != null) {
            val turnServers = signalingSettingsOverall.ocs!!.settings!!.turnServers
            for ((_, urls, username, credential) in turnServers!!) {
                if (urls != null) {
                    for (url in urls) {
                        Log.d(TAG, "   TURN server url: $url")
                        iceServers!!.add(PeerConnection.IceServer(url, username, credential))
                    }
                }
            }
        }
    }

    private fun checkCapabilities() {
        ncApi!!.getCapabilities(credentials, ApiUtils.getUrlForCapabilities(baseUrl!!))
            .retry(API_RETRIES)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : Observer<CapabilitiesOverall> {
                override fun onSubscribe(d: Disposable) {
                    // unused atm
                }

                override fun onNext(capabilitiesOverall: CapabilitiesOverall) {
                    // FIXME check for compatible Call API version
                    if (hasExternalSignalingServer) {
                        setupAndInitiateWebSocketsConnection()
                    } else {
                        signalingMessageReceiver = internalSignalingMessageReceiver
                        signalingMessageReceiver!!.addListener(localParticipantMessageListener)
                        signalingMessageReceiver!!.addListener(offerMessageListener)
                        signalingMessageSender = internalSignalingMessageSender
                        joinRoomAndCall()
                    }
                }

                override fun onError(e: Throwable) {
                    Snackbar.make(binding!!.root, R.string.nc_common_error_sorry, Snackbar.LENGTH_LONG).show()
                    Log.e(TAG, "Failed to fetch capabilities", e)
                    // unused atm
                }

                override fun onComplete() {
                    // unused atm
                }
            })
    }

    private fun joinRoomAndCall() {
        callSession = ApplicationWideCurrentRoomHolder.getInstance().session
        val apiVersion = ApiUtils.getConversationApiVersion(conversationUser, intArrayOf(ApiUtils.API_V4, 1))
        Log.d(TAG, "joinRoomAndCall")
        Log.d(TAG, "   baseUrl= $baseUrl")
        Log.d(TAG, "   roomToken= $roomToken")
        Log.d(TAG, "   callSession= $callSession")
        val url = ApiUtils.getUrlForParticipantsActive(apiVersion, baseUrl, roomToken)
        Log.d(TAG, "   url= $url")

        // if session is empty, e.g. we when we got here by notification, we need to join the room to get a session
        if (TextUtils.isEmpty(callSession)) {
            ncApi!!.joinRoom(credentials, url, conversationPassword)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .retry(API_RETRIES)
                .subscribe(object : Observer<RoomOverall> {
                    override fun onSubscribe(d: Disposable) {
                        // unused atm
                    }

                    override fun onNext(roomOverall: RoomOverall) {
                        val conversation = roomOverall.ocs!!.data
                        callRecordingViewModel!!.setRecordingState(conversation!!.callRecording)
                        callSession = conversation.sessionId
                        Log.d(TAG, " new callSession by joinRoom= $callSession")

                        setInitialApplicationWideCurrentRoomHolderValues(conversation)

                        callOrJoinRoomViaWebSocket()
                    }

                    override fun onError(e: Throwable) {
                        Log.e(TAG, "joinRoom onError", e)
                    }

                    override fun onComplete() {
                        Log.d(TAG, "joinRoom onComplete")
                    }
                })
        } else {
            // we are in a room and start a call -> same session needs to be used
            callOrJoinRoomViaWebSocket()
        }
    }

    private fun callOrJoinRoomViaWebSocket() {
        if (hasExternalSignalingServer) {
            webSocketClient!!.joinRoomWithRoomTokenAndSession(roomToken!!, callSession)
        } else {
            performCall()
        }
    }

    private fun performCall() {
        fun getRoomAndContinue() {
            val getRoomApiVersion = ApiUtils.getConversationApiVersion(
                conversationUser,
                intArrayOf(ApiUtils.API_V4, 1)
            )
            ncApi!!.getRoom(credentials, ApiUtils.getUrlForRoom(getRoomApiVersion, baseUrl, roomToken))
                .retry(API_RETRIES)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(object : Observer<RoomOverall> {
                    override fun onSubscribe(d: Disposable) {
                        // unused atm
                    }

                    override fun onNext(roomOverall: RoomOverall) {
                        val conversation = roomOverall.ocs!!.data
                        callRecordingViewModel!!.setRecordingState(conversation!!.callRecording)
                        callSession = conversation.sessionId

                        setInitialApplicationWideCurrentRoomHolderValues(conversation)

                        startCallTimeCounter(conversation.callStartTime)

                        if (currentCallStatus !== CallStatus.LEAVING) {
                            if (currentCallStatus !== CallStatus.IN_CONVERSATION) {
                                setCallState(CallStatus.JOINED)
                            }
                            ApplicationWideCurrentRoomHolder.getInstance().isInCall = true
                            ApplicationWideCurrentRoomHolder.getInstance().isDialing = false
                            if (!TextUtils.isEmpty(roomToken)) {
                                cancelExistingNotificationsForRoom(
                                    applicationContext,
                                    conversationUser!!,
                                    roomToken!!
                                )
                            }
                            if (!hasExternalSignalingServer) {
                                pullSignalingMessages()
                            }
                        }
                    }

                    override fun onError(e: Throwable) {
                        Log.e(TAG, "Failed to get room", e)
                    }

                    override fun onComplete() {
                        // unused atm
                    }
                })
        }

        var inCallFlag = Participant.InCallFlags.IN_CALL
        if (canPublishAudioStream) {
            inCallFlag += Participant.InCallFlags.WITH_AUDIO
        }
        if (!isVoiceOnlyCall && canPublishVideoStream) {
            inCallFlag += Participant.InCallFlags.WITH_VIDEO
        }
        callParticipantList = CallParticipantList(signalingMessageReceiver)
        callParticipantList!!.addObserver(callParticipantListObserver)

        val apiVersion = ApiUtils.getCallApiVersion(conversationUser, intArrayOf(ApiUtils.API_V4, 1))
        ncApi!!.joinCall(
            credentials,
            ApiUtils.getUrlForCall(apiVersion, baseUrl, roomToken!!),
            inCallFlag,
            isCallWithoutNotification,
            recordingConsentGiven
        )
            .subscribeOn(Schedulers.io())
            .retry(API_RETRIES)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : Observer<GenericOverall> {
                override fun onSubscribe(d: Disposable) {
                    // unused atm
                }

                override fun onNext(genericOverall: GenericOverall) {
                    getRoomAndContinue()
                }

                override fun onError(e: Throwable) {
                    Log.e(TAG, "Failed to join call", e)
                    Snackbar.make(binding!!.root, R.string.nc_common_error_sorry, Snackbar.LENGTH_LONG).show()
                    hangup(true)
                }

                override fun onComplete() {
                    // unused atm
                }
            })
    }

    private fun setInitialApplicationWideCurrentRoomHolderValues(conversation: Conversation) {
        ApplicationWideCurrentRoomHolder.getInstance().userInRoom = conversationUser
        ApplicationWideCurrentRoomHolder.getInstance().session = conversation.sessionId
        ApplicationWideCurrentRoomHolder.getInstance().currentRoomId = conversation.roomId
        ApplicationWideCurrentRoomHolder.getInstance().currentRoomToken = conversation.token
        ApplicationWideCurrentRoomHolder.getInstance().callStartTime = conversation.callStartTime
    }

    private fun startCallTimeCounter(callStartTime: Long?) {
        if (callStartTime != null && hasSpreedFeatureCapability(
                conversationUser!!.capabilities!!.spreedCapability!!, SpreedFeatures.RECORDING_V1
            )
        ) {
            binding!!.callDuration.visibility = View.VISIBLE
            val currentTimeInSec = System.currentTimeMillis() / SECOND_IN_MILLIES
            elapsedSeconds = currentTimeInSec - callStartTime

            val callTimeTask: Runnable = object : Runnable {
                override fun run() {
                    if (othersInCall) {
                        binding!!.callDuration.text = DateUtils.formatElapsedTime(elapsedSeconds)
                        if (elapsedSeconds.toInt() == CALL_TIME_ONE_HOUR) {
                            showCallRunningSinceOneHourOrMoreInfo()
                        }
                    } else {
                        binding!!.callDuration.text = CALL_DURATION_EMPTY
                    }

                    elapsedSeconds += 1
                    callTimeHandler.postDelayed(this, CALL_TIME_COUNTER_DELAY)
                }
            }
            callTimeHandler.post(callTimeTask)
        } else {
            binding!!.callDuration.visibility = View.GONE
        }
    }

    private fun showCallRunningSinceOneHourOrMoreInfo() {
        binding!!.callDuration.setTypeface(null, Typeface.BOLD)
        vibrateShort(context)
        Snackbar.make(
            binding!!.root,
            context.resources.getString(R.string.call_running_since_one_hour),
            Snackbar.LENGTH_LONG
        ).show()
    }

    private fun pullSignalingMessages() {
        val signalingApiVersion = ApiUtils.getSignalingApiVersion(conversationUser, intArrayOf(ApiUtils.API_V3, 2, 1))
        val delayOnError = AtomicInteger(0)

        ncApi!!.pullSignalingMessages(
            credentials,
            ApiUtils.getUrlForSignaling(
                signalingApiVersion,
                baseUrl,
                roomToken!!
            )
        )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .repeatWhen { observable: Observable<Any?>? -> observable }
            .takeWhile { isConnectionEstablished }
            .doOnNext { delayOnError.set(0) }
            .retryWhen { errors: Observable<Throwable?> ->
                errors.flatMap { error: Throwable? ->
                    if (!isConnectionEstablished) {
                        return@flatMap Observable.error<Long>(error)
                    }
                    if (delayOnError.get() == 0) {
                        delayOnError.set(1)
                    } else if (delayOnError.get() < 16) {
                        delayOnError.set(delayOnError.get() * 2)
                    }
                    Observable.timer(delayOnError.get().toLong(), TimeUnit.SECONDS)
                }
            }
            .subscribe(object : Observer<SignalingOverall> {
                override fun onSubscribe(d: Disposable) {
                    signalingDisposable = d
                }

                override fun onNext(signalingOverall: SignalingOverall) {
                    receivedSignalingMessages(signalingOverall.ocs!!.signalings)
                }

                override fun onError(e: Throwable) {
                    dispose(signalingDisposable)
                }

                override fun onComplete() {
                    dispose(signalingDisposable)
                }
            })
    }

    private fun setupAndInitiateWebSocketsConnection() {
        if (webSocketConnectionHelper == null) {
            webSocketConnectionHelper = WebSocketConnectionHelper()
        }
        if (webSocketClient == null) {
            webSocketClient = WebSocketConnectionHelper.getExternalSignalingInstanceForServer(
                externalSignalingServer!!.externalSignalingServer,
                conversationUser,
                externalSignalingServer!!.externalSignalingTicket,
                TextUtils.isEmpty(credentials)
            )
            // Although setupAndInitiateWebSocketsConnection could be called several times the web socket is
            // initialized just once, so the message receiver is also initialized just once.
            signalingMessageReceiver = webSocketClient!!.getSignalingMessageReceiver()
            signalingMessageReceiver!!.addListener(localParticipantMessageListener)
            signalingMessageReceiver!!.addListener(offerMessageListener)
            signalingMessageSender = webSocketClient!!.signalingMessageSender
        } else {
            if (webSocketClient!!.isConnected && currentCallStatus === CallStatus.PUBLISHER_FAILED) {
                webSocketClient!!.restartWebSocket()
            }
        }
        joinRoomAndCall()
    }

    private fun initiateCall() {
        if (isConnectionEstablished) {
            Log.d(TAG, "connection already established")
            return
        }
        checkDevicePermissions()
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    fun onMessageEvent(webSocketCommunicationEvent: WebSocketCommunicationEvent) {
        if (currentCallStatus === CallStatus.LEAVING) {
            return
        }
        if (webSocketCommunicationEvent.getHashMap() != null) {
            when (webSocketCommunicationEvent.getType()) {
                "hello" -> {
                    Log.d(TAG, "onMessageEvent 'hello'")
                    if (!webSocketCommunicationEvent.getHashMap()!!.containsKey("oldResumeId")) {
                        if (currentCallStatus === CallStatus.RECONNECTING) {
                            hangup(false)
                        } else {
                            setCallState(CallStatus.RECONNECTING)
                            runOnUiThread { initiateCall() }
                        }
                    }
                }

                "roomJoined" -> {
                    Log.d(TAG, "onMessageEvent 'roomJoined'")
                    startSendingNick()
                    if (webSocketCommunicationEvent.getHashMap()!!["roomToken"] == roomToken) {
                        performCall()
                    }
                }

                "recordingStatus" -> {
                    Log.d(TAG, "onMessageEvent 'recordingStatus'")
                    if (webSocketCommunicationEvent.getHashMap()!!.containsKey(KEY_RECORDING_STATE)) {
                        val recordingStateString = webSocketCommunicationEvent.getHashMap()!![KEY_RECORDING_STATE]
                        if (recordingStateString != null) {
                            runOnUiThread { callRecordingViewModel!!.setRecordingState(recordingStateString.toInt()) }
                        }
                    }
                }
            }
        }
    }

    private fun dispose(disposable: Disposable?) {
        if (disposable != null && !disposable.isDisposed) {
            disposable.dispose()
        } else if (disposable == null) {
            if (signalingDisposable != null && !signalingDisposable!!.isDisposed) {
                signalingDisposable!!.dispose()
                signalingDisposable = null
            }
        }
    }

    private fun receivedSignalingMessages(signalingList: List<Signaling>?) {
        if (signalingList != null) {
            for (signaling in signalingList) {
                try {
                    receivedSignalingMessage(signaling)
                } catch (e: IOException) {
                    Log.e(TAG, "Failed to process received signaling message", e)
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun receivedSignalingMessage(signaling: Signaling) {
        val messageType = signaling.type
        if (!isConnectionEstablished && currentCallStatus !== CallStatus.CONNECTING) {
            return
        }

        when (messageType) {
            "usersInRoom" ->
                internalSignalingMessageReceiver.process(signaling.messageWrapper as List<Map<String?, Any?>?>?)

            "message" -> {
                val ncSignalingMessage = LoganSquare.parse(
                    signaling.messageWrapper.toString(),
                    NCSignalingMessage::class.java
                )
                internalSignalingMessageReceiver.process(ncSignalingMessage)
            }

            else ->
                Log.e(TAG, "unexpected message type when receiving signaling message")
        }
    }

    private fun hangup(shutDownView: Boolean) {
        Log.d(TAG, "hangup! shutDownView=$shutDownView")
        if (shutDownView) {
            setCallState(CallStatus.LEAVING)
        }
        stopCallingSound()
        callTimeHandler.removeCallbacksAndMessages(null)
        dispose(null)

        if (shutDownView) {
            if (videoCapturer != null) {
                try {
                    videoCapturer!!.stopCapture()
                } catch (e: InterruptedException) {
                    Log.e(TAG, "Failed to stop capturing while hanging up")
                }
                videoCapturer!!.dispose()
                videoCapturer = null
            }
            binding!!.selfVideoRenderer.release()
            if (audioSource != null) {
                audioSource!!.dispose()
                audioSource = null
            }
            runOnUiThread {
                if (audioManager != null) {
                    audioManager!!.stop()
                    audioManager = null
                }
            }
            if (videoSource != null) {
                videoSource = null
            }
            if (peerConnectionFactory != null) {
                peerConnectionFactory = null
            }
            localAudioTrack = null
            localVideoTrack = null
            if (TextUtils.isEmpty(credentials) && hasExternalSignalingServer) {
                WebSocketConnectionHelper.deleteExternalSignalingInstanceForUserEntity(-1)
            }
        }

        val peerConnectionIdsToEnd: MutableList<String> = ArrayList(peerConnectionWrapperList.size)

        for (wrapper in peerConnectionWrapperList) {
            peerConnectionIdsToEnd.add(wrapper.sessionId)
        }

        for (sessionId in peerConnectionIdsToEnd) {
            endPeerConnection(sessionId, "video")
            endPeerConnection(sessionId, "screen")
        }

        val callParticipantIdsToEnd: MutableList<String> = ArrayList(peerConnectionWrapperList.size)
        for (callParticipant in callParticipants.values) {
            callParticipantIdsToEnd.add(callParticipant!!.callParticipantModel.sessionId)
        }

        for (sessionId in callParticipantIdsToEnd) {
            removeCallParticipant(sessionId)
        }

        ApplicationWideCurrentRoomHolder.getInstance().isInCall = false
        ApplicationWideCurrentRoomHolder.getInstance().isDialing = false
        hangupNetworkCalls(shutDownView)
    }

    private fun hangupNetworkCalls(shutDownView: Boolean) {
        Log.d(TAG, "hangupNetworkCalls. shutDownView=$shutDownView")
        val apiVersion = ApiUtils.getCallApiVersion(conversationUser, intArrayOf(ApiUtils.API_V4, 1))
        if (callParticipantList != null) {
            callParticipantList!!.removeObserver(callParticipantListObserver)
            callParticipantList!!.destroy()
        }
        ncApi!!.leaveCall(credentials, ApiUtils.getUrlForCall(apiVersion, baseUrl, roomToken!!))
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : Observer<GenericOverall> {
                override fun onSubscribe(d: Disposable) {
                    // unused atm
                }

                override fun onNext(genericOverall: GenericOverall) {
                    if (switchToRoomToken.isNotEmpty()) {
                        val intent = Intent(context, ChatActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        val bundle = Bundle()
                        bundle.putBoolean(KEY_SWITCH_TO_ROOM, true)
                        bundle.putBoolean(KEY_START_CALL_AFTER_ROOM_SWITCH, true)
                        bundle.putString(KEY_ROOM_TOKEN, switchToRoomToken)
                        bundle.putBoolean(KEY_CALL_VOICE_ONLY, isVoiceOnlyCall)
                        intent.putExtras(bundle)
                        startActivity(intent)
                        finish()
                    } else if (shutDownView) {
                        finish()
                    } else if (currentCallStatus === CallStatus.RECONNECTING ||
                        currentCallStatus === CallStatus.PUBLISHER_FAILED
                    ) {
                        initiateCall()
                    }
                }

                override fun onError(e: Throwable) {
                    Log.w(TAG, "Something went wrong when leaving the call", e)
                    finish()
                }

                override fun onComplete() {
                    // unused atm
                }
            })
    }

    private fun startVideoCapture() {
        if (videoCapturer != null) {
            videoCapturer!!.startCapture(1280, 720, 30)
        }
    }

    @Suppress("Detekt.ComplexMethod")
    private fun handleCallParticipantsChanged(
        joined: Collection<Participant>,
        updated: Collection<Participant>,
        left: Collection<Participant>,
        unchanged: Collection<Participant>
    ) {
        Log.d(TAG, "handleCallParticipantsChanged")
        hasMCU = hasExternalSignalingServer && webSocketClient != null && webSocketClient!!.hasMCU()
        Log.d(TAG, "   hasMCU is $hasMCU")

        // The signaling session is the same as the Nextcloud session only when the MCU is not used.
        var currentSessionId = callSession
        if (hasMCU) {
            currentSessionId = webSocketClient!!.sessionId
        }
        Log.d(TAG, "   currentSessionId is $currentSessionId")

        val participantsInCall: MutableList<Participant> = ArrayList()
        participantsInCall.addAll(joined)
        participantsInCall.addAll(updated)
        participantsInCall.addAll(unchanged)

        var isSelfInCall = false
        var selfParticipant: Participant? = null

        for (participant in participantsInCall) {
            val inCallFlag = participant.inCall
            if (participant.sessionId != currentSessionId) {
                Log.d(
                    TAG,
                    "   inCallFlag of participant " +
                        participant.sessionId!!.substring(0, 4) +
                        " : " +
                        inCallFlag
                )
            } else {
                Log.d(TAG, "   inCallFlag of currentSessionId: $inCallFlag")
                isSelfInCall = inCallFlag != 0L
                selfParticipant = participant
            }
        }

        if (!isSelfInCall &&
            currentCallStatus !== CallStatus.LEAVING &&
            ApplicationWideCurrentRoomHolder.getInstance().isInCall
        ) {
            Log.d(TAG, "Most probably a moderator ended the call for all.")
            hangup(true)
            return
        }

        if (!isSelfInCall) {
            Log.d(TAG, "Self not in call, disconnecting from all other sessions")
            for ((_, _, _, _, _, _, _, _, _, _, sessionId) in participantsInCall) {
                Log.d(TAG, "   session that will be removed is: $sessionId")
                endPeerConnection(sessionId, "video")
                endPeerConnection(sessionId, "screen")
                removeCallParticipant(sessionId)
            }
            return
        }
        if (currentCallStatus === CallStatus.LEAVING) {
            return
        }
        if (hasMCU) {
            // Ensure that own publishing peer is set up.
            getOrCreatePeerConnectionWrapperForSessionIdAndType(
                webSocketClient!!.sessionId,
                VIDEO_STREAM_TYPE_VIDEO,
                true
            )
        }
        var selfJoined = false
        val selfParticipantHasAudioOrVideo = participantInCallFlagsHaveAudioOrVideo(selfParticipant)
        for (participant in joined) {
            val sessionId = participant.sessionId
            if (sessionId == null) {
                Log.w(TAG, "Null sessionId for call participant, this should not happen: $participant")
                continue
            }
            if (sessionId == currentSessionId) {
                selfJoined = true
                continue
            }
            Log.d(TAG, "   newSession joined: $sessionId")
            addCallParticipant(sessionId)

            val userId = participant.userId
            if (userId != null) {
                callParticipants[sessionId]!!.setUserId(userId)
            }

            if (participant.internal != null) {
                callParticipants[sessionId]!!.setInternal(participant.internal)
            }

            val nick: String? = if (hasExternalSignalingServer) {
                webSocketClient!!.getDisplayNameForSession(sessionId)
            } else {
                if (offerAnswerNickProviders[sessionId] != null) offerAnswerNickProviders[sessionId]?.nick else ""
            }

            callParticipants[sessionId]!!.setNick(nick)
            val participantHasAudioOrVideo = participantInCallFlagsHaveAudioOrVideo(participant)

            // FIXME Without MCU, PeerConnectionWrapper only sends an offer if the local session ID is higher than the
            // remote session ID. However, if the other participant does not have audio nor video that participant
            // will not send an offer, so no connection is actually established when the remote participant has a
            // higher session ID but is not publishing media.
            if (hasMCU && participantHasAudioOrVideo ||
                !hasMCU && selfParticipantHasAudioOrVideo &&
                (!participantHasAudioOrVideo || sessionId < currentSessionId!!)
            ) {
                getOrCreatePeerConnectionWrapperForSessionIdAndType(sessionId, VIDEO_STREAM_TYPE_VIDEO, false)
            }
        }
        othersInCall = if (selfJoined) {
            joined.size > 1
        } else {
            joined.isNotEmpty()
        }

        if (othersInCall && currentCallStatus !== CallStatus.IN_CONVERSATION) {
            setCallState(CallStatus.IN_CONVERSATION)
        }
        for ((_, _, _, _, _, _, _, _, _, _, sessionId) in left) {
            Log.d(TAG, "   oldSession that will be removed is: $sessionId")
            endPeerConnection(sessionId, "video")
            endPeerConnection(sessionId, "screen")
            removeCallParticipant(sessionId)
        }
    }

    private fun participantInCallFlagsHaveAudioOrVideo(participant: Participant?): Boolean {
        return if (participant == null) {
            false
        } else {
            participant.inCall and Participant.InCallFlags.WITH_AUDIO.toLong() > 0 ||
                !isVoiceOnlyCall &&
                participant.inCall and Participant.InCallFlags.WITH_VIDEO.toLong() > 0
        }
    }

    private fun getPeerConnectionWrapperForSessionIdAndType(sessionId: String?, type: String): PeerConnectionWrapper? {
        for (wrapper in peerConnectionWrapperList) {
            if (wrapper.sessionId == sessionId && wrapper.videoStreamType == type) {
                return wrapper
            }
        }
        return null
    }

    private fun getOrCreatePeerConnectionWrapperForSessionIdAndType(
        sessionId: String?,
        type: String,
        publisher: Boolean
    ): PeerConnectionWrapper? {
        var peerConnectionWrapper: PeerConnectionWrapper?
        peerConnectionWrapper = getPeerConnectionWrapperForSessionIdAndType(sessionId, type)

        return if (peerConnectionWrapper != null) {
            peerConnectionWrapper
        } else {
            if (peerConnectionFactory == null) {
                Log.e(TAG, "peerConnectionFactory was null in getOrCreatePeerConnectionWrapperForSessionIdAndType")
                Snackbar.make(
                    binding!!.root,
                    context.resources.getString(R.string.nc_common_error_sorry),
                    Snackbar.LENGTH_LONG
                ).show()
                hangup(true)
                return null
            }
            peerConnectionWrapper = if (hasMCU && publisher) {
                PeerConnectionWrapper(
                    peerConnectionFactory,
                    iceServers,
                    sdpConstraintsForMCU,
                    sessionId,
                    callSession,
                    localStream,
                    true,
                    true,
                    type,
                    signalingMessageReceiver,
                    signalingMessageSender
                )
            } else if (hasMCU) {
                PeerConnectionWrapper(
                    peerConnectionFactory,
                    iceServers,
                    sdpConstraints,
                    sessionId,
                    callSession,
                    null,
                    false,
                    true,
                    type,
                    signalingMessageReceiver,
                    signalingMessageSender
                )
            } else {
                if ("screen" != type) {
                    PeerConnectionWrapper(
                        peerConnectionFactory,
                        iceServers,
                        sdpConstraints,
                        sessionId,
                        callSession,
                        localStream,
                        false,
                        false,
                        type,
                        signalingMessageReceiver,
                        signalingMessageSender
                    )
                } else {
                    PeerConnectionWrapper(
                        peerConnectionFactory,
                        iceServers,
                        sdpConstraints,
                        sessionId,
                        callSession,
                        null,
                        false,
                        false,
                        type,
                        signalingMessageReceiver,
                        signalingMessageSender
                    )
                }
            }
            peerConnectionWrapperList.add(peerConnectionWrapper)
            if (!publisher) {
                var callParticipant = callParticipants[sessionId]
                if (callParticipant == null) {
                    callParticipant = addCallParticipant(sessionId)
                }
                if ("screen" == type) {
                    callParticipant.setScreenPeerConnectionWrapper(peerConnectionWrapper)
                } else {
                    callParticipant.setPeerConnectionWrapper(peerConnectionWrapper)
                }
            }
            if (publisher) {
                peerConnectionWrapper.addObserver(selfPeerConnectionObserver)
                startSendingNick()
            }
            peerConnectionWrapper
        }
    }

    private fun addCallParticipant(sessionId: String?): CallParticipant {
        val callParticipant = CallParticipant(sessionId, signalingMessageReceiver)
        callParticipants[sessionId] = callParticipant
        val callParticipantMessageListener: CallParticipantMessageListener =
            CallActivityCallParticipantMessageListener(sessionId)
        callParticipantMessageListeners[sessionId] = callParticipantMessageListener
        signalingMessageReceiver!!.addListener(callParticipantMessageListener, sessionId)
        if (!hasExternalSignalingServer) {
            val offerAnswerNickProvider = OfferAnswerNickProvider(sessionId)
            offerAnswerNickProviders[sessionId] = offerAnswerNickProvider
            signalingMessageReceiver!!.addListener(
                offerAnswerNickProvider.videoWebRtcMessageListener,
                sessionId,
                "video"
            )
            signalingMessageReceiver!!.addListener(
                offerAnswerNickProvider.screenWebRtcMessageListener,
                sessionId,
                "screen"
            )
        }
        val callParticipantModel = callParticipant.callParticipantModel
        val screenParticipantDisplayItemManager = ScreenParticipantDisplayItemManager(callParticipantModel)
        screenParticipantDisplayItemManagers[sessionId] = screenParticipantDisplayItemManager
        callParticipantModel.addObserver(
            screenParticipantDisplayItemManager,
            screenParticipantDisplayItemManagersHandler
        )
        val callParticipantEventDisplayer = CallParticipantEventDisplayer(callParticipantModel)
        callParticipantEventDisplayers[sessionId] = callParticipantEventDisplayer
        callParticipantModel.addObserver(callParticipantEventDisplayer, callParticipantEventDisplayersHandler)
        runOnUiThread { addParticipantDisplayItem(callParticipantModel, "video") }
        return callParticipant
    }

    private fun endPeerConnection(sessionId: String?, type: String) {
        val peerConnectionWrapper = getPeerConnectionWrapperForSessionIdAndType(sessionId, type) ?: return
        if (webSocketClient != null &&
            webSocketClient!!.sessionId != null &&
            webSocketClient!!.sessionId == sessionId
        ) {
            peerConnectionWrapper.removeObserver(selfPeerConnectionObserver)
        }
        val callParticipant = callParticipants[sessionId]
        if (callParticipant != null) {
            if ("screen" == type) {
                callParticipant.setScreenPeerConnectionWrapper(null)
            } else {
                callParticipant.setPeerConnectionWrapper(null)
            }
        }
        peerConnectionWrapper.removePeerConnection()
        peerConnectionWrapperList.remove(peerConnectionWrapper)
    }

    private fun removeCallParticipant(sessionId: String?) {
        val callParticipant = callParticipants.remove(sessionId) ?: return
        val screenParticipantDisplayItemManager = screenParticipantDisplayItemManagers.remove(sessionId)
        callParticipant.callParticipantModel.removeObserver(screenParticipantDisplayItemManager)
        val callParticipantEventDisplayer = callParticipantEventDisplayers.remove(sessionId)
        callParticipant.callParticipantModel.removeObserver(callParticipantEventDisplayer)
        callParticipant.destroy()
        val listener = callParticipantMessageListeners.remove(sessionId)
        signalingMessageReceiver!!.removeListener(listener)
        val offerAnswerNickProvider = offerAnswerNickProviders.remove(sessionId)
        if (offerAnswerNickProvider != null) {
            signalingMessageReceiver!!.removeListener(offerAnswerNickProvider.videoWebRtcMessageListener)
            signalingMessageReceiver!!.removeListener(offerAnswerNickProvider.screenWebRtcMessageListener)
        }
        runOnUiThread { removeParticipantDisplayItem(sessionId, "video") }
    }

    private fun removeParticipantDisplayItem(sessionId: String?, videoStreamType: String) {
        Log.d(TAG, "removeParticipantDisplayItem")
        val participantDisplayItem = participantDisplayItems!!.remove("$sessionId-$videoStreamType") ?: return
        participantDisplayItem.destroy()
        if (!isDestroyed) {
            initGridAdapter()
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(configurationChangeEvent: ConfigurationChangeEvent?) {
        powerManagerUtils!!.setOrientation(Objects.requireNonNull(resources).configuration.orientation)
        initGridAdapter()
        updateSelfVideoViewPosition()
    }

    private fun updateSelfVideoViewIceConnectionState(iceConnectionState: IceConnectionState) {
        val connected = iceConnectionState == IceConnectionState.CONNECTED ||
            iceConnectionState == IceConnectionState.COMPLETED

        // FIXME In voice only calls there is no video view, so the progress bar would appear floating in the middle of
        // nowhere. However, a way to signal that the local participant is not connected to the HPB is still need in
        // that case.
        if (!connected && !isVoiceOnlyCall) {
            binding!!.selfVideoViewProgressBar.visibility = View.VISIBLE
        } else {
            binding!!.selfVideoViewProgressBar.visibility = View.GONE
        }
    }

    private fun updateSelfVideoViewPosition() {
        Log.d(TAG, "updateSelfVideoViewPosition")
        if (!isInPipMode) {
            val layoutParams = binding!!.selfVideoRenderer.layoutParams as FrameLayout.LayoutParams
            val displayMetrics = applicationContext.resources.displayMetrics
            val screenWidthPx = displayMetrics.widthPixels
            val screenWidthDp = DisplayUtils.convertPixelToDp(screenWidthPx.toFloat(), applicationContext).toInt()
            var newXafterRotate = 0f
            val newYafterRotate: Float = if (binding!!.callInfosLinearLayout.visibility == View.VISIBLE) {
                250f
            } else {
                20f
            }
            if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                layoutParams.height = resources.getDimension(R.dimen.call_self_video_short_side_length).toInt()
                layoutParams.width = resources.getDimension(R.dimen.call_self_video_long_side_length).toInt()
                newXafterRotate =
                    (screenWidthDp - resources.getDimension(R.dimen.call_self_video_short_side_length) * 0.8).toFloat()
            } else if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                layoutParams.height = resources.getDimension(R.dimen.call_self_video_long_side_length).toInt()
                layoutParams.width = resources.getDimension(R.dimen.call_self_video_short_side_length).toInt()
                newXafterRotate =
                    (screenWidthDp - resources.getDimension(R.dimen.call_self_video_short_side_length) * 0.5).toFloat()
            }
            binding!!.selfVideoRenderer.layoutParams = layoutParams
            val newXafterRotatePx = DisplayUtils.convertDpToPixel(newXafterRotate, applicationContext).toInt()
            binding!!.selfVideoViewWrapper.y = newYafterRotate
            binding!!.selfVideoViewWrapper.x = newXafterRotatePx.toFloat()
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(proximitySensorEvent: ProximitySensorEvent) {
        if (!isVoiceOnlyCall) {
            val enableVideo = proximitySensorEvent.proximitySensorEventType ==
                ProximitySensorEvent.ProximitySensorEventType.SENSOR_FAR && videoOn
            if (permissionUtil!!.isCameraPermissionGranted() &&
                (currentCallStatus === CallStatus.CONNECTING || isConnectionEstablished) &&
                videoOn && enableVideo != localVideoTrack!!.enabled()
            ) {
                toggleMedia(enableVideo, true)
            }
        }
    }

    private fun startSendingNick() {
        val dataChannelMessage = DataChannelMessage()
        dataChannelMessage.type = "nickChanged"
        val nickChangedPayload: MutableMap<String, String> = HashMap()
        nickChangedPayload["userid"] = conversationUser!!.userId!!
        nickChangedPayload["name"] = conversationUser!!.displayName!!
        dataChannelMessage.payloadMap = nickChangedPayload.toMap()
        for (peerConnectionWrapper in peerConnectionWrapperList) {
            if (peerConnectionWrapper.isMCUPublisher) {
                Observable
                    .interval(1, TimeUnit.SECONDS)
                    .repeatUntil { !isConnectionEstablished || isDestroyed }
                    .observeOn(Schedulers.io())
                    .subscribe(object : Observer<Long> {
                        override fun onSubscribe(d: Disposable) {
                            // unused atm
                        }

                        override fun onNext(aLong: Long) {
                            peerConnectionWrapper.sendChannelData(dataChannelMessage)
                        }

                        override fun onError(e: Throwable) {
                            // unused atm
                        }

                        override fun onComplete() {
                            // unused atm
                        }
                    })
                break
            }
        }
    }

    private fun addParticipantDisplayItem(callParticipantModel: CallParticipantModel, videoStreamType: String) {
        if (callParticipantModel.isInternal != null && callParticipantModel.isInternal) {
            return
        }
        val defaultGuestNick = resources.getString(R.string.nc_nick_guest)
        val participantDisplayItem = ParticipantDisplayItem(
            baseUrl,
            defaultGuestNick,
            rootEglBase,
            videoStreamType,
            callParticipantModel
        )
        val sessionId = callParticipantModel.sessionId
        participantDisplayItems!!["$sessionId-$videoStreamType"] = participantDisplayItem
        initGridAdapter()
    }

    private fun setCallState(callState: CallStatus) {
        if (currentCallStatus == null || currentCallStatus !== callState) {
            currentCallStatus = callState
            if (handler == null) {
                handler = Handler(Looper.getMainLooper())
            } else {
                handler!!.removeCallbacksAndMessages(null)
            }
            when (callState) {
                CallStatus.CONNECTING -> handler!!.post {
                    playCallingSound()
                    if (isIncomingCallFromNotification) {
                        binding!!.callStates.callStateTextView.setText(R.string.nc_call_incoming)
                    } else {
                        binding!!.callStates.callStateTextView.setText(R.string.nc_call_ringing)
                    }
                    binding!!.callConversationNameTextView.text = conversationName
                    binding!!.callModeTextView.text = descriptionForCallType
                    if (binding!!.callStates.callStateRelativeLayout.visibility != View.VISIBLE) {
                        binding!!.callStates.callStateRelativeLayout.visibility = View.VISIBLE
                    }
                    if (binding!!.gridview.visibility != View.INVISIBLE) {
                        binding!!.gridview.visibility = View.INVISIBLE
                    }
                    if (binding!!.callStates.callStateProgressBar.visibility != View.VISIBLE) {
                        binding!!.callStates.callStateProgressBar.visibility = View.VISIBLE
                    }
                    if (binding!!.callStates.errorImageView.visibility != View.GONE) {
                        binding!!.callStates.errorImageView.visibility = View.GONE
                    }
                }

                CallStatus.CALLING_TIMEOUT -> handler!!.post {
                    hangup(false)
                    binding!!.callStates.callStateTextView.setText(R.string.nc_call_timeout)
                    binding!!.callModeTextView.text = descriptionForCallType
                    if (binding!!.callStates.callStateRelativeLayout.visibility != View.VISIBLE) {
                        binding!!.callStates.callStateRelativeLayout.visibility = View.VISIBLE
                    }
                    if (binding!!.callStates.callStateProgressBar.visibility != View.GONE) {
                        binding!!.callStates.callStateProgressBar.visibility = View.GONE
                    }
                    if (binding!!.gridview.visibility != View.INVISIBLE) {
                        binding!!.gridview.visibility = View.INVISIBLE
                    }
                    binding!!.callStates.errorImageView.setImageResource(R.drawable.ic_av_timer_timer_24dp)
                    if (binding!!.callStates.errorImageView.visibility != View.VISIBLE) {
                        binding!!.callStates.errorImageView.visibility = View.VISIBLE
                    }
                }

                CallStatus.PUBLISHER_FAILED -> handler!!.post {
                    // No calling sound when the publisher failed
                    binding!!.callStates.callStateTextView.setText(R.string.nc_call_reconnecting)
                    binding!!.callModeTextView.text = descriptionForCallType
                    if (binding!!.callStates.callStateRelativeLayout.visibility != View.VISIBLE) {
                        binding!!.callStates.callStateRelativeLayout.visibility = View.VISIBLE
                    }
                    if (binding!!.gridview.visibility != View.INVISIBLE) {
                        binding!!.gridview.visibility = View.INVISIBLE
                    }
                    if (binding!!.callStates.callStateProgressBar.visibility != View.VISIBLE) {
                        binding!!.callStates.callStateProgressBar.visibility = View.VISIBLE
                    }
                    if (binding!!.callStates.errorImageView.visibility != View.GONE) {
                        binding!!.callStates.errorImageView.visibility = View.GONE
                    }
                }

                CallStatus.RECONNECTING -> handler!!.post {
                    playCallingSound()
                    binding!!.callStates.callStateTextView.setText(R.string.nc_call_reconnecting)
                    binding!!.callModeTextView.text = descriptionForCallType
                    if (binding!!.callStates.callStateRelativeLayout.visibility != View.VISIBLE) {
                        binding!!.callStates.callStateRelativeLayout.visibility = View.VISIBLE
                    }
                    if (binding!!.gridview.visibility != View.INVISIBLE) {
                        binding!!.gridview.visibility = View.INVISIBLE
                    }
                    if (binding!!.callStates.callStateProgressBar.visibility != View.VISIBLE) {
                        binding!!.callStates.callStateProgressBar.visibility = View.VISIBLE
                    }
                    if (binding!!.callStates.errorImageView.visibility != View.GONE) {
                        binding!!.callStates.errorImageView.visibility = View.GONE
                    }
                }

                CallStatus.JOINED -> {
                    handler!!.postDelayed({ setCallState(CallStatus.CALLING_TIMEOUT) }, 45000)
                    handler!!.post {
                        binding!!.callModeTextView.text = descriptionForCallType
                        if (isIncomingCallFromNotification) {
                            binding!!.callStates.callStateTextView.setText(R.string.nc_call_incoming)
                        } else {
                            binding!!.callStates.callStateTextView.setText(R.string.nc_call_ringing)
                        }
                        if (binding!!.callStates.callStateRelativeLayout.visibility != View.VISIBLE) {
                            binding!!.callStates.callStateRelativeLayout.visibility = View.VISIBLE
                        }
                        if (binding!!.callStates.callStateProgressBar.visibility != View.VISIBLE) {
                            binding!!.callStates.callStateProgressBar.visibility = View.VISIBLE
                        }
                        if (binding!!.gridview.visibility != View.INVISIBLE) {
                            binding!!.gridview.visibility = View.INVISIBLE
                        }
                        if (binding!!.callStates.errorImageView.visibility != View.GONE) {
                            binding!!.callStates.errorImageView.visibility = View.GONE
                        }
                    }
                }

                CallStatus.IN_CONVERSATION -> handler!!.post {
                    stopCallingSound()
                    binding!!.callModeTextView.text = descriptionForCallType
                    if (!isVoiceOnlyCall) {
                        binding!!.callInfosLinearLayout.visibility = View.GONE
                    }
                    if (!isPushToTalkActive) {
                        animateCallControls(false, 5000)
                    }
                    if (binding!!.callStates.callStateRelativeLayout.visibility != View.INVISIBLE) {
                        binding!!.callStates.callStateRelativeLayout.visibility = View.INVISIBLE
                    }
                    if (binding!!.callStates.callStateProgressBar.visibility != View.GONE) {
                        binding!!.callStates.callStateProgressBar.visibility = View.GONE
                    }
                    if (binding!!.gridview.visibility != View.VISIBLE) {
                        binding!!.gridview.visibility = View.VISIBLE
                    }
                    if (binding!!.callStates.errorImageView.visibility != View.GONE) {
                        binding!!.callStates.errorImageView.visibility = View.GONE
                    }
                }

                CallStatus.OFFLINE -> handler!!.post {
                    stopCallingSound()
                    binding!!.callStates.callStateTextView.setText(R.string.nc_offline)
                    if (binding!!.callStates.callStateRelativeLayout.visibility != View.VISIBLE) {
                        binding!!.callStates.callStateRelativeLayout.visibility = View.VISIBLE
                    }
                    if (binding!!.gridview.visibility != View.INVISIBLE) {
                        binding!!.gridview.visibility = View.INVISIBLE
                    }
                    if (binding!!.callStates.callStateProgressBar.visibility != View.GONE) {
                        binding!!.callStates.callStateProgressBar.visibility = View.GONE
                    }
                    binding!!.callStates.errorImageView.setImageResource(R.drawable.ic_signal_wifi_off_white_24dp)
                    if (binding!!.callStates.errorImageView.visibility != View.VISIBLE) {
                        binding!!.callStates.errorImageView.visibility = View.VISIBLE
                    }
                }

                CallStatus.LEAVING -> handler!!.post {
                    if (!isDestroyed) {
                        stopCallingSound()
                        binding!!.callModeTextView.text = descriptionForCallType
                        binding!!.callStates.callStateTextView.setText(R.string.nc_leaving_call)
                        binding!!.callStates.callStateRelativeLayout.visibility = View.VISIBLE
                        binding!!.gridview.visibility = View.INVISIBLE
                        binding!!.callStates.callStateProgressBar.visibility = View.VISIBLE
                        binding!!.callStates.errorImageView.visibility = View.GONE
                    }
                }
            }
        }
    }

    private val descriptionForCallType: String
        get() {
            val appName = resources.getString(R.string.nc_app_product_name)
            return if (isVoiceOnlyCall) {
                String.format(resources.getString(R.string.nc_call_voice), appName)
            } else {
                String.format(resources.getString(R.string.nc_call_video), appName)
            }
        }

    private fun playCallingSound() {
        stopCallingSound()
        val ringtoneUri: Uri? = if (isIncomingCallFromNotification) {
            getCallRingtoneUri(applicationContext, appPreferences)
        } else {
            Uri.parse("android.resource://" + applicationContext.packageName + "/raw/tr110_1_kap8_3_freiton1")
        }
        if (ringtoneUri != null) {
            mediaPlayer = MediaPlayer()
            try {
                mediaPlayer!!.setDataSource(this, ringtoneUri)
                mediaPlayer!!.isLooping = true
                val audioAttributes = AudioAttributes.Builder().setContentType(
                    AudioAttributes.CONTENT_TYPE_SONIFICATION
                )
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .build()
                mediaPlayer!!.setAudioAttributes(audioAttributes)
                mediaPlayer!!.setOnPreparedListener { mp: MediaPlayer? -> mediaPlayer!!.start() }
                mediaPlayer!!.prepareAsync()
            } catch (e: IOException) {
                Log.e(TAG, "Failed to play sound")
            }
        }
    }

    private fun stopCallingSound() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer!!.isPlaying) {
                    mediaPlayer!!.stop()
                }
            } catch (e: IllegalStateException) {
                Log.e(TAG, "mediaPlayer was not initialized", e)
            } finally {
                if (mediaPlayer != null) {
                    mediaPlayer!!.release()
                }
                mediaPlayer = null
            }
        }
    }

    fun addReactionForAnimation(emoji: String?, displayName: String?) {
        reactionAnimator!!.addReaction(emoji!!, displayName!!)
    }

    /**
     * Temporary implementation of SignalingMessageReceiver until signaling related code is extracted from
     * CallActivity.
     *
     *
     * All listeners are called in the main thread.
     */
    private class InternalSignalingMessageReceiver : SignalingMessageReceiver() {
        fun process(users: List<Map<String?, Any?>?>?) {
            processUsersInRoom(users)
        }

        fun process(message: NCSignalingMessage?) {
            processSignalingMessage(message)
        }
    }

    private inner class OfferAnswerNickProvider(private val sessionId: String?) {
        val videoWebRtcMessageListener: WebRtcMessageListener = WebRtcMessageListener()
        val screenWebRtcMessageListener: WebRtcMessageListener = WebRtcMessageListener()
        var nick: String? = null
            private set

        private inner class WebRtcMessageListener : SignalingMessageReceiver.WebRtcMessageListener {
            override fun onOffer(sdp: String, nick: String?) {
                onOfferOrAnswer(nick)
            }

            override fun onAnswer(sdp: String, nick: String?) {
                onOfferOrAnswer(nick)
            }

            override fun onCandidate(sdpMid: String, sdpMLineIndex: Int, sdp: String) {
                // unused atm
            }

            override fun onEndOfCandidates() {
                // unused atm
            }
        }

        private fun onOfferOrAnswer(nick: String?) {
            this.nick = nick
            if (callParticipants[sessionId] != null) {
                callParticipants[sessionId]!!.setNick(nick)
            }
        }
    }

    private inner class CallActivityCallParticipantMessageListener(private val sessionId: String?) :
        CallParticipantMessageListener {
        override fun onRaiseHand(state: Boolean, timestamp: Long) {
            // unused atm
        }

        override fun onReaction(reaction: String) {
            // unused atm
        }

        override fun onUnshareScreen() {
            endPeerConnection(sessionId, "screen")
        }
    }

    private inner class CallActivitySelfPeerConnectionObserver : PeerConnectionObserver {
        override fun onStreamAdded(mediaStream: MediaStream) {
            // unused atm
        }

        override fun onStreamRemoved(mediaStream: MediaStream) {
            // unused atm
        }

        override fun onIceConnectionStateChanged(iceConnectionState: IceConnectionState) {
            runOnUiThread {
                updateSelfVideoViewIceConnectionState(iceConnectionState)
                if (iceConnectionState == IceConnectionState.FAILED) {
                    setCallState(CallStatus.PUBLISHER_FAILED)
                    webSocketClient!!.clearResumeId()
                    hangup(false)
                }
            }
        }
    }

    private inner class ScreenParticipantDisplayItemManager(private val callParticipantModel: CallParticipantModel) :
        CallParticipantModel.Observer {
        override fun onChange() {
            val sessionId = callParticipantModel.sessionId
            if (callParticipantModel.screenIceConnectionState == null) {
                removeParticipantDisplayItem(sessionId, "screen")
                return
            }
            val hasScreenParticipantDisplayItem = participantDisplayItems!!["$sessionId-screen"] != null
            if (!hasScreenParticipantDisplayItem) {
                addParticipantDisplayItem(callParticipantModel, "screen")
            }
        }

        override fun onReaction(reaction: String) {
            // unused atm
        }
    }

    private inner class CallParticipantEventDisplayer(private val callParticipantModel: CallParticipantModel) :
        CallParticipantModel.Observer {
        private var raisedHand: Boolean

        init {
            raisedHand = if (callParticipantModel.raisedHand != null) callParticipantModel.raisedHand.state else false
        }

        override fun onChange() {
            if (callParticipantModel.raisedHand == null || !callParticipantModel.raisedHand.state) {
                raisedHand = false
                return
            }
            if (raisedHand) {
                return
            }
            raisedHand = true
            val nick = callParticipantModel.nick
            Snackbar.make(
                binding!!.root,
                String.format(context.resources.getString(R.string.nc_call_raised_hand), nick),
                Snackbar.LENGTH_LONG
            ).show()
        }

        override fun onReaction(reaction: String) {
            addReactionForAnimation(reaction, callParticipantModel.nick)
        }
    }

    private inner class InternalSignalingMessageSender : SignalingMessageSender {
        override fun send(ncSignalingMessage: NCSignalingMessage) {
            addLocalParticipantNickIfNeeded(ncSignalingMessage)
            val serializedNcSignalingMessage: String = try {
                LoganSquare.serialize(ncSignalingMessage)
            } catch (e: IOException) {
                Log.e(TAG, "Failed to serialize signaling message", e)
                return
            }

            // The message wrapper can not be defined in a JSON model to be directly serialized, as sent messages
            // need to be serialized twice; first the signaling message, and then the wrapper as a whole. Received
            // messages, on the other hand, just need to be deserialized once.
            val stringBuilder = StringBuilder()
            stringBuilder.append('{')
                .append("\"fn\":\"")
                .append(StringEscapeUtils.escapeJson(serializedNcSignalingMessage))
                .append('\"')
                .append(',')
                .append("\"sessionId\":")
                .append('\"').append(StringEscapeUtils.escapeJson(callSession)).append('\"')
                .append(',')
                .append("\"ev\":\"message\"")
                .append('}')
            val strings: MutableList<String> = ArrayList()
            val stringToSend = stringBuilder.toString()
            strings.add(stringToSend)
            val apiVersion = ApiUtils.getSignalingApiVersion(conversationUser, intArrayOf(ApiUtils.API_V3, 2, 1))
            ncApi!!.sendSignalingMessages(
                credentials,
                ApiUtils.getUrlForSignaling(apiVersion, baseUrl, roomToken!!),
                strings.toString()
            )
                .retry(API_RETRIES)
                .subscribeOn(Schedulers.io())
                .subscribe(object : Observer<SignalingOverall> {
                    override fun onSubscribe(d: Disposable) {
                        // unused atm
                    }

                    override fun onNext(signalingOverall: SignalingOverall) {
                        // When sending messages to the internal signaling server the response has been empty since
                        // Talk v2.9.0, so it is not really needed to process it, but there is no harm either in
                        // doing that, as technically messages could be returned.
                        receivedSignalingMessages(signalingOverall.ocs!!.signalings)
                    }

                    override fun onError(e: Throwable) {
                        Log.e(TAG, "Failed to send signaling message", e)
                    }

                    override fun onComplete() {
                        // unused atm
                    }
                })
        }

        /**
         * Adds the local participant nick to offers and answers.
         *
         *
         * For legacy reasons the offers and answers sent when the internal signaling server is used are expected to
         * provide the nick of the local participant.
         *
         * @param ncSignalingMessage the message to add the nick to
         */
        private fun addLocalParticipantNickIfNeeded(ncSignalingMessage: NCSignalingMessage) {
            val type = ncSignalingMessage.type
            if ("offer" != type && "answer" != type) {
                return
            }
            val payload = ncSignalingMessage.payload
                ?: // Broken message, this should not happen
                return
            payload.nick = conversationUser!!.displayName
        }
    }

    private inner class MicrophoneButtonTouchListener : OnTouchListener {
        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            v.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_UP && isPushToTalkActive) {
                isPushToTalkActive = false
                binding!!.microphoneButton.setImageResource(R.drawable.ic_mic_off_white_24px)
                pulseAnimation!!.stop()
                toggleMedia(false, false)
                animateCallControls(false, 5000)
            }
            return true
        }
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    fun onMessageEvent(networkEvent: NetworkEvent) {
        if (networkEvent.networkConnectionEvent == NetworkEvent.NetworkConnectionEvent.NETWORK_CONNECTED) {
            if (handler != null) {
                handler!!.removeCallbacksAndMessages(null)
            }
        } else if (networkEvent.networkConnectionEvent ==
            NetworkEvent.NetworkConnectionEvent.NETWORK_DISCONNECTED
        ) {
            if (handler != null) {
                handler!!.removeCallbacksAndMessages(null)
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        Log.d(TAG, "onPictureInPictureModeChanged")
        Log.d(TAG, "isInPictureInPictureMode= $isInPictureInPictureMode")
        isInPipMode = isInPictureInPictureMode
        if (isInPictureInPictureMode) {
            mReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (MICROPHONE_PIP_INTENT_NAME != intent.action) {
                        return
                    }
                    when (intent.getIntExtra(MICROPHONE_PIP_INTENT_EXTRA_ACTION, 0)) {
                        MICROPHONE_PIP_REQUEST_MUTE, MICROPHONE_PIP_REQUEST_UNMUTE -> onMicrophoneClick()
                    }
                }
            }
            registerPermissionHandlerBroadcastReceiver(
                mReceiver,
                IntentFilter(MICROPHONE_PIP_INTENT_NAME),
                permissionUtil!!.privateBroadcastPermission,
                null,
                ReceiverFlag.NotExported
            )
            updateUiForPipMode()
        } else {
            unregisterReceiver(mReceiver)
            mReceiver = null
            updateUiForNormalMode()
        }
    }

    private fun updatePictureInPictureActions(@DrawableRes iconId: Int, title: String?, requestCode: Int) {
        if (isGreaterEqualOreo && isPipModePossible) {
            val actions = ArrayList<RemoteAction>()
            val icon = Icon.createWithResource(this, iconId)
            val intentFlag: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_IMMUTABLE
            } else {
                0
            }
            val intent = PendingIntent.getBroadcast(
                this,
                requestCode,
                Intent(MICROPHONE_PIP_INTENT_NAME).putExtra(MICROPHONE_PIP_INTENT_EXTRA_ACTION, requestCode),
                intentFlag
            )
            actions.add(RemoteAction(icon, title!!, title, intent))
            mPictureInPictureParamsBuilder.setActions(actions)
            setPictureInPictureParams(mPictureInPictureParamsBuilder.build())
        }
    }

    override fun updateUiForPipMode() {
        Log.d(TAG, "updateUiForPipMode")
        val params = RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 0, 0, 0)
        binding!!.gridview.layoutParams = params
        binding!!.callControls.visibility = View.GONE
        binding!!.callInfosLinearLayout.visibility = View.GONE
        binding!!.selfVideoViewWrapper.visibility = View.GONE
        binding!!.callStates.callStateRelativeLayout.visibility = View.GONE
        if (participantDisplayItems!!.size > 1) {
            binding!!.pipCallConversationNameTextView.text = conversationName
            binding!!.pipGroupCallOverlay.visibility = View.VISIBLE
        } else {
            binding!!.pipGroupCallOverlay.visibility = View.GONE
        }
        binding!!.selfVideoRenderer.release()
    }

    override fun updateUiForNormalMode() {
        Log.d(TAG, "updateUiForNormalMode")
        if (isVoiceOnlyCall) {
            binding!!.callControls.visibility = View.VISIBLE
        } else {
            // animateCallControls needs this to be invisible for a check.
            binding!!.callControls.visibility = View.INVISIBLE
        }
        initViews()
        binding!!.callInfosLinearLayout.visibility = View.VISIBLE
        binding!!.selfVideoViewWrapper.visibility = View.VISIBLE
        binding!!.pipGroupCallOverlay.visibility = View.GONE
    }

    override fun suppressFitsSystemWindows() {
        binding!!.callLayout.fitsSystemWindows = false
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        eventBus.post(ConfigurationChangeEvent())
    }

    val isAllowedToStartOrStopRecording: Boolean
        get() = (
            isCallRecordingAvailable(conversationUser!!.capabilities!!.spreedCapability!!) &&
                isModerator
            )
    val isAllowedToRaiseHand: Boolean
        get() = hasSpreedFeatureCapability(
            conversationUser.capabilities!!.spreedCapability!!,
            SpreedFeatures.RAISE_HAND
        ) || isBreakoutRoom

    private inner class SelfVideoTouchListener : OnTouchListener {
        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(view: View, event: MotionEvent): Boolean {
            val duration = event.eventTime - event.downTime
            if (event.actionMasked == MotionEvent.ACTION_MOVE) {
                val newY = event.rawY - binding!!.selfVideoViewWrapper.height / 2f
                val newX = event.rawX - binding!!.selfVideoViewWrapper.width / 2f
                binding!!.selfVideoViewWrapper.y = newY
                binding!!.selfVideoViewWrapper.x = newX
            } else if (event.actionMasked == MotionEvent.ACTION_UP && duration < SWITCH_CAMERA_THRESHOLD_DURATION) {
                switchCamera()
            }
            return true
        }
    }

    companion object {
        var active = false

        // const val VIDEO_STREAM_TYPE_SCREEN = "screen"
        const val VIDEO_STREAM_TYPE_VIDEO = "video"
        const val TAG = "CallActivity"
        private val PERMISSIONS_CAMERA = arrayOf(
            Manifest.permission.CAMERA
        )
        private val PERMISSIONS_MICROPHONE = arrayOf(
            Manifest.permission.RECORD_AUDIO
        )
        private const val MICROPHONE_PIP_INTENT_NAME = "microphone_pip_intent"
        private const val MICROPHONE_PIP_INTENT_EXTRA_ACTION = "microphone_pip_action"
        private const val MICROPHONE_PIP_REQUEST_MUTE = 1
        private const val MICROPHONE_PIP_REQUEST_UNMUTE = 2

        const val OPACITY_ENABLED = 1.0f
        const val OPACITY_DISABLED = 0.7f
        const val OPACITY_INVISIBLE = 0.0f

        const val SECOND_IN_MILLIES: Long = 1000
        const val CALL_TIME_COUNTER_DELAY: Long = 1000
        const val CALL_TIME_ONE_HOUR = 3600
        const val CALL_DURATION_EMPTY = "--:--"
        const val API_RETRIES: Long = 3

        const val SWITCH_CAMERA_THRESHOLD_DURATION = 100

        private const val SAMPLE_RATE = 8000
        private const val MICROPHONE_VALUE_THRESHOLD = 20
        private const val MICROPHONE_VALUE_SLEEP: Long = 1000

        private const val SIGNALING_MESSAGE_SPEAKING_STARTED = "speaking"
        private const val SIGNALING_MESSAGE_SPEAKING_STOPPED = "stoppedSpeaking"
        private const val SIGNALING_MESSAGE_VIDEO_ON = "videoOn"
        private const val SIGNALING_MESSAGE_VIDEO_OFF = "videoOff"
        private const val SIGNALING_MESSAGE_AUDIO_ON = "audioOn"
        private const val SIGNALING_MESSAGE_AUDIO_OFF = "audioOff"
    }
}
