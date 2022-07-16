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
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;

import com.facebook.common.executors.UiThreadImmediateExecutorService;
import com.facebook.common.references.CloseableReference;
import com.facebook.datasource.DataSource;
import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.imagepipeline.core.ImagePipeline;
import com.facebook.imagepipeline.datasource.BaseBitmapDataSubscriber;
import com.facebook.imagepipeline.image.CloseableImage;
import com.facebook.imagepipeline.request.ImageRequest;
import com.nextcloud.talk.R;
import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.data.user.model.User;
import com.nextcloud.talk.databinding.CallNotificationActivityBinding;
import com.nextcloud.talk.events.CallNotificationClick;
import com.nextcloud.talk.models.json.conversations.Conversation;
import com.nextcloud.talk.models.json.conversations.RoomOverall;
import com.nextcloud.talk.models.json.participants.Participant;
import com.nextcloud.talk.models.json.participants.ParticipantsOverall;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.DisplayUtils;
import com.nextcloud.talk.utils.DoNotDisturbUtils;
import com.nextcloud.talk.utils.NotificationUtils;
import com.nextcloud.talk.utils.bundle.BundleKeys;
import com.nextcloud.talk.utils.database.user.CapabilitiesUtilNew;
import com.nextcloud.talk.utils.preferences.AppPreferences;

import org.greenrobot.eventbus.EventBus;
import org.parceler.Parcels;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import autodagger.AutoInjector;
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import okhttp3.Cache;

@SuppressLint("LongLogTag")
@AutoInjector(NextcloudTalkApplication.class)
public class CallNotificationActivity extends CallBaseActivity {

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
    private User userBeingCalled;
    private String credentials;
    private Conversation currentConversation;
    private MediaPlayer mediaPlayer;
    private boolean leavingScreen = false;
    private Handler handler;
    private CallNotificationActivityBinding binding;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);

        NextcloudTalkApplication.Companion.getSharedApplication().getComponentApplication().inject(this);

        binding = CallNotificationActivityBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        hideNavigationIfNoPipAvailable();

        eventBus.post(new CallNotificationClick());

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
            Log.d(TAG, "accept call (voice only)");
            originalBundle.putBoolean(BundleKeys.INSTANCE.getKEY_CALL_VOICE_ONLY(), true);
            proceedToCall();
        });

        binding.callAnswerCameraView.setOnClickListener(l -> {
            Log.d(TAG, "accept call (with video)");
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

    private void hangup() {
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
        int apiVersion = ApiUtils.getCallApiVersion(userBeingCalled, new int[]{ApiUtils.APIv4, 1});

        ncApi.getPeersForCall(
            credentials,
            ApiUtils.getUrlForCall(
                apiVersion,
                userBeingCalled.getBaseUrl(),
                currentConversation.getToken()))
            .subscribeOn(Schedulers.io())
            .repeatWhen(completed -> completed.zipWith(Observable.range(1, 12), (n, i) -> i)
                .flatMap(retryCount -> Observable.timer(5, TimeUnit.SECONDS))
                .takeWhile(observable -> !leavingScreen))
            .subscribe(new Observer<ParticipantsOverall>() {
                @Override
                public void onSubscribe(@NonNull Disposable d) {
                    disposablesList.add(d);
                }

                @Override
                public void onNext(@NonNull ParticipantsOverall participantsOverall) {
                    boolean hasParticipantsInCall = false;
                    boolean inCallOnDifferentDevice = false;
                    List<Participant> participantList = participantsOverall.getOcs().getData();
                    hasParticipantsInCall = participantList.size() > 0;

                    if (hasParticipantsInCall) {
                        for (Participant participant : participantList) {
                            if (participant.getCalculatedActorType() == Participant.ActorType.USERS &&
                                participant.getCalculatedActorId().equals(userBeingCalled.getUserId())) {
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
                public void onError(@NonNull Throwable e) {
                    Log.e(TAG, "error while getPeersForCall", e);
                }

                @Override
                public void onComplete() {
                    runOnUiThread(() -> hangup());
                }
            });

    }

    private void handleFromNotification() {
        int apiVersion = ApiUtils.getConversationApiVersion(userBeingCalled, new int[]{ApiUtils.APIv4,
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
                    currentConversation = roomOverall.getOcs().getData();
                    setUpAfterConversationIsKnown();

                    if (apiVersion >= 3) {
                        boolean hasCallFlags =
                            CapabilitiesUtilNew.hasSpreedFeatureCapability(userBeingCalled,
                                                                           "conversation-call-flags");
                        if (hasCallFlags) {
                            if (isInCallWithVideo(currentConversation.getCallFlag())) {
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

    private boolean isInCallWithVideo(int callFlag) {
        return (callFlag >= Participant.InCallFlags.IN_CALL + Participant.InCallFlags.WITH_VIDEO);
    }

    private void setUpAfterConversationIsKnown() {
        binding.conversationNameTextView.setText(currentConversation.getDisplayName());

        if(currentConversation.getType() == Conversation.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL){
            setAvatarForOneToOneCall();
        } else {
            binding.avatarImageView.setImageResource(R.drawable.ic_circular_group);
        }

        checkIfAnyParticipantsRemainInRoom();
        showAnswerControls();
    }

    private void setAvatarForOneToOneCall() {
        ImageRequest imageRequest =
            DisplayUtils.getImageRequestForUrl(
                ApiUtils.getUrlForAvatar(userBeingCalled.getBaseUrl(),
                                         currentConversation.getName(),
                                         true));

        ImagePipeline imagePipeline = Fresco.getImagePipeline();
        DataSource<CloseableReference<CloseableImage>> dataSource = imagePipeline.fetchDecodedImage(imageRequest, null);

        dataSource.subscribe(new BaseBitmapDataSubscriber() {
            @Override
            protected void onNewResultImpl(@Nullable Bitmap bitmap) {
                binding.avatarImageView.getHierarchy().setImage(
                    new BitmapDrawable(getResources(), bitmap),
                    100,
                    true);
            }

            @Override
            protected void onFailureImpl(DataSource<CloseableReference<CloseableImage>> dataSource) {
                Log.e(TAG, "failed to load avatar");
            }
        }, UiThreadImmediateExecutorService.getInstance());
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

    private void playRingtoneSound() {
        Uri ringtoneUri = NotificationUtils.INSTANCE.getCallRingtoneUri(getApplicationContext(), appPreferences);
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

    public void updateUiForPipMode() {
        binding.callAnswerButtons.setVisibility(View.INVISIBLE);
        binding.incomingCallRelativeLayout.setVisibility(View.INVISIBLE);
    }

    public void updateUiForNormalMode() {
        binding.callAnswerButtons.setVisibility(View.VISIBLE);
        binding.incomingCallRelativeLayout.setVisibility(View.VISIBLE);
    }

    @Override
    void suppressFitsSystemWindows() {
        binding.controllerCallNotificationLayout.setFitsSystemWindows(false);
    }
}