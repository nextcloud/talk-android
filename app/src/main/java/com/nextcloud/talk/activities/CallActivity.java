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
import android.content.res.Resources;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

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
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.events.MediaStreamEvent;
import com.nextcloud.talk.events.SessionDescriptionSendEvent;
import com.nextcloud.talk.persistence.entities.UserEntity;
import com.nextcloud.talk.webrtc.PeerConnectionWrapper;

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
import java.util.List;
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
import ru.alexbykov.nopermission.PermissionHelper;

@AutoInjector(NextcloudTalkApplication.class)
public class CallActivity extends AppCompatActivity {
    private static final String TAG = "CallActivity";

    @BindView(R.id.pip_video_view)
    SurfaceViewRenderer pipVideoView;

    @BindView(R.id.fullscreen_video_view)
    SurfaceViewRenderer fullScreenVideoView;

    @Inject
    NcApi ncApi;

    @Inject
    EventBus eventBus;

    PeerConnectionFactory peerConnectionFactory;
    MediaConstraints audioConstraints;
    MediaConstraints videoConstraints;
    MediaConstraints sdpConstraints;
    VideoSource videoSource;
    VideoTrack localVideoTrack;
    AudioSource audioSource;
    AudioTrack localAudioTrack;
    VideoCapturer videoCapturer;
    VideoRenderer localRenderer;
    VideoRenderer remoteRenderer;
    PeerConnection localPeer;
    boolean leavingCall = false;
    BooleanSupplier booleanSupplier = () -> leavingCall;
    Disposable signalingDisposable;
    Disposable pingDisposable;
    List<PeerConnection.IceServer> iceServers;
    private String roomToken;
    private UserEntity userEntity;
    private String callSession;

    private String credentials;
    private List<PeerConnectionWrapper> peerConnectionWrapperList = new ArrayList<>();

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
        userEntity = Parcels.unwrap(getIntent().getExtras().getParcelable("userEntity"));
        callSession = "0";

        credentials = ApiHelper.getCredentials(userEntity.getUsername(), userEntity.getToken());
        initViews();

