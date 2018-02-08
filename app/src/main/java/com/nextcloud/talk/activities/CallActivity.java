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
 *
 * Inspired by:
 *  - Google samples
 *  - https://github.com/vivek1794/webrtc-android-codelab (MIT licence)
 */

package com.nextcloud.talk.activities;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.bluelinelabs.logansquare.LoganSquare;
import com.nextcloud.talk.R;
import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.events.MediaStreamEvent;
import com.nextcloud.talk.events.PeerConnectionEvent;
import com.nextcloud.talk.events.SessionDescriptionSendEvent;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.call.CallOverall;
import com.nextcloud.talk.models.json.generic.GenericOverall;
import com.nextcloud.talk.models.json.rooms.Room;
import com.nextcloud.talk.models.json.rooms.RoomsOverall;
import com.nextcloud.talk.models.json.signaling.DataChannelMessage;
import com.nextcloud.talk.models.json.signaling.NCIceCandidate;
import com.nextcloud.talk.models.json.signaling.NCMessagePayload;
import com.nextcloud.talk.models.json.signaling.NCMessageWrapper;
import com.nextcloud.talk.models.json.signaling.NCSignalingMessage;
import com.nextcloud.talk.models.json.signaling.Signaling;
import com.nextcloud.talk.models.json.signaling.SignalingOverall;
import com.nextcloud.talk.models.json.signaling.settings.IceServer;
import com.nextcloud.talk.models.json.signaling.settings.SignalingSettingsOverall;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.animations.PulseAnimation;
import com.nextcloud.talk.utils.bundle.BundleKeys;
import com.nextcloud.talk.utils.database.user.UserUtils;
import com.nextcloud.talk.webrtc.MagicAudioManager;
import com.nextcloud.talk.webrtc.MagicPeerConnectionWrapper;
import com.nextcloud.talk.webrtc.MagicWebRTCUtils;

import org.apache.commons.lang3.StringEscapeUtils;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.parceler.Parcels;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RendererCommon;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.io.IOException;
import java.net.CookieManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import autodagger.AutoInjector;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnLongClick;
import eu.davidea.flipview.FlipView;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import me.zhanghai.android.effortlesspermissions.AfterPermissionDenied;
import me.zhanghai.android.effortlesspermissions.EffortlessPermissions;
import me.zhanghai.android.effortlesspermissions.OpenAppDetailsDialogFragment;
import pub.devrel.easypermissions.AfterPermissionGranted;

@AutoInjector(NextcloudTalkApplication.class)
public class CallActivity extends AppCompatActivity {
    private static final String TAG = "CallActivity";
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

    @BindView(R.id.pip_video_view)
    SurfaceViewRenderer pipVideoView;
    @BindView(R.id.relative_layout)
    RelativeLayout relativeLayout;
    @BindView(R.id.remote_renderers_layout)
    LinearLayout remoteRenderersLayout;

    @BindView(R.id.call_controls)
    LinearLayout callControls;
    @BindView(R.id.call_control_microphone)
    FlipView microphoneControlButton;
    @BindView(R.id.call_control_camera)
    FlipView cameraControlButton;
    @BindView(R.id.call_control_switch_camera)
    FlipView cameraSwitchButton;

    @Inject
    NcApi ncApi;
    @Inject
    EventBus eventBus;
    @Inject
    UserUtils userUtils;
    @Inject
    CookieManager cookieManager;

    PeerConnectionFactory peerConnectionFactory;
    MediaConstraints audioConstraints;
    MediaConstraints videoConstraints;
    MediaConstraints sdpConstraints;
    MagicAudioManager audioManager;
    VideoSource videoSource;
    VideoTrack localVideoTrack;
    AudioSource audioSource;
    AudioTrack localAudioTrack;
    VideoCapturer videoCapturer;
    VideoRenderer localRenderer;
    EglBase rootEglBase;
    boolean leavingCall = false;
    boolean inCall = false;
    Disposable signalingDisposable;
    Disposable pingDisposable;
    List<PeerConnection.IceServer> iceServers;
    private CameraEnumerator cameraEnumerator;
    private String roomToken;
    private UserEntity userEntity;
    private String callSession;
    private MediaStream localMediaStream;
    private String credentials;
    private List<MagicPeerConnectionWrapper> magicPeerConnectionWrapperList = new ArrayList<>();

    private boolean videoOn = false;
    private boolean audioOn = false;

    private Handler handler = new Handler();

    private boolean isPTTActive = false;
    private PulseAnimation pulseAnimation;
    private View.OnClickListener videoOnClickListener;

