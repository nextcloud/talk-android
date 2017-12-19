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

import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.bluelinelabs.logansquare.LoganSquare;
import com.nextcloud.talk.R;
import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.api.helpers.api.ApiHelper;
import com.nextcloud.talk.api.models.json.call.CallOverall;
import com.nextcloud.talk.api.models.json.generic.GenericOverall;
import com.nextcloud.talk.api.models.json.signaling.NCIceCandidate;
import com.nextcloud.talk.api.models.json.signaling.NCMessagePayload;
import com.nextcloud.talk.api.models.json.signaling.NCMessageWrapper;
import com.nextcloud.talk.api.models.json.signaling.NCSignalingMessage;
import com.nextcloud.talk.api.models.json.signaling.Signaling;
import com.nextcloud.talk.api.models.json.signaling.SignalingOverall;
import com.nextcloud.talk.api.models.json.signaling.settings.IceServer;
import com.nextcloud.talk.api.models.json.signaling.settings.SignalingSettingsOverall;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.events.MediaStreamEvent;
import com.nextcloud.talk.events.PeerConnectionEvent;
import com.nextcloud.talk.events.SessionDescriptionSendEvent;
import com.nextcloud.talk.persistence.entities.UserEntity;
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
import org.webrtc.CameraEnumerator;
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
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.BooleanSupplier;
import io.reactivex.schedulers.Schedulers;
import me.zhanghai.android.effortlesspermissions.EffortlessPermissions;
import me.zhanghai.android.effortlesspermissions.OpenAppDetailsDialogFragment;
import pub.devrel.easypermissions.AfterPermissionGranted;

@AutoInjector(NextcloudTalkApplication.class)
public class CallActivity extends AppCompatActivity {
    private static final String TAG = "CallActivity";
    private static final String[] PERMISSIONS_CALL = {
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO
    };
    @BindView(R.id.pip_video_view)
    SurfaceViewRenderer pipVideoView;
    @BindView(R.id.full_screen_surface_view)
    SurfaceViewRenderer fullScreenVideoView;
    @BindView(R.id.relative_layout)
    RelativeLayout relativeLayout;
    @BindView(R.id.remote_renderers_layout)
    LinearLayout remoteRenderersLayout;
    @Inject
    NcApi ncApi;
    @Inject
    EventBus eventBus;
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
    BooleanSupplier booleanSupplier = () -> leavingCall;
    Disposable signalingDisposable;
    Disposable pingDisposable;
    List<PeerConnection.IceServer> iceServers;
    private String roomToken;
    private UserEntity userEntity;
    private String callSession;
    private VideoCapturer videoCapturerAndroid;
    private MediaStream localMediaStream;
    private String credentials;
    private List<MagicPeerConnectionWrapper> magicPeerConnectionWrapperList = new ArrayList<>();

    private static int getSystemUiVisibility() {
        int flags = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN;
        flags |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        return flags;
    }

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

        roomToken = getIntent().getExtras().getString("roomToken", "");
        userEntity = Parcels.unwrap((Parcelable) getIntent().getExtras().get("userEntity"));
        callSession = "0";

        credentials = ApiHelper.getCredentials(userEntity.getUsername(), userEntity.getToken());
        initViews();

