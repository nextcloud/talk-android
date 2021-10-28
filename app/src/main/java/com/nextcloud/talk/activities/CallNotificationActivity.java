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

package com.nextcloud.talk.activities;

import android.annotation.SuppressLint;
import android.app.KeyguardManager;
import android.app.PictureInPictureParams;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.util.Rational;
import android.view.View;
import android.view.WindowManager;

import com.bluelinelabs.logansquare.LoganSquare;
import com.facebook.common.executors.UiThreadImmediateExecutorService;
import com.facebook.common.references.CloseableReference;
import com.facebook.datasource.DataSource;
import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.imagepipeline.core.ImagePipeline;
import com.facebook.imagepipeline.datasource.BaseBitmapDataSubscriber;
import com.facebook.imagepipeline.image.CloseableImage;
import com.facebook.imagepipeline.postprocessors.BlurPostProcessor;
import com.facebook.imagepipeline.request.ImageRequest;
import com.nextcloud.talk.R;
import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.databinding.CallNotificationActivityBinding;
import com.nextcloud.talk.events.CallNotificationClick;
import com.nextcloud.talk.events.ConfigurationChangeEvent;
import com.nextcloud.talk.models.RingtoneSettings;
import com.nextcloud.talk.models.database.CapabilitiesUtil;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.conversations.Conversation;
import com.nextcloud.talk.models.json.conversations.RoomOverall;
import com.nextcloud.talk.models.json.participants.Participant;
import com.nextcloud.talk.models.json.participants.ParticipantsOverall;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.DisplayUtils;
import com.nextcloud.talk.utils.DoNotDisturbUtils;
import com.nextcloud.talk.utils.bundle.BundleKeys;
import com.nextcloud.talk.utils.preferences.AppPreferences;
import com.nextcloud.talk.utils.singletons.AvatarStatusCodeHolder;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.michaelevans.colorart.library.ColorArt;
import org.parceler.Parcels;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.constraintlayout.widget.ConstraintLayout;
import autodagger.AutoInjector;
import butterknife.OnClick;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import okhttp3.Cache;

@AutoInjector(NextcloudTalkApplication.class)
public class CallNotificationActivity extends BaseActivity {

    public static final String TAG = "CallNotificationActivity";

    @Inject
    NcApi ncApi;

    @Inject
    AppPreferences appPreferences;

    @Inject
    Cache cache;

    @Inject
    EventBus eventBus;

    @Inject
    Context context;

    private List<Disposable> disposablesList = new ArrayList<>();
    private Bundle originalBundle;
    private String roomId;
    private UserEntity userBeingCalled;
    private String credentials;
    private Conversation currentConversation;
    private MediaPlayer mediaPlayer;
    private boolean leavingScreen = false;
    private Handler handler;
    private CallNotificationActivityBinding binding;
    private Boolean isInPipMode = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        NextcloudTalkApplication.Companion.getSharedApplication().getComponentApplication().inject(this);

        dismissKeyguard();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        eventBus.post(new CallNotificationClick());

        binding = CallNotificationActivityBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        Bundle extras = getIntent().getExtras();
        this.roomId = extras.getString(BundleKeys.INSTANCE.getKEY_ROOM_ID(), "");
        this.currentConversation = Parcels.unwrap(extras.getParcelable(BundleKeys.INSTANCE.getKEY_ROOM()));
        this.userBeingCalled = extras.getParcelable(BundleKeys.INSTANCE.getKEY_USER_ENTITY());

        this.originalBundle = extras;
        credentials = ApiUtils.getCredentials(userBeingCalled.getUsername(), userBeingCalled.getToken());

        setCallDescriptionText();

        if (currentConversation == null) {
            handleFromNotification();
        } else {
            setUpAfterConversationIsKnown();
        }

        if (DoNotDisturbUtils.INSTANCE.shouldPlaySound()) {
            playRingtoneSound();
        }

