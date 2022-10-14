/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Tim Krüger
 * Copyright (C) 2022 Tim Krüger <t@timkrueger.me>
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

package com.nextcloud.talk.activities;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.app.RemoteAction;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.bluelinelabs.logansquare.LoganSquare;
import com.nextcloud.talk.R;
import com.nextcloud.talk.adapters.ParticipantDisplayItem;
import com.nextcloud.talk.adapters.ParticipantsAdapter;
import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.data.user.model.User;
import com.nextcloud.talk.databinding.CallActivityBinding;
import com.nextcloud.talk.events.ConfigurationChangeEvent;
import com.nextcloud.talk.events.MediaStreamEvent;
import com.nextcloud.talk.events.NetworkEvent;
import com.nextcloud.talk.events.PeerConnectionEvent;
import com.nextcloud.talk.events.SessionDescriptionSendEvent;
import com.nextcloud.talk.events.WebSocketCommunicationEvent;
import com.nextcloud.talk.models.ExternalSignalingServer;
import com.nextcloud.talk.models.json.capabilities.CapabilitiesOverall;
import com.nextcloud.talk.models.json.conversations.Conversation;
import com.nextcloud.talk.models.json.conversations.RoomOverall;
import com.nextcloud.talk.models.json.conversations.RoomsOverall;
import com.nextcloud.talk.models.json.generic.GenericOverall;
import com.nextcloud.talk.models.json.participants.Participant;
import com.nextcloud.talk.models.json.participants.ParticipantsOverall;
import com.nextcloud.talk.models.json.signaling.DataChannelMessage;
import com.nextcloud.talk.models.json.signaling.DataChannelMessageNick;
import com.nextcloud.talk.models.json.signaling.NCIceCandidate;
import com.nextcloud.talk.models.json.signaling.NCMessagePayload;
import com.nextcloud.talk.models.json.signaling.NCMessageWrapper;
import com.nextcloud.talk.models.json.signaling.NCSignalingMessage;
import com.nextcloud.talk.models.json.signaling.Signaling;
import com.nextcloud.talk.models.json.signaling.SignalingOverall;
import com.nextcloud.talk.models.json.signaling.settings.IceServer;
import com.nextcloud.talk.models.json.signaling.settings.SignalingSettingsOverall;
import com.nextcloud.talk.ui.dialog.AudioOutputDialog;
import com.nextcloud.talk.users.UserManager;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.DisplayUtils;
import com.nextcloud.talk.utils.NotificationUtils;
import com.nextcloud.talk.utils.animations.PulseAnimation;
import com.nextcloud.talk.utils.permissions.PlatformPermissionUtil;
import com.nextcloud.talk.utils.power.PowerManagerUtils;
import com.nextcloud.talk.utils.preferences.AppPreferences;
import com.nextcloud.talk.utils.singletons.ApplicationWideCurrentRoomHolder;
import com.nextcloud.talk.webrtc.MagicWebRTCUtils;
import com.nextcloud.talk.webrtc.MagicWebSocketInstance;
import com.nextcloud.talk.webrtc.PeerConnectionWrapper;
import com.nextcloud.talk.webrtc.WebRtcAudioManager;
import com.nextcloud.talk.webrtc.WebSocketConnectionHelper;
import com.wooplr.spotlight.SpotlightView;

import org.apache.commons.lang3.StringEscapeUtils;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RendererCommon;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.inject.Inject;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import autodagger.AutoInjector;
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import me.zhanghai.android.effortlesspermissions.AfterPermissionDenied;
import me.zhanghai.android.effortlesspermissions.EffortlessPermissions;
import me.zhanghai.android.effortlesspermissions.OpenAppDetailsDialogFragment;
import okhttp3.Cache;
import pub.devrel.easypermissions.AfterPermissionGranted;

import static android.app.PendingIntent.FLAG_IMMUTABLE;
import static com.nextcloud.talk.utils.bundle.BundleKeys.KEY_CALL_VOICE_ONLY;
import static com.nextcloud.talk.utils.bundle.BundleKeys.KEY_CALL_WITHOUT_NOTIFICATION;
import static com.nextcloud.talk.utils.bundle.BundleKeys.KEY_CONVERSATION_NAME;
import static com.nextcloud.talk.utils.bundle.BundleKeys.KEY_CONVERSATION_PASSWORD;
import static com.nextcloud.talk.utils.bundle.BundleKeys.KEY_FROM_NOTIFICATION_START_CALL;
import static com.nextcloud.talk.utils.bundle.BundleKeys.KEY_MODIFIED_BASE_URL;
import static com.nextcloud.talk.utils.bundle.BundleKeys.KEY_PARTICIPANT_PERMISSION_CAN_PUBLISH_AUDIO;
import static com.nextcloud.talk.utils.bundle.BundleKeys.KEY_PARTICIPANT_PERMISSION_CAN_PUBLISH_VIDEO;
import static com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_ID;
import static com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN;
import static com.nextcloud.talk.utils.bundle.BundleKeys.KEY_USER_ENTITY;
import static com.nextcloud.talk.webrtc.Globals.JOB_ID;
import static com.nextcloud.talk.webrtc.Globals.PARTICIPANTS_UPDATE;
import static com.nextcloud.talk.webrtc.Globals.ROOM_TOKEN;
import static com.nextcloud.talk.webrtc.Globals.UPDATE_ALL;
import static com.nextcloud.talk.webrtc.Globals.UPDATE_IN_CALL;

@AutoInjector(NextcloudTalkApplication.class)
public class CallActivity extends CallBaseActivity {

    public static final String VIDEO_STREAM_TYPE_SCREEN = "screen";
    public static final String VIDEO_STREAM_TYPE_VIDEO = "video";

    @Inject
    NcApi ncApi;
    @Inject
    EventBus eventBus;
    @Inject
    UserManager userManager;
    @Inject
    AppPreferences appPreferences;
    @Inject
    Cache cache;
    @Inject
    PlatformPermissionUtil permissionUtil;

    public static final String TAG = "CallActivity";

    public WebRtcAudioManager audioManager;

    private static final String[] PERMISSIONS_CALL = {
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    };

    private static final String[] PERMISSIONS_CAMERA = {
        Manifest.permission.CAMERA
    };

    private static final String[] PERMISSIONS_MICROPHONE = {
        Manifest.permission.RECORD_AUDIO
    };

    private static final String MICROPHONE_PIP_INTENT_NAME = "microphone_pip_intent";
    private static final String MICROPHONE_PIP_INTENT_EXTRA_ACTION = "microphone_pip_action";
    private static final int MICROPHONE_PIP_REQUEST_MUTE = 1;
    private static final int MICROPHONE_PIP_REQUEST_UNMUTE = 2;

    private BroadcastReceiver mReceiver;

    private PeerConnectionFactory peerConnectionFactory;
    private MediaConstraints audioConstraints;
    private MediaConstraints videoConstraints;
    private MediaConstraints sdpConstraints;
    private MediaConstraints sdpConstraintsForMCU;
    private VideoSource videoSource;
    private VideoTrack localVideoTrack;
    private AudioSource audioSource;
    private AudioTrack localAudioTrack;
    private VideoCapturer videoCapturer;
    private EglBase rootEglBase;
    private Disposable signalingDisposable;
    private List<PeerConnection.IceServer> iceServers;
    private CameraEnumerator cameraEnumerator;
    private String roomToken;
    private User conversationUser;
    private String conversationName;
    private String callSession;
    private MediaStream localStream;
    private String credentials;
    private List<PeerConnectionWrapper> peerConnectionWrapperList = new ArrayList<>();
    private Map<String, Participant> participantMap = new HashMap<>();

    private boolean videoOn = false;
    private boolean microphoneOn = false;

    private boolean isVoiceOnlyCall;
    private boolean isCallWithoutNotification;
    private boolean isIncomingCallFromNotification;
    private Handler callControlHandler = new Handler();
    private Handler callInfosHandler = new Handler();
    private Handler cameraSwitchHandler = new Handler();

    // push to talk
    private boolean isPushToTalkActive = false;
    private PulseAnimation pulseAnimation;

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

    private MediaPlayer mediaPlayer;

    private Map<String, ParticipantDisplayItem> participantDisplayItems;
    private ParticipantsAdapter participantsAdapter;

    private CallActivityBinding binding;

    private AudioOutputDialog audioOutputDialog;

