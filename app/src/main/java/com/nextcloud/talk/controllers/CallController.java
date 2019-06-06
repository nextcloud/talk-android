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

package com.nextcloud.talk.controllers;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import autodagger.AutoInjector;
import butterknife.BindView;
import butterknife.OnClick;
import butterknife.OnLongClick;
import com.bluelinelabs.logansquare.LoganSquare;
import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.drawee.interfaces.DraweeController;
import com.facebook.drawee.view.SimpleDraweeView;
import com.nextcloud.talk.R;
import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.controllers.base.BaseController;
import com.nextcloud.talk.events.*;
import com.nextcloud.talk.models.ExternalSignalingServer;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.call.CallOverall;
import com.nextcloud.talk.models.json.capabilities.CapabilitiesOverall;
import com.nextcloud.talk.models.json.generic.GenericOverall;
import com.nextcloud.talk.models.json.participants.Participant;
import com.nextcloud.talk.models.json.participants.ParticipantsOverall;
import com.nextcloud.talk.models.json.rooms.Conversation;
import com.nextcloud.talk.models.json.rooms.RoomsOverall;
import com.nextcloud.talk.models.json.signaling.*;
import com.nextcloud.talk.models.json.signaling.settings.IceServer;
import com.nextcloud.talk.models.json.signaling.settings.SignalingSettingsOverall;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.DisplayUtils;
import com.nextcloud.talk.utils.NotificationUtils;
import com.nextcloud.talk.utils.animations.PulseAnimation;
import com.nextcloud.talk.utils.bundle.BundleKeys;
import com.nextcloud.talk.utils.database.user.UserUtils;
import com.nextcloud.talk.utils.power.PowerManagerUtils;
import com.nextcloud.talk.utils.preferences.AppPreferences;
import com.nextcloud.talk.utils.singletons.ApplicationWideCurrentRoomHolder;
import com.nextcloud.talk.utils.singletons.MerlinTheWizard;
import com.nextcloud.talk.webrtc.*;
import com.wooplr.spotlight.SpotlightView;
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import me.zhanghai.android.effortlesspermissions.AfterPermissionDenied;
import me.zhanghai.android.effortlesspermissions.EffortlessPermissions;
import me.zhanghai.android.effortlesspermissions.OpenAppDetailsDialogFragment;
import okhttp3.Cache;
import org.apache.commons.lang3.StringEscapeUtils;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.parceler.Parcel;
import org.webrtc.*;
import pub.devrel.easypermissions.AfterPermissionGranted;

import javax.inject.Inject;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

@AutoInjector(NextcloudTalkApplication.class)
public class CallController extends BaseController {

    private static final String TAG = "CallController";

    private static final String[] PERMISSIONS_CALL = {
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO,
    };

    private static final String[] PERMISSIONS_CAMERA = {
            Manifest.permission.CAMERA
    };

    private static final String[] PERMISSIONS_MICROPHONE = {
            Manifest.permission.RECORD_AUDIO
    };

    @BindView(R.id.callControlEnableSpeaker)
    SimpleDraweeView callControlEnableSpeaker;

    @BindView(R.id.pip_video_view)
    SurfaceViewRenderer pipVideoView;
    @BindView(R.id.relative_layout)
    RelativeLayout relativeLayout;
    @BindView(R.id.remote_renderers_layout)
    LinearLayout remoteRenderersLayout;

    @BindView(R.id.callControlsRelativeLayout)
    RelativeLayout callControls;
    @BindView(R.id.call_control_microphone)
    SimpleDraweeView microphoneControlButton;
    @BindView(R.id.call_control_camera)
    SimpleDraweeView cameraControlButton;
    @BindView(R.id.call_control_switch_camera)
    SimpleDraweeView cameraSwitchButton;
    @BindView(R.id.connectingTextView)
    TextView connectingTextView;

    @BindView(R.id.connectingRelativeLayoutView)
    RelativeLayout connectingView;

    @BindView(R.id.conversationRelativeLayoutView)
    RelativeLayout conversationView;

    @Inject
    NcApi ncApi;
    @Inject
    EventBus eventBus;
    @Inject
    UserUtils userUtils;
    @Inject
    AppPreferences appPreferences;
    @Inject
    Cache cache;

    private PeerConnectionFactory peerConnectionFactory;
    private MediaConstraints audioConstraints;
    private MediaConstraints videoConstraints;
    private MediaConstraints sdpConstraints;
    private MediaConstraints sdpConstraintsForMCU;
    private MagicAudioManager audioManager;
    private VideoSource videoSource;
    private VideoTrack localVideoTrack;
    private AudioSource audioSource;
    private AudioTrack localAudioTrack;
    private VideoCapturer videoCapturer;
    private EglBase rootEglBase;
    private Disposable signalingDisposable;
    private Disposable pingDisposable;
    private List<PeerConnection.IceServer> iceServers;
    private CameraEnumerator cameraEnumerator;
    private String roomToken;
    private UserEntity conversationUser;
    private String callSession;
    private MediaStream localMediaStream;
    private String credentials;
    private List<MagicPeerConnectionWrapper> magicPeerConnectionWrapperList = new ArrayList<>();
    private Map<String, Participant> participantMap = new HashMap<>();

    private boolean videoOn = false;
    private boolean audioOn = false;

    private boolean isMultiSession = false;
    private boolean needsPing = true;

    private boolean isVoiceOnlyCall;
    private Handler callControlHandler = new Handler();
    private Handler cameraSwitchHandler = new Handler();

    private boolean isPTTActive = false;
    private PulseAnimation pulseAnimation;
    private View.OnClickListener videoOnClickListener;

    private String baseUrl;
    private String roomId;

    private SpotlightView spotlightView;

    private ExternalSignalingServer externalSignalingServer;
    private MagicWebSocketInstance webSocketClient;
    private WebSocketConnectionHelper webSocketConnectionHelper;
    private boolean hasMCU;
    private boolean hasExternalSignalingServer;
    private String conversationPassword;

    private PowerManagerUtils powerManagerUtils;

    private Handler handler;

    private CallStatus currentCallStatus;

    @Parcel
    public enum CallStatus {
        CALLING, CALLING_TIMEOUT, ESTABLISHED, RECONNECTING, OFFLINE, LEAVING
    }

    public CallController(Bundle args) {
        super(args);
        NextcloudTalkApplication.getSharedApplication().getComponentApplication().inject(this);

        roomId = args.getString(BundleKeys.KEY_ROOM_ID, "");
        roomToken = args.getString(BundleKeys.KEY_ROOM_TOKEN, "");
        conversationUser = args.getParcelable(BundleKeys.KEY_USER_ENTITY);
        conversationPassword = args.getString(BundleKeys.KEY_CONVERSATION_PASSWORD, "");
        isVoiceOnlyCall = args.getBoolean(BundleKeys.KEY_CALL_VOICE_ONLY, false);

        credentials = ApiUtils.getCredentials(conversationUser.getUsername(), conversationUser.getToken());

        baseUrl = args.getString(BundleKeys.KEY_MODIFIED_BASE_URL, "");

        if (TextUtils.isEmpty(baseUrl)) {
            baseUrl = conversationUser.getBaseUrl();
        }

        powerManagerUtils = new PowerManagerUtils();
        currentCallStatus = CallStatus.CALLING;
    }