        PermissionHelper permissionHelper = new PermissionHelper(this);
        permissionHelper.check(android.Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO,
                Manifest.permission.MODIFY_AUDIO_SETTINGS, Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.ACCESS_WIFI_STATE, Manifest.permission.INTERNET)
                .onSuccess(() -> {
                    start();
                })
                .onDenied(new Runnable() {
                    @Override
                    public void run() {
                        // do nothing
                    }
                })
                .run();

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
        fullScreenVideoView.setMirror(false);
        EglBase rootEglBase = EglBase.create();
        pipVideoView.init(rootEglBase.getEglBaseContext(), null);
        pipVideoView.setZOrderMediaOverlay(true);
        fullScreenVideoView.init(rootEglBase.getEglBaseContext(), null);
        fullScreenVideoView.setZOrderMediaOverlay(true);
        fullScreenVideoView.setEnableHardwareScaler(true);
        pipVideoView.setEnableHardwareScaler(true);
        pipVideoView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        fullScreenVideoView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
    }

    public void start() {
        //Initialize PeerConnectionFactory globals.
        //Params are context, initAudio,initVideo and videoCodecHwAcceleration
        PeerConnectionFactory.initializeAndroidGlobals(this, true, true,
                false);

        //Create a new PeerConnectionFactory instance.
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        peerConnectionFactory = new PeerConnectionFactory(options);

        //Now create a VideoCapturer instance. Callback methods are there if you want to do something! Duh!
        VideoCapturer videoCapturerAndroid = createVideoCapturer();

        //Create MediaConstraints - Will be useful for specifying video and audio constraints.
        audioConstraints = new MediaConstraints();
        videoConstraints = new MediaConstraints();

        //Create a VideoSource instance
        videoSource = peerConnectionFactory.createVideoSource(videoCapturerAndroid);
        localVideoTrack = peerConnectionFactory.createVideoTrack("NCv0", videoSource);

        //create an AudioSource instance
        audioSource = peerConnectionFactory.createAudioSource(audioConstraints);
        localAudioTrack = peerConnectionFactory.createAudioTrack("NCa0", audioSource);

        Resources r = getResources();
        int px = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 120, r.getDisplayMetrics());
        videoCapturerAndroid.startCapture(px, px, 30);

        //create a videoRenderer based on SurfaceViewRenderer instance
        localRenderer = new VideoRenderer(pipVideoView);
        // And finally, with our VideoRenderer ready, we
        // can add our renderer to the VideoTrack.
        localVideoTrack.addRenderer(localRenderer);

        //we already have video and audio tracks. Now create peerconnections
        iceServers = new ArrayList<>();
        iceServers.add(new PeerConnection.IceServer("stun:stun.nextcloud.com:443"));

        //create sdpConstraints
        sdpConstraints = new MediaConstraints();
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
        sdpConstraints.optional.add(new MediaConstraints.KeyValuePair("internalSctpDataChannels", "true"));
        sdpConstraints.optional.add(new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));


        ncApi.joinRoom(credentials, ApiHelper.getUrlForJoinRoom(userEntity.getBaseUrl(), roomToken))
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
                                         localPeer = alwaysGetPeerConnectionWrapperForSessionId(callSession, true).
                                                getPeerConnection();

                                        //creating local mediastream
                                        MediaStream stream = peerConnectionFactory.createLocalMediaStream("NCMS");
                                        stream.addTrack(localAudioTrack);
                                        stream.addTrack(localVideoTrack);
                                        localPeer.addStream(stream);

                                        // start pinging the call
                                        ncApi.pingCall(ApiHelper.getCredentials(userEntity.getUsername(), userEntity.getToken()),
                                                ApiHelper.getUrlForCallPing(userEntity.getBaseUrl(), roomToken))
                                                .subscribeOn(Schedulers.newThread())
                                                .observeOn(AndroidSchedulers.mainThread())
                                                .repeatWhen(observable -> observable.delay(5000, TimeUnit.MILLISECONDS))
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
                                                .repeatWhen(observable -> observable.delay(1500, TimeUnit.MILLISECONDS))
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
                PeerConnectionWrapper peerConnectionWrapper = alwaysGetPeerConnectionWrapperForSessionId
                        (ncSignalingMessage.getFrom(), ncSignalingMessage.getFrom().equals(callSession));

                switch (ncSignalingMessage.getType()) {
                    case "offer":
                    case "answer":
                        peerConnectionWrapper.setNick(ncSignalingMessage.getPayload().getNick());
                        peerConnectionWrapper.getPeerConnection().setRemoteDescription(peerConnectionWrapper
                                .getMagicSdpObserver(), new
                                SessionDescription
                                (SessionDescription.Type
                                        .fromCanonicalForm(ncSignalingMessage.getType()),
                                        ncSignalingMessage
                                                .getPayload().getSdp()));

                        break;
                    case "candidate":
                        NCIceCandidate ncIceCandidate = ncSignalingMessage.getPayload().getIceCandidate();
                        IceCandidate iceCandidate = new IceCandidate(ncIceCandidate.getSdpMid(),
                            ncIceCandidate.getSdpMLineIndex(), ncIceCandidate.getCandidate());
                        peerConnectionWrapper.addCandidate(iceCandidate);
                        break;
                    case "endOfCandidates":
                        Log.d("MARIO", "END CANDIDATES");
                        peerConnectionWrapper.drainIceCandidates();
                        break;
                    default:
                        break;
                }
            }
        } else {
            Log.d(TAG, "Something went very very wrong");
        }
    }

    private void processUsersInRoom(List<HashMap<String, String>> users) {
        List<String> newSessions = new ArrayList<>();
        List<String> oldSesssions = new ArrayList<>();

        for (HashMap<String, String> participant : users) {
            if (participant.containsKey("sessionId") && !participant.get("sessionId").equals
                    (callSession)) {
                newSessions.add(participant.get("sessionId"));
            }
        }

        for (PeerConnectionWrapper peerConnectionWrapper : peerConnectionWrapperList) {
            if (!peerConnectionWrapper.isLocal()) {
                oldSesssions.add(peerConnectionWrapper.getSessionId());
            }
        }

        // Calculate sessions that left the call
        List<String> leftSessions = oldSesssions;
        leftSessions.removeAll(newSessions);

        // Calculate sessions that join the call
        newSessions.removeAll(oldSesssions);

        if (leavingCall) {
            return;
        }

        PeerConnectionWrapper peerConnectionWrapper;

        for (String sessionId : newSessions) {
            if (getPeerConnectionWrapperForSessionId(sessionId) == null) {
                if (sessionId.compareTo(callSession) < 0 ) {
                    PeerConnectionWrapper connectionWrapper = alwaysGetPeerConnectionWrapperForSessionId(sessionId,
                            false);
                    connectionWrapper.getPeerConnection().createAnswer(connectionWrapper.getMagicSdpObserver(), sdpConstraints);
                } else {
                    Log.d(TAG, "Waiting for offer");
                }

            }
        }

        for (String sessionId : leftSessions) {
            if ((peerConnectionWrapper = getPeerConnectionWrapperForSessionId(sessionId)) != null) {
                peerConnectionWrapper.getPeerConnection().close();
                peerConnectionWrapperList.remove(peerConnectionWrapper);
            }
        }
    }


    private PeerConnectionWrapper alwaysGetPeerConnectionWrapperForSessionId(String sessionId, boolean isLocalPeer) {
        PeerConnectionWrapper peerConnectionWrapper;
        if ((peerConnectionWrapper = getPeerConnectionWrapperForSessionId(sessionId)) != null) {
            return peerConnectionWrapper;
        } else {

            peerConnectionWrapper = new PeerConnectionWrapper(peerConnectionFactory,
                    iceServers, sdpConstraints, sessionId, isLocalPeer, callSession);
            peerConnectionWrapperList.add(peerConnectionWrapper);
            return peerConnectionWrapper;
        }
    }

    private PeerConnectionWrapper getPeerConnectionWrapperForSessionId(String sessionId) {
        for (PeerConnectionWrapper peerConnectionWrapper : peerConnectionWrapperList) {
            if (peerConnectionWrapper.getSessionId().equals(sessionId)) {
                return peerConnectionWrapper;
            }
        }
        return null;
    }

    private void hangup() {

        leavingCall = true;

        dispose(null);

        for (PeerConnectionWrapper peerConnectionWrapper : peerConnectionWrapperList) {
            if (peerConnectionWrapper.getPeerConnection() != null) {
                peerConnectionWrapper.getPeerConnection().close();
            }
        }

        if (videoCapturer != null) {
            videoCapturer.dispose();
        }

        pipVideoView.release();
        fullScreenVideoView.release();

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

                    }

                    @Override
                    public void onError(Throwable e) {

                    }

                    @Override
                    public void onComplete() {

                    }
                });
    }

    private void gotRemoteStream(MediaStream stream) {
        //we have remote video stream. add to the renderer.
        final VideoTrack videoTrack = stream.videoTracks.getFirst();
        AudioTrack audioTrack = stream.audioTracks.getFirst();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    remoteRenderer = new VideoRenderer(fullScreenVideoView);
                    videoTrack.addRenderer(remoteRenderer);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        hangup();
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
    }

    @Override
    public void onStop() {
        super.onStop();
        eventBus.unregister(this);
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onMessageEvent(MediaStreamEvent mediaStreamEvent) {
        gotRemoteStream(mediaStreamEvent.getMediaStream());
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onMessageEvent(SessionDescriptionSendEvent sessionDescriptionSend) {
        Log.d("MARIO_123", "SENDING " + sessionDescriptionSend.getType());
        String credentials = ApiHelper.getCredentials(userEntity.getUsername(), userEntity.getToken());
        NCMessageWrapper ncMessageWrapper = new NCMessageWrapper();
        ncMessageWrapper.setEv("message");
        ncMessageWrapper.setSessionId(callSession);
        // Create signaling message and payload
        NCSignalingMessage ncSignalingMessage = new NCSignalingMessage();
        //ncSignalingMessage.setFrom(callSession);
        ncSignalingMessage.setTo(sessionDescriptionSend.getPeerId());
        ncSignalingMessage.setRoomType("video");
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

        ncApi.sendSignalingMessages(credentials, ApiHelper.getUrlForSignaling(userEntity.getBaseUrl()),
                ncMessageWrapper)
                .subscribeOn(Schedulers.newThread())
                .subscribe(new Observer<Integer>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onNext(Integer integer) {

                    }

                    @Override
                    public void onError(Throwable e) {

                    }

                    @Override
                    public void onComplete() {

                    }
                });
    }


}
