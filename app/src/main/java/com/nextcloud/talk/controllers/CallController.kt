/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
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

package com.nextcloud.talk.controllers

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Color
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import butterknife.BindView
import butterknife.OnClick
import butterknife.OnLongClick
import coil.api.load
import coil.transform.CircleCropTransformation
import com.bluelinelabs.logansquare.LoganSquare
import com.nextcloud.talk.R
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.controllers.base.BaseController
import com.nextcloud.talk.events.*
import com.nextcloud.talk.models.ExternalSignalingServer
import com.nextcloud.talk.models.json.capabilities.CapabilitiesOverall
import com.nextcloud.talk.models.json.conversations.ConversationOverall
import com.nextcloud.talk.models.json.conversations.RoomsOverall
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.models.json.participants.Participant
import com.nextcloud.talk.models.json.participants.ParticipantsOverall
import com.nextcloud.talk.models.json.signaling.*
import com.nextcloud.talk.models.json.signaling.settings.IceServer
import com.nextcloud.talk.models.json.signaling.settings.SignalingSettingsOverall
import com.nextcloud.talk.newarch.local.models.UserNgEntity
import com.nextcloud.talk.newarch.local.models.getCredentials
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.NotificationUtils
import com.nextcloud.talk.utils.animations.PulseAnimation
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.database.user.UserUtils
import com.nextcloud.talk.utils.power.PowerManagerUtils
import com.nextcloud.talk.webrtc.*
import com.wooplr.spotlight.SpotlightView
import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import me.zhanghai.android.effortlesspermissions.AfterPermissionDenied
import me.zhanghai.android.effortlesspermissions.EffortlessPermissions
import me.zhanghai.android.effortlesspermissions.OpenAppDetailsDialogFragment
import org.apache.commons.lang3.StringEscapeUtils
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.koin.android.ext.android.inject
import org.parceler.Parcel
import org.webrtc.*
import pub.devrel.easypermissions.AfterPermissionGranted
import java.io.IOException
import java.net.CookieManager
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.log10

class CallController(args: Bundle) : BaseController() {

    @JvmField
    @BindView(R.id.callControlEnableSpeaker)
    var callControlEnableSpeaker: ImageView? = null

    @JvmField
    @BindView(R.id.pip_video_view)
    var pipVideoView: SurfaceViewRenderer? = null

    @JvmField
    @BindView(R.id.relative_layout)
    var relativeLayout: RelativeLayout? = null

    @JvmField
    @BindView(R.id.remote_renderers_layout)
    var remoteRenderersLayout: LinearLayout? = null

    @JvmField
    @BindView(R.id.callControlsRelativeLayout)
    var callControls: RelativeLayout? = null

    @JvmField
    @BindView(R.id.call_control_microphone)
    var microphoneControlButton: ImageView? = null

    @JvmField
    @BindView(R.id.call_control_camera)
    var cameraControlButton: ImageView? = null

    @JvmField
    @BindView(R.id.call_control_switch_camera)
    var cameraSwitchButton: ImageView? = null

    @JvmField
    @BindView(R.id.connectingTextView)
    var connectingTextView: TextView? = null

    @JvmField
    @BindView(R.id.connectingRelativeLayoutView)
    var connectingView: RelativeLayout? = null

    @JvmField
    @BindView(R.id.conversationRelativeLayoutView)
    var conversationView: RelativeLayout? = null

    @JvmField
    @BindView(R.id.errorImageView)
    var errorImageView: ImageView? = null

    @JvmField
    @BindView(R.id.progress_bar)
    var progressBar: ProgressBar? = null

    val userUtils: UserUtils by inject()
    val ncApi: NcApi by inject()
    val cookieManager: CookieManager by inject()
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var audioConstraints: MediaConstraints? = null
    private var videoConstraints: MediaConstraints? = null
    private var sdpConstraints: MediaConstraints? = null
    private var sdpConstraintsForMCU: MediaConstraints? = null
    private var audioManager: MagicAudioManager? = null
    private var videoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var videoCapturer: VideoCapturer? = null
    private var rootEglBase: EglBase? = null
    private var signalingDisposable: Disposable? = null
    private var pingDisposable: Disposable? = null
    private var iceServers: MutableList<PeerConnection.IceServer>? = null
    private var cameraEnumerator: CameraEnumerator? = null
    private var roomToken: String
    private val conversationUser: UserNgEntity?
    private var callSession: String? = null
    private var localMediaStream: MediaStream? = null
    private val credentials: String?
    private val magicPeerConnectionWrapperList = ArrayList<MagicPeerConnectionWrapper>()
    private var participantMap: MutableMap<String, Participant> = HashMap()

    private var videoOn = false
    private var audioOn = false

    private var isMultiSession = false
    private var needsPing = true

    private val isVoiceOnlyCall: Boolean
    private val callControlHandler = Handler()
    private val cameraSwitchHandler = Handler()

    private var isPTTActive = false
    private var pulseAnimation: PulseAnimation? = null
    private var videoOnClickListener: View.OnClickListener? = null

    private var baseUrl: String? = null
    private val roomId: String

    private var spotlightView: SpotlightView? = null

    private var externalSignalingServer: ExternalSignalingServer? = null
    private var webSocketClient: MagicWebSocketInstance? = null
    private var webSocketConnectionHelper: WebSocketConnectionHelper? = null
    private var hasMCU: Boolean = false
    private var hasExternalSignalingServer: Boolean = false
    private val conversationPassword: String

    private var recorder: MediaRecorder = MediaRecorder()
    private val timer = Timer()

    private val powerManagerUtils: PowerManagerUtils

    private var handler: Handler? = null

    private var currentCallStatus: CallStatus? = null

    private var mediaPlayer: MediaPlayer? = null

    private val isConnectionEstablished: Boolean
        get() = currentCallStatus == CallStatus.ESTABLISHED || currentCallStatus == CallStatus.IN_CONVERSATION

    init {
        roomId = args.getString(BundleKeys.KEY_ROOM_ID, "")
        roomToken = args.getString(BundleKeys.KEY_CONVERSATION_TOKEN, "")
        conversationUser = args.getParcelable(BundleKeys.KEY_USER_ENTITY)
        conversationPassword = args.getString(BundleKeys.KEY_CONVERSATION_PASSWORD, "")
        isVoiceOnlyCall = args.getBoolean(BundleKeys.KEY_CALL_VOICE_ONLY, false)

        credentials = ApiUtils.getCredentials(conversationUser!!.username, conversationUser.token)

        baseUrl = args.getString(BundleKeys.KEY_MODIFIED_BASE_URL, "")

        if (TextUtils.isEmpty(baseUrl)) {
            baseUrl = conversationUser.baseUrl
        }

        powerManagerUtils = PowerManagerUtils()
        setCallState(CallStatus.CALLING)
    }

    override fun inflateView(
            inflater: LayoutInflater,
            container: ViewGroup
    ): View {
        return inflater.inflate(R.layout.controller_call, container, false)
    }

    private fun createCameraEnumerator() {
        if (activity != null) {
            var camera2EnumeratorIsSupported = false
            try {
                camera2EnumeratorIsSupported = Camera2Enumerator.isSupported(activity)
            } catch (throwable: Throwable) {
                Log.w(TAG, "Camera2Enumator threw an error")
            }

            if (camera2EnumeratorIsSupported) {
                cameraEnumerator = Camera2Enumerator(activity!!)
            } else {
                cameraEnumerator =
                        Camera1Enumerator(MagicWebRTCUtils.shouldEnableVideoHardwareAcceleration())
            }
        }
    }

    override fun onViewBound(view: View) {
        super.onViewBound(view)

        microphoneControlButton!!.setOnTouchListener(MicrophoneButtonTouchListener())
        videoOnClickListener = VideoClickListener()

        pulseAnimation = PulseAnimation.create()
                .with(microphoneControlButton!!)
                .setDuration(310)
                .setRepeatCount(PulseAnimation.INFINITE)
                .setRepeatMode(PulseAnimation.REVERSE)

        setPipVideoViewDimensions()

        callControls!!.z = 100.0f
        basicInitialization()
        initViews()

        initiateCall()
    }