    private String baseUrl;
    
    private static int getSystemUiVisibility() {
        int flags = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN;
        flags |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        return flags;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        NextcloudTalkApplication.getSharedApplication().getComponentApplication().inject(this);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(getSystemUiVisibility());

        setContentView(R.layout.activity_call);
        ButterKnife.bind(this);

        microphoneControlButton.setOnTouchListener(new microphoneButtonTouchListener());
        videoOnClickListener = new videoClickListener();

        pulseAnimation = PulseAnimation.create().with(microphoneControlButton.getFrontImageView())
                .setDuration(310)
                .setRepeatCount(PulseAnimation.INFINITE)
                .setRepeatMode(PulseAnimation.REVERSE);

        roomToken = getIntent().getExtras().getString(BundleKeys.KEY_ROOM_TOKEN, "");
        userEntity = Parcels.unwrap(getIntent().getExtras().getParcelable(BundleKeys.KEY_USER_ENTITY));
        callSession = getIntent().getExtras().getString(BundleKeys.KEY_CALL_SESSION, "0");
        credentials = ApiUtils.getCredentials(userEntity.getUsername(), userEntity.getToken());

        if (getIntent().getExtras().containsKey(BundleKeys.KEY_MODIFIED_BASE_URL)) {
            credentials = null;
            baseUrl = getIntent().getExtras().getString(BundleKeys.KEY_MODIFIED_BASE_URL);
        } else {
            baseUrl = userEntity.getBaseUrl();
        }

        callControls.setZ(100.0f);
        basicInitialization();

        if (!userEntity.getCurrent()) {
            userUtils.createOrUpdateUser(null,
                    null, null, null,
                    null, true, null, userEntity.getId())
                    .subscribe(new Observer<UserEntity>() {
                        @Override
                        public void onSubscribe(Disposable d) {

                        }

                        @Override
                        public void onNext(UserEntity userEntity) {
                            cookieManager.getCookieStore().removeAll();
                            userUtils.disableAllUsersWithoutId(userEntity.getId());
                            if (getIntent().getExtras().containsKey("fromNotification")) {
                                handleFromNotification();
                            } else {
                                initViews();
                                checkPermissions();
                            }
                        }

                        @Override
                        public void onError(Throwable e) {

                        }

                        @Override
                        public void onComplete() {

                        }
                    });

        } else if (getIntent().getExtras().containsKey("fromNotification")) {
            handleFromNotification();
        } else {
            initViews();
            checkPermissions();
        }
    }