    private final ActivityResultLauncher<String> requestBluetoothPermissionLauncher =
        registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                enableBluetoothManager();
            }
        });

    private boolean canPublishAudioStream;
    private boolean canPublishVideoStream;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);

        NextcloudTalkApplication.Companion.getSharedApplication().getComponentApplication().inject(this);

        binding = CallActivityBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        hideNavigationIfNoPipAvailable();

        Bundle extras = getIntent().getExtras();
        roomId = extras.getString(KEY_ROOM_ID, "");
        roomToken = extras.getString(KEY_ROOM_TOKEN, "");
        conversationUser = extras.getParcelable(KEY_USER_ENTITY);
        conversationPassword = extras.getString(KEY_CONVERSATION_PASSWORD, "");
        conversationName = extras.getString(KEY_CONVERSATION_NAME, "");
        isVoiceOnlyCall = extras.getBoolean(KEY_CALL_VOICE_ONLY, false);
        isCallWithoutNotification = extras.getBoolean(KEY_CALL_WITHOUT_NOTIFICATION, false);
        canPublishAudioStream = extras.getBoolean(KEY_PARTICIPANT_PERMISSION_CAN_PUBLISH_AUDIO);
        canPublishVideoStream = extras.getBoolean(KEY_PARTICIPANT_PERMISSION_CAN_PUBLISH_VIDEO);

        if (extras.containsKey(KEY_FROM_NOTIFICATION_START_CALL)) {
            isIncomingCallFromNotification = extras.getBoolean(KEY_FROM_NOTIFICATION_START_CALL);
        }

        credentials = ApiUtils.getCredentials(conversationUser.getUsername(), conversationUser.getToken());

        baseUrl = extras.getString(KEY_MODIFIED_BASE_URL, "");
        if (TextUtils.isEmpty(baseUrl)) {
            baseUrl = conversationUser.getBaseUrl();
        }

        powerManagerUtils = new PowerManagerUtils();

        if (extras.getString("state", "").equalsIgnoreCase("resume")) {
            setCallState(CallStatus.IN_CONVERSATION);
        } else {
            setCallState(CallStatus.CONNECTING);
        }

        initClickListeners();
        binding.microphoneButton.setOnTouchListener(new MicrophoneButtonTouchListener());

        pulseAnimation = PulseAnimation.create().with(binding.microphoneButton)
            .setDuration(310)
            .setRepeatCount(PulseAnimation.INFINITE)
            .setRepeatMode(PulseAnimation.REVERSE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestBluetoothPermission();
        }
        basicInitialization();
        participantDisplayItems = new HashMap<>();
        initViews();
        if (!isConnectionEstablished()) {
            initiateCall();
        }
        updateSelfVideoViewPosition();
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    private void requestBluetoothPermission() {
        if (ContextCompat.checkSelfPermission(
            getContext(), Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_DENIED) {
            requestBluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT);
        }
    }

    private void enableBluetoothManager() {
        if (audioManager != null) {
            audioManager.startBluetoothManager();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        try {
            cache.evictAll();
        } catch (IOException e) {
            Log.e(TAG, "Failed to evict cache");
        }
    }

    private void initClickListeners() {
        binding.pictureInPictureButton.setOnClickListener(l -> enterPipMode());

        binding.audioOutputButton.setOnClickListener(v -> {
            audioOutputDialog = new AudioOutputDialog(this);
            audioOutputDialog.show();
        });

        if (canPublishAudioStream) {
            binding.microphoneButton.setOnClickListener(l -> onMicrophoneClick());
            binding.microphoneButton.setOnLongClickListener(l -> {
                if (!microphoneOn) {
                    callControlHandler.removeCallbacksAndMessages(null);
                    callInfosHandler.removeCallbacksAndMessages(null);
                    cameraSwitchHandler.removeCallbacksAndMessages(null);
                    isPushToTalkActive = true;
                    binding.callControls.setVisibility(View.VISIBLE);
                    if (!isVoiceOnlyCall) {
                        binding.switchSelfVideoButton.setVisibility(View.VISIBLE);
                    }
                }
                onMicrophoneClick();
                return true;
            });
        } else {
            binding.microphoneButton.setOnClickListener(
                l -> Toast.makeText(context,
                                    R.string.nc_not_allowed_to_activate_audio,
                                    Toast.LENGTH_SHORT
                                   ).show()
                                                       );
        }

        if (canPublishVideoStream) {
            binding.cameraButton.setOnClickListener(l -> onCameraClick());
        } else {
            binding.cameraButton.setOnClickListener(
                l -> Toast.makeText(context,
                                    R.string.nc_not_allowed_to_activate_video,
                                    Toast.LENGTH_SHORT
                                   ).show()
                                                   );
        }

        binding.hangupButton.setOnClickListener(l -> {
            hangup(true);
        });

        binding.switchSelfVideoButton.setOnClickListener(l -> switchCamera());

        binding.gridview.setOnItemClickListener((parent, view, position, id) -> animateCallControls(true, 0));

        binding.callStates.callStateRelativeLayout.setOnClickListener(l -> {
            if (currentCallStatus == CallStatus.CALLING_TIMEOUT) {
                setCallState(CallStatus.RECONNECTING);
                hangupNetworkCalls(false);
            }
        });
    }

    private void createCameraEnumerator() {
        boolean camera2EnumeratorIsSupported = false;
        try {
            camera2EnumeratorIsSupported = Camera2Enumerator.isSupported(this);
        } catch (final Throwable t) {
            Log.w(TAG, "Camera2Enumerator threw an error", t);
        }

        if (camera2EnumeratorIsSupported) {
            cameraEnumerator = new Camera2Enumerator(this);
        } else {
            cameraEnumerator = new Camera1Enumerator(MagicWebRTCUtils.shouldEnableVideoHardwareAcceleration());
        }
    }

    private void basicInitialization() {
        rootEglBase = EglBase.create();
        createCameraEnumerator();

        //Create a new PeerConnectionFactory instance.
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        DefaultVideoEncoderFactory defaultVideoEncoderFactory = new DefaultVideoEncoderFactory(
            rootEglBase.getEglBaseContext(), true, true);
        DefaultVideoDecoderFactory defaultVideoDecoderFactory = new DefaultVideoDecoderFactory(
            rootEglBase.getEglBaseContext());

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setVideoEncoderFactory(defaultVideoEncoderFactory)
            .setVideoDecoderFactory(defaultVideoDecoderFactory)
            .createPeerConnectionFactory();

        //Create MediaConstraints - Will be useful for specifying video and audio constraints.
        audioConstraints = new MediaConstraints();
        videoConstraints = new MediaConstraints();

        localStream = peerConnectionFactory.createLocalMediaStream("NCMS");

        // Create and audio manager that will take care of audio routing,
        // audio modes, audio device enumeration etc.
        audioManager = WebRtcAudioManager.create(getApplicationContext(), isVoiceOnlyCall);
        // Store existing audio settings and change audio mode to
        // MODE_IN_COMMUNICATION for best possible VoIP performance.
        Log.d(TAG, "Starting the audio manager...");
        audioManager.start(this::onAudioManagerDevicesChanged);

        if (isVoiceOnlyCall) {
            setAudioOutputChannel(WebRtcAudioManager.AudioDevice.EARPIECE);
        } else {
            setAudioOutputChannel(WebRtcAudioManager.AudioDevice.SPEAKER_PHONE);
        }

        iceServers = new ArrayList<>();

        //create sdpConstraints
        sdpConstraints = new MediaConstraints();
        sdpConstraintsForMCU = new MediaConstraints();
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        String offerToReceiveVideoString = "true";

        if (isVoiceOnlyCall) {
            offerToReceiveVideoString = "false";
        }

        sdpConstraints.mandatory.add(
            new MediaConstraints.KeyValuePair("OfferToReceiveVideo", offerToReceiveVideoString));

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

    public void setAudioOutputChannel(WebRtcAudioManager.AudioDevice selectedAudioDevice) {
        if (audioManager != null) {
            audioManager.selectAudioDevice(selectedAudioDevice);
            updateAudioOutputButton(audioManager.getCurrentAudioDevice());
        }
    }

    private void updateAudioOutputButton(WebRtcAudioManager.AudioDevice activeAudioDevice) {
        switch (activeAudioDevice) {
            case BLUETOOTH:
                binding.audioOutputButton.setImageResource ( R.drawable.ic_baseline_bluetooth_audio_24);
                break;
            case SPEAKER_PHONE:
                binding.audioOutputButton.setImageResource(R.drawable.ic_volume_up_white_24dp);
                break;
            case EARPIECE:
                binding.audioOutputButton.setImageResource(R.drawable.ic_baseline_phone_in_talk_24);
                break;
            case WIRED_HEADSET:
                binding.audioOutputButton.setImageResource(R.drawable.ic_baseline_headset_mic_24);
                break;
            default:
                Log.e(TAG, "Icon for audio output not available");
                break;
        }
        DrawableCompat.setTint(binding.audioOutputButton.getDrawable(), Color.WHITE);
    }

    private void handleFromNotification() {
        int apiVersion = ApiUtils.getConversationApiVersion(conversationUser, new int[]{ApiUtils.APIv4, 1});

        ncApi.getRooms(credentials, ApiUtils.getUrlForRooms(apiVersion, baseUrl), false)
            .retry(3)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(new Observer<RoomsOverall>() {
                @Override
                public void onSubscribe(@io.reactivex.annotations.NonNull Disposable d) {
                    // unused atm
                }

                @Override
                public void onNext(@io.reactivex.annotations.NonNull RoomsOverall roomsOverall) {
                    for (Conversation conversation : roomsOverall.getOcs().getData()) {
                        if (roomId.equals(conversation.getRoomId())) {
                            roomToken = conversation.getToken();
                            break;
                        }
                    }

                    checkDevicePermissions();
                }

                @Override
                public void onError(@io.reactivex.annotations.NonNull Throwable e) {
                    // unused atm
                }

                @Override
                public void onComplete() {
                    // unused atm
                }
            });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initViews() {
        Log.d(TAG, "initViews");
        binding.callInfosLinearLayout.setVisibility(View.VISIBLE);
        binding.selfVideoViewWrapper.setVisibility(View.VISIBLE);

        if (!isPipModePossible()) {
            binding.pictureInPictureButton.setVisibility(View.GONE);
        }

        if (isVoiceOnlyCall) {
            binding.switchSelfVideoButton.setVisibility(View.GONE);
            binding.cameraButton.setVisibility(View.GONE);
            binding.selfVideoRenderer.setVisibility(View.GONE);

            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                                                                 ViewGroup.LayoutParams.WRAP_CONTENT);
            params.addRule(RelativeLayout.BELOW, R.id.callInfosLinearLayout);
            int callControlsHeight = Math.round(getApplicationContext().getResources().getDimension(R.dimen.call_controls_height));
            params.setMargins(0, 0, 0, callControlsHeight);
            binding.gridview.setLayoutParams(params);
        } else {
            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                                                                 ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, 0, 0);
            binding.gridview.setLayoutParams(params);

            if (cameraEnumerator.getDeviceNames().length < 2) {
                binding.switchSelfVideoButton.setVisibility(View.GONE);
            }
            initSelfVideoView();
        }

        binding.gridview.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent me) {
                int action = me.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    animateCallControls(true, 0);
                }
                return false;
            }
        });

        binding.conversationRelativeLayout.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent me) {
                int action = me.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    animateCallControls(true, 0);
                }
                return false;
            }
        });

        animateCallControls(true, 0);

        initGridAdapter();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initSelfVideoView() {
        try {
            binding.selfVideoRenderer.init(rootEglBase.getEglBaseContext(), null);
        } catch (IllegalStateException e) {
            Log.d(TAG, "selfVideoRenderer already initialized", e);
        }

        binding.selfVideoRenderer.setZOrderMediaOverlay(true);
        // disabled because it causes some devices to crash
        binding.selfVideoRenderer.setEnableHardwareScaler(false);
        binding.selfVideoRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        binding.selfVideoRenderer.setOnTouchListener(new SelfVideoTouchListener());
    }

    private void initGridAdapter() {
        Log.d(TAG, "initGridAdapter");
        int columns;
        int participantsInGrid = participantDisplayItems.size();
        if (getResources() != null
            && getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            if (participantsInGrid > 2) {
                columns = 2;
            } else {
                columns = 1;
            }
        } else {
            if (participantsInGrid > 2) {
                columns = 3;
            } else if (participantsInGrid > 1) {
                columns = 2;
            } else {
                columns = 1;
            }
        }

        binding.gridview.setNumColumns(columns);

        binding.conversationRelativeLayout
            .getViewTreeObserver()
            .addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    binding.conversationRelativeLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    int height = binding.conversationRelativeLayout.getMeasuredHeight();
                    binding.gridview.setMinimumHeight(height);
                }
            });

        binding
            .callInfosLinearLayout
            .getViewTreeObserver()
            .addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    binding.callInfosLinearLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                }
            });

        participantsAdapter = new ParticipantsAdapter(
            this,
            participantDisplayItems,
            binding.conversationRelativeLayout,
            binding.callInfosLinearLayout,
            columns,
            isVoiceOnlyCall);
        binding.gridview.setAdapter(participantsAdapter);

        if (isInPipMode) {
            updateUiForPipMode();
        }
    }


    private void checkDevicePermissions() {
        if (isVoiceOnlyCall) {
            onMicrophoneClick();
        } else {
            if (EffortlessPermissions.hasPermissions(this, PERMISSIONS_CALL)) {
                onPermissionsGranted();
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(PERMISSIONS_CALL, 100);
            } else {
                onRequestPermissionsResult(100, PERMISSIONS_CALL, new int[]{1, 1});
            }
        }

    }

    private boolean isConnectionEstablished() {
        return (currentCallStatus == CallStatus.JOINED || currentCallStatus == CallStatus.IN_CONVERSATION);
    }

    @AfterPermissionGranted(100)
    private void onPermissionsGranted() {
        if (EffortlessPermissions.hasPermissions(this, PERMISSIONS_CALL)) {
            if (!videoOn && !isVoiceOnlyCall) {
                onCameraClick();
            }

            if (!microphoneOn) {
                onMicrophoneClick();
            }

            if (!isVoiceOnlyCall) {
                if (cameraEnumerator.getDeviceNames().length == 0) {
                    binding.cameraButton.setVisibility(View.GONE);
                }

                if (cameraEnumerator.getDeviceNames().length > 1) {
                    binding.switchSelfVideoButton.setVisibility(View.VISIBLE);
                }
            }

            if (!isConnectionEstablished()) {
                fetchSignalingSettings();
            }
        } else if (EffortlessPermissions.somePermissionPermanentlyDenied(this, PERMISSIONS_CALL)) {
            checkIfSomeAreApproved();
        }

    }

    private void checkIfSomeAreApproved() {
        if (!isVoiceOnlyCall) {
            if (cameraEnumerator.getDeviceNames().length == 0) {
                binding.cameraButton.setVisibility(View.GONE);
            }

            if (cameraEnumerator.getDeviceNames().length > 1) {
                binding.switchSelfVideoButton.setVisibility(View.VISIBLE);
            }

            if (EffortlessPermissions.hasPermissions(this, PERMISSIONS_CAMERA) && canPublishVideoStream) {
                if (!videoOn) {
                    onCameraClick();
                }
            } else {
                binding.cameraButton.setImageResource(R.drawable.ic_videocam_off_white_24px);
                binding.cameraButton.setAlpha(0.7f);
                binding.switchSelfVideoButton.setVisibility(View.GONE);
            }
        }

        if (EffortlessPermissions.hasPermissions(this, PERMISSIONS_MICROPHONE) && canPublishAudioStream) {
            if (!microphoneOn) {
                onMicrophoneClick();
            }
        } else {
            binding.microphoneButton.setImageResource(R.drawable.ic_mic_off_white_24px);
        }

        if (!isConnectionEstablished()) {
            fetchSignalingSettings();
        }
    }

    @AfterPermissionDenied(100)
    private void onPermissionsDenied() {
        if (!isVoiceOnlyCall) {
            if (cameraEnumerator.getDeviceNames().length == 0) {
                binding.cameraButton.setVisibility(View.GONE);
            } else if (cameraEnumerator.getDeviceNames().length == 1) {
                binding.switchSelfVideoButton.setVisibility(View.GONE);
            }
        }

        if ((EffortlessPermissions.hasPermissions(this, PERMISSIONS_CAMERA) ||
            EffortlessPermissions.hasPermissions(this, PERMISSIONS_MICROPHONE))) {
            checkIfSomeAreApproved();
        } else if (!isConnectionEstablished()) {
            fetchSignalingSettings();
        }
    }

    private void onAudioManagerDevicesChanged(
        final WebRtcAudioManager.AudioDevice currentDevice,
        final Set<WebRtcAudioManager.AudioDevice> availableDevices) {
        Log.d(TAG, "onAudioManagerDevicesChanged: " + availableDevices + ", "
            + "currentDevice: " + currentDevice);

        final boolean shouldDisableProximityLock = (currentDevice == WebRtcAudioManager.AudioDevice.WIRED_HEADSET
            || currentDevice == WebRtcAudioManager.AudioDevice.SPEAKER_PHONE
            || currentDevice == WebRtcAudioManager.AudioDevice.BLUETOOTH);

        if (shouldDisableProximityLock) {
            powerManagerUtils.updatePhoneState(PowerManagerUtils.PhoneState.WITHOUT_PROXIMITY_SENSOR_LOCK);
        } else {
            powerManagerUtils.updatePhoneState(PowerManagerUtils.PhoneState.WITH_PROXIMITY_SENSOR_LOCK);
        }

        if (audioOutputDialog != null) {
            audioOutputDialog.updateOutputDeviceList();
        }
        updateAudioOutputButton(currentDevice);
    }


    private void cameraInitialization() {
        videoCapturer = createCameraCapturer(cameraEnumerator);

        //Create a VideoSource instance
        if (videoCapturer != null) {
            SurfaceTextureHelper surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread",
                                                                                    rootEglBase.getEglBaseContext());
            videoSource = peerConnectionFactory.createVideoSource(false);
            videoCapturer.initialize(surfaceTextureHelper, getApplicationContext(), videoSource.getCapturerObserver());
        }
        localVideoTrack = peerConnectionFactory.createVideoTrack("NCv0", videoSource);
        localStream.addTrack(localVideoTrack);
        localVideoTrack.setEnabled(false);
        localVideoTrack.addSink(binding.selfVideoRenderer);
    }

    private void microphoneInitialization() {
        //create an AudioSource instance
        audioSource = peerConnectionFactory.createAudioSource(audioConstraints);
        localAudioTrack = peerConnectionFactory.createAudioTrack("NCa0", audioSource);
        localAudioTrack.setEnabled(false);
        localStream.addTrack(localAudioTrack);
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
                    binding.selfVideoRenderer.setMirror(true);
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
                    binding.selfVideoRenderer.setMirror(false);
                    return videoCapturer;
                }
            }
        }

        return null;
    }

    public void onMicrophoneClick() {

        if (!canPublishAudioStream) {
            microphoneOn = false;
            binding.microphoneButton.setImageResource(R.drawable.ic_mic_off_white_24px);
            toggleMedia(false, false);
        }

        if (isVoiceOnlyCall && !isConnectionEstablished()) {
            fetchSignalingSettings();
        }

        if (!canPublishAudioStream) {
            // In the case no audio stream will be published it's not needed to check microphone permissions
            return;
        }

        if (EffortlessPermissions.hasPermissions(this, PERMISSIONS_MICROPHONE)) {

            if (!appPreferences.getPushToTalkIntroShown()) {
                int primary = viewThemeUtils.getScheme(binding.audioOutputButton.getContext()).getPrimary();
                spotlightView = new SpotlightView.Builder(this)
                    .introAnimationDuration(300)
                    .enableRevealAnimation(true)
                    .performClick(false)
                    .fadeinTextDuration(400)
                    .headingTvColor(primary)
                    .headingTvSize(20)
                    .headingTvText(getResources().getString(R.string.nc_push_to_talk))
                    .subHeadingTvColor(getResources().getColor(R.color.bg_default))
                    .subHeadingTvSize(16)
                    .subHeadingTvText(getResources().getString(R.string.nc_push_to_talk_desc))
                    .maskColor(Color.parseColor("#dc000000"))
                    .target(binding.microphoneButton)
                    .lineAnimDuration(400)
                    .lineAndArcColor(primary)
                    .enableDismissAfterShown(true)
                    .dismissOnBackPress(true)
                    .usageId("pushToTalk")
                    .show();

                appPreferences.setPushToTalkIntroShown(true);
            }

            if (!isPushToTalkActive) {
                microphoneOn = !microphoneOn;

                if (microphoneOn) {
                    binding.microphoneButton.setImageResource(R.drawable.ic_mic_white_24px);
                    updatePictureInPictureActions(R.drawable.ic_mic_white_24px,
                                                  getResources().getString(R.string.nc_pip_microphone_mute),
                                                  MICROPHONE_PIP_REQUEST_MUTE);
                } else {
                    binding.microphoneButton.setImageResource(R.drawable.ic_mic_off_white_24px);
                    updatePictureInPictureActions(R.drawable.ic_mic_off_white_24px,
                                                  getResources().getString(R.string.nc_pip_microphone_unmute),
                                                  MICROPHONE_PIP_REQUEST_UNMUTE);
                }

                toggleMedia(microphoneOn, false);
            } else {
                binding.microphoneButton.setImageResource(R.drawable.ic_mic_white_24px);
                pulseAnimation.start();
                toggleMedia(true, false);
            }
        } else if (EffortlessPermissions.somePermissionPermanentlyDenied(this, PERMISSIONS_MICROPHONE)) {
            // Microphone permission is permanently denied so we cannot request it normally.

            OpenAppDetailsDialogFragment.show(
                R.string.nc_microphone_permission_permanently_denied,
                R.string.nc_permissions_settings, (AppCompatActivity) this);
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(PERMISSIONS_MICROPHONE, 100);
            } else {
                onRequestPermissionsResult(100, PERMISSIONS_MICROPHONE, new int[]{1});
            }
        }
    }

    public void onCameraClick() {

        if (!canPublishVideoStream) {
            videoOn = false;
            binding.cameraButton.setImageResource(R.drawable.ic_videocam_off_white_24px);
            binding.switchSelfVideoButton.setVisibility(View.GONE);
            return;
        }

        if (EffortlessPermissions.hasPermissions(this, PERMISSIONS_CAMERA)) {
            videoOn = !videoOn;

            if (videoOn) {
                binding.cameraButton.setImageResource(R.drawable.ic_videocam_white_24px);
                if (cameraEnumerator.getDeviceNames().length > 1) {
                    binding.switchSelfVideoButton.setVisibility(View.VISIBLE);
                }
            } else {
                binding.cameraButton.setImageResource(R.drawable.ic_videocam_off_white_24px);
                binding.switchSelfVideoButton.setVisibility(View.GONE);
            }

            toggleMedia(videoOn, true);
        } else if (EffortlessPermissions.somePermissionPermanentlyDenied(this, PERMISSIONS_CAMERA)) {
            // Camera permission is permanently denied so we cannot request it normally.
            OpenAppDetailsDialogFragment.show(
                R.string.nc_camera_permission_permanently_denied,
                R.string.nc_permissions_settings, (AppCompatActivity) this);
        } else {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(PERMISSIONS_CAMERA, 100);
            } else {
                onRequestPermissionsResult(100, PERMISSIONS_CAMERA, new int[]{1});
            }
        }

    }

    public void switchCamera() {
        CameraVideoCapturer cameraVideoCapturer = (CameraVideoCapturer) videoCapturer;
        if (cameraVideoCapturer != null) {
            cameraVideoCapturer.switchCamera(new CameraVideoCapturer.CameraSwitchHandler() {
                @Override
                public void onCameraSwitchDone(boolean currentCameraIsFront) {
                    binding.selfVideoRenderer.setMirror(currentCameraIsFront);
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
                binding.cameraButton.setAlpha(1.0f);
                message = "videoOn";
                startVideoCapture();
            } else {
                binding.cameraButton.setAlpha(0.7f);
                if (videoCapturer != null) {
                    try {
                        videoCapturer.stopCapture();
                    } catch (InterruptedException e) {
                        Log.d(TAG, "Failed to stop capturing video while sensor is near the ear");
                    }
                }
            }

            if (localStream != null && localStream.videoTracks.size() > 0) {
                localStream.videoTracks.get(0).setEnabled(enable);
            }
            if (enable) {
                binding.selfVideoRenderer.setVisibility(View.VISIBLE);
            } else {
                binding.selfVideoRenderer.setVisibility(View.INVISIBLE);
            }
        } else {
            message = "audioOff";
            if (enable) {
                message = "audioOn";
                binding.microphoneButton.setAlpha(1.0f);
            } else {
                binding.microphoneButton.setAlpha(0.7f);
            }

            if (localStream != null && localStream.audioTracks.size() > 0) {
                localStream.audioTracks.get(0).setEnabled(enable);
            }
        }

        if (isConnectionEstablished() && peerConnectionWrapperList != null) {
            if (!hasMCU) {
                for (PeerConnectionWrapper peerConnectionWrapper : peerConnectionWrapperList) {
                    peerConnectionWrapper.sendChannelData(new DataChannelMessage(message));
                }
            } else {
                for (PeerConnectionWrapper peerConnectionWrapper : peerConnectionWrapperList) {
                    if (peerConnectionWrapper.getSessionId().equals(webSocketClient.getSessionId())) {
                        peerConnectionWrapper.sendChannelData(new DataChannelMessage(message));
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
        } else if (!isPushToTalkActive) {
            float alpha;
            long duration;

            if (show) {
                callControlHandler.removeCallbacksAndMessages(null);
                callInfosHandler.removeCallbacksAndMessages(null);
                cameraSwitchHandler.removeCallbacksAndMessages(null);
                alpha = 1.0f;
                duration = 1000;
                if (binding.callControls.getVisibility() != View.VISIBLE) {
                    binding.callControls.setAlpha(0.0f);
                    binding.callControls.setVisibility(View.VISIBLE);

                    binding.callInfosLinearLayout.setAlpha(0.0f);
                    binding.callInfosLinearLayout.setVisibility(View.VISIBLE);

                    binding.switchSelfVideoButton.setAlpha(0.0f);
                    if (videoOn) {
                        binding.switchSelfVideoButton.setVisibility(View.VISIBLE);
                    }
                } else {
                    callControlHandler.postDelayed(() -> animateCallControls(false, 0), 5000);
                    return;
                }
            } else {
                alpha = 0.0f;
                duration = 1000;
            }

            binding.callControls.setEnabled(false);
            binding.callControls.animate()
                .translationY(0)
                .alpha(alpha)
                .setDuration(duration)
                .setStartDelay(startDelay)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        if (!show) {
                            binding.callControls.setVisibility(View.GONE);
                            if (spotlightView != null && spotlightView.getVisibility() != View.GONE) {
                                spotlightView.setVisibility(View.GONE);
                            }
                        } else {
                            callControlHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    if (!isPushToTalkActive) {
                                        animateCallControls(false, 0);
                                    }
                                }
                            }, 7500);
                        }

                        binding.callControls.setEnabled(true);
                    }
                });

            binding.callInfosLinearLayout.setEnabled(false);
            binding.callInfosLinearLayout.animate()
                .translationY(0)
                .alpha(alpha)
                .setDuration(duration)
                .setStartDelay(startDelay)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        if (!show) {
                            binding.callInfosLinearLayout.setVisibility(View.GONE);
                        } else {
                            callInfosHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    if (!isPushToTalkActive) {
                                        animateCallControls(false, 0);
                                    }
                                }
                            }, 7500);
                        }

                        binding.callInfosLinearLayout.setEnabled(true);
                    }
                });

            binding.switchSelfVideoButton.setEnabled(false);
            binding.switchSelfVideoButton.animate()
                .translationY(0)
                .alpha(alpha)
                .setDuration(duration)
                .setStartDelay(startDelay)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        if (!show) {
                            binding.switchSelfVideoButton.setVisibility(View.GONE);
                        }

                        binding.switchSelfVideoButton.setEnabled(true);
                    }
                });

        }
    }

    @Override
    public void onDestroy() {
        if (localStream != null) {
            localStream.dispose();
            localStream = null;
            Log.d(TAG, "Disposed localStream");
        } else {
            Log.d(TAG, "localStream is null");
        }

        if (currentCallStatus != CallStatus.LEAVING) {
            hangup(true);
        }
        powerManagerUtils.updatePhoneState(PowerManagerUtils.PhoneState.IDLE);
        super.onDestroy();
    }

    private void fetchSignalingSettings() {
        Log.d(TAG, "fetchSignalingSettings");
        int apiVersion = ApiUtils.getSignalingApiVersion(conversationUser, new int[]{ApiUtils.APIv3, 2, 1});

        ncApi.getSignalingSettings(credentials, ApiUtils.getUrlForSignalingSettings(apiVersion, baseUrl))
            .subscribeOn(Schedulers.io())
            .retry(3)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(new Observer<SignalingSettingsOverall>() {
                @Override
                public void onSubscribe(@io.reactivex.annotations.NonNull Disposable d) {
                    // unused atm
                }

                @Override
                public void onNext(@io.reactivex.annotations.NonNull SignalingSettingsOverall signalingSettingsOverall) {
                    if (signalingSettingsOverall.getOcs() != null
                        && signalingSettingsOverall.getOcs().getSettings() != null) {
                        externalSignalingServer = new ExternalSignalingServer();

                        if (!TextUtils.isEmpty(
                            signalingSettingsOverall.getOcs().getSettings().getExternalSignalingServer()) &&
                            !TextUtils.isEmpty(
                                signalingSettingsOverall.getOcs().getSettings().getExternalSignalingTicket())) {
                            externalSignalingServer = new ExternalSignalingServer();
                            externalSignalingServer.setExternalSignalingServer(
                                signalingSettingsOverall.getOcs().getSettings().getExternalSignalingServer());
                            externalSignalingServer.setExternalSignalingTicket(
                                signalingSettingsOverall.getOcs().getSettings().getExternalSignalingTicket());
                            hasExternalSignalingServer = true;
                        } else {
                            hasExternalSignalingServer = false;
                        }
                        Log.d(TAG, "   hasExternalSignalingServer: " + hasExternalSignalingServer);

                        if (!"?".equals(conversationUser.getUserId()) && conversationUser.getId() != null) {
                            Log.d(TAG, "Update externalSignalingServer for: " + conversationUser.getId() +
                                " / " + conversationUser.getUserId());
                            userManager.updateExternalSignalingServer(conversationUser.getId(), externalSignalingServer)
                                .subscribeOn(Schedulers.io())
                                .subscribe();
                        } else {
                            conversationUser.setExternalSignalingServer(externalSignalingServer);
                        }

                        if (signalingSettingsOverall.getOcs().getSettings().getStunServers() != null) {
                            List<IceServer> stunServers =
                                signalingSettingsOverall.getOcs().getSettings().getStunServers();
                            if (apiVersion == ApiUtils.APIv3) {
                                for (IceServer stunServer : stunServers) {
                                    if (stunServer.getUrls() != null) {
                                        for (String url : stunServer.getUrls()) {
                                            Log.d(TAG, "   STUN server url: " + url);
                                            iceServers.add(new PeerConnection.IceServer(url));
                                        }
                                    }
                                }
                            } else {
                                if (signalingSettingsOverall.getOcs().getSettings().getStunServers() != null) {
                                    for (IceServer stunServer : stunServers) {
                                        Log.d(TAG, "   STUN server url: " + stunServer.getUrl());
                                        iceServers.add(new PeerConnection.IceServer(stunServer.getUrl()));
                                    }
                                }
                            }
                        }

                        if (signalingSettingsOverall.getOcs().getSettings().getTurnServers() != null) {
                            List<IceServer> turnServers =
                                signalingSettingsOverall.getOcs().getSettings().getTurnServers();
                            for (IceServer turnServer : turnServers) {
                                if (turnServer.getUrls() != null) {
                                    for (String url : turnServer.getUrls()) {
                                        Log.d(TAG, "   TURN server url: " + url);
                                        iceServers.add(new PeerConnection.IceServer(
                                            url, turnServer.getUsername(), turnServer.getCredential()
                                        ));
                                    }
                                }
                            }
                        }
                    }

                    checkCapabilities();
                }

                @Override
                public void onError(@io.reactivex.annotations.NonNull Throwable e) {
                    Log.e(TAG, e.getMessage(), e);
                }

                @Override
                public void onComplete() {
                    // unused atm
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
                public void onSubscribe(@io.reactivex.annotations.NonNull Disposable d) {
                    // unused atm
                }

                @Override
                public void onNext(@io.reactivex.annotations.NonNull CapabilitiesOverall capabilitiesOverall) {
                    // FIXME check for compatible Call API version
                    if (hasExternalSignalingServer) {
                        setupAndInitiateWebSocketsConnection();
                    } else {
                        joinRoomAndCall();
                    }
                }

                @Override
                public void onError(@io.reactivex.annotations.NonNull Throwable e) {
                    // unused atm
                }

                @Override
                public void onComplete() {
                    // unused atm
                }
            });
    }

    private void joinRoomAndCall() {
        callSession = ApplicationWideCurrentRoomHolder.getInstance().getSession();

        int apiVersion = ApiUtils.getConversationApiVersion(conversationUser, new int[]{ApiUtils.APIv4, 1});

        Log.d(TAG, "joinRoomAndCall");
        Log.d(TAG, "   baseUrl= " + baseUrl);
        Log.d(TAG, "   roomToken= " + roomToken);
        Log.d(TAG, "   callSession= " + callSession);

        String url = ApiUtils.getUrlForParticipantsActive(apiVersion, baseUrl, roomToken);
        Log.d(TAG, "   url= " + url);

        if (TextUtils.isEmpty(callSession)) {
            ncApi.joinRoom(credentials, url, conversationPassword)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .retry(3)
                .subscribe(new Observer<RoomOverall>() {
                    @Override
                    public void onSubscribe(@io.reactivex.annotations.NonNull Disposable d) {
                        // unused atm
                    }

                    @Override
                    public void onNext(@io.reactivex.annotations.NonNull RoomOverall roomOverall) {
                        callSession = roomOverall.getOcs().getData().getSessionId();
                        Log.d(TAG, " new callSession by joinRoom= " + callSession);

                        ApplicationWideCurrentRoomHolder.getInstance().setSession(callSession);
                        ApplicationWideCurrentRoomHolder.getInstance().setCurrentRoomId(roomId);
                        ApplicationWideCurrentRoomHolder.getInstance().setCurrentRoomToken(roomToken);
                        ApplicationWideCurrentRoomHolder.getInstance().setUserInRoom(conversationUser);
                        callOrJoinRoomViaWebSocket();
                    }

                    @Override
                    public void onError(@io.reactivex.annotations.NonNull Throwable e) {
                        Log.e(TAG, "joinRoom onError", e);
                    }

                    @Override
                    public void onComplete() {
                        Log.d(TAG, "joinRoom onComplete");
                    }
                });
        } else {
            // we are in a room and start a call -> same session needs to be used
            callOrJoinRoomViaWebSocket();
        }
    }

    private void callOrJoinRoomViaWebSocket() {
        if (hasExternalSignalingServer) {
            webSocketClient.joinRoomWithRoomTokenAndSession(roomToken, callSession);
        } else {
            performCall();
        }
    }

    private void performCall() {
        int inCallFlag = Participant.InCallFlags.IN_CALL;

        if (canPublishAudioStream) {
            inCallFlag += Participant.InCallFlags.WITH_AUDIO;
        }

        if (!isVoiceOnlyCall && canPublishVideoStream) {
            inCallFlag += Participant.InCallFlags.WITH_VIDEO;
        }

        int apiVersion = ApiUtils.getCallApiVersion(conversationUser, new int[]{ApiUtils.APIv4, 1});

        ncApi.joinCall(
                credentials,
                ApiUtils.getUrlForCall(apiVersion, baseUrl, roomToken),
                inCallFlag,
                isCallWithoutNotification)
            .subscribeOn(Schedulers.io())
            .retry(3)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(new Observer<GenericOverall>() {
                @Override
                public void onSubscribe(@io.reactivex.annotations.NonNull Disposable d) {
                    // unused atm
                }

                @Override
                public void onNext(@io.reactivex.annotations.NonNull GenericOverall genericOverall) {
                    if (currentCallStatus != CallStatus.LEAVING) {
                        setCallState(CallStatus.JOINED);

                        ApplicationWideCurrentRoomHolder.getInstance().setInCall(true);
                        ApplicationWideCurrentRoomHolder.getInstance().setDialing(false);

                        if (!TextUtils.isEmpty(roomToken)) {
                            NotificationUtils.INSTANCE.cancelExistingNotificationsForRoom(getApplicationContext(),
                                                                                          conversationUser,
                                                                                          roomToken);
                        }

                        if (!hasExternalSignalingServer) {
                            int apiVersion = ApiUtils.getSignalingApiVersion(conversationUser,
                                                                             new int[]{ApiUtils.APIv3, 2, 1});

                            AtomicInteger delayOnError = new AtomicInteger(0);

                            ncApi.pullSignalingMessages(credentials,
                                                        ApiUtils.getUrlForSignaling(apiVersion,
                                                                                    baseUrl,
                                                                                    roomToken))
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .repeatWhen(observable -> observable)
                                .takeWhile(observable -> isConnectionEstablished())
                                .doOnNext(value -> delayOnError.set(0))
                                .retryWhen(errors -> errors
                                    .flatMap(error -> {
                                        if (!isConnectionEstablished()) {
                                            return Observable.error(error);
                                        }

                                        if (delayOnError.get() == 0) {
                                            delayOnError.set(1);
                                        } else if (delayOnError.get() < 16) {
                                            delayOnError.set(delayOnError.get() * 2);
                                        }

                                        return Observable.timer(delayOnError.get(), TimeUnit.SECONDS);
                                    })
                                )
                                .subscribe(new Observer<SignalingOverall>() {
                                    @Override
                                    public void onSubscribe(@io.reactivex.annotations.NonNull Disposable d) {
                                        signalingDisposable = d;
                                    }

                                    @Override
                                    public void onNext(
                                        @io.reactivex.annotations.NonNull
                                            SignalingOverall signalingOverall) {
                                        receivedSignalingMessages(signalingOverall.getOcs().getSignalings());
                                    }

                                    @Override
                                    public void onError(@io.reactivex.annotations.NonNull Throwable e) {
                                        dispose(signalingDisposable);
                                    }

                                    @Override
                                    public void onComplete() {
                                        dispose(signalingDisposable);
                                    }
                                });
                        }
                    }
                }

                @Override
                public void onError(@io.reactivex.annotations.NonNull Throwable e) {
                    // unused atm
                }

                @Override
                public void onComplete() {
                    // unused atm
                }
            });
    }

    private void setupAndInitiateWebSocketsConnection() {
        if (webSocketConnectionHelper == null) {
            webSocketConnectionHelper = new WebSocketConnectionHelper();
        }

        if (webSocketClient == null) {
            webSocketClient = WebSocketConnectionHelper.getExternalSignalingInstanceForServer(
                externalSignalingServer.getExternalSignalingServer(),
                conversationUser, externalSignalingServer.getExternalSignalingTicket(),
                TextUtils.isEmpty(credentials));
        } else {
            if (webSocketClient.isConnected() && currentCallStatus == CallStatus.PUBLISHER_FAILED) {
                webSocketClient.restartWebSocket();
            }
        }

        joinRoomAndCall();
    }

    private void initiateCall() {
        if (!TextUtils.isEmpty(roomToken)) {
            checkDevicePermissions();
        } else {
            handleFromNotification();
        }
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onMessageEvent(WebSocketCommunicationEvent webSocketCommunicationEvent) {
        if (currentCallStatus == CallStatus.LEAVING) {
            return;
        }

        switch (webSocketCommunicationEvent.getType()) {
            case "hello":
                Log.d(TAG, "onMessageEvent 'hello'");
                if (!webSocketCommunicationEvent.getHashMap().containsKey("oldResumeId")) {
                    if (currentCallStatus == CallStatus.RECONNECTING) {
                        hangup(false);
                    } else {
                        setCallState(CallStatus.RECONNECTING);
                        runOnUiThread(this::initiateCall);
                    }
                }
                break;
            case "roomJoined":
                Log.d(TAG, "onMessageEvent 'roomJoined'");
                startSendingNick();

                if (webSocketCommunicationEvent.getHashMap().get("roomToken").equals(roomToken)) {
                    performCall();
                }
                break;
            case PARTICIPANTS_UPDATE:
                Log.d(TAG, "onMessageEvent 'participantsUpdate'");

                // See MagicWebSocketInstance#onMessage in case "participants" how the 'updateParameters' are created
                Map<String, String> updateParameters = webSocketCommunicationEvent.getHashMap();

                if (updateParameters == null) {
                    break;
                }

                String updateRoomToken = updateParameters.get(ROOM_TOKEN);
                String updateAll = updateParameters.get(UPDATE_ALL);
                String updateInCall = updateParameters.get(UPDATE_IN_CALL);
                String jobId = updateParameters.get(JOB_ID);

                if (roomToken.equals(updateRoomToken)) {
                    if (updateAll != null && Boolean.parseBoolean(updateAll)) {
                        if ("0".equals(updateInCall)) {
                            Log.d(TAG, "Most probably a moderator ended the call for all.");
                            hangup(true);
                        }
                    } else if (jobId != null) {
                        // In that case a list of users for the room is passed.
                        processUsersInRoom(
                            (List<HashMap<String, Object>>) webSocketClient
                                .getJobWithId(
                                    Integer.valueOf(jobId)));
                    }

                }
                break;
            case "signalingMessage":
                Log.d(TAG, "onMessageEvent 'signalingMessage'");
                processMessage((NCSignalingMessage) webSocketClient.getJobWithId(
                    Integer.valueOf(webSocketCommunicationEvent.getHashMap().get("jobId"))));
                break;
            case "peerReadyForRequestingOffer":
                Log.d(TAG, "onMessageEvent 'peerReadyForRequestingOffer'");
                webSocketClient.requestOfferForSessionIdWithType(
                    webSocketCommunicationEvent.getHashMap().get("sessionId"), "video");
                break;
        }
    }

    private void dispose(@Nullable Disposable disposable) {
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        } else if (disposable == null) {
            if (signalingDisposable != null && !signalingDisposable.isDisposed()) {
                signalingDisposable.dispose();
                signalingDisposable = null;
            }
        }
    }

    private void receivedSignalingMessages(@Nullable List<Signaling> signalingList) {
        if (signalingList != null) {
            for (Signaling signaling : signalingList) {
                try {
                    receivedSignalingMessage(signaling);
                } catch (IOException e) {
                    Log.e(TAG, "Failed to process received signaling message", e);
                }
            }
        }
    }

    private void receivedSignalingMessage(Signaling signaling) throws IOException {
        String messageType = signaling.getType();

        if (!isConnectionEstablished() && currentCallStatus != CallStatus.CONNECTING) {
            return;
        }

        if ("usersInRoom".equals(messageType)) {
            processUsersInRoom((List<HashMap<String, Object>>) signaling.getMessageWrapper());
        } else if ("message".equals(messageType)) {
            NCSignalingMessage ncSignalingMessage = LoganSquare.parse(signaling.getMessageWrapper().toString(),
                                                                      NCSignalingMessage.class);
            processMessage(ncSignalingMessage);
        } else {
            Log.e(TAG, "unexpected message type when receiving signaling message");
        }
    }

    private void processMessage(NCSignalingMessage ncSignalingMessage) {
        if (ncSignalingMessage.getRoomType().equals("video") || ncSignalingMessage.getRoomType().equals("screen")) {
            String type = null;
            if (ncSignalingMessage.getPayload() != null && ncSignalingMessage.getPayload().getType() != null) {
                type = ncSignalingMessage.getPayload().getType();
            } else if (ncSignalingMessage.getType() != null) {
                type = ncSignalingMessage.getType();
            }

            PeerConnectionWrapper peerConnectionWrapper = null;

            if ("offer".equals(type)) {
                peerConnectionWrapper =
                    getOrCreatePeerConnectionWrapperForSessionIdAndType(ncSignalingMessage.getFrom(),
                                                                        ncSignalingMessage.getRoomType(), false);
            } else {
                peerConnectionWrapper =
                    getPeerConnectionWrapperForSessionIdAndType(ncSignalingMessage.getFrom(),
                                                                ncSignalingMessage.getRoomType());
            }

            if ("unshareScreen".equals(type) ||
                (("offer".equals(type) ||
                    "answer".equals(type) ||
                    "candidate".equals(type) ||
                    "endOfCandidates".equals(type)) &&
                    peerConnectionWrapper != null)) {
                switch (type) {
                    case "unshareScreen":
                        endPeerConnection(ncSignalingMessage.getFrom(), true);
                        break;
                    case "offer":
                    case "answer":
                        peerConnectionWrapper.setNick(ncSignalingMessage.getPayload().getNick());
                        SessionDescription sessionDescriptionWithPreferredCodec;

                        String sessionDescriptionStringWithPreferredCodec = MagicWebRTCUtils.preferCodec
                            (ncSignalingMessage.getPayload().getSdp(),
                             "H264", false);

                        sessionDescriptionWithPreferredCodec = new SessionDescription(
                            SessionDescription.Type.fromCanonicalForm(type),
                            sessionDescriptionStringWithPreferredCodec);

                        if (peerConnectionWrapper.getPeerConnection() != null) {
                            peerConnectionWrapper.getPeerConnection().setRemoteDescription(
                                peerConnectionWrapper.getMagicSdpObserver(),
                                sessionDescriptionWithPreferredCodec);
                        }
                        break;
                    case "candidate":
                        NCIceCandidate ncIceCandidate = ncSignalingMessage.getPayload().getIceCandidate();
                        IceCandidate iceCandidate = new IceCandidate(ncIceCandidate.getSdpMid(),
                                                                     ncIceCandidate.getSdpMLineIndex(),
                                                                     ncIceCandidate.getCandidate());
                        peerConnectionWrapper.addCandidate(iceCandidate);
                        break;
                    case "endOfCandidates":
                        peerConnectionWrapper.drainIceCandidates();
                        break;
                    default:
                        break;
                }
            }
        } else {
            Log.e(TAG, "unexpected RoomType while processing NCSignalingMessage");
        }
    }

    private void hangup(boolean shutDownView) {
        Log.d(TAG, "hangup! shutDownView=" + shutDownView);
        if (shutDownView) {
            setCallState(CallStatus.LEAVING);
        }
        stopCallingSound();
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

            binding.selfVideoRenderer.release();

            if (audioSource != null) {
                audioSource.dispose();
                audioSource = null;
            }

            runOnUiThread(() -> {
                if (audioManager != null) {
                    audioManager.stop();
                    audioManager = null;
                }
            });

            if (videoSource != null) {
                videoSource = null;
            }

            if (peerConnectionFactory != null) {
                peerConnectionFactory = null;
            }


            localAudioTrack = null;
            localVideoTrack = null;


            if (TextUtils.isEmpty(credentials) && hasExternalSignalingServer) {
                WebSocketConnectionHelper.deleteExternalSignalingInstanceForUserEntity(-1);
            }
        }

        List<String> sessionIdsToEnd = new ArrayList<String>(peerConnectionWrapperList.size());
        for (PeerConnectionWrapper wrapper : peerConnectionWrapperList) {
            sessionIdsToEnd.add(wrapper.getSessionId());
        }
        for (String sessionId : sessionIdsToEnd) {
            endPeerConnection(sessionId, false);
        }

        hangupNetworkCalls(shutDownView);
        ApplicationWideCurrentRoomHolder.getInstance().setInCall(false);
    }

    private void hangupNetworkCalls(boolean shutDownView) {
        Log.d(TAG, "hangupNetworkCalls. shutDownView=" + shutDownView);
        int apiVersion = ApiUtils.getCallApiVersion(conversationUser, new int[]{ApiUtils.APIv4, 1});

        ncApi.leaveCall(credentials, ApiUtils.getUrlForCall(apiVersion, baseUrl, roomToken))
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(new Observer<GenericOverall>() {
                @Override
                public void onSubscribe(@io.reactivex.annotations.NonNull Disposable d) {
                    // unused atm
                }

                @Override
                public void onNext(@io.reactivex.annotations.NonNull GenericOverall genericOverall) {
                    if (shutDownView) {
                        finish();
                    } else if (currentCallStatus == CallStatus.RECONNECTING
                        || currentCallStatus == CallStatus.PUBLISHER_FAILED) {
                        initiateCall();
                    }
                }

                @Override
                public void onError(@io.reactivex.annotations.NonNull Throwable e) {
                    // unused atm
                }

                @Override
                public void onComplete() {
                    // unused atm
                }
            });
    }

    private void startVideoCapture() {
        if (videoCapturer != null) {
            videoCapturer.startCapture(1280, 720, 30);
        }
    }

    private void processUsersInRoom(List<HashMap<String, Object>> users) {
        Log.d(TAG, "processUsersInRoom");
        List<String> newSessions = new ArrayList<>();
        Set<String> oldSessions = new HashSet<>();
        Map<String, String> userIdsBySessionId = new HashMap<>();

        hasMCU = hasExternalSignalingServer && webSocketClient != null && webSocketClient.hasMCU();
        Log.d(TAG, "   hasMCU is " + hasMCU);

        // The signaling session is the same as the Nextcloud session only when the MCU is not used.
        String currentSessionId = callSession;
        if (hasMCU) {
            currentSessionId = webSocketClient.getSessionId();
        }

        Log.d(TAG, "   currentSessionId is " + currentSessionId);

        for (HashMap<String, Object> participant : users) {
            long inCallFlag = (long) participant.get("inCall");
            if (!participant.get("sessionId").equals(currentSessionId)) {
                Log.d(TAG, "   inCallFlag of participant "
                    + participant.get("sessionId").toString().substring(0, 4)
                    + " : "
                    + inCallFlag);

                boolean isInCall = inCallFlag != 0;
                if (isInCall) {
                    newSessions.add(participant.get("sessionId").toString());
                }

                // The property is "userId" when not using the external signaling server and "userid" when using it.
                String userId = null;
                if (participant.get("userId") != null) {
                    userId = participant.get("userId").toString();
                } else if (participant.get("userid") != null) {
                    userId = participant.get("userid").toString();
                }
                userIdsBySessionId.put(participant.get("sessionId").toString(), userId);
            } else {
                Log.d(TAG, "   inCallFlag of currentSessionId: " + inCallFlag);
                if (inCallFlag == 0 && currentCallStatus != CallStatus.LEAVING && ApplicationWideCurrentRoomHolder.getInstance().isInCall()) {
                    Log.d(TAG, "Most probably a moderator ended the call for all.");
                    hangup(true);
                }
            }
        }

        for (PeerConnectionWrapper peerConnectionWrapper : peerConnectionWrapperList) {
            if (!peerConnectionWrapper.isMCUPublisher()) {
                oldSessions.add(peerConnectionWrapper.getSessionId());
            }
        }

        // Calculate sessions that left the call
        List<String> disconnectedSessions = new ArrayList<>(oldSessions);
        disconnectedSessions.removeAll(newSessions);

        // Calculate sessions that join the call
        newSessions.removeAll(oldSessions);

        if (!isConnectionEstablished() && currentCallStatus != CallStatus.CONNECTING) {
            return;
        }

        if (newSessions.size() > 0 && !hasMCU) {
            getPeersForCall();
        }

        if (hasMCU) {
            // Ensure that own publishing peer is set up.
            getOrCreatePeerConnectionWrapperForSessionIdAndType(webSocketClient.getSessionId(), VIDEO_STREAM_TYPE_VIDEO, true);
        }

        for (String sessionId : newSessions) {
            Log.d(TAG, "   newSession joined: " + sessionId);
            getOrCreatePeerConnectionWrapperForSessionIdAndType(sessionId, VIDEO_STREAM_TYPE_VIDEO, false);

	        String userId = userIdsBySessionId.get(sessionId);

            runOnUiThread(() -> {
                setupVideoStreamForLayout(
                    null,
                    sessionId,
                    userId,
                    false,
                    VIDEO_STREAM_TYPE_VIDEO);
            });
        }

        if (newSessions.size() > 0 && currentCallStatus != CallStatus.IN_CONVERSATION) {
            setCallState(CallStatus.IN_CONVERSATION);
        }

        for (String sessionId : disconnectedSessions) {
            Log.d(TAG, "   oldSession that will be removed is: " + sessionId);
            endPeerConnection(sessionId, false);
        }
    }

    private void getPeersForCall() {
        Log.d(TAG, "getPeersForCall");
        int apiVersion = ApiUtils.getCallApiVersion(conversationUser, new int[]{ApiUtils.APIv4, 1});

        ncApi.getPeersForCall(
                credentials,
                ApiUtils.getUrlForCall(
                    apiVersion,
                    baseUrl,
                    roomToken))
            .subscribeOn(Schedulers.io())
            .subscribe(new Observer<ParticipantsOverall>() {
                @Override
                public void onSubscribe(@io.reactivex.annotations.NonNull Disposable d) {
                    // unused atm
                }

                @Override
                public void onNext(@io.reactivex.annotations.NonNull ParticipantsOverall participantsOverall) {
                    participantMap = new HashMap<>();
                    for (Participant participant : participantsOverall.getOcs().getData()) {
                        participantMap.put(participant.getSessionId(), participant);
                    }
                }

                @Override
                public void onError(@io.reactivex.annotations.NonNull Throwable e) {
                    Log.e(TAG, "error while executing getPeersForCall", e);
                }

                @Override
                public void onComplete() {
                    // unused atm
                }
            });
    }

    private void deletePeerConnection(PeerConnectionWrapper peerConnectionWrapper) {
        peerConnectionWrapper.removePeerConnection();
        peerConnectionWrapperList.remove(peerConnectionWrapper);
    }

    private PeerConnectionWrapper getPeerConnectionWrapperForSessionIdAndType(String sessionId, String type) {
        for (PeerConnectionWrapper wrapper : peerConnectionWrapperList) {
            if (wrapper.getSessionId().equals(sessionId)
                && wrapper.getVideoStreamType().equals(type)) {
                return wrapper;
            }
        }

        return null;
    }

    private PeerConnectionWrapper getOrCreatePeerConnectionWrapperForSessionIdAndType(String sessionId,
                                                                                      String type,
                                                                                      boolean publisher) {
        PeerConnectionWrapper peerConnectionWrapper;
        if ((peerConnectionWrapper = getPeerConnectionWrapperForSessionIdAndType(sessionId, type)) != null) {
            return peerConnectionWrapper;
        } else {
            if (peerConnectionFactory == null) {
                Log.e(TAG, "peerConnectionFactory was null in getOrCreatePeerConnectionWrapperForSessionIdAndType.");
                Toast.makeText(context, context.getResources().getString(R.string.nc_common_error_sorry),
                               Toast.LENGTH_LONG).show();
                hangup(true);
                return null;
            }

            if (hasMCU && publisher) {
                peerConnectionWrapper = new PeerConnectionWrapper(peerConnectionFactory,
                                                                  iceServers,
                                                                  sdpConstraintsForMCU,
                                                                  sessionId,
                                                                  callSession,
                                                                  localStream,
                                                                  true,
                                                                  true,
                                                                  type);

            } else if (hasMCU) {
                peerConnectionWrapper = new PeerConnectionWrapper(peerConnectionFactory,
                                                                  iceServers,
                                                                  sdpConstraints,
                                                                  sessionId,
                                                                  callSession,
                                                                  null,
                                                                  false,
                                                                  true,
                                                                  type);
            } else {
                if (!"screen".equals(type)) {
                    peerConnectionWrapper = new PeerConnectionWrapper(peerConnectionFactory,
                                                                      iceServers,
                                                                      sdpConstraints,
                                                                      sessionId,
                                                                      callSession,
                                                                      localStream,
                                                                      false,
                                                                      false,
                                                                      type);
                } else {
                    peerConnectionWrapper = new PeerConnectionWrapper(peerConnectionFactory,
                                                                      iceServers,
                                                                      sdpConstraints,
                                                                      sessionId,
                                                                      callSession,
                                                                      null,
                                                                      false,
                                                                      false,
                                                                      type);
                }
            }

            peerConnectionWrapperList.add(peerConnectionWrapper);

            if (publisher) {
                startSendingNick();
            }

            return peerConnectionWrapper;
        }
    }

    private List<PeerConnectionWrapper> getPeerConnectionWrapperListForSessionId(String sessionId) {
        List<PeerConnectionWrapper> internalList = new ArrayList<>();
        for (PeerConnectionWrapper peerConnectionWrapper : peerConnectionWrapperList) {
            if (peerConnectionWrapper.getSessionId().equals(sessionId)) {
                internalList.add(peerConnectionWrapper);
            }
        }

        return internalList;
    }

    private void endPeerConnection(String sessionId, boolean justScreen) {
        List<PeerConnectionWrapper> peerConnectionWrappers;
        if (!(peerConnectionWrappers = getPeerConnectionWrapperListForSessionId(sessionId)).isEmpty()) {
            for (PeerConnectionWrapper peerConnectionWrapper : peerConnectionWrappers) {
                if (peerConnectionWrapper.getSessionId().equals(sessionId)) {
                    String videoStreamType = peerConnectionWrapper.getVideoStreamType();
                    if (VIDEO_STREAM_TYPE_SCREEN.equals(videoStreamType) || !justScreen) {
                        runOnUiThread(() -> removeMediaStream(sessionId, videoStreamType));
                        deletePeerConnection(peerConnectionWrapper);
                    }
                }
            }
        }
    }

    private void removeMediaStream(String sessionId, String videoStreamType) {
        Log.d(TAG, "removeMediaStream");
        participantDisplayItems.remove(sessionId + "-" + videoStreamType);

        if (!isDestroyed()) {
            initGridAdapter();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(ConfigurationChangeEvent configurationChangeEvent) {
        powerManagerUtils.setOrientation(Objects.requireNonNull(getResources()).getConfiguration().orientation);
        initGridAdapter();
        updateSelfVideoViewPosition();
    }

    private void updateSelfVideoViewConnected(boolean connected) {
        // FIXME In voice only calls there is no video view, so the progress bar would appear floating in the middle of
        // nowhere. However, a way to signal that the local participant is not connected to the HPB is still need in
        // that case.
        if (!connected && !isVoiceOnlyCall) {
            binding.selfVideoViewProgressBar.setVisibility(View.VISIBLE);
        } else {
            binding.selfVideoViewProgressBar.setVisibility(View.GONE);
        }
    }

    private void updateSelfVideoViewPosition() {
        Log.d(TAG, "updateSelfVideoViewPosition");
        if (!isInPipMode) {
            FrameLayout.LayoutParams layoutParams =
                (FrameLayout.LayoutParams) binding.selfVideoRenderer.getLayoutParams();

            DisplayMetrics displayMetrics = getApplicationContext().getResources().getDisplayMetrics();
            int screenWidthPx = displayMetrics.widthPixels;

            int screenWidthDp = (int) DisplayUtils.convertPixelToDp(screenWidthPx, getApplicationContext());

            float newXafterRotate = 0;
            float newYafterRotate;
            if (binding.callInfosLinearLayout.getVisibility() == View.VISIBLE) {
                newYafterRotate = 250;
            } else {
                newYafterRotate = 20;
            }

            if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                layoutParams.height = (int) getResources().getDimension(R.dimen.call_self_video_short_side_length);
                layoutParams.width = (int) getResources().getDimension(R.dimen.call_self_video_long_side_length);
                newXafterRotate = (float) (screenWidthDp - getResources().getDimension(R.dimen.call_self_video_short_side_length) * 0.8);

            } else if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
                layoutParams.height = (int) getResources().getDimension(R.dimen.call_self_video_long_side_length);
                layoutParams.width = (int) getResources().getDimension(R.dimen.call_self_video_short_side_length);
                newXafterRotate = (float) (screenWidthDp - getResources().getDimension(R.dimen.call_self_video_short_side_length) * 0.5);
            }
            binding.selfVideoRenderer.setLayoutParams(layoutParams);

            int newXafterRotatePx = (int) DisplayUtils.convertDpToPixel(newXafterRotate, getApplicationContext());
            binding.selfVideoViewWrapper.setY(newYafterRotate);
            binding.selfVideoViewWrapper.setX(newXafterRotatePx);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(PeerConnectionEvent peerConnectionEvent) {
        String sessionId = peerConnectionEvent.getSessionId();
        String participantDisplayItemId = sessionId + "-" + peerConnectionEvent.getVideoStreamType();

        if (peerConnectionEvent.getPeerConnectionEventType() ==
            PeerConnectionEvent.PeerConnectionEventType.PEER_CONNECTED) {
            if (webSocketClient != null && webSocketClient.getSessionId() != null && webSocketClient.getSessionId().equals(sessionId)) {
                updateSelfVideoViewConnected(true);
            } else if (participantDisplayItems.get(participantDisplayItemId) != null) {
                participantDisplayItems.get(participantDisplayItemId).setConnected(true);
                participantsAdapter.notifyDataSetChanged();
            }
        } else if (peerConnectionEvent.getPeerConnectionEventType() ==
            PeerConnectionEvent.PeerConnectionEventType.PEER_DISCONNECTED) {
            if (webSocketClient != null && webSocketClient.getSessionId() != null && webSocketClient.getSessionId().equals(sessionId)) {
                updateSelfVideoViewConnected(false);
            } else if (participantDisplayItems.get(participantDisplayItemId) != null) {
                participantDisplayItems.get(participantDisplayItemId).setConnected(false);
                participantsAdapter.notifyDataSetChanged();
            }
        } else if (peerConnectionEvent.getPeerConnectionEventType() ==
            PeerConnectionEvent.PeerConnectionEventType.PEER_CLOSED) {
            endPeerConnection(sessionId, VIDEO_STREAM_TYPE_SCREEN.equals(peerConnectionEvent.getVideoStreamType()));
        } else if (peerConnectionEvent.getPeerConnectionEventType() ==
            PeerConnectionEvent.PeerConnectionEventType.SENSOR_FAR ||
            peerConnectionEvent.getPeerConnectionEventType() ==
                PeerConnectionEvent.PeerConnectionEventType.SENSOR_NEAR) {

            if (!isVoiceOnlyCall) {
                boolean enableVideo = peerConnectionEvent.getPeerConnectionEventType() ==
                    PeerConnectionEvent.PeerConnectionEventType.SENSOR_FAR && videoOn;
                if (EffortlessPermissions.hasPermissions(this, PERMISSIONS_CAMERA) &&
                    (currentCallStatus == CallStatus.CONNECTING || isConnectionEstablished()) && videoOn
                    && enableVideo != localVideoTrack.enabled()) {
                    toggleMedia(enableVideo, true);
                }
            }
        } else if (peerConnectionEvent.getPeerConnectionEventType() ==
            PeerConnectionEvent.PeerConnectionEventType.NICK_CHANGE) {
            if (participantDisplayItems.get(participantDisplayItemId) != null) {
                participantDisplayItems.get(participantDisplayItemId).setNick(peerConnectionEvent.getNick());
                participantsAdapter.notifyDataSetChanged();
            }
        } else if (peerConnectionEvent.getPeerConnectionEventType() ==
            PeerConnectionEvent.PeerConnectionEventType.VIDEO_CHANGE && !isVoiceOnlyCall) {
            if (participantDisplayItems.get(participantDisplayItemId) != null) {
                participantDisplayItems.get(participantDisplayItemId).setStreamEnabled(peerConnectionEvent.getChangeValue());
                participantsAdapter.notifyDataSetChanged();
            }
        } else if (peerConnectionEvent.getPeerConnectionEventType() ==
            PeerConnectionEvent.PeerConnectionEventType.AUDIO_CHANGE) {
            if (participantDisplayItems.get(participantDisplayItemId) != null) {
                participantDisplayItems.get(participantDisplayItemId).setAudioEnabled(peerConnectionEvent.getChangeValue());
                participantsAdapter.notifyDataSetChanged();
            }
        } else if (peerConnectionEvent.getPeerConnectionEventType() ==
            PeerConnectionEvent.PeerConnectionEventType.PUBLISHER_FAILED) {
            setCallState(CallStatus.PUBLISHER_FAILED);
            webSocketClient.clearResumeId();
            hangup(false);
        }
    }

    private void startSendingNick() {
        DataChannelMessageNick dataChannelMessage = new DataChannelMessageNick();
        dataChannelMessage.setType("nickChanged");
        HashMap<String, String> nickChangedPayload = new HashMap<>();
        nickChangedPayload.put("userid", conversationUser.getUserId());
        nickChangedPayload.put("name", conversationUser.getDisplayName());
        dataChannelMessage.setPayload(nickChangedPayload);
        for (PeerConnectionWrapper peerConnectionWrapper : peerConnectionWrapperList) {
            if (peerConnectionWrapper.isMCUPublisher()) {
                Observable
                    .interval(1, TimeUnit.SECONDS)
                    .repeatUntil(() -> (!isConnectionEstablished() || isDestroyed()))
                    .observeOn(Schedulers.io())
                    .subscribe(new Observer<Long>() {
                        @Override
                        public void onSubscribe(@io.reactivex.annotations.NonNull Disposable d) {
                            // unused atm
                        }

                        @Override
                        public void onNext(@io.reactivex.annotations.NonNull Long aLong) {
                            peerConnectionWrapper.sendNickChannelData(dataChannelMessage);
                        }

                        @Override
                        public void onError(@io.reactivex.annotations.NonNull Throwable e) {
                            // unused atm
                        }

                        @Override
                        public void onComplete() {
                            // unused atm
                        }
                    });
                break;
            }

        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(MediaStreamEvent mediaStreamEvent) {
        if (mediaStreamEvent.getMediaStream() != null) {
            boolean hasAtLeastOneVideoStream = mediaStreamEvent.getMediaStream().videoTracks != null
                && mediaStreamEvent.getMediaStream().videoTracks.size() > 0;

            setupVideoStreamForLayout(
                mediaStreamEvent.getMediaStream(),
                mediaStreamEvent.getSession(),
                null,
                hasAtLeastOneVideoStream,
                mediaStreamEvent.getVideoStreamType());
        } else {
            setupVideoStreamForLayout(
                null,
                mediaStreamEvent.getSession(),
                null,
                false,
                mediaStreamEvent.getVideoStreamType());
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
                .append(StringEscapeUtils.escapeJson(LoganSquare.serialize(ncMessageWrapper.getSignalingMessage())))
                .append("\"")
                .append(",")
                .append("\"sessionId\":")
                .append("\"").append(StringEscapeUtils.escapeJson(callSession)).append("\"")
                .append(",")
                .append("\"ev\":\"message\"")
                .append("}");

            List<String> strings = new ArrayList<>();
            String stringToSend = stringBuilder.toString();
            strings.add(stringToSend);

            int apiVersion = ApiUtils.getSignalingApiVersion(conversationUser, new int[]{ApiUtils.APIv3, 2, 1});

            ncApi.sendSignalingMessages(credentials, ApiUtils.getUrlForSignaling(apiVersion, baseUrl, roomToken),
                                        strings.toString())
                .retry(3)
                .subscribeOn(Schedulers.io())
                .subscribe(new Observer<SignalingOverall>() {
                    @Override
                    public void onSubscribe(@io.reactivex.annotations.NonNull Disposable d) {
                        // unused atm
                    }

                    @Override
                    public void onNext(@io.reactivex.annotations.NonNull SignalingOverall signalingOverall) {
                        receivedSignalingMessages(signalingOverall.getOcs().getSignalings());
                    }

                    @Override
                    public void onError(@io.reactivex.annotations.NonNull Throwable e) {
                        Log.e(TAG, "", e);
                    }

                    @Override
                    public void onComplete() {
                        // unused atm
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

    private void setupVideoStreamForLayout(@Nullable MediaStream mediaStream,
                                           String session,
                                           String userId,
                                           boolean videoStreamEnabled,
                                           String videoStreamType) {
        PeerConnectionWrapper peerConnectionWrapper = getPeerConnectionWrapperForSessionIdAndType(session,
                                                                                                  videoStreamType);

        boolean connected = false;
        if (peerConnectionWrapper != null) {
            PeerConnection.IceConnectionState iceConnectionState = peerConnectionWrapper.getPeerConnection().iceConnectionState();
            connected = iceConnectionState == PeerConnection.IceConnectionState.CONNECTED ||
                iceConnectionState == PeerConnection.IceConnectionState.COMPLETED;
        }

        String nick;
        if (hasExternalSignalingServer) {
            nick = webSocketClient.getDisplayNameForSession(session);
        } else {
            nick = peerConnectionWrapper != null ? peerConnectionWrapper.getNick() : "";
        }

        String userId4Usage = userId;

        if (userId4Usage == null) {
            if (hasMCU) {
                userId4Usage = webSocketClient.getUserIdForSession(session);
            } else if (participantMap.get(session) != null && participantMap.get(session).getCalculatedActorType() == Participant.ActorType.USERS) {
                userId4Usage = participantMap.get(session).getCalculatedActorId();
            }
        }

        ParticipantDisplayItem participantDisplayItem = new ParticipantDisplayItem(baseUrl,
                                                                                   userId4Usage,
                                                                                   session,
                                                                                   connected,
                                                                                   nick,
                                                                                   mediaStream,
                                                                                   videoStreamType,
                                                                                   videoStreamEnabled,
                                                                                   rootEglBase);
        participantDisplayItems.put(session + "-" + videoStreamType, participantDisplayItem);

        initGridAdapter();
    }

    private void setCallState(CallStatus callState) {
        if (currentCallStatus == null || currentCallStatus != callState) {
            currentCallStatus = callState;
            if (handler == null) {
                handler = new Handler(Looper.getMainLooper());
            } else {
                handler.removeCallbacksAndMessages(null);
            }

            switch (callState) {
                case CONNECTING:
                    handler.post(() -> {
                        playCallingSound();
                        if (isIncomingCallFromNotification) {
                            binding.callStates.callStateTextView.setText(R.string.nc_call_incoming);
                        } else {
                            binding.callStates.callStateTextView.setText(R.string.nc_call_ringing);
                        }
                        binding.callConversationNameTextView.setText(conversationName);

                        binding.callModeTextView.setText(getDescriptionForCallType());

                        if (binding.callStates.callStateRelativeLayout.getVisibility() != View.VISIBLE) {
                            binding.callStates.callStateRelativeLayout.setVisibility(View.VISIBLE);
                        }

                        if (binding.gridview.getVisibility() != View.INVISIBLE) {
                            binding.gridview.setVisibility(View.INVISIBLE);
                        }

                        if (binding.callStates.callStateProgressBar.getVisibility() != View.VISIBLE) {
                            binding.callStates.callStateProgressBar.setVisibility(View.VISIBLE);
                        }

                        if (binding.callStates.errorImageView.getVisibility() != View.GONE) {
                            binding.callStates.errorImageView.setVisibility(View.GONE);
                        }
                    });
                    break;
                case CALLING_TIMEOUT:
                    handler.post(() -> {
                        hangup(false);
                        binding.callStates.callStateTextView.setText(R.string.nc_call_timeout);
                        binding.callModeTextView.setText(getDescriptionForCallType());
                        if (binding.callStates.callStateRelativeLayout.getVisibility() != View.VISIBLE) {
                            binding.callStates.callStateRelativeLayout.setVisibility(View.VISIBLE);
                        }

                        if (binding.callStates.callStateProgressBar.getVisibility() != View.GONE) {
                            binding.callStates.callStateProgressBar.setVisibility(View.GONE);
                        }

                        if (binding.gridview.getVisibility() != View.INVISIBLE) {
                            binding.gridview.setVisibility(View.INVISIBLE);
                        }

                        binding.callStates.errorImageView.setImageResource(R.drawable.ic_av_timer_timer_24dp);

                        if (binding.callStates.errorImageView.getVisibility() != View.VISIBLE) {
                            binding.callStates.errorImageView.setVisibility(View.VISIBLE);
                        }
                    });
                    break;
                case PUBLISHER_FAILED:
                    handler.post(() -> {
                        // No calling sound when the publisher failed
                        binding.callStates.callStateTextView.setText(R.string.nc_call_reconnecting);
                        binding.callModeTextView.setText(getDescriptionForCallType());
                        if (binding.callStates.callStateRelativeLayout.getVisibility() != View.VISIBLE) {
                            binding.callStates.callStateRelativeLayout.setVisibility(View.VISIBLE);
                        }
                        if (binding.gridview.getVisibility() != View.INVISIBLE) {
                            binding.gridview.setVisibility(View.INVISIBLE);
                        }
                        if (binding.callStates.callStateProgressBar.getVisibility() != View.VISIBLE) {
                            binding.callStates.callStateProgressBar.setVisibility(View.VISIBLE);
                        }
                        if (binding.callStates.errorImageView.getVisibility() != View.GONE) {
                            binding.callStates.errorImageView.setVisibility(View.GONE);
                        }
                    });
                    break;
                case RECONNECTING:
                    handler.post(() -> {
                        playCallingSound();
                        binding.callStates.callStateTextView.setText(R.string.nc_call_reconnecting);
                        binding.callModeTextView.setText(getDescriptionForCallType());
                        if (binding.callStates.callStateRelativeLayout.getVisibility() != View.VISIBLE) {
                            binding.callStates.callStateRelativeLayout.setVisibility(View.VISIBLE);
                        }
                        if (binding.gridview.getVisibility() != View.INVISIBLE) {
                            binding.gridview.setVisibility(View.INVISIBLE);
                        }
                        if (binding.callStates.callStateProgressBar.getVisibility() != View.VISIBLE) {
                            binding.callStates.callStateProgressBar.setVisibility(View.VISIBLE);
                        }

                        if (binding.callStates.errorImageView.getVisibility() != View.GONE) {
                            binding.callStates.errorImageView.setVisibility(View.GONE);
                        }
                    });
                    break;
                case JOINED:
                    handler.postDelayed(() -> setCallState(CallStatus.CALLING_TIMEOUT), 45000);
                    handler.post(() -> {
                        binding.callModeTextView.setText(getDescriptionForCallType());
                        if (isIncomingCallFromNotification) {
                            binding.callStates.callStateTextView.setText(R.string.nc_call_incoming);
                        } else {
                            binding.callStates.callStateTextView.setText(R.string.nc_call_ringing);
                        }
                        if (binding.callStates.callStateRelativeLayout.getVisibility() != View.VISIBLE) {
                            binding.callStates.callStateRelativeLayout.setVisibility(View.VISIBLE);
                        }

                        if (binding.callStates.callStateProgressBar.getVisibility() != View.VISIBLE) {
                            binding.callStates.callStateProgressBar.setVisibility(View.VISIBLE);
                        }

                        if (binding.gridview.getVisibility() != View.INVISIBLE) {
                            binding.gridview.setVisibility(View.INVISIBLE);
                        }

                        if (binding.callStates.errorImageView.getVisibility() != View.GONE) {
                            binding.callStates.errorImageView.setVisibility(View.GONE);
                        }
                    });
                    break;
                case IN_CONVERSATION:
                    handler.post(() -> {
                        stopCallingSound();
                        binding.callModeTextView.setText(getDescriptionForCallType());

                        if (!isVoiceOnlyCall) {
                            binding.callInfosLinearLayout.setVisibility(View.GONE);
                        }

                        if (!isPushToTalkActive) {
                            animateCallControls(false, 5000);
                        }

                        if (binding.callStates.callStateRelativeLayout.getVisibility() != View.INVISIBLE) {
                            binding.callStates.callStateRelativeLayout.setVisibility(View.INVISIBLE);
                        }

                        if (binding.callStates.callStateProgressBar.getVisibility() != View.GONE) {
                            binding.callStates.callStateProgressBar.setVisibility(View.GONE);
                        }

                        if (binding.gridview.getVisibility() != View.VISIBLE) {
                            binding.gridview.setVisibility(View.VISIBLE);
                        }

                        if (binding.callStates.errorImageView.getVisibility() != View.GONE) {
                            binding.callStates.errorImageView.setVisibility(View.GONE);
                        }
                    });
                    break;
                case OFFLINE:
                    handler.post(() -> {
                        stopCallingSound();

                        binding.callStates.callStateTextView.setText(R.string.nc_offline);

                        if (binding.callStates.callStateRelativeLayout.getVisibility() != View.VISIBLE) {
                            binding.callStates.callStateRelativeLayout.setVisibility(View.VISIBLE);
                        }


                        if (binding.gridview.getVisibility() != View.INVISIBLE) {
                            binding.gridview.setVisibility(View.INVISIBLE);
                        }

                        if (binding.callStates.callStateProgressBar.getVisibility() != View.GONE) {
                            binding.callStates.callStateProgressBar.setVisibility(View.GONE);
                        }

                        binding.callStates.errorImageView.setImageResource(R.drawable.ic_signal_wifi_off_white_24dp);
                        if (binding.callStates.errorImageView.getVisibility() != View.VISIBLE) {
                            binding.callStates.errorImageView.setVisibility(View.VISIBLE);
                        }
                    });
                    break;
                case LEAVING:
                    handler.post(() -> {
                        if (!isDestroyed()) {
                            stopCallingSound();
                            binding.callModeTextView.setText(getDescriptionForCallType());
                            binding.callStates.callStateTextView.setText(R.string.nc_leaving_call);
                            binding.callStates.callStateRelativeLayout.setVisibility(View.VISIBLE);
                            binding.gridview.setVisibility(View.INVISIBLE);
                            binding.callStates.callStateProgressBar.setVisibility(View.VISIBLE);
                            binding.callStates.errorImageView.setVisibility(View.GONE);
                        }
                    });
                    break;
                default:
            }
        }
    }

    private String getDescriptionForCallType() {
        String appName = getResources().getString(R.string.nc_app_product_name);
        if (isVoiceOnlyCall) {
            return String.format(getResources().getString(R.string.nc_call_voice), appName);
        } else {
            return String.format(getResources().getString(R.string.nc_call_video), appName);
        }
    }

    private void playCallingSound() {
        stopCallingSound();
        Uri ringtoneUri;
        if (isIncomingCallFromNotification) {
            ringtoneUri = NotificationUtils.INSTANCE.getCallRingtoneUri(getApplicationContext(), appPreferences);
        } else {
            ringtoneUri = Uri.parse("android.resource://" + getApplicationContext().getPackageName() + "/raw" +
                                        "/tr110_1_kap8_3_freiton1");
        }

        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(this, ringtoneUri);
            mediaPlayer.setLooping(true);
            AudioAttributes audioAttributes = new AudioAttributes.Builder().setContentType(
                    AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .build();
            mediaPlayer.setAudioAttributes(audioAttributes);

            mediaPlayer.setOnPreparedListener(mp -> mediaPlayer.start());

            mediaPlayer.prepareAsync();

        } catch (IOException e) {
            Log.e(TAG, "Failed to play sound");
        }
    }

    private void stopCallingSound() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }

            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    private class MicrophoneButtonTouchListener implements View.OnTouchListener {

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            v.onTouchEvent(event);
            if (event.getAction() == MotionEvent.ACTION_UP && isPushToTalkActive) {
                isPushToTalkActive = false;
                binding.microphoneButton.setImageResource(R.drawable.ic_mic_off_white_24px);
                pulseAnimation.stop();
                toggleMedia(false, false);
                animateCallControls(false, 5000);
            }
            return true;
        }
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onMessageEvent(NetworkEvent networkEvent) {
        if (networkEvent.getNetworkConnectionEvent() == NetworkEvent.NetworkConnectionEvent.NETWORK_CONNECTED) {
            if (handler != null) {
                handler.removeCallbacksAndMessages(null);
            }
        } else if (networkEvent.getNetworkConnectionEvent() ==
            NetworkEvent.NetworkConnectionEvent.NETWORK_DISCONNECTED) {
            if (handler != null) {
                handler.removeCallbacksAndMessages(null);
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        Log.d(TAG, "onPictureInPictureModeChanged");
        Log.d(TAG, "isInPictureInPictureMode= " + isInPictureInPictureMode);
        isInPipMode = isInPictureInPictureMode;
        if (isInPictureInPictureMode) {
            mReceiver =
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        if (intent == null || !MICROPHONE_PIP_INTENT_NAME.equals(intent.getAction())) {
                            return;
                        }

                        final int action = intent.getIntExtra(MICROPHONE_PIP_INTENT_EXTRA_ACTION, 0);
                        switch (action) {
                            case MICROPHONE_PIP_REQUEST_MUTE:
                            case MICROPHONE_PIP_REQUEST_UNMUTE:
                                onMicrophoneClick();
                                break;
                        }
                    }
                };
            registerReceiver(mReceiver,
                             new IntentFilter(MICROPHONE_PIP_INTENT_NAME),
                             permissionUtil.getPrivateBroadcastPermission(),
                             null);

            updateUiForPipMode();
        } else {
            unregisterReceiver(mReceiver);
            mReceiver = null;

            updateUiForNormalMode();
        }
    }

    void updatePictureInPictureActions(
        @DrawableRes int iconId,
        String title,
        int requestCode) {

        if (isGreaterEqualOreo() && isPipModePossible()) {
            final ArrayList<RemoteAction> actions = new ArrayList<>();

            final Icon icon = Icon.createWithResource(this, iconId);

            int intentFlag;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                intentFlag = FLAG_IMMUTABLE;
            } else {
                intentFlag = 0;
            }
            final PendingIntent intent =
                PendingIntent.getBroadcast(
                    this,
                    requestCode,
                    new Intent(MICROPHONE_PIP_INTENT_NAME).putExtra(MICROPHONE_PIP_INTENT_EXTRA_ACTION, requestCode),
                    intentFlag);

            actions.add(new RemoteAction(icon, title, title, intent));

            mPictureInPictureParamsBuilder.setActions(actions);
            setPictureInPictureParams(mPictureInPictureParamsBuilder.build());
        }
    }

    public void updateUiForPipMode() {
        Log.d(TAG, "updateUiForPipMode");
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                                                             ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 0);
        binding.gridview.setLayoutParams(params);


        binding.callControls.setVisibility(View.GONE);
        binding.callInfosLinearLayout.setVisibility(View.GONE);
        binding.selfVideoViewWrapper.setVisibility(View.GONE);
        binding.callStates.callStateRelativeLayout.setVisibility(View.GONE);

        if (participantDisplayItems.size() > 1) {
            binding.pipCallConversationNameTextView.setText(conversationName);
            binding.pipGroupCallOverlay.setVisibility(View.VISIBLE);
        } else {
            binding.pipGroupCallOverlay.setVisibility(View.GONE);
        }

        binding.selfVideoRenderer.release();
    }

    public void updateUiForNormalMode() {
        Log.d(TAG, "updateUiForNormalMode");
        if (isVoiceOnlyCall) {
            binding.callControls.setVisibility(View.VISIBLE);
        } else {
            // animateCallControls needs this to be invisible for a check.
            binding.callControls.setVisibility(View.INVISIBLE);
        }
        initViews();

        binding.callInfosLinearLayout.setVisibility(View.VISIBLE);
        binding.selfVideoViewWrapper.setVisibility(View.VISIBLE);

        binding.pipGroupCallOverlay.setVisibility(View.GONE);
    }

    @Override
    void suppressFitsSystemWindows() {
        binding.controllerCallLayout.setFitsSystemWindows(false);
    }

    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        eventBus.post(new ConfigurationChangeEvent());
    }

    private class SelfVideoTouchListener implements View.OnTouchListener {

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouch(View view, MotionEvent event) {
            long duration = event.getEventTime() - event.getDownTime();

            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                float newY = event.getRawY() - binding.selfVideoViewWrapper.getHeight() / (float) 2;
                float newX = event.getRawX() - binding.selfVideoViewWrapper.getWidth() / (float) 2;
                binding.selfVideoViewWrapper.setY(newY);
                binding.selfVideoViewWrapper.setX(newX);
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP && duration < 100) {
                switchCamera();
            }
            return true;
        }
    }
}