    private fun basicInitialization() {
        rootEglBase = EglBase.create()
        createCameraEnumerator()

        //Create a new PeerConnectionFactory instance.
        val options = PeerConnectionFactory.Options()
        peerConnectionFactory = PeerConnectionFactory.builder()
                .createPeerConnectionFactory()

        peerConnectionFactory!!.setVideoHwAccelerationOptions(
                rootEglBase!!.eglBaseContext,
                rootEglBase!!.eglBaseContext
        )

        //Create MediaConstraints - Will be useful for specifying video and audio constraints.
        audioConstraints = MediaConstraints()
        videoConstraints = MediaConstraints()

        localMediaStream = peerConnectionFactory!!.createLocalMediaStream("NCMS")

        // Create and audio manager that will take care of audio routing,
        // audio modes, audio device enumeration etc.
        audioManager = MagicAudioManager.create(applicationContext, !isVoiceOnlyCall)
        // Store existing audio settings and change audio mode to
        // MODE_IN_COMMUNICATION for best possible VoIP performance.
        Log.d(TAG, "Starting the audio manager...")
        audioManager!!.start(
                MagicAudioManager.AudioManagerEvents { device, availableDevices ->
                    this.onAudioManagerDevicesChanged(
                            device, availableDevices
                    )
                })

        iceServers = mutableListOf()

        //create sdpConstraints
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

        sdpConstraintsForMCU!!.mandatory.add(
                MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false")
        )
        sdpConstraintsForMCU!!.mandatory.add(
                MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false")
        )

        sdpConstraintsForMCU!!.optional.add(
                MediaConstraints.KeyValuePair("internalSctpDataChannels", "true")
        )
        sdpConstraintsForMCU!!.optional.add(
                MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true")
        )

        sdpConstraints!!.optional.add(
                MediaConstraints.KeyValuePair("internalSctpDataChannels", "true")
        )
        sdpConstraints!!.optional.add(MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"))

        if (!isVoiceOnlyCall) {
            cameraInitialization()
        }

        microphoneInitialization()
    }

    private fun handleFromNotification() {
        ncApi.getRooms(credentials, ApiUtils.getUrlForRoomEndpoint(baseUrl))
                .retry(3)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(object : Observer<RoomsOverall> {
                    override fun onSubscribe(d: Disposable) {

                    }

                    override fun onNext(roomsOverall: RoomsOverall) {
                        for (conversation in roomsOverall.ocs?.data!!) {
                            if (roomId == conversation.conversationId) {
                                roomToken = conversation.token.toString()
                                break
                            }
                        }

                        checkPermissions()
                    }

                    override fun onError(e: Throwable) {

                    }

                    override fun onComplete() {

                    }
                })
    }

    private fun initViews() {
        if (isVoiceOnlyCall) {
            callControlEnableSpeaker!!.visibility = View.VISIBLE
            cameraSwitchButton!!.visibility = View.GONE
            cameraControlButton!!.visibility = View.GONE
            pipVideoView!!.visibility = View.GONE
        } else {
            if (cameraEnumerator!!.deviceNames.size < 2) {
                cameraSwitchButton!!.visibility = View.GONE
            }

            pipVideoView!!.init(rootEglBase!!.eglBaseContext, null)
            pipVideoView!!.setZOrderMediaOverlay(true)
            // disabled because it causes some devices to crash
            pipVideoView!!.setEnableHardwareScaler(false)
            pipVideoView!!.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
        }
    }

    private fun checkPermissions() {
        if (isVoiceOnlyCall) {
            onMicrophoneClick()
        } else if (activity != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(PERMISSIONS_CALL, 100)
            } else {
                onRequestPermissionsResult(100, PERMISSIONS_CALL, intArrayOf(1, 1))
            }
        }
    }

    @AfterPermissionGranted(100)
    private fun onPermissionsGranted() {
        if (EffortlessPermissions.hasPermissions(activity, *PERMISSIONS_CALL)) {
            if (!videoOn && !isVoiceOnlyCall) {
                onCameraClick()
            }

            if (!audioOn) {
                onMicrophoneClick()
            }

            if (!isVoiceOnlyCall) {
                if (cameraEnumerator!!.deviceNames.size == 0) {
                    cameraControlButton!!.visibility = View.GONE
                }

                if (cameraEnumerator!!.deviceNames.size > 1) {
                    cameraSwitchButton!!.visibility = View.VISIBLE
                }
            }

            if (!isConnectionEstablished) {
                fetchSignalingSettings()
            }
        } else if (activity != null && EffortlessPermissions.somePermissionPermanentlyDenied(
                        activity!!,
                        *PERMISSIONS_CALL
                )
        ) {
            checkIfSomeAreApproved()
        }
    }

    private fun checkIfSomeAreApproved() {
        if (!isVoiceOnlyCall) {
            if (cameraEnumerator!!.deviceNames.size == 0) {
                cameraControlButton!!.visibility = View.GONE
            }

            if (cameraEnumerator!!.deviceNames.size > 1) {
                cameraSwitchButton!!.visibility = View.VISIBLE
            }

            if (activity != null && EffortlessPermissions.hasPermissions(
                            activity,
                            *PERMISSIONS_CAMERA
                    )
            ) {
                if (!videoOn) {
                    onCameraClick()
                }
            } else {
                cameraControlButton?.setImageResource(R.drawable.ic_videocam_off_white_24px)
                cameraControlButton?.alpha = 0.7f
                cameraSwitchButton?.visibility = View.GONE
            }
        }

        if (EffortlessPermissions.hasPermissions(activity, *PERMISSIONS_MICROPHONE)) {
            if (!audioOn) {
                onMicrophoneClick()
            }
        } else {
            microphoneControlButton?.setImageResource(R.drawable.ic_mic_off_white_24px)
        }

        if (!isConnectionEstablished) {
            fetchSignalingSettings()
        }
    }

    @AfterPermissionDenied(100)
    private fun onPermissionsDenied() {
        if (!isVoiceOnlyCall) {
            if (cameraEnumerator!!.deviceNames.size == 0) {
                cameraControlButton!!.visibility = View.GONE
            } else if (cameraEnumerator!!.deviceNames.size == 1) {
                cameraSwitchButton!!.visibility = View.GONE
            }
        }

        if (activity != null && (EffortlessPermissions.hasPermissions(
                        activity,
                        *PERMISSIONS_CAMERA
                ) || EffortlessPermissions.hasPermissions(activity, *PERMISSIONS_MICROPHONE))
        ) {
            checkIfSomeAreApproved()
        } else if (!isConnectionEstablished) {
            fetchSignalingSettings()
        }
    }

    private fun onAudioManagerDevicesChanged(
            device: MagicAudioManager.AudioDevice,
            availableDevices: Set<MagicAudioManager.AudioDevice>
    ) {
        Log.d(
                TAG, "onAudioManagerDevicesChanged: " + availableDevices + ", "
                + "selected: " + device
        )

        val shouldDisableProximityLock = (device == MagicAudioManager.AudioDevice.WIRED_HEADSET
                || device == MagicAudioManager.AudioDevice.SPEAKER_PHONE
                || device == MagicAudioManager.AudioDevice.BLUETOOTH)

        if (shouldDisableProximityLock) {
            powerManagerUtils.updatePhoneState(
                    PowerManagerUtils.PhoneState.WITHOUT_PROXIMITY_SENSOR_LOCK
            )
        } else {
            powerManagerUtils.updatePhoneState(PowerManagerUtils.PhoneState.WITH_PROXIMITY_SENSOR_LOCK)
        }
    }

    private fun cameraInitialization() {
        videoCapturer = createCameraCapturer(cameraEnumerator!!)

        //Create a VideoSource instance
        if (videoCapturer != null) {
            videoSource = peerConnectionFactory!!.createVideoSource(videoCapturer!!)
            localVideoTrack = peerConnectionFactory!!.createVideoTrack("NCv0", videoSource!!)
            localMediaStream!!.addTrack(localVideoTrack!!)
            localVideoTrack!!.setEnabled(false)
            localVideoTrack!!.addSink(pipVideoView)
        }
    }

    private fun microphoneInitialization() {
        //create an AudioSource instance
        audioSource = peerConnectionFactory!!.createAudioSource(audioConstraints)
        localAudioTrack = peerConnectionFactory!!.createAudioTrack("NCa0", audioSource!!)
        localAudioTrack!!.setEnabled(false)
        localMediaStream!!.addTrack(localAudioTrack!!)
    }