    @Override
    protected View inflateView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container) {
        return inflater.inflate(R.layout.controller_call, container, false);
    }

    private void createCameraEnumerator() {
        if (getActivity() != null) {
            boolean camera2EnumeratorIsSupported = false;
            try {
                camera2EnumeratorIsSupported = Camera2Enumerator.isSupported(getActivity());
            } catch (final Throwable throwable) {
                Log.w(TAG, "Camera2Enumator threw an error");
            }

            if (camera2EnumeratorIsSupported) {
                cameraEnumerator = new Camera2Enumerator(getActivity());
            } else {
                cameraEnumerator = new Camera1Enumerator(MagicWebRTCUtils.shouldEnableVideoHardwareAcceleration());
            }
        }
    }

    @Override
    protected void onViewBound(@NonNull View view) {
        super.onViewBound(view);

        microphoneControlButton.setOnTouchListener(new MicrophoneButtonTouchListener());
        videoOnClickListener = new VideoClickListener();

        pulseAnimation = PulseAnimation.create().with(microphoneControlButton)
                .setDuration(310)
                .setRepeatCount(PulseAnimation.INFINITE)
                .setRepeatMode(PulseAnimation.REVERSE);

        setPipVideoViewDimensions();

        try {
            cache.evictAll();
        } catch (IOException e) {
            Log.e(TAG, "Failed to evict cache");
        }

        callControls.setZ(100.0f);
        basicInitialization();
        initViews();

        initiateCall();
    }

    private void basicInitialization() {
        rootEglBase = EglBase.create();
        createCameraEnumerator();

        //Create a new PeerConnectionFactory instance.
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        peerConnectionFactory = PeerConnectionFactory.builder().createPeerConnectionFactory();

        peerConnectionFactory.setVideoHwAccelerationOptions(rootEglBase.getEglBaseContext(),
                rootEglBase.getEglBaseContext());

        //Create MediaConstraints - Will be useful for specifying video and audio constraints.
        audioConstraints = new MediaConstraints();
        videoConstraints = new MediaConstraints();

        localMediaStream = peerConnectionFactory.createLocalMediaStream("NCMS");

        // Create and audio manager that will take care of audio routing,
        // audio modes, audio device enumeration etc.
        audioManager = MagicAudioManager.create(getApplicationContext(), !isVoiceOnlyCall);
        // Store existing audio settings and change audio mode to
        // MODE_IN_COMMUNICATION for best possible VoIP performance.
        Log.d(TAG, "Starting the audio manager...");
        audioManager.start(this::onAudioManagerDevicesChanged);

        iceServers = new ArrayList<>();

        //create sdpConstraints
        sdpConstraints = new MediaConstraints();
        sdpConstraintsForMCU = new MediaConstraints();
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        String offerToReceiveVideoString = "true";

        if (isVoiceOnlyCall) {
            offerToReceiveVideoString = "false";
        }

        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", offerToReceiveVideoString));

        sdpConstraintsForMCU.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"));
        sdpConstraintsForMCU.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"));

        sdpConstraintsForMCU.optional.add(new MediaConstraints.KeyValuePair("internalSctpDataChannels", "true"));
        sdpConstraintsForMCU.optional.add(new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));

        sdpConstraints.optional.add(new MediaConstraints.KeyValuePair("internalSctpDataChannels", "true"));
        sdpConstraints.optional.add(new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));

        if (!isVoiceOnlyCall) {
            cameraInitialization();
        }

        microphoneInitialization();
    }

    private void handleFromNotification() {
        ncApi.getRooms(credentials, ApiUtils.getUrlForGetRooms(baseUrl))
                .retry(3)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<RoomsOverall>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onNext(RoomsOverall roomsOverall) {
                        for (Conversation conversation : roomsOverall.getOcs().getData()) {
                            if (roomId.equals(conversation.getRoomId())) {
                                roomToken = conversation.getToken();
                                break;
                            }
                        }

                        checkPermissions();
                    }

                    @Override
                    public void onError(Throwable e) {

                    }

                    @Override
                    public void onComplete() {

                    }
                });
    }

    private void initViews() {
        if (isVoiceOnlyCall) {
            callControlEnableSpeaker.setVisibility(View.VISIBLE);
            cameraSwitchButton.setVisibility(View.GONE);
            cameraControlButton.setVisibility(View.GONE);
            pipVideoView.setVisibility(View.GONE);
        } else {
            if (cameraEnumerator.getDeviceNames().length < 2) {
                cameraSwitchButton.setVisibility(View.GONE);
            }

            pipVideoView.init(rootEglBase.getEglBaseContext(), null);
            pipVideoView.setZOrderMediaOverlay(true);
            // disabled because it causes some devices to crash
            pipVideoView.setEnableHardwareScaler(false);
            pipVideoView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        }
    }


    private void checkPermissions() {
        if (isVoiceOnlyCall) {
            onMicrophoneClick();
        } else if (getActivity() != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(PERMISSIONS_CALL, 100);
            } else {
                onRequestPermissionsResult(100, PERMISSIONS_CALL, new int[]{1, 1});
            }
        }

    }

    @AfterPermissionGranted(100)
    private void onPermissionsGranted() {
        if (EffortlessPermissions.hasPermissions(getActivity(), PERMISSIONS_CALL)) {
            if (!videoOn && !isVoiceOnlyCall) {
                onCameraClick();
            }

            if (!audioOn) {
                onMicrophoneClick();
            }

            if (!isVoiceOnlyCall) {
                if (cameraEnumerator.getDeviceNames().length == 0) {
                    cameraControlButton.setVisibility(View.GONE);
                }

                if (cameraEnumerator.getDeviceNames().length > 1) {
                    cameraSwitchButton.setVisibility(View.VISIBLE);
                }
            }

            if (!currentCallStatus.equals(CallStatus.ESTABLISHED)) {
                fetchSignalingSettings();
            }
        } else if (getActivity() != null && EffortlessPermissions.somePermissionPermanentlyDenied(getActivity(),
                PERMISSIONS_CALL)) {
            checkIfSomeAreApproved();
        }

    }

    private void checkIfSomeAreApproved() {
        if (!isVoiceOnlyCall) {
            if (cameraEnumerator.getDeviceNames().length == 0) {
                cameraControlButton.setVisibility(View.GONE);
            }

            if (cameraEnumerator.getDeviceNames().length > 1) {
                cameraSwitchButton.setVisibility(View.VISIBLE);
            }

            if (getActivity() != null && EffortlessPermissions.hasPermissions(getActivity(), PERMISSIONS_CAMERA)) {
                if (!videoOn) {
                    onCameraClick();
                }
            } else {
                cameraControlButton.getHierarchy().setPlaceholderImage(R.drawable.ic_videocam_off_white_24px);
                cameraControlButton.setAlpha(0.7f);
                cameraSwitchButton.setVisibility(View.GONE);
            }
        }

        if (EffortlessPermissions.hasPermissions(getActivity(), PERMISSIONS_MICROPHONE)) {
            if (!audioOn) {
                onMicrophoneClick();
            }
        } else {
            microphoneControlButton.getHierarchy().setPlaceholderImage(R.drawable.ic_mic_off_white_24px);
        }

        if (!currentCallStatus.equals(CallStatus.ESTABLISHED)) {
            fetchSignalingSettings();
        }
    }

    @AfterPermissionDenied(100)
    private void onPermissionsDenied() {
        if (!isVoiceOnlyCall) {
            if (cameraEnumerator.getDeviceNames().length == 0) {
                cameraControlButton.setVisibility(View.GONE);
            } else if (cameraEnumerator.getDeviceNames().length == 1) {
                cameraSwitchButton.setVisibility(View.GONE);
            }
        }

        if (getActivity() != null && (EffortlessPermissions.hasPermissions(getActivity(), PERMISSIONS_CAMERA) ||
                EffortlessPermissions.hasPermissions(getActivity(), PERMISSIONS_MICROPHONE))) {
            checkIfSomeAreApproved();
        } else if (!currentCallStatus.equals(CallStatus.ESTABLISHED)) {
            fetchSignalingSettings();
        }
    }

    private void onAudioManagerDevicesChanged(
            final MagicAudioManager.AudioDevice device, final Set<MagicAudioManager.AudioDevice> availableDevices) {
        Log.d(TAG, "onAudioManagerDevicesChanged: " + availableDevices + ", "
                + "selected: " + device);

        final boolean shouldDisableProximityLock = (device.equals(MagicAudioManager.AudioDevice.WIRED_HEADSET)
                || device.equals(MagicAudioManager.AudioDevice.SPEAKER_PHONE)
                || device.equals(MagicAudioManager.AudioDevice.BLUETOOTH));

        if (shouldDisableProximityLock) {
            powerManagerUtils.updatePhoneState(PowerManagerUtils.PhoneState.WITHOUT_PROXIMITY_SENSOR_LOCK);
        } else {
            powerManagerUtils.updatePhoneState(PowerManagerUtils.PhoneState.WITH_PROXIMITY_SENSOR_LOCK);
        }
    }


    private void cameraInitialization() {
        videoCapturer = createCameraCapturer(cameraEnumerator);

        //Create a VideoSource instance
        if (videoCapturer != null) {
            videoSource = peerConnectionFactory.createVideoSource(videoCapturer);
            localVideoTrack = peerConnectionFactory.createVideoTrack("NCv0", videoSource);
            localMediaStream.addTrack(localVideoTrack);
            localVideoTrack.setEnabled(false);
            localVideoTrack.addSink(pipVideoView);
        }

    }

    private void microphoneInitialization() {
        //create an AudioSource instance
        audioSource = peerConnectionFactory.createAudioSource(audioConstraints);
        localAudioTrack = peerConnectionFactory.createAudioTrack("NCa0", audioSource);
        localAudioTrack.setEnabled(false);
        localMediaStream.addTrack(localAudioTrack);
    }

    private VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();

        // First, try to find front facing camera
        Logging.d(TAG, "Looking for front facing cameras.");
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating front facing camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);
                if (videoCapturer != null) {
                    pipVideoView.setMirror(true);
                    return videoCapturer;
                }
            }
        }


        // Front facing camera not found, try something else
        Logging.d(TAG, "Looking for other cameras.");
        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating other camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    pipVideoView.setMirror(false);
                    return videoCapturer;
                }
            }
        }

        return null;
    }

    @OnLongClick(R.id.call_control_microphone)
    boolean onMicrophoneLongClick() {
        if (!audioOn) {
            callControlHandler.removeCallbacksAndMessages(null);
            cameraSwitchHandler.removeCallbacksAndMessages(null);
            isPTTActive = true;
            callControls.setVisibility(View.VISIBLE);
            if (!isVoiceOnlyCall) {
                cameraSwitchButton.setVisibility(View.VISIBLE);
            }
        }

        onMicrophoneClick();
        return true;
    }

    @OnClick(R.id.callControlEnableSpeaker)
    public void onEnableSpeakerphoneClick() {
        if (audioManager != null) {
            audioManager.toggleUseSpeakerphone();
            if (audioManager.isSpeakerphoneAutoOn()) {
                callControlEnableSpeaker.getHierarchy().setPlaceholderImage(R.drawable.ic_volume_up_white_24dp);
            } else {
                callControlEnableSpeaker.getHierarchy().setPlaceholderImage(R.drawable.ic_volume_mute_white_24dp);
            }
        }
    }

    @OnClick(R.id.call_control_microphone)
    public void onMicrophoneClick() {
        if (getActivity() != null && EffortlessPermissions.hasPermissions(getActivity(), PERMISSIONS_MICROPHONE)) {

            if (getActivity() != null && !appPreferences.getPushToTalkIntroShown()) {
                spotlightView = new SpotlightView.Builder(getActivity())
                        .introAnimationDuration(300)
                        .enableRevealAnimation(true)
                        .performClick(false)
                        .fadeinTextDuration(400)
                        .headingTvColor(getResources().getColor(R.color.colorPrimary))
                        .headingTvSize(20)
                        .headingTvText(getResources().getString(R.string.nc_push_to_talk))
                        .subHeadingTvColor(getResources().getColor(R.color.white))
                        .subHeadingTvSize(16)
                        .subHeadingTvText(getResources().getString(R.string.nc_push_to_talk_desc))
                        .maskColor(Color.parseColor("#dc000000"))
                        .target(microphoneControlButton)
                        .lineAnimDuration(400)
                        .lineAndArcColor(getResources().getColor(R.color.colorPrimary))
                        .enableDismissAfterShown(true)
                        .dismissOnBackPress(true)
                        .usageId("pushToTalk")
                        .show();

                appPreferences.setPushToTalkIntroShown(true);
            }

            if (!isPTTActive) {
                audioOn = !audioOn;

                if (audioOn) {
                    microphoneControlButton.getHierarchy().setPlaceholderImage(R.drawable.ic_mic_white_24px);
                } else {
                    microphoneControlButton.getHierarchy().setPlaceholderImage(R.drawable.ic_mic_off_white_24px);
                }

                toggleMedia(audioOn, false);
            } else {
                microphoneControlButton.getHierarchy().setPlaceholderImage(R.drawable.ic_mic_white_24px);
                pulseAnimation.start();
                toggleMedia(true, false);
            }

            if (isVoiceOnlyCall && !currentCallStatus.equals(CallStatus.ESTABLISHED)) {
                fetchSignalingSettings();
            }

        } else if (getActivity() != null && EffortlessPermissions.somePermissionPermanentlyDenied(getActivity(),
                PERMISSIONS_MICROPHONE)) {
            // Microphone permission is permanently denied so we cannot request it normally.

            OpenAppDetailsDialogFragment.show(
                    R.string.nc_microphone_permission_permanently_denied,
                    R.string.nc_permissions_settings, (AppCompatActivity) getActivity());
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(PERMISSIONS_MICROPHONE, 100);
            } else {
                onRequestPermissionsResult(100, PERMISSIONS_MICROPHONE, new int[]{1});
            }
        }
    }

    @OnClick(R.id.callControlHangupView)
    void onHangupClick() {
        currentCallStatus = CallStatus.LEAVING;
        hangup(true);
    }

    @OnClick(R.id.call_control_camera)
    public void onCameraClick() {
        if (getActivity() != null && EffortlessPermissions.hasPermissions(getActivity(), PERMISSIONS_CAMERA)) {
            videoOn = !videoOn;

            if (videoOn) {
                cameraControlButton.getHierarchy().setPlaceholderImage(R.drawable.ic_videocam_white_24px);
                if (cameraEnumerator.getDeviceNames().length > 1) {
                    cameraSwitchButton.setVisibility(View.VISIBLE);
                }
            } else {
                cameraControlButton.getHierarchy().setPlaceholderImage(R.drawable.ic_videocam_off_white_24px);
                cameraSwitchButton.setVisibility(View.GONE);
            }

            toggleMedia(videoOn, true);
        } else if (getActivity() != null && EffortlessPermissions.somePermissionPermanentlyDenied(getActivity(),
                PERMISSIONS_CAMERA)) {
            // Camera permission is permanently denied so we cannot request it normally.
            OpenAppDetailsDialogFragment.show(
                    R.string.nc_camera_permission_permanently_denied,
                    R.string.nc_permissions_settings, (AppCompatActivity) getActivity());
        } else {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(PERMISSIONS_CAMERA, 100);
            } else {
                onRequestPermissionsResult(100, PERMISSIONS_CAMERA, new int[]{1});
            }
        }

    }

    @OnClick({R.id.call_control_switch_camera, R.id.pip_video_view})
    public void switchCamera() {
        CameraVideoCapturer cameraVideoCapturer = (CameraVideoCapturer) videoCapturer;
        if (cameraVideoCapturer != null) {
            cameraVideoCapturer.switchCamera(new CameraVideoCapturer.CameraSwitchHandler() {
                @Override
                public void onCameraSwitchDone(boolean currentCameraIsFront) {
                    pipVideoView.setMirror(currentCameraIsFront);
                }

                @Override
                public void onCameraSwitchError(String s) {

                }
            });
        }
    }

    private void toggleMedia(boolean enable, boolean video) {
        String message;
        if (video) {
            message = "videoOff";
            if (enable) {
                cameraControlButton.setAlpha(1.0f);
                message = "videoOn";
                startVideoCapture();
            } else {
                cameraControlButton.setAlpha(0.7f);
                if (videoCapturer != null) {
                    try {
                        videoCapturer.stopCapture();
                    } catch (InterruptedException e) {
                        Log.d(TAG, "Failed to stop capturing video while sensor is near the ear");
                    }
                }
            }

            if (localMediaStream != null && localMediaStream.videoTracks.size() > 0) {
                localMediaStream.videoTracks.get(0).setEnabled(enable);
            }
            if (enable) {
                pipVideoView.setVisibility(View.VISIBLE);
            } else {
                pipVideoView.setVisibility(View.INVISIBLE);
            }
        } else {
            message = "audioOff";
            if (enable) {
                message = "audioOn";
                microphoneControlButton.setAlpha(1.0f);
            } else {
                microphoneControlButton.setAlpha(0.7f);
            }

            if (localMediaStream != null && localMediaStream.audioTracks.size() > 0) {
                localMediaStream.audioTracks.get(0).setEnabled(enable);
            }
        }

        if (currentCallStatus.equals(CallStatus.ESTABLISHED)) {
            if (!hasMCU) {
                for (int i = 0; i < magicPeerConnectionWrapperList.size(); i++) {
                    magicPeerConnectionWrapperList.get(i).sendChannelData(new DataChannelMessage(message));
                }
            } else {
                for (int i = 0; i < magicPeerConnectionWrapperList.size(); i++) {
                    if (magicPeerConnectionWrapperList.get(i).getSessionId().equals(webSocketClient.getSessionId())) {
                        magicPeerConnectionWrapperList.get(i).sendChannelData(new DataChannelMessage(message));
                        break;

                    }
                }
            }
        }
    }


    private void animateCallControls(boolean show, long startDelay) {
        if (isVoiceOnlyCall) {
            if (spotlightView != null && spotlightView.getVisibility() != View.GONE) {
                spotlightView.setVisibility(View.GONE);
            }
        } else if (!isPTTActive) {
            float alpha;
            long duration;

            if (show) {
                callControlHandler.removeCallbacksAndMessages(null);
                cameraSwitchHandler.removeCallbacksAndMessages(null);
                alpha = 1.0f;
                duration = 1000;
                if (callControls.getVisibility() != View.VISIBLE) {
                    callControls.setAlpha(0.0f);
                    callControls.setVisibility(View.VISIBLE);

                    cameraSwitchButton.setAlpha(0.0f);
                    cameraSwitchButton.setVisibility(View.VISIBLE);
                } else {
                    callControlHandler.postDelayed(() -> animateCallControls(false, 0), 5000);
                    return;
                }
            } else {
                alpha = 0.0f;
                duration = 1000;
            }

            if (callControls != null) {
                callControls.setEnabled(false);
                callControls.animate()
                        .translationY(0)
                        .alpha(alpha)
                        .setDuration(duration)
                        .setStartDelay(startDelay)
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                super.onAnimationEnd(animation);
                                if (callControls != null) {
                                    if (!show) {
                                        callControls.setVisibility(View.GONE);
                                        if (spotlightView != null && spotlightView.getVisibility() != View.GONE) {
                                            spotlightView.setVisibility(View.GONE);
                                        }
                                    } else {
                                        callControlHandler.postDelayed(new Runnable() {
                                            @Override
                                            public void run() {
                                                if (!isPTTActive) {
                                                    animateCallControls(false, 0);
                                                }
                                            }
                                        }, 7500);
                                    }

                                    callControls.setEnabled(true);
                                }
                            }
                        });
            }

            if (cameraSwitchButton != null) {
                cameraSwitchButton.setEnabled(false);
                cameraSwitchButton.animate()
                        .translationY(0)
                        .alpha(alpha)
                        .setDuration(duration)
                        .setStartDelay(startDelay)
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                super.onAnimationEnd(animation);
                                if (cameraSwitchButton != null) {
                                    if (!show) {
                                        cameraSwitchButton.setVisibility(View.GONE);
                                    }

                                    cameraSwitchButton.setEnabled(true);
                                }
                            }
                        });
            }

        }
    }

    @Override
    public void onDestroy() {
        onHangupClick();
        super.onDestroy();
    }

    private void fetchSignalingSettings() {
        ncApi.getSignalingSettings(credentials, ApiUtils.getUrlForSignalingSettings(baseUrl))
                .subscribeOn(Schedulers.io())
                .retry(3)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<SignalingSettingsOverall>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onNext(SignalingSettingsOverall signalingSettingsOverall) {
                        IceServer iceServer;
                        if (signalingSettingsOverall != null && signalingSettingsOverall.getOcs() != null &&
                                signalingSettingsOverall.getOcs().getSettings() != null) {

                            externalSignalingServer = new ExternalSignalingServer();

                            if (!TextUtils.isEmpty(signalingSettingsOverall.getOcs().getSettings().getExternalSignalingServer()) &&
                                    !TextUtils.isEmpty(signalingSettingsOverall.getOcs().getSettings().getExternalSignalingTicket())) {
                                externalSignalingServer = new ExternalSignalingServer();
                                externalSignalingServer.setExternalSignalingServer(signalingSettingsOverall.getOcs().getSettings().getExternalSignalingServer());
                                externalSignalingServer.setExternalSignalingTicket(signalingSettingsOverall.getOcs().getSettings().getExternalSignalingTicket());
                                hasExternalSignalingServer = true;
                            } else {
                                hasExternalSignalingServer = false;
                            }

                            if (!conversationUser.getUserId().equals("?")) {
                                try {
                                    userUtils.createOrUpdateUser(null, null, null, null, null, null, null,
                                            conversationUser.getId(), null, null, LoganSquare.serialize(externalSignalingServer))
                                            .subscribeOn(Schedulers.io())
                                            .subscribe();
                                } catch (IOException exception) {
                                    Log.e(TAG, "Failed to serialize external signaling server");
                                }
                            }

                            if (signalingSettingsOverall.getOcs().getSettings().getStunServers() != null) {
                                for (int i = 0; i < signalingSettingsOverall.getOcs().getSettings().getStunServers().size();
                                     i++) {
                                    iceServer = signalingSettingsOverall.getOcs().getSettings().getStunServers().get(i);
                                    if (TextUtils.isEmpty(iceServer.getUsername()) || TextUtils.isEmpty(iceServer
                                            .getCredential())) {
                                        iceServers.add(new PeerConnection.IceServer(iceServer.getUrl()));
                                    } else {
                                        iceServers.add(new PeerConnection.IceServer(iceServer.getUrl(),
                                                iceServer.getUsername(), iceServer.getCredential()));
                                    }
                                }
                            }

                            if (signalingSettingsOverall.getOcs().getSettings().getTurnServers() != null) {
                                for (int i = 0; i < signalingSettingsOverall.getOcs().getSettings().getTurnServers().size();
                                     i++) {
                                    iceServer = signalingSettingsOverall.getOcs().getSettings().getTurnServers().get(i);
                                    for (int j = 0; j < iceServer.getUrls().size(); j++) {
                                        if (TextUtils.isEmpty(iceServer.getUsername()) || TextUtils.isEmpty(iceServer
                                                .getCredential())) {
                                            iceServers.add(new PeerConnection.IceServer(iceServer.getUrls().get(j)));
                                        } else {
                                            iceServers.add(new PeerConnection.IceServer(iceServer.getUrls().get(j),
                                                    iceServer.getUsername(), iceServer.getCredential()));
                                        }
                                    }
                                }
                            }
                        }

                        checkCapabilities();
                    }

                    @Override
                    public void onError(Throwable e) {
                    }

                    @Override
                    public void onComplete() {

                    }
                });
    }

    private void checkCapabilities() {
        ncApi.getCapabilities(credentials, ApiUtils.getUrlForCapabilities(baseUrl))
                .retry(3)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<CapabilitiesOverall>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onNext(CapabilitiesOverall capabilitiesOverall) {
                        isMultiSession = capabilitiesOverall.getOcs().getData()
                                .getCapabilities() != null && capabilitiesOverall.getOcs().getData()
                                .getCapabilities().getSpreedCapability() != null &&
                                capabilitiesOverall.getOcs().getData()
                                        .getCapabilities().getSpreedCapability()
                                        .getFeatures() != null && capabilitiesOverall.getOcs().getData()
                                .getCapabilities().getSpreedCapability()
                                .getFeatures().contains("multi-room-users");

                        needsPing = !(capabilitiesOverall.getOcs().getData()
                                .getCapabilities() != null && capabilitiesOverall.getOcs().getData()
                                .getCapabilities().getSpreedCapability() != null &&
                                capabilitiesOverall.getOcs().getData()
                                        .getCapabilities().getSpreedCapability()
                                        .getFeatures() != null && capabilitiesOverall.getOcs().getData()
                                .getCapabilities().getSpreedCapability()
                                .getFeatures().contains("no-ping"));

                        if (!hasExternalSignalingServer) {
                            joinRoomAndCall();
                        } else {
                            setupAndInitiateWebSocketsConnection();
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                        isMultiSession = false;
                    }

                    @Override
                    public void onComplete() {

                    }
                });
    }

    private void joinRoomAndCall() {
        ncApi.joinRoom(credentials, ApiUtils.getUrlForSettingMyselfAsActiveParticipant(baseUrl,
                roomToken), conversationPassword)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .retry(3)
                .subscribe(new Observer<CallOverall>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onNext(CallOverall callOverall) {
                        callSession = callOverall.getOcs().getData().getSessionId();
                        ApplicationWideCurrentRoomHolder.getInstance().setSession(callSession);
                        ApplicationWideCurrentRoomHolder.getInstance().setCurrentRoomId(roomId);
                        ApplicationWideCurrentRoomHolder.getInstance().setCurrentRoomToken(roomToken);
                        ApplicationWideCurrentRoomHolder.getInstance().setUserInRoom(conversationUser);
                        callOrJoinRoomViaWebSocket();
                    }

                    @Override
                    public void onError(Throwable e) {

                    }

                    @Override
                    public void onComplete() {

                    }
                });
    }

    private void callOrJoinRoomViaWebSocket() {
        if (!hasExternalSignalingServer) {
            performCall();
        } else {
            webSocketClient.joinRoomWithRoomTokenAndSession(roomToken, callSession);
        }
    }

    private void performCall() {
        ncApi.joinCall(credentials,
                ApiUtils.getUrlForCall(baseUrl, roomToken))
                .subscribeOn(Schedulers.io())
                .retry(3)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<GenericOverall>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onNext(GenericOverall genericOverall) {
                        currentCallStatus = CallStatus.ESTABLISHED;

                        if (connectingView != null) {
                            connectingView.setVisibility(View.GONE);
                        }

                        if (conversationView != null) {
                            conversationView.setVisibility(View.VISIBLE);
                        }

                        if (!isPTTActive) {
                            animateCallControls(false, 5000);
                        }

                        ApplicationWideCurrentRoomHolder.getInstance().setInCall(true);

                        if (needsPing) {
                            ncApi.pingCall(credentials, ApiUtils.getUrlForCallPing(baseUrl, roomToken))
                                    .subscribeOn(Schedulers.io())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .repeatWhen(observable -> observable.delay(5000, TimeUnit.MILLISECONDS))
                                    .takeWhile(observable -> currentCallStatus.equals(CallStatus.ESTABLISHED))
                                    .retry(3, observable -> currentCallStatus.equals(CallStatus.ESTABLISHED))
                                    .subscribe(new Observer<GenericOverall>() {
                                        @Override
                                        public void onSubscribe(Disposable d) {
                                            pingDisposable = d;
                                        }

                                        @Override
                                        public void onNext(GenericOverall genericOverall) {

                                        }

                                        @Override
                                        public void onError(Throwable e) {
                                            dispose(pingDisposable);
                                        }

                                        @Override
                                        public void onComplete() {
                                            dispose(pingDisposable);
                                        }
                                    });
                        }

                        // Start pulling signaling messages
                        String urlToken = null;
                        if (isMultiSession) {
                            urlToken = roomToken;
                        }

                        if (!conversationUser.hasSpreedCapabilityWithName("no-ping") && !TextUtils.isEmpty(roomId)) {
                            NotificationUtils.cancelExistingNotifications(getApplicationContext(), conversationUser, roomId);
                        } else if (!TextUtils.isEmpty(roomToken)) {
                            NotificationUtils.cancelExistingNotifications(getApplicationContext(), conversationUser, roomToken);
                        }

                        if (!hasExternalSignalingServer) {
                            ncApi.pullSignalingMessages(credentials, ApiUtils.getUrlForSignaling(baseUrl, urlToken))
                                    .subscribeOn(Schedulers.io())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .repeatWhen(observable -> observable)
                                    .takeWhile(observable -> currentCallStatus.equals(CallStatus.ESTABLISHED))
                                    .retry(3, observable -> currentCallStatus.equals(CallStatus.ESTABLISHED))
                                    .subscribe(new Observer<SignalingOverall>() {
                                        @Override
                                        public void onSubscribe(Disposable d) {
                                            signalingDisposable = d;
                                        }

                                        @Override
                                        public void onNext(SignalingOverall signalingOverall) {
                                            if (signalingOverall.getOcs().getSignalings() != null) {
                                                for (int i = 0; i < signalingOverall.getOcs().getSignalings().size(); i++) {
                                                    try {
                                                        receivedSignalingMessage(signalingOverall.getOcs().getSignalings().get(i));
                                                    } catch (IOException e) {
                                                        Log.e(TAG, "Failed to process received signaling" +
                                                                " message");
                                                    }
                                                }
                                            }
                                        }

                                        @Override
                                        public void onError(Throwable e) {
                                            dispose(signalingDisposable);
                                        }

                                        @Override
                                        public void onComplete() {
                                            dispose(signalingDisposable);
                                        }
                                    });


                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                    }

                    @Override
                    public void onComplete() {

                    }
                });
    }

    private void setupAndInitiateWebSocketsConnection() {
        webSocketConnectionHelper = new WebSocketConnectionHelper();
        webSocketClient = webSocketConnectionHelper.getExternalSignalingInstanceForServer(
                externalSignalingServer.getExternalSignalingServer(),
                conversationUser, externalSignalingServer.getExternalSignalingTicket(),
                TextUtils.isEmpty(credentials));

        joinRoomAndCall();
    }

    private void initiateCall() {
        if (!TextUtils.isEmpty(roomToken)) {
            checkPermissions();
        } else {
            handleFromNotification();
        }
    }
    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onMessageEvent(WebSocketCommunicationEvent webSocketCommunicationEvent) {
        switch (webSocketCommunicationEvent.getType()) {
            case "hello":
                if (!currentCallStatus.equals(CallStatus.RECONNECTING)) {
                    if (!webSocketCommunicationEvent.getHashMap().containsKey("oldResumeId")) {
                        initiateCall();
                    } else {
                        // do nothing, let's just continue
                    }
                }
                break;
            case "roomJoined":
                if (hasExternalSignalingServer) {
                    startSendingNick();
                }

                if (webSocketCommunicationEvent.getHashMap().get("roomToken").equals(roomToken)) {
                    performCall();
                }
                break;
            case "participantsUpdate":
                if (webSocketCommunicationEvent.getHashMap().get("roomToken").equals(roomToken)) {
                    processUsersInRoom((List<HashMap<String, Object>>) webSocketClient.getJobWithId(Integer.valueOf(webSocketCommunicationEvent.getHashMap().get("jobId"))));
                }
                break;
            case "signalingMessage":
                processMessage((NCSignalingMessage) webSocketClient.getJobWithId(Integer.valueOf(webSocketCommunicationEvent.getHashMap().get("jobId"))));
                break;
            case "peerReadyForRequestingOffer":
                webSocketClient.requestOfferForSessionIdWithType(webSocketCommunicationEvent.getHashMap().get("sessionId"), "video");
                break;
        }
    }

    @OnClick({R.id.pip_video_view, R.id.remote_renderers_layout})
    public void showCallControls() {
        animateCallControls(true, 0);
    }

    private void dispose(@Nullable Disposable disposable) {
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        } else if (disposable == null) {

            if (pingDisposable != null && !pingDisposable.isDisposed()) {
                pingDisposable.dispose();
                pingDisposable = null;
            }

            if (signalingDisposable != null && !signalingDisposable.isDisposed()) {
                signalingDisposable.dispose();
                signalingDisposable = null;
            }
        }
    }

    private void receivedSignalingMessage(Signaling signaling) throws IOException {
        String messageType = signaling.getType();

        if (!currentCallStatus.equals(CallStatus.ESTABLISHED)) {
            return;
        }

        if ("usersInRoom".equals(messageType)) {
            processUsersInRoom((List<HashMap<String, Object>>) signaling.getMessageWrapper());
        } else if ("message".equals(messageType)) {
            NCSignalingMessage ncSignalingMessage = LoganSquare.parse(signaling.getMessageWrapper().toString(),
                    NCSignalingMessage.class);
            processMessage(ncSignalingMessage);
        } else {
            Log.d(TAG, "Something went very very wrong");
        }
    }

    private void processMessage(NCSignalingMessage ncSignalingMessage) {
        if (ncSignalingMessage.getRoomType().equals("video") || ncSignalingMessage.getRoomType().equals("screen")) {
            MagicPeerConnectionWrapper magicPeerConnectionWrapper =
                    getPeerConnectionWrapperForSessionIdAndType(ncSignalingMessage.getFrom(),
                            ncSignalingMessage.getRoomType(), false);

            String type = null;
            if (ncSignalingMessage.getPayload() != null && ncSignalingMessage.getPayload().getType() != null) {
                type = ncSignalingMessage.getPayload().getType();
            } else if (ncSignalingMessage.getType() != null) {
                type = ncSignalingMessage.getType();
            }

            if (type != null) {
                switch (type) {
                    case "unshareScreen":
                        endPeerConnection(ncSignalingMessage.getFrom(), true);
                        break;
                    case "offer":
                    case "answer":
                        magicPeerConnectionWrapper.setNick(ncSignalingMessage.getPayload().getNick());
                        SessionDescription sessionDescriptionWithPreferredCodec;

                        String sessionDescriptionStringWithPreferredCodec = MagicWebRTCUtils.preferCodec
                                (ncSignalingMessage.getPayload().getSdp(),
                                        "H264", false);

                        sessionDescriptionWithPreferredCodec = new SessionDescription(
                                SessionDescription.Type.fromCanonicalForm(type),
                                sessionDescriptionStringWithPreferredCodec);

                        if (magicPeerConnectionWrapper.getPeerConnection() != null) {
                            magicPeerConnectionWrapper.getPeerConnection().setRemoteDescription(magicPeerConnectionWrapper
                                    .getMagicSdpObserver(), sessionDescriptionWithPreferredCodec);
                        }
                        break;
                    case "candidate":
                        NCIceCandidate ncIceCandidate = ncSignalingMessage.getPayload().getIceCandidate();
                        IceCandidate iceCandidate = new IceCandidate(ncIceCandidate.getSdpMid(),
                                ncIceCandidate.getSdpMLineIndex(), ncIceCandidate.getCandidate());
                        magicPeerConnectionWrapper.addCandidate(iceCandidate);
                        break;
                    case "endOfCandidates":
                        magicPeerConnectionWrapper.drainIceCandidates();
                        break;
                    default:
                        break;
                }
            }
        } else {
            Log.d(TAG, "Something went very very wrong");
        }
    }

    private void hangup(boolean shutDownView) {
        dispose(null);
        if (shutDownView) {

            if (videoCapturer != null) {
                try {
                    videoCapturer.stopCapture();
                } catch (InterruptedException e) {
                    Log.e(TAG, "Failed to stop capturing while hanging up");
                }
                videoCapturer.dispose();
                videoCapturer = null;
            }

            if (pipVideoView != null) {
                pipVideoView.release();
            }

            if (audioSource != null) {
                audioSource.dispose();
                audioSource = null;
            }

            if (audioManager != null) {
                audioManager.stop();
                audioManager = null;
            }

            if (videoSource != null) {
                videoSource = null;
            }

            if (peerConnectionFactory != null) {
                peerConnectionFactory = null;
            }

            localMediaStream = null;
            localAudioTrack = null;
            localVideoTrack = null;


            if (TextUtils.isEmpty(credentials) && hasExternalSignalingServer) {
                WebSocketConnectionHelper.deleteExternalSignalingInstanceForUserEntity(-1);
            }
        }

        for (int i = 0; i < magicPeerConnectionWrapperList.size(); i++) {
            endPeerConnection(magicPeerConnectionWrapperList.get(i).getSessionId(), false);
        }

        hangupNetworkCalls(shutDownView);
    }

    private void hangupNetworkCalls(boolean shutDownView) {
        if (MerlinTheWizard.isConnectedToInternet()) {
            ncApi.leaveCall(credentials, ApiUtils.getUrlForCall(baseUrl, roomToken))
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Observer<GenericOverall>() {
                        @Override
                        public void onSubscribe(Disposable d) {

                        }

                        @Override
                        public void onNext(GenericOverall genericOverall) {
                            if (!TextUtils.isEmpty(credentials) && hasExternalSignalingServer) {
                                webSocketClient.joinRoomWithRoomTokenAndSession("", callSession);
                            }

                            if (isMultiSession) {
                                if (shutDownView && getActivity() != null) {
                                    getActivity().finish();
                                } else if (!shutDownView && currentCallStatus.equals(CallStatus.RECONNECTING)) {
                                    initiateCall();
                                }
                            } else {
                                leaveRoom(shutDownView);
                            }
                        }

                        @Override
                        public void onError(Throwable e) {

                        }

                        @Override
                        public void onComplete() {

                        }
                    });
        } else if (shutDownView && getActivity() != null) {
            getActivity().finish();
        }
    }

    private void leaveRoom(boolean shutDownView) {
        ncApi.leaveRoom(credentials, ApiUtils.getUrlForSettingMyselfAsActiveParticipant(baseUrl, roomToken))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<GenericOverall>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onNext(GenericOverall genericOverall) {
                        if (shutDownView && getActivity() != null) {
                            getActivity().finish();
                        }
                    }

                    @Override
                    public void onError(Throwable e) {

                    }

                    @Override
                    public void onComplete() {

                    }
                });
    }

    private void startVideoCapture() {
        if (videoCapturer != null) {
            videoCapturer.startCapture(1280, 720, 30);
        }
    }

    private void processUsersInRoom(List<HashMap<String, Object>> users) {
        List<String> newSessions = new ArrayList<>();
        Set<String> oldSesssions = new HashSet<>();

        for (HashMap<String, Object> participant : users) {
            if (!participant.get("sessionId").equals(callSession)) {
                Object inCallObject = participant.get("inCall");
                boolean isNewSession;
                if (inCallObject instanceof Boolean) {
                    isNewSession = (boolean) inCallObject;
                } else {
                    isNewSession = ((long) inCallObject) != 0;
                }

                if (isNewSession) {
                    newSessions.add(participant.get("sessionId").toString());
                } else {
                    oldSesssions.add(participant.get("sessionId").toString());
                }
            }
        }

        for (MagicPeerConnectionWrapper magicPeerConnectionWrapper : magicPeerConnectionWrapperList) {
            if (!magicPeerConnectionWrapper.isMCUPublisher()) {
                oldSesssions.add(magicPeerConnectionWrapper.getSessionId());
            }
        }

        // Calculate sessions that left the call
        oldSesssions.removeAll(newSessions);

        // Calculate sessions that join the call
        newSessions.removeAll(oldSesssions);

        if (!currentCallStatus.equals(CallStatus.ESTABLISHED)) {
            return;
        }

        if (newSessions.size() > 0 && !hasMCU) {
            getPeersForCall();
        }

        hasMCU = hasExternalSignalingServer && webSocketClient != null && webSocketClient.hasMCU();

        for (String sessionId : newSessions) {
            getPeerConnectionWrapperForSessionIdAndType(sessionId, "video", hasMCU && sessionId.equals(webSocketClient.getSessionId()));
        }

        for (String sessionId : oldSesssions) {
            endPeerConnection(sessionId, false);
        }
    }

    private void getPeersForCall() {
        ncApi.getPeersForCall(credentials, ApiUtils.getUrlForCall(baseUrl, roomToken))
                .subscribeOn(Schedulers.io())
                .subscribe(new Observer<ParticipantsOverall>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onNext(ParticipantsOverall participantsOverall) {
                        participantMap = new HashMap<>();
                        for (Participant participant : participantsOverall.getOcs().getData()) {
                            participantMap.put(participant.getSessionId(), participant);
                            if (getActivity() != null) {
                                getActivity().runOnUiThread(() -> setupAvatarForSession(participant.getSessionId()));
                            }
                        }
                    }

                    @Override
                    public void onError(Throwable e) {

                    }

                    @Override
                    public void onComplete() {

                    }
                });
    }

    private void deleteMagicPeerConnection(MagicPeerConnectionWrapper magicPeerConnectionWrapper) {
        magicPeerConnectionWrapper.removePeerConnection();
        magicPeerConnectionWrapperList.remove(magicPeerConnectionWrapper);
    }

    private MagicPeerConnectionWrapper getPeerConnectionWrapperForSessionId(String sessionId, String type) {
        for (int i = 0; i < magicPeerConnectionWrapperList.size(); i++) {
            if (magicPeerConnectionWrapperList.get(i).getSessionId().equals(sessionId) && magicPeerConnectionWrapperList.get(i).getVideoStreamType().equals(type)) {
                return magicPeerConnectionWrapperList.get(i);
            }
        }

        return null;
    }

    private MagicPeerConnectionWrapper getPeerConnectionWrapperForSessionIdAndType(String sessionId, String type, boolean publisher) {
        MagicPeerConnectionWrapper magicPeerConnectionWrapper;
        if ((magicPeerConnectionWrapper = getPeerConnectionWrapperForSessionId(sessionId, type)) != null) {
            return magicPeerConnectionWrapper;
        } else {
            if (hasMCU && publisher) {
                magicPeerConnectionWrapper = new MagicPeerConnectionWrapper(peerConnectionFactory,
                        iceServers, sdpConstraintsForMCU, sessionId, callSession, localMediaStream, true, true, type);
            } else if (hasMCU) {
                magicPeerConnectionWrapper = new MagicPeerConnectionWrapper(peerConnectionFactory,
                        iceServers, sdpConstraints, sessionId, callSession, null, false, true, type);
            } else {
                if (!"screen".equals(type)) {
                    magicPeerConnectionWrapper = new MagicPeerConnectionWrapper(peerConnectionFactory,
                            iceServers, sdpConstraints, sessionId, callSession, localMediaStream, false, false, type);
                } else {
                    magicPeerConnectionWrapper = new MagicPeerConnectionWrapper(peerConnectionFactory,
                            iceServers, sdpConstraints, sessionId, callSession, null, false, false, type);
                }
            }

            magicPeerConnectionWrapperList.add(magicPeerConnectionWrapper);
            return magicPeerConnectionWrapper;
        }
    }

    private List<MagicPeerConnectionWrapper> getPeerConnectionWrapperListForSessionId(String sessionId) {
        List<MagicPeerConnectionWrapper> internalList = new ArrayList<>();
        for (MagicPeerConnectionWrapper magicPeerConnectionWrapper : magicPeerConnectionWrapperList) {
            if (magicPeerConnectionWrapper.getSessionId().equals(sessionId)) {
                internalList.add(magicPeerConnectionWrapper);
            }
        }

        return internalList;
    }

    private void endPeerConnection(String sessionId, boolean justScreen) {
        List<MagicPeerConnectionWrapper> magicPeerConnectionWrappers;
        MagicPeerConnectionWrapper magicPeerConnectionWrapper;
        if (!(magicPeerConnectionWrappers = getPeerConnectionWrapperListForSessionId(sessionId)).isEmpty()
                && getActivity() != null) {
            for (int i = 0; i < magicPeerConnectionWrappers.size(); i++) {
                magicPeerConnectionWrapper = magicPeerConnectionWrappers.get(i);
                if (magicPeerConnectionWrapper.getSessionId().equals(sessionId)) {
                    if (magicPeerConnectionWrapper.getVideoStreamType().equals("screen") || !justScreen) {
                        MagicPeerConnectionWrapper finalMagicPeerConnectionWrapper = magicPeerConnectionWrapper;
                        getActivity().runOnUiThread(() -> removeMediaStream(sessionId + "+" +
                                finalMagicPeerConnectionWrapper.getVideoStreamType()));
                        deleteMagicPeerConnection(magicPeerConnectionWrapper);
                    }
                }
            }
        }
    }

    private void removeMediaStream(String sessionId) {
        if (remoteRenderersLayout != null && remoteRenderersLayout.getChildCount() > 0) {
            RelativeLayout relativeLayout = remoteRenderersLayout.findViewWithTag(sessionId);
            if (relativeLayout != null) {
                SurfaceViewRenderer surfaceViewRenderer = relativeLayout.findViewById(R.id.surface_view);
                surfaceViewRenderer.release();
                remoteRenderersLayout.removeView(relativeLayout);
                remoteRenderersLayout.invalidate();
            }
        }

        if (callControls != null) {
            callControls.setZ(100.0f);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(ConfigurationChangeEvent configurationChangeEvent) {
        powerManagerUtils.setOrientation(Objects.requireNonNull(getResources()).getConfiguration().orientation);


        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            remoteRenderersLayout.setOrientation(LinearLayout.HORIZONTAL);
        } else if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            remoteRenderersLayout.setOrientation(LinearLayout.VERTICAL);
        }

        setPipVideoViewDimensions();
    }

    private void setPipVideoViewDimensions() {
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) pipVideoView.getLayoutParams();

        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            remoteRenderersLayout.setOrientation(LinearLayout.HORIZONTAL);
            layoutParams.height = (int) getResources().getDimension(R.dimen.large_preview_dimension);
            layoutParams.width = FrameLayout.LayoutParams.WRAP_CONTENT;
            pipVideoView.setLayoutParams(layoutParams);
        } else if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            remoteRenderersLayout.setOrientation(LinearLayout.VERTICAL);
            layoutParams.height = FrameLayout.LayoutParams.WRAP_CONTENT;
            layoutParams.width = (int) getResources().getDimension(R.dimen.large_preview_dimension);
            pipVideoView.setLayoutParams(layoutParams);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(PeerConnectionEvent peerConnectionEvent) {
        if (peerConnectionEvent.getPeerConnectionEventType().equals(PeerConnectionEvent.PeerConnectionEventType
                .PEER_CLOSED)) {
            endPeerConnection(peerConnectionEvent.getSessionId(), peerConnectionEvent.getVideoStreamType().equals("screen"));
        } else if (peerConnectionEvent.getPeerConnectionEventType().equals(PeerConnectionEvent
                .PeerConnectionEventType.SENSOR_FAR) ||
                peerConnectionEvent.getPeerConnectionEventType().equals(PeerConnectionEvent
                        .PeerConnectionEventType.SENSOR_NEAR)) {

            if (!isVoiceOnlyCall) {
                boolean enableVideo = peerConnectionEvent.getPeerConnectionEventType().equals(PeerConnectionEvent
                        .PeerConnectionEventType.SENSOR_FAR) && videoOn;
                if (getActivity() != null && EffortlessPermissions.hasPermissions(getActivity(), PERMISSIONS_CAMERA) &&
                        (currentCallStatus.equals(CallStatus.CALLING) || currentCallStatus.equals(CallStatus.ESTABLISHED)) && videoOn
                        && enableVideo != localVideoTrack.enabled()) {
                    toggleMedia(enableVideo, true);
                }
            }
        } else if (peerConnectionEvent.getPeerConnectionEventType().equals(PeerConnectionEvent
                .PeerConnectionEventType.NICK_CHANGE)) {
            gotNick(peerConnectionEvent.getSessionId(), peerConnectionEvent.getNick(), true, peerConnectionEvent.getVideoStreamType());
        } else if (peerConnectionEvent.getPeerConnectionEventType().equals(PeerConnectionEvent
                .PeerConnectionEventType.VIDEO_CHANGE) && !isVoiceOnlyCall) {
            gotAudioOrVideoChange(true, peerConnectionEvent.getSessionId() + "+" + peerConnectionEvent.getVideoStreamType(),
                    peerConnectionEvent.getChangeValue());
        } else if (peerConnectionEvent.getPeerConnectionEventType().equals(PeerConnectionEvent
                .PeerConnectionEventType.AUDIO_CHANGE)) {
            gotAudioOrVideoChange(false, peerConnectionEvent.getSessionId() + "+" + peerConnectionEvent.getVideoStreamType(),
                    peerConnectionEvent.getChangeValue());
        }
    }

    private void startSendingNick() {
        DataChannelMessage dataChannelMessage = new DataChannelMessage();
        dataChannelMessage.setType("nickChanged");
        HashMap<String, String> nickChangedPayload = new HashMap<>();
        nickChangedPayload.put("userid", conversationUser.getUserId());
        nickChangedPayload.put("name", conversationUser.getDisplayName());
        dataChannelMessage.setPayload(nickChangedPayload);
        for (int i = 0; i < magicPeerConnectionWrapperList.size(); i++) {
            if (magicPeerConnectionWrapperList.get(i).isMCUPublisher()) {
                int finalI = i;
                Observable
                        .interval(1, TimeUnit.SECONDS)
                        .takeWhile(observer -> currentCallStatus.equals(CallStatus.ESTABLISHED))
                        .observeOn(Schedulers.io())
                        .doOnNext(n -> magicPeerConnectionWrapperList.get(finalI).sendChannelData(dataChannelMessage));
                break;
            }

        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(MediaStreamEvent mediaStreamEvent) {
        if (mediaStreamEvent.getMediaStream() != null) {
            setupVideoStreamForLayout(mediaStreamEvent.getMediaStream(), mediaStreamEvent.getSession(),
                    mediaStreamEvent.getMediaStream().videoTracks != null
                            && mediaStreamEvent.getMediaStream().videoTracks.size() > 0, mediaStreamEvent.getVideoStreamType());
        } else {
            setupVideoStreamForLayout(null, mediaStreamEvent.getSession(), false, mediaStreamEvent.getVideoStreamType());
        }
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onMessageEvent(SessionDescriptionSendEvent sessionDescriptionSend) throws IOException {
        NCMessageWrapper ncMessageWrapper = new NCMessageWrapper();
        ncMessageWrapper.setEv("message");
        ncMessageWrapper.setSessionId(callSession);
        NCSignalingMessage ncSignalingMessage = new NCSignalingMessage();
        ncSignalingMessage.setTo(sessionDescriptionSend.getPeerId());
        ncSignalingMessage.setRoomType(sessionDescriptionSend.getVideoStreamType());
        ncSignalingMessage.setType(sessionDescriptionSend.getType());
        NCMessagePayload ncMessagePayload = new NCMessagePayload();
        ncMessagePayload.setType(sessionDescriptionSend.getType());

        if (!"candidate".equals(sessionDescriptionSend.getType())) {
            ncMessagePayload.setSdp(sessionDescriptionSend.getSessionDescription().description);
            ncMessagePayload.setNick(conversationUser.getDisplayName());
        } else {
            ncMessagePayload.setIceCandidate(sessionDescriptionSend.getNcIceCandidate());
        }


        // Set all we need
        ncSignalingMessage.setPayload(ncMessagePayload);
        ncMessageWrapper.setSignalingMessage(ncSignalingMessage);


        if (!hasExternalSignalingServer) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("{")
                    .append("\"fn\":\"")
                    .append(StringEscapeUtils.escapeJson(LoganSquare.serialize(ncMessageWrapper.getSignalingMessage()))).append("\"")
                    .append(",")
                    .append("\"sessionId\":")
                    .append("\"").append(StringEscapeUtils.escapeJson(callSession)).append("\"")
                    .append(",")
                    .append("\"ev\":\"message\"")
                    .append("}");

            List<String> strings = new ArrayList<>();
            String stringToSend = stringBuilder.toString();
            strings.add(stringToSend);

            String urlToken = null;
            if (isMultiSession) {
                urlToken = roomToken;
            }

            ncApi.sendSignalingMessages(credentials, ApiUtils.getUrlForSignaling(baseUrl, urlToken),
                    strings.toString())
                    .retry(3)
                    .subscribeOn(Schedulers.io())
                    .subscribe(new Observer<SignalingOverall>() {
                        @Override
                        public void onSubscribe(Disposable d) {

                        }

                        @Override
                        public void onNext(SignalingOverall signalingOverall) {
                            if (signalingOverall.getOcs().getSignalings() != null) {
                                for (int i = 0; i < signalingOverall.getOcs().getSignalings().size(); i++) {
                                    try {
                                        receivedSignalingMessage(signalingOverall.getOcs().getSignalings().get(i));
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        }

                        @Override
                        public void onError(Throwable e) {
                        }

                        @Override
                        public void onComplete() {

                        }
                    });
        } else {
            webSocketClient.sendCallMessage(ncMessageWrapper);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        EffortlessPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults,
                this);
    }

    private void setupAvatarForSession(String session) {
        if (remoteRenderersLayout != null) {
            RelativeLayout relativeLayout = remoteRenderersLayout.findViewWithTag(session + "+video");
            if (relativeLayout != null) {
                SimpleDraweeView avatarImageView = relativeLayout.findViewById(R.id.avatarImageView);

                String userId;

                if (hasMCU) {
                    userId = webSocketClient.getUserIdForSession(session);
                } else {
                    userId = participantMap.get(session).getUserId();
                }

                if (!TextUtils.isEmpty(userId)) {

                    if (getActivity() != null) {
                        avatarImageView.setController(null);

                        DraweeController draweeController = Fresco.newDraweeControllerBuilder()
                                .setOldController(avatarImageView.getController())
                                .setImageRequest(DisplayUtils.getImageRequestForUrl(ApiUtils.getUrlForAvatarWithName(baseUrl,
                                        userId,
                                        R.dimen.avatar_size_big), null))
                                .build();
                        avatarImageView.setController(draweeController);
                    }
                }
            }
        }
    }

    private void setupVideoStreamForLayout(@Nullable MediaStream mediaStream, String session, boolean enable, String videoStreamType) {
        boolean isInitialLayoutSetupForPeer = false;
        if (remoteRenderersLayout.findViewWithTag(session) == null) {
            setupNewPeerLayout(session, videoStreamType);
            isInitialLayoutSetupForPeer = true;
        }

        RelativeLayout relativeLayout = remoteRenderersLayout.findViewWithTag(session + "+" + videoStreamType);
        SurfaceViewRenderer surfaceViewRenderer = relativeLayout.findViewById(R.id.surface_view);
        SimpleDraweeView imageView = relativeLayout.findViewById(R.id.avatarImageView);

        if (mediaStream != null && mediaStream.videoTracks != null && mediaStream.videoTracks.size() > 0 && enable) {
            VideoTrack videoTrack = mediaStream.videoTracks.get(0);

            videoTrack.addSink(surfaceViewRenderer);

            imageView.setVisibility(View.INVISIBLE);
            surfaceViewRenderer.setVisibility(View.VISIBLE);
        } else {
            imageView.setVisibility(View.VISIBLE);
            surfaceViewRenderer.setVisibility(View.INVISIBLE);

            if (isInitialLayoutSetupForPeer && isVoiceOnlyCall) {
                gotAudioOrVideoChange(true, session, false);
            }
        }

        callControls.setZ(100.0f);
    }

    private void gotAudioOrVideoChange(boolean video, String sessionId, boolean change) {
        RelativeLayout relativeLayout = remoteRenderersLayout.findViewWithTag(sessionId);
        if (relativeLayout != null) {
            ImageView imageView;
            SimpleDraweeView avatarImageView = relativeLayout.findViewById(R.id.avatarImageView);
            SurfaceViewRenderer surfaceViewRenderer = relativeLayout.findViewById(R.id.surface_view);

            if (video) {
                imageView = relativeLayout.findViewById(R.id.remote_video_off);

                if (change) {
                    avatarImageView.setVisibility(View.INVISIBLE);
                    surfaceViewRenderer.setVisibility(View.VISIBLE);
                } else {
                    avatarImageView.setVisibility(View.VISIBLE);
                    surfaceViewRenderer.setVisibility(View.INVISIBLE);
                }
            } else {
                imageView = relativeLayout.findViewById(R.id.remote_audio_off);
            }

            if (change && imageView.getVisibility() != View.INVISIBLE) {
                imageView.setVisibility(View.INVISIBLE);
            } else if (!change && imageView.getVisibility() != View.VISIBLE) {
                imageView.setVisibility(View.VISIBLE);
            }
        }
    }

    private void setupNewPeerLayout(String session, String type) {
        if (remoteRenderersLayout.findViewWithTag(session + "+" + type) == null && getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                RelativeLayout relativeLayout = (RelativeLayout)
                        getActivity().getLayoutInflater().inflate(R.layout.call_item, remoteRenderersLayout,
                                false);
                relativeLayout.setTag(session + "+" + type);
                SurfaceViewRenderer surfaceViewRenderer = relativeLayout.findViewById(R.id
                        .surface_view);

                surfaceViewRenderer.setMirror(false);
                surfaceViewRenderer.init(rootEglBase.getEglBaseContext(), null);
                surfaceViewRenderer.setZOrderMediaOverlay(false);
                // disabled because it causes some devices to crash
                surfaceViewRenderer.setEnableHardwareScaler(false);
                surfaceViewRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
                surfaceViewRenderer.setOnClickListener(videoOnClickListener);
                remoteRenderersLayout.addView(relativeLayout);
                if (hasExternalSignalingServer) {
                    gotNick(session, webSocketClient.getDisplayNameForSession(session), false, type);
                } else {
                    gotNick(session, getPeerConnectionWrapperForSessionIdAndType(session, type, false).getNick(), false, type);
                }

                if ("video".equals(type)) {
                    setupAvatarForSession(session);
                }

                callControls.setZ(100.0f);
            });
        }
    }

    private void gotNick(String sessionOrUserId, String nick, boolean isFromAnEvent, String type) {
        if (isFromAnEvent && hasExternalSignalingServer) {
            // get session based on userId
            sessionOrUserId = webSocketClient.getSessionForUserId(sessionOrUserId);
        }

        sessionOrUserId += "+" + type;

        if (relativeLayout != null) {
            RelativeLayout relativeLayout = remoteRenderersLayout.findViewWithTag(sessionOrUserId);
            TextView textView = relativeLayout.findViewById(R.id.peer_nick_text_view);
            if (!textView.getText().equals(nick)) {
                textView.setText(nick);
            }
        }
    }

    @Override
    protected void onAttach(@NonNull View view) {
        super.onAttach(view);
        eventBus.register(this);
    }

    @Override
    protected void onDetach(@NonNull View view) {
        super.onDetach(view);
        eventBus.unregister(this);
    }

    private class MicrophoneButtonTouchListener implements View.OnTouchListener {

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            v.onTouchEvent(event);
            if (event.getAction() == MotionEvent.ACTION_UP && isPTTActive) {
                isPTTActive = false;
                microphoneControlButton.getHierarchy().setPlaceholderImage(R.drawable.ic_mic_off_white_24px);
                pulseAnimation.stop();
                toggleMedia(false, false);
                animateCallControls(false, 5000);
            }
            return true;
        }
    }

    private class VideoClickListener implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            showCallControls();
        }
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onMessageEvent(NetworkEvent networkEvent) {
        if (networkEvent.getNetworkConnectionEvent().equals(NetworkEvent.NetworkConnectionEvent.NETWORK_CONNECTED)) {
            if (handler != null) {
                handler.removeCallbacksAndMessages(null);
            }

            currentCallStatus = CallStatus.RECONNECTING;
            hangupNetworkCalls(false);

        } else if (networkEvent.getNetworkConnectionEvent().equals(NetworkEvent.NetworkConnectionEvent.NETWORK_DISCONNECTED)) {
            if (handler != null) {
                handler.removeCallbacksAndMessages(null);
            }

            currentCallStatus = CallStatus.OFFLINE;
            hangup(false);
        }
    }
}