        initClickListeners();
    }

    @SuppressLint({"LongLogTag"})
    @Override
    public void onStart() {
        super.onStart();

        if (handler == null) {
            handler = new Handler();

            try {
                cache.evictAll();
            } catch (IOException e) {
                Log.e(TAG, "Failed to evict cache");
            }
        }
    }

    private void initClickListeners() {
        binding.callAnswerVoiceOnlyView.setOnClickListener(l -> {
            originalBundle.putBoolean(BundleKeys.INSTANCE.getKEY_CALL_VOICE_ONLY(), true);
            proceedToCall();
        });

        binding.callAnswerCameraView.setOnClickListener(l -> {
            originalBundle.putBoolean(BundleKeys.INSTANCE.getKEY_CALL_VOICE_ONLY(), false);
            proceedToCall();
        });

        binding.hangupButton.setOnClickListener(l -> hangup());
    }

    private void setCallDescriptionText() {
        String callDescriptionWithoutTypeInfo =
            String.format(
                getResources().getString(R.string.nc_call_unknown),
                getResources().getString(R.string.nc_app_product_name));

        binding.incomingCallVoiceOrVideoTextView.setText(callDescriptionWithoutTypeInfo);
    }

    private void showAnswerControls() {
        binding.callAnswerCameraView.setVisibility(View.VISIBLE);
        binding.callAnswerVoiceOnlyView.setVisibility(View.VISIBLE);
    }

    @OnClick(R.id.hangupButton)
    void hangup() {
        leavingScreen = true;
        finish();
    }

    private void proceedToCall() {
        originalBundle.putString(BundleKeys.INSTANCE.getKEY_ROOM_TOKEN(), currentConversation.getToken());
        originalBundle.putString(BundleKeys.INSTANCE.getKEY_CONVERSATION_NAME(), currentConversation.getDisplayName());

        Intent intent = new Intent(this, CallActivity.class);
        intent.putExtras(originalBundle);
        startActivity(intent);
    }

    private void checkIfAnyParticipantsRemainInRoom() {
        int apiVersion = ApiUtils.getCallApiVersion(userBeingCalled, new int[] {ApiUtils.APIv4, 1});

        ncApi.getPeersForCall(credentials, ApiUtils.getUrlForCall(apiVersion, userBeingCalled.getBaseUrl(),
                                                                  currentConversation.getToken()))
                .subscribeOn(Schedulers.io())
                .takeWhile(observable -> !leavingScreen)
                .subscribe(new Observer<ParticipantsOverall>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        disposablesList.add(d);
                    }

                    @Override
                    public void onNext(ParticipantsOverall participantsOverall) {
                        boolean hasParticipantsInCall = false;
                        boolean inCallOnDifferentDevice = false;
                        List<Participant> participantList = participantsOverall.getOcs().getData();
                        hasParticipantsInCall = participantList.size() > 0;

                        if (hasParticipantsInCall) {
                            for (Participant participant : participantList) {
                                if (participant.getActorType() == Participant.ActorType.USERS &&
                                        participant.getActorId().equals(userBeingCalled.getUserId())) {
                                    inCallOnDifferentDevice = true;
                                    break;
                                }
                            }
                        }

                        if (!hasParticipantsInCall || inCallOnDifferentDevice) {
                            runOnUiThread(() -> hangup());
                        }
                    }

                    @Override
                    public void onError(Throwable e) {

                    }

                    @Override
                    public void onComplete() {
                        if (!leavingScreen) {
                            handler.postDelayed(() -> checkIfAnyParticipantsRemainInRoom(), 5000);
                        }
                    }
                });

    }

    private void handleFromNotification() {
        int apiVersion = ApiUtils.getConversationApiVersion(userBeingCalled, new int[] {ApiUtils.APIv4,
                ApiUtils.APIv3, 1});

        ncApi.getRoom(credentials, ApiUtils.getUrlForRoom(apiVersion, userBeingCalled.getBaseUrl(), roomId))
                .subscribeOn(Schedulers.io())
                .retry(3)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<RoomOverall>() {
                    @Override
                    public void onSubscribe(@io.reactivex.annotations.NonNull Disposable d) {
                        disposablesList.add(d);
                    }

                    @Override
                    public void onNext(@io.reactivex.annotations.NonNull RoomOverall roomOverall) {
                        currentConversation = roomOverall.getOcs().data;
                        setUpAfterConversationIsKnown();

                        if (apiVersion >= 3) {
                            boolean hasCallFlags =
                                    CapabilitiesUtil.hasSpreedFeatureCapability(userBeingCalled,
                                                                                "conversation-call-flags");
                            if (hasCallFlags) {
                                if (isInCallWithVideo(currentConversation.callFlag)) {
                                    binding.incomingCallVoiceOrVideoTextView.setText(
                                            String.format(getResources().getString(R.string.nc_call_video),
                                                          getResources().getString(R.string.nc_app_product_name)));
                                } else {
                                    binding.incomingCallVoiceOrVideoTextView.setText(
                                            String.format(getResources().getString(R.string.nc_call_voice),
                                                          getResources().getString(R.string.nc_app_product_name)));
                                }
                            }
                        }
                    }

                    @SuppressLint("LongLogTag")
                    @Override
                    public void onError(@io.reactivex.annotations.NonNull Throwable e) {
                        Log.e(TAG, e.getMessage(), e);
                    }

                    @Override
                    public void onComplete() {

                    }
                });
    }

    private boolean isInCallWithVideo(int callFlag) {
        return (Participant.ParticipantFlags.IN_CALL_WITH_VIDEO.getValue() == callFlag
                || Participant.ParticipantFlags.IN_CALL_WITH_AUDIO_AND_VIDEO.getValue() == callFlag);
    }

    private void setUpAfterConversationIsKnown() {
        binding.conversationNameTextView.setText(currentConversation.getDisplayName());

        // TODO: load avatar, but don't block UI!
//        loadAvatar();
        checkIfAnyParticipantsRemainInRoom();
        showAnswerControls();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(ConfigurationChangeEvent configurationChangeEvent) {
        ConstraintLayout.LayoutParams layoutParams = (ConstraintLayout.LayoutParams) binding.avatarImageView.getLayoutParams();
        int dimen = (int) getResources().getDimension(R.dimen.avatar_size_very_big);

        layoutParams.width = dimen;
        layoutParams.height = dimen;
        binding.avatarImageView.setLayoutParams(layoutParams);
    }


    private void loadAvatar() {
        switch (currentConversation.getType()) {
            case ROOM_TYPE_ONE_TO_ONE_CALL:
                binding.avatarImageView.setVisibility(View.VISIBLE);

                ImageRequest imageRequest =
                        DisplayUtils.getImageRequestForUrl(
                                ApiUtils.getUrlForAvatarWithName(userBeingCalled.getBaseUrl(),
                                                                 currentConversation.getName(),
                                                                 R.dimen.avatar_size_very_big),
                                null);

                ImagePipeline imagePipeline = Fresco.getImagePipeline();
                DataSource<CloseableReference<CloseableImage>> dataSource = imagePipeline.fetchDecodedImage(imageRequest, null);

                dataSource.subscribe(new BaseBitmapDataSubscriber() {
                    @Override
                    protected void onNewResultImpl(@Nullable Bitmap bitmap) {
                        binding.avatarImageView.getHierarchy().setImage(new BitmapDrawable(bitmap), 100,
                                                                true);
                        if (getResources() != null) {
                            binding.incomingCallRelativeLayout.setBackground(
                                    getResources().getDrawable(R.drawable.incoming_gradient));
                        }

                        if (AvatarStatusCodeHolder.getInstance().getStatusCode() == 200 ||
                                AvatarStatusCodeHolder.getInstance().getStatusCode() == 0) {

                                Bitmap backgroundBitmap = bitmap.copy(bitmap.getConfig(), true);
                                new BlurPostProcessor(5, context).process(backgroundBitmap);
                                binding.backgroundImageView.setImageDrawable(new BitmapDrawable(backgroundBitmap));

                        } else if (AvatarStatusCodeHolder.getInstance().getStatusCode() == 201) {
                            ColorArt colorArt = new ColorArt(bitmap);
                            int color = colorArt.getBackgroundColor();

                            float[] hsv = new float[3];
                            Color.colorToHSV(color, hsv);
                            hsv[2] *= 0.75f;
                            color = Color.HSVToColor(hsv);

                            binding.backgroundImageView.setImageDrawable(new ColorDrawable(color));
                        }
                    }

                    @Override
                    protected void onFailureImpl(DataSource<CloseableReference<CloseableImage>> dataSource) {
                        // unused atm
                    }
                }, UiThreadImmediateExecutorService.getInstance());

                break;
            case ROOM_GROUP_CALL:
                binding.avatarImageView.setImageResource(R.drawable.ic_circular_group);
            case ROOM_PUBLIC_CALL:
                binding.avatarImageView.setImageResource(R.drawable.ic_circular_group);
                break;
            default:
        }
    }

    private void endMediaNotifications() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }

            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    @Override
    public void onDestroy() {
        AvatarStatusCodeHolder.getInstance().setStatusCode(0);
        leavingScreen = true;
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
            handler = null;
        }
        dispose();
        endMediaNotifications();
        super.onDestroy();
    }

    private void dispose() {
        if (disposablesList != null) {
            for (Disposable disposable : disposablesList) {
                if (!disposable.isDisposed()) {
                    disposable.dispose();
                }
            }
        }
    }

    @SuppressLint("LongLogTag")
    private void playRingtoneSound() {
        String callRingtonePreferenceString = appPreferences.getCallRingtoneUri();
        Uri ringtoneUri;

        if (TextUtils.isEmpty(callRingtonePreferenceString)) {
            // play default sound
            ringtoneUri = Uri.parse("android.resource://" + getApplicationContext().getPackageName() +
                                            "/raw/librem_by_feandesign_call");
        } else {
            try {
                RingtoneSettings ringtoneSettings = LoganSquare.parse(
                        callRingtonePreferenceString, RingtoneSettings.class);
                ringtoneUri = ringtoneSettings.getRingtoneUri();
            } catch (IOException e) {
                Log.e(TAG, "Failed to parse ringtone settings");
                ringtoneUri = Uri.parse("android.resource://" + getApplicationContext().getPackageName() +
                                                "/raw/librem_by_feandesign_call");
            }
        }

        if (ringtoneUri != null) {
            mediaPlayer = new MediaPlayer();
            try {
                mediaPlayer.setDataSource(this, ringtoneUri);

                mediaPlayer.setLooping(true);
                AudioAttributes audioAttributes = new AudioAttributes
                        .Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .build();
                mediaPlayer.setAudioAttributes(audioAttributes);

                mediaPlayer.setOnPreparedListener(mp -> mediaPlayer.start());

                mediaPlayer.prepareAsync();
            } catch (IOException e) {
                Log.e(TAG, "Failed to set data source");
            }
        }
    }

    public void onBackPressed() {
        enterPipMode();
    }

    public void onUserLeaveHint() {
        enterPipMode();
    }

    void enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPictureInPictureMode(getPipParams());
        } else {
            finish();
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    public PictureInPictureParams getPipParams() {
        Rational pipRatio = new Rational(300, 500);
        return new PictureInPictureParams.Builder()
            .setAspectRatio(pipRatio)
            .build();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        isInPipMode = isInPictureInPictureMode;
        if (isInPictureInPictureMode) {
            updateUiForPipMode();
        } else {
            updateUiForNormalMode();
        }
    }

    public void updateUiForPipMode(){
        binding.callAnswerButtons.setVisibility(View.INVISIBLE);
        binding.incomingCallRelativeLayout.setVisibility(View.INVISIBLE);
    }

    public void updateUiForNormalMode(){
        binding.callAnswerButtons.setVisibility(View.VISIBLE);
        binding.incomingCallRelativeLayout.setVisibility(View.VISIBLE);
    }

    // TODO: dismiss keyguard works, but whenever accepting the call and switch to CallActivity by intent, the
    //  lockscreen is shown (although CallActivity also dismisses the keyguard in the same way.)
    private void dismissKeyguard() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            KeyguardManager keyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
            keyguardManager.requestDismissKeyguard(this, null);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
    }
}