    private fun createCameraCapturer(enumerator: CameraEnumerator): VideoCapturer? {
        val deviceNames = enumerator.deviceNames

        // First, try to find front facing camera
        Logging.d(TAG, "Looking for front facing cameras.")
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating front facing camera capturer.")
                val videoCapturer = enumerator.createCapturer(deviceName, null)
                if (videoCapturer != null) {
                    pipVideoView!!.setMirror(true)
                    return videoCapturer
                }
            }
        }

        // Front facing camera not found, try something else
        Logging.d(TAG, "Looking for other cameras.")
        for (deviceName in deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating other camera capturer.")
                val videoCapturer = enumerator.createCapturer(deviceName, null)

                if (videoCapturer != null) {
                    pipVideoView!!.setMirror(false)
                    return videoCapturer
                }
            }
        }

        return null
    }

    @OnLongClick(R.id.call_control_microphone)
    internal fun onMicrophoneLongClick(): Boolean {
        if (!audioOn) {
            callControlHandler.removeCallbacksAndMessages(null)
            cameraSwitchHandler.removeCallbacksAndMessages(null)
            isPTTActive = true
            callControls!!.visibility = View.VISIBLE
            if (!isVoiceOnlyCall) {
                cameraSwitchButton!!.visibility = View.VISIBLE
            }
        }

        onMicrophoneClick()
        return true
    }

    @OnClick(R.id.callControlEnableSpeaker)
    fun onEnableSpeakerphoneClick() {
        if (audioManager != null) {
            audioManager!!.toggleUseSpeakerphone()
            if (audioManager!!.isSpeakerphoneAutoOn) {
                callControlEnableSpeaker?.setImageResource(R.drawable.ic_volume_up_white_24dp)
            } else {
                callControlEnableSpeaker?.setImageResource(R.drawable.ic_volume_mute_white_24dp)
            }
        }
    }

    @OnClick(R.id.call_control_microphone)
    fun onMicrophoneClick() {
        if (activity != null && EffortlessPermissions.hasPermissions(
                        activity,
                        *PERMISSIONS_MICROPHONE
                )
        ) {

            if (activity != null && !appPreferences.pushToTalkIntroShown) {
                spotlightView = SpotlightView.Builder(activity!!)
                        .introAnimationDuration(300)
                        .enableRevealAnimation(true)
                        .performClick(false)
                        .fadeinTextDuration(400)
                        .headingTvColor(resources!!.getColor(R.color.colorPrimary))
                        .headingTvSize(20)
                        .headingTvText(resources!!.getString(R.string.nc_push_to_talk))
                        .subHeadingTvColor(resources!!.getColor(R.color.bg_default))
                        .subHeadingTvSize(16)
                        .subHeadingTvText(resources!!.getString(R.string.nc_push_to_talk_desc))
                        .maskColor(Color.parseColor("#dc000000"))
                        .target(microphoneControlButton)
                        .lineAnimDuration(400)
                        .lineAndArcColor(resources!!.getColor(R.color.colorPrimary))
                        .enableDismissAfterShown(true)
                        .dismissOnBackPress(true)
                        .usageId("pushToTalk")
                        .show()

                appPreferences.pushToTalkIntroShown = true
            }

            if (!isPTTActive) {
                audioOn = !audioOn

                if (audioOn) {
                    microphoneControlButton?.setImageResource(R.drawable.ic_mic_white_24px)
                } else {
                    microphoneControlButton?.setImageResource(R.drawable.ic_mic_off_white_24px)
                }

                toggleMedia(audioOn, false, false)
            } else {
                microphoneControlButton?.setImageResource(R.drawable.ic_mic_white_24px)
                pulseAnimation!!.start()
                toggleMedia(true, false, false)
            }

            if (isVoiceOnlyCall && !isConnectionEstablished) {
                fetchSignalingSettings()
            }
        } else if (activity != null && EffortlessPermissions.somePermissionPermanentlyDenied(
                        activity!!,
                        *PERMISSIONS_MICROPHONE
                )
        ) {
            // Microphone permission is permanently denied so we cannot request it normally.

            OpenAppDetailsDialogFragment.show(
                    R.string.nc_microphone_permission_permanently_denied,
                    R.string.nc_permissions_settings, (activity as AppCompatActivity?)!!
            )
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(PERMISSIONS_MICROPHONE, 100)
            } else {
                onRequestPermissionsResult(100, PERMISSIONS_MICROPHONE, intArrayOf(1))
            }
        }
    }

    @OnClick(R.id.callControlHangupView)
    internal fun onHangupClick() {
        setCallState(CallStatus.LEAVING)
        hangup(true)
    }

    @OnClick(R.id.call_control_camera)
    fun onCameraClick() {
        if (activity != null && EffortlessPermissions.hasPermissions(
                        activity,
                        *PERMISSIONS_CAMERA
                )
        ) {
            videoOn = !videoOn

            if (videoOn) {
                cameraControlButton?.setImageResource(R.drawable.ic_videocam_white_24px)
                if (cameraEnumerator!!.deviceNames.size > 1) {
                    cameraSwitchButton!!.visibility = View.VISIBLE
                }
            } else {
                cameraControlButton?.setImageResource(R.drawable.ic_videocam_off_white_24px)
                cameraSwitchButton!!.visibility = View.GONE
            }

            toggleMedia(videoOn, true, false)
        } else if (activity != null && EffortlessPermissions.somePermissionPermanentlyDenied(
                        activity!!,
                        *PERMISSIONS_CAMERA
                )
        ) {
            // Camera permission is permanently denied so we cannot request it normally.
            OpenAppDetailsDialogFragment.show(
                    R.string.nc_camera_permission_permanently_denied,
                    R.string.nc_permissions_settings, (activity as AppCompatActivity?)!!
            )
        } else {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(PERMISSIONS_CAMERA, 100)
            } else {
                onRequestPermissionsResult(100, PERMISSIONS_CAMERA, intArrayOf(1))
            }
        }
    }

    @OnClick(R.id.call_control_switch_camera, R.id.pip_video_view)
    fun switchCamera() {
        val cameraVideoCapturer = videoCapturer as CameraVideoCapturer?
        cameraVideoCapturer?.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(currentCameraIsFront: Boolean) {
                pipVideoView!!.setMirror(currentCameraIsFront)
            }

            override fun onCameraSwitchError(s: String) {

            }
        })
    }

    private fun toggleMedia(
            enable: Boolean,
            video: Boolean,
            silencedByModerator: Boolean = false
    ) {
        var message: String
        val alreadySilenced = microphoneControlButton?.alpha == 0.7f
        if (video) {
            message = "videoOff"
            if (enable) {
                cameraControlButton!!.alpha = 1.0f
                message = "videoOn"
                startVideoCapture()
            } else {
                cameraControlButton!!.alpha = 0.7f
                if (videoCapturer != null) {
                    try {
                        videoCapturer!!.stopCapture()
                    } catch (e: InterruptedException) {
                        Log.d(TAG, "Failed to stop capturing video while sensor is near the ear")
                    }

                }
            }

            if (localMediaStream != null && localMediaStream!!.videoTracks.size > 0) {
                localMediaStream!!.videoTracks[0].setEnabled(enable)
            }
            if (enable) {
                pipVideoView!!.visibility = View.VISIBLE
            } else {
                pipVideoView!!.visibility = View.INVISIBLE
            }
        } else {
            message = "audioOff"
            if (enable) {
                message = "audioOn"
                microphoneControlButton!!.alpha = 1.0f
            } else {
                microphoneControlButton!!.alpha = 0.7f
            }

            if (localMediaStream != null && localMediaStream!!.audioTracks.size > 0) {
                localMediaStream!!.audioTracks[0].setEnabled(enable)
            }
        }

        if (!alreadySilenced && !enable && !video && silencedByModerator) {
            microphoneControlButton?.setImageResource(R.drawable.ic_mic_off_white_24px)
            Toast.makeText(context, R.string.silenced_by_moderator, Toast.LENGTH_SHORT).show()
        }

        sendDataChannelMessage(message)
    }

    private fun sendDataChannelMessage(message: String) {
        if (isConnectionEstablished) {
            if (!hasMCU) {
                for (i in magicPeerConnectionWrapperList.indices) {
                    magicPeerConnectionWrapperList[i].sendChannelData(DataChannelMessage(message))
                }
            } else {
                for (i in magicPeerConnectionWrapperList.indices) {
                    if (magicPeerConnectionWrapperList[i]
                                    .sessionId == webSocketClient!!.sessionId
                    ) {
                        magicPeerConnectionWrapperList[i].sendChannelData(DataChannelMessage(message))
                        break
                    }
                }
            }
        }
    }

    private fun startListening() {
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
        timer.scheduleAtFixedRate(RecorderTask(recorder), 0, 1000)
        recorder.setOutputFile("/dev/null")

        try {
            recorder.prepare()
            recorder.start()
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    inner class RecorderTask(private val recorder: MediaRecorder) : TimerTask() {
        private var speaking = false
        override fun run() {
            if (isConnectionEstablished) {
                val amplitude: Int = recorder.maxAmplitude
                val amplitudeDb = 20 * log10(abs(amplitude).toDouble())
                if (amplitudeDb >= 50) {
                    if (!speaking) {
                        speaking = true
                        sendDataChannelMessage("speaking")
                    }
                } else {
                    if (speaking) {
                        speaking = false
                        sendDataChannelMessage("stoppedSpeaking")
                    }
                }
            }
        }
    }

    private fun animateCallControls(
            show: Boolean,
            startDelay: Long
    ) {
        if (isVoiceOnlyCall) {
            if (spotlightView != null && spotlightView!!.visibility != View.GONE) {
                spotlightView!!.visibility = View.GONE
            }
        } else if (!isPTTActive) {
            val alpha: Float
            val duration: Long

            if (show) {
                callControlHandler.removeCallbacksAndMessages(null)
                cameraSwitchHandler.removeCallbacksAndMessages(null)
                alpha = 1.0f
                duration = 1000
                if (callControls!!.visibility != View.VISIBLE) {
                    callControls!!.alpha = 0.0f
                    callControls!!.visibility = View.VISIBLE

                    cameraSwitchButton!!.alpha = 0.0f
                    cameraSwitchButton!!.visibility = View.VISIBLE
                } else {
                    callControlHandler.postDelayed({ animateCallControls(false, 0) }, 5000)
                    return
                }
            } else {
                alpha = 0.0f
                duration = 1000
            }

            if (callControls != null) {
                callControls!!.isEnabled = false
                callControls!!.animate()
                        .translationY(0f)
                        .alpha(alpha)
                        .setDuration(duration)
                        .setStartDelay(startDelay)
                        .setListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                super.onAnimationEnd(animation)
                                if (callControls != null) {
                                    if (!show) {
                                        callControls!!.visibility = View.GONE
                                        if (spotlightView != null && spotlightView!!.visibility != View.GONE) {
                                            spotlightView!!.visibility = View.GONE
                                        }
                                    } else {
                                        callControlHandler.postDelayed({
                                            if (!isPTTActive) {
                                                animateCallControls(false, 0)
                                            }
                                        }, 7500)
                                    }

                                    callControls!!.isEnabled = true
                                }
                            }
                        })
            }

            if (cameraSwitchButton != null) {
                cameraSwitchButton!!.isEnabled = false
                cameraSwitchButton!!.animate()
                        .translationY(0f)
                        .alpha(alpha)
                        .setDuration(duration)
                        .setStartDelay(startDelay)
                        .setListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                super.onAnimationEnd(animation)
                                if (cameraSwitchButton != null) {
                                    if (!show) {
                                        cameraSwitchButton!!.visibility = View.GONE
                                    }

                                    cameraSwitchButton!!.isEnabled = true
                                }
                            }
                        })
            }
        }
    }

    public override fun onDestroy() {
        if (currentCallStatus != CallStatus.LEAVING) {
            onHangupClick()
        }
        powerManagerUtils.updatePhoneState(PowerManagerUtils.PhoneState.IDLE)
        super.onDestroy()
    }

    private fun fetchSignalingSettings() {
        ncApi.getSignalingSettings(credentials, ApiUtils.getUrlForSignalingSettings(baseUrl))
                .subscribeOn(Schedulers.io())
                .retry(3)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(object : Observer<SignalingSettingsOverall> {
                    override fun onSubscribe(d: Disposable) {

                    }

                    override fun onNext(signalingSettingsOverall: SignalingSettingsOverall) {
                        var iceServer: IceServer
                        if (signalingSettingsOverall.ocs != null &&
                                signalingSettingsOverall.ocs.signalingSettings != null
                        ) {

                            externalSignalingServer = ExternalSignalingServer()

                            if (!TextUtils.isEmpty(
                                            signalingSettingsOverall.ocs.signalingSettings.externalSignalingServer
                                    ) && !TextUtils.isEmpty(
                                            signalingSettingsOverall.ocs
                                                    .signalingSettings
                                                    .externalSignalingTicket
                                    )
                            ) {
                                externalSignalingServer = ExternalSignalingServer()
                                externalSignalingServer!!.externalSignalingServer = signalingSettingsOverall.ocs.signalingSettings.externalSignalingServer
                                externalSignalingServer!!.externalSignalingTicket = signalingSettingsOverall.ocs.signalingSettings.externalSignalingTicket
                                hasExternalSignalingServer = true
                            } else {
                                hasExternalSignalingServer = false
                            }

                            if (signalingSettingsOverall.ocs.signalingSettings.stunServers != null) {
                                for (i in 0 until signalingSettingsOverall.ocs.signalingSettings.stunServers!!.size) {
                                    iceServer = signalingSettingsOverall.ocs.signalingSettings.stunServers!![i]
                                    if (TextUtils.isEmpty(iceServer.username) || TextUtils.isEmpty(
                                                    iceServer
                                                            .credential
                                            )
                                    ) {
                                        iceServers!!.add(PeerConnection.IceServer(iceServer.url))
                                    } else {
                                        iceServers!!.add(
                                                PeerConnection.IceServer(
                                                        iceServer.url,
                                                        iceServer.username, iceServer.credential
                                                )
                                        )
                                    }
                                }
                            }

                            if (signalingSettingsOverall.ocs.signalingSettings.turnServers != null) {
                                for (i in 0 until signalingSettingsOverall.ocs.signalingSettings.turnServers!!.size) {
                                    iceServer = signalingSettingsOverall.ocs.signalingSettings.turnServers!![i]
                                    for (j in 0 until iceServer.urls!!.size) {
                                        if (TextUtils.isEmpty(iceServer.username) || TextUtils.isEmpty(
                                                        iceServer
                                                                .credential
                                                )
                                        ) {
                                            iceServers!!.add(PeerConnection.IceServer(iceServer.urls!![j]))
                                        } else {
                                            iceServers!!.add(
                                                    PeerConnection.IceServer(
                                                            iceServer.urls!![j],
                                                            iceServer.username, iceServer.credential
                                                    )
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        checkCapabilities()
                    }

                    override fun onError(e: Throwable) {}

                    override fun onComplete() {

                    }
                })
    }

    private fun checkCapabilities() {
        ncApi.getCapabilities(credentials, ApiUtils.getUrlForCapabilities(baseUrl))
                .retry(3)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(object : Observer<CapabilitiesOverall> {
                    override fun onSubscribe(d: Disposable) {

                    }

                    override fun onNext(capabilitiesOverall: CapabilitiesOverall) {
                        isMultiSession = capabilitiesOverall.ocs.data
                                .capabilities.spreedCapability
                                ?.features?.contains("multi-room-users") == true

                        needsPing = capabilitiesOverall.ocs.data
                                .capabilities.spreedCapability
                                ?.features?.contains("no-ping") == false

                        if (!hasExternalSignalingServer) {
                            joinRoomAndCall()
                        } else {
                            setupAndInitiateWebSocketsConnection()
                        }
                    }

                    override fun onError(e: Throwable) {
                        isMultiSession = false
                    }

                    override fun onComplete() {

                    }
                })
    }

    private fun joinRoomAndCall() {
        ncApi.joinRoom(
                        credentials, ApiUtils.getUrlForSettingMyselfAsActiveParticipant(
                        baseUrl,
                        roomToken
                ), conversationPassword
                )
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .retry(3)
                .subscribe(object : Observer<ConversationOverall> {
                    override fun onSubscribe(d: Disposable) {

                    }

                    override fun onNext(conversationOverall: ConversationOverall) {
                        callSession = conversationOverall.ocs.data
                                .sessionId
                        callOrJoinRoomViaWebSocket()
                    }

                    override fun onError(e: Throwable) {

                    }

                    override fun onComplete() {

                    }
                })
    }

    private fun callOrJoinRoomViaWebSocket() {
        if (!hasExternalSignalingServer) {
            performCall()
        } else {
            webSocketClient!!.joinRoomWithRoomTokenAndSession(roomToken, callSession)
        }
    }

    private fun performCall() {
        ncApi.joinCall(
                        credentials,
                        ApiUtils.getUrlForCall(baseUrl, roomToken)
                )
                .subscribeOn(Schedulers.io())
                .retry(3)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(object : Observer<GenericOverall> {
                    override fun onSubscribe(d: Disposable) {

                    }

                    override fun onNext(genericOverall: GenericOverall) {
                        if (currentCallStatus != CallStatus.LEAVING) {
                            setCallState(CallStatus.ESTABLISHED)

                            if (needsPing) {
                                ncApi.pingCall(credentials, ApiUtils.getUrlForCallPing(baseUrl, roomToken))
                                        .subscribeOn(Schedulers.io())
                                        .observeOn(AndroidSchedulers.mainThread())
                                        .repeatWhen { observable -> observable.delay(5000, TimeUnit.MILLISECONDS) }
                                        .takeWhile { observable -> isConnectionEstablished }
                                        .retry(3) { observable -> isConnectionEstablished }
                                        .subscribe(object : Observer<GenericOverall> {
                                            override fun onSubscribe(d: Disposable) {
                                                pingDisposable = d
                                            }

                                            override fun onNext(genericOverall: GenericOverall) {

                                            }

                                            override fun onError(e: Throwable) {
                                                dispose(pingDisposable)
                                            }

                                            override fun onComplete() {
                                                dispose(pingDisposable)
                                            }
                                        })
                            }

                            // Start pulling signaling messages
                            var urlToken: String? = null
                            if (isMultiSession) {
                                urlToken = roomToken
                            }

                            if (!conversationUser!!.hasSpreedFeatureCapability("no-ping") && !TextUtils.isEmpty(
                                            roomId
                                    )
                            ) {
                                NotificationUtils.cancelExistingNotificationsForRoom(
                                        applicationContext, conversationUser, roomId
                                )
                            } else if (!TextUtils.isEmpty(roomToken)) {
                                NotificationUtils.cancelExistingNotificationsForRoom(
                                        applicationContext, conversationUser, roomToken
                                )
                            }

                            if (!hasExternalSignalingServer) {
                                ncApi.pullSignalingMessages(
                                                credentials,
                                                ApiUtils.getUrlForSignaling(baseUrl, urlToken)
                                        )
                                        .subscribeOn(Schedulers.io())
                                        .observeOn(AndroidSchedulers.mainThread())
                                        .repeatWhen { observable -> observable }
                                        .takeWhile { observable -> isConnectionEstablished }
                                        .retry(3) { observable -> isConnectionEstablished }
                                        .subscribe(object : Observer<SignalingOverall> {
                                            override fun onSubscribe(d: Disposable) {
                                                signalingDisposable = d
                                            }

                                            override fun onNext(signalingOverall: SignalingOverall) {
                                                if (signalingOverall.ocs.signalings != null) {
                                                    for (i in 0 until signalingOverall.ocs.signalings.size) {
                                                        try {
                                                            receivedSignalingMessage(
                                                                    signalingOverall.ocs.signalings[i]
                                                            )
                                                        } catch (e: IOException) {
                                                            Log.e(TAG, "Failed to process received signaling" + " message")
                                                        }

                                                    }
                                                }
                                            }

                                            override fun onError(e: Throwable) {
                                                dispose(signalingDisposable)
                                            }

                                            override fun onComplete() {
                                                dispose(signalingDisposable)
                                            }
                                        })
                            }
                        }
                    }

                    override fun onError(e: Throwable) {}

                    override fun onComplete() {

                    }
                })
    }

    private fun setupAndInitiateWebSocketsConnection() {
        if (webSocketConnectionHelper == null) {
            webSocketConnectionHelper = WebSocketConnectionHelper()
        }

        if (webSocketClient == null) {
            webSocketClient = WebSocketConnectionHelper.getExternalSignalingInstanceForServer(
                    externalSignalingServer!!.externalSignalingServer!!,
                    conversationUser!!, externalSignalingServer!!.externalSignalingTicket,
                    TextUtils.isEmpty(credentials)
            )
        } else {
            if (webSocketClient!!.isConnected && currentCallStatus == CallStatus.PUBLISHER_FAILED) {
                webSocketClient!!.restartWebSocket()
            }
        }

        joinRoomAndCall()
    }

    private fun initiateCall() {
        if (!TextUtils.isEmpty(roomToken)) {
            checkPermissions()
        } else {
            handleFromNotification()
        }
    }

    override fun onDetach(view: View) {
        eventBus.unregister(this)
        super.onDetach(view)
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        eventBus.register(this)
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    fun onMessageEvent(webSocketCommunicationEvent: WebSocketCommunicationEvent) {
        when (webSocketCommunicationEvent.type) {
            "hello" -> if (!webSocketCommunicationEvent.hashMap!!.containsKey("oldResumeId")) {
                if (currentCallStatus == CallStatus.RECONNECTING) {
                    hangup(false)
                } else {
                    initiateCall()
                }
            } else {
            }
            "roomJoined" -> {
                startSendingNick()

                if (webSocketCommunicationEvent.hashMap!!["roomToken"] == roomToken) {
                    performCall()
                }
            }
            "participantsUpdate" -> if (webSocketCommunicationEvent.hashMap!!["roomToken"] == roomToken) {
                processUsersInRoom(
                        webSocketClient!!.getJobWithId(
                                Integer.valueOf(webSocketCommunicationEvent.hashMap["jobId"]!!)
                        ) as List<HashMap<String, Any>>
                )
            }
            "signalingMessage" -> processMessage(
                    webSocketClient!!.getJobWithId(
                            Integer.valueOf(webSocketCommunicationEvent.hashMap!!["jobId"]!!)
                    ) as NCSignalingMessage
            )
            "peerReadyForRequestingOffer" -> webSocketCommunicationEvent.hashMap!!["sessionId"]?.let {
                webSocketClient!!.requestOfferForSessionIdWithType(
                        it, "video"
                )
            }
            "mutedByModerator" -> {
                toggleMedia(enable = false, video = false, silencedByModerator = true)
            }
        }
    }

    @OnClick(R.id.pip_video_view, R.id.remote_renderers_layout)
    fun showCallControls() {
        animateCallControls(true, 0)
    }

    private fun dispose(disposable: Disposable?) {
        if (disposable != null && !disposable.isDisposed) {
            disposable.dispose()
        } else if (disposable == null) {

            if (pingDisposable != null && !pingDisposable!!.isDisposed) {
                pingDisposable!!.dispose()
                pingDisposable = null
            }

            if (signalingDisposable != null && !signalingDisposable!!.isDisposed) {
                signalingDisposable!!.dispose()
                signalingDisposable = null
            }
        }
    }

    @Throws(IOException::class)
    private fun receivedSignalingMessage(signaling: Signaling) {
        val messageType = signaling.type

        if (!isConnectionEstablished && currentCallStatus != CallStatus.CALLING) {
            return
        }

        if ("usersInRoom" == messageType) {
            processUsersInRoom(signaling.messageWrapper as List<HashMap<String, Any>>)
        } else if ("message" == messageType) {
            val ncSignalingMessage = LoganSquare.parse(
                    signaling.messageWrapper.toString(),
                    NCSignalingMessage::class.java
            )
            processMessage(ncSignalingMessage)
        } else {
            Log.d(TAG, "Something went very very wrong")
        }
    }

    private fun processMessage(ncSignalingMessage: NCSignalingMessage) {
        if (ncSignalingMessage.roomType == "video" || ncSignalingMessage.roomType == "screen") {
            val magicPeerConnectionWrapper = getPeerConnectionWrapperForSessionIdAndType(
                    ncSignalingMessage.from,
                    ncSignalingMessage.roomType, false
            )

            var type: String? = null
            if (ncSignalingMessage.payload != null && ncSignalingMessage.payload.type != null) {
                type = ncSignalingMessage.payload.type
            } else if (ncSignalingMessage.type != null) {
                type = ncSignalingMessage.type
            }

            if (type != null) {
                when (type) {
                    "unshareScreen" -> endPeerConnection(ncSignalingMessage.from, true)
                    "offer", "answer" -> {
                        magicPeerConnectionWrapper.nick = ncSignalingMessage.payload.nick
                        val sessionDescriptionWithPreferredCodec: SessionDescription

                        val sessionDescriptionStringWithPreferredCodec = MagicWebRTCUtils.preferCodec(
                                ncSignalingMessage.payload.sdp,
                                "H264", false
                        )

                        sessionDescriptionWithPreferredCodec = SessionDescription(
                                SessionDescription.Type.fromCanonicalForm(type),
                                sessionDescriptionStringWithPreferredCodec
                        )

                        if (magicPeerConnectionWrapper.peerConnection != null) {
                            magicPeerConnectionWrapper.peerConnection!!
                                    .setRemoteDescription(
                                            magicPeerConnectionWrapper
                                                    .magicSdpObserver, sessionDescriptionWithPreferredCodec
                                    )
                        }
                    }
                    "candidate" -> {
                        val ncIceCandidate = ncSignalingMessage.payload.iceCandidate
                        val iceCandidate = IceCandidate(
                                ncIceCandidate.sdpMid,
                                ncIceCandidate.sdpMLineIndex, ncIceCandidate.candidate
                        )
                        magicPeerConnectionWrapper.addCandidate(iceCandidate)
                    }
                    "endOfCandidates" -> magicPeerConnectionWrapper.drainIceCandidates()
                    "control" -> {
                        when (ncSignalingMessage.payload.action) {
                            "forceMute" -> {
                                if (ncSignalingMessage.payload.peerId == callSession) {
                                    toggleMedia(false, video = false, silencedByModerator = true)
                                }
                            }
                            else -> {}
                        }
                    }
                    else -> {
                    }
                }
            }
        } else {
            Log.d(TAG, "Something went very very wrong")
        }
    }

    private fun hangup(shutDownView: Boolean) {
        stopCallingSound()
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

            if (pipVideoView != null) {
                pipVideoView!!.release()
            }

            if (audioSource != null) {
                audioSource!!.dispose()
                audioSource = null
            }

            if (audioManager != null) {
                audioManager!!.stop()
                audioManager = null
            }

            if (videoSource != null) {
                videoSource = null
            }

            if (peerConnectionFactory != null) {
                peerConnectionFactory = null
            }

            localMediaStream = null
            localAudioTrack = null
            localVideoTrack = null

            if (TextUtils.isEmpty(credentials) && hasExternalSignalingServer) {
                WebSocketConnectionHelper.deleteExternalSignalingInstanceForUserEntity(-1)
            }
        }

        for (i in magicPeerConnectionWrapperList.indices) {
            endPeerConnection(magicPeerConnectionWrapperList[i].sessionId, false)
        }

        timer.cancel()
        try {
            recorder.stop()
            recorder.release()
        } catch (e: Exception) {
            // do nothing
        }

        hangupNetworkCalls(shutDownView)
    }

    private fun hangupNetworkCalls(shutDownView: Boolean) {
        ncApi.leaveCall(credentials, ApiUtils.getUrlForCall(baseUrl, roomToken))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(object : Observer<GenericOverall> {
                    override fun onSubscribe(d: Disposable) {

                    }

                    override fun onNext(genericOverall: GenericOverall) {
                        if (!TextUtils.isEmpty(credentials) && hasExternalSignalingServer) {
                            webSocketClient!!.joinRoomWithRoomTokenAndSession("", callSession)
                        }

                        if (isMultiSession) {
                            if (shutDownView && activity != null) {
                                activity!!.finish()
                            } else if (!shutDownView && (currentCallStatus == CallStatus.RECONNECTING || currentCallStatus == CallStatus.PUBLISHER_FAILED)) {
                                initiateCall()
                            }
                        } else {
                            leaveRoom(shutDownView)
                        }
                    }

                    override fun onError(e: Throwable) {

                    }

                    override fun onComplete() {

                    }
                })
    }

    private fun leaveRoom(shutDownView: Boolean) {
        ncApi.leaveRoom(
                        credentials,
                        ApiUtils.getUrlForSettingMyselfAsActiveParticipant(baseUrl, roomToken)
                )
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(object : Observer<GenericOverall> {
                    override fun onSubscribe(d: Disposable) {

                    }

                    override fun onNext(genericOverall: GenericOverall) {
                        if (shutDownView && activity != null) {
                            activity!!.finish()
                        }
                    }

                    override fun onError(e: Throwable) {

                    }

                    override fun onComplete() {

                    }
                })
    }

    private fun startVideoCapture() {
        if (videoCapturer != null) {
            videoCapturer!!.startCapture(1280, 720, 30)
        }
    }

    private fun processUsersInRoom(users: List<HashMap<String, Any>>) {
        val newSessions = ArrayList<String>()
        val oldSesssions = HashSet<String>()

        for (participant in users) {
            if (participant["sessionId"] != callSession) {
                val inCallObject = participant["inCall"]
                val isNewSession: Boolean
                if (inCallObject is Boolean) {
                    isNewSession = inCallObject
                } else {
                    isNewSession = inCallObject as Long != 0L
                }

                if (isNewSession) {
                    newSessions.add(participant["sessionId"]!!.toString())
                } else {
                    oldSesssions.add(participant["sessionId"]!!.toString())
                }
            }
        }

        for (magicPeerConnectionWrapper in magicPeerConnectionWrapperList) {
            if (!magicPeerConnectionWrapper.isMCUPublisher) {
                oldSesssions.add(magicPeerConnectionWrapper.sessionId)
            }
        }

        // Calculate sessions that left the call
        oldSesssions.removeAll(newSessions)

        // Calculate sessions that join the call
        newSessions.removeAll(oldSesssions)

        if (!isConnectionEstablished && currentCallStatus != CallStatus.CALLING) {
            return
        }

        if (newSessions.size > 0 && !hasMCU) {
            getPeersForCall()
        }

        hasMCU = hasExternalSignalingServer && webSocketClient != null && webSocketClient!!.hasMCU()

        for (sessionId in newSessions) {
            getPeerConnectionWrapperForSessionIdAndType(
                    sessionId, "video",
                    hasMCU && sessionId == webSocketClient!!.sessionId
            )
        }

        if (newSessions.size > 0 && currentCallStatus != CallStatus.IN_CONVERSATION) {
            setCallState(CallStatus.IN_CONVERSATION)
        }

        for (sessionId in oldSesssions) {
            endPeerConnection(sessionId, false)
        }
    }

    private fun getPeersForCall() {
        ncApi.getPeersForCall(credentials, ApiUtils.getUrlForCall(baseUrl, roomToken))
                .subscribeOn(Schedulers.io())
                .subscribe(object : Observer<ParticipantsOverall> {
                    override fun onSubscribe(d: Disposable) {

                    }

                    override fun onNext(participantsOverall: ParticipantsOverall) {
                        participantMap = HashMap()
                        for (participant in participantsOverall.ocs.data) {
                            participantMap[participant.sessionId!!] = participant
                            if (activity != null) {
                                activity!!.runOnUiThread { setupAvatarForSession(participant.sessionId!!) }
                            }
                        }
                    }

                    override fun onError(e: Throwable) {

                    }

                    override fun onComplete() {

                    }
                })
    }

    private fun deleteMagicPeerConnection(magicPeerConnectionWrapper: MagicPeerConnectionWrapper) {
        magicPeerConnectionWrapper.removePeerConnection()
        magicPeerConnectionWrapperList.remove(magicPeerConnectionWrapper)
    }

    private fun getPeerConnectionWrapperForSessionId(
            sessionId: String,
            type: String
    ): MagicPeerConnectionWrapper? {
        for (i in magicPeerConnectionWrapperList.indices) {
            if (magicPeerConnectionWrapperList[i].sessionId == sessionId && magicPeerConnectionWrapperList[i].videoStreamType == type) {
                return magicPeerConnectionWrapperList[i]
            }
        }

        return null
    }

    private fun getPeerConnectionWrapperForSessionIdAndType(
            sessionId: String,
            type: String,
            publisher: Boolean
    ): MagicPeerConnectionWrapper {
        var magicPeerConnectionWrapper: MagicPeerConnectionWrapper? = getPeerConnectionWrapperForSessionId(sessionId, type)
        if (magicPeerConnectionWrapper != null) {
            return magicPeerConnectionWrapper
        } else {
            if (hasMCU && publisher) {
                magicPeerConnectionWrapper = MagicPeerConnectionWrapper(
                        peerConnectionFactory!!,
                        iceServers, sdpConstraintsForMCU!!, sessionId, callSession, localMediaStream, true, true,
                        type
                )
            } else if (hasMCU) {
                magicPeerConnectionWrapper = MagicPeerConnectionWrapper(
                        peerConnectionFactory!!,
                        iceServers, sdpConstraints!!, sessionId, callSession, null, false, true, type
                )
            } else {
                if ("screen" != type) {
                    magicPeerConnectionWrapper = MagicPeerConnectionWrapper(
                            peerConnectionFactory!!,
                            iceServers, sdpConstraints!!, sessionId, callSession, localMediaStream, false, false,
                            type
                    )
                } else {
                    magicPeerConnectionWrapper = MagicPeerConnectionWrapper(
                            peerConnectionFactory!!,
                            iceServers, sdpConstraints!!, sessionId, callSession, null, false, false, type
                    )
                }
            }

            magicPeerConnectionWrapperList.add(magicPeerConnectionWrapper)

            if (publisher) {
                startSendingNick()
            }

            return magicPeerConnectionWrapper
        }
    }

    private fun getPeerConnectionWrapperListForSessionId(
            sessionId: String
    ): List<MagicPeerConnectionWrapper> {
        val internalList = ArrayList<MagicPeerConnectionWrapper>()
        for (magicPeerConnectionWrapper in magicPeerConnectionWrapperList) {
            if (magicPeerConnectionWrapper.sessionId == sessionId) {
                internalList.add(magicPeerConnectionWrapper)
            }
        }

        return internalList
    }

    private fun endPeerConnection(
            sessionId: String,
            justScreen: Boolean
    ) {
        val magicPeerConnectionWrappers: List<MagicPeerConnectionWrapper> = getPeerConnectionWrapperListForSessionId(sessionId)
        var magicPeerConnectionWrapper: MagicPeerConnectionWrapper
        if (!magicPeerConnectionWrappers.isEmpty() && activity != null
        ) {
            for (i in magicPeerConnectionWrappers.indices) {
                magicPeerConnectionWrapper = magicPeerConnectionWrappers[i]
                if (magicPeerConnectionWrapper.sessionId == sessionId) {
                    if (magicPeerConnectionWrapper.videoStreamType == "screen" || !justScreen) {
                        activity!!.runOnUiThread {
                            removeMediaStream(
                                    sessionId + "+" +
                                            magicPeerConnectionWrapper.videoStreamType
                            )
                        }
                        deleteMagicPeerConnection(magicPeerConnectionWrapper)
                    }
                }
            }
        }
    }

    private fun removeMediaStream(sessionId: String) {
        if (remoteRenderersLayout != null && remoteRenderersLayout!!.childCount > 0) {
            val relativeLayout = remoteRenderersLayout!!.findViewWithTag<RelativeLayout>(sessionId)
            if (relativeLayout != null) {
                val surfaceViewRenderer =
                        relativeLayout.findViewById<SurfaceViewRenderer>(R.id.surface_view)
                surfaceViewRenderer.release()
                remoteRenderersLayout!!.removeView(relativeLayout)
                remoteRenderersLayout!!.invalidate()
            }
        }

        if (callControls != null) {
            callControls!!.z = 100.0f
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(configurationChangeEvent: ConfigurationChangeEvent) {
        powerManagerUtils.setOrientation(resources!!.configuration.orientation)

        if (resources!!.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            remoteRenderersLayout!!.orientation = LinearLayout.HORIZONTAL
        } else if (resources!!.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            remoteRenderersLayout!!.orientation = LinearLayout.VERTICAL
        }

        setPipVideoViewDimensions()

        cookieManager.cookieStore.removeAll()
    }

    private fun setPipVideoViewDimensions() {
        val layoutParams = pipVideoView!!.layoutParams as FrameLayout.LayoutParams

        if (resources!!.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            remoteRenderersLayout!!.orientation = LinearLayout.HORIZONTAL
            layoutParams.height = resources!!.getDimension(R.dimen.large_preview_dimension)
                    .toInt()
            layoutParams.width = FrameLayout.LayoutParams.WRAP_CONTENT
            pipVideoView!!.layoutParams = layoutParams
        } else if (resources!!.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            remoteRenderersLayout!!.orientation = LinearLayout.VERTICAL
            layoutParams.height = FrameLayout.LayoutParams.WRAP_CONTENT
            layoutParams.width = resources!!.getDimension(R.dimen.large_preview_dimension)
                    .toInt()
            pipVideoView!!.layoutParams = layoutParams
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(peerConnectionEvent: PeerConnectionEvent) {
        if (peerConnectionEvent.peerConnectionEventType == PeerConnectionEvent.PeerConnectionEventType
                        .PEER_CLOSED
        ) {
            endPeerConnection(
                    peerConnectionEvent.sessionId,
                    peerConnectionEvent.videoStreamType == "screen"
            )
        } else if (peerConnectionEvent.peerConnectionEventType == PeerConnectionEvent
                        .PeerConnectionEventType.SENSOR_FAR || peerConnectionEvent.peerConnectionEventType == PeerConnectionEvent
                        .PeerConnectionEventType.SENSOR_NEAR
        ) {

            if (!isVoiceOnlyCall) {
                val enableVideo = peerConnectionEvent.peerConnectionEventType == PeerConnectionEvent
                        .PeerConnectionEventType.SENSOR_FAR && videoOn
                if (activity != null && EffortlessPermissions.hasPermissions(
                                activity,
                                *PERMISSIONS_CAMERA
                        ) &&
                        (currentCallStatus == CallStatus.CALLING || isConnectionEstablished) && videoOn
                        && enableVideo != localVideoTrack!!.enabled()
                ) {
                    toggleMedia(enableVideo, true, false)
                }
            }
        } else if (peerConnectionEvent.peerConnectionEventType == PeerConnectionEvent
                        .PeerConnectionEventType.NICK_CHANGE
        ) {
            gotNick(
                    peerConnectionEvent.sessionId, peerConnectionEvent.nick, true,
                    peerConnectionEvent.videoStreamType
            )
        } else if (peerConnectionEvent.peerConnectionEventType == PeerConnectionEvent
                        .PeerConnectionEventType.VIDEO_CHANGE && !isVoiceOnlyCall
        ) {
            gotAudioOrVideoChange(
                    true,
                    peerConnectionEvent.sessionId + "+" + peerConnectionEvent.videoStreamType,
                    peerConnectionEvent.changeValue!!
            )
        } else if (peerConnectionEvent.peerConnectionEventType == PeerConnectionEvent
                        .PeerConnectionEventType.AUDIO_CHANGE
        ) {
            gotAudioOrVideoChange(
                    false,
                    peerConnectionEvent.sessionId + "+" + peerConnectionEvent.videoStreamType,
                    peerConnectionEvent.changeValue!!
            )
        } else if (peerConnectionEvent.peerConnectionEventType == PeerConnectionEvent.PeerConnectionEventType.PUBLISHER_FAILED) {
            currentCallStatus = CallStatus.PUBLISHER_FAILED
            webSocketClient!!.clearResumeId()
            hangup(false)
        }
    }

    private fun startSendingNick() {
        val dataChannelMessage = DataChannelMessageNick()
        dataChannelMessage.type = "nickChanged"
        val nickChangedPayload = HashMap<String, String>()
        nickChangedPayload["userid"] = conversationUser!!.userId
        nickChangedPayload["name"] = conversationUser.displayName.toString()
        dataChannelMessage.payload = nickChangedPayload
        val magicPeerConnectionWrapper: MagicPeerConnectionWrapper
        for (i in magicPeerConnectionWrapperList.indices) {
            if (magicPeerConnectionWrapperList[i].isMCUPublisher) {
                magicPeerConnectionWrapper = magicPeerConnectionWrapperList[i]
                Observable
                        .interval(1, TimeUnit.SECONDS)
                        .repeatUntil { !isConnectionEstablished || isBeingDestroyed || isDestroyed }
                        .observeOn(Schedulers.io())
                        .subscribe(object : Observer<Long> {
                            override fun onSubscribe(d: Disposable) {

                            }

                            override fun onNext(aLong: Long) {
                                magicPeerConnectionWrapper.sendNickChannelData(dataChannelMessage)
                            }

                            override fun onError(e: Throwable) {

                            }

                            override fun onComplete() {

                            }
                        })
                break
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(mediaStreamEvent: MediaStreamEvent) {
        if (mediaStreamEvent.mediaStream != null) {
            setupVideoStreamForLayout(
                    mediaStreamEvent.mediaStream, mediaStreamEvent.session,
                    mediaStreamEvent.mediaStream.videoTracks != null && mediaStreamEvent.mediaStream.videoTracks.size > 0,
                    mediaStreamEvent.videoStreamType
            )
        } else {
            setupVideoStreamForLayout(
                    null, mediaStreamEvent.session, false,
                    mediaStreamEvent.videoStreamType
            )
        }
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    @Throws(IOException::class)
    fun onMessageEvent(sessionDescriptionSend: SessionDescriptionSendEvent) {
        val ncMessageWrapper = NCMessageWrapper()
        ncMessageWrapper.ev = "message"
        ncMessageWrapper.sessionId = callSession
        val ncSignalingMessage = NCSignalingMessage()
        ncSignalingMessage.to = sessionDescriptionSend.peerId
        ncSignalingMessage.roomType = sessionDescriptionSend.videoStreamType
        ncSignalingMessage.type = sessionDescriptionSend.type
        val ncMessagePayload = NCMessagePayload()
        ncMessagePayload.type = sessionDescriptionSend.type

        if ("candidate" != sessionDescriptionSend.type) {
            ncMessagePayload.sdp = sessionDescriptionSend.sessionDescription!!.description
            ncMessagePayload.nick = conversationUser!!.displayName
        } else {
            ncMessagePayload.iceCandidate = sessionDescriptionSend.ncIceCandidate
        }

        // Set all we need
        ncSignalingMessage.payload = ncMessagePayload
        ncMessageWrapper.signalingMessage = ncSignalingMessage

        if (!hasExternalSignalingServer) {
            val stringBuilder = StringBuilder()
            stringBuilder.append("{")
                    .append("\"fn\":\"")
                    .append(
                            StringEscapeUtils.escapeJson(
                                    LoganSquare.serialize(ncMessageWrapper.signalingMessage)
                            )
                    )
                    .append("\"")
                    .append(",")
                    .append("\"sessionId\":")
                    .append("\"")
                    .append(StringEscapeUtils.escapeJson(callSession))
                    .append("\"")
                    .append(",")
                    .append("\"ev\":\"message\"")
                    .append("}")

            val strings = ArrayList<String>()
            val stringToSend = stringBuilder.toString()
            strings.add(stringToSend)

            var urlToken: String? = null
            if (isMultiSession) {
                urlToken = roomToken
            }

            ncApi.sendSignalingMessages(
                            credentials, ApiUtils.getUrlForSignaling(baseUrl, urlToken),
                            strings.toString()
                    )
                    .retry(3)
                    .subscribeOn(Schedulers.io())
                    .subscribe(object : Observer<SignalingOverall> {
                        override fun onSubscribe(d: Disposable) {

                        }

                        override fun onNext(signalingOverall: SignalingOverall) {
                            if (signalingOverall.ocs.signalings != null) {
                                for (i in 0 until signalingOverall.ocs.signalings.size) {
                                    try {
                                        receivedSignalingMessage(signalingOverall.ocs.signalings[i])
                                    } catch (e: IOException) {
                                        e.printStackTrace()
                                    }

                                }
                            }
                        }

                        override fun onError(e: Throwable) {}

                        override fun onComplete() {

                        }
                    })
        } else {
            webSocketClient!!.sendCallMessage(ncMessageWrapper)
        }
    }

    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<String>,
            grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        EffortlessPermissions.onRequestPermissionsResult(
                requestCode, permissions, grantResults,
                this
        )
    }

    private fun setupAvatarForSession(session: String) {
        if (remoteRenderersLayout != null) {
            val relativeLayout = remoteRenderersLayout!!.findViewWithTag<RelativeLayout>("$session+video")
            if (relativeLayout != null) {
                val avatarImageView = relativeLayout.findViewById(R.id.avatarImageView) as ImageView

                val userId: String

                if (hasMCU) {
                    userId = webSocketClient!!.getUserIdForSession(session)
                } else {
                    userId = participantMap[session]!!.userId!!
                }

                if (!TextUtils.isEmpty(userId)) {

                    if (activity != null) {
                        avatarImageView.load(
                                ApiUtils.getUrlForAvatarWithName(
                                        baseUrl,
                                        userId, R.dimen.avatar_size_big
                                )
                        ) {
                            addHeader("Authorization", conversationUser!!.getCredentials())
                            transformations(CircleCropTransformation())
                        }
                    }
                }
            }
        }
    }

    private fun setupVideoStreamForLayout(
            mediaStream: MediaStream?,
            session: String,
            enable: Boolean,
            videoStreamType: String
    ) {
        var isInitialLayoutSetupForPeer = false
        if (remoteRenderersLayout!!.findViewWithTag<View>(session) == null) {
            setupNewPeerLayout(session, videoStreamType)
            isInitialLayoutSetupForPeer = true
        }

        val relativeLayout =
                remoteRenderersLayout!!.findViewWithTag<RelativeLayout>("$session+$videoStreamType")
        val surfaceViewRenderer = relativeLayout.findViewById<SurfaceViewRenderer>(R.id.surface_view)
        val imageView = relativeLayout.findViewById(R.id.avatarImageView) as ImageView

        if (!(mediaStream?.videoTracks == null || mediaStream.videoTracks.size <= 0 || !enable)
        ) {
            val videoTrack = mediaStream.videoTracks[0]

            videoTrack.addSink(surfaceViewRenderer)

            imageView.visibility = View.INVISIBLE
            surfaceViewRenderer.visibility = View.VISIBLE
        } else {
            imageView.visibility = View.VISIBLE
            surfaceViewRenderer.visibility = View.INVISIBLE

            if (isInitialLayoutSetupForPeer && isVoiceOnlyCall) {
                gotAudioOrVideoChange(true, session, false)
            }
        }

        callControls!!.z = 100.0f
    }

    private fun gotAudioOrVideoChange(
            video: Boolean,
            sessionId: String,
            change: Boolean
    ) {
        val relativeLayout = remoteRenderersLayout!!.findViewWithTag<RelativeLayout>(sessionId)
        if (relativeLayout != null) {
            val imageView: ImageView
            val avatarImageView = relativeLayout.findViewById(R.id.avatarImageView) as ImageView
            val surfaceViewRenderer = relativeLayout.findViewById<SurfaceViewRenderer>(R.id.surface_view)

            if (video) {
                imageView = relativeLayout.findViewById(R.id.remote_video_off)

                if (change) {
                    avatarImageView.visibility = View.INVISIBLE
                    surfaceViewRenderer.visibility = View.VISIBLE
                } else {
                    avatarImageView.visibility = View.VISIBLE
                    surfaceViewRenderer.visibility = View.INVISIBLE
                }
            } else {
                imageView = relativeLayout.findViewById(R.id.remote_audio_off)
            }

            if (change && imageView.visibility != View.INVISIBLE) {
                imageView.visibility = View.INVISIBLE
            } else if (!change && imageView.visibility != View.VISIBLE) {
                imageView.visibility = View.VISIBLE
            }
        }
    }

    private fun setupNewPeerLayout(
            session: String,
            type: String
    ) {
        if (remoteRenderersLayout!!.findViewWithTag<View>(
                        "$session+$type"
                ) == null && activity != null
        ) {
            activity!!.runOnUiThread {
                val relativeLayout = activity!!.layoutInflater.inflate(
                        R.layout.call_item, remoteRenderersLayout,
                        false
                ) as RelativeLayout
                relativeLayout.tag = "$session+$type"
                val surfaceViewRenderer = relativeLayout.findViewById<SurfaceViewRenderer>(
                        R.id
                                .surface_view
                )

                surfaceViewRenderer.setMirror(false)
                surfaceViewRenderer.init(rootEglBase!!.eglBaseContext, null)
                surfaceViewRenderer.setZOrderMediaOverlay(false)
                // disabled because it causes some devices to crash
                surfaceViewRenderer.setEnableHardwareScaler(false)
                surfaceViewRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                surfaceViewRenderer.setOnClickListener(videoOnClickListener)
                remoteRenderersLayout!!.addView(relativeLayout)
                if (hasExternalSignalingServer) {
                    gotNick(session, webSocketClient!!.getDisplayNameForSession(session), false, type)
                } else {
                    gotNick(
                            session,
                            getPeerConnectionWrapperForSessionIdAndType(session, type, false).nick!!, false,
                            type
                    )
                }

                if ("video" == type) {
                    setupAvatarForSession(session)
                }

                callControls!!.z = 100.0f
            }
        }
    }

    private fun gotNick(
            sessionOrUserId: String,
            nick: String,
            isFromAnEvent: Boolean,
            type: String
    ) {
        var sessionOrUserId = sessionOrUserId
        if (isFromAnEvent && hasExternalSignalingServer) {
            // get session based on userId
            sessionOrUserId = webSocketClient!!.getSessionForUserId(sessionOrUserId).toString()
        }

        sessionOrUserId += "+$type"

        if (relativeLayout != null) {
            val relativeLayout = remoteRenderersLayout!!.findViewWithTag<RelativeLayout>(sessionOrUserId)
            val textView = relativeLayout.findViewById<TextView>(R.id.peer_nick_text_view)
            if (textView.text != nick) {
                textView.text = nick
            }
        }
    }

    @OnClick(R.id.connectingRelativeLayoutView)
    fun onConnectingViewClick() {
        if (currentCallStatus == CallStatus.CALLING_TIMEOUT) {
            setCallState(CallStatus.RECONNECTING)
            hangupNetworkCalls(false)
        }
    }

    private fun setCallState(callState: CallStatus) {
        if (currentCallStatus == null || currentCallStatus != callState) {
            currentCallStatus = callState
            if (handler == null) {
                handler = Handler(Looper.getMainLooper())
            } else {
                handler!!.removeCallbacksAndMessages(null)
            }

            when (callState) {
                CallController.CallStatus.CALLING -> handler!!.post {
                    playCallingSound()
                    connectingTextView!!.setText(R.string.nc_connecting_call)
                    if (connectingView!!.visibility != View.VISIBLE) {
                        connectingView!!.visibility = View.VISIBLE
                    }

                    if (conversationView!!.visibility != View.INVISIBLE) {
                        conversationView!!.visibility = View.INVISIBLE
                    }

                    if (progressBar!!.visibility != View.VISIBLE) {
                        progressBar!!.visibility = View.VISIBLE
                    }

                    if (errorImageView!!.visibility != View.GONE) {
                        errorImageView!!.visibility = View.GONE
                    }
                }
                CallController.CallStatus.CALLING_TIMEOUT -> handler!!.post {
                    hangup(false)
                    connectingTextView!!.setText(R.string.nc_call_timeout)
                    if (connectingView!!.visibility != View.VISIBLE) {
                        connectingView!!.visibility = View.VISIBLE
                    }

                    if (progressBar!!.visibility != View.GONE) {
                        progressBar!!.visibility = View.GONE
                    }

                    if (conversationView!!.visibility != View.INVISIBLE) {
                        conversationView!!.visibility = View.INVISIBLE
                    }

                    errorImageView!!.setImageResource(R.drawable.ic_av_timer_timer_24dp)

                    if (errorImageView!!.visibility != View.VISIBLE) {
                        errorImageView!!.visibility = View.VISIBLE
                    }
                }
                CallController.CallStatus.RECONNECTING -> handler!!.post {
                    playCallingSound()
                    connectingTextView!!.setText(R.string.nc_call_reconnecting)
                    if (connectingView!!.visibility != View.VISIBLE) {
                        connectingView!!.visibility = View.VISIBLE
                    }
                    if (conversationView!!.visibility != View.INVISIBLE) {
                        conversationView!!.visibility = View.INVISIBLE
                    }
                    if (progressBar!!.visibility != View.VISIBLE) {
                        progressBar!!.visibility = View.VISIBLE
                    }

                    if (errorImageView!!.visibility != View.GONE) {
                        errorImageView!!.visibility = View.GONE
                    }
                }
                CallController.CallStatus.ESTABLISHED -> {
                    handler!!.postDelayed({ setCallState(CallStatus.CALLING_TIMEOUT) }, 45000)
                    handler!!.post {
                        if (connectingView != null) {
                            connectingTextView!!.setText(R.string.nc_calling)
                            if (connectingTextView!!.visibility != View.VISIBLE) {
                                connectingView!!.visibility = View.VISIBLE
                            }
                        }

                        if (progressBar != null) {
                            if (progressBar!!.visibility != View.VISIBLE) {
                                progressBar!!.visibility = View.VISIBLE
                            }
                        }

                        if (conversationView != null) {
                            if (conversationView!!.visibility != View.INVISIBLE) {
                                conversationView!!.visibility = View.INVISIBLE
                            }
                        }

                        if (errorImageView != null) {
                            if (errorImageView!!.visibility != View.GONE) {
                                errorImageView!!.visibility = View.GONE
                            }
                        }
                    }
                }
                CallController.CallStatus.IN_CONVERSATION -> handler!!.post {
                    stopCallingSound()
                    //startListening()

                    if (!isPTTActive) {
                        animateCallControls(false, 5000)
                    }

                    if (connectingView != null) {
                        if (connectingView!!.visibility != View.INVISIBLE) {
                            connectingView!!.visibility = View.INVISIBLE
                        }
                    }

                    if (progressBar != null) {
                        if (progressBar!!.visibility != View.GONE) {
                            progressBar!!.visibility = View.GONE
                        }
                    }

                    if (conversationView != null) {
                        if (conversationView!!.visibility != View.VISIBLE) {
                            conversationView!!.visibility = View.VISIBLE
                        }
                    }

                    if (errorImageView != null) {
                        if (errorImageView!!.visibility != View.GONE) {
                            errorImageView!!.visibility = View.GONE
                        }
                    }
                }
                CallController.CallStatus.OFFLINE -> handler!!.post {
                    stopCallingSound()

                    if (connectingTextView != null) {
                        connectingTextView!!.setText(R.string.nc_offline)

                        if (connectingView!!.visibility != View.VISIBLE) {
                            connectingView!!.visibility = View.VISIBLE
                        }
                    }

                    if (conversationView != null) {
                        if (conversationView!!.visibility != View.INVISIBLE) {
                            conversationView!!.visibility = View.INVISIBLE
                        }
                    }

                    if (progressBar != null) {
                        if (progressBar!!.visibility != View.GONE) {
                            progressBar!!.visibility = View.GONE
                        }
                    }

                    if (errorImageView != null) {
                        errorImageView!!.setImageResource(R.drawable.ic_signal_wifi_off_white_24dp)
                        if (errorImageView!!.visibility != View.VISIBLE) {
                            errorImageView!!.visibility = View.VISIBLE
                        }
                    }
                }
                CallController.CallStatus.LEAVING -> handler!!.post {
                    if (!isDestroyed && !isBeingDestroyed) {
                        stopCallingSound()
                        connectingTextView!!.setText(R.string.nc_leaving_call)
                        connectingView!!.visibility = View.VISIBLE
                        conversationView!!.visibility = View.INVISIBLE
                        progressBar!!.visibility = View.VISIBLE
                        errorImageView!!.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun playCallingSound() {
        stopCallingSound()
        val ringtoneUri = Uri.parse(
                "android.resource://"
                        + applicationContext!!.packageName
                        + "/raw/librem_by_feandesign_call"
        )
        if (activity != null) {
            mediaPlayer = MediaPlayer()
            try {
                mediaPlayer!!.setDataSource(context, ringtoneUri)
                mediaPlayer!!.isLooping = true
                val audioAttributes = AudioAttributes.Builder()
                        .setContentType(
                                AudioAttributes
                                        .CONTENT_TYPE_SONIFICATION
                        )
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .build()
                mediaPlayer!!.setAudioAttributes(audioAttributes)

                mediaPlayer!!.setOnPreparedListener { mp -> mediaPlayer!!.start() }

                mediaPlayer!!.prepareAsync()
            } catch (e: IOException) {
                Log.e(TAG, "Failed to play sound")
            }

        }
    }

    private fun stopCallingSound() {
        if (mediaPlayer != null) {
            if (mediaPlayer!!.isPlaying) {
                mediaPlayer!!.stop()
            }

            mediaPlayer!!.release()
            mediaPlayer = null
        }
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    fun onMessageEvent(networkEvent: NetworkEvent) {
        if (networkEvent.networkConnectionEvent == NetworkEvent.NetworkConnectionEvent.NETWORK_CONNECTED) {
            if (handler != null) {
                handler!!.removeCallbacksAndMessages(null)
            }

            /*if (!hasMCU) {
                      setCallState(CallStatus.RECONNECTING);
                      hangupNetworkCalls(false);
                  }*/

        } else if (networkEvent.networkConnectionEvent == NetworkEvent.NetworkConnectionEvent.NETWORK_DISCONNECTED) {
            if (handler != null) {
                handler!!.removeCallbacksAndMessages(null)
            }

            /* if (!hasMCU) {
                      setCallState(CallStatus.OFFLINE);
                      hangup(false);
                  }*/
        }
    }

    @Parcel
    enum class CallStatus {
        CALLING,
        CALLING_TIMEOUT,
        ESTABLISHED,
        IN_CONVERSATION,
        RECONNECTING,
        OFFLINE,
        LEAVING,
        PUBLISHER_FAILED
    }

    private inner class MicrophoneButtonTouchListener : View.OnTouchListener {

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(
                v: View,
                event: MotionEvent
        ): Boolean {
            v.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_UP && isPTTActive) {
                isPTTActive = false
                microphoneControlButton?.setImageResource(R.drawable.ic_mic_off_white_24px)
                pulseAnimation!!.stop()
                toggleMedia(false, false, false)
                animateCallControls(false, 5000)
            }
            return true
        }
    }

    private inner class VideoClickListener : View.OnClickListener {

        override fun onClick(v: View) {
            showCallControls()
        }
    }

    companion object {

        private val TAG = "CallController"

        private val PERMISSIONS_CALL =
                arrayOf(android.Manifest.permission.CAMERA, android.Manifest.permission.RECORD_AUDIO)

        private val PERMISSIONS_CAMERA = arrayOf(Manifest.permission.CAMERA)

        private val PERMISSIONS_MICROPHONE = arrayOf(Manifest.permission.RECORD_AUDIO)
    }
}