    private void handleFromNotification() {
        ncApi.getRooms(credentials, ApiUtils.getUrlForGetRooms(baseUrl))
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<RoomsOverall>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onNext(RoomsOverall roomsOverall) {
                        for (Room room : roomsOverall.getOcs().getData()) {
                            if (roomToken.equals(room.getRoomId())) {
                                roomToken = room.getToken();
                                break;
                            }
                        }

                        initViews();
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

        if (inCall) {
            for (int i = 0; i < magicPeerConnectionWrapperList.size(); i++) {
                magicPeerConnectionWrapperList.get(i).sendChannelData(new DataChannelMessage(message));
            }
        }
    }

    @OnLongClick(R.id.call_control_microphone)
    public boolean onMicrophoneLongClick() {
        if (!audioOn) {
            handler.removeCallbacksAndMessages(null);
            isPTTActive = true;
            callControls.setVisibility(View.VISIBLE);
        }

        onMicrophoneClick();
        return true;
    }

    @OnClick(R.id.call_control_microphone)
    public void onMicrophoneClick() {
        if (EffortlessPermissions.hasPermissions(this, PERMISSIONS_MICROPHONE)) {
            if (!isPTTActive) {
                audioOn = !audioOn;

                if (audioOn) {
                    microphoneControlButton.getFrontImageView().setImageResource(R.drawable.ic_mic_white_24px);
                } else {
                    microphoneControlButton.getFrontImageView().setImageResource(R.drawable.ic_mic_off_white_24px);
                }

                toggleMedia(audioOn, false);
            } else {
                microphoneControlButton.getFrontImageView().setImageResource(R.drawable.ic_mic_white_24px);
                pulseAnimation.start();
                toggleMedia(true, false);
            }

        } else if (EffortlessPermissions.somePermissionPermanentlyDenied(this, PERMISSIONS_MICROPHONE)) {
            // Microphone permission is permanently denied so we cannot request it normally.
            OpenAppDetailsDialogFragment.show(
                    R.string.nc_microphone_permission_permanently_denied,
                    R.string.nc_permissions_settings, this);
        } else {
            EffortlessPermissions.requestPermissions(this, R.string.nc_permissions_audio,
                    100, PERMISSIONS_MICROPHONE);
        }
    }

    @OnClick(R.id.call_control_hangup)
    public void onHangupClick() {
        if (inCall) {
            hangup(false);
        } else {
            hangup(true);
        }
    }

    @OnClick(R.id.call_control_camera)
    public void onCameraClick() {
        if (EffortlessPermissions.hasPermissions(this, PERMISSIONS_CAMERA)) {
            videoOn = !videoOn;

            if (videoOn) {
                cameraControlButton.getFrontImageView().setImageResource(R.drawable.ic_videocam_white_24px);
                if (cameraEnumerator.getDeviceNames().length > 1) {
                    cameraSwitchButton.setVisibility(View.VISIBLE);
                }
            } else {
                cameraControlButton.getFrontImageView().setImageResource(R.drawable.ic_videocam_off_white_24px);
                cameraSwitchButton.setVisibility(View.GONE);
            }

            toggleMedia(videoOn, true);
        } else if (EffortlessPermissions.somePermissionPermanentlyDenied(this, PERMISSIONS_CAMERA)) {
            // Camera permission is permanently denied so we cannot request it normally.
            OpenAppDetailsDialogFragment.show(
                    R.string.nc_camera_permission_permanently_denied,
                    R.string.nc_permissions_settings, this);
        } else {
            EffortlessPermissions.requestPermissions(this, R.string.nc_permissions_video,
                    100, PERMISSIONS_CAMERA);
        }

    }


    @OnClick(R.id.call_control_switch_camera)
    public void switchCamera() {
        CameraVideoCapturer cameraVideoCapturer = (CameraVideoCapturer) videoCapturer;
        if (cameraVideoCapturer != null) {
            cameraVideoCapturer.switchCamera(null);
        }
    }

    private void createCameraEnumerator() {
        boolean camera2EnumeratorIsSupported = false;
        try {
            camera2EnumeratorIsSupported = Camera2Enumerator.isSupported(this);
        } catch (final Throwable throwable) {
            Log.w(TAG, "Camera2Enumator threw an error");
        }

        if (camera2EnumeratorIsSupported) {
            cameraEnumerator = new Camera2Enumerator(this);
        } else {
            cameraEnumerator = new Camera1Enumerator(MagicWebRTCUtils.shouldEnableVideoHardwareAcceleration());
        }
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
                    return videoCapturer;
                }
            }
        }

        return null;
    }

    public void initViews() {
        if (cameraEnumerator.getDeviceNames().length < 2) {
            cameraSwitchButton.setVisibility(View.GONE);
        }

        // setting this to true because it's not shown by default
        pipVideoView.setMirror(true);
        pipVideoView.init(rootEglBase.getEglBaseContext(), null);
        pipVideoView.setZOrderMediaOverlay(true);
        // disabled because it causes some devices to crash
        pipVideoView.setEnableHardwareScaler(false);
        pipVideoView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
    }

    private void checkPermissions() {
        EffortlessPermissions.requestPermissions(this, R.string.nc_permissions,
                100, PERMISSIONS_CALL);
    }

    @AfterPermissionGranted(100)
    private void onPermissionsGranted() {
        if (EffortlessPermissions.hasPermissions(this, PERMISSIONS_CALL)) {
            if (!videoOn) {
                onCameraClick();
            }

            if (!audioOn) {
                onMicrophoneClick();
            }

            if (cameraEnumerator.getDeviceNames().length == 0) {
                cameraControlButton.setVisibility(View.GONE);
            }

            if (cameraSwitchButton != null && cameraEnumerator.getDeviceNames().length > 1) {
                cameraSwitchButton.setVisibility(View.VISIBLE);
            }

            if (!inCall) {
                startCall();
            }
        } else if (EffortlessPermissions.somePermissionPermanentlyDenied(this,
                PERMISSIONS_CALL)) {
            checkIfSomeAreApproved();
        }
    }

    private void checkIfSomeAreApproved() {
        if (cameraEnumerator.getDeviceNames().length == 0) {
            cameraControlButton.setVisibility(View.GONE);
        }

        if (cameraSwitchButton != null && cameraEnumerator.getDeviceNames().length > 1) {
            cameraSwitchButton.setVisibility(View.VISIBLE);
        }

        if (EffortlessPermissions.hasPermissions(this, PERMISSIONS_CAMERA)) {
            if (!videoOn) {
                onCameraClick();
            }
        } else {
            cameraControlButton.getFrontImageView().setImageResource(R.drawable.ic_videocam_off_white_24px);
            if (cameraSwitchButton != null) {
                cameraSwitchButton.setVisibility(View.GONE);
            }
        }

        if (EffortlessPermissions.hasPermissions(this, PERMISSIONS_MICROPHONE)) {
            if (!audioOn) {
                onMicrophoneClick();
            }
        } else {
            microphoneControlButton.getFrontImageView().setImageResource(R.drawable.ic_mic_off_white_24px);
        }

        if (!inCall) {
            startCall();
        }
    }

    @AfterPermissionDenied(100)
    private void onPermissionsDenied() {
        if (cameraEnumerator.getDeviceNames().length == 0) {
            cameraControlButton.setVisibility(View.GONE);
        } else if (cameraEnumerator.getDeviceNames().length == 1) {
            cameraSwitchButton.setVisibility(View.GONE);
        }

        if (EffortlessPermissions.hasPermissions(this, PERMISSIONS_CAMERA) ||
                EffortlessPermissions.hasPermissions(this, PERMISSIONS_MICROPHONE)) {
            checkIfSomeAreApproved();
        }
    }

    private void basicInitialization() {
        rootEglBase = EglBase.create();
        createCameraEnumerator();

        //Create a new PeerConnectionFactory instance.
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        peerConnectionFactory = new PeerConnectionFactory(options);

        peerConnectionFactory.setVideoHwAccelerationOptions(rootEglBase.getEglBaseContext(),
                rootEglBase.getEglBaseContext());

        //Create MediaConstraints - Will be useful for specifying video and audio constraints.
        audioConstraints = new MediaConstraints();
        videoConstraints = new MediaConstraints();

        localMediaStream = peerConnectionFactory.createLocalMediaStream("NCMS");

        // Create and audio manager that will take care of audio routing,
        // audio modes, audio device enumeration etc.
        audioManager = MagicAudioManager.create(getApplicationContext());
        // Store existing audio settings and change audio mode to
        // MODE_IN_COMMUNICATION for best possible VoIP performance.
        Log.d(TAG, "Starting the audio manager...");
        audioManager.start(new MagicAudioManager.AudioManagerEvents() {
            @Override
            public void onAudioDeviceChanged(MagicAudioManager.AudioDevice selectedAudioDevice,
                                             Set<MagicAudioManager.AudioDevice> availableAudioDevices) {
                onAudioManagerDevicesChanged(selectedAudioDevice, availableAudioDevices);
            }
        });

        iceServers = new ArrayList<>();

        //create sdpConstraints
        sdpConstraints = new MediaConstraints();
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
        sdpConstraints.optional.add(new MediaConstraints.KeyValuePair("internalSctpDataChannels", "true"));
        sdpConstraints.optional.add(new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));

        cameraInitialization();
        microphoneInitialization();
    }

    private void cameraInitialization() {
        videoCapturer = createCameraCapturer(cameraEnumerator);

        //Create a VideoSource instance
        if (videoCapturer != null) {
            videoSource = peerConnectionFactory.createVideoSource(videoCapturer);
            localVideoTrack = peerConnectionFactory.createVideoTrack("NCv0", videoSource);
            localMediaStream.addTrack(localVideoTrack);
            localVideoTrack.setEnabled(false);

            //create a videoRenderer based on SurfaceViewRenderer instance
            localRenderer = new VideoRenderer(pipVideoView);
            // And finally, with our VideoRenderer ready, we
            // can add our renderer to the VideoTrack.
            localVideoTrack.addRenderer(localRenderer);
        }

    }

    private void microphoneInitialization() {
        //create an AudioSource instance
        audioSource = peerConnectionFactory.createAudioSource(audioConstraints);
        localAudioTrack = peerConnectionFactory.createAudioTrack("NCa0", audioSource);
        localAudioTrack.setEnabled(false);
        localMediaStream.addTrack(localAudioTrack);
    }

    private void startCall() {
        if (!isPTTActive) {
            animateCallControls(false, 7500);
        }
        startPullingSignalingMessages();
    }

    @OnClick({R.id.pip_video_view, R.id.remote_renderers_layout})
    public void showCallControls() {
        animateCallControls(true, 0);
    }

    public void startPullingSignalingMessages() {
        leavingCall = false;

        ncApi.getSignalingSettings(credentials, ApiUtils.getUrlForSignalingSettings(baseUrl))
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<SignalingSettingsOverall>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onNext(SignalingSettingsOverall signalingSettingsOverall) {
                        IceServer iceServer;
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

                        joinRoomAndCall();
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

    private void joinRoomAndCall() {
        if (callSession.equals("0")) {
            ncApi.joinRoom(credentials, ApiUtils.getUrlForRoomParticipants(baseUrl, roomToken), null)
                    .subscribeOn(Schedulers.newThread())
                    .observeOn(AndroidSchedulers.mainThread())
                    .retry(3)
                    .subscribe(new Observer<CallOverall>() {
                        @Override
                        public void onSubscribe(Disposable d) {

                        }

                        @Override
                        public void onNext(CallOverall callOverall) {
                            performCall(callOverall.getOcs().getData().getSessionId());
                        }

                        @Override
                        public void onError(Throwable e) {

                        }

                        @Override
                        public void onComplete() {

                        }
                    });
        } else {
            performCall(null);
        }
    }

    private void performCall(@Nullable String callSessionId) {
        ncApi.joinCall(credentials,
                ApiUtils.getUrlForCall(baseUrl, roomToken))
                .subscribeOn(Schedulers.newThread())
                .retry(3)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<GenericOverall>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onNext(GenericOverall genericOverall) {
                        inCall = true;
                        if (callSessionId != null) {
                            callSession = callSessionId;
                        }

                        // start pinging the call
                        ncApi.pingCall(credentials, ApiUtils.getUrlForCallPing(baseUrl, roomToken))
                                .subscribeOn(Schedulers.newThread())
                                .observeOn(AndroidSchedulers.mainThread())
                                .repeatWhen(observable -> observable.delay(5000, TimeUnit.MILLISECONDS))
                                .takeWhile(observable -> inCall)
                                .retry(3, observable -> inCall)
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

                        // Start pulling signaling messages
                        ncApi.pullSignalingMessages(credentials, ApiUtils.getUrlForSignaling(baseUrl))
                                .subscribeOn(Schedulers.newThread())
                                .observeOn(AndroidSchedulers.mainThread())
                                .repeatWhen(observable -> observable)
                                .takeWhile(observable -> inCall)
                                .retry(3, observable -> inCall)
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

                    @Override
                    public void onError(Throwable e) {
                    }

                    @Override
                    public void onComplete() {

                    }
                });
    }

    private void receivedSignalingMessage(Signaling signaling) throws IOException {
        String messageType = signaling.getType();

        if (leavingCall) {
            return;
        }

        if ("usersInRoom".equals(messageType)) {
            processUsersInRoom((List<HashMap<String, String>>) signaling.getMessageWrapper());
        } else if ("message".equals(messageType)) {
            NCSignalingMessage ncSignalingMessage = LoganSquare.parse(signaling.getMessageWrapper().toString(),
                    NCSignalingMessage.class);
            if (ncSignalingMessage.getRoomType().equals("video")) {
                MagicPeerConnectionWrapper magicPeerConnectionWrapper = alwaysGetPeerConnectionWrapperForSessionId
                        (ncSignalingMessage.getFrom());

                String type = null;
                if (ncSignalingMessage.getPayload() != null && ncSignalingMessage.getPayload().getType() !=
                        null) {
                    type = ncSignalingMessage.getPayload().getType();
                } else if (ncSignalingMessage.getType() != null) {
                    type = ncSignalingMessage.getType();
                }

                if (type != null) {
                    switch (type) {
                        case "offer":
                        case "answer":
                            magicPeerConnectionWrapper.setNick(ncSignalingMessage.getPayload().getNick());
                            String sessionDescriptionStringWithPreferredCodec = MagicWebRTCUtils.preferCodec
                                    (ncSignalingMessage.getPayload().getSdp(),
                                            "VP8", false);

                            SessionDescription sessionDescriptionWithPreferredCodec = new SessionDescription(
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
        } else {
            Log.d(TAG, "Something went very very wrong");
        }
    }

    // This method is called when the audio manager reports audio device change,
    // e.g. from wired headset to speakerphone.
    private void onAudioManagerDevicesChanged(
            final MagicAudioManager.AudioDevice device, final Set<MagicAudioManager.AudioDevice> availableDevices) {
        Log.d(TAG, "onAudioManagerDevicesChanged: " + availableDevices + ", "
                + "selected: " + device);
    }

    private void processUsersInRoom(List<HashMap<String, String>> users) {
        List<String> newSessions = new ArrayList<>();
        Set<String> oldSesssions = new HashSet<>();

        for (HashMap<String, String> participant : users) {
            if (!participant.get("sessionId").equals(callSession)) {
                Object inCallObject = participant.get("inCall");
                if ((boolean) inCallObject) {
                    newSessions.add(participant.get("sessionId"));
                } else {
                    oldSesssions.add(participant.get("sessionId"));
                }
            }
        }

        for (MagicPeerConnectionWrapper magicPeerConnectionWrapper : magicPeerConnectionWrapperList) {
            oldSesssions.add(magicPeerConnectionWrapper.getSessionId());
        }

        // Calculate sessions that left the call
        oldSesssions.removeAll(newSessions);

        // Calculate sessions that join the call
        newSessions.removeAll(oldSesssions);

        if (leavingCall) {
            return;
        }

        for (String sessionId : newSessions) {
            alwaysGetPeerConnectionWrapperForSessionId(sessionId);
        }

        for (String sessionId : oldSesssions) {
            endPeerConnection(sessionId);
        }
    }


    private void deleteMagicPeerConnection(MagicPeerConnectionWrapper magicPeerConnectionWrapper) {
        magicPeerConnectionWrapper.removePeerConnection();
        magicPeerConnectionWrapperList.remove(magicPeerConnectionWrapper);
    }

    private MagicPeerConnectionWrapper alwaysGetPeerConnectionWrapperForSessionId(String sessionId) {
        MagicPeerConnectionWrapper magicPeerConnectionWrapper;
        if ((magicPeerConnectionWrapper = getPeerConnectionWrapperForSessionId(sessionId)) != null) {
            return magicPeerConnectionWrapper;
        } else {
            magicPeerConnectionWrapper = new MagicPeerConnectionWrapper(peerConnectionFactory,
                    iceServers, sdpConstraints, sessionId, callSession, localMediaStream);
            magicPeerConnectionWrapperList.add(magicPeerConnectionWrapper);
            return magicPeerConnectionWrapper;
        }
    }

    private MagicPeerConnectionWrapper getPeerConnectionWrapperForSessionId(String sessionId) {
        for (MagicPeerConnectionWrapper magicPeerConnectionWrapper : magicPeerConnectionWrapperList) {
            if (magicPeerConnectionWrapper.getSessionId().equals(sessionId)) {
                return magicPeerConnectionWrapper;
            }
        }
        return null;
    }

    private void hangup(boolean dueToNetworkChange) {

        leavingCall = true;
        inCall = false;

        if (videoCapturer != null) {
            try {
                videoCapturer.stopCapture();
            } catch (InterruptedException e) {
                Log.e(TAG, "Failed to stop capturing while hanging up");
            }
            videoCapturer.dispose();
            videoCapturer = null;
        }

        for (int i = 0; i < magicPeerConnectionWrapperList.size(); i++) {
            endPeerConnection(magicPeerConnectionWrapperList.get(i).getSessionId());

        }

        pipVideoView.release();

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

        if (!dueToNetworkChange && credentials != null) {
            hangupNetworkCalls();
        } else {
            finish();
        }
    }

    private void hangupNetworkCalls() {
        ncApi.leaveCall(credentials, ApiUtils.getUrlForCall(baseUrl, roomToken))
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<GenericOverall>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onNext(GenericOverall genericOverall) {
                        ncApi.leaveRoom(credentials, ApiUtils.getUrlForRoomParticipants(baseUrl, roomToken))
                                .subscribeOn(Schedulers.newThread())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(new Observer<GenericOverall>() {
                                    @Override
                                    public void onSubscribe(Disposable d) {

                                    }

                                    @Override
                                    public void onNext(GenericOverall genericOverall) {
                                        finish();
                                    }

                                    @Override
                                    public void onError(Throwable e) {

                                    }

                                    @Override
                                    public void onComplete() {

                                    }
                                });

                    }

                    @Override
                    public void onError(Throwable e) {

                    }

                    @Override
                    public void onComplete() {

                    }
                });
    }

    private void gotNick(String sessionId, String nick) {
        RelativeLayout relativeLayout = remoteRenderersLayout.findViewWithTag(sessionId);
        if (relativeLayout != null) {
            TextView textView = relativeLayout.findViewById(R.id.peer_nick_text_view);
            textView.setText(nick);
        }
    }

    private void gotAudioOrVideoChange(boolean video, String sessionId, boolean change) {
        RelativeLayout relativeLayout = remoteRenderersLayout.findViewWithTag(sessionId);
        if (relativeLayout != null) {
            ImageView imageView;
            if (video) {
                imageView = relativeLayout.findViewById(R.id.remote_video_off);
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

    private void gotRemoteStream(MediaStream stream, String session) {
        removeMediaStream(session);

        if (stream.videoTracks.size() == 1) {
            VideoTrack videoTrack = stream.videoTracks.get(0);
            try {
                RelativeLayout relativeLayout = (RelativeLayout)
                        getLayoutInflater().inflate(R.layout.surface_renderer, remoteRenderersLayout,
                                false);
                relativeLayout.setTag(session);
                SurfaceViewRenderer surfaceViewRenderer = relativeLayout.findViewById(R.id
                        .surface_view);
                surfaceViewRenderer.setMirror(false);
                surfaceViewRenderer.init(rootEglBase.getEglBaseContext(), null);
                surfaceViewRenderer.setZOrderMediaOverlay(false);
                // disabled because it causes some devices to crash
                surfaceViewRenderer.setEnableHardwareScaler(false);
                surfaceViewRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
                surfaceViewRenderer.setOnClickListener(videoOnClickListener);
                VideoRenderer remoteRenderer = new VideoRenderer(surfaceViewRenderer);
                videoTrack.addRenderer(remoteRenderer);
                remoteRenderersLayout.addView(relativeLayout);
                gotNick(session, getPeerConnectionWrapperForSessionId(session).getNick());
            } catch (Exception e) {
                Log.d(TAG, "Failed to create a new video view");
            }
        }

        callControls.setZ(100.0f);
    }

    @Override
    public void onDestroy() {
        if (inCall) {
            hangup(false);
        }
        //this.unregisterReceiver(networkBroadcastReceier);
        rootEglBase.release();
        super.onDestroy();
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

    @Override
    public void onStart() {
        super.onStart();
        eventBus.register(this);
        if (videoOn && EffortlessPermissions.hasPermissions(this, PERMISSIONS_CAMERA)) {
            startVideoCapture();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        eventBus.unregister(this);
        if (videoCapturer != null && EffortlessPermissions.hasPermissions(this, PERMISSIONS_CAMERA)) {
            try {
                videoCapturer.stopCapture();
            } catch (InterruptedException e) {
                Log.e(TAG, "Failed to stop the capturing process");
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onMessageEvent(PeerConnectionEvent peerConnectionEvent) {
        if (peerConnectionEvent.getPeerConnectionEventType().equals(PeerConnectionEvent.PeerConnectionEventType
                .CLOSE_PEER)) {
            endPeerConnection(peerConnectionEvent.getSessionId());
        } else if (peerConnectionEvent.getPeerConnectionEventType().equals(PeerConnectionEvent
                .PeerConnectionEventType.SENSOR_FAR) ||
                peerConnectionEvent.getPeerConnectionEventType().equals(PeerConnectionEvent
                        .PeerConnectionEventType.SENSOR_NEAR)) {
            boolean enableVideo = peerConnectionEvent.getPeerConnectionEventType().equals(PeerConnectionEvent
                    .PeerConnectionEventType.SENSOR_FAR) && videoOn;
            if (EffortlessPermissions.hasPermissions(this, PERMISSIONS_CAMERA) && inCall && videoOn
                    && enableVideo != localVideoTrack.enabled()) {
                runOnUiThread(() -> toggleMedia(enableVideo, true));
            }
        } else if (peerConnectionEvent.getPeerConnectionEventType().equals(PeerConnectionEvent
                .PeerConnectionEventType.NICK_CHANGE)) {
            runOnUiThread(() -> gotNick(peerConnectionEvent.getSessionId(), peerConnectionEvent.getNick()));
        } else if (peerConnectionEvent.getPeerConnectionEventType().equals(PeerConnectionEvent
                .PeerConnectionEventType.VIDEO_CHANGE)) {
            runOnUiThread(() -> gotAudioOrVideoChange(true, peerConnectionEvent.getSessionId(),
                    peerConnectionEvent.getChangeValue()));
        } else if (peerConnectionEvent.getPeerConnectionEventType().equals(PeerConnectionEvent
                .PeerConnectionEventType.AUDIO_CHANGE)) {
            runOnUiThread(() -> gotAudioOrVideoChange(false, peerConnectionEvent.getSessionId(),
                    peerConnectionEvent.getChangeValue()));
        }
    }

    private void endPeerConnection(String sessionId) {
        MagicPeerConnectionWrapper magicPeerConnectionWrapper;
        if ((magicPeerConnectionWrapper = getPeerConnectionWrapperForSessionId(sessionId)) != null) {
            runOnUiThread(() -> removeMediaStream(sessionId));
            deleteMagicPeerConnection(magicPeerConnectionWrapper);
        }
    }

    private void removeMediaStream(String sessionId) {
        if (remoteRenderersLayout.getChildCount() > 0) {
            RelativeLayout relativeLayout = remoteRenderersLayout.findViewWithTag(sessionId);
            if (relativeLayout != null) {
                SurfaceViewRenderer surfaceViewRenderer = relativeLayout.findViewById(R.id.surface_view);
                surfaceViewRenderer.release();
                remoteRenderersLayout.removeView(relativeLayout);
                remoteRenderersLayout.invalidate();
            }
        }

        callControls.setZ(100.0f);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(MediaStreamEvent mediaStreamEvent) {
        if (mediaStreamEvent.getMediaStream() != null) {
            gotRemoteStream(mediaStreamEvent.getMediaStream(), mediaStreamEvent.getSession());
        } else {
            removeMediaStream(mediaStreamEvent.getSession());
        }
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onMessageEvent(SessionDescriptionSendEvent sessionDescriptionSend) throws IOException {
        NCMessageWrapper ncMessageWrapper = new NCMessageWrapper();
        ncMessageWrapper.setEv("message");
        ncMessageWrapper.setSessionId(callSession);
        NCSignalingMessage ncSignalingMessage = new NCSignalingMessage();
        ncSignalingMessage.setTo(sessionDescriptionSend.getPeerId());
        ncSignalingMessage.setRoomType("video");
        ncSignalingMessage.setType(sessionDescriptionSend.getType());
        NCMessagePayload ncMessagePayload = new NCMessagePayload();
        ncMessagePayload.setType(sessionDescriptionSend.getType());

        if (!"candidate".equals(sessionDescriptionSend.getType())) {
            ncMessagePayload.setSdp(sessionDescriptionSend.getSessionDescription().description);
            ncMessagePayload.setNick(userEntity.getDisplayName());
        } else {
            ncMessagePayload.setIceCandidate(sessionDescriptionSend.getNcIceCandidate());
        }


        // Set all we need
        ncSignalingMessage.setPayload(ncMessagePayload);
        ncMessageWrapper.setSignalingMessage(ncSignalingMessage);


        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("{");
        stringBuilder.append("\"fn\":\"");
        stringBuilder.append(StringEscapeUtils.escapeJson(LoganSquare.serialize(ncMessageWrapper
                .getSignalingMessage()))).append("\"");
        stringBuilder.append(",");
        stringBuilder.append("\"sessionId\":");
        stringBuilder.append("\"").append(StringEscapeUtils.escapeJson(callSession)).append("\"");
        stringBuilder.append(",");
        stringBuilder.append("\"ev\":\"message\"");
        stringBuilder.append("}");

        List<String> strings = new ArrayList<>();
        String stringToSend = stringBuilder.toString();
        strings.add(stringToSend);

        ncApi.sendSignalingMessages(credentials, ApiUtils.getUrlForSignaling(baseUrl),
                strings.toString())
                .retry(3)
                .subscribeOn(Schedulers.newThread())
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
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {

        // Checks the orientation of the screen
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            remoteRenderersLayout.setOrientation(LinearLayout.HORIZONTAL);
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            remoteRenderersLayout.setOrientation(LinearLayout.VERTICAL);
        }

        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        EffortlessPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults,
                this);
    }

    private void animateCallControls(boolean show, long startDelay) {
        if (!isPTTActive) {
            float alpha;
            long duration;

            if (show) {
                handler.removeCallbacksAndMessages(null);
                alpha = 1.0f;
                duration = 1000;
                if (callControls.getVisibility() != View.VISIBLE) {
                    callControls.setAlpha(0.0f);
                    callControls.setVisibility(View.VISIBLE);
                } else {
                    handler.postDelayed(() -> animateCallControls(false, 0), 5000);
                    return;
                }
            } else {
                alpha = 0.0f;
                duration = 1000;
            }

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
                                } else {
                                    handler.postDelayed(new Runnable() {
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
    }

    @Override
    public void onBackPressed() {
        onHangupClick();
    }

    private class microphoneButtonTouchListener implements View.OnTouchListener {

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            v.onTouchEvent(event);
            if (event.getAction() == MotionEvent.ACTION_UP && isPTTActive) {
                isPTTActive = false;
                microphoneControlButton.getFrontImageView().setImageResource(R.drawable.ic_mic_off_white_24px);
                pulseAnimation.stop();
                toggleMedia(false, false);
                animateCallControls(false, 5000);
            }
            return true;
        }
    }

    private class videoClickListener implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            showCallControls();
        }
    }
}