        checkPermissions();

    }

    private VideoCapturer createVideoCapturer() {
        videoCapturer = createCameraCapturer(new Camera1Enumerator(false));
        return videoCapturer;
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
        pipVideoView.setMirror(true);
        rootEglBase = EglBase.create();
        pipVideoView.init(rootEglBase.getEglBaseContext(), null);
        pipVideoView.setZOrderMediaOverlay(true);
        pipVideoView.setEnableHardwareScaler(true);
        pipVideoView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);

        fullScreenVideoView.setMirror(true);
        fullScreenVideoView.init(rootEglBase.getEglBaseContext(), null);
        fullScreenVideoView.setZOrderMediaOverlay(true);
        fullScreenVideoView.setEnableHardwareScaler(true);
        fullScreenVideoView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);

    }

    @AfterPermissionGranted(100)
    private void checkPermissions() {
        if (EffortlessPermissions.hasPermissions(this, PERMISSIONS_CALL)) {
            start();
        } else if (EffortlessPermissions.somePermissionPermanentlyDenied(this,
                PERMISSIONS_CALL)) {
            // Some permission is permanently denied so we cannot request them normally.
            OpenAppDetailsDialogFragment.show(
                    R.string.nc_permissions_permanently_denied,
                    R.string.nc_permissions_settings, this);
        } else {
            EffortlessPermissions.requestPermissions(this, R.string.nc_permissions,
                    100, PERMISSIONS_CALL);
        }
    }

    public void start() {
        //Initialize PeerConnectionFactory globals.
        PeerConnectionFactory.InitializationOptions initializationOptions = PeerConnectionFactory.InitializationOptions
                .builder(this)
                .setEnableVideoHwAcceleration(false)
                .createInitializationOptions();
        PeerConnectionFactory.initialize(initializationOptions);

        //Create a new PeerConnectionFactory instance.
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        peerConnectionFactory = new PeerConnectionFactory(options);

        //Now create a VideoCapturer instance. Callback methods are there if you want to do something! Duh!
        videoCapturerAndroid = createVideoCapturer();

        //Create MediaConstraints - Will be useful for specifying video and audio constraints.
        audioConstraints = new MediaConstraints();
        videoConstraints = new MediaConstraints();

        //Create a VideoSource instance
        videoSource = peerConnectionFactory.createVideoSource(videoCapturerAndroid);
        localVideoTrack = peerConnectionFactory.createVideoTrack("NCv0", videoSource);

        //create an AudioSource instance
        audioSource = peerConnectionFactory.createAudioSource(audioConstraints);
        localAudioTrack = peerConnectionFactory.createAudioTrack("NCa0", audioSource);

        localMediaStream = peerConnectionFactory.createLocalMediaStream("NCMS");
        localMediaStream.addTrack(localAudioTrack);
        localMediaStream.addTrack(localVideoTrack);

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

        startVideoCapture();

        //create a videoRenderer based on SurfaceViewRenderer instance
        localRenderer = new VideoRenderer(fullScreenVideoView);
        // And finally, with our VideoRenderer ready, we
        // can add our renderer to the VideoTrack.
        localVideoTrack.addRenderer(localRenderer);

        iceServers = new ArrayList<>();

        //create sdpConstraints
        sdpConstraints = new MediaConstraints();
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
        sdpConstraints.optional.add(new MediaConstraints.KeyValuePair("internalSctpDataChannels", "true"));
        sdpConstraints.optional.add(new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));


        ncApi.getSignalingSettings(ApiHelper.getCredentials(userEntity.getUsername(), userEntity.getToken()),
                ApiHelper.getUrlForSignalingSettings(userEntity.getBaseUrl()))
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
            Resources r = getResources();
            int px = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 120, r.getDisplayMetrics());
            videoCapturerAndroid.startCapture(px, px, 30);
        }
    }

    private void joinRoomAndCall() {
        ncApi.joinRoom(credentials, ApiHelper.getUrlForRoom(userEntity.getBaseUrl(), roomToken))
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<CallOverall>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onNext(CallOverall callOverall) {
                        ncApi.joinCall(credentials,
                                ApiHelper.getUrlForCall(userEntity.getBaseUrl(), roomToken))
                                .subscribeOn(Schedulers.newThread())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(new Observer<GenericOverall>() {
                                    @Override
                                    public void onSubscribe(Disposable d) {

                                    }

                                    @Override
                                    public void onNext(GenericOverall genericOverall) {
                                        callSession = callOverall.getOcs().getData().getSessionId();

                                        // start pinging the call
                                        ncApi.pingCall(ApiHelper.getCredentials(userEntity.getUsername(), userEntity.getToken()),
                                                ApiHelper.getUrlForCallPing(userEntity.getBaseUrl(), roomToken))
                                                .subscribeOn(Schedulers.newThread())
                                                .observeOn(AndroidSchedulers.mainThread())
                                                .repeatWhen(completed -> completed.delay(5000, TimeUnit.MILLISECONDS))
                                                .repeatUntil(booleanSupplier)
                                                .retry(3)
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
                                        ncApi.pullSignalingMessages(ApiHelper.getCredentials(userEntity.getUsername(),
                                                userEntity.getToken()), ApiHelper.getUrlForSignaling(userEntity.getBaseUrl()))
                                                .subscribeOn(Schedulers.newThread())
                                                .observeOn(AndroidSchedulers.mainThread())
                                                //.repeatWhen(observable -> observable.delay(1500, TimeUnit
                                                //        .MILLISECONDS))
                                                .repeatWhen(completed -> completed)
                                                .repeatUntil(booleanSupplier)
                                                .retry(3)
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
                                                                    e.printStackTrace();
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

                            magicPeerConnectionWrapper.getPeerConnection().setRemoteDescription(magicPeerConnectionWrapper
                                    .getMagicSdpObserver(), sessionDescriptionWithPreferredCodec);
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
        if (magicPeerConnectionWrapper.getPeerConnection() != null) {
            magicPeerConnectionWrapper.getPeerConnection().close();
        }
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

    private void hangup() {

        leavingCall = true;
        dispose(null);

        for (int i = 0; i < magicPeerConnectionWrapperList.size(); i++) {
            endPeerConnection(magicPeerConnectionWrapperList.get(i).getSessionId());

        }

        if (videoCapturer != null) {
            videoCapturer.dispose();
        }


        if (localMediaStream != null) {
            if (localMediaStream.videoTracks != null && localMediaStream.videoTracks.size() > 0) {
                localMediaStream.removeTrack(localMediaStream.videoTracks.get(0));
            }

            if (localMediaStream.audioTracks != null && localMediaStream.audioTracks.size() > 0) {
                localMediaStream.removeTrack(localMediaStream.audioTracks.get(0));
            }
        }

        localVideoTrack = null;
        localAudioTrack = null;
        localRenderer = null;
        localMediaStream = null;

        if (videoCapturer != null) {
            videoCapturer.dispose();
            videoCapturer = null;
        }

        pipVideoView.release();

        String credentials = ApiHelper.getCredentials(userEntity.getUsername(), userEntity.getToken());
        ncApi.leaveCall(credentials, ApiHelper.getUrlForCall(userEntity.getBaseUrl(), roomToken))
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<GenericOverall>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onNext(GenericOverall genericOverall) {
                        ncApi.leaveRoom(credentials, ApiHelper.getUrlForRoom(userEntity.getBaseUrl(), roomToken))
                                .subscribeOn(Schedulers.newThread())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(new Observer<GenericOverall>() {
                                    @Override
                                    public void onSubscribe(Disposable d) {

                                    }

                                    @Override
                                    public void onNext(GenericOverall genericOverall) {

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

    private void gotRemoteStream(MediaStream stream, String session) {
        if (fullScreenVideoView != null) {
            remoteRenderersLayout.setVisibility(View.VISIBLE);
            pipVideoView.setVisibility(View.VISIBLE);
            localVideoTrack.removeRenderer(localRenderer);
            localRenderer = new VideoRenderer(pipVideoView);
            localVideoTrack.addRenderer(localRenderer);
            relativeLayout.removeView(fullScreenVideoView);
        }

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
                surfaceViewRenderer.setZOrderMediaOverlay(true);
                surfaceViewRenderer.setEnableHardwareScaler(true);
                surfaceViewRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
                VideoRenderer remoteRenderer = new VideoRenderer(surfaceViewRenderer);
                videoTrack.addRenderer(remoteRenderer);
                remoteRenderersLayout.addView(relativeLayout);
                relativeLayout.invalidate();
                gotNick(session, getPeerConnectionWrapperForSessionId(session).getNick());
            } catch (Exception e) {
                Log.d(TAG, "Failed to create a new video view");
            }
        }
    }

    @Override
    public void onDestroy() {
        hangup();
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
        startVideoCapture();
    }

    @Override
    public void onStop() {
        super.onStop();
        eventBus.unregister(this);
        if (videoCapturer != null) {
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

            /*boolean enableVideo = peerConnectionEvent.getPeerConnectionEventType().equals(PeerConnectionEvent
                    .PeerConnectionEventType.SENSOR_FAR);
            String videoMessage = "videoOff";
            if (enableVideo) {
                videoMessage = "videoOn";
            }

            localMediaStream.videoTracks.get(0).setEnabled(enableVideo);
            for (int i = 0; i < magicPeerConnectionWrapperList.size(); i++) {
                magicPeerConnectionWrapperList.get(i).sendChannelData(new DataChannelMessage(videoMessage));
            }*/
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
        String credentials = ApiHelper.getCredentials(userEntity.getUsername(), userEntity.getToken());
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

        ncApi.sendSignalingMessages(credentials, ApiHelper.getUrlForSignaling(userEntity.getBaseUrl()),
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
}